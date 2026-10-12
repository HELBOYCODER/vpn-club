package com.helboy.vpnclub.corex

import android.content.Context
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

/** کار در حال اجرا — هر دکمه یک کار جدا. */
enum class Task { NONE, SCAN_IP, RESCAN, INJECT, CONNECT, SUB, DEPLOY, WARP }

/**
 * مغز برنامه (singleton هم‌عمر با پردازه، مستقل از Activity).
 *
 * امکانات جدا، هرکدام با دکمه‌ی خودش:
 *  - [scanIps]        اسکنر: پیدا کردن IPهای تمیز کلادفلر
 *  - [rescanServers]  ری‌اسکن: لیست تازه‌ی ۱۰ سرور سالم تصادفی از کل ساب‌ها
 *  - [injectIps]      اینجکت: تزریق تمیزترین IP به سرورهای پشت کلادفلر در همان ۱۰ سرور
 *  - [connectFastest] سریع‌ترین اتصال: همه‌ی ۱۰ سرور هم‌زمان تست، اتصال به کم‌تاخیرترین
 *  - [connectTo]      اتصال به سرور انتخابی
 * همه با موتور PattNG و پروفایل‌های فایروال ایران ([IranVariants]).
 *
 * خودکار در پس‌زمینه: نگهبان اتصال (تعویض سرور هنگام قطعی)، بررسی سلامت لیست و تغییر شبکه.
 */
object VpnController {

    data class UiState(
        val phase: Phase = Phase.IDLE,
        val message: String = "",
        val connected: Boolean = false,
        val servers: List<ServerEntry> = emptyList(),
        val current: ServerEntry? = null,
        val configCount: Int = 0,
        val cleanIpCount: Int = 0,
        val task: Task = Task.NONE,
        val taskMsg: String = "",
        val connectedSince: Long = 0,
        val upBps: Long = 0,
        val downBps: Long = 0,
        val upTotal: Long = 0,
        val downTotal: Long = 0,
        val latencyMs: Long = -1,
        val cleanIps: List<CloudflareScanner.CleanIp> = emptyList(),
        val needsPermission: Boolean = false,
        /** تنها IP که برای اینجکت استفاده می‌شود (سریع‌ترینِ آخرین اسکن یا ورود دستی). */
        val selectedIp: String = "",
        val psiphonOn: Boolean = false,
        val psiphon: PsiphonState = PsiphonState.OFF,
        val psiphonHasConfig: Boolean = false,
        /** پینگ مستقیم IP منتخب با اینترنت گوشی (−۱ = هنوز/ناموفق). */
        val selectedIpMs: Long = -1,
        val selectedIpColo: String = "",
        /** نتیجه‌ی آخرین اینجکت روی هر سرور. */
        val injectReport: List<HellboyEngine.InjectResult> = emptyList(),
        val autoInject: Boolean = true,
        /** «ساب‌های من» (پنل‌های نصب‌شده + دستی). */
        val mySubs: List<MySub> = emptyList(),
        /** ساب در حال رفرش/پینگ (برای نشانگر روی همان کارت). */
        val subBusyId: String = "",
        /** نصب پنل: مراحل زنده، نتیجه یا خطا. */
        val deploySteps: List<String> = emptyList(),
        val deployResult: CloudflareDeployer.Result? = null,
        val deployError: String = "",
        /** همه‌ی سرورهای هر ساب (حتی بدون پینگ) برای انتخاب دستی. */
        val subServers: Map<String, List<SubServer>> = emptyMap(),
        /** auto | mine */
        val serverSource: String = "auto",
        /** WARP: حساب ثبت شده؟ نویز روشن؟ */
        val warpRegistered: Boolean = false,
        val warpNoise: Boolean = true
    ) {
        val testing: Boolean get() = task != Task.NONE
        val busyConnecting: Boolean get() = task == Task.CONNECT
    }

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default +
        CoroutineExceptionHandler { _, t ->
            DiagLogger.log("CONTROLLER_EXC ${t.javaClass.simpleName}: ${t.message}")
            _state.update { it.copy(task = Task.NONE, taskMsg = "") }
        })

    private lateinit var app: Context
    private lateinit var engine: HellboyEngine
    private lateinit var settings: AppSettings
    private lateinit var subStore: SubStore
    @Volatile private var initialized = false

    /** فقط یک «کار» (اسکن/ری‌اسکن/اینجکت/اتصال) در هر لحظه. */
    private val opMutex = Mutex()
    private var opJob: Job? = null
    private var watchJob: Job? = null
    private var statsJob: Job? = null
    private var poolJob: Job? = null
    @Volatile private var switching = false
    @Volatile private var baseUp = 0L
    @Volatile private var baseDown = 0L

    fun init(context: Context) {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            app = context.applicationContext
            engine = HellboyEngine(app)
            settings = AppSettings.of(app)
            subStore = SubStore(app)
            _state.update {
                it.copy(servers = engine.savedServers().sortedByDescending { e -> e.score },
                    cleanIps = engine.savedCleanIps(), selectedIp = settings.selectedIp)
            }
            initialized = true
        }
        _state.update { it.copy(psiphonOn = settings.psiphonEnabled, psiphonHasConfig = PsiphonRunner.hasUserConfig(app),
            autoInject = settings.autoInjectAfterScan, mySubs = subStore.load(), serverSource = settings.serverSource) }
        _state.update { st -> st.copy(subServers = st.mySubs.associate { it.id to subStore.loadServers(it.id) }) }
        DiagLogger.sessionHeader(app)
        XrayCore.warpNoise = settings.warpNoise
        _state.update { it.copy(warpRegistered = WarpManager.account(app) != null, warpNoise = settings.warpNoise) }
        scope.launch {
            PsiphonRunner.state.collect { ps ->
                _state.update { it.copy(psiphon = ps) }
                // سایفون مستقیم تنها مسیر است؛ اگر موتورش افتاد، تونل را ببند تا کاربر بداند
                if (ps == PsiphonState.FAILED && psiphonOnlyUp && !opMutex.isLocked) {
                    HellboyVpnService.stop(app)
                    markDisconnected("سایفون قطع شد: ${PsiphonRunner.lastError}")
                }
            }
        }
        registerNetworkKick()
        scope.launch {
            HellboyVpnService.status.collect { s ->
                if (s.state == SvcState.STOPPED && _state.value.connected) markDisconnected("اتصال توسط سیستم قطع شد")
            }
        }
    }

    fun settings(): AppSettings = settings

    /** پیام کوتاه از UI (مثلاً «کپی شد»). */
    fun notify(msg: String) = toast(msg)
    fun clearPermissionFlag() { _state.update { it.copy(needsPermission = false) } }

    // ════════════════════════════ دکمه‌ها ════════════════════════════

    /** دکمه‌ی اصلی: وصل/قطع. وصل = سریع‌ترین (یا سرور انتخابی اگر گزینه‌ی سریع‌ترین خاموش است). */
    fun toggle() {
        val s = _state.value
        if (s.connected || s.task == Task.CONNECT) { disconnect(); return }
        if (psiphonDirect()) { connectPsiphonOnly(); return }
        if (settings.fastestMode || s.servers.isEmpty()) connectFastest()
        else connectTo(s.servers.first())
    }

    /** اسکنر IP تمیز کلادفلر. */
    fun scanIps() = runTask(Task.SCAN_IP) {
        DiagLogger.section("SCAN_IP net=${DiagLogger.networkSummary(app)}")
        doScanIps()
        maybeAutoInject()
    }

    /** لیست تازه‌ی ۱۰ سرور سالم تصادفی از کل ساب‌ها (جایگزین لیست قبلی). */
    fun rescanServers() = runTask(Task.RESCAN) { DiagLogger.section("RESCAN"); doRescan() }

    /** تزریق IPهای اسکن‌شده به سرورهای لیست. */
    fun injectIps() = runTask(Task.INJECT) { doInject() }

    /** روشن/خاموش کردن سایفون روی سرور (اگر وصلیم، بدون قطع سرور اعمال می‌شود). */
    fun setPsiphon(on: Boolean): String? {
        if (on && !PsiphonRunner.binaryAvailable(app)) return "موتور سایفون برای پردازنده‌ی این گوشی موجود نیست"
        settings.psiphonEnabled = on
        _state.update { it.copy(psiphonOn = on, message = if (on) "سایفون روشن شد" else "سایفون خاموش شد") }
        if (!on && psiphonOnlyUp) disconnect()
        else if (_state.value.connected) HellboyVpnService.applyPsiphon(app)
        return null
    }

    /** ذخیره‌ی کانفیگ رسمی سایفون. null = موفق. */
    fun importPsiphonConfig(text: String): String? {
        val err = PsiphonRunner.saveUserConfig(app, text)
        _state.update { it.copy(psiphonHasConfig = PsiphonRunner.hasUserConfig(app)) }
        return err
    }

    fun clearPsiphonConfig() {
        PsiphonRunner.clearUserConfig(app)
        toast("کانفیگ رسمی پاک شد؛ سایفون با حالت رایگان کار می‌کند")
        _state.update { it.copy(psiphonHasConfig = false) }
    }

    /** انتخاب/ویرایش دستی IP منتخب. false = IP نامعتبر. */
    fun selectIp(ip: String): Boolean {
        val v = ip.trim()
        if (!isValidIpv4(v)) return false
        settings.selectedIp = v
        _state.update { it.copy(selectedIp = v, selectedIpMs = -1, selectedIpColo = "", message = "IP منتخب: $v — در حال پینگ…") }
        DiagLogger.log("PICK_IP manual $v")
        scope.launch {
            val p = engine.pingIp(v)
            if (_state.value.selectedIp == v) _state.update {
                it.copy(selectedIpMs = p?.tlsMs ?: -1, selectedIpColo = p?.colo.orEmpty(),
                    message = if (p != null) "IP منتخب: $v · پینگ ${p.tlsMs}ms (${p.colo})" else "IP منتخب: $v · با اینترنت گوشی پینگ نداد")
            }
        }
        return true
    }

    /** دکمه‌ی «منتخب»: بهترین IP (پایدارترین پینگ مستقیم) از اسکن آخر؛ اگر اسکنی نیست، اول اسکن. */
    fun pickSelectedIp() = runTask(Task.SCAN_IP) {
        DiagLogger.section("PICK_IP button cached=${_state.value.cleanIps.size}")
        if (_state.value.cleanIps.isEmpty()) doScanIps() else choosePick(_state.value.cleanIps)
        val st = _state.value
        if (st.selectedIp.isNotEmpty()) toast("IP منتخب: ${st.selectedIp}" + if (st.selectedIpMs > 0) " · پینگ ${st.selectedIpMs}ms" else " · پینگ مستقیم نداد")
        maybeAutoInject()
    }

    fun setAutoInject(on: Boolean) {
        settings.autoInjectAfterScan = on
        _state.update { it.copy(autoInject = on) }
    }

    /** اینجکت خودکار بعد از انتخاب IP (اگر روشن و سرور داریم). */
    private suspend fun maybeAutoInject() {
        if (settings.autoInjectAfterScan && _state.value.servers.isNotEmpty() && _state.value.selectedIp.isNotEmpty()) {
            DiagLogger.log("AUTO_INJECT after pick")
            doInject()
        }
    }

    private suspend fun choosePick(ips: List<CloudflareScanner.CleanIp>) {
        setTask(Task.SCAN_IP, "پینگ IPهای برتر با اینترنت گوشی…")
        val best = engine.pickBestIp(ips)
        val pick = best ?: ips.firstOrNull() ?: return
        settings.selectedIp = pick.ip
        _state.update { it.copy(selectedIp = pick.ip, selectedIpMs = best?.tlsMs ?: -1, selectedIpColo = pick.colo) }
        DiagLogger.log("PICK_IP auto ${pick.ip} direct=${best?.tlsMs ?: "FAIL"} colo=${pick.colo}")
    }

    /** اینجکت دستی IP منتخب روی یک سرور مشخص (اگر همان سرور متصل است، اتصال روی IP جدید می‌رود). */
    fun injectInto(entry: ServerEntry) = runTask(Task.INJECT) {
        val ip = _state.value.selectedIp.ifEmpty { doScanIps().firstOrNull()?.ip.orEmpty() }
        if (ip.isEmpty()) { toast("اول IP اسکن یا وارد کنید"); return@runTask }
        if (!entry.canInjectManual) { toast("این سرور ${entry.injectBlocker}؛ IP تمیز رویش کار نمی‌کند"); return@runTask }
        setTask(Task.INJECT, "اینجکت $ip روی ${label(entry)}…")
        val updated = engine.injectOne(entry, ip)
        val ok = updated.latencyMs > 0
        // انتخاب کاربر اعمال می‌شود؛ وضعیت تست کنارش نمایش داده می‌شود
        publish(_state.value.servers.map { if (it.serverKey == entry.serverKey) updated.copy(cf = true) else it })
        val isCurrent = _state.value.connected && _state.value.current?.serverKey == entry.serverKey
        if (isCurrent && ok) {
            setTask(Task.INJECT, "اتصال دوباره با $ip…")
            toast(if (activate(updated)) "اتصال روی $ip رفت (${updated.latencyMs}ms)" else "اتصال با $ip برقرار نشد؛ IP قبلی حفظ شد")
        } else toast(if (ok) "$ip روی ${label(entry)} اینجکت شد (${updated.latencyMs}ms)"
                     else "$ip روی ${label(entry)} اعمال شد ولی تست جواب نداد — IP دیگری امتحان کنید")
    }

    /**
     * ویرایش کانفیگ یک سرور: کانفیگ تازه تست می‌شود و جایگزین می‌شود (حتی اگر پینگ نداد، با
     * وضعیت «بدون پاسخ» — انتخاب کاربر حفظ می‌شود). اگر همان سرور وصل است، اتصال روی کانفیگ جدید می‌رود.
     */
    fun editServer(old: ServerEntry, cfg: ProxyConfig) = runTask(Task.INJECT) {
        DiagLogger.section("EDIT ${old.config.host}:${old.config.port} -> ${cfg.host}:${cfg.port}")
        setTask(Task.INJECT, "تست کانفیگ ویرایش‌شده…")
        val (fresh, r) = engine.testConfig(cfg, old.variantId, old.useFrag)
        val wasCurrent = _state.value.connected && _state.value.current?.serverKey == old.serverKey
        if (old.source.startsWith("sub-")) recordSubEdit(old.source, old.config.raw, fresh, r)
        publish(_state.value.servers.map { if (it.serverKey == old.serverKey) fresh.copy(source = old.source) else it }.distinctBy { it.serverKey })
        if (wasCurrent && r is DelayResult.Ok) {
            setTask(Task.INJECT, "اتصال با کانفیگ جدید…")
            toast(if (activate(fresh)) "کانفیگ ذخیره و اتصال روی آن رفت (${r.ms}ms)" else "کانفیگ ذخیره شد ولی اتصال برقرار نشد؛ کانفیگ قبلی حفظ شد")
        } else toast(when (r) {
            is DelayResult.Ok -> "کانفیگ ذخیره شد · پینگ ${r.ms}ms"
            is DelayResult.Fail -> "کانفیگ ذخیره شد ولی پینگ نداد (${r.log.take(50)})"
        })
    }

    /** ثبت ویرایش روی لیست ذخیره‌شده‌ی ساب (لینک اصلی نگه داشته می‌شود تا رفرش ویرایش را پاک نکند). */
    private fun recordSubEdit(subId: String, oldRaw: String, fresh: ServerEntry, r: DelayResult) {
        val list = _state.value.subServers[subId].orEmpty()
        val old = list.firstOrNull { it.raw == oldRaw }
        val orig = old?.origRaw?.ifEmpty { null } ?: oldRaw
        val ms = (r as? DelayResult.Ok)?.ms ?: -1
        val upd = SubServer(fresh.config.raw, ms, "ویرایش‌شده" + if (r is DelayResult.Fail) " · ${r.log.take(40)}" else "", true, origRaw = orig)
        val newList = if (old != null) list.map { if (it.raw == oldRaw) upd else it } else listOf(upd) + list
        subStore.saveServers(subId, newList)
        _state.update { it.copy(subServers = it.subServers + (subId to newList)) }
        DiagLogger.log("SUB_EDIT $subId ${fresh.config.host}:${fresh.config.port} -> ${if (ms > 0) "${ms}ms" else "no ping"}")
    }

    /** ویرایش یک سرور داخل ساب (از لیست باز شده‌ی ساب). */
    fun editSubServer(subId: String, srv: SubServer, cfg: ProxyConfig) = runTask(Task.INJECT) {
        DiagLogger.section("SUB_EDIT ${cfg.host}:${cfg.port}")
        setTask(Task.INJECT, "تست کانفیگ ویرایش‌شده…")
        val (fresh, r) = engine.testConfig(cfg)
        recordSubEdit(subId, srv.raw, fresh, r)
        val entry = fresh.copy(source = subId)
        val oldKey = srv.toEntry(subId)?.serverKey
        val wasCurrent = _state.value.connected && _state.value.current?.serverKey == oldKey
        if (r is DelayResult.Ok) publish(listOf(entry) + _state.value.servers.filterNot { it.serverKey == oldKey })
        if (wasCurrent && r is DelayResult.Ok) {
            toast(if (activate(entry)) "ذخیره شد و اتصال روی کانفیگ جدید رفت (${r.ms}ms)" else "ذخیره شد ولی اتصال برقرار نشد؛ قبلی حفظ شد")
        } else toast(when (r) { is DelayResult.Ok -> "ذخیره شد · پینگ ${r.ms}ms"; is DelayResult.Fail -> "ذخیره شد ولی پینگ نداد (${r.log.take(50)})" })
    }

    /** حذف یک سرور از لیست (اگر وصل است، اول قطع). */
    fun removeServer(e: ServerEntry) {
        if (_state.value.connected && _state.value.current?.serverKey == e.serverKey) disconnect()
        publish(_state.value.servers.filterNot { it.serverKey == e.serverKey })
        DiagLogger.log("REMOVE ${e.config.host}:${e.config.port}")
        toast("سرور حذف شد")
    }

    /** وارد کردن کانفیگ از متن (کلیپ‌بورد / QR دیگران): همه تست و به لیست اضافه می‌شوند. */
    fun importConfigs(text: String) = runTask(Task.RESCAN) {
        val cfgs = ConfigEditor.parseMany(text)
        DiagLogger.section("IMPORT parsed=${cfgs.size}")
        if (cfgs.isEmpty()) { toast("لینک کانفیگ معتبری پیدا نشد"); return@runTask }
        val supported = cfgs.filter { XrayCore.isSupported(it) }
        if (supported.isEmpty()) { toast("این کانفیگ‌ها پشتیبانی نمی‌شوند"); return@runTask }
        val existing = _state.value.servers.map { it.serverKey }.toSet()
        var added = 0; var alive = 0
        supported.take(30).forEachIndexed { i, c ->
            setTask(Task.RESCAN, "تست کانفیگ ${i + 1}/${minOf(supported.size, 30)}…")
            val (e, r) = engine.testConfig(c)
            if (e.serverKey in existing) return@forEachIndexed
            added++; if (r is DelayResult.Ok) alive++
            publish((_state.value.servers + e).distinctBy { it.serverKey })
        }
        toast("$added کانفیگ اضافه شد · $alive پینگ داد" + if (supported.size < cfgs.size) " · ${cfgs.size - supported.size} پشتیبانی نشد" else "")
    }

    // ════════════════════════════ ساب‌های من ════════════════════════════

    private fun saveSubs(list: List<MySub>) { subStore.save(list); _state.update { it.copy(mySubs = list) } }
    private fun updateSub(id: String, f: (MySub) -> MySub) = saveSubs(_state.value.mySubs.map { if (it.id == id) f(it) else it })

    /** افزودن ساب (دستی یا بعد از نصب پنل) و رفرش فوری. null = موفق؛ وگرنه دلیل. */
    fun addSub(name: String, url: String, kind: String = "manual", panelUrl: String = ""): String? {
        val u = url.trim()
        if (!(u.startsWith("https://") || u.startsWith("http://"))) return "لینک ساب باید با https:// شروع شود"
        if (_state.value.mySubs.any { it.url == u }) return "این ساب از قبل اضافه شده"
        val sub = MySub(MySub.newId(), name.trim().ifEmpty { runCatching { java.net.URI(u).host }.getOrDefault("ساب") }, u, kind, panelUrl)
        saveSubs(_state.value.mySubs + sub)
        DiagLogger.log("SUB_ADD ${sub.kind} ${runCatching { java.net.URI(u).host }.getOrDefault("?")}")
        refreshSub(sub.id)
        return null
    }

    fun removeSub(id: String) {
        val cur = _state.value.current
        saveSubs(_state.value.mySubs.filterNot { it.id == id })
        subStore.deleteServers(id)
        _state.update { it.copy(subServers = it.subServers - id) }
        if (_state.value.mySubs.isEmpty() && settings.serverSource == "mine") setServerSource("auto")
        publish(_state.value.servers.filterNot { it.source == id && it.serverKey != cur?.serverKey })
        toast("ساب حذف شد")
    }

    /** رفرش: دریافت تازه‌ی کانفیگ‌های ساب ← تست همه با اینترنت گوشی ← سالم‌ها به لیست سرورها. */
    fun refreshSub(id: String) = runTask(Task.SUB) { doRefreshSub(id, fetch = true) }

    /** پینگ: تست دوباره‌ی همه‌ی سرورهای ذخیره‌شده‌ی همین ساب (اگر چیزی ذخیره نیست، رفرش کامل). */
    fun pingSub(id: String) = runTask(Task.SUB) { doRefreshSub(id, fetch = _state.value.subServers[id].isNullOrEmpty()) }

    /**
     * [fetch] = دریافت دوباره از لینک ساب؛ وگرنه فقط تست دوباره‌ی لیست ذخیره‌شده.
     * همه‌ی سرورها (حتی ناسالم/پشتیبانی‌نشده با دلیل) برای نمایش و انتخاب دستی ذخیره می‌شوند.
     * @return تعداد سالم
     */
    private suspend fun doRefreshSub(id: String, fetch: Boolean): Int {
        val sub = _state.value.mySubs.firstOrNull { it.id == id } ?: return 0
        _state.update { it.copy(subBusyId = id) }
        try {
            DiagLogger.section("SUB_${if (fetch) "REFRESH" else "PING"} ${sub.name} (${sub.kind})")
            val cfgs0: List<ProxyConfig> = if (fetch) {
                setTask(Task.SUB, "دریافت ساب «${sub.name}»…")
                try { engine.fetchSub(sub.url) } catch (e: Exception) { DiagLogger.log("SUB_FETCH error ${e.message}"); emptyList() }
            } else (_state.value.subServers[id] ?: emptyList()).mapNotNull { LinkParser.parse(it.origRaw.ifEmpty { it.raw }) }
            // ویرایش‌های کاربر روی سرورهای ساب: لینک اصلی ← لینک ویرایش‌شده (با رفرش از بین نمی‌رود)
            val overrides = (_state.value.subServers[id] ?: emptyList()).filter { it.edited }.associate { it.origRaw to it.raw }
            val cfgs = cfgs0.map { c -> overrides[c.raw]?.let { LinkParser.parse(it) } ?: c }
            val origOf = cfgs0.mapNotNull { c -> overrides[c.raw]?.let { it to c.raw } }.toMap()
            if (cfgs.isEmpty()) {
                updateSub(id) { it.copy(lastFetchAt = System.currentTimeMillis(), lastError = "کانفیگی دریافت نشد") }
                toast("از «${sub.name}» کانفیگی دریافت نشد" + if (!_state.value.connected) " — workers.dev شاید بدون VPN باز نشود؛ اول با حالت خودکار وصل شوید و دوباره رفرش کنید" else "")
                return 0
            }
            val (supported, unsupported) = cfgs.partition { XrayCore.isSupported(it) }
            val toTest = supported.take(MAX_SUB_TEST)
            val results = engine.testMany(toTest, id, { d, a -> setTask(Task.SUB, "تست «${sub.name}» $d/${toTest.size} · سالم: $a") },
                preferredIp = _state.value.selectedIp.ifEmpty { null })
            val tested = results.associateBy { it.first.config.raw }
            val list = supported.map { c ->
                val t = tested[c.raw]
                when (val r = t?.second) {
                    is DelayResult.Ok -> SubServer.of(t.first, r.ms,
                        listOfNotNull(if (origOf[c.raw] != null) "ویرایش‌شده" else null,
                            IranVariants.byId(t.first.variantId)?.takeIf { it.id != "plain" }?.label,
                            t.first.config.dialHost.takeIf { t.first.injected }?.let { "IP $it" }).joinToString(" · "), origOf[c.raw].orEmpty())
                    is DelayResult.Fail -> SubServer(c.raw, -1, r.log.take(60), origRaw = origOf[c.raw].orEmpty())
                    null -> SubServer(c.raw, 0, "تست نشده", origRaw = origOf[c.raw].orEmpty())
                }
            }.sortedWith(compareBy({ if (it.ms > 0) 0 else 1 }, { if (it.ms > 0) it.ms else Long.MAX_VALUE })) +
                unsupported.map { SubServer(it.raw, -1, unsupportedReason(it), supported = false) }
            subStore.saveServers(id, list)
            _state.update { it.copy(subServers = it.subServers + (id to list)) }
            val alive = results.filter { it.second is DelayResult.Ok }.map { it.first }
            // سرورهای قبلی همین ساب در لیست سرورها جایگزین می‌شوند (سرور متصل حفظ می‌شود)
            val cur = _state.value.current
            publish(alive + _state.value.servers.filterNot { it.source == id && it.serverKey != cur?.serverKey })
            val best = alive.minOfOrNull { it.latencyMs } ?: -1
            updateSub(id) { it.copy(lastFetchAt = System.currentTimeMillis(), configCount = cfgs.size, aliveCount = alive.size, bestMs = best,
                lastError = when { alive.isNotEmpty() -> ""; supported.isEmpty() -> "هیچ کانفیگ پشتیبانی‌شده‌ای ندارد"; else -> "هیچ کانفیگی پینگ نداد؛ می‌توانید دستی امتحان کنید" }) }
            DiagLogger.log("SUB_RESULT ${sub.name} configs=${cfgs.size} supported=${supported.size} tested=${toTest.size} alive=${alive.size} best=${best}ms")
            unsupported.take(10).forEach { DiagLogger.log("SUB_UNSUPPORTED ${it.scheme} ${it.host}:${it.port} -> ${unsupportedReason(it)}") }
            toast("«${sub.name}»: ${alive.size} از ${toTest.size} سالم" + (if (best > 0) " · بهترین ${best}ms" else "") +
                (if (unsupported.isNotEmpty()) " · ${unsupported.size} پشتیبانی نمی‌شود" else ""))
            return alive.size
        } finally { _state.update { it.copy(subBusyId = "") } }
    }

    private fun unsupportedReason(c: ProxyConfig): String {
        val q = c.query.toMap()
        return when {
            c.scheme !in setOf("vless", "vmess", "trojan", "ss") -> "پروتکل ${c.scheme} پشتیبانی نمی‌شود"
            listOf("allowInsecure", "insecure").any { q[it] == "1" || q[it] == "true" } -> "allowInsecure (هسته‌ی جدید رد می‌کند)"
            (q["security"] ?: "") == "reality" && q["pbk"].isNullOrBlank() -> "Reality بدون کلید pbk"
            else -> "ترنسپورت ${q["type"] ?: "tcp"} یا رمزنگاری پشتیبانی نمی‌شود"
        }
    }

    /** اتصال دستی به یک سرور ساب (حتی اگر پینگ نداده باشد — تصمیم با کاربر). */
    fun connectSubServer(subId: String, srv: SubServer) = runTask(Task.CONNECT) {
        val e = srv.toEntry(subId) ?: run { toast("لینک این سرور خراب است"); return@runTask }
        val cfg = e.config
        if (!XrayCore.isSupported(cfg)) { toast("این سرور پشتیبانی نمی‌شود: ${srv.note}"); return@runTask }
        DiagLogger.log("SUB_MANUAL_CONNECT ${cfg.scheme} ${cfg.host}:${cfg.port} dial=${cfg.dialHost} variant=${e.variantId ?: "-"} lastMs=${srv.ms}")
        setTask(Task.CONNECT, "اتصال به ${label(e)}…")
        setState(Phase.CONNECTING, "اتصال به ${label(e)}…")
        if (activate(e)) {
            val live = _state.value.current ?: e
            publish(listOf(live.copy(latencyMs = _state.value.latencyMs.coerceAtLeast(1))) + _state.value.servers)
            toast("وصل شد: ${label(e)}")
        } else if (_state.value.connected) setState(Phase.CONNECTED, "این سرور وصل نشد؛ سرور قبلی حفظ شد")
        else setState(Phase.FAILED, "این سرور وصل نشد — سرور دیگری از همین ساب را امتحان کنید")
    }

    /** منبع سرورها. null = موفق؛ وگرنه دلیل. */
    fun setServerSource(v: String): String? {
        if (v == "mine" && _state.value.mySubs.isEmpty()) return "اول یک ساب اضافه کنید؛ بدون ساب، حالت خودکار استفاده می‌شود"
        settings.serverSource = v
        _state.update { it.copy(serverSource = v) }
        DiagLogger.log("SERVER_SOURCE $v")
        toast(if (v == "mine") "فقط سرورهای ساب‌های من استفاده می‌شوند" else "خودکار: ساب‌های عمومی + ساب‌های من")
        return null
    }

    /** حالت «فقط ساب‌های من» فعال و ساب داریم. */
    private val mineOnly: Boolean get() = settings.serverSource == "mine" && _state.value.mySubs.isNotEmpty()

    /** اتصال سریع‌ترین در حالت «فقط ساب‌های من». */
    private suspend fun doConnectMine() {
        DiagLogger.section("CONNECT mine subs=${_state.value.mySubs.size}")
        if (_state.value.servers.none { it.source.startsWith("sub-") }) {
            setState(Phase.TESTING, "رفرش ساب‌های من…")
            for (sub in _state.value.mySubs) doRefreshSub(sub.id, fetch = true)
        }
        var pool = _state.value.servers.filter { it.source.startsWith("sub-") }
        setTask(Task.CONNECT, "تست سرورهای ساب‌های من…")
        setState(Phase.TESTING, "یافتن سریع‌ترین سرور ساب‌های من…")
        var alive = engine.retestAll(pool) { d, a -> setTask(Task.CONNECT, "تست $d/${pool.size} · سالم: $a") }
        if (alive.isEmpty()) {
            setState(Phase.TESTING, "سرورها پاسخ ندادند؛ دریافت دوباره‌ی ساب‌ها…")
            for (sub in _state.value.mySubs) doRefreshSub(sub.id, fetch = true)
            pool = _state.value.servers.filter { it.source.startsWith("sub-") }
            alive = pool.filter { it.latencyMs > 0 }
        }
        // اگر تست‌ها جواب ندادند (مثلاً ساب بدون VPN دریافت نشد)، سرورهای ذخیره‌شده‌ی ساب‌ها مستقیم امتحان می‌شوند
        val tried = alive.map { it.serverKey }.toMutableSet()
        val stored = _state.value.mySubs.flatMap { sub ->
            _state.value.subServers[sub.id].orEmpty().filter { it.supported }.mapNotNull { it.toEntry(sub.id) }
        }.filter { it.serverKey !in tried }.sortedBy { if (it.latencyMs > 0) it.latencyMs else Long.MAX_VALUE }.take(8)
        if (alive.isEmpty() && stored.isNotEmpty()) DiagLogger.log("CONNECT mine fallback to ${stored.size} stored sub servers")
        for (e in alive.sortedBy { it.latencyMs } + stored) {
            if (!currentCoroutineContext().isActive) return
            setTask(Task.CONNECT, "اتصال به ${label(e)}" + (if (e.latencyMs > 0) " (${e.latencyMs}ms)" else "") + "…")
            setState(Phase.CONNECTING, "اتصال به ${label(e)}…")
            if (activate(e)) return
            if (_state.value.needsPermission) return
        }
        setState(Phase.FAILED, "هیچ سروری از ساب‌های من وصل نشد · ساب را باز کنید و دستی امتحان کنید، یا منبع را «خودکار» کنید")
    }

    private const val MAX_SUB_TEST = 60

    // ════════════════════════════ WARP رایگان ════════════════════════════

    private suspend fun ensureWarp(): WarpManager.Account? {
        WarpManager.account(app)?.let { return it }
        setTask(_state.value.task, "ثبت دستگاه در WARP…")
        return try {
            kotlinx.coroutines.withContext(Dispatchers.IO) { WarpManager.register(app) }.also { _state.update { s -> s.copy(warpRegistered = true) } }
        } catch (e: Exception) {
            DiagLogger.log("WARP register failed: ${e.javaClass.simpleName} ${e.message?.take(120)}")
            toast("ثبت WARP ناموفق بود (${e.message?.take(60)})" + if (!_state.value.connected) " — اگر API کلادفلر بسته است، اول با سرور دیگری وصل شوید" else "")
            null
        }
    }

    /** اسکن اندپوینت‌ها: دو دور (۲۴ و بعد ۴۰ اندپوینت) تا سالم پیدا شود. */
    private suspend fun doWarpScan(acc: WarpManager.Account): List<ServerEntry> {
        DiagLogger.section("WARP scan noise=${settings.warpNoise}")
        var found = engine.warpScan(acc, 24) { d, a -> setTask(_state.value.task, "اسکن اندپوینت WARP $d/24 · سالم: $a") }
        if (found.isEmpty()) found = engine.warpScan(acc, 40) { d, a -> setTask(_state.value.task, "اسکن دوم WARP $d/40 · سالم: $a") }
        val cur = _state.value.current
        publish(found.take(5) + _state.value.servers.filterNot { it.source == "warp" && it.serverKey != cur?.serverKey })
        DiagLogger.log("WARP_SCAN result alive=${found.size} best=${found.firstOrNull()?.latencyMs ?: -1}ms")
        return found
    }

    /** اتصال به WARP رایگان: ثبت (اگر لازم) ← اسکن اندپوینت ← اتصال به سریع‌ترین. */
    fun connectWarp() = runTask(Task.CONNECT) {
        _state.update { it.copy(needsPermission = false) }
        setState(Phase.CONNECTING, "WARP…")
        val acc = ensureWarp() ?: run { setState(Phase.FAILED, "ثبت WARP ممکن نشد"); return@runTask }
        val list = _state.value.servers.filter { it.source == "warp" && it.latencyMs > 0 }.ifEmpty { doWarpScan(acc) }
        for (e in list.sortedBy { it.latencyMs }.take(5)) {
            setTask(Task.CONNECT, "اتصال WARP ${e.config.dialHost}:${e.config.dialPort} (${e.latencyMs}ms)…")
            if (activate(e)) { toast("متصل به WARP (${e.latencyMs}ms)"); return@runTask }
            if (_state.value.needsPermission) return@runTask
        }
        setState(Phase.FAILED, "اندپوینت WARP سالمی پیدا نشد · «اسکن WARP» یا روشن/خاموش کردن نویز را امتحان کنید")
    }

    fun warpRescan() = runTask(Task.WARP) {
        val acc = ensureWarp() ?: return@runTask
        val found = doWarpScan(acc)
        toast(if (found.isEmpty()) "اندپوینت WARP سالمی پیدا نشد" else "${found.size} اندپوینت WARP سالم · بهترین ${found.first().latencyMs}ms")
    }

    fun setWarpNoise(on: Boolean) {
        settings.warpNoise = on; XrayCore.warpNoise = on
        _state.update { it.copy(warpNoise = on) }
        DiagLogger.log("WARP noise=$on")
    }

    /** حساب WARP تازه (کلید جدید). */
    fun warpReset() = runTask(Task.WARP) {
        WarpManager.clear(app)
        _state.update { it.copy(warpRegistered = false) }
        publish(_state.value.servers.filterNot { it.source == "warp" && it.serverKey != _state.value.current?.serverKey })
        ensureWarp()?.let { toast("حساب WARP تازه ساخته شد") }
    }

    // ════════════════════════════ نصب پنل کلادفلر ════════════════════════════

    fun clearDeploy() { if (_state.value.task != Task.DEPLOY) _state.update { it.copy(deploySteps = emptyList(), deployResult = null, deployError = "") } }

    /** نصب پنل BPB / Zeus با توکن کلادفلر؛ لینک ساب خودکار به «ساب‌های من» اضافه و رفرش می‌شود. */
    fun deployPanel(panel: CloudflareDeployer.Panel, token: String, workerName: String) = runTask(Task.DEPLOY) {
        _state.update { it.copy(deploySteps = emptyList(), deployResult = null, deployError = "") }
        try {
            val r = kotlinx.coroutines.withContext(Dispatchers.IO) {
                CloudflareDeployer.deploy(panel, token, workerName) { step ->
                    _state.update { it.copy(deploySteps = it.deploySteps + step) }
                    setTask(Task.DEPLOY, step)
                }
            }
            _state.update { it.copy(deployResult = r) }
            DiagLogger.log("CF_DEPLOY done ${r.panel} host=${r.host} sub=${if (r.subUrl.isNotEmpty()) "yes" else "no"}")
            if (r.subUrl.isNotEmpty()) {
                val sub = MySub(MySub.newId(), "${r.panel.label} · ${r.workerName}", r.subUrl,
                    if (r.panel == CloudflareDeployer.Panel.BPB) "bpb" else "zeus", r.panelUrl)
                saveSubs(_state.value.mySubs + sub)
                _state.update { it.copy(deploySteps = it.deploySteps + "لینک ساب به «ساب‌های من» اضافه شد") }
                // ورکر تازه چند ثانیه طول می‌کشد تا در همه‌ی لبه‌ها فعال شود؛ رفرش بعد از پایان همین کار
                scope.launch {
                    delay(4_000)
                    while (opMutex.isLocked) delay(200)
                    refreshSub(sub.id)
                }
            }
        } catch (e: CloudflareDeployer.DeployException) {
            DiagLogger.log("CF_DEPLOY fail ${e.message}")
            _state.update { it.copy(deployError = e.message ?: "خطا") }
        } catch (e: Exception) {
            DiagLogger.log("CF_DEPLOY fail ${e.javaClass.simpleName}: ${e.message}")
            _state.update { it.copy(deployError = "خطای شبکه: ${e.message ?: e.javaClass.simpleName}" +
                if (!_state.value.connected) " — اگر کلادفلر/گیت‌هاب بدون VPN باز نمی‌شود، اول وصل شوید و دوباره نصب کنید" else "") }
        }
    }

    /** سریع‌ترین اتصال: همه‌ی سرورها هم‌زمان تست و اتصال به کم‌تاخیرترین. */
    fun connectFastest() = runTask(Task.CONNECT) { doConnectFastest() }

    /** حالت «سایفون مستقیم» فعال است (کلید روشن + کانفیگ + حالت direct). */
    fun psiphonDirect(): Boolean = settings.psiphonEnabled && settings.psiphonMode == "direct" &&
        PsiphonRunner.hasConfig(app) && PsiphonRunner.binaryAvailable(app)

    /** اتصال فقط با سایفون، بدون سرور (وقتی هیچ سروری جواب نمی‌دهد). */
    fun connectPsiphonOnly() = runTask(Task.CONNECT) {
        _state.update { it.copy(needsPermission = false) }
        setTask(Task.CONNECT, "اتصال سایفون مستقیم… (تا ۲ دقیقه)")
        setState(Phase.CONNECTING, "اتصال سایفون مستقیم…")
        CurrentConfigHolder.clear(app)
        HellboyVpnService.startPsiphonOnly(app)
        val st = withTimeoutOrNull(130_000) {
            HellboyVpnService.status.first { it.state == SvcState.UP || it.state == SvcState.FAILED }
        }
        if (st?.state != SvcState.UP) {
            HellboyVpnService.stop(app)
            val err = st?.error
            if (err is SvcError.Tun && android.net.VpnService.prepare(app) != null)
                _state.update { it.copy(needsPermission = true) }
            setState(Phase.FAILED, st?.message?.ifEmpty { null } ?: "سایفون وصل نشد. منطقه یا پروتکل را عوض کنید.")
            return@runTask
        }
        var lat = -1L
        for (i in 0 until 3) { lat = HealthTester.tunnelLatency(PsiphonRunner.LOCAL_SOCKS, 8_000); if (lat > 0) break; delay(800) }
        val b = Tun2Socks.bytes()
        baseUp = b?.first ?: 0; baseDown = b?.second ?: 0
        _state.update { it.copy(phase = Phase.CONNECTED, message = "متصل با سایفون مستقیم", connected = true, current = null,
            connectedSince = System.currentTimeMillis(), latencyMs = lat) }
        startStats()
        if (watchJob?.isActive != true) startWatchdog()
    }

    private val psiphonOnlyUp: Boolean get() = _state.value.connected && _state.value.current == null

    /** اتصال به سرور مشخص از لیست. */
    fun connectTo(entry: ServerEntry) = runTask(Task.CONNECT) {
        setTask(Task.CONNECT, "اتصال به ${label(entry)}…")
        setState(Phase.CONNECTING, "اتصال به ${label(entry)}…")
        if (!activate(entry)) {
            if (_state.value.connected) setState(Phase.CONNECTED, "این سرور وصل نشد؛ سرور قبلی حفظ شد")
            else setState(Phase.FAILED, "این سرور وصل نشد. «سریع‌ترین اتصال» را امتحان کنید.")
        }
    }

    fun disconnect() {
        opJob?.cancel(); watchJob?.cancel(); statsJob?.cancel(); poolJob?.cancel()
        HellboyVpnService.stop(app)
        CurrentConfigHolder.clear(app)
        _state.update {
            it.copy(phase = Phase.IDLE, message = "", connected = false, current = null,
                task = Task.NONE, taskMsg = "", upBps = 0, downBps = 0, upTotal = 0, downTotal = 0,
                connectedSince = 0, latencyMs = -1)
        }
    }

    /** اجرای یک کار به‌صورت انحصاری. کار دیگری در حال اجرا باشد، نادیده گرفته می‌شود. */
    private fun runTask(t: Task, block: suspend () -> Unit) {
        if (opMutex.isLocked) return
        opJob = scope.launch {
            opMutex.withLock {
                setTask(t, "")
                try { block() } finally { setTask(Task.NONE, "") }
            }
        }
    }

    // ════════════════════════════ کارها ════════════════════════════

    private suspend fun doScanIps(): List<CloudflareScanner.CleanIp> {
        setTask(Task.SCAN_IP, "اسکن IP تمیز کلادفلر…")
        val ips = engine.freshCleanIps { tested, found ->
            setTask(Task.SCAN_IP, "اسکن IP تمیز… $tested بررسی · $found تمیز")
        }
        _state.update { it.copy(cleanIps = ips, cleanIpCount = ips.size) }
        if (ips.isEmpty()) { toast("IP تمیزی پیدا نشد — اینترنت را بررسی و دوباره اسکن کنید"); return ips }
        // از هر اسکن فقط یک IP منتخب: پایدارترین پینگ مستقیم با اینترنت گوشی
        choosePick(ips)
        val s = _state.value
        toast("${ips.size} IP تمیز · منتخب: ${s.selectedIp}" + (if (s.selectedIpMs > 0) " (${s.selectedIpMs}ms ${s.selectedIpColo})" else " (پینگ مستقیم نداد)"))
        return ips
    }

    private suspend fun doRescan(): List<ServerEntry> {
        setTask(Task.RESCAN, "دریافت ساب‌ها…")
        val disc = engine.discover { p -> setTask(Task.RESCAN, p.message) } ?: run {
            toast("دریافت ساب‌ها ناموفق بود. اینترنت را بررسی کنید.")
            return _state.value.servers
        }
        _state.update { it.copy(configCount = disc.totalParsed, cleanIpCount = disc.cleanIps.size,
            cleanIps = if (it.cleanIps.isEmpty()) disc.cleanIps else it.cleanIps) }
        // سرور فعلی (اگر وصلیم) در لیست می‌ماند؛ بقیه کاملاً تازه
        val keep = listOfNotNull(_state.value.current.takeIf { _state.value.connected })
        // سرورهای «ساب‌های من» با ری‌اسکن پاک نمی‌شوند
        val mine = _state.value.servers.filter { it.source.startsWith("sub-") && it.latencyMs > 0 }
        var fresh = keep
        val want = HellboyEngine.TARGET_SERVERS - keep.size
        engine.findServers(disc, want, skipKeys = keep.map { it.serverKey }.toSet(),
            onFound = { e ->
                fresh = (fresh + e).distinctBy { it.serverKey }
                setTask(Task.RESCAN, "سرور سالم: ${fresh.size}/${HellboyEngine.TARGET_SERVERS}")
                publish(fresh + mine)
            },
            onState = { p -> setTask(Task.RESCAN, "${p.message} · سالم: ${fresh.size}/${HellboyEngine.TARGET_SERVERS}") },
            preferredIp = _state.value.selectedIp.ifEmpty { null })
        if (fresh.size <= keep.size) toast("سرور سالم جدیدی پیدا نشد")
        else toast("${fresh.size} سرور سالم آماده است")
        return _state.value.servers
    }

    private suspend fun doInject(): Int {
        if (_state.value.servers.isEmpty()) { toast("اول «ری‌اسکن» بزنید تا سرورها پیدا شوند"); return 0 }
        if (_state.value.selectedIp.isEmpty()) doScanIps()
        val sel = _state.value.selectedIp
        if (sel.isEmpty()) return 0
        // فقط «یک» IP منتخب — تا برنامه بین چند IP سردرگم نشود
        val ips = listOf(CloudflareScanner.CleanIp(sel, 443, _state.value.cleanIps.firstOrNull { it.ip == sel }?.tlsMs ?: 0))
        // ۱۰ سرور برتر (بقیه‌ی لیست دست‌نخورده می‌ماند)
        val all = _state.value.servers
        val top = all.sortedByDescending { it.score }.take(HellboyEngine.TARGET_SERVERS)
        setTask(Task.INJECT, "اینجکت $sel روی ${top.size} سرور برتر…")
        val outcome = engine.injectCleanIps(top, ips) { d, t ->
            setTask(Task.INJECT, "اینجکت و پینگ با اینترنت گوشی ($d/$t)")
        }
        val changed = outcome.changed
        val byKey = outcome.list.associateBy { it.serverKey }
        val list = all.map { byKey[it.serverKey] ?: it }
        publish(list)
        _state.update { it.copy(injectReport = outcome.report) }
        val eligible = list.count { it.canInject }

        // ── ریشه‌ی باگ: قبلاً فقط لیست عوض می‌شد و اتصال زنده روی IP قبلی می‌ماند ──
        val cur = _state.value.current
        var curMsg = ""
        if (_state.value.connected && cur != null) {
            val updated = list.firstOrNull { it.serverKey == cur.serverKey }
            when {
                !cur.canInject -> curMsg = " · سرور فعلی: ${cur.injectBlocker}؛ IP تمیز روی آن کار نمی‌کند — یک سرور «کلادفلر» انتخاب کنید"
                updated != null && updated.config.dialHost != cur.config.dialHost -> {
                    setTask(Task.INJECT, "اتصال دوباره با IP تمیز ${updated.config.dialHost}…")
                    curMsg = if (activate(updated)) " · اتصال فعلی روی ${updated.config.dialHost} رفت"
                             else " · اتصال با IP جدید برقرار نشد؛ IP قبلی حفظ شد"
                }
                updated != null && updated.config.dialHost == sel -> curMsg = " · سرور فعلی روی همین IP است"
                else -> curMsg = " · IP منتخب روی سرور فعلی پینگ نداد؛ آدرس قبلی حفظ شد"
            }
            DiagLogger.log("INJECT_CURRENT ${cur.config.host}${curMsg}")
        }
        val okN = outcome.report.count { it.ok }
        val failN = outcome.report.count { !it.ok && it.ip.isNotEmpty() }
        toast(when {
            eligible == 0 -> "هیچ‌کدام از سرورها پشت کلادفلر نیستند؛ «ری‌اسکن» بزنید"
            okN == 0 -> "$sel روی هیچ سروری پینگ نداد · IP دیگری انتخاب کنید (جزئیات در گزارش)"
            else -> "$sel روی $okN سرور پینگ داد" + (if (failN > 0) " · $failN ناموفق" else "") + (if (changed > 0) " · $changed سرور IP جدید گرفت" else "")
        } + curMsg)
        return changed
    }

    private suspend fun doConnectFastest() {
        _state.update { it.copy(needsPermission = false) }
        if (mineOnly) { doConnectMine(); return }
        DiagLogger.section("CONNECT fastest servers=${_state.value.servers.size} selectedIp=${_state.value.selectedIp.ifEmpty { "-" }} net=${DiagLogger.networkSummary(app)}")
        // ۱) لیست خالی → ساخت: ری‌اسکن ← اسکن IP ← اینجکت
        if (_state.value.servers.isEmpty()) {
            setState(Phase.TESTING, "یافتن سرورهای سالم…")
            doRescan()
            if (_state.value.servers.isEmpty()) { setState(Phase.FAILED, "سرور سالمی پیدا نشد. دوباره تلاش کنید."); return }
            doInject()
        }
        // ۲) همه هم‌زمان تست، سریع‌ترین اول
        setTask(Task.CONNECT, "یافتن سریع‌ترین سرور…")
        setState(Phase.TESTING, "یافتن سریع‌ترین سرور…")
        var alive = engine.retestAll(_state.value.servers) { d, a ->
            setTask(Task.CONNECT, "تست سرورها $d/${_state.value.servers.size} · سالم: $a")
        }
        if (alive.isEmpty()) {
            // لیست مرده → ساخت دوباره
            setState(Phase.TESTING, "سرورهای قبلی ضعیف شده‌اند؛ لیست تازه…")
            doRescan(); doInject()
            alive = _state.value.servers
        } else publish(alive)
        // ۳) اتصال به ترتیب سرعت
        for (e in alive.sortedBy { it.latencyMs }) {
            if (!currentCoroutineContext().isActive) return
            setTask(Task.CONNECT, "اتصال به ${label(e)} (${e.latencyMs}ms)…")
            setState(Phase.CONNECTING, "اتصال به ${label(e)}…")
            if (activate(e)) return
            if (_state.value.needsPermission) return
        }
        setState(Phase.FAILED, "اتصال ممکن نشد. «ری‌اسکن» را بزنید و دوباره وصل شوید.")
    }

    // ════════════════════════════ اتصال ════════════════════════════

    /** سرویس را روی این سرور بالا می‌آورد و صحت جریان داده را می‌سنجد. */
    private suspend fun activate(e: ServerEntry): Boolean {
        val prev = if (_state.value.connected) _state.value.current else null
        switching = true
        try {
            if (tryActivate(e)) return true
        } finally { switching = false }
        if (prev != null) {
            CurrentConfigHolder.save(app, prev)
            HellboyVpnService.start(app)
            withTimeoutOrNull(20_000) {
                HellboyVpnService.status.first { (it.state == SvcState.UP && it.serverId == prev.id) || it.state == SvcState.FAILED }
            }
        } else {
            HellboyVpnService.stop(app)
            CurrentConfigHolder.clear(app)
        }
        return false
    }

    private suspend fun tryActivate(e: ServerEntry): Boolean {
        DiagLogger.log("ACTIVATE start ${e.config.scheme} ${e.config.host}:${e.config.port} dial=${e.config.dialHost}:${e.config.dialPort} " +
            "sni=${e.config.sniHost} net=${e.config.query.toMap()["type"] ?: "tcp"} variant=${e.variantId ?: "-"} frag=${e.useFrag} testMs=${e.latencyMs} psiphon=${settings.psiphonEnabled}")
        CurrentConfigHolder.save(app, e)
        HellboyVpnService.start(app)
        // با سایفون، اولین اتصال تا ~۷۵ ثانیه طول می‌کشد (سایفون قبل از تونل سیستم وصل می‌شود)
        val wait = if (settings.psiphonEnabled && !_state.value.connected) 100_000L else 20_000L
        val st = withTimeoutOrNull(wait) {
            HellboyVpnService.status.first {
                (it.state == SvcState.UP && it.serverId == e.id) || it.state == SvcState.FAILED
            }
        }
        if (st == null || st.state != SvcState.UP || st.serverId != e.id) {
            DiagLogger.log("ACTIVATE fail ${e.config.host} -> ${st?.state} ${st?.message}")
            val err = st?.error
            if (err is SvcError.Tun && (err.kind == TunFailure.PERMISSION_REVOKED ||
                    android.net.VpnService.prepare(app) != null)) {
                _state.update { it.copy(needsPermission = true, message = err.userMessage) }
            }
            return false
        }
        var lat = -1L
        for (i in 0 until 3) {
            lat = HealthTester.tunnelLatency(HellboyVpnService.activeSocksPort, 6_000)
            if (lat > 0) break
            delay(500)
        }
        if (lat <= 0) { DiagLogger.log("ACTIVATE no_data ${e.config.host} (service up but no traffic through tunnel)"); return false }
        DiagLogger.log("ACTIVATE ok ${e.config.host} dial=${e.config.dialHost} tunnelLatency=${lat}ms port=${HellboyVpnService.activeSocksPort}")
        val b = Tun2Socks.bytes()
        baseUp = b?.first ?: 0; baseDown = b?.second ?: 0
        val now = System.currentTimeMillis()
        val since = if (_state.value.connected && _state.value.connectedSince > 0) _state.value.connectedSince else now
        _state.update { it.copy(phase = Phase.CONNECTED, message = "", connected = true, current = e,
            connectedSince = since, latencyMs = lat) }
        startStats()
        if (watchJob?.isActive != true) startWatchdog()
        startPoolMonitor()
        return true
    }

    // ════════════════════════════ خودکار ════════════════════════════

    private fun startStats() {
        statsJob?.cancel()
        statsJob = scope.launch {
            var lastUp = baseUp; var lastDown = baseDown
            while (isActive) {
                delay(1_000)
                val b = Tun2Socks.bytes() ?: continue
                _state.update {
                    it.copy(upBps = (b.first - lastUp).coerceAtLeast(0), downBps = (b.second - lastDown).coerceAtLeast(0),
                        upTotal = (b.first - baseUp).coerceAtLeast(0), downTotal = (b.second - baseDown).coerceAtLeast(0))
                }
                lastUp = b.first; lastDown = b.second
            }
        }
    }

    private fun startWatchdog() {
        watchJob?.cancel()
        watchJob = scope.launch {
            var fails = 0
            while (isActive) {
                delay(10_000)
                if (!_state.value.connected || switching) continue
                val lat = HealthTester.tunnelLatency(HellboyVpnService.activeSocksPort, 6_000)
                if (lat > 0) { fails = 0; _state.update { it.copy(latencyMs = lat) }; continue }
                if (psiphonOnlyUp) continue // سایفون خودش دوباره وصل می‌شود
                if (++fails >= 2 && settings.autoFailover) { fails = 0; failover() }
            }
        }
    }

    /** هر چند دقیقه: سرورهای مرده از لیست حذف؛ اگر ≤ WEAK_POOL ماند، ری‌اسکن + اینجکت خودکار. */
    private fun startPoolMonitor() {
        if (poolJob?.isActive == true) return
        poolJob = scope.launch {
            while (isActive) {
                delay(POOL_CHECK_MS)
                if (!_state.value.connected || switching || opMutex.isLocked) continue
                val current = _state.value.servers
                if (current.isEmpty()) continue
                // اینترنت خود گوشی قطع است: تست/ری‌اسکن بی‌فایده است (لاگ: یک ساعت دریافت ساب ناموفق)
                if (internetDown()) { DiagLogger.log("POOL_CHECK skipped — internet down"); continue }
                opMutex.withLock {
                    val alive = engine.retestAll(current)
                    val keepCur = _state.value.current?.let { c -> current.firstOrNull { it.serverKey == c.serverKey } }
                    publish((alive + listOfNotNull(keepCur)).distinctBy { it.serverKey })
                    DiagLogger.log("POOL_CHECK alive=${alive.size}/${current.size}")
                    if (alive.size <= WEAK_POOL) {
                        setTask(Task.RESCAN, "تازه‌سازی خودکار لیست…")
                        try {
                            if (mineOnly) for (sub in _state.value.mySubs) doRefreshSub(sub.id, fetch = true)
                            else { doRescan(); doInject() }
                        } finally { setTask(Task.NONE, "") }
                    }
                }
            }
        }
    }

    /** قطعی: سریع‌ترین سرور سالم دیگر؛ اگر هیچ نبود ری‌اسکن + اینجکت و دوباره. */
    private suspend fun failover() {
        val cur = _state.value.current
        _state.update { it.copy(message = "قطعی تشخیص داده شد — تعویض سرور…") }
        DiagLogger.log("FAILOVER from ${cur?.config?.host}")
        if (internetDown()) {
            if (!netDownLogged) { netDownLogged = true; DiagLogger.log("NET_DOWN underlying internet unreachable (1.1.1.1/8.8.8.8) — waiting, no failover/rescan") }
            _state.update { it.copy(message = "اینترنت گوشی قطع است — منتظر بازگشت شبکه…") }
            return
        }
        if (netDownLogged) { netDownLogged = false; DiagLogger.log("NET_UP internet back") }
        val others = _state.value.servers.filter { it.serverKey != cur?.serverKey && (!mineOnly || it.source.startsWith("sub-")) }
        val alive = engine.retestAll(others).sortedBy { it.latencyMs }
        for (e in alive) {
            if (!currentCoroutineContext().isActive) return
            if (activate(e)) { _state.update { it.copy(message = "") }; return }
        }
        if (opMutex.isLocked) return
        opMutex.withLock {
            setTask(Task.RESCAN, "همه‌ی سرورها ضعیف شدند؛ لیست تازه…")
            try {
                if (mineOnly) for (sub in _state.value.mySubs) doRefreshSub(sub.id, fetch = true)
                else { doRescan(); doInject() }
            } finally { setTask(Task.NONE, "") }
        }
        for (e in _state.value.servers.filter { !mineOnly || it.source.startsWith("sub-") }.sortedBy { it.latencyMs }) { if (activate(e)) return }
        markDisconnected("سرور سالمی پیدا نشد")
        setState(Phase.FAILED, "سرور سالمی پیدا نشد")
    }

    /** تغییر شبکه‌ی زیرین (وای‌فای ↔ سیم‌کارت): سلامت تونل فوراً سنجیده می‌شود. */
    private fun registerNetworkKick() {
        try {
            val cm = app.getSystemService(android.net.ConnectivityManager::class.java)
            val req = android.net.NetworkRequest.Builder()
                .addCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .removeCapability(android.net.NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
                .build()
            cm.registerNetworkCallback(req, object : android.net.ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: android.net.Network) { kickCheck() }
            })
        } catch (_: Exception) {}
    }

    @Volatile private var kickBusy = false
    private fun kickCheck() {
        if (kickBusy || !_state.value.connected) return
        kickBusy = true
        scope.launch {
            try {
                delay(2_000)
                if (!_state.value.connected || switching) return@launch
                var lat = HealthTester.tunnelLatency(HellboyVpnService.activeSocksPort, 5_000)
                if (lat <= 0) { delay(1_500); lat = HealthTester.tunnelLatency(HellboyVpnService.activeSocksPort, 5_000) }
                if (lat <= 0 && settings.autoFailover && !psiphonOnlyUp) failover()
            } finally { kickBusy = false }
        }
    }

    // ════════════════════════════ کمکی ════════════════════════════

    private fun markDisconnected(msg: String) {
        watchJob?.cancel(); statsJob?.cancel(); poolJob?.cancel()
        _state.update { it.copy(phase = Phase.IDLE, message = msg, connected = false, current = null,
            upBps = 0, downBps = 0, connectedSince = 0) }
    }

    /** لیست را مرتب (سریع‌ترین اول)، به ۱۰ محدود، در state و دیسک می‌نویسد. */
    private fun publish(list: List<ServerEntry>) {
        // سقف لیست ۳۰ (کانفیگ‌های ویرایش/واردشده‌ی کاربر نباید با کپ ۱۰ حذف شوند)؛ اینجکت روی ۱۰ برتر
        val sorted = list.distinctBy { it.serverKey }.sortedByDescending { it.score }
            .take(MAX_LIST)
        _state.update { it.copy(servers = sorted) }
        engine.saveServers(sorted)
    }

    private fun setTask(t: Task, msg: String) { _state.update { it.copy(task = t, taskMsg = msg) } }

    /** پیام نتیجه‌ی یک کار (وقتی وصلیم فاز اصلی را عوض نمی‌کند). */
    private fun toast(msg: String) { _state.update { it.copy(message = msg) } }

    private fun setState(ph: Phase, msg: String) {
        _state.update { if (it.connected && ph != Phase.CONNECTED) it.copy(message = msg) else it.copy(phase = ph, message = msg) }
    }

    private fun isValidIpv4(s: String): Boolean {
        val p = s.split(".")
        return p.size == 4 && p.all { x -> x.toIntOrNull()?.let { it in 0..255 } == true && x.length <= 3 }
    }

    private const val MAX_LIST = 30

    @Volatile private var netDownLogged = false

    /** اینترنت زیرین گوشی (خارج از تونل) قطع است؟ دو مقصد مستقل TCP. */
    private fun internetDown(): Boolean =
        HealthTester.tcpPing("1.1.1.1", 443, 2_500) < 0 && HealthTester.tcpPing("8.8.8.8", 443, 2_500) < 0 &&
            HealthTester.tcpPing("9.9.9.9", 443, 2_500) < 0

    private fun label(e: ServerEntry) = e.config.tag.ifEmpty { e.config.host }.take(32)

    private const val WEAK_POOL = 5
    private const val POOL_CHECK_MS = 4 * 60_000L
}

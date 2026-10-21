package com.helboy.vpnclub.ui.proto.engine

import dev.valid7996.narcicgeteway.domain.config.DohPresets
import dev.valid7996.narcicgeteway.domain.config.SingBoxTuning
import dev.valid7996.narcicgeteway.domain.config.SniSpoofConfig
import dev.valid7996.narcicgeteway.domain.model.AppSettings
import dev.valid7996.narcicgeteway.domain.model.FragmentPackets
import dev.valid7996.narcicgeteway.domain.model.HevLogLevel
import dev.valid7996.narcicgeteway.domain.model.ImportTargetMode
import dev.valid7996.narcicgeteway.domain.model.LogLevel
import dev.valid7996.narcicgeteway.domain.model.NotifChip
import dev.valid7996.narcicgeteway.domain.model.OutboundDomainResolve
import dev.valid7996.narcicgeteway.domain.model.DomainStrategy
import dev.valid7996.narcicgeteway.domain.model.RoutingMode
import dev.valid7996.narcicgeteway.domain.model.RunMode
import dev.valid7996.narcicgeteway.domain.model.SingBoxMuxProtocol
import dev.valid7996.narcicgeteway.domain.model.SingBoxStack
import dev.valid7996.narcicgeteway.domain.model.VpnBypassLan
import dev.valid7996.narcicgeteway.domain.model.VpnInterfaceAddress
import dev.valid7996.narcicgeteway.domain.model.XudpQuic

/*
 * The table behind EngineSettingsPage. Every user-facing field of AppSettings is described here once: what it is
 * called, how it is edited, when it is greyed out, and how it is written back. The page just walks the table, and
 * the unit tests check the table against AppSettings itself, so a new engine setting cannot be forgotten silently.
 */

private val ALWAYS: (AppSettings) -> Boolean = { true }

/** One editable thing on the page. */
sealed interface EngineControl {
    /** Stable id; for single-field controls it is the AppSettings property name. Used for test tags. */
    val id: String
    val title: String
    val hint: String?

    /** AppSettings property names this control reads and writes. */
    val covers: Set<String>
    val enabledWhen: (AppSettings) -> Boolean
    val visibleWhen: (AppSettings) -> Boolean

    /** [current] with this control's fields put back to the ones in [defaults]. */
    fun restore(current: AppSettings, defaults: AppSettings): AppSettings
}

class SwitchControl(
    override val id: String,
    override val title: String,
    override val hint: String? = null,
    val get: (AppSettings) -> Boolean,
    val set: (AppSettings, Boolean) -> AppSettings,
    override val enabledWhen: (AppSettings) -> Boolean = ALWAYS,
    override val visibleWhen: (AppSettings) -> Boolean = ALWAYS,
    override val covers: Set<String> = setOf(id),
) : EngineControl {
    override fun restore(current: AppSettings, defaults: AppSettings) = set(current, get(defaults))
}

/** One of N. Options are (id, label); the id is what [get] returns and [set] receives. */
class ChoiceControl(
    override val id: String,
    override val title: String,
    override val hint: String? = null,
    val options: List<Pair<String, String>>,
    val get: (AppSettings) -> String,
    val set: (AppSettings, String) -> AppSettings,
    override val enabledWhen: (AppSettings) -> Boolean = ALWAYS,
    override val visibleWhen: (AppSettings) -> Boolean = ALWAYS,
    override val covers: Set<String> = setOf(id),
    private val restoreOverride: ((AppSettings, AppSettings) -> AppSettings)? = null,
) : EngineControl {
    override fun restore(current: AppSettings, defaults: AppSettings) =
        restoreOverride?.invoke(current, defaults) ?: set(current, get(defaults))
}

/** Free text. [validate] returns a message when the text must not be saved; blank text is checked by it too. */
class TextControl(
    override val id: String,
    override val title: String,
    override val hint: String? = null,
    val get: (AppSettings) -> String,
    val set: (AppSettings, String) -> AppSettings,
    val placeholder: String = "",
    val validate: (String) -> String? = { null },
    val password: Boolean = false,
    val multiline: Boolean = false,
    val monospace: Boolean = false,
    /** Ready-made values shown as chips under the field: (value, label). */
    val presets: List<Pair<String, String>> = emptyList(),
    override val enabledWhen: (AppSettings) -> Boolean = ALWAYS,
    override val visibleWhen: (AppSettings) -> Boolean = ALWAYS,
    override val covers: Set<String> = setOf(id),
) : EngineControl {
    override fun restore(current: AppSettings, defaults: AppSettings) = set(current, get(defaults))
}

/** A whole number between [min] and [max]; anything else is shown as an error and not saved. */
class NumberControl(
    override val id: String,
    override val title: String,
    override val hint: String? = null,
    val min: Int,
    val max: Int,
    val unit: String = "",
    val get: (AppSettings) -> Int,
    val set: (AppSettings, Int) -> AppSettings,
    val extraCheck: (Int) -> String? = { null },
    override val enabledWhen: (AppSettings) -> Boolean = ALWAYS,
    override val visibleWhen: (AppSettings) -> Boolean = ALWAYS,
    override val covers: Set<String> = setOf(id),
) : EngineControl {
    override fun restore(current: AppSettings, defaults: AppSettings) = set(current, get(defaults))
}

/** Read-only explanation. [id] still names the AppSettings field it speaks for, if any. */
class InfoControl(
    override val id: String,
    override val title: String,
    override val hint: String? = null,
    override val covers: Set<String> = emptySet(),
    override val visibleWhen: (AppSettings) -> Boolean = ALWAYS,
) : EngineControl {
    override val enabledWhen: (AppSettings) -> Boolean = ALWAYS
    override fun restore(current: AppSettings, defaults: AppSettings) = current
}

class EngineSection(
    val id: String,
    val title: String,
    val hint: String,
    /** True when a running connection keeps the old values until it is reconnected. */
    val nextConnect: Boolean,
    val controls: List<EngineControl>,
) {
    /** Every AppSettings field this section owns. */
    val covers: Set<String> get() = controls.flatMapTo(linkedSetOf()) { it.covers }

    /** [current] with every control of this section put back to [defaults]. */
    fun restore(current: AppSettings, defaults: AppSettings): AppSettings =
        controls.fold(current) { acc, c -> c.restore(acc, defaults) }
}

object EngineDefaults {
    /** What a fresh install starts with (the repository seeds ZepTun and IPv6 on, the data class does not). */
    val settings: AppSettings = AppSettings(useZepTun = true, useHevTun = false, enableIpv6 = true)
}

private fun sw(
    id: String, title: String, hint: String? = null,
    get: (AppSettings) -> Boolean, set: (AppSettings, Boolean) -> AppSettings,
    enabled: (AppSettings) -> Boolean = ALWAYS, visible: (AppSettings) -> Boolean = ALWAYS,
) = SwitchControl(id, title, hint, get, set, enabled, visible)

private fun <E : Enum<E>> choice(
    id: String, title: String, hint: String? = null,
    entries: List<E>, label: (E) -> String,
    get: (AppSettings) -> E, set: (AppSettings, E) -> AppSettings,
    enabled: (AppSettings) -> Boolean = ALWAYS, visible: (AppSettings) -> Boolean = ALWAYS,
) = ChoiceControl(
    id = id, title = title, hint = hint,
    options = entries.map { it.name to label(it) },
    get = { get(it).name },
    set = { s, v -> entries.firstOrNull { it.name == v }?.let { set(s, it) } ?: s },
    enabledWhen = enabled, visibleWhen = visible,
)

private fun strChoice(
    id: String, title: String, hint: String? = null,
    options: List<Pair<String, String>>,
    get: (AppSettings) -> String, set: (AppSettings, String) -> AppSettings,
    enabled: (AppSettings) -> Boolean = ALWAYS, visible: (AppSettings) -> Boolean = ALWAYS,
) = ChoiceControl(id, title, hint, options, get, set, enabled, visible)

private fun txt(
    id: String, title: String, hint: String? = null,
    get: (AppSettings) -> String, set: (AppSettings, String) -> AppSettings,
    placeholder: String = "", validate: (String) -> String? = { null },
    password: Boolean = false, multiline: Boolean = false, monospace: Boolean = false,
    presets: List<Pair<String, String>> = emptyList(),
    enabled: (AppSettings) -> Boolean = ALWAYS, visible: (AppSettings) -> Boolean = ALWAYS,
) = TextControl(id, title, hint, get, set, placeholder, validate, password, multiline, monospace, presets, enabled, visible)

private fun num(
    id: String, title: String, hint: String? = null, min: Int, max: Int, unit: String = "",
    get: (AppSettings) -> Int, set: (AppSettings, Int) -> AppSettings,
    extraCheck: (Int) -> String? = { null },
    enabled: (AppSettings) -> Boolean = ALWAYS, visible: (AppSettings) -> Boolean = ALWAYS,
) = NumberControl(id, title, hint, min, max, unit, get, set, extraCheck, enabled, visible)

private val vpnOnly: (AppSettings) -> Boolean = { it.isVpnMode }

object EngineSettingsTable {
    /** Property names deliberately without a control, with the reason (shown in the report and checked by tests). */
    val excluded: Map<String, String> = linkedMapOf(
        // Narcic UI appearance: Hellboy has its own appearance page.
        "themeMode" to "ظاهر: صفحه‌ی ظاهر هلبوی",
        "dynamicColor" to "ظاهر: صفحه‌ی ظاهر هلبوی",
        "accentColor" to "ظاهر: صفحه‌ی ظاهر هلبوی",
        "amoledBlack" to "ظاهر: صفحه‌ی ظاهر هلبوی",
        "language" to "ظاهر: هلبوی فقط فارسی است",
        "customDownloadColor" to "ظاهر",
        "customUploadColor" to "ظاهر",
        "customDownloadTileColor" to "ظاهر",
        "customUploadTileColor" to "ظاهر",
        "customConnectColor" to "ظاهر",
        "customConnectedColor" to "ظاهر",
        "customTapHintColor" to "ظاهر",
        "customServerCardColor" to "ظاهر",
        "customServerActiveColor" to "ظاهر",
        "customConfigCardColor" to "ظاهر",
        "customConfigCardTextColor" to "ظاهر",
        "connectButtonStyle" to "ظاهر: دکمه‌ی اتصال هلبوی سبک خودش را دارد",
        "navBarStyle" to "ظاهر",
        "themeProfileId" to "ظاهر",
        "cardCornerStyle" to "ظاهر",
        "listDensity" to "ظاهر",
        "showServerPing" to "ظاهر کارت سرور نارسیک",
        "showServerUsage" to "ظاهر کارت سرور نارسیک",
        "monospaceAddress" to "ظاهر",
        "screenTransition" to "ظاهر",
        "homeAfterSelect" to "ناوبری نارسیک؛ هلبوی تب‌های خودش را دارد",
        "showTrafficTiles" to "ظاهر",
        "trafficTilesAboveHero" to "ظاهر",
        "trafficTileSize" to "ظاهر",
        "uiFontScale" to "ظاهر",
        "trafficCardStyle" to "ظاهر",
        "doubleColumnDisplay" to "ظاهر",
        "serverGridColumns" to "ظاهر",
        "animationMode" to "ظاهر",
        "groupAllDisplay" to "ظاهر",
        "reduceMotion" to "ظاهر",
        "renderingMode" to "ظاهر / دسکتاپ",
        "onboardingVersion" to "وضعیت داخلی خوش‌آمدگویی نارسیک",
        // Internal bookkeeping nobody edits.
        "lastUpdateCheckMs" to "وضعیت داخلی",
        "dismissedUpdateVersion" to "وضعیت داخلی",
        "dismissedUpdateAtMs" to "وضعیت داخلی",
        "successfulConnections" to "وضعیت داخلی",
        "ratePromptLastShownMs" to "وضعیت داخلی",
        "rateNeverAsk" to "وضعیت داخلی",
        "routingMigrated" to "وضعیت داخلی",
        "routingSeeded" to "وضعیت داخلی",
        // Owned by other screens.
        "ai" to "صفحه‌ی دستیار هوشمند (AiAssistantPage)",
        "customProxyRules" to "ویرایشگر قوانین مسیریابی (بخش مسیریابی)",
        "customDirectRules" to "ویرایشگر قوانین مسیریابی (بخش مسیریابی)",
        "customBlockRules" to "ویرایشگر قوانین مسیریابی (بخش مسیریابی)",
        "rulesets" to "ویرایشگر قوانین مسیریابی (بخش مسیریابی)",
        "perAppProxyEnabled" to "صفحه‌ی پروکسی هر برنامه",
        "perAppBypassMode" to "صفحه‌ی پروکسی هر برنامه",
        "perAppPackages" to "صفحه‌ی پروکسی هر برنامه",
        "geoFilesSource" to "صفحه‌ی فایل‌های geo",
        "torEnabledBridges" to "فهرست پل‌های Tor را مرورگر پل‌ها می‌نویسد، نه تایپ دستی",
    )

    val sections: List<EngineSection> by lazy { build() }

    /** Every AppSettings property that has a control. */
    val covered: Set<String> get() = sections.flatMapTo(linkedSetOf()) { it.covers }

    fun section(id: String): EngineSection? = sections.firstOrNull { it.id == id }

    private fun build(): List<EngineSection> = listOf(
        EngineSection(
            "mode", "حالت اتصال", "VPN یا فقط پروکسی، و رفتار هنگام قطع و روشن شدن.", nextConnect = true,
            controls = listOf(
                strChoice(
                    "runMode", "حالت اجرا",
                    "در حالت «فقط پروکسی» تونل سراسری ساخته نمی‌شود و برنامه‌ها باید دستی به SOCKS محلی وصل شوند.",
                    options = listOf(RunMode.Vpn.name to "VPN", RunMode.ProxyOnly.name to "فقط پروکسی"),
                    get = { it.runMode.name },
                    set = { s, v -> RunMode.entries.firstOrNull { it.name == v }?.let { s.copy(runMode = it) } ?: s },
                ),
                sw("killSwitch", "قطع اضطراری (Kill switch)", "اگر VPN قطع شد، به جای نشت ترافیک بدون تونل، همه چیز را مسدود کن.",
                    { it.killSwitch }, { s, v -> s.copy(killSwitch = v) }),
                sw("autoConnectOnBoot", "اتصال خودکار پس از روشن شدن گوشی",
                    "هنگام بوت به سرور انتخاب‌شده وصل می‌شود؛ ممکن است همیشه موفق نباشد.",
                    { it.autoConnectOnBoot }, { s, v -> s.copy(autoConnectOnBoot = v) }),
            ),
        ),
        EngineSection(
            "vpn", "VPN و شبکه", "آدرس اینترفیس، MTU، IPv6 و رفتار با شبکه‌ی محلی.", nextConnect = true,
            controls = listOf(
                sw("enableIpv6", "IPv6", "آدرس و مسیرهای IPv6 به اینترفیس VPN اضافه می‌شود.",
                    { it.enableIpv6 }, { s, v -> s.copy(enableIpv6 = v) }),
                sw("preferIpv6", "ترجیح IPv6", "هنگام پیدا کردن آدرس دامنه‌ها، آدرس IPv6 را ترجیح بده.",
                    { it.preferIpv6 }, { s, v -> s.copy(preferIpv6 = v) }, enabled = { it.enableIpv6 }),
                choice(
                    "vpnInterfaceAddress", "آدرس اینترفیس VPN", "اگر با شبکه‌ی محلی‌تان برخورد دارد، یکی دیگر را انتخاب کنید.",
                    VpnInterfaceAddress.entries, { it.label },
                    { it.vpnInterfaceAddress }, { s, v -> s.copy(vpnInterfaceAddress = v) }, enabled = vpnOnly,
                ),
                num(
                    "vpnMtu", "MTU", "پیش‌فرض ۱۵۰۰. برای اندازه‌گیری خودکار از صفحه‌ی «بهینه‌ساز MTU» استفاده کنید.",
                    min = MtuLogic.MIN_ALLOWED, max = MtuLogic.MAX_ALLOWED, unit = "بایت",
                    get = { it.vpnMtu }, set = { s, v -> s.copy(vpnMtu = v) }, enabled = vpnOnly,
                ),
                choice(
                    "vpnBypassLan", "شبکه‌ی محلی (LAN)", "آدرس‌های داخل شبکه‌ی خانه مستقیم بروند یا از تونل بگذرند.",
                    VpnBypassLan.entries,
                    { when (it) {
                        VpnBypassLan.FollowConfig -> "طبق کانفیگ"
                        VpnBypassLan.Bypass -> "مستقیم"
                        VpnBypassLan.NotBypass -> "از تونل"
                    } },
                    { it.vpnBypassLan }, { s, v -> s.copy(vpnBypassLan = v) }, enabled = vpnOnly,
                ),
            ),
        ),
        EngineSection(
            "dns", "DNS", "سرور DNS داخل تونل، DNS مستقیم، DoH و DNS محلی/جعلی.", nextConnect = true,
            controls = listOf(
                sw("localDnsEnabled", "DNS محلی هسته", "DNS را خود هسته مدیریت می‌کند؛ برای دور زدن LAN و آدرس‌های محلی توصیه می‌شود.",
                    { it.localDnsEnabled }, { s, v -> s.copy(localDnsEnabled = v) }, enabled = vpnOnly),
                sw("fakeDnsEnabled", "DNS جعلی (FakeDNS)", "برای هر پرس‌وجو یک IP مصنوعی برمی‌گرداند. سریع‌تر است ولی بعضی برنامه‌ها خراب می‌شوند.",
                    { it.fakeDnsEnabled }, { s, v -> s.copy(fakeDnsEnabled = v) }, enabled = { it.isVpnMode && it.localDnsEnabled }),
                txt(
                    "vpnDns", "DNS اینترفیس VPN", "سروری که اندروید برای برنامه‌ها می‌شناسد؛ وقتی DNS محلی روشن است نیاز نیست.",
                    { it.vpnDns }, { s, v -> s.copy(vpnDns = v.trim()) },
                    placeholder = "1.1.1.1", validate = SettingsValidation::checkNotBlank,
                    presets = listOf("1.1.1.1" to "Cloudflare", "8.8.8.8" to "Google", "9.9.9.9" to "Quad9"),
                    enabled = { it.isVpnMode && !it.localDnsEnabled },
                ),
                txt(
                    "remoteDns", "DNS خارجی (از داخل تونل)", "UDP، TCP، HTTPS (DoH) یا QUIC. یکی از DoHهای آماده را بزنید یا آدرس خودتان را بنویسید.",
                    { it.remoteDns }, { s, v -> s.copy(remoteDns = v.trim()) },
                    placeholder = "https://cloudflare-dns.com/dns-query", validate = SettingsValidation::checkNotBlank, monospace = true,
                    presets = DohPresets.SERVERS.map { it.url to it.name },
                ),
                txt(
                    "directDns", "DNS مستقیم", "برای دامنه‌های داخلی که از تونل رد نمی‌شوند.",
                    { it.directDns }, { s, v -> s.copy(directDns = v.trim()) },
                    placeholder = "223.5.5.5", validate = SettingsValidation::checkNotBlank,
                    presets = listOf("223.5.5.5" to "AliDNS", "8.8.8.8" to "Google", "178.22.122.100" to "شکن"),
                ),
                txt(
                    "dnsHosts", "نگاشت دستی دامنه‌ها (Hosts)", "قالب: دامنه:آدرس، جداشده با ویرگول. مثلا example.com:1.2.3.4",
                    { it.dnsHosts }, { s, v -> s.copy(dnsHosts = v) }, placeholder = "domain:address,domain2:address2", monospace = true,
                ),
                choice(
                    "outboundDomainResolve", "تفکیک دامنه‌ی سرور", "وقتی آدرس سرور دامنه است، چه زمانی و چگونه به IP تبدیل شود.",
                    OutboundDomainResolve.entries,
                    { when (it) {
                        OutboundDomainResolve.DoNotResolve -> "تبدیل نکن"
                        OutboundDomainResolve.ResolveAndAddToHosts -> "تبدیل و ثبت در Hosts"
                        OutboundDomainResolve.ResolveAndReplace -> "تبدیل و جایگزینی"
                    } },
                    { it.outboundDomainResolve }, { s, v -> s.copy(outboundDomainResolve = v) },
                ),
            ),
        ),
        EngineSection(
            "dnsAdv", "DNS پیشرفته (تونل DNS)", "این گزینه‌ها روی پروفایل‌های DNSTT و VayDNS اعمال می‌شوند و بر تنظیم خود پروفایل غالب‌اند.",
            nextConnect = true,
            controls = listOf(
                sw("dnsGlobalResolverEnabled", "فهرست رزالور سراسری", "یک فهرست ثابت رزالور را روی همه‌ی پروفایل‌های تونل DNS اعمال کن.",
                    { it.dnsGlobalResolverEnabled }, { s, v -> s.copy(dnsGlobalResolverEnabled = v) }),
                txt("dnsGlobalResolvers", "آی‌پی رزالورها", null,
                    { it.dnsGlobalResolvers }, { s, v -> s.copy(dnsGlobalResolvers = v) },
                    placeholder = "8.8.8.8, 1.1.1.1", monospace = true, enabled = { it.dnsGlobalResolverEnabled }),
                sw("dnsPoolEnabled", "استخر رزالور", "در هر اتصال خودکار سریع‌ترین رزالورها را انتخاب کن.",
                    { it.dnsPoolEnabled }, { s, v -> s.copy(dnsPoolEnabled = v) }),
                txt("dnsPoolText", "فهرست استخر", "هر خط یک رزالور.",
                    { it.dnsPoolText }, { s, v -> s.copy(dnsPoolText = v) },
                    placeholder = "8.8.8.8\n1.1.1.1", multiline = true, monospace = true, enabled = { it.dnsPoolEnabled }),
                sw("dnsPoolFullVerification", "بررسی کامل رزالورها", "کندتر است ولی رزالورهای خراب را دقیق‌تر کنار می‌گذارد.",
                    { it.dnsPoolFullVerification }, { s, v -> s.copy(dnsPoolFullVerification = v) }, enabled = { it.dnsPoolEnabled }),
                strChoice("remoteDnsMode", "رزالور خارجی", null,
                    listOf("default" to "پیش‌فرض", "custom" to "سفارشی"),
                    { it.remoteDnsMode }, { s, v -> s.copy(remoteDnsMode = v) }),
                txt("remoteDnsPrimary", "رزالور اصلی", null,
                    { it.remoteDnsPrimary }, { s, v -> s.copy(remoteDnsPrimary = v.trim()) }, placeholder = "9.9.9.9",
                    visible = { it.remoteDnsMode == "custom" }),
                txt("remoteDnsFallback", "رزالور پشتیبان", null,
                    { it.remoteDnsFallback }, { s, v -> s.copy(remoteDnsFallback = v.trim()) }, placeholder = "8.8.8.8",
                    visible = { it.remoteDnsMode == "custom" }),
                strChoice("dnsWorkerMode", "تعداد کارگر DNS", "هر پرس‌وجو جدا، یا تعداد ثابتی کارگر هم‌زمان.",
                    listOf("per_query" to "به ازای هر پرس‌وجو", "two" to "۲", "three" to "۳", "five" to "۵"),
                    { it.dnsWorkerMode }, { s, v -> s.copy(dnsWorkerMode = v) }),
                InfoControl("preventDnsFallback", "جلوگیری از نشت DNS",
                    "همیشه روشن است و قابل خاموش کردن نیست؛ اگر رزالورها جواب ندهند، DNS سیستم استفاده نمی‌شود.",
                    covers = setOf("preventDnsFallback")),
            ),
        ),
        EngineSection(
            "tun", "موتور TUN", "چه چیزی بسته‌های اندروید را به اتصال‌های پروکسی‌شده تبدیل کند.", nextConnect = true,
            controls = listOf(
                ChoiceControl(
                    id = "tunEngine", title = "موتور TUN",
                    hint = "Xray-TUN: تونل خود هسته. Hev: hev-socks5-tunnel. ZepTun: اتصال‌های آماده نگه می‌دارد و مصرف حافظه‌اش کران‌دار است.",
                    options = TunEngine.entries.map { it.name to it.label },
                    get = { TunEngine.of(it).name },
                    set = { s, v -> TunEngine.entries.firstOrNull { it.name == v }?.applyTo(s) ?: s },
                    enabledWhen = vpnOnly,
                    covers = setOf("useHevTun", "useZepTun"),
                    restoreOverride = { cur, d -> cur.copy(useHevTun = d.useHevTun, useZepTun = d.useZepTun) },
                ),
                choice("hevTunLogLevel", "سطح لاگ Hev/ZepTun", null,
                    HevLogLevel.entries, { it.value },
                    { it.hevTunLogLevel }, { s, v -> s.copy(hevTunLogLevel = v) },
                    enabled = vpnOnly, visible = { it.useHevTun }),
                txt("hevTunRwTimeout", "زمان‌بندی خواندن/نوشتن Hev", "ثانیه، به صورت tcp,udp",
                    { it.hevTunRwTimeout }, { s, v -> s.copy(hevTunRwTimeout = v.trim()) },
                    placeholder = "300,60", validate = SettingsValidation::checkHevTimeout,
                    enabled = vpnOnly, visible = { it.useHevTun }),
            ),
        ),
        EngineSection(
            "proxy", "پروکسی محلی و اشتراک در شبکه", "ورودی SOCKS/HTTP روی خود گوشی و اشتراک‌گذاری آن با دستگاه‌های دیگر.", nextConnect = true,
            controls = listOf(
                SwitchControl(
                    "enableLocalProxy", "پروکسی محلی", "بدون آن ورودی SOCKS وجود ندارد و به‌روزرسانی اشتراک‌ها از پروکسی عبور نمی‌کند.",
                    { it.enableLocalProxy },
                    { s, v -> s.copy(enableLocalProxy = v, appendHttpProxy = if (!v) false else s.appendHttpProxy) },
                    enabledWhen = { !it.localProxyForced },
                ),
                sw("appendHttpProxy", "افزودن پروکسی HTTP به اینترفیس", "به مرورگرها اجازه می‌دهد مستقیم از پروکسی HTTP استفاده کنند (اندروید ۱۰ به بالا).",
                    { it.appendHttpProxy }, { s, v -> s.copy(appendHttpProxy = v) }, enabled = { it.effectiveLocalProxy }),
                sw("dynamicSocksPort", "پورت SOCKS پویا", "هر اتصال یک پورت آزاد انتخاب می‌کند تا برنامه‌ی دیگر مزاحم نشود.",
                    { it.dynamicSocksPort }, { s, v -> s.copy(dynamicSocksPort = v) }, enabled = { it.effectiveLocalProxy }),
                SwitchControl(
                    "proxySharing", "اشتراک پروکسی در شبکه (LAN)", "دستگاه‌های دیگر همین شبکه با نام کاربری و رمز زیر می‌توانند از پروکسی استفاده کنند.",
                    { it.proxySharing },
                    { s, v ->
                        if (v && (s.socksUsername.isBlank() || s.socksPassword.isBlank())) {
                            s.copy(
                                proxySharing = true,
                                socksUsername = s.socksUsername.ifBlank { "nrg" },
                                socksPassword = s.socksPassword.ifBlank { SettingsValidation.randomToken() },
                            )
                        } else s.copy(proxySharing = v)
                    },
                    enabledWhen = { it.effectiveLocalProxy },
                ),
                num("socksPort", "پورت اشتراک", "بین ۱۰۲۴ و ۶۵۴۳۴ و غیر از پورت‌های داخلی برنامه. پورت HTTP یکی بالاتر است.",
                    min = 1024, max = 65534,
                    get = { it.socksPort }, set = { s, v -> s.copy(socksPort = v) },
                    extraCheck = SettingsValidation::checkLanPort,
                    enabled = { it.effectiveLocalProxy && it.proxySharing }),
                txt("socksUsername", "نام کاربری اشتراک", null,
                    { it.socksUsername }, { s, v -> s.copy(socksUsername = v.trim()) },
                    validate = SettingsValidation::checkNotBlank, enabled = { it.effectiveLocalProxy && it.proxySharing }),
                txt("socksPassword", "رمز اشتراک", null,
                    { it.socksPassword }, { s, v -> s.copy(socksPassword = v) },
                    validate = SettingsValidation::checkNotBlank, password = true, enabled = { it.effectiveLocalProxy && it.proxySharing }),
                sw("socksEnableUdp", "UDP روی SOCKS", "احراز هویت SOCKS5 همراه UDP کاملا امن نیست.",
                    { it.socksEnableUdp }, { s, v -> s.copy(socksEnableUdp = v) }, enabled = { it.effectiveLocalProxy && it.proxySharing }),
            ),
        ),
        EngineSection(
            "core", "هسته و مسیریابی", "تشخیص مقصد، سطح لاگ و روش کلی مسیریابی.", nextConnect = true,
            controls = listOf(
                sw("sniffingEnabled", "Sniffing", "تشخیص دامنه‌ی مقصد از روی ترافیک.",
                    { it.sniffingEnabled }, { s, v -> s.copy(sniffingEnabled = v) }),
                sw("routeOnly", "فقط برای مسیریابی", "دامنه‌ی تشخیص‌داده‌شده فقط برای مسیریابی استفاده شود و مقصد همان IP بماند.",
                    { it.routeOnly }, { s, v -> s.copy(routeOnly = v) }),
                choice("logLevel", "سطح لاگ هسته", "سطح پایین‌تر یعنی لاگ کم‌تر و باتری کم‌تر.",
                    LogLevel.entries, { it.value }, { it.logLevel }, { s, v -> s.copy(logLevel = v) }),
                choice("routingMode", "حالت مسیریابی", "کدام آدرس‌ها مستقیم بروند. قوانین دقیق‌تر را در بخش مسیریابی بنویسید.",
                    RoutingMode.entries,
                    { when (it) {
                        RoutingMode.Global -> "همه از تونل"
                        RoutingMode.BypassLan -> "LAN مستقیم"
                        RoutingMode.BypassIran -> "ایران مستقیم"
                        RoutingMode.BypassLanAndIran -> "LAN و ایران مستقیم"
                    } },
                    { it.routingMode }, { s, v -> s.copy(routingMode = v) }),
                choice("domainStrategy", "استراتژی دامنه", "AsIs سریع‌تر است؛ IPIfNonMatch فقط وقتی قانونی نخورد دامنه را به IP تبدیل می‌کند.",
                    DomainStrategy.entries, { it.value }, { it.domainStrategy }, { s, v -> s.copy(domainStrategy = v) }),
                sw("blockAds", "مسدود کردن تبلیغات", "دامنه‌های تبلیغاتی شناخته‌شده بسته می‌شوند.",
                    { it.blockAds }, { s, v -> s.copy(blockAds = v) }),
            ),
        ),
        EngineSection(
            "mux", "Mux (Xray)", "چند جریان روی یک اتصال؛ دست‌دادن کم‌تر، اما ممکن است اتصال ناپایدار شود.", nextConnect = true,
            controls = listOf(
                sw("muxEnabled", "فعال‌سازی Mux", "سریع‌تر است، اما اتصال ممکن است ناپایدار شود.",
                    { it.muxEnabled }, { s, v -> s.copy(muxEnabled = v) }),
                num("muxConcurrency", "هم‌زمانی TCP", "بازه‌ی ۱- تا ۱۰۲۴؛ ۱- یعنی خاموش.", min = -1, max = 1024,
                    get = { it.muxConcurrency }, set = { s, v -> s.copy(muxConcurrency = v) }, enabled = { it.muxEnabled }),
                num("muxXudpConcurrency", "هم‌زمانی XUDP", "بازه‌ی ۱- تا ۱۰۲۴.", min = -1, max = 1024,
                    get = { it.muxXudpConcurrency }, set = { s, v -> s.copy(muxXudpConcurrency = v) }, enabled = { it.muxEnabled }),
                choice("muxXudpQuic", "QUIC روی XUDP", "با بستن QUIC برنامه‌ها به TCP برمی‌گردند.",
                    XudpQuic.entries,
                    { when (it) { XudpQuic.Reject -> "رد کن"; XudpQuic.Allow -> "اجازه بده"; XudpQuic.Skip -> "از Mux رد شو" } },
                    { it.muxXudpQuic }, { s, v -> s.copy(muxXudpQuic = v) },
                    enabled = { it.muxEnabled && it.muxXudpConcurrency >= 0 }),
            ),
        ),
        EngineSection(
            "fragment", "Fragment و نویز", "تکه‌تکه کردن دست‌دادن TLS برای عبور از فیلترینگ.", nextConnect = true,
            controls = listOf(
                sw("fragmentEnabled", "فعال‌سازی Fragment", null,
                    { it.fragmentEnabled }, { s, v -> s.copy(fragmentEnabled = v) }),
                choice("fragmentPackets", "بسته‌های هدف", "tlshello یعنی فقط ClientHello.",
                    FragmentPackets.entries, { it.value },
                    { it.fragmentPackets }, { s, v -> s.copy(fragmentPackets = v) }, enabled = { it.fragmentEnabled }),
                txt("fragmentLength", "اندازه‌ی تکه‌ها", "قالب: کمینه-بیشینه",
                    { it.fragmentLength }, { s, v -> s.copy(fragmentLength = v.trim()) },
                    placeholder = "50-100", validate = SettingsValidation::checkRange, enabled = { it.fragmentEnabled }),
                txt("fragmentInterval", "فاصله‌ی بین تکه‌ها (میلی‌ثانیه)", "قالب: کمینه-بیشینه",
                    { it.fragmentInterval }, { s, v -> s.copy(fragmentInterval = v.trim()) },
                    placeholder = "10-20", validate = SettingsValidation::checkRange, enabled = { it.fragmentEnabled }),
                num("fragmentMaxSplit", "بیشینه‌ی تقسیم", "حداکثر تعداد تکه‌ها برای هر بسته.", min = 1, max = 1000,
                    get = { it.fragmentMaxSplit }, set = { s, v -> s.copy(fragmentMaxSplit = v) }, enabled = { it.fragmentEnabled }),
            ),
        ),
        EngineSection(
            "singbox", "Sing-Box", "تنظیماتی که فقط روی سرورها و کانفیگ‌های Sing-Box اثر دارند.", nextConnect = true,
            controls = listOf(
                sw("singBoxMuxEnabled", "Mux", "چند جریان روی یک اتصال؛ ولی کند شدن یک اتصال همه را کند می‌کند.",
                    { it.singBoxMuxEnabled }, { s, v -> s.copy(singBoxMuxEnabled = v) }),
                choice("singBoxMuxProtocol", "پروتکل Mux", null,
                    SingBoxMuxProtocol.entries, { it.value },
                    { it.singBoxMuxProtocol }, { s, v -> s.copy(singBoxMuxProtocol = v) }, enabled = { it.singBoxMuxEnabled }),
                num("singBoxMuxMaxConnections", "بیشینه‌ی اتصال Mux", "۰ یعنی انتخاب خودکار.", min = 0, max = 64,
                    get = { it.singBoxMuxMaxConnections }, set = { s, v -> s.copy(singBoxMuxMaxConnections = v) },
                    enabled = { it.singBoxMuxEnabled }),
                sw("singBoxMuxPadding", "Padding برای Mux", "شکل جریان‌ها را از دید کسی که اندازه‌ی بسته‌ها را می‌بیند پنهان می‌کند.",
                    { it.singBoxMuxPadding }, { s, v -> s.copy(singBoxMuxPadding = v) }, enabled = { it.singBoxMuxEnabled }),
                sw("singBoxBrutalEnabled", "Brutal", "با سرعت ثابت می‌فرستد و عقب نمی‌نشیند؛ سرور هم باید آن را داشته باشد.",
                    { it.singBoxBrutalEnabled }, { s, v -> s.copy(singBoxBrutalEnabled = v) }, enabled = { it.singBoxMuxEnabled }),
                num("singBoxBrutalUpMbps", "سرعت آپلود Brutal", null, min = 1, max = 10_000, unit = "Mbps",
                    get = { it.singBoxBrutalUpMbps }, set = { s, v -> s.copy(singBoxBrutalUpMbps = v) },
                    enabled = { it.singBoxMuxEnabled && it.singBoxBrutalEnabled }),
                num("singBoxBrutalDownMbps", "سرعت دانلود Brutal", null, min = 1, max = 10_000, unit = "Mbps",
                    get = { it.singBoxBrutalDownMbps }, set = { s, v -> s.copy(singBoxBrutalDownMbps = v) },
                    enabled = { it.singBoxMuxEnabled && it.singBoxBrutalEnabled }),
                sw("singBoxTlsFragment", "Fragment برای TLS", "دست‌دادن را تکه‌تکه می‌فرستد تا فیلترینگ نتواند آن را یک‌جا بخواند.",
                    { it.singBoxTlsFragment }, { s, v -> s.copy(singBoxTlsFragment = v) }),
                sw("singBoxTlsRecordFragment", "Fragment در سطح Record", "تکه‌تکه کردن داخل خود دست‌دادن؛ از فیلترینگ بازچینش‌کننده هم رد می‌شود.",
                    { it.singBoxTlsRecordFragment }, { s, v -> s.copy(singBoxTlsRecordFragment = v) }),
                strChoice("singBoxUtlsFingerprint", "اثر انگشت uTLS", "شبیه‌سازی دست‌دادن یک مرورگر واقعی.",
                    listOf("" to "خاموش") + SingBoxTuning.FINGERPRINTS.map { it to it },
                    { it.singBoxUtlsFingerprint }, { s, v -> s.copy(singBoxUtlsFingerprint = v) }),
                sw("singBoxUdpOverTcp", "UDP روی TCP", "برای سرورهایی که پروتکلشان اجازه می‌دهد، UDP را داخل همان اتصال می‌برد.",
                    { it.singBoxUdpOverTcp }, { s, v -> s.copy(singBoxUdpOverTcp = v) }),
                choice("singBoxTunStack", "پشته‌ی TUN", "خودکار برای بیشتر دستگاه‌ها مناسب است.",
                    SingBoxStack.entries, { if (it == SingBoxStack.Auto) "خودکار" else it.value },
                    { it.singBoxTunStack }, { s, v -> s.copy(singBoxTunStack = v) }),
                sw("singBoxStrictRoute", "مسیر سخت‌گیرانه", "جلوی ترافیکی که می‌خواهد از کنار تونل خارج شود را می‌گیرد.",
                    { it.singBoxStrictRoute }, { s, v -> s.copy(singBoxStrictRoute = v) }),
                sw("singBoxEndpointIndependentNat", "NAT مستقل از مقصد", "بازی و تماس به آن نیاز دارند؛ حافظه‌ی بیشتری می‌گیرد.",
                    { it.singBoxEndpointIndependentNat }, { s, v -> s.copy(singBoxEndpointIndependentNat = v) }),
                sw("singBoxStoreCache", "حافظه‌ی نهان کانفیگ", "انتخاب‌ها و پاسخ‌های ذخیره‌شده بعد از راه‌اندازی دوباره می‌مانند.",
                    { it.singBoxStoreCache }, { s, v -> s.copy(singBoxStoreCache = v) }),
                sw("singBoxNtpEnabled", "همگام‌سازی ساعت (NTP)", "VMess و REALITY دستگاهی را که ساعتش جابه‌جا شده قبول نمی‌کنند.",
                    { it.singBoxNtpEnabled }, { s, v -> s.copy(singBoxNtpEnabled = v) }),
                txt("singBoxNtpServer", "سرور NTP", null,
                    { it.singBoxNtpServer }, { s, v -> s.copy(singBoxNtpServer = v.trim()) },
                    placeholder = "time.apple.com", validate = SettingsValidation::checkNotBlank, enabled = { it.singBoxNtpEnabled }),
            ),
        ),
        EngineSection(
            "auto", "انتخاب خودکار", "سریع‌ترین سرور گروه، اتصال‌های تازه را حمل می‌کند و در صورت کندی خودکار عوض می‌شود.", nextConnect = true,
            controls = listOf(
                sw("autoSelectRetry", "تلاش دوباره روی سرور بعدی", "اگر سرور فعلی پاسخ نداد، اتصال‌ها ظرف یکی‌دو ثانیه روی سرور بعدی ادامه می‌یابند.",
                    { it.autoSelectRetry }, { s, v -> s.copy(autoSelectRetry = v) }),
                num("autoSelectSwitchMargin", "حاشیه‌ی تعویض", "سرور تازه باید حداقل این‌قدر سریع‌تر باشد تا عوض شود.", min = 1, max = 99, unit = "درصد",
                    get = { it.autoSelectSwitchMargin }, set = { s, v -> s.copy(autoSelectSwitchMargin = v) }),
                num("autoSelectCheckSeconds", "فاصله‌ی بررسی", null, min = 5, max = 3600, unit = "ثانیه",
                    get = { it.autoSelectCheckSeconds }, set = { s, v -> s.copy(autoSelectCheckSeconds = v) }),
                num("autoSelectSweepMinutes", "فاصله‌ی بررسی کامل", null, min = 1, max = 240, unit = "دقیقه",
                    get = { it.autoSelectSweepMinutes }, set = { s, v -> s.copy(autoSelectSweepMinutes = v) }),
            ),
        ),
        EngineSection(
            "tests", "تست و اشتراک‌ها", "نشانی تست پینگ، هم‌زمانی تست و به‌روزرسانی خودکار اشتراک‌ها.", nextConnect = false,
            controls = listOf(
                txt("delayTestUrl", "نشانی تست تأخیر", "نشانی‌ای که تست پینگ واقعی از طریق هر سرور به آن وصل می‌شود.",
                    { it.delayTestUrl }, { s, v -> s.copy(delayTestUrl = v.trim()) },
                    placeholder = "https://www.gstatic.com/generate_204", validate = SettingsValidation::checkUrl, monospace = true),
                num("realPingConcurrency", "تعداد تست هم‌زمان", "عدد بزرگ‌تر سریع‌تر است ولی شبکه را بیشتر مشغول می‌کند.", min = 1, max = 128,
                    get = { it.realPingConcurrency }, set = { s, v -> s.copy(realPingConcurrency = v) }),
                sw("useRealDelayForTests", "پینگ واقعی به جای TCP", "تست از داخل خود سرور می‌گذرد و نتیجه به تجربه‌ی واقعی نزدیک‌تر است.",
                    { it.useRealDelayForTests }, { s, v -> s.copy(useRealDelayForTests = v) }),
                txt("ipApiUrl", "نشانی تشخیص IP خروجی", "برای نمایش کشور و IP بعد از اتصال.",
                    { it.ipApiUrl }, { s, v -> s.copy(ipApiUrl = v.trim()) },
                    placeholder = "https://api.ip.sb/geoip", validate = SettingsValidation::checkUrl, monospace = true),
                sw("autoUpdateSubscriptions", "به‌روزرسانی خودکار اشتراک‌ها", "اگر ارائه‌دهنده فاصله‌ی خودش را بفرستد، همان رعایت می‌شود. هنگام اتصال، به‌روزرسانی از داخل تونل انجام می‌شود.",
                    { it.autoUpdateSubscriptions }, { s, v -> s.copy(autoUpdateSubscriptions = v) }),
                num("subscriptionUpdateIntervalHours", "فاصله‌ی به‌روزرسانی", "بین ۱ ساعت و یک هفته.", min = 1, max = 168, unit = "ساعت",
                    get = { it.subscriptionUpdateIntervalHours }, set = { s, v -> s.copy(subscriptionUpdateIntervalHours = v) },
                    enabled = { it.autoUpdateSubscriptions }),
                sw("autoTestAfterUpdate", "تست خودکار بعد از به‌روزرسانی", null,
                    { it.autoTestAfterUpdate }, { s, v -> s.copy(autoTestAfterUpdate = v) }),
                sw("autoRemoveInvalidAfterTest", "حذف خودکار سرورهای خراب بعد از تست", "سرورهایی که تست را رد شوند از فهرست پاک می‌شوند.",
                    { it.autoRemoveInvalidAfterTest }, { s, v -> s.copy(autoRemoveInvalidAfterTest = v) }),
                sw("autoSortAfterTest", "مرتب‌سازی خودکار بعد از تست", "سریع‌ترین سرورها بالای فهرست می‌آیند.",
                    { it.autoSortAfterTest }, { s, v -> s.copy(autoSortAfterTest = v) }),
            ),
        ),
        EngineSection(
            "tor", "Tor", "پل‌ها، نودهای ورودی/خروجی و رفتار مدارها.", nextConnect = true,
            controls = listOf(
                strChoice("torBridgesMode", "پل‌ها", null,
                    listOf("none" to "بدون پل", "default" to "پیش‌فرض", "own" to "پل‌های خودم"),
                    { it.torBridgesMode }, { s, v -> s.copy(torBridgesMode = v) }),
                strChoice("torBridgeTransport", "نوع پل", null,
                    listOf("obfs4", "obfs3", "scramblesuit", "meek_lite", "snowflake", "conjure", "webtunnel", "vanilla").map { it to it },
                    { it.torBridgeTransport }, { s, v -> s.copy(torBridgeTransport = v) },
                    visible = { it.torBridgesMode != "none" }),
                strChoice("torSnowflakeRendezvous", "نقطه‌ی تماس Snowflake", null,
                    listOf("amp" to "AMP", "cdn77" to "CDN77", "amazon" to "AMAZON"),
                    { it.torSnowflakeRendezvous }, { s, v -> s.copy(torSnowflakeRendezvous = v) },
                    visible = { it.torBridgesMode != "none" && it.torBridgeTransport == "snowflake" }),
                txt("torSnowflakeStun", "سرور STUN برای Snowflake", "خالی یعنی پیش‌فرض.",
                    { it.torSnowflakeStun }, { s, v -> s.copy(torSnowflakeStun = v.trim()) },
                    visible = { it.torBridgesMode != "none" && it.torBridgeTransport == "snowflake" }),
                txt("torOwnBridges", "پل‌های من", "هر خط یک پل.",
                    { it.torOwnBridges }, { s, v -> s.copy(torOwnBridges = v) },
                    placeholder = "obfs4 1.2.3.4:443 CERT… iat-mode=0", multiline = true, monospace = true,
                    visible = { it.torBridgesMode == "own" }),
                txt("torEntryNodes", "نودهای ورودی", null, { it.torEntryNodes }, { s, v -> s.copy(torEntryNodes = v.trim()) }, placeholder = "{de},{nl}"),
                txt("torExitNodes", "نودهای خروجی", null, { it.torExitNodes }, { s, v -> s.copy(torExitNodes = v.trim()) }, placeholder = "{us},{gb}"),
                txt("torExcludeNodes", "نودهای مستثنا", null, { it.torExcludeNodes }, { s, v -> s.copy(torExcludeNodes = v.trim()) }, placeholder = "{ru},{cn}"),
                txt("torExcludeExitNodes", "خروجی‌های مستثنا", null, { it.torExcludeExitNodes }, { s, v -> s.copy(torExcludeExitNodes = v.trim()) }, placeholder = "{ru},{cn}"),
                sw("torStrictNodes", "نودها الزامی", "فهرست نودها را برای همه‌ی مدارها قطعی بدان، حتی اگر اتصال قطع شود.",
                    { it.torStrictNodes }, { s, v -> s.copy(torStrictNodes = v) }),
                txt("torVirtualAddrNetwork", "شبکه‌ی آدرس مجازی", null,
                    { it.torVirtualAddrNetwork }, { s, v -> s.copy(torVirtualAddrNetwork = v.trim()) },
                    placeholder = "10.192.0.0/10", validate = SettingsValidation::checkNotBlank, monospace = true),
                sw("torHardwareAccel", "شتاب سخت‌افزاری", "در صورت وجود از شتاب‌دهنده‌ی رمزنگاری استفاده کن.",
                    { it.torHardwareAccel }, { s, v -> s.copy(torHardwareAccel = v) }),
                sw("torAvoidDiskWrites", "کم‌نویسی روی دیسک", null,
                    { it.torAvoidDiskWrites }, { s, v -> s.copy(torAvoidDiskWrites = v) }),
                sw("torConnectionPadding", "پُرکردن اتصال (Padding)", "در برابر برخی تحلیل‌های ترافیک دفاع می‌کند؛ برای صرفه‌جویی در پهنای باند خاموش کنید.",
                    { it.torConnectionPadding }, { s, v -> s.copy(torConnectionPadding = v) }),
                sw("torReducedConnectionPadding", "Padding کم‌تر", "اتصال‌ها کوتاه‌تر باز می‌مانند و padding کم‌تری می‌رود.",
                    { it.torReducedConnectionPadding }, { s, v -> s.copy(torReducedConnectionPadding = v) }),
                sw("torFascistFirewall", "فقط پورت ۸۰ و ۴۴۳", "فقط به رله‌هایی که این پورت‌ها را دارند وصل شو.",
                    { it.torFascistFirewall }, { s, v -> s.copy(torFascistFirewall = v) }),
                num("torNewCircuitPeriod", "فاصله‌ی ساخت مدار تازه", null, min = 1, max = 86_400, unit = "ثانیه",
                    get = { it.torNewCircuitPeriod }, set = { s, v -> s.copy(torNewCircuitPeriod = v) }),
                num("torMaxCircuitDirtiness", "بیشینه‌ی عمر مدار استفاده‌شده", null, min = 1, max = 86_400, unit = "ثانیه",
                    get = { it.torMaxCircuitDirtiness }, set = { s, v -> s.copy(torMaxCircuitDirtiness = v) }),
                num("torDormantClientTimeout", "زمان خواب کلاینت", null, min = 1, max = 10_080, unit = "دقیقه",
                    get = { it.torDormantClientTimeout }, set = { s, v -> s.copy(torDormantClientTimeout = v) }),
                sw("torEnforceDistinctSubnets", "زیرشبکه‌های متفاوت", "دو رله با IP در یک /16 را روی یک مدار نگذار.",
                    { it.torEnforceDistinctSubnets }, { s, v -> s.copy(torEnforceDistinctSubnets = v) }),
                sw("torTrackHostExits", "ثابت نگه داشتن خروجی هر هاست", "برای هاست‌های اخیر همان نود خروجی دوباره استفاده می‌شود.",
                    { it.torTrackHostExits }, { s, v -> s.copy(torTrackHostExits = v) }),
                sw("torClientUseIPv4", "استفاده از IPv4", null, { it.torClientUseIPv4 }, { s, v -> s.copy(torClientUseIPv4 = v) }),
                sw("torClientUseIPv6", "استفاده از IPv6", null, { it.torClientUseIPv6 }, { s, v -> s.copy(torClientUseIPv6 = v) }),
                sw("torIsolateUid", "جداسازی بر اساس برنامه", "مدار هر برنامه از برنامه‌های دیگر جداست.",
                    { it.torIsolateUid }, { s, v -> s.copy(torIsolateUid = v) }),
                sw("torIsolateDestAddr", "جداسازی بر اساس مقصد", "برای هر هاست مقصد یک مدار جدا.",
                    { it.torIsolateDestAddr }, { s, v -> s.copy(torIsolateDestAddr = v) }),
                sw("torIsolateDestPort", "جداسازی بر اساس پورت مقصد", null,
                    { it.torIsolateDestPort }, { s, v -> s.copy(torIsolateDestPort = v) }),
                sw("torFakeSniEnabled", "SNI جعلی برای Tor", "TLS تور هاست‌های زیر را تقلید می‌کند (uTLS).",
                    { it.torFakeSniEnabled }, { s, v -> s.copy(torFakeSniEnabled = v) }),
                txt("torFakeSniHosts", "هاست‌های SNI جعلی", "خالی یعنی پیش‌فرض.",
                    { it.torFakeSniHosts }, { s, v -> s.copy(torFakeSniHosts = v) },
                    placeholder = "play.googleapis.com, drive.google.com", enabled = { it.torFakeSniEnabled }),
            ),
        ),
        EngineSection(
            "ssh", "SSH", "رمز و فشرده‌سازی تونل‌های SSH.", nextConnect = true,
            controls = listOf(
                strChoice("sshCipher", "الگوریتم رمز", null,
                    listOf(
                        "auto" to "خودکار",
                        "aes128-gcm@openssh.com" to "AES-128-GCM",
                        "chacha20-poly1305@openssh.com" to "ChaCha20-Poly1305",
                        "aes256-gcm@openssh.com" to "AES-256-GCM",
                        "aes128-ctr" to "قدیمی (AES-128-CTR)",
                    ),
                    { it.sshCipher }, { s, v -> s.copy(sshCipher = v) }),
                sw("sshCompression", "فشرده‌سازی", "روی خطوط کند خوب است و با HTTPS بد.",
                    { it.sshCompression }, { s, v -> s.copy(sshCompression = v) }),
                num("sshMaxChannels", "بیشینه‌ی کانال", null, min = 1, max = 64,
                    get = { it.sshMaxChannels }, set = { s, v -> s.copy(sshMaxChannels = v) }),
            ),
        ),
        EngineSection(
            "spoof", "جعل SNI بدون روت (Rsta)", "یک تنظیم برای همه‌ی پروفایل‌هایی که سرورشان 127.0.0.1:40443 است.", nextConnect = false,
            controls = listOf(
                txt("rstaSpoofConnectIp", "IP اتصال", "IP تمیز CDN که موتور به آن وصل می‌شود. از اسکنر IP تمیز می‌توانید پیدا کنید.",
                    { it.rstaSpoofConnectIp }, { s, v -> s.copy(rstaSpoofConnectIp = v.trim()) },
                    placeholder = SniSpoofConfig.DEFAULT_CONNECT_IP, validate = SettingsValidation::checkNotBlank, monospace = true),
                num("rstaSpoofConnectPort", "پورت اتصال", null, min = 1, max = 65535,
                    get = { it.rstaSpoofConnectPort }, set = { s, v -> s.copy(rstaSpoofConnectPort = v) }),
                txt("rstaSpoofFakeSni", "SNI جعلی", "نامی که در دست‌دادن TLS نشان داده می‌شود.",
                    { it.rstaSpoofFakeSni }, { s, v -> s.copy(rstaSpoofFakeSni = v.trim()) },
                    placeholder = SniSpoofConfig.DEFAULT_FAKE_SNI, validate = SettingsValidation::checkNotBlank, monospace = true),
                strChoice("rstaSpoofMethod", "روش", null,
                    SniSpoofConfig.METHODS.map { it to it },
                    { SniSpoofConfig.method(it.rstaSpoofMethod) }, { s, v -> s.copy(rstaSpoofMethod = v) }),
            ),
        ),
        EngineSection(
            "app", "اعلان‌ها و رفتار برنامه", "اعلان اتصال و رفتار فهرست سرورها.", nextConnect = false,
            controls = listOf(
                sw("speedInNotification", "سرعت در اعلان", null,
                    { it.speedInNotification }, { s, v -> s.copy(speedInNotification = v) }),
                sw("liveNotification", "اعلان زنده", "اعلان اتصال هم‌زمان با ترافیک به‌روز می‌شود.",
                    { it.liveNotification }, { s, v -> s.copy(liveNotification = v) }),
                choice("notifChip", "برچسب اعلان", "کدام مقدار کنار اعلان نمایش داده شود.",
                    NotifChip.entries,
                    { when (it) {
                        NotifChip.Speed -> "سرعت"
                        NotifChip.ConfigName -> "نام سرور"
                        NotifChip.Duration -> "مدت اتصال"
                        NotifChip.Total -> "حجم کل"
                        NotifChip.None -> "هیچ"
                    } },
                    { it.notifChip }, { s, v -> s.copy(notifChip = v) }, enabled = { it.liveNotification }),
                sw("confirmRemove", "تأیید قبل از حذف", "پیش از حذف سرور یا قانون یک بار می‌پرسد.",
                    { it.confirmRemove }, { s, v -> s.copy(confirmRemove = v) }),
                choice("importTargetMode", "مقصد ورود کانفیگ", "کانفیگ تازه کجا ذخیره شود.",
                    ImportTargetMode.entries,
                    { when (it) {
                        ImportTargetMode.Ask -> "بپرس"
                        ImportTargetMode.Subscription -> "اشتراک شخصی"
                        ImportTargetMode.Manual -> "بخش دستی"
                    } },
                    { it.importTargetMode }, { s, v -> s.copy(importTargetMode = v) }),
            ),
        ),
    )
}

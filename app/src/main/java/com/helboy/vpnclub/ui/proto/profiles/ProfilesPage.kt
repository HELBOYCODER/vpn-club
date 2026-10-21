package com.helboy.vpnclub.ui.proto.profiles

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.helboy.vpnclub.ui.components.Chip
import com.helboy.vpnclub.ui.components.ClubCard
import com.helboy.vpnclub.ui.fa
import com.helboy.vpnclub.ui.formatBytes
import com.helboy.vpnclub.ui.proto.ProtoChoice
import com.helboy.vpnclub.ui.proto.ProtoConfirm
import com.helboy.vpnclub.ui.proto.ProtoDialog
import com.helboy.vpnclub.ui.proto.ProtoEmpty
import com.helboy.vpnclub.ui.proto.ProtoEnv
import com.helboy.vpnclub.ui.proto.ProtoError
import com.helboy.vpnclub.ui.proto.ProtoField
import com.helboy.vpnclub.ui.proto.ProtoLoading
import com.helboy.vpnclub.ui.proto.ProtoNavRow
import com.helboy.vpnclub.ui.proto.ProtoOutlined
import com.helboy.vpnclub.ui.proto.ProtoRoute
import com.helboy.vpnclub.ui.screen.QrDialog
import com.helboy.vpnclub.ui.theme.BorderDark
import com.helboy.vpnclub.ui.theme.NeonCyan
import com.helboy.vpnclub.ui.theme.NeonIndigo
import com.helboy.vpnclub.ui.theme.Palette
import com.helboy.vpnclub.ui.theme.StatusAmber
import com.helboy.vpnclub.ui.theme.StatusGreen
import com.helboy.vpnclub.ui.theme.StatusRed
import com.helboy.vpnclub.ui.theme.TextPrimary
import com.helboy.vpnclub.ui.theme.TextSecondary
import com.helboy.vpnclub.ui.theme.TextTertiary
import dev.valid7996.narcicgeteway.data.net.PingCoordinator
import dev.valid7996.narcicgeteway.domain.config.VpnProfile
import dev.valid7996.narcicgeteway.domain.model.AppSettings
import dev.valid7996.narcicgeteway.ui.platform.LocalPlatform
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The popups the page can have open; only one at a time. */
private sealed interface Dlg {
    data class Actions(val id: String) : Dlg
    data class Rename(val id: String) : Dlg
    data class Move(val ids: List<String>) : Dlg
    data class Qr(val title: String, val link: String) : Dlg
    data class Confirm(val title: String, val text: String, val label: String, val action: () -> Unit) : Dlg
    data object Add : Dlg
}

/** The settings that shape this list; defaults when the settings store was not installed (tests). */
@Composable
private fun rememberListSettings(): AppSettings {
    val flow = remember { runCatching { ProtoEnv.settings.settings }.getOrNull() }
    val state = flow?.collectAsState()
    return state?.value ?: AppSettings()
}

/**
 * The root of the "Protocols" tab: every stored profile with search, groups, per-profile and bulk actions,
 * latency tests, reordering and the way into every editor. Tapping a profile makes it the active one.
 */
@Composable
fun ProfilesPage(onNavigate: (ProtoRoute) -> Unit, onActivated: () -> Unit) {
    val repo = ProtoEnv.repo
    val settings = rememberListSettings()
    val platform = LocalPlatform.current
    val scope = rememberCoroutineScope()

    val profiles by repo.profiles.collectAsState()
    val subs by repo.subscriptions.collectAsState()
    val activeId by repo.activeId.collectAsState()
    val progress by PingCoordinator.progress.collectAsState()

    var query by remember { mutableStateOf("") }
    var groupChoice by remember { mutableStateOf(GROUP_ALL) }
    var selecting by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf(emptySet<String>()) }
    var dialog by remember { mutableStateOf<Dlg?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var updating by remember { mutableStateOf(false) }
    var bulkMenu by remember { mutableStateOf(false) }

    val chips = remember(profiles, subs, settings.groupAllDisplay) { groupChips(profiles, subs, settings.groupAllDisplay) }
    val group = effectiveGroup(groupChoice, chips)
    val shown = remember(profiles, query, group) { filterProfiles(profiles, query, group) }
    val subNames = remember(subs) { subs.associate { it.id to it.name.ifBlank { it.url.take(24) } } }
    val liveSelection = selected.filter { id -> profiles.any { it.id == id } }.toSet()
    val testing = progress != null

    fun attempt(what: String, block: () -> Unit) {
        error = null
        runCatching(block).onFailure { error = "$what انجام نشد: ${it.message ?: "خطای نامشخص"}" }
    }

    fun endSelection() {
        selecting = false
        selected = emptySet()
    }

    fun startTests(targets: List<VpnProfile>, real: Boolean) {
        if (targets.isEmpty()) {
            notice = "پروفایلی برای تست وجود ندارد."
            return
        }
        error = null
        notice = null
        val autoRemove = settings.autoRemoveInvalidAfterTest
        val autoSort = settings.autoSortAfterTest
        attempt("شروع تست") {
            PingCoordinator.start(
                profiles = targets,
                useRealDelay = real,
                concurrency = settings.realPingConcurrency,
                delayUrl = settings.delayTestUrl,
                chainConfig = repo::chainProbeConfig,
                clearPings = { repo.clearPings(it, persist = false) },
                onResult = { id, ms, cc -> repo.setPing(id, ms, cc, persist = false) },
                flush = { repo.flushProfiles() },
                onFinished = {
                    if (autoRemove) repo.removeInvalid()
                    if (autoSort) repo.sortByTestResults()
                },
            )
        }
    }

    fun confirmOrDo(title: String, text: String, label: String, force: Boolean = false, action: () -> Unit) {
        if (settings.confirmRemove || force) dialog = Dlg.Confirm(title, text, label, action) else action()
    }

    fun askDelete(ids: List<String>) {
        if (ids.isEmpty()) return
        val what = if (ids.size == 1) "«${profiles.firstOrNull { it.id == ids[0] }?.name?.let(::cleanName) ?: ""}»" else "${ids.size.fa()} پروفایل"
        confirmOrDo("حذف", "$what حذف شود؟ این کار برگشت‌پذیر نیست.", "حذف") {
            attempt("حذف") { if (ids.size == 1) repo.remove(ids[0]) else repo.removeAll(ids) }
            selected = selected - ids.toSet()
            if (selected.isEmpty()) selecting = false
            notice = "${ids.size.fa()} پروفایل حذف شد."
        }
    }

    fun reorder(id: String, delta: Int) {
        val ids = shown.map { it.id }
        val moved = moveWithin(ids, id, delta)
        if (moved != ids) attempt("جابه‌جایی") { repo.reorder(moved) }
    }

    fun copyLink(p: VpnProfile) {
        val link = runCatching { repo.shareLinkOf(p) }.getOrNull()
        if (link.isNullOrBlank()) {
            error = "این پروفایل لینک اشتراک ندارد."
        } else {
            platform.copyToClipboard(link)
            platform.toast("لینک کپی شد")
            notice = "لینک «${cleanName(p.name)}» کپی شد."
        }
    }

    if (selecting) BackHandler { endSelection() }

    Column(Modifier.fillMaxSize().testTag("proto_profiles")) {
        // ── header ──
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("پروتکل‌ها", color = TextPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text(
                    "${profiles.size.fa()} پروفایل" + if (shown.size != profiles.size) " · ${shown.size.fa()} نمایش داده می‌شود" else "",
                    color = TextSecondary, fontSize = 12.sp, modifier = Modifier.testTag("proto_profiles_count"),
                )
            }
            IconButton(onClick = { dialog = Dlg.Add }, modifier = Modifier.testTag("proto_profiles_add")) {
                Icon(Icons.Rounded.Add, "افزودن", tint = NeonCyan)
            }
            Box {
                IconButton(onClick = { bulkMenu = true }, modifier = Modifier.testTag("proto_profiles_more")) {
                    Icon(Icons.Rounded.MoreVert, "عملیات گروهی", tint = TextPrimary)
                }
                DropdownMenu(expanded = bulkMenu, onDismissRequest = { bulkMenu = false }) {
                    val scopeName = if (group == GROUP_ALL) "همه" else chips.firstOrNull { it.id == group }?.label ?: "این گروه"
                    DropdownMenuItem(text = { Text("انتخاب چندتایی") }, onClick = { bulkMenu = false; selecting = true })
                    DropdownMenuItem(
                        text = { Text("پینگ TCP ($scopeName)") }, enabled = !testing && shown.isNotEmpty(),
                        onClick = { bulkMenu = false; startTests(shown, real = false) },
                    )
                    DropdownMenuItem(
                        text = { Text("تأخیر واقعی ($scopeName)") }, enabled = !testing && shown.isNotEmpty(),
                        onClick = { bulkMenu = false; startTests(shown, real = true) },
                    )
                    DropdownMenuItem(
                        text = { Text("مرتب‌سازی بر اساس نتیجه‌ی تست") }, enabled = profiles.size > 1,
                        onClick = {
                            bulkMenu = false
                            attempt("مرتب‌سازی") { repo.sortByTestResults() }
                            notice = "فهرست از سریع‌ترین به کندترین مرتب شد."
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("حذف پروفایل‌های نامعتبر") },
                        onClick = {
                            bulkMenu = false
                            val n = repo.invalidCount()
                            if (n == 0) notice = "پروفایل نامعتبری پیدا نشد." else {
                                dialog = Dlg.Confirm("حذف نامعتبرها", "${n.fa()} پروفایل نامعتبر یا بی‌پاسخ حذف شود؟", "حذف") {
                                    attempt("حذف نامعتبرها") { notice = "${repo.removeInvalid().fa()} پروفایل نامعتبر حذف شد." }
                                }
                            }
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("حذف تکراری‌ها") },
                        onClick = {
                            bulkMenu = false
                            confirmOrDo("حذف تکراری‌ها", "پروفایل‌هایی که لینک یکسان دارند فقط یک‌بار می‌مانند.", "حذف") {
                                attempt("حذف تکراری‌ها") {
                                    val n = repo.removeDuplicates()
                                    notice = if (n == 0) "تکراری پیدا نشد." else "${n.fa()} پروفایل تکراری حذف شد."
                                }
                            }
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(if (group == GROUP_ALL) "حذف همه‌ی پروفایل‌ها" else "حذف محتوای «$scopeName»", color = StatusRed) },
                        enabled = profiles.isNotEmpty(),
                        onClick = {
                            bulkMenu = false
                            val count = inGroup(profiles, group).count { !it.isLocked }
                            confirmOrDo(
                                "حذف گروهی",
                                "${count.fa()} پروفایل از «$scopeName» حذف می‌شود (پروفایل‌های قفل‌دار می‌مانند).",
                                "حذف", force = true,
                            ) {
                                attempt("حذف گروهی") {
                                    if (group == GROUP_ALL) repo.removeAll { !it.isLocked }
                                    else repo.removeSubscriptionConfigs(group.ifEmpty { null })
                                }
                                notice = "${count.fa()} پروفایل حذف شد."
                            }
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("به‌روزرسانی همه‌ی اشتراک‌ها") }, enabled = subs.isNotEmpty() && !updating,
                        onClick = {
                            bulkMenu = false
                            updating = true
                            error = null
                            notice = null
                            scope.launch {
                                val result = runCatching { withContext(Dispatchers.IO) { repo.updateAllSubscriptions() } }
                                updating = false
                                result.onSuccess { n -> notice = "${n.fa()} پروفایل از اشتراک‌ها دریافت شد." }
                                    .onFailure { error = "به‌روزرسانی اشتراک‌ها انجام نشد: ${it.message ?: "اتصال برقرار نشد"}. اینترنت را بررسی کنید و دوباره امتحان کنید." }
                            }
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("کپی همه‌ی لینک‌ها") }, enabled = profiles.isNotEmpty(),
                        onClick = {
                            bulkMenu = false
                            val text = runCatching { repo.exportAllPayloads() }.getOrDefault("")
                            if (text.isBlank()) notice = "چیزی برای خروجی گرفتن نیست." else {
                                platform.copyToClipboard(text)
                                notice = "${text.lines().count { it.isNotBlank() }.fa()} لینک کپی شد."
                            }
                        },
                    )
                }
            }
        }

        Column(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ProtoField(
                label = "جستجو (نام، آدرس یا پروتکل)",
                value = query, onValueChange = { query = it },
                tag = "proto_profiles_search",
            )
            if (chips.size > 1) {
                ProtoChoice(
                    title = null,
                    options = chips.map { it.id to "${it.label} · ${it.count.fa()}" },
                    selected = group,
                    onSelect = { groupChoice = it },
                    modifier = Modifier.testTag("proto_profiles_groups"),
                )
            }
            progress?.let { p ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ProtoLoading(
                        "در حال تست ${p.done.fa()} از ${p.total.fa()}…",
                        modifier = Modifier.weight(1f),
                    )
                    ProtoOutlined("توقف", { PingCoordinator.cancel() }, tag = "proto_profiles_cancel")
                }
            }
            if (updating) ProtoLoading("در حال به‌روزرسانی اشتراک‌ها…")
            error?.let { ProtoError(it) }
            notice?.let {
                Text(it, color = TextSecondary, fontSize = 12.sp, modifier = Modifier.testTag("proto_profiles_notice"))
            }
            if (selecting) {
                SelectionBar(
                    count = liveSelection.size,
                    canReorder = liveSelection.size == 1,
                    testing = testing,
                    onAll = { selected = shown.map { it.id }.toSet() },
                    onTest = { startTests(bulkTargets(shown, liveSelection), real = settings.useRealDelayForTests) },
                    onMove = { if (liveSelection.isNotEmpty()) dialog = Dlg.Move(liveSelection.toList()) },
                    onDelete = { askDelete(liveSelection.toList()) },
                    onUp = { liveSelection.firstOrNull()?.let { reorder(it, -1) } },
                    onDown = { liveSelection.firstOrNull()?.let { reorder(it, 1) } },
                    onDone = { endSelection() },
                )
            }
        }

        // ── list ──
        val columns = settings.serverGridColumns.coerceIn(1, 3)
        val gap = settings.listDensity.gapDp.dp
        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            modifier = Modifier.weight(1f).fillMaxWidth().testTag("proto_profiles_list"),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(gap),
            horizontalArrangement = Arrangement.spacedBy(gap),
        ) {
            when {
                profiles.isEmpty() -> item(span = { GridItemSpan(maxLineSpan) }) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                        ProtoEmpty(
                            title = "هنوز پروفایلی ندارید",
                            hint = "لینک کانفیگ را وارد کنید، یک اشتراک اضافه کنید یا با دکمه‌ی «+» یکی از پروتکل‌ها را بسازید.",
                            actionText = "وارد کردن کانفیگ",
                            onAction = { onNavigate(ProtoRoute.Import) },
                        )
                        ProtoOutlined("اشتراک‌ها", { onNavigate(ProtoRoute.Subscriptions) }, tag = "proto_profiles_empty_subs")
                    }
                }
                shown.isEmpty() -> item(span = { GridItemSpan(maxLineSpan) }) {
                    ProtoEmpty(
                        title = "موردی پیدا نشد",
                        hint = if (query.isNotBlank()) "برای «${query.trim()}» پروفایلی در این گروه نیست." else "این گروه پروفایلی ندارد.",
                        actionText = if (query.isNotBlank()) "پاک کردن جستجو" else if (group != GROUP_ALL && chips.any { it.id == GROUP_ALL }) "نمایش همه" else null,
                        onAction = { if (query.isNotBlank()) query = "" else groupChoice = GROUP_ALL },
                    )
                }
                else -> items(shown, key = { it.id }) { p ->
                    ProfileCard(
                        p = p,
                        active = p.id == activeId,
                        selecting = selecting,
                        picked = p.id in liveSelection,
                        groupName = if (group == GROUP_ALL) subNames[p.subscriptionId] else null,
                        s = settings,
                        onClick = {
                            if (selecting) {
                                selected = if (p.id in selected) selected - p.id else selected + p.id
                            } else {
                                attempt("انتخاب") { repo.setActive(p.id) }
                                onActivated()
                            }
                        },
                        onLongClick = { selecting = true; selected = selected + p.id },
                        onMore = { dialog = Dlg.Actions(p.id) },
                    )
                }
            }
        }
    }

    // ── popups ──
    when (val d = dialog) {
        null -> Unit
        Dlg.Add -> ProtoDialog(title = "افزودن پروفایل", onDismiss = { dialog = null }) {
            ProtoNavRow(
                "وارد کردن / چسباندن", "لینک، متن چندخطی، QR یا فایل کانفیگ؛ اشتراک هم شناخته می‌شود",
                onClick = { dialog = null; onNavigate(ProtoRoute.Import) }, icon = Icons.Rounded.ContentCopy, tag = "proto_add_import",
            )
            ProtoNavRow(
                "اشتراک‌ها", "افزودن و به‌روزرسانی لینک اشتراک و گروه‌ها",
                onClick = { dialog = null; onNavigate(ProtoRoute.Subscriptions) }, icon = Icons.Rounded.Folder, tag = "proto_add_subs",
            )
            ADD_KIND_ENTRIES.forEach { e ->
                ProtoNavRow(
                    e.title, e.hint,
                    onClick = { dialog = null; onNavigate(ProtoRoute.New(e.kind)) },
                    icon = Icons.Rounded.Add, tag = "proto_add_${e.kind.name}",
                )
            }
        }

        is Dlg.Actions -> {
            val p = profiles.firstOrNull { it.id == d.id }
            if (p == null) dialog = null else {
                val link = remember(p) { runCatching { repo.shareLinkOf(p) }.getOrNull() }
                val locked = p.isLocked
                ProtoDialog(title = cleanName(p.name), onDismiss = { dialog = null }) {
                    ActionRow(
                        Icons.Rounded.Edit, "ویرایش",
                        subtitle = if (locked) "پروفایل قفل‌دار قابل ویرایش نیست؛ فقط می‌توانید نام آن را عوض کنید." else null,
                        enabled = !locked, tag = "proto_act_edit",
                    ) { dialog = null; onNavigate(ProtoRoute.Edit(p.id)) }
                    ActionRow(Icons.Rounded.Edit, "تغییر نام", tag = "proto_act_rename") { dialog = Dlg.Rename(p.id) }
                    ActionRow(
                        Icons.Rounded.ContentCopy, "کپی لینک",
                        subtitle = if (link == null) (if (locked) "پروفایل قفل‌دار لینک ندارد؛ از «قفل و اشتراک» استفاده کنید." else "این نوع پروفایل لینک اشتراک ندارد.") else null,
                        enabled = link != null, tag = "proto_act_copy",
                    ) { dialog = null; copyLink(p) }
                    ActionRow(Icons.Rounded.Share, "اشتراک به‌صورت متن", enabled = link != null, tag = "proto_act_share") {
                        dialog = null
                        link?.let { platform.shareText(it) }
                    }
                    ActionRow(Icons.Rounded.QrCode2, "نمایش QR", enabled = link != null, tag = "proto_act_qr") {
                        dialog = link?.let { Dlg.Qr(cleanName(p.name), it) }
                    }
                    ActionRow(Icons.Rounded.Folder, "انتقال به گروه", tag = "proto_act_move") { dialog = Dlg.Move(listOf(p.id)) }
                    ActionRow(Icons.Rounded.Speed, "تست پینگ TCP", enabled = !testing, tag = "proto_act_tcp") {
                        dialog = null; startTests(listOf(p), real = false)
                    }
                    ActionRow(Icons.Rounded.Timer, "تست تأخیر واقعی", enabled = !testing, tag = "proto_act_real") {
                        dialog = null; startTests(listOf(p), real = true)
                    }
                    val index = shown.indexOfFirst { it.id == p.id }
                    ActionRow(Icons.Rounded.ArrowUpward, "یک جا بالاتر", enabled = index > 0, tag = "proto_act_up") {
                        reorder(p.id, -1)
                    }
                    ActionRow(Icons.Rounded.ArrowDownward, "یک جا پایین‌تر", enabled = index >= 0 && index < shown.size - 1, tag = "proto_act_down") {
                        reorder(p.id, 1)
                    }
                    ActionRow(
                        Icons.Rounded.RestartAlt, "صفر کردن مصرف",
                        subtitle = "${formatBytes(p.bytesDown)} دریافت · ${formatBytes(p.bytesUp)} ارسال",
                        enabled = p.bytesDown > 0 || p.bytesUp > 0, tag = "proto_act_reset",
                    ) {
                        dialog = null
                        attempt("صفر کردن مصرف") { repo.resetUsage(p.id) }
                    }
                    ActionRow(Icons.Rounded.Lock, "قفل و اشتراک", tag = "proto_act_lock") {
                        dialog = null; onNavigate(ProtoRoute.LockShare(p.id))
                    }
                    ActionRow(Icons.Rounded.Delete, "حذف", tint = StatusRed, tag = "proto_act_delete") {
                        dialog = null; askDelete(listOf(p.id))
                    }
                }
            }
        }

        is Dlg.Rename -> {
            val p = profiles.firstOrNull { it.id == d.id }
            if (p == null) dialog = null else {
                var name by remember(d.id) { mutableStateOf(p.name) }
                ProtoDialog(
                    title = "تغییر نام", onDismiss = { dialog = null },
                    confirmLabel = "ذخیره", confirmEnabled = name.isNotBlank(),
                    onConfirm = {
                        attempt("تغییر نام") { repo.rename(p.id, name.trim()) }
                        dialog = null
                    },
                ) {
                    ProtoField("نام", name, { name = it }, tag = "proto_rename_field")
                }
            }
        }

        is Dlg.Move -> {
            var newGroup by remember { mutableStateOf("") }
            fun moveTo(groupId: String) {
                attempt("انتقال") { repo.moveToGroup(d.ids, groupId) }
                notice = "${d.ids.size.fa()} پروفایل منتقل شد."
                dialog = null
            }
            ProtoDialog(title = "انتقال به گروه", onDismiss = { dialog = null }) {
                chips.filter { it.id != GROUP_ALL }.ifEmpty { listOf(GroupChip(GROUP_MANUAL, "دستی", 0)) }.forEach { c ->
                    ActionRow(Icons.Rounded.Folder, c.label, subtitle = "${c.count.fa()} پروفایل", tag = "proto_move_${c.id}") { moveTo(c.id) }
                }
                ProtoField("گروه تازه", newGroup, { newGroup = it }, placeholder = "نام گروه", tag = "proto_move_new")
                ProtoOutlined("ساخت گروه و انتقال", {
                    attempt("ساخت گروه") { moveTo(repo.addGroup(newGroup).id) }
                }, enabled = newGroup.isNotBlank())
            }
        }

        is Dlg.Qr -> QrDialog(d.title, d.link, onClose = { dialog = null })

        is Dlg.Confirm -> ProtoConfirm(
            title = d.title, text = d.text, confirmLabel = d.label, danger = true,
            onConfirm = { dialog = null; d.action() },
            onDismiss = { dialog = null },
        )
    }
}

@Composable
private fun SelectionBar(
    count: Int,
    canReorder: Boolean,
    testing: Boolean,
    onAll: () -> Unit,
    onTest: () -> Unit,
    onMove: () -> Unit,
    onDelete: () -> Unit,
    onUp: () -> Unit,
    onDown: () -> Unit,
    onDone: () -> Unit,
) {
    ClubCard(Modifier.fillMaxWidth().testTag("proto_profiles_selection"), border = NeonCyan) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("${count.fa()} پروفایل انتخاب شده", color = TextPrimary, fontWeight = FontWeight.SemiBold)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ProtoOutlined("همه", onAll, tag = "proto_sel_all")
                ProtoOutlined("تست", onTest, enabled = count > 0 && !testing, tag = "proto_sel_test")
                ProtoOutlined("انتقال", onMove, enabled = count > 0, tag = "proto_sel_move")
                ProtoOutlined("حذف", onDelete, enabled = count > 0, tag = "proto_sel_delete")
                ProtoOutlined("بالا", onUp, enabled = canReorder, tag = "proto_sel_up")
                ProtoOutlined("پایین", onDown, enabled = canReorder, tag = "proto_sel_down")
                ProtoOutlined("پایان", onDone, tag = "proto_sel_done")
            }
        }
    }
}

@Composable
private fun ActionRow(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    enabled: Boolean = true,
    tint: Color? = null,
    tag: String? = null,
    onClick: () -> Unit,
) {
    val c = tint ?: TextPrimary
    Row(
        Modifier.fillMaxWidth()
            .let { if (tag != null) it.testTag(tag) else it }
            .clickable(enabled = enabled, onClick = onClick)
            .alpha(if (enabled) 1f else 0.5f)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(icon, null, tint = if (tint != null) c else NeonCyan)
        Column(Modifier.weight(1f)) {
            Text(title, color = c, fontSize = 14.sp)
            if (subtitle != null) Text(subtitle, color = TextTertiary, fontSize = 11.sp)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ProfileCard(
    p: VpnProfile,
    active: Boolean,
    selecting: Boolean,
    picked: Boolean,
    groupName: String?,
    s: AppSettings,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onMore: () -> Unit,
) {
    val border = when {
        picked -> NeonCyan
        active -> StatusGreen
        else -> BorderDark
    }
    val bucket = latencyBucket(p.lastPingMs)
    val pingColor = when (bucket) {
        LatencyBucket.Unknown -> TextTertiary
        LatencyBucket.Failed -> StatusRed
        LatencyBucket.Fast -> StatusGreen
        LatencyBucket.Medium -> StatusAmber
        LatencyBucket.Slow -> StatusRed
    }
    val pingText = when (bucket) {
        LatencyBucket.Unknown -> "—"
        LatencyBucket.Failed -> "ناموفق"
        else -> "${(p.lastPingMs ?: 0).fa()} ms"
    }
    val flag = profileFlag(p)
    ClubCard(
        Modifier.fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .testTag("proto_profile_${p.id}"),
        border = border,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                Modifier.size(40.dp).background(if (picked) NeonCyan else Palette.cardElevated, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                when {
                    picked -> Icon(Icons.Rounded.Check, "انتخاب شده", tint = Palette.onAccent)
                    flag != null -> Text(flag, fontSize = 22.sp)
                    else -> Text(protocolBadge(p), color = TextSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    cleanName(p.name), color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Text(
                    addressLabel(p), color = TextSecondary, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    fontFamily = if (s.monospaceAddress) FontFamily.Monospace else FontFamily.Default,
                )
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    Chip(protocolLabel(p), NeonIndigo)
                    if (active) Chip("فعال", StatusGreen)
                    if (p.isLocked) Chip("قفل‌دار", StatusAmber)
                    if (groupName != null) Chip(groupName, NeonCyan)
                }
                if (p.isLocked) {
                    val left = expiryLabel(p.expiresAt, System.currentTimeMillis())
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(Icons.Rounded.Lock, null, tint = StatusAmber, modifier = Modifier.size(13.dp))
                        Text(left ?: "بدون انقضا", color = StatusAmber, fontSize = 11.sp)
                    }
                }
                if (s.showServerUsage && (p.bytesDown > 0 || p.bytesUp > 0)) {
                    Text(
                        "↓ ${formatBytes(p.bytesDown)}  ↑ ${formatBytes(p.bytesUp)}",
                        color = TextTertiary, fontSize = 11.sp,
                    )
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                if (s.showServerPing) Text(pingText, color = pingColor, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                IconButton(onClick = onMore, modifier = Modifier.size(32.dp).testTag("proto_profile_more_${p.id}")) {
                    Icon(Icons.Rounded.MoreVert, "عملیات", tint = TextSecondary)
                }
            }
        }
    }
}

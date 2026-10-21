package com.helboy.vpnclub.ui.proto.editors2

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.helboy.vpnclub.ui.fa
import com.helboy.vpnclub.ui.proto.ProtoButton
import com.helboy.vpnclub.ui.proto.ProtoChoice
import com.helboy.vpnclub.ui.proto.ProtoConfirm
import com.helboy.vpnclub.ui.proto.ProtoEnv
import com.helboy.vpnclub.ui.proto.ProtoField
import com.helboy.vpnclub.ui.proto.ProtoLoading
import com.helboy.vpnclub.ui.proto.ProtoOutlined
import com.helboy.vpnclub.ui.proto.ProtoPage
import com.helboy.vpnclub.ui.proto.ProtoSection
import dev.valid7996.narcicgeteway.data.config.ConfigRepository
import dev.valid7996.narcicgeteway.domain.config.ConfigParser
import dev.valid7996.narcicgeteway.domain.config.OvpnConfig
import dev.valid7996.narcicgeteway.domain.config.ProfileSource
import dev.valid7996.narcicgeteway.domain.config.ServerConfig
import dev.valid7996.narcicgeteway.domain.config.SingBoxJson
import dev.valid7996.narcicgeteway.domain.config.VpnProfile
import dev.valid7996.narcicgeteway.ui.platform.LocalPlatform
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val TITLE = "کانفیگ دستی"

/**
 * Adds a config by hand or edits one. With [existingId] null it creates: paste or pick a link, a list of links, an
 * Xray or sing-box JSON, an .ovpn or a WireGuard / AmneziaWG config, or fill the form. With an id it edits that
 * profile: a link profile through its parsed fields (the link is regenerated), a custom or sing-box JSON in a
 * validating JSON editor. [onBack] is called after a save and when the person leaves.
 */
@Composable
fun ManualConfigEditor(existingId: String?, onBack: () -> Unit) {
    val repo = ProtoEnv.repo
    val profiles by repo.profiles.collectAsState()
    val existing = existingId?.let { id -> profiles.firstOrNull { it.id == id } }
    when {
        existingId == null -> ManualCreate(onBack)
        existing == null -> EditorUnavailable(TITLE, "این کانفیگ دیگر در فهرست نیست؛ شاید حذف شده باشد.", onBack)
        existing.isLocked -> EditorLocked(existing, onBack)
        else -> when (val src = existing.source) {
            is ProfileSource.Link -> LinkEdit(existing, src.link, onBack)
            is ProfileSource.RawJson -> JsonEdit(existing, JsonFlavour.Xray, src.json, onBack)
            is ProfileSource.SingBox -> JsonEdit(existing, JsonFlavour.SingBox, src.json, onBack)
            is ProfileSource.SingBoxConfig -> JsonEdit(existing, JsonFlavour.SingBox, src.json, onBack)
            else -> EditorUnavailable(
                TITLE,
                "این پروفایل در این ویرایشگر باز نمی‌شود؛ هر پروتکل مدیریت‌شده ویرایشگر خودش را دارد.",
                onBack,
            )
        }
    }
}

// ───────────────────────── edit: a link profile ─────────────────────────

@Composable
private fun LinkEdit(existing: VpnProfile, link: String, onBack: () -> Unit) {
    val repo = ProtoEnv.repo
    val platform = LocalPlatform.current

    val parsed: ServerConfig? = remember(existing.id) { runCatching { ConfigParser.parse(link) }.getOrNull() }
    var rawMode by remember { mutableStateOf(parsed == null) }
    var form by remember {
        mutableStateOf(parsed?.let { LinkFormRules.fromConfig(it).copy(remark = existing.name) } ?: LinkFormRules.blank())
    }
    var raw by remember { mutableStateOf(link) }
    var error by remember { mutableStateOf<String?>(null) }

    val check = remember(form) { LinkFormRules.check(form, parsed) }
    val rawProblem = remember(raw) { LinkFormRules.rawLinkProblem(raw) }
    val newLink: String? = if (rawMode) raw.trim().takeIf { rawProblem == null } else check.link

    fun save() {
        val saved = newLink ?: return
        error = null
        runCatching {
            val rebuilt = VpnProfile.fromLink(
                link = saved,
                id = existing.id,
                addedAt = existing.addedAt,
                subscriptionId = existing.subscriptionId,
            )
            repo.update(
                rebuilt.copy(
                    lastPingMs = existing.lastPingMs,
                    countryCode = existing.countryCode,
                    bytesDown = existing.bytesDown,
                    bytesUp = existing.bytesUp,
                    note = existing.note,
                    expiresAt = existing.expiresAt,
                ),
            )
        }.onSuccess {
            platform.toast(SAVED_TEXT)
            onBack()
        }.onFailure {
            error = "ذخیره نشد: ${it.message?.take(120) ?: "خطای نامشخص"}"
        }
    }

    ProtoPage(title = "ویرایش ${existing.name}", onBack = onBack) {
        if (parsed == null) {
            EditorNote(
                "این لینک در فرم باز نمی‌شود، چون هسته آن را به‌صورت لینک ساده نمی‌خواند. متن لینک را مستقیم ویرایش کنید.",
                NoteTone.Warn,
                tag = "proto_manual_notform",
            )
        } else {
            ProtoChoice(
                title = null,
                options = listOf("form" to "فرم کامل", "raw" to "لینک خام"),
                selected = if (rawMode) "raw" else "form",
                onSelect = { pick ->
                    if (pick == "raw" && !rawMode) {
                        check.link?.let { raw = it }
                    } else if (pick == "form" && rawMode) {
                        runCatching { ConfigParser.parse(raw.trim()) }.getOrNull()?.let {
                            form = LinkFormRules.fromConfig(it).copy(remark = it.remark.ifBlank { existing.name })
                        }
                    }
                    rawMode = pick == "raw"
                },
            )
        }

        if (rawMode) {
            ProtoSection(title = "لینک خام", hint = "لینک همان‌طور که نوشته شود ذخیره می‌شود.") {
                ProtoField(
                    label = "لینک کانفیگ",
                    value = raw,
                    onValueChange = { raw = it },
                    singleLine = false,
                    minLines = 4,
                    monospace = true,
                    error = rawProblem,
                    tag = "proto_manual_raw",
                )
            }
        } else {
            LinkFormFields(form = form, check = check, creating = false, onChange = { form = it })
        }

        LinkPreview(newLink = newLink, original = link)
        error?.let { EditorNote(it, NoteTone.Error, tag = "proto_manual_error") }
        ProtoButton(
            text = "ذخیره",
            icon = Icons.Rounded.Check,
            onClick = { save() },
            enabled = newLink != null,
            tag = "proto_save",
        )
    }
}

/** The link the form currently produces, with copy and share, and whether it differs from the stored one. */
@Composable
private fun LinkPreview(newLink: String?, original: String?) {
    val platform = LocalPlatform.current
    ProtoSection(title = "پیش‌نمایش لینک") {
        if (newLink == null) {
            EditorNote("تا خطاهای بالا درست نشود لینکی ساخته نمی‌شود.", NoteTone.Warn, tag = "proto_manual_preview_none")
        } else {
            EditorCode(newLink, tag = "proto_manual_preview", maxLines = 8)
            if (original != null) {
                EditorNote(
                    if (newLink == original) "لینک نسبت به نسخهٔ ذخیره‌شده تغییری نکرده است." else "لینک تغییر کرده و با ذخیره جایگزین می‌شود.",
                    if (newLink == original) NoteTone.Info else NoteTone.Ok,
                    tag = "proto_manual_changed",
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ProtoOutlined(
                    text = "کپی لینک",
                    onClick = {
                        platform.copyToClipboard(newLink)
                        platform.toast("لینک کپی شد")
                    },
                    tag = "proto_manual_copy",
                )
                ProtoOutlined(text = "اشتراک", onClick = { platform.shareText(newLink) }, tag = "proto_manual_share")
            }
        }
    }
}

// ───────────────────────── edit: a JSON profile ─────────────────────────

@Composable
private fun JsonEdit(existing: VpnProfile, flavour: JsonFlavour, initial: String, onBack: () -> Unit) {
    val repo = ProtoEnv.repo
    val platform = LocalPlatform.current
    var text by remember { mutableStateOf(initial) }
    val result = remember(text, flavour) { JsonRules.check(text, flavour) }
    var saveError by remember(text) { mutableStateOf<String?>(null) }

    fun save() {
        val body = text.trim()
        val r = if (flavour == JsonFlavour.Xray) repo.updateRawJson(existing.id, body) else repo.updateSingBox(existing.id, body)
        r.onSuccess {
            platform.toast(SAVED_TEXT)
            onBack()
        }.onFailure {
            saveError = it.message?.take(160)?.ifBlank { null }
                ?: if (flavour == JsonFlavour.Xray) JsonRules.XRAY_INVALID else JsonRules.SINGBOX_INVALID
        }
    }

    ProtoPage(
        title = if (flavour == JsonFlavour.Xray) "کانفیگ سفارشی (JSON)" else "JSON برای sing-box",
        onBack = onBack,
    ) {
        EditorNote(
            if (flavour == JsonFlavour.Xray) "یک کانفیگ JSON هستهٔ Xray." else "یک کانفیگ یا سرور sing-box.",
        )
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ProtoOutlined(
                text = "مرتب‌سازی",
                onClick = { JsonRules.pretty(text)?.let { text = it } },
                enabled = result.parsed,
                tag = "proto_manual_json_format",
            )
            ProtoOutlined(
                text = "فشرده",
                onClick = { JsonRules.minify(text)?.let { text = it } },
                enabled = result.parsed,
                tag = "proto_manual_json_minify",
            )
            ProtoOutlined(
                text = "کپی",
                onClick = {
                    platform.copyToClipboard(text)
                    platform.toast("کپی شد")
                },
                tag = "proto_manual_json_copy",
            )
            ProtoOutlined(
                text = "جای‌گذاری",
                onClick = { platform.readClipboard()?.takeIf { it.isNotBlank() }?.let { text = it } },
                tag = "proto_manual_json_paste",
            )
        }
        EditorNote(
            saveError ?: result.message,
            if (result.ok && saveError == null) NoteTone.Ok else NoteTone.Error,
            tag = "proto_manual_json_status",
        )
        ProtoField(
            label = "متن JSON",
            value = text,
            onValueChange = { text = it },
            singleLine = false,
            minLines = 14,
            monospace = true,
            tag = "proto_manual_json",
        )
        ProtoButton(
            text = "ذخیره",
            icon = Icons.Rounded.Check,
            onClick = { save() },
            enabled = result.ok,
            tag = "proto_save",
        )
    }
}

// ───────────────────────── create ─────────────────────────

private data class ImportOutcome(
    val count: Int,
    val duplicates: Int = 0,
    val subscriptionName: String? = null,
)

/** Imports [text] the way its kind needs; runs off the main thread, throws what the engine throws. */
private suspend fun importPastedText(
    repo: ConfigRepository,
    kind: PasteKind,
    text: String,
    name: String,
    user: String,
    pass: String,
): ImportOutcome = withContext(Dispatchers.IO) {
    val body = text.trim()
    val chosen = name.trim()
    when {
        kind == PasteKind.Ovpn -> {
            val info = OvpnConfig.inspect(body)
            repo.addOvpn(
                body,
                name = chosen.ifBlank { info.name },
                username = user.ifBlank { null },
                password = pass.ifBlank { null },
            ).getOrThrow()
            ImportOutcome(1)
        }
        kind is PasteKind.XrayJson && !kind.isList && chosen.isNotEmpty() -> {
            repo.addRawJson(body, name = chosen).getOrThrow()
            ImportOutcome(1)
        }
        kind is PasteKind.SingBox && kind.shape == SingBoxJson.Shape.FullConfig && chosen.isNotEmpty() -> {
            repo.addSingBoxConfig(body, name = chosen).getOrThrow()
            ImportOutcome(1)
        }
        else -> {
            val r = repo.importPasted(body).getOrThrow()
            ImportOutcome(r.count, r.duplicates, r.subscriptionName.takeIf { r.subscription && it.isNotBlank() })
        }
    }
}

@Composable
private fun ManualCreate(onBack: () -> Unit) {
    val repo = ProtoEnv.repo
    val platform = LocalPlatform.current
    val scope = rememberCoroutineScope()

    var mode by remember { mutableStateOf("paste") }
    var text by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var user by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var running by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf<Pair<String, NoteTone>?>(null) }
    var obfuscate by remember { mutableStateOf<List<String>>(emptyList()) }
    var form by remember { mutableStateOf(LinkFormRules.blank()) }

    // Recognising a long pasted subscription is real work: it runs off the main thread.
    val kind by produceState<PasteKind?>(initialValue = null, key1 = text) {
        value = if (text.isBlank()) PasteKind.Empty else withContext(Dispatchers.Default) { PasteDetector.detect(text) }
    }
    val ovpnInfo = remember(text, kind) {
        if (kind == PasteKind.Ovpn) runCatching { OvpnConfig.inspect(text) } else null
    }
    val formCheck = remember(form) { LinkFormRules.check(form) }

    val nameUsed = when (val k = kind) {
        PasteKind.Ovpn -> true
        is PasteKind.XrayJson -> !k.isList
        is PasteKind.SingBox -> k.shape == SingBoxJson.Shape.FullConfig
        else -> false
    }

    /** After something was added: offer the WireGuard obfuscation, or leave. */
    fun finished(before: Set<String>, message: String) {
        val fresh = repo.profiles.value.map { it.id }.filterNot { it in before }
        val plain = repo.plainWireguardAmong(fresh)
        if (plain.isNotEmpty()) {
            note = message to NoteTone.Ok
            obfuscate = plain
        } else {
            platform.toast(message)
            onBack()
        }
    }

    fun addPasted() {
        val k = kind ?: return
        running = true
        note = null
        val before = repo.profiles.value.map { it.id }.toSet()
        scope.launch {
            val result = runCatching { importPastedText(repo, k, text, name, user, pass) }
            running = false
            result.onSuccess { out ->
                when {
                    out.count > 0 -> finished(
                        before,
                        if (out.subscriptionName != null) {
                            "اشتراک «${out.subscriptionName}» اضافه شد — ${out.count.fa()} سرور"
                        } else {
                            "${out.count.fa()} سرور اضافه شد"
                        },
                    )
                    out.duplicates > 0 -> note = "از قبل در فهرست هست (${out.duplicates.fa()})" to NoteTone.Warn
                    else -> note = ImportErrors.GENERIC to NoteTone.Error
                }
            }.onFailure { note = ImportErrors.text(it) to NoteTone.Error }
        }
    }

    fun addFromForm() {
        val link = formCheck.link ?: return
        note = null
        val before = repo.profiles.value.map { it.id }.toSet()
        repo.importText(link)
            .onSuccess { n ->
                if (n > 0) finished(before, "${n.fa()} سرور اضافه شد")
                else note = "این سرور از قبل در فهرست هست." to NoteTone.Warn
            }
            .onFailure { note = ImportErrors.text(it) to NoteTone.Error }
    }

    ProtoPage(title = "افزودن دستی", onBack = onBack) {
        ProtoChoice(
            title = null,
            options = listOf("paste" to "چسباندن متن یا فایل", "form" to "ساخت با فرم"),
            selected = mode,
            onSelect = { mode = it; note = null },
        )

        if (mode == "paste") {
            ProtoSection(
                title = "متن کانفیگ",
                hint = "لینک، چند لینک (هر خط یکی)، نشانی اشتراک، JSON هستهٔ Xray یا sing-box، فایل OpenVPN، یا کانفیگ WireGuard / AmneziaWG.",
            ) {
                ProtoField(
                    label = "متن کانفیگ",
                    value = text,
                    onValueChange = { text = it; note = null },
                    singleLine = false,
                    minLines = 6,
                    monospace = true,
                    tag = "proto_manual_paste",
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ProtoOutlined(
                        text = "از کلیپ‌بورد",
                        onClick = {
                            val clip = platform.readClipboard()
                            if (clip.isNullOrBlank()) platform.toast("کلیپ‌بورد خالی است") else { text = clip; note = null }
                        },
                        tag = "proto_manual_clipboard",
                    )
                    ProtoOutlined(
                        text = "از فایل",
                        onClick = {
                            scope.launch {
                                val pick = platform.pickFileBytes() ?: return@launch
                                if (pick.bytes.isEmpty()) {
                                    note = "فایل خالی است." to NoteTone.Error
                                } else {
                                    text = pick.bytes.decodeToString()
                                    note = null
                                    if (name.isBlank()) name = pick.name.substringBeforeLast('.').takeIf { it.isNotBlank() }.orEmpty()
                                }
                            }
                        },
                        tag = "proto_manual_file",
                    )
                }
                val k = kind
                if (k == null) {
                    ProtoLoading("در حال بررسی متن…")
                } else if (k != PasteKind.Empty) {
                    EditorNote(
                        PasteDetector.label(k),
                        if (k.importable) NoteTone.Ok else NoteTone.Error,
                        tag = "proto_manual_kind",
                    )
                }
                if (nameUsed) {
                    ProtoField(
                        label = "نام (اختیاری)",
                        value = name,
                        onValueChange = { name = it },
                        tag = "proto_manual_import_name",
                    )
                }
                ovpnInfo?.let { r ->
                    r.onSuccess { info ->
                        EditorNote("سرور OpenVPN: ${info.host}:${info.port.fa()} (${info.network})")
                        if (info.needsCredentials && !info.hasInlineCredentials) {
                            EditorNote("این پروفایل نام کاربری و رمز عبور می‌خواهد.", NoteTone.Warn)
                            ProtoField("نام کاربری", user, { user = it }, tag = "proto_manual_ovpn_user")
                            ProtoField("رمز عبور", pass, { pass = it }, password = true, tag = "proto_manual_ovpn_pass")
                        }
                    }.onFailure { EditorNote(ImportErrors.text(it), NoteTone.Error, tag = "proto_manual_ovpn_error") }
                }
            }

            note?.let { EditorNote(it.first, it.second, tag = "proto_manual_note") }
            val single = (kind as? PasteKind.Links)?.single
            val singleParsed = remember(single) { single?.let { runCatching { ConfigParser.parse(it) }.getOrNull() } }
            if (singleParsed != null) {
                ProtoOutlined(
                    text = "بارگذاری در فرم برای ویرایش",
                    onClick = {
                        form = LinkFormRules.fromConfig(singleParsed)
                        mode = "form"
                    },
                    tag = "proto_manual_to_form",
                )
            }
            ProtoButton(
                text = "افزودن",
                icon = Icons.Rounded.Add,
                onClick = { addPasted() },
                enabled = kind?.importable == true,
                running = running,
                tag = "proto_save",
            )
        } else {
            LinkFormFields(form = form, check = formCheck, creating = true, onChange = { form = it })
            LinkPreview(newLink = formCheck.link, original = null)
            note?.let { EditorNote(it.first, it.second, tag = "proto_manual_note") }
            ProtoButton(
                text = "افزودن",
                icon = Icons.Rounded.Add,
                onClick = { addFromForm() },
                enabled = formCheck.valid,
                tag = "proto_save",
            )
        }
    }

    if (obfuscate.isNotEmpty()) {
        val ids = obfuscate
        ProtoConfirm(
            title = "وایرگارد مبهم‌سازی شود؟",
            text = "${ids.size.fa()} کانفیگ وایرگارد معمولی وارد شد. مبهم‌سازی (AmneziaWG) دست‌دادن اولیه را از DPI پنهان می‌کند، " +
                "ولی فقط وقتی کار می‌کند که سرور هم AmneziaWG با همین پارامترها اجرا کند.",
            confirmLabel = "مبهم‌سازی کن",
            onConfirm = {
                val changed = repo.enableWireguardObfuscation(ids)
                obfuscate = emptyList()
                platform.toast("مبهم‌سازی روی ${changed.fa()} کانفیگ فعال شد")
                onBack()
            },
            onDismiss = {
                obfuscate = emptyList()
                onBack()
            },
        )
    }
}

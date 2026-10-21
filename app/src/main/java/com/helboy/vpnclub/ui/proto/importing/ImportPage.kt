package com.helboy.vpnclub.ui.proto.importing

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.sp
import com.helboy.vpnclub.ui.proto.ProtoButton
import com.helboy.vpnclub.ui.proto.ProtoEnv
import com.helboy.vpnclub.ui.proto.ProtoField
import com.helboy.vpnclub.ui.proto.ProtoLoading
import com.helboy.vpnclub.ui.proto.ProtoOutlined
import com.helboy.vpnclub.ui.proto.ProtoPage
import com.helboy.vpnclub.ui.proto.ProtoSection
import com.helboy.vpnclub.ui.theme.BorderDark
import com.helboy.vpnclub.ui.theme.NeonCyan
import com.helboy.vpnclub.ui.theme.StatusGreen
import com.helboy.vpnclub.ui.theme.StatusRed
import com.helboy.vpnclub.ui.theme.TextSecondary
import com.helboy.vpnclub.ui.theme.TextTertiary
import dev.valid7996.narcicgeteway.crypto.ZsxMetadata
import dev.valid7996.narcicgeteway.ui.platform.FilePick
import dev.valid7996.narcicgeteway.ui.platform.LocalPlatform
import dev.valid7996.narcicgeteway.ui.platform.Platform
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Brings profiles in: pasted links / configs / JSON / base64 / subscription addresses, a QR code (camera or image),
 * or a file. Sealed (.nrg) configs ask for their key; a bare web address is offered as a subscription.
 */
@Composable
fun ImportPage(onBack: () -> Unit, onImported: () -> Unit) {
    val platform = LocalPlatform.current
    val scope = rememberCoroutineScope()

    var text by remember { mutableStateOf("") }
    var running by remember { mutableStateOf(false) }
    var summary by remember { mutableStateOf<ImportSummary?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var sourceNote by remember { mutableStateOf<String?>(null) }

    var offerUrl by remember { mutableStateOf<String?>(null) }
    var subName by remember { mutableStateOf("") }

    var unlock by remember { mutableStateOf<Pair<ByteArray, ZsxMetadata>?>(null) }
    var relayText by remember { mutableStateOf<String?>(null) }
    var ovpn by remember { mutableStateOf<Pair<String, String>?>(null) }

    val kind = remember(text) { ImportLogic.classify(text.take(100_000)) }

    fun handle(outcome: ImportOutcome) {
        when (outcome) {
            is ImportOutcome.Done -> {
                error = null
                offerUrl = null
                summary = outcome.summary
                platform.toast(ImportLogic.formatSummary(outcome.summary))
                if (outcome.summary.changedList) {
                    text = ""
                    subName = ""
                    onImported()
                }
            }
            is ImportOutcome.OfferSubscription -> {
                summary = null
                error = null
                offerUrl = outcome.url
            }
            is ImportOutcome.NeedKey -> unlock = outcome.bytes to outcome.meta
            is ImportOutcome.NeedRelayKey -> relayText = outcome.text
            is ImportOutcome.NeedOvpnCredentials -> ovpn = outcome.text to outcome.name
            is ImportOutcome.Failed -> {
                summary = null
                error = outcome.message
            }
        }
    }

    fun launchImport(block: suspend () -> ImportOutcome) {
        if (running) return
        running = true
        error = null
        summary = null
        scope.launch {
            val outcome = try {
                block()
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Throwable) {
                ImportOutcome.Failed(ImportLogic.describeError(e))
            }
            running = false
            handle(outcome)
        }
    }

    fun importNow(raw: String, fileName: String? = null) {
        val repo = ProtoEnv.repo
        sourceNote = fileName
        launchImport { ImportEngine.importText(repo, raw, fileName) }
    }

    fun importFile() {
        if (running) return
        scope.launch {
            val picked = pickSafely(platform) ?: return@launch
            val decoded = withContext(Dispatchers.Default) { ImportLogic.decodePickedFile(picked.name, picked.bytes) }
            if (decoded == null) {
                summary = null
                error = "فایل «${picked.name}» خالی است یا خوانده نمی‌شود."
            } else {
                importNow(decoded, picked.name)
            }
        }
    }

    ProtoPage(title = "ورود کانفیگ", onBack = onBack) {
        ProtoSection(
            title = "متن، لینک یا اشتراک",
            hint = "لینک vless / vmess / trojan / ss / hysteria / wireguard، چند لینک زیر هم، JSON، فایل OpenVPN یا WireGuard، " +
                "نشانی اشتراک و لینک‌های قفل‌شدهٔ nrg:// را اینجا بگذارید.",
        ) {
            ProtoField(
                label = "کانفیگ", value = text, onValueChange = { text = it; error = null },
                placeholder = "vless://…", singleLine = false, minLines = 5, monospace = true,
                enabled = !running, tag = "proto_import_text",
            )
            if (kind != InputKind.Empty) {
                Text("تشخیص: ${kind.label}", color = TextTertiary, fontSize = 11.sp, modifier = Modifier.testTag("proto_import_kind"))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                ProtoOutlined(
                    "جای‌گذاری از کلیپ‌بورد", enabled = !running, modifier = Modifier.weight(1f).testTag("proto_import_paste"),
                    onClick = {
                        val clip = platform.readClipboard()
                        if (clip.isNullOrBlank()) platform.toast("کلیپ‌بورد خالی است.") else { text = clip; error = null }
                    },
                )
                ProtoOutlined(
                    "انتخاب فایل", enabled = !running, modifier = Modifier.weight(1f).testTag("proto_import_file"),
                    onClick = { importFile() },
                )
            }
            if (platform.supportsQrScan) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    ProtoOutlined(
                        "اسکن QR با دوربین", enabled = !running, modifier = Modifier.weight(1f).testTag("proto_import_scan"),
                        onClick = {
                            platform.scanQrCode { result ->
                                if (!result.isNullOrBlank()) scope.launch { text = result; importNow(result) }
                            }
                        },
                    )
                    ProtoOutlined(
                        "خواندن QR از عکس", enabled = !running, modifier = Modifier.weight(1f).testTag("proto_import_qr_image"),
                        onClick = {
                            platform.scanQrFromImage { result ->
                                if (!result.isNullOrBlank()) scope.launch { text = result; importNow(result) }
                            }
                        },
                    )
                }
            }
            ProtoButton(
                text = if (running) "در حال ورود…" else "ورود کانفیگ", icon = Icons.Rounded.Download,
                onClick = { importNow(text) }, running = running, enabled = text.isNotBlank(), tag = "proto_import_button",
            )
        }

        if (running) ProtoLoading("در حال بررسی و ورود…")

        offerUrl?.let { url ->
            ProtoSection(
                title = "این یک نشانی اشتراک است",
                hint = "با اشتراک، لیست سرورها از این نشانی دریافت می‌شود و می‌توانید هر وقت خواستید به‌روزش کنید.",
            ) {
                Text(ImportLogic.shortUrl(url, 60), color = TextSecondary, fontSize = 12.sp, modifier = Modifier.testTag("proto_import_offer_url"))
                ProtoField(
                    label = "نام اشتراک (اختیاری)", value = subName, onValueChange = { subName = it },
                    enabled = !running, tag = "proto_import_sub_name",
                )
                ProtoButton(
                    text = "افزودن به‌عنوان اشتراک", icon = Icons.Rounded.Link, running = running, tag = "proto_import_sub_add",
                    onClick = {
                        val repo = ProtoEnv.repo
                        launchImport { ImportEngine.addSubscription(repo, url, subName) }
                    },
                )
            }
        }

        summary?.let { s ->
            ProtoSection(title = "نتیجه", border = if (s.imported > 0) NeonCyan else BorderDark) {
                Text(
                    ImportLogic.formatSummary(s), color = if (s.imported > 0 || s.savedOffline) StatusGreen else TextSecondary,
                    fontSize = 13.sp, modifier = Modifier.testTag("proto_import_summary"),
                )
                sourceNote?.let { Text("فایل: $it", color = TextTertiary, fontSize = 11.sp) }
                s.firstFailure?.let { StatusLine("نخستین خطا: $it", StatusRed, tag = "proto_import_first_failure") }
            }
        }

        error?.let { StatusLine(it, StatusRed, tag = "proto_import_error") }
    }

    unlock?.let { (bytes, meta) ->
        UnlockDialog(
            bytes = bytes, meta = meta,
            onDismiss = { unlock = null },
            onUnlocked = {
                unlock = null
                text = ""
                summary = ImportSummary(imported = 1)
                error = null
                onImported()
            },
        )
    }

    relayText?.let { pending ->
        RelayKeyDialog(
            onDismiss = { relayText = null },
            onSubmit = { key ->
                relayText = null
                val repo = ProtoEnv.repo
                launchImport { ImportEngine.importRelayWithKey(repo, pending, key) }
            },
        )
    }

    ovpn?.let { (config, name) ->
        OvpnCredentialsDialog(
            name = name,
            onDismiss = { ovpn = null },
            onSubmit = { user, pass ->
                ovpn = null
                val repo = ProtoEnv.repo
                launchImport { ImportEngine.addOvpn(repo, config, name, user, pass) }
            },
        )
    }
}

private suspend fun pickSafely(platform: Platform): FilePick? = try {
    platform.pickFileBytes()
} catch (e: kotlinx.coroutines.CancellationException) {
    throw e
} catch (e: Throwable) {
    null
}

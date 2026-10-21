package com.helboy.vpnclub.ui.proto.importing

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.helboy.vpnclub.ui.fa
import com.helboy.vpnclub.ui.proto.ProtoButton
import com.helboy.vpnclub.ui.proto.ProtoChoice
import com.helboy.vpnclub.ui.proto.ProtoEmpty
import com.helboy.vpnclub.ui.proto.ProtoEnv
import com.helboy.vpnclub.ui.proto.ProtoField
import com.helboy.vpnclub.ui.proto.ProtoOutlined
import com.helboy.vpnclub.ui.proto.ProtoPage
import com.helboy.vpnclub.ui.proto.ProtoSection
import com.helboy.vpnclub.ui.proto.ProtoSwitch
import com.helboy.vpnclub.ui.theme.StatusAmber
import com.helboy.vpnclub.ui.theme.StatusGreen
import com.helboy.vpnclub.ui.theme.StatusRed
import com.helboy.vpnclub.ui.theme.TextPrimary
import com.helboy.vpnclub.ui.theme.TextSecondary
import com.helboy.vpnclub.ui.theme.TextTertiary
import dev.valid7996.narcicgeteway.crypto.NrgLink
import dev.valid7996.narcicgeteway.crypto.ZsxCrypto
import dev.valid7996.narcicgeteway.crypto.ZsxSealRequest
import dev.valid7996.narcicgeteway.data.config.NrgRelay
import dev.valid7996.narcicgeteway.ui.platform.LocalPlatform
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val MODE_DEVICE = "device"
private const val MODE_KEY = "key"
private const val MODE_PUBLIC = "public"

private val MODE_OPTIONS = listOf(
    MODE_DEVICE to "قفل روی دستگاه خریدار",
    MODE_KEY to "قفل با کلید یک‌بار مصرف",
    MODE_PUBLIC to "عمومی (بدون قفل دستگاه)",
)

private class LockResult(
    val link: String,
    val sealed: ByteArray,
    val title: String,
    val expiresAt: Long?,
    val deviceCode: String?,
    val key: String?,
    val note: String,
    val relayNote: String?,
)

/**
 * Sharing one profile: the plain link and QR, and an encrypted (.nrg) export that can be bound to the buyer's device,
 * to a one-time key, or left public, with an expiry and an optional short-link relay.
 */
@Composable
fun LockSharePage(profileId: String, onBack: () -> Unit) {
    val repo = ProtoEnv.repo
    val platform = LocalPlatform.current
    val scope = rememberCoroutineScope()
    val profiles by repo.profiles.collectAsState()
    val profile = profiles.firstOrNull { it.id == profileId }
    val plainLink = remember(profile) { profile?.let { p -> runCatching { repo.shareLinkOf(p) }.getOrNull() } }

    var title by remember(profileId) { mutableStateOf(profile?.name.orEmpty()) }
    var note by remember(profileId) { mutableStateOf("") }
    var mode by remember(profileId) { mutableStateOf(MODE_DEVICE) }
    var buyerCode by remember(profileId) { mutableStateOf("") }
    var key by remember(profileId) { mutableStateOf("") }
    var expiryCode by remember(profileId) { mutableStateOf("5") }
    var days by remember(profileId) { mutableStateOf("0") }
    var hours by remember(profileId) { mutableStateOf("0") }
    var minutes by remember(profileId) { mutableStateOf("5") }
    var useRelay by remember(profileId) { mutableStateOf(repo.nrgRelayEndpoint.isNotBlank()) }
    var relayUrl by remember(profileId) { mutableStateOf(repo.nrgRelayEndpoint) }
    var relayToken by remember(profileId) { mutableStateOf(repo.nrgRelayToken) }
    var generating by remember(profileId) { mutableStateOf(false) }
    var error by remember(profileId) { mutableStateOf<String?>(null) }
    var result by remember(profileId) { mutableStateOf<LockResult?>(null) }
    var showPlainQr by remember(profileId) { mutableStateOf(false) }
    var showLockedQr by remember(profileId) { mutableStateOf(false) }

    fun generate(payload: String) {
        if (generating) return
        val problem = ImportLogic.validateLockOptions(mode, buyerCode.trim(), key.trim())
        if (problem != null) {
            error = problem
            return
        }
        error = null
        generating = true
        scope.launch {
            try {
                val now = System.currentTimeMillis()
                val expiresAt = ImportLogic.expiryFor(
                    expiryCode.toIntOrNull() ?: 0, now,
                    days.toIntOrNull() ?: 0, hours.toIntOrNull() ?: 0, minutes.toIntOrNull() ?: 0,
                )
                val device = buyerCode.trim()
                val userKey = key.trim()
                val name = title.trim().ifEmpty { profile?.name.orEmpty() }
                val request = ZsxSealRequest(
                    configPayload = payload,
                    nameEn = name, nameFa = name, note = note,
                    expiresAt = expiresAt,
                    password = when (mode) {
                        MODE_DEVICE -> device
                        MODE_KEY -> userKey
                        else -> null
                    },
                    deviceBound = mode == MODE_DEVICE,
                )
                // Key stretching is slow on purpose: never on the main thread.
                val sealed = withContext(Dispatchers.Default) { repo.createLockedZsx(request) }
                var link = NrgLink.encode(sealed)
                var relayNote: String? = null
                if (useRelay) {
                    if (NrgRelay.hostOf(relayUrl) == null) {
                        relayNote = "آدرس سرور رله نامعتبر است؛ لینک کامل ساخته شد."
                    } else {
                        try {
                            val relay = withContext(Dispatchers.IO) {
                                NrgRelay.upload(relayUrl, relayToken.ifBlank { null }, sealed, expiresAt, if (mode == MODE_KEY) userKey else null)
                            }
                            link = NrgLink.relayLink(relay)
                            repo.nrgRelayEndpoint = relayUrl
                            repo.nrgRelayToken = relayToken
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Throwable) {
                            relayNote = "بارگذاری روی سرور رله ناموفق بود؛ لینک کامل ساخته شد."
                        }
                    }
                }
                result = LockResult(
                    link = link, sealed = sealed, title = name, expiresAt = expiresAt,
                    deviceCode = device.takeIf { mode == MODE_DEVICE },
                    key = userKey.takeIf { mode == MODE_KEY },
                    note = note, relayNote = relayNote,
                )
                showLockedQr = false
                platform.toast("کانفیگ قفل‌شده ساخته شد.")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                error = "ساخت کانفیگ قفل‌شده ناموفق بود؛ دوباره تلاش کنید."
            } finally {
                generating = false
            }
        }
    }

    ProtoPage(title = "اشتراک‌گذاری کانفیگ", onBack = onBack) {
        if (profile == null) {
            ProtoEmpty(title = "این کانفیگ پیدا نشد", hint = "ممکن است حذف شده باشد؛ به لیست برگردید و دوباره امتحان کنید.")
        } else {
            Text(profile.name, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp, maxLines = 2)

            if (plainLink == null) {
                ProtoSection(title = "قابل اشتراک نیست", border = StatusAmber) {
                    Text(
                        if (profile.isLocked) "کانفیگ قفل‌شده را نمی‌توان دوباره منتشر کرد." else "این نوع کانفیگ لینک اشتراک ندارد.",
                        color = TextSecondary, fontSize = 13.sp, modifier = Modifier.testTag("proto_lock_unavailable"),
                    )
                }
            } else {
                PlainShareSection(plainLink, showPlainQr, onToggleQr = { showPlainQr = !showPlainQr })

                ProtoSection(
                    title = "اشتراک قفل‌شده (NRG)",
                    hint = "کانفیگ رمز می‌شود؛ گیرنده فقط با لینک و کلید یا کد دستگاه درست می‌تواند آن را باز کند.",
                ) {
                    ProtoField(label = "عنوان", value = title, onValueChange = { title = it }, tag = "proto_lock_title")
                    ProtoField(label = "یادداشت یا نام خریدار (اختیاری)", value = note, onValueChange = { note = it })

                    ProtoChoice(
                        title = "نوع قفل", options = MODE_OPTIONS, selected = mode, onSelect = { mode = it; error = null },
                        hint = when (mode) {
                            MODE_DEVICE -> "فقط روی دستگاهی باز می‌شود که کدش را وارد می‌کنید."
                            MODE_KEY -> "با کلید باز می‌شود و بعد از اولین استفاده کلید باطل می‌شود."
                            else -> "هر کسی که لینک را دارد می‌تواند با برنامه باز کند؛ فقط متن کانفیگ رمز است."
                        },
                    )
                    when (mode) {
                        MODE_DEVICE -> {
                            ProtoField(
                                label = "کد دستگاه خریدار", value = buyerCode, onValueChange = { buyerCode = it; error = null },
                                placeholder = "کد را از خریدار بگیرید", monospace = true, tag = "proto_lock_device",
                            )
                            ProtoOutlined("جای‌گذاری کد از کلیپ‌بورد", onClick = {
                                val clip = platform.readClipboard()
                                if (!clip.isNullOrBlank()) { buyerCode = clip.trim(); error = null }
                            })
                        }
                        MODE_KEY -> {
                            ProtoField(
                                label = "کلید", value = key, onValueChange = { key = it; error = null },
                                placeholder = "NG-XXXX-XXXX-XXXX", monospace = true, tag = "proto_lock_key",
                            )
                            ProtoOutlined("ساخت کلید جدید", onClick = {
                                key = ImportLogic.userKeyFrom(ZsxCrypto.secureRandomBytes(12))
                                error = null
                            }, tag = "proto_lock_new_key")
                        }
                    }

                    ProtoChoice(
                        title = "مدت اعتبار",
                        options = ImportLogic.EXPIRY_OPTIONS.map { it.code.toString() to it.label },
                        selected = expiryCode, onSelect = { expiryCode = it },
                    )
                    if (expiryCode == "8") {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ProtoField("روز", days, { days = it.filter(Char::isDigit).take(4) }, Modifier.weight(1f))
                            ProtoField("ساعت", hours, { hours = it.filter(Char::isDigit).take(3) }, Modifier.weight(1f))
                            ProtoField("دقیقه", minutes, { minutes = it.filter(Char::isDigit).take(4) }, Modifier.weight(1f))
                        }
                    }

                    ProtoSwitch(
                        title = "لینک کوتاه با سرور رله", checked = useRelay, onChange = { useRelay = it },
                        subtitle = "اختیاری: کانفیگ رمزشده روی سرور خودتان می‌ماند و در لینک فقط یک شناسه است.",
                    )
                    if (useRelay) {
                        ProtoField(
                            label = "نشانی سرور رله", value = relayUrl, onValueChange = { relayUrl = it },
                            placeholder = "https://nrg.example.workers.dev", monospace = true,
                        )
                        ProtoField(label = "توکن بارگذاری (اختیاری)", value = relayToken, onValueChange = { relayToken = it }, monospace = true)
                    }

                    error?.let { StatusLine(it, StatusRed, tag = "proto_lock_error") }
                    ProtoButton(
                        text = if (generating) "در حال ساخت…" else "ساخت کانفیگ قفل‌شده", icon = Icons.Rounded.Lock,
                        running = generating, onClick = { generate(plainLink) }, accent = StatusAmber, tag = "proto_lock_generate",
                    )
                }

                result?.let { r -> LockedResultSection(r, showLockedQr, onToggleQr = { showLockedQr = !showLockedQr }) }
            }

            ProtoSection(
                title = "کد دستگاه من",
                hint = "اگر کسی می‌خواهد کانفیگی فقط برای همین گوشی بسازد، این کد را برایش بفرستید.",
            ) { DeviceCodeRow() }
        }
    }
}

@Composable
private fun PlainShareSection(link: String, showQr: Boolean, onToggleQr: () -> Unit) {
    val platform = LocalPlatform.current
    ProtoSection(title = "لینک ساده", hint = "هر برنامه‌ای که این نوع لینک را بشناسد می‌تواند آن را وارد کند. رمز نشده است.") {
        Text(
            link, color = TextPrimary, fontFamily = FontFamily.Monospace, fontSize = 11.sp, maxLines = 4,
            modifier = Modifier.fillMaxWidth().background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(8.dp)).padding(8.dp)
                .testTag("proto_share_link"),
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ProtoOutlined("کپی", modifier = Modifier.weight(1f).testTag("proto_share_copy"), onClick = {
                platform.copyToClipboard(link)
                platform.toast("لینک کپی شد")
            })
            ProtoOutlined("اشتراک", modifier = Modifier.weight(1f).testTag("proto_share_send"), onClick = { platform.shareText(link) })
            ProtoOutlined(if (showQr) "بستن QR" else "QR", modifier = Modifier.weight(1f).testTag("proto_share_qr"), onClick = onToggleQr)
        }
        if (showQr) ProtoQr(link)
    }
}

@Composable
private fun LockedResultSection(r: LockResult, showQr: Boolean, onToggleQr: () -> Unit) {
    val platform = LocalPlatform.current
    val now = System.currentTimeMillis()
    val message = remember(r) { ImportLogic.buildShareMessage(r.title, r.link, r.expiresAt, now, r.deviceCode, r.key, r.note) }

    ProtoSection(title = "آمادهٔ ارسال", border = StatusGreen) {
        Text(
            "اعتبار: ${ImportLogic.remainingText(r.expiresAt, now)} · طول لینک ${r.link.length.fa()} نویسه" +
                if (r.link.startsWith("nrg://r.")) " (لینک کوتاه)" else "",
            color = TextSecondary, fontSize = 12.sp, modifier = Modifier.testTag("proto_lock_result"),
        )
        r.relayNote?.let { StatusLine(it, StatusAmber) }
        Text(
            r.link, color = TextPrimary, fontFamily = FontFamily.Monospace, fontSize = 11.sp, maxLines = 3,
            modifier = Modifier.fillMaxWidth().background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(8.dp)).padding(8.dp)
                .testTag("proto_lock_link"),
        )
        r.deviceCode?.let { Text("قفل‌شده برای کد دستگاه: $it", color = StatusAmber, fontSize = 12.sp, fontFamily = FontFamily.Monospace) }
        r.key?.let {
            Text("کلید: $it", color = StatusAmber, fontSize = 13.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
            Text("کلید را ترجیحاً جدا از لینک بفرستید؛ بعد از اولین استفاده باطل می‌شود.", color = TextTertiary, fontSize = 11.sp)
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ProtoOutlined("کپی لینک", modifier = Modifier.weight(1f), onClick = {
                platform.copyToClipboard(r.link)
                platform.toast("لینک کپی شد")
            })
            ProtoOutlined("کپی پیام کامل", modifier = Modifier.weight(1f).testTag("proto_lock_copy_message"), onClick = {
                platform.copyToClipboard(message)
                platform.toast("پیام کپی شد")
            })
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ProtoOutlined("اشتراک پیام", modifier = Modifier.weight(1f), onClick = { platform.shareText(message) })
            ProtoOutlined("فایل .nrg", modifier = Modifier.weight(1f).testTag("proto_lock_file"), onClick = {
                platform.shareFiles(listOf(ImportLogic.safeFileName(r.title) to r.sealed))
            })
            ProtoOutlined(if (showQr) "بستن QR" else "QR", modifier = Modifier.weight(1f), onClick = onToggleQr)
        }
        if (showQr) ProtoQr(r.link)
        if (r.key != null) {
            ProtoOutlined("کپی کلید", onClick = {
                platform.copyToClipboard(r.key)
                platform.toast("کلید کپی شد")
            })
        }
    }
}

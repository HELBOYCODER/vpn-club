package com.helboy.vpnclub.ui.proto.importing

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.helboy.vpnclub.ui.proto.ProtoDialog
import com.helboy.vpnclub.ui.proto.ProtoEnv
import com.helboy.vpnclub.ui.theme.TextPrimary
import com.helboy.vpnclub.ui.theme.TextSecondary
import dev.valid7996.narcicgeteway.core.DeepLinkBus
import dev.valid7996.narcicgeteway.crypto.ZsxMetadata
import dev.valid7996.narcicgeteway.domain.config.DeepLinkPreview
import dev.valid7996.narcicgeteway.domain.config.DeepLinkRequest
import dev.valid7996.narcicgeteway.ui.platform.LocalPlatform
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * Listens to [DeepLinkBus] (links opened from outside the app) and asks before anything is imported. Always composed
 * by the shell; it draws nothing when no link is waiting. Locked configs and short links ask for their key.
 */
@Composable
fun DeepLinkImportHost(onImported: () -> Unit) {
    val platform = LocalPlatform.current
    val scope = rememberCoroutineScope()
    val pending by DeepLinkBus.pending.collectAsState()

    // These have to outlive the confirm dialog: the bus is cleared as soon as the person confirms.
    var unlock by remember { mutableStateOf<Pair<ByteArray, ZsxMetadata>?>(null) }
    var relayText by remember { mutableStateOf<String?>(null) }

    fun finish(outcome: ImportOutcome) {
        when (outcome) {
            is ImportOutcome.Done -> {
                platform.toast(ImportLogic.formatSummary(outcome.summary))
                if (outcome.summary.changedList) onImported()
            }
            is ImportOutcome.NeedKey -> unlock = outcome.bytes to outcome.meta
            is ImportOutcome.NeedRelayKey -> relayText = outcome.text
            is ImportOutcome.NeedOvpnCredentials ->
                platform.toast("این پروفایل OpenVPN نام کاربری و رمز می‌خواهد؛ آن را از صفحهٔ «ورود کانفیگ» اضافه کنید.")
            is ImportOutcome.OfferSubscription -> scope.launch {
                // A link that is only a web address: the person already confirmed, so add it as a subscription.
                finishSafely(platform::toast) { ImportEngine.addSubscription(ProtoEnv.repo, outcome.url, null) }
                    ?.let { finish(it) }
            }
            is ImportOutcome.Failed -> platform.toast(outcome.message)
        }
    }

    unlock?.let { (bytes, meta) ->
        UnlockDialog(
            bytes = bytes, meta = meta,
            onDismiss = { unlock = null },
            onUnlocked = {
                unlock = null
                onImported()
            },
        )
    }

    relayText?.let { text ->
        RelayKeyDialog(
            onDismiss = { relayText = null },
            onSubmit = { key ->
                relayText = null
                scope.launch {
                    finishSafely(platform::toast) { ImportEngine.importRelayWithKey(ProtoEnv.repo, text, key) }?.let { finish(it) }
                }
            },
        )
    }

    val current = pending ?: return
    val request = current.request
    val preview = remember(current) {
        runCatching { DeepLinkPreview.of(request) }.getOrDefault(DeepLinkPreview(name = null, detail = ""))
    }
    val kind = remember(current) {
        when (request) {
            is DeepLinkRequest.Subscription -> InputKind.SubscriptionUrl
            is DeepLinkRequest.ConfigText -> ImportLogic.classify(request.text)
        }
    }
    val title = when (kind) {
        InputKind.SubscriptionUrl -> "افزودن اشتراک"
        InputKind.Locked, InputKind.Relay -> "کانفیگ قفل‌شده"
        else -> "ورود کانفیگ"
    }
    val body = when (kind) {
        InputKind.SubscriptionUrl -> "یک لینک می‌خواهد این اشتراک را اضافه کند و سرورهایش را دریافت کند."
        InputKind.Locked, InputKind.Relay -> "یک لینک کانفیگ قفل‌شده باز شد. اگر کلید لازم باشد از شما پرسیده می‌شود."
        else -> "یک لینک می‌خواهد این کانفیگ را به لیست شما اضافه کند."
    }

    ProtoDialog(
        title = title,
        onDismiss = { DeepLinkBus.clear() },
        confirmLabel = if (kind == InputKind.SubscriptionUrl) "افزودن اشتراک" else "ورود",
        onConfirm = {
            DeepLinkBus.clear()
            scope.launch {
                val outcome = finishSafely(platform::toast) {
                    val repo = ProtoEnv.repo
                    when (request) {
                        is DeepLinkRequest.Subscription -> ImportEngine.addSubscription(repo, request.url, request.name)
                        is DeepLinkRequest.ConfigText -> ImportEngine.importText(repo, request.text)
                    }
                }
                if (outcome != null) finish(outcome)
            }
        },
    ) {
        Text(body, color = TextSecondary, fontSize = 13.sp, modifier = Modifier.testTag("proto_deeplink_body"))
        preview.name?.let { Text(it, color = TextPrimary, fontWeight = FontWeight.SemiBold, maxLines = 2) }
        if (preview.detail.isNotBlank()) {
            Text(preview.detail, color = TextPrimary, fontFamily = FontFamily.Monospace, fontSize = 12.sp, maxLines = 2)
        }
        if (request is DeepLinkRequest.Subscription) {
            Text(ImportLogic.shortUrl(request.url, 60), color = TextSecondary, fontFamily = FontFamily.Monospace, fontSize = 11.sp, maxLines = 3)
        }
    }
}

/** Runs [block]; a crash becomes a toast and null, never an exception into the UI. */
private suspend fun finishSafely(toast: (String) -> Unit, block: suspend () -> ImportOutcome): ImportOutcome? = try {
    block()
} catch (e: CancellationException) {
    throw e
} catch (e: Throwable) {
    toast(ImportLogic.describeError(e))
    null
}

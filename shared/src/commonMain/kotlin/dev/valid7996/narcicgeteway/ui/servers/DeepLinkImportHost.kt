package dev.valid7996.narcicgeteway.ui.servers

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.valid7996.narcicgeteway.core.DeepLinkBus
import dev.valid7996.narcicgeteway.crypto.ZsxMetadata
import dev.valid7996.narcicgeteway.crypto.ZsxPasswordRequiredException
import dev.valid7996.narcicgeteway.data.config.ConfigRepository
import dev.valid7996.narcicgeteway.domain.config.ConfigParseException
import dev.valid7996.narcicgeteway.domain.config.DeepLinkPreview
import dev.valid7996.narcicgeteway.domain.config.DeepLinkRequest
import dev.valid7996.narcicgeteway.shared.resources.Res
import dev.valid7996.narcicgeteway.shared.resources.*
import dev.valid7996.narcicgeteway.ui.platform.LocalPlatform
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.foundation.shape.RoundedCornerShape

@Composable
fun DeepLinkImportHost(repository: ConfigRepository, onImported: () -> Unit) {
    val platform = LocalPlatform.current
    val scope = rememberCoroutineScope()
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val pending by DeepLinkBus.pending.collectAsStateWithLifecycle()

    // The key prompt has to outlive the confirm dialog (DeepLinkBus is cleared as soon as the user taps "import").
    var unlock by remember { mutableStateOf<Pair<ByteArray, ZsxMetadata>?>(null) }
    unlock?.let { (bytes, meta) ->
        UnlockConfigDialog(
            zsxBytes = bytes,
            metadata = meta,
            repository = repository,
            onDismiss = { unlock = null },
            onSuccess = {
                unlock = null
                onImported()
            },
        )
    }

    var relayPrompt by remember { mutableStateOf<String?>(null) }
    relayPrompt?.let { pendingText ->
        RelayKeyDialog(
            onDismiss = { relayPrompt = null },
            onSubmit = { key ->
                relayPrompt = null
                scope.launch(Dispatchers.Default) {
                    repository.resolveNrgRelay(pendingText, key)
                        .mapCatching { resolved -> repository.importPasted(resolved).getOrThrow() }
                        .onSuccess { r ->
                            platform.toast(getString(Res.string.servers_imported, r.count))
                            if (r.count > 0) launch(Dispatchers.Main) { onImported() }
                        }
                        .onFailure { err ->
                            when (err) {
                                is dev.valid7996.narcicgeteway.data.config.RelayAlreadyClaimedException ->
                                    platform.toast("این لینک قبلاً روی یک دستگاه دیگر فعال شده است.")
                                is dev.valid7996.narcicgeteway.crypto.ZsxWrongPasswordException ->
                                    platform.toast("کلید اختصاصی وارد شده اشتباه است.")
                                else -> platform.toast(getString(Res.string.config_invalid))
                            }
                        }
                }
            },
        )
    }

    val current = pending ?: return
    val request = current.request
    val preview = remember(current) { DeepLinkPreview.of(request) }
    val isSubscription = request is DeepLinkRequest.Subscription

    AlertDialog(
        onDismissRequest = { DeepLinkBus.clear() },
        shape = RoundedCornerShape(14.dp),
        containerColor = if (isDark) Color(0xFF1C1C1E) else Color(0xFFFFFFFF),
        icon = { Icon(painterResource(Res.drawable.ic_add_link), null, tint = Color(0xFF007AFF)) },
        title = {
            Text(
                stringResource(
                    if (isSubscription) Res.string.deeplink_subscription_title else Res.string.deeplink_config_title,
                ),
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    stringResource(
                        if (isSubscription) Res.string.deeplink_subscription_body else Res.string.deeplink_config_body,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                preview.name?.let { name ->
                    Text(
                        name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    preview.detail,
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (request is DeepLinkRequest.Subscription) {
                    Text(
                        request.url,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                DeepLinkBus.clear()

                scope.launch(Dispatchers.Default) {
                    val outcome = when (request) {
                        is DeepLinkRequest.Subscription -> repository.importSubscriptionUrl(request.url, request.name)
                        is DeepLinkRequest.ConfigText -> repository.importPasted(request.text)
                    }
                    outcome
                        .onSuccess { r ->
                            platform.toast(
                                when {
                                    r.subscription -> getString(Res.string.subs_imported, r.subscriptionName, r.count)
                                    r.count > 0 -> getString(Res.string.servers_imported, r.count)
                                    r.duplicates > 0 -> getString(Res.string.servers_already_added, r.duplicates)
                                    else -> getString(Res.string.config_invalid)
                                },
                            )
                            if (r.count > 0) launch(Dispatchers.Main) { onImported() }
                        }
                        .onFailure { e ->
                            if (e is dev.valid7996.narcicgeteway.data.config.RelayKeyRequiredException &&
                                request is DeepLinkRequest.ConfigText
                            ) {
                                launch(Dispatchers.Main) { relayPrompt = request.text }
                                return@onFailure
                            }
                            if (e is dev.valid7996.narcicgeteway.data.config.RelayAlreadyClaimedException) {
                                platform.toast("این لینک قبلاً روی یک دستگاه دیگر فعال شده و دیگر قابل استفاده نیست.")
                                return@onFailure
                            }
                            if (e is ZsxPasswordRequiredException && request is DeepLinkRequest.ConfigText) {
                                val text = repository.resolveNrgRelay(request.text).getOrDefault(request.text)
                                val locked = repository.lockedFromText(text)
                                if (locked != null) {
                                    launch(Dispatchers.Main) { unlock = locked }
                                    return@onFailure
                                }
                            }
                            if (e.message?.contains("قفل شده است") == true) {
                                platform.toast(e.message ?: "این کانفیگ اختصاصی برای دستگاه دیگری قفل شده است.")
                                return@onFailure
                            }
                            if (e is dev.valid7996.narcicgeteway.crypto.ZsxExpiredException) {
                                platform.toast("این کانفیگ منقضی شده است و اعتبار آن پایان یافته است.")
                                return@onFailure
                            }
                            if (e is dev.valid7996.narcicgeteway.crypto.ZsxKeyConsumedException) {
                                platform.toast("این کلید اختصاصی قبلاً استفاده شده و باطل گردیده است.")
                                return@onFailure
                            }
                            if (e is ConfigRepository.SubscriptionNotFetchedException && e.savedForLater) {
                                platform.toast(getString(Res.string.subs_saved_unreachable, e.name))
                                return@onFailure
                            }
                            platform.toast(
                                getString(
                                    when {
                                        request is DeepLinkRequest.Subscription ||
                                            e is ConfigRepository.SubscriptionNotFetchedException -> Res.string.subs_failed
                                        else -> when ((e as? ConfigParseException)?.reason) {
                                            ConfigParseException.Reason.UnsupportedSsCipher -> Res.string.import_err_ss_cipher
                                            ConfigParseException.Reason.MissingSsCipher -> Res.string.import_err_ss_no_cipher
                                            ConfigParseException.Reason.UnsupportedSsPlugin -> Res.string.import_err_ss_plugin
                                            else -> Res.string.config_invalid
                                        }
                                    },
                                ),
                            )
                        }
                }
            }) {
                Text(
                    stringResource(
                        if (isSubscription) Res.string.deeplink_add_subscription else Res.string.deeplink_import,
                    ),
                    color = Color(0xFF007AFF),
                    fontWeight = FontWeight.SemiBold,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = { DeepLinkBus.clear() }) {
                Text(
                    stringResource(Res.string.action_cancel),
                    color = if (isDark) Color(0xFF8E8E93) else Color(0xFF6E6E73),
                )
            }
        },
    )
}

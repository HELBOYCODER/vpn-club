package dev.valid7996.narcicgeteway.core

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.VpnService
import dev.valid7996.narcicgeteway.core.AppLog as Log
import androidx.core.content.ContextCompat
import dev.valid7996.narcicgeteway.NrgSecureApp
import dev.valid7996.narcicgeteway.domain.model.RunMode

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val container = (context.applicationContext as NrgSecureApp).container
        val settings = container.settingsRepository.settings.value
        SubscriptionUpdateScheduler.sync(context, settings, container.configRepository.subscriptions.value)

        if (!settings.autoConnectOnBoot) return
        val active = container.configRepository.activeProfile() ?: return

        if (active.isIkev2) return

        if (settings.runMode != RunMode.ProxyOnly && VpnService.prepare(context) != null) {
            Log.i(TAG, "auto-connect skipped: VPN permission not granted")
            return
        }
        runCatching {
            ContextCompat.startForegroundService(
                context,
                Intent(context, NrgVpnService::class.java)
                    .putExtra(VpnManager.EXTRA_COMMAND, VpnManager.CMD_START_ACTIVE),
            )
        }.onFailure { Log.w(TAG, "auto-connect at boot failed to start the service", it) }
    }

    private companion object {
        const val TAG = "BootReceiver"
    }
}

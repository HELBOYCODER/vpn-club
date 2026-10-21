package dev.valid7996.narcicgeteway

import android.app.Application
import android.content.Context
import android.content.res.Configuration
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.svg.SvgDecoder
import dev.valid7996.narcicgeteway.core.AetherDelayProbe
import dev.valid7996.narcicgeteway.core.RstaSpoofClient
import dev.valid7996.narcicgeteway.data.settings.SettingsRepository
import dev.valid7996.narcicgeteway.core.XrayController
import dev.valid7996.narcicgeteway.data.net.PingService
import dev.valid7996.narcicgeteway.core.platform.LocaleManager
import dev.valid7996.narcicgeteway.di.AppContainer

class NrgSecureApp : Application(), SingletonImageLoader.Factory {
    lateinit var container: AppContainer
        private set

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(LocaleManager.wrap(base, SettingsRepository.readLanguageTag(base)))
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        LocaleManager.reassert(SettingsRepository.readLanguageTag(this))
    }

    override fun onCreate() {
        super.onCreate()

        container = AppContainer(this)

        // One app, two engines: init the Hellboy engine and tie both tunnels together before any UI exists,
        // so the tile, widget and boot receiver also stop the other engine's tunnel correctly.
        com.helboy.vpnclub.corex.VpnController.init(this)
        com.helboy.vpnclub.bridge.EngineBridge.install(this)

        XrayController.warmUp(this)
        PingService.rstaSpoofLease = { RstaSpoofClient.lease(this, SettingsRepository(this).settings.value) }
        PingService.aetherTunnel = { profile, measure -> AetherDelayProbe.run(this, profile, measure) }
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components { add(SvgDecoder.Factory()) }
            .build()
}

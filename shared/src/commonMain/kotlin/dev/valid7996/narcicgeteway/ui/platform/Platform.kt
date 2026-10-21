package dev.valid7996.narcicgeteway.ui.platform

import androidx.compose.runtime.staticCompositionLocalOf
import dev.valid7996.narcicgeteway.data.assets.GeoAssets
import dev.valid7996.narcicgeteway.data.update.Distribution
import dev.valid7996.narcicgeteway.data.update.GitHubReleases

data class LiveStats(

    val cpuPercent: Float? = null,

    val coreCount: Int = 1,

    val memoryBytes: Long? = null,

    val threads: Int? = null,

    val tempC: Float? = null,

    val thermalHeadroom: Float? = null,

    val batteryPercent: Int? = null,
    val charging: Boolean = false,

    val currentMilliAmps: Int? = null,

    val thermalStatus: Int? = null,

    val processUptimeMs: Long? = null,
)

interface Platform {
    fun copyToClipboard(text: String)
    fun readClipboard(): String?
    fun shareText(text: String)
    fun openUri(url: String)

    fun toast(message: String)

    /** True where the MHRV relay's local CA can be handed to the system installer (Android). */
    val supportsMhrvCa: Boolean get() = false

    /** Starts the system install dialog for the MHRV relay CA; returns a one-line status for a toast. */
    fun installMhrvCa(): String = ""

    /** Tests TLS handshake latency to [googleIp]:443 using [sni] via native mhrv-rs core. Returns JSON string. */
    suspend fun testSni(googleIp: String, sni: String): String = ""

    fun shareFiles(files: List<Pair<String, ByteArray>>) {}

    fun liveStats(): LiveStats = LiveStats()

    /** The state of the no-root SNI spoof engine, one line ("inactive", "active -> ip:port (sni=..., method=...)", "failed: ..."). */
    fun rstaSpoofStatus(): String = ""

    /** The recent lines of the engine's own log. */
    fun rstaSpoofLog(): List<String> = emptyList()

    val isComputer: Boolean get() = false

    fun cryptoAcceleration(): String = ""

    fun installedApps(): List<InstalledApp> = emptyList()

    fun appIcon(packageName: String): ByteArray? = null

    fun scanQrCode(onResult: (String?) -> Unit) { onResult(null) }

    fun scanQrFromImage(onResult: (String?) -> Unit) { onResult(null) }

    val supportsQrScan: Boolean get() = false

    val supportsSystemProxy: Boolean get() = false

    val supportsTun: Boolean get() = true

    val choosesTunEngine: Boolean get() = true

    val automaticRendering: AutomaticRendering? get() = null

    fun lanIpv4Addresses(): List<String> = emptyList()

    val supportsPerAppProxy: Boolean get() = false

    val supportsDynamicColor: Boolean get() = false

    val geoAssets: GeoAssets? get() = null

    suspend fun pickFileBytes(): FilePick? = null

    val isIgnoringBatteryOptimizations: Boolean get() = true

    val supportsBatteryOptimization: Boolean get() = false

    fun openBatteryOptimizationSettings() {}

    val distribution: Distribution get() = Distribution.GitHub

    val deviceAbis: List<String> get() = emptyList()

    fun openStorePage() {
        openUri(
            if (distribution == Distribution.PlayStore) dev.valid7996.narcicgeteway.data.update.PlayStore.WEB_URL
            else GitHubReleases.PROJECT_URL,
        )
    }
}

enum class AutomaticRendering { Gpu, SoftwareWindows, SoftwareNvidiaWayland }

data class FilePick(val name: String, val bytes: ByteArray)

data class InstalledApp(
    val packageName: String,
    val label: String,
    val isSystem: Boolean,
    val iconPng: ByteArray? = null,
)

val LocalPlatform = staticCompositionLocalOf<Platform> {
    error("LocalPlatform not provided — wrap the UI in a Platform provider")
}

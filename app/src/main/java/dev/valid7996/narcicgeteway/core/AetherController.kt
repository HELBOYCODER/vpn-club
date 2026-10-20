package dev.valid7996.narcicgeteway.core

import android.content.Context
import android.os.Build
import kotlin.concurrent.Volatile
import dev.valid7996.narcicgeteway.core.AppLog as Log
import dev.valid7996.narcicgeteway.domain.config.AetherCommands
import dev.valid7996.narcicgeteway.domain.config.AetherExitJson
import dev.valid7996.narcicgeteway.domain.config.AetherProfile
import java.io.File
import java.io.IOException
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Owns the Aether core process of a session, ported from PattNG: the core is the libaether.so
 * shipped in the APK's jniLibs, run as a process with a SOCKS listener on the loopback. A minimal
 * Xray exit proxy runs beside it, and the core dials out through it, so the profile's finalMask and
 * dialMode apply and a carrier of its own keeps working; without Xray the core dials out directly.
 * Missing WARP keys are registered first, the tunnel starts once the listener answers a SOCKS
 * greeting, and the output is relayed into the app log. Leftover processes of a killed app are reaped.
 */
class AetherController(
    private val context: Context,
    private val profile: AetherProfile,
    private val socksPort: Int,
    private val onEstablished: () -> Unit,
    private val onStopped: (String?) -> Unit,
) {
    @Volatile private var process: Process? = null
    @Volatile private var stopped = false
    @Volatile private var worker: Thread? = null

    /** True once the core has said that Psiphon carries traffic; only a Psiphon session waits for it. */
    @Volatile private var readyWordSeen = false

    val isRunning: Boolean get() = worker?.isAlive == true && !stopped

    fun start(): Boolean {
        val binary = AetherSupport.binary(context)
        if (!binary.canExecute()) {
            LogBus.append("E/aether the Aether core (libaether.so) is missing for this device's ABI")
            onStopped("Aether core binary missing")
            return false
        }
        stopped = false
        val t = Thread({
            if (profile.protocol == AetherProfile.PROTO_WG_OVER_MASQUE && !AetherSupport.capabilities(context).wgOverMasque) {
                val caps = AetherSupport.capabilities(context)
                LogBus.append("E/aether WireGuard over MASQUE needs aether 2.3 or newer; this build ships ${caps.version.ifEmpty { "an older core" }}. Replace libaether.so with the one of a current PattNG build.")
                onStopped("The Aether core is too old for WireGuard over MASQUE")
            } else {
                runSession(binary)
            }
        }, "aether-worker").apply { isDaemon = true }
        worker = t
        t.start()
        return true
    }

    fun stop() {
        stopped = true
        runCatching { process?.destroy() }
        runCatching { worker?.interrupt() }
    }

    private fun runSession(binary: File) {
        AetherSupport.sessionRunning = true
        try {
            AetherSupport.reapStale(context, "127.0.0.1:$socksPort")
            val workDir = AetherSupport.workDir(context).apply { mkdirs() }
            AetherIdentityStore.settle(workDir)

            val missing = AetherIdentityStore.missingKeys(workDir, profile)
            if (missing.isNotEmpty()) {
                val upstream = AetherSupport.startExitProxy(profile)
                try {
                    LogBus.append("I/aether registering missing WARP keys: ${missing.joinToString()}")
                    val registered = AetherIdentityStore.registerBlocking(
                        context = context,
                        kind = AetherProfile.registerKindOf(profile.protocol),
                        upstreamPort = upstream,
                        workDir = workDir,
                        onOutput = ::relayOutput,
                    )
                    if (!registered) {
                        onStopped("WARP key registration failed")
                        return
                    }
                } finally {
                    AetherSupport.stopExitProxy()
                }
            }

            var arguments = AetherCommands.runArguments(profile, socksPort)
            val upstream = if (AetherCommands.UPSTREAM !in arguments) {
                AetherSupport.startExitProxy(profile)
            } else {
                null
            }
            if (upstream != null) {
                arguments = arguments + listOf(AetherCommands.UPSTREAM, "socks5://127.0.0.1:$upstream")
            }
            // Psiphon's listener answers the SOCKS greeting before it has a tunnel; its readiness is the core's
            // word, an info line, which a quieter level of a hand-written command would never print.
            val needsWord = AetherCommands.readyNeedsWord(arguments)
            if (needsWord) arguments = AetherCommands.withInfoLines(arguments)
            // The listener the app dials: the one the arguments name (a command of the profile may name another).
            val listenPort = AetherCommands.listenerPortOf(arguments) ?: socksPort
            readyWordSeen = false

            val p = try {
                AetherSupport.startProcess(context, arguments, workDir, session = true)
            } catch (e: Exception) {
                Log.e("AetherController", "failed to spawn core", e)
                AetherSupport.stopExitProxy()
                onStopped(e.message)
                return
            }
            process = p

            val reader = Thread({
                try {
                    p.inputStream.bufferedReader().forEachLine { line ->
                        relayOutput(line)
                    }
                } catch (_: IOException) {
                }
            }, "aether-log").apply { isDaemon = true }
            reader.start()

            // PattNG waits for the listener as long as the core lives: a scan of a hard network,
            // and a Psiphon or Tor carrier, takes minutes. The stop flag and the process death end it.
            var ready = false
            while (!stopped) {
                if ((!needsWord || readyWordSeen) && AetherSupport.answersSocks(listenPort)) {
                    ready = true
                    break
                }
                if (!p.isAlive) break
                Thread.sleep(POLL_INTERVAL_MS)
            }

            if (!ready || stopped) {
                p.destroy()
                AetherSupport.stopExitProxy()
                onStopped(if (stopped) null else "Aether core listener did not come up")
                return
            }

            onEstablished()

            p.waitFor()
            AetherSupport.stopExitProxy()
            if (!stopped) onStopped("Aether core exited unexpectedly with code ${p.exitValue()}")
        } catch (t: Throwable) {
            Log.e("AetherController", "worker threw", t)
            AetherSupport.stopExitProxy()
            if (!stopped) onStopped(t.message)
        } finally {
            AetherSupport.sessionRunning = false
        }
    }

    private fun relayOutput(line: String) {
        val clean = line.trim()
        if (clean.isNotEmpty()) {
            if (AetherCommands.isReadyWord(clean)) readyWordSeen = true
            PsiphonServerList.regionsOf(clean)?.let(PsiphonServerList::remember)
            LogBus.append("${AetherSupport.levelOf(clean)}/aether $clean")
        }
    }

    companion object {
        private const val POLL_INTERVAL_MS = 300L
    }
}

/** Shared process plumbing of the Aether core, used by the session, the scanner and the identity store. */
object AetherSupport {

    const val BINARY_NAME = "libaether.so"
    const val PSIPHON_BINARY_NAME = "libpsiphon-tunnel-core.so"
    const val TRANSPORT_BINARY_NAME = "liblyrebird.so"

    private const val OWNER_ENV = "NARCIC_AETHER_OWNER"
    private const val CERT_DIR_ENV = "SSL_CERT_DIR"
    const val PSIPHON_BIN_ENV = "AETHER_PSIPHON_BIN"
    const val TOR_PT_ENV = "AETHER_TOR_PT"
    const val PSIPHON_DIR_ENV = "AETHER_PSIPHON_DIR"
    const val PSIPHON_CONFIG_ENV = "AETHER_PSIPHON_CONFIG"

    private val torTransports = listOf("obfs4", "snowflake", "webtunnel", "meek_lite", "obfs3", "scramblesuit")

    private const val PSIPHON_OVERLAY = """{"DNSResolverAlternateServers": ["1.1.1.1", "1.0.0.1", "8.8.8.8", "8.8.4.4"]}"""

    fun binary(context: Context): File = File(context.applicationInfo.nativeLibraryDir, BINARY_NAME)

    fun psiphonBinary(context: Context): File = File(context.applicationInfo.nativeLibraryDir, PSIPHON_BINARY_NAME)

    fun transportBinary(context: Context): File = File(context.applicationInfo.nativeLibraryDir, TRANSPORT_BINARY_NAME)

    fun isCoreSupported(context: Context): Boolean = binary(context).canExecute()

    fun isPsiphonSupported(context: Context): Boolean = psiphonBinary(context).canExecute()

    fun isTorTransportsSupported(context: Context): Boolean = transportBinary(context).canExecute()

    fun workDir(context: Context): File = File(context.filesDir, "aether")

    /** Where the Psiphon client of the session keeps its datastore: the servers it was given, fetched and discovered. */
    fun psiphonDir(context: Context): File = File(context.filesDir, "aether-psiphon")

    /** The datastore of a scan or a key registration: apart from the session's, which holds a lock on its own. */
    fun psiphonProbeDir(context: Context): File = File(context.filesDir, "aether-psiphon-probe")

    fun environment(context: Context, workDir: File, session: Boolean = false): MutableMap<String, String> = mutableMapOf(
        "HOME" to workDir.absolutePath,
        "TMPDIR" to context.cacheDir.absolutePath,
        "AETHER_CONFIG" to File(workDir, AetherIdentityStore.BASE_FILE).absolutePath,
        "AETHER_MASQUE_CONFIG" to File(workDir, AetherIdentityStore.MASQUE_FILE).absolutePath,
        "AETHER_WG_CONFIG" to File(workDir, AetherIdentityStore.WIREGUARD_FILE).absolutePath,
        OWNER_ENV to android.os.Process.myPid().toString(),
        PSIPHON_DIR_ENV to (if (session) psiphonDir(context) else psiphonProbeDir(context)).apply { mkdirs() }.absolutePath,
    ).apply {
        certificateDirectories()?.let { put(CERT_DIR_ENV, it) }
        psiphonBinary(context).takeIf { it.canExecute() }?.let { put(PSIPHON_BIN_ENV, it.absolutePath) }
        transportBinary(context).takeIf { it.canExecute() }?.let { transport ->
            put(TOR_PT_ENV, torTransports.joinToString(";") { "$it=${transport.absolutePath}" })
        }
        psiphonOverlay(workDir)?.let { put(PSIPHON_CONFIG_ENV, it.absolutePath) }
    }

    /**
     * Starts the core on [arguments]. The word `shipped` the profile writes after `--psiphon-server-entries` is
     * replaced by the unpacked list of Psiphon servers, or the flag dropped when the app holds none: the core would
     * otherwise be told to read a file called "shipped" and the Psiphon client would start without servers.
     */
    /** What the bundled core (libaether.so) understands. Cores differ by version: the flags changed names and came and went. */
    data class CoreCaps(
        val version: String,
        val masqueSni: Boolean,
        val apiAddress: Boolean,
        val enrollAddress: Boolean,
        val goolClassic: Boolean,
        val psiphonConfig: Boolean = true,
    ) {
        /** gool is WireGuard over MASQUE only from aether 2.3, the core that also names the classic one --gool-classic. */
        val wgOverMasque: Boolean get() = goolClassic
    }

    @Volatile private var cachedCaps: CoreCaps? = null

    /**
     * Asks the core itself: its own --help lists every flag it takes. Run once and kept; blocking, so never on the
     * main thread. A core that cannot be asked is taken for an old one, which is the safe side (flags it might not know are dropped).
     */
    fun capabilities(context: Context): CoreCaps {
        cachedCaps?.let { return it }
        val help = try {
            val p = ProcessBuilder(listOf(binary(context).absolutePath, "--help")).redirectErrorStream(true).start()
            val out = StringBuilder()
            val reader = Thread({
                try { p.inputStream.bufferedReader().forEachLine { out.appendLine(it) } } catch (_: IOException) {}
            }, "aether-help").apply { isDaemon = true }
            reader.start()
            reader.join(5_000)
            p.destroy()
            out.toString()
        } catch (_: Exception) {
            ""
        }
        val caps = CoreCaps(
            version = Regex("""Aether v?(\d+\.\d+\.\d+)""").find(help)?.groupValues?.get(1).orEmpty(),
            masqueSni = "--masque-sni" in help,
            apiAddress = "--api-address" in help,
            enrollAddress = "--enroll-address" in help,
            goolClassic = "--gool-classic" in help,
            psiphonConfig = "--psiphon-config" in help,
        )
        if (help.isNotBlank()) cachedCaps = caps
        return caps
    }

    /** [arguments] as the bundled core takes them: a flag it does not know makes it print its help and exit, so it is dropped or renamed. */
    fun adapt(context: Context, arguments: List<String>): List<String> {
        if (arguments.none { it in ADAPTED_FLAGS || it == "gool-classic" }) return arguments
        val caps = capabilities(context)
        var out = arguments
        if (!caps.masqueSni) out = AetherCommands.withoutOption(out, AetherCommands.MASQUE_SNI)
        if (!caps.goolClassic) out = out.filter { it != AetherCommands.GOOL_CLASSIC }
        if (!caps.psiphonConfig) out = AetherCommands.withOverlay(out, null)
        if (AetherCommands.API_ADDRESS in out && !caps.apiAddress) {
            out = if (caps.enrollAddress) {
                out.map { if (it == AetherCommands.API_ADDRESS) AetherCommands.ENROLL_ADDRESS else it }
            } else {
                AetherCommands.withoutOption(out, AetherCommands.API_ADDRESS)
            }
        }
        // The old core registers WARP-in-WARP as plain gool.
        val at = out.indexOf(AetherCommands.REGISTER)
        if (at >= 0 && out.getOrNull(at + 1) == "gool-classic" && !caps.goolClassic) {
            out = out.toMutableList().also { it[at + 1] = "gool" }
        }
        return out
    }

    private val ADAPTED_FLAGS = setOf(
        AetherCommands.MASQUE_SNI, AetherCommands.GOOL_CLASSIC, AetherCommands.API_ADDRESS, AetherCommands.REGISTER,
        AetherCommands.PSIPHON_CONFIG,
    )

    fun startProcess(context: Context, arguments: List<String>, workDir: File, session: Boolean = false): Process {
        val entries = if (AetherCommands.PSIPHON_SERVER_ENTRIES in arguments) {
            PsiphonServerList.entriesFile(context, workDir) { problem ->
                LogBus.append("W/aether the Psiphon server list is not usable (${problem.message}); the entries kept from before stay")
            }
        } else {
            null
        }
        if (AetherCommands.PSIPHON_SERVER_ENTRIES in arguments && entries == null) {
            LogBus.append("W/aether no Psiphon server list is bundled; Psiphon fetches its own")
        }
        // The overlay: the dial overrides of the CDN edges and beast mode, laid over the core's Psiphon configuration.
        val overlay = AetherCommands.overlayOf(arguments)?.let { json ->
            File(workDir, "psiphon-fronting.json").also { file ->
                workDir.mkdirs()
                file.writeText(json)
            }
        }
        val finalArguments = adapt(
            context,
            AetherCommands.withOverlay(AetherCommands.withShippedList(arguments, entries?.absolutePath), overlay?.absolutePath),
        )
        val builder = ProcessBuilder(listOf(binary(context).absolutePath) + finalArguments)
            .directory(workDir)
            .redirectErrorStream(true)
        builder.environment().putAll(environment(context, workDir, session))
        // The overlay of the flag holds the resolvers the environment's file does: one of them only.
        if (overlay != null) builder.environment().remove(PSIPHON_CONFIG_ENV)
        return builder.start()
    }

    fun levelOf(line: String): Char = when {
        line.startsWith("Error:") || line.startsWith("E/") -> 'E'
        line.startsWith("Warn:") || line.startsWith("W/") -> 'W'
        line.startsWith("[") && line.contains("] ") ->
            when (line.substringAfter('[').substringBefore(']').split(' ').getOrNull(1)) {
                "ERROR" -> 'E'
                "WARN" -> 'W'
                "DEBUG", "TRACE" -> 'D'
                else -> 'I'
            }

        else -> 'I'
    }

    fun answersSocks(port: Int): Boolean = try {
        Socket().use { socket ->
            socket.connect(InetSocketAddress("127.0.0.1", port), PROBE_TIMEOUT_MS)
            socket.soTimeout = PROBE_TIMEOUT_MS
            socket.getOutputStream().write(byteArrayOf(5, 1, 0))
            socket.getInputStream().read() == 5
        }
    } catch (_: Exception) {
        false
    }

    fun findFreePort(): Int = ServerSocket(0).use { socket ->
        socket.localPort.takeIf { it in 1024..65535 } ?: 10892
    }

    fun scanPort(profile: AetherProfile): Int =
        if (profile.tor == AetherProfile.CARRIER_REVERSE) findFreePort() else 0

    @Volatile private var exitRunning = false

    /** True while a session of Aether runs; a ping of another profile cannot start a second core then. */
    @Volatile var sessionRunning = false

    fun startExitProxy(profile: AetherProfile): Int? {
        if (exitRunning) return AetherExitJson.EXIT_SOCKS_PORT
        val config = AetherExitJson.exitConfig(profile.finalMask, profile.dialMode, profile.targetStrategy)
        val ok = try {
            XrayController.start(config)
        } catch (t: Throwable) {
            Log.w("AetherSupport", "aether: the exit proxy failed to start", t)
            false
        }
        if (!ok || !runBlockingWait { answersSocks(AetherExitJson.EXIT_SOCKS_PORT) }) {
            LogBus.append("W/aether the exit proxy did not come up; the core dials out directly")
            runCatching { XrayController.stop() }
            return null
        }
        exitRunning = true
        LogBus.append("I/aether the exit proxy is up on 127.0.0.1:${AetherExitJson.EXIT_SOCKS_PORT}")
        return AetherExitJson.EXIT_SOCKS_PORT
    }

    /** True while the exit is part of the routing front's Xray, which the service starts and stops, not this object. */
    @Volatile private var exitAdopted = false

    /** The front's Xray carries the exit: [startExitProxy] then finds it running, and [stopExitProxy] leaves it be. */
    fun adoptExit() {
        exitAdopted = true
        exitRunning = true
    }

    /** The service stopped the front's Xray: the exit is gone with it. */
    fun releaseExit() {
        exitAdopted = false
        exitRunning = false
    }

    fun stopExitProxy() {
        if (exitAdopted) return
        if (exitRunning) {
            exitRunning = false
            runCatching { XrayController.stop() }
        }
    }

    private fun runBlockingWait(probe: () -> Boolean): Boolean {
        var ok = false
        val deadline = System.nanoTime() + EXIT_START_TIMEOUT_MS * 1_000_000
        while (System.nanoTime() < deadline) {
            if (probe()) {
                ok = true
                break
            }
            try {
                Thread.sleep(READY_POLL_MS)
            } catch (_: InterruptedException) {
                break
            }
        }
        return ok
    }

    fun reapStale(context: Context, bindAddress: String?) {
        val binaryPath = binary(context).absolutePath
        val procDir = File("/proc")
        val entries = procDir.listFiles() ?: return
        for (entry in entries) {
            val pid = entry.name.toIntOrNull() ?: continue
            val argv = readNulSeparated(File(entry, "cmdline")) ?: continue
            if (argv.firstOrNull() != binaryPath) continue
            val environ = readNulSeparated(File(entry, "environ"))
            val owner = environ?.firstOrNull { it.startsWith("$OWNER_ENV=") }?.substringAfter('=')?.toIntOrNull()
            val ownerAlive = owner?.let { File(procDir, it.toString()).isDirectory }
            val holdsBind = bindAddress != null && argv.contains(bindAddress)
            if (ownerAlive == false || holdsBind) {
                Log.w("AetherSupport", "aether: killing leftover core process, pid=$pid ownerAlive=$ownerAlive holdsBind=$holdsBind")
                runCatching { android.os.Process.killProcess(pid) }
            }
        }
    }

    private fun certificateDirectories(): String? = listOf(
        "/apex/com.android.conscrypt/cacerts",
        "/system/etc/security/cacerts",
    ).filter { File(it).isDirectory }.takeIf { it.isNotEmpty() }?.joinToString(":")

    private fun psiphonOverlay(workDir: File): File? = try {
        File(workDir, "psiphon-overlay.json").apply { if (!isFile || readText() != PSIPHON_OVERLAY) writeText(PSIPHON_OVERLAY) }
    } catch (_: IOException) {
        null
    }

    private fun readNulSeparated(file: File): List<String>? = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            java.nio.file.Files.readAllBytes(file.toPath()).toString(Charsets.UTF_8).split('\u0000').filter { it.isNotEmpty() }
        } else {
            file.readBytes().toString(Charsets.UTF_8).split('\u0000').filter { it.isNotEmpty() }
        }
    } catch (_: Exception) {
        null
    }

    private const val READY_POLL_MS = 500L
    private const val PROBE_TIMEOUT_MS = 1_000
    private const val EXIT_START_TIMEOUT_MS = 10_000L
}

/** Handles reading and registering WARP identity keys via the Rust core. */
object AetherIdentityStore {

    const val BASE_FILE = "aether.toml"
    const val MASQUE_FILE = "aether-masque.toml"
    const val MASQUE_INNER_FILE = "aether-masque-secondary.toml"
    const val WIREGUARD_FILE = "aether-wg.toml"
    const val WIREGUARD_INNER_FILE = "aether-wg-secondary.toml"
    const val MASQUE_GOOL_FILE = "aether-masque-gool.toml"

    private val identityField = Regex("""^(device_id|ipv4|ipv6)\s*=\s*"([^"]*)"$""")

    data class AetherIdentity(val deviceId: String, val ipv4: String, val ipv6: String)
    data class KeyEntry(val file: String, val identity: AetherIdentity?)

    fun filesOf(protocol: String): List<String> = when (protocol) {
        AetherProfile.PROTO_MASQUE -> listOf(MASQUE_FILE)
        AetherProfile.PROTO_WG -> listOf(WIREGUARD_FILE)
        AetherProfile.PROTO_GOOL -> listOf(WIREGUARD_FILE, WIREGUARD_INNER_FILE)
        AetherProfile.PROTO_MIM -> listOf(MASQUE_FILE, MASQUE_INNER_FILE)
        AetherProfile.PROTO_WG_OVER_MASQUE -> listOf(MASQUE_FILE, MASQUE_GOOL_FILE)
        else -> listOf(WIREGUARD_FILE)
    }

    fun read(file: File): AetherIdentity? = try {
        if (file.isFile) parse(file.readText()) else null
    } catch (_: Exception) {
        null
    }

    private fun parse(text: String): AetherIdentity? {
        val fields = mutableMapOf<String, String>()
        for (raw in text.lineSequence()) {
            identityField.matchEntire(raw.trim())?.destructured?.let { (key, value) ->
                fields.putIfAbsent(key, value)
            }
        }
        val deviceId = fields["device_id"]?.takeIf { it.isNotBlank() } ?: return null
        return AetherIdentity(deviceId, fields["ipv4"].orEmpty(), fields["ipv6"].orEmpty())
    }

    fun keys(context: Context): List<KeyEntry> {
        val workDir = AetherSupport.workDir(context)
        return listOf(WIREGUARD_FILE, WIREGUARD_INNER_FILE, MASQUE_FILE, MASQUE_INNER_FILE, MASQUE_GOOL_FILE).map { name ->
            KeyEntry(name, read(File(workDir, name)))
        }
    }

    fun missingKeys(workDir: File, profile: AetherProfile): List<String> =
        filesOf(profile.protocol).filter { read(File(workDir, it)) == null }

    suspend fun register(
        context: Context,
        request: AetherCommands.KeysRequest,
        upstreamPort: Int?,
        workDir: File,
        onOutput: (String) -> Unit,
    ): Boolean = withContext(Dispatchers.IO) {
        registerBlocking(context, request, upstreamPort, workDir, onOutput)
    }

    fun registerBlocking(
        context: Context,
        kind: String,
        upstreamPort: Int?,
        workDir: File,
        onOutput: (String) -> Unit,
    ): Boolean = registerBlocking(
        context = context,
        request = AetherCommands.KeysRequest(kind = kind),
        upstreamPort = upstreamPort,
        workDir = workDir,
        onOutput = onOutput,
    )

    /** Registers the keys the page's [request] asks for, through the settings it carries. */
    fun registerBlocking(
        context: Context,
        request: AetherCommands.KeysRequest,
        upstreamPort: Int?,
        workDir: File,
        onOutput: (String) -> Unit,
    ): Boolean {
        var arguments = AetherCommands.keysArguments(request)
        if (upstreamPort != null) {
            arguments = arguments + listOf(AetherCommands.UPSTREAM, "socks5://127.0.0.1:$upstreamPort")
        }
        return runUntil(
            context = context,
            arguments = arguments,
            workDir = workDir,
            timeoutMs = REGISTER_TIMEOUT_MS,
            source = "aether-key",
            onOutput = onOutput,
            match = { line -> if (registeredWord.containsMatchIn(line)) line else null },
        ) != null
    }

    internal fun <T> runUntil(
        context: Context,
        arguments: List<String>,
        workDir: File,
        timeoutMs: Long,
        source: String,
        onOutput: (String) -> Unit,
        match: (String) -> T?,
    ): T? {
        AetherSupport.reapStale(context, null)
        val process = try {
            AetherSupport.startProcess(context, arguments, workDir)
        } catch (e: IOException) {
            Log.w("AetherIdentity", "$source: failed to launch", e)
            return null
        }
        var found: T? = null
        val output = Thread({
            try {
                process.inputStream.bufferedReader().forEachLine { line ->
                    val text = line.trim()
                    if (text.isNotEmpty()) {
                        onOutput(text)
                        val hit = match(text)
                        if (hit != null && found == null) found = hit
                    }
                }
            } catch (_: IOException) {
            }
        }, "$source-output").apply { isDaemon = true }
        output.start()
        val deadline = System.nanoTime() + timeoutMs * 1_000_000
        while (found == null && System.nanoTime() < deadline) {
            if (runCatching { process.isAlive }.getOrNull() != true) {
                output.join(1_000)
                break
            }
            try {
                Thread.sleep(200)
            } catch (_: InterruptedException) {
                break
            }
        }
        process.destroy()
        runCatching { process.waitFor() }
        AetherSupport.reapStale(context, null)
        return found
    }

    fun settle(workDir: File) {
        val renewalDir = File(workDir.parentFile, "aether-renewal")
        if (!File(renewalDir, "ready").isFile) return
        workDir.mkdirs()
        for (name in listOf(WIREGUARD_FILE, WIREGUARD_INNER_FILE, MASQUE_FILE, MASQUE_INNER_FILE, MASQUE_GOOL_FILE)) {
            val renewed = File(renewalDir, name)
            if (renewed.isFile) runCatching { renewed.renameTo(File(workDir, name)) }
        }
        renewalDir.deleteRecursively()
    }

    private val registeredWord = Regex("""identities ready: \S""")
    private const val REGISTER_TIMEOUT_MS = 4 * 60_000L
}

/** Runs the Aether scanner using libaether.so with full PattNG regex matching. */
object AetherScanner {

    data class ScanResult(val endpoint: String, val innerHop: String? = null)

    private const val SCAN_TIMEOUT_MS = 8 * 60_000L

    private val masqueGateway = Regex("""selected MASQUE gateway (\S+)""")
    private val wireguardEndpoint = Regex("""selected WireGuard endpoint (\S+)""")
    private val goolHops = Regex("""using cloudflare edge (\S+) \(outer\) and (\S+) \(inner\)""")
    private val mimHops = Regex("""masque-in-masque ready: (\S+) \(outer\) and (\S+) \(inner\)""")
    private val goolOverMasqueReady = Regex("""gool ready: masque (\S+) carries wireguard (\S+)""")
    private val exitAccepted = Regex("""exit location \S+ accepted""")
    private val exitRejected = Regex("""exit location \S+ rejected""")

    @Volatile private var activeProcess: Process? = null

    fun cancel() {
        runCatching { activeProcess?.destroy() }
    }

    suspend fun scan(
        context: Context,
        profile: AetherProfile,
        onOutput: (String) -> Unit = {},
    ): ScanResult? = withContext(Dispatchers.IO) {
        val workDir = AetherSupport.workDir(context).apply { mkdirs() }
        AetherIdentityStore.settle(workDir)
        val upstream = AetherSupport.startExitProxy(profile)
        try {
            var arguments = AetherCommands.buildArguments(profile, AetherSupport.scanPort(profile), scan = true)
            if (upstream != null) {
                arguments = arguments + listOf(AetherCommands.UPSTREAM, "socks5://127.0.0.1:$upstream")
            }
            val matcher = matcher(profile, exitRuled = profile.exitLoc.isNotBlank())
            AetherSupport.reapStale(context, null)
            val p = try {
                AetherSupport.startProcess(context, arguments, workDir)
            } catch (e: Exception) {
                onOutput("E/aether-scan failed to launch scanner: ${e.message}")
                return@withContext null
            }
            activeProcess = p
            var found: ScanResult? = null
            val reader = Thread({
                try {
                    p.inputStream.bufferedReader().forEachLine { line ->
                        val text = line.trim()
                        if (text.isNotEmpty()) {
                            onOutput(text)
                            val hit = matcher(text)
                            if (hit != null && found == null) found = hit
                        }
                    }
                } catch (_: IOException) {
                }
            }, "scan-reader").apply { isDaemon = true }
            reader.start()

            val deadline = System.currentTimeMillis() + SCAN_TIMEOUT_MS
            while (found == null && System.currentTimeMillis() < deadline && p.isAlive) {
                Thread.sleep(250)
            }
            p.destroy()
            runCatching { p.waitFor() }
            activeProcess = null
            AetherSupport.reapStale(context, null)
            found
        } finally {
            AetherSupport.stopExitProxy()
        }
    }

    internal fun matcher(profile: AetherProfile, exitRuled: Boolean): (String) -> ScanResult? {
        val plain = { line: String -> parse(profile, line) }
        // MASQUE-in-MASQUE and WireGuard over MASQUE say they are ready after the exit check, so their line stands alone.
        if (!exitRuled || profile.protocol == AetherProfile.PROTO_MIM || profile.protocol == AetherProfile.PROTO_WG_OVER_MASQUE) return plain
        var named: ScanResult? = null
        return { line ->
            val found = parse(profile, line)
            when {
                found != null -> {
                    named = found
                    null
                }
                exitAccepted.containsMatchIn(line) -> named
                exitRejected.containsMatchIn(line) -> {
                    named = null
                    null
                }
                else -> null
            }
        }
    }

    fun parse(profile: AetherProfile, line: String): ScanResult? = when (profile.protocol) {
        AetherProfile.PROTO_GOOL -> hopsOf(goolHops, line)
        AetherProfile.PROTO_MIM -> hopsOf(mimHops, line)
        AetherProfile.PROTO_MASQUE -> masqueGateway.find(line)?.let { ScanResult(it.groupValues[1]) }
        // Once the whole tunnel carries, the gateway alone: the WireGuard endpoint inside it is the profile's, which the scan kept.
        AetherProfile.PROTO_WG_OVER_MASQUE -> goolOverMasqueReady.find(line)?.let { ScanResult(it.groupValues[1]) }
        else -> wireguardEndpoint.find(line)?.let { ScanResult(it.groupValues[1]) }
    }

    private fun hopsOf(hops: Regex, line: String): ScanResult? = hops.find(line)?.let { match ->
        val outer = match.groupValues[1]
        val inner = match.groupValues[2]
        if (outer.isNotEmpty() && inner.isNotEmpty()) ScanResult(outer, inner) else null
    }
}

package dev.valid7996.narcicgeteway.ui

import android.content.Context
import dev.valid7996.narcicgeteway.core.AetherIdentityStore
import dev.valid7996.narcicgeteway.core.AetherScanner
import dev.valid7996.narcicgeteway.core.AetherSupport
import dev.valid7996.narcicgeteway.core.LogBus
import dev.valid7996.narcicgeteway.core.PsiphonServerList
import dev.valid7996.narcicgeteway.domain.config.AetherCommands
import dev.valid7996.narcicgeteway.domain.config.AetherProfile
import dev.valid7996.narcicgeteway.ui.servers.AetherActions
import dev.valid7996.narcicgeteway.ui.servers.AetherCoreInfo
import dev.valid7996.narcicgeteway.ui.servers.AetherKeyUiEntry
import dev.valid7996.narcicgeteway.ui.servers.AetherScanUiResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The Aether answers of Android: the scanner, the WARP keys, Psiphon / Tor status and registration. */
class AetherActionsAndroid(private val context: Context) : AetherActions {

    override suspend fun scan(profile: AetherProfile, onOutput: (String) -> Unit): AetherScanUiResult? =
        AetherScanner.scan(context, profile) { line ->
            onOutput(line)
            relayScan(line)
        }?.let { AetherScanUiResult(it.endpoint, it.innerHop) }

    override fun cancelScan() {
        AetherScanner.cancel()
    }

    // The exit proxy waits up to ten seconds for Xray to answer, with Thread.sleep: run on the main dispatcher, as the
    // keys page launches it, that froze the phone. Everything of the registration runs on IO.
    override suspend fun registerKeys(request: AetherCommands.KeysRequest, onOutput: (String) -> Unit): Boolean =
        withContext(Dispatchers.IO) {
            val workDir = AetherSupport.workDir(context).apply { mkdirs() }
            // The calls to the WARP API leave through the exit of the page's own finalMask and dialMode.
            val upstream = AetherSupport.startExitProxy(
                AetherProfile(finalMask = request.finalMask.trim(), dialMode = request.dialMode.trim()),
            )
            try {
                AetherIdentityStore.register(
                    context = context,
                    request = request,
                    upstreamPort = upstream,
                    workDir = workDir,
                ) { line ->
                    onOutput(line)
                    relayKey(line)
                }
            } finally {
                AetherSupport.stopExitProxy()
            }
        }

    override suspend fun coreInfo(): AetherCoreInfo = withContext(Dispatchers.IO) {
        val caps = AetherSupport.capabilities(context)
        AetherCoreInfo(caps.version, caps.masqueSni, caps.wgOverMasque)
    }

    override suspend fun keys(): List<AetherKeyUiEntry> = AetherIdentityStore.keys(context).map { entry ->
        AetherKeyUiEntry(entry.file, entry.identity?.deviceId, entry.identity?.ipv4, entry.identity?.ipv6)
    }

    override suspend fun clearPsiphonData(): Boolean = withContext(Dispatchers.IO) {
        val session = AetherSupport.psiphonDir(context).deleteRecursively()
        val probe = AetherSupport.psiphonProbeDir(context).deleteRecursively()
        PsiphonServerList.remember(emptyList())
        session && probe
    }

    /** The countries Psiphon can leave from: what its client last reported, else what the servers of the bundled list offer. */
    override suspend fun psiphonRegions(): List<String> = withContext(Dispatchers.IO) {
        PsiphonServerList.remembered().ifEmpty {
            val workDir = AetherSupport.workDir(context).apply { mkdirs() }
            PsiphonServerList.entriesFile(context, workDir)?.let { file ->
                runCatching { PsiphonServerList.regions(file.readText()) }.getOrDefault(emptySet())
            }.orEmpty()
        }.toList()
    }

    override fun isCoreAvailable(): Boolean = AetherSupport.isCoreSupported(context)

    override fun isPsiphonAvailable(): Boolean = AetherSupport.isPsiphonSupported(context)

    override fun isTorTransportsAvailable(): Boolean = AetherSupport.isTorTransportsSupported(context)

    private fun relayScan(line: String) {
        LogBus.append("${AetherSupport.levelOf(line)}/aether-scan ${line.trim()}")
    }

    private fun relayKey(line: String) {
        LogBus.append("${AetherSupport.levelOf(line)}/aether-key ${line.trim()}")
    }
}

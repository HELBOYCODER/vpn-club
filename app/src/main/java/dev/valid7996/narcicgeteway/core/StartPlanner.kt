package dev.valid7996.narcicgeteway.core

import android.content.Context
import dev.valid7996.narcicgeteway.R
import dev.valid7996.narcicgeteway.NrgSecureApp
import dev.valid7996.narcicgeteway.data.assets.GeoAssetsRepository
import dev.valid7996.narcicgeteway.domain.config.ConfigParser
import dev.valid7996.narcicgeteway.domain.config.Ikev2Profile
import dev.valid7996.narcicgeteway.domain.config.LocalPorts
import dev.valid7996.narcicgeteway.domain.config.LocalProxy
import dev.valid7996.narcicgeteway.domain.config.ProfileSource
import dev.valid7996.narcicgeteway.domain.config.RoutingMigration
import dev.valid7996.narcicgeteway.domain.config.ServerConfig
import dev.valid7996.narcicgeteway.domain.config.UnsupportedTransportException
import dev.valid7996.narcicgeteway.domain.config.VpnProfile
import dev.valid7996.narcicgeteway.domain.config.XrayJsonBuilder
import dev.valid7996.narcicgeteway.domain.config.autoSelectTuning
import dev.valid7996.narcicgeteway.domain.config.singBoxDeviceOptions
import dev.valid7996.narcicgeteway.domain.config.toBuildOptions
import dev.valid7996.narcicgeteway.domain.model.AppSettings
import dev.valid7996.narcicgeteway.domain.model.OutboundDomainResolve
import dev.valid7996.narcicgeteway.domain.model.RunMode
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class StartPlanner(context: Context) {
    private val appContext = context.applicationContext
    private val container get() = (appContext as NrgSecureApp).container
    private val configRepository get() = container.configRepository
    private val settingsRepository get() = container.settingsRepository

    sealed interface Plan {
        data class Start(
            val configJson: String,
            val remark: String,
            val kind: String,
            val socksPort: Int,
            val proxyOnly: Boolean,
        ) : Plan

        data class Ikev2(val remark: String, val profile: Ikev2Profile) : Plan

        data class Failure(val message: String) : Plan
    }

    fun planActive(allowProxyOnly: Boolean): Plan = plan(configRepository.activeProfile(), allowProxyOnly)

    fun plan(profile: VpnProfile?, allowProxyOnly: Boolean = true): Plan {
        AutoSelect.clearPrepared()
        if (profile == null) return Plan.Failure(appContext.getString(R.string.select_config_first))
        val exp = profile.expiresAt
        if (profile.isLocked && exp != null && System.currentTimeMillis() >= exp) {
            return Plan.Failure(appContext.getString(R.string.zsx_expired))
        }
        val effective = profile.resolveUnsealed() ?: profile
        effective.ikev2Settings()?.let { return Plan.Ikev2(profile.name, it) }
        effective.mhrvRelaySettings()?.let {
            if (!it.isValid) return Plan.Failure(appContext.getString(R.string.mhrv_missing_creds))
        }
        val kind = engineKindOf(effective)
        val configJson = try {
            if (kind == VpnManager.KIND_CROSS_CHAIN) buildCrossChainWrapper(effective)
            else engineConfigOf(effective, resolveServers = kind == VpnManager.KIND_XRAY)
        } catch (e: Exception) {
            return Plan.Failure(messageFor(e))
        }
        val settings = settingsRepository.settings.value

        val proxyOnly = allowProxyOnly && settings.runMode == RunMode.ProxyOnly && kind == VpnManager.KIND_XRAY
        return Plan.Start(
            configJson = configJson,
            remark = if (profile.isAutoSelect) autoSelectRemark(profile) else profile.name,
            kind = kind,
            socksPort = LocalProxy.SOCKS_PORT,
            proxyOnly = proxyOnly,
        )
    }

    private fun autoSelectRemark(profile: VpnProfile): String {
        val group = when (profile.autoSelectSettings()?.subscriptionId) {
            null -> appContext.getString(R.string.auto_group_all_short)
            "" -> appContext.getString(R.string.auto_group_manual_short)
            else -> profile.name
        }
        return appContext.getString(R.string.auto_notif_remark, group)
    }

    private fun messageFor(e: Exception): String = when (e) {
        is UnsupportedTransportException -> appContext.getString(R.string.transport_unsupported, e.transport)

        is dev.valid7996.narcicgeteway.crypto.ZsxLegacyException -> appContext.getString(R.string.zsx_legacy)
        is dev.valid7996.narcicgeteway.crypto.ZsxExpiredException -> appContext.getString(R.string.zsx_expired)

        is dev.valid7996.narcicgeteway.data.config.ProxyChainMemberMissingException ->
            appContext.getString(R.string.chain_member_missing, e.missing.joinToString(", "))

        is dev.valid7996.narcicgeteway.data.config.CrossChainUnsupportedException ->
            if (e.reason == dev.valid7996.narcicgeteway.data.config.CrossChainUnsupportedException.CARRIER) {
                appContext.getString(R.string.crosschain_bad_carrier, e.engine)
            } else {
                appContext.getString(R.string.crosschain_bad_dialer, e.engine)
            }

        is dev.valid7996.narcicgeteway.data.config.CrossChainUdpUnsupportedException ->
            appContext.getString(R.string.crosschain_udp_unsupported, e.protocol, e.carrier)
        is dev.valid7996.narcicgeteway.domain.config.UdpHopUnsupportedException ->
            appContext.getString(R.string.chain_udp_hop_unsupported, e.member, e.previous)
        is dev.valid7996.narcicgeteway.domain.config.ServerlessHopException -> appContext.getString(
            when (e.reason) {
                dev.valid7996.narcicgeteway.domain.config.ServerlessHopException.Reason.NotFirst -> R.string.chain_serverless_not_first
                dev.valid7996.narcicgeteway.domain.config.ServerlessHopException.Reason.NoServer -> R.string.chain_serverless_no_server
                dev.valid7996.narcicgeteway.domain.config.ServerlessHopException.Reason.NoDirectOutbound -> R.string.chain_serverless_no_direct
            },
        )
        is dev.valid7996.narcicgeteway.domain.config.AutoSelectNoMembersException ->
            appContext.getString(R.string.auto_no_members)
        else -> appContext.getString(R.string.config_invalid)
    }

    fun engineKindOf(profile: VpnProfile): String = when {
        profile.isCrossChain -> VpnManager.KIND_CROSS_CHAIN
        profile.psiphonSettings() != null -> VpnManager.KIND_PSIPHON
        profile.dnsTunnelSettings() != null -> VpnManager.KIND_DNS_TUNNEL
        profile.masterDnsSettings() != null -> VpnManager.KIND_MASTERDNS
        profile.mhrvRelaySettings() != null -> VpnManager.KIND_MHRV
        profile.openConnectSettings() != null -> VpnManager.KIND_OPENCONNECT
        profile.aetherSettings() != null -> VpnManager.KIND_AETHER
        profile.ikev2Settings() != null -> VpnManager.KIND_IKEV2
        profile.isTor -> VpnManager.KIND_TOR
        profile.sshSettings() != null -> VpnManager.KIND_SSH
        profile.sniSpoofSettings() != null -> VpnManager.KIND_SNISPOOF
        profile.isSingBoxConfig -> VpnManager.KIND_SINGBOX
        else -> VpnManager.KIND_XRAY
    }

    private fun engineConfigOf(
        profile: VpnProfile,
        carrier: Boolean = false,
        resolveServers: Boolean = false,
    ): String {
        profile.sniSpoofSettings()?.let { sniSpoof ->
            val geoReady = GeoAssetsRepository(appContext).allPresent()
            val xray = profile.toXrayConfigJson(
                settingsRepository.settings.value.buildOptions(geoReady, carrier),
            )
            return org.json.JSONObject().apply {
                put("xray", org.json.JSONObject(xray))
                put("sni", org.json.JSONObject().apply {
                    put("fakeSni", sniSpoof.fakeSni)
                    put("cleanIp", sniSpoof.cleanIp)
                    put("noRoot", sniSpoof.noRoot)
                    put("method", sniSpoof.method)
                    put("connectPort", sniSpoof.connectPort)
                })
            }.toString()
        }
        if (profile.isTor) return "tor"
        (profile.source as? dev.valid7996.narcicgeteway.domain.config.ProfileSource.SingBoxConfig)?.let { src ->

            val s = settingsRepository.settings.value
            return dev.valid7996.narcicgeteway.domain.config.SingBoxConfigs.prepareForDevice(
                src.json,
                ipv6 = s.enableIpv6,
                device = s.singBoxDeviceOptions(),
            ).json
        }
        profile.sshSettings()?.let {
            return kotlinx.serialization.json.Json.encodeToString(
                dev.valid7996.narcicgeteway.domain.config.SshProfile.serializer(), it,
            )
        }
        profile.psiphonSettings()?.let { psiphon ->
            val dataDir = java.io.File(appContext.filesDir, "psiphon").apply { mkdirs() }
            return dev.valid7996.narcicgeteway.domain.config.PsiphonConfigBuilder.build(
                profile = psiphon,
                dataDirPath = dataDir.absolutePath,
                socksPort = LocalPorts.PSIPHON_SOCKS,
                httpPort = LocalPorts.PSIPHON_HTTP,
            )
        }
        profile.masterDnsSettings()?.let {
            return kotlinx.serialization.json.Json.encodeToString(
                dev.valid7996.narcicgeteway.domain.config.MasterDnsProfile.serializer(), it,
            )
        }
        profile.mhrvRelaySettings()?.let {
            return kotlinx.serialization.json.Json.encodeToString(
                dev.valid7996.narcicgeteway.domain.config.MhrvRelayProfile.serializer(), it,
            )
        }
        profile.openConnectSettings()?.let {
            return kotlinx.serialization.json.Json.encodeToString(
                dev.valid7996.narcicgeteway.domain.config.OpenConnectProfile.serializer(), it,
            )
        }
        profile.aetherSettings()?.let { aether ->
            val profileJson = kotlinx.serialization.json.Json.encodeToString(
                dev.valid7996.narcicgeteway.domain.config.AetherProfile.serializer(), aether,
            )
            // The routing settings are rules of Xray: with any set, an Xray applies them in front of the core.
            val geoReady = GeoAssetsRepository(appContext).allPresent()
            val options = settingsRepository.settings.value.buildOptions(geoReady)
            if (!dev.valid7996.narcicgeteway.domain.config.AetherFront.needed(options)) return profileJson
            return org.json.JSONObject().apply {
                put("aether", org.json.JSONObject(profileJson))
                put("front", dev.valid7996.narcicgeteway.domain.config.AetherFront.config(aether, options))
            }.toString()
        }
        profile.ikev2Settings()?.let {
            return kotlinx.serialization.json.Json.encodeToString(
                dev.valid7996.narcicgeteway.domain.config.Ikev2Profile.serializer(), it,
            )
        }
        profile.dnsTunnelSettings()?.let { dnsTunnel ->
            val s = settingsRepository.settings.value
            var effective = dnsTunnel

            if (s.dnsGlobalResolverEnabled && s.dnsGlobalResolvers.isNotBlank()) {
                effective = effective.copy(resolvers = s.dnsGlobalResolvers)
            }

            val workers = when (s.dnsWorkerMode) {
                "two" -> 2; "three" -> 3; "five" -> 5; else -> 0
            }
            if (workers > 0) {
                effective = effective.copy(
                    resolverMode = dev.valid7996.narcicgeteway.domain.config.DnsTunnelProfile.MODE_ROUND_ROBIN,
                    rrSpreadCount = workers,
                )
            }
            return kotlinx.serialization.json.Json.encodeToString(
                dev.valid7996.narcicgeteway.domain.config.DnsTunnelProfile.serializer(), effective,
            )
        }
        val geoReady = GeoAssetsRepository(appContext).allPresent()
        val settings = settingsRepository.settings.value
        if (profile.isAutoSelect) {
            val build = configRepository.buildAutoSelectConfig(
                profile,
                settings.buildOptions(geoReady, carrier),
                settings.autoSelectTuning(),
            )
            AutoSelect.prepare(profile.id, build.memberProfiles)
            return build.json
        }
        val options = settings.buildOptions(geoReady, carrier, if (resolveServers) profile else null)

        return if (profile.proxyChainSettings() != null) {
            configRepository.buildChainConfig(profile, options)
        } else {
            profile.toXrayConfigJson(options)
        }
    }

    private fun buildCrossChainWrapper(profile: VpnProfile): String {
        val (inner, outer) = configRepository.crossChainMembers(profile)

        if (inner.isTor &&
            !dev.valid7996.narcicgeteway.core.tor.TorConfigBuilder.supportsUpstreamProxy(
                settingsRepository.settings.value,
            )
        ) {
            throw dev.valid7996.narcicgeteway.data.config.CrossChainUnsupportedException(
                dev.valid7996.narcicgeteway.data.config.CrossChainUnsupportedException.DIALER, inner.name,
            )
        }
        return org.json.JSONObject().apply {
            put("innerKind", engineKindOf(inner))
            put("inner", engineConfigOf(inner))
            put("outerKind", engineKindOf(outer))

            put("outer", engineConfigOf(outer, carrier = true))
        }.toString()
    }

    private fun AppSettings.buildOptions(
        geoReady: Boolean,
        carrier: Boolean = false,
        resolveFor: VpnProfile? = null,
    ): XrayJsonBuilder.BuildOptions {
        val ruleOutbounds = configRepository.resolveRuleOutbounds(RoutingMigration.effectiveRulesets(this))
        val hosts = if (resolveFor != null && outboundDomainResolve != OutboundDomainResolve.DoNotResolve) {
            resolveServerHosts(physicalServersOf(resolveFor) + ruleOutbounds.values, preferIpv6)
        } else emptyMap()
        return toBuildOptions(
            geoAssetsAvailable = geoReady,
            carrier = carrier,
            ruleOutbounds = ruleOutbounds,
            resolvedServerHosts = hosts,
            useXrayTun = !useHevTun && !carrier && runMode == dev.valid7996.narcicgeteway.domain.model.RunMode.Vpn,
        )
    }

    private fun physicalServersOf(profile: VpnProfile): List<ServerConfig> {
        val link = when (val src = profile.source) {
            is ProfileSource.Link -> src.link
            is ProfileSource.ProxyChain -> src.memberIds.firstOrNull()
                ?.let { configRepository.profile(it) }
                ?.takeIf { it.source is ProfileSource.Link }
                ?.rawPayload()
            else -> null
        } ?: return emptyList()
        return listOfNotNull(runCatching { ConfigParser.parse(link) }.getOrNull())
    }

    private fun resolveServerHosts(servers: Collection<ServerConfig>, preferIpv6: Boolean): Map<String, List<String>> {
        val hosts = servers.map { it.address.trim() }
            .filter { it.isNotEmpty() && !XrayJsonBuilder.isIpLiteral(it) }
            .distinct()
        if (hosts.isEmpty()) return emptyMap()
        val pool = Executors.newFixedThreadPool(hosts.size.coerceAtMost(4))
        return try {
            val pending = hosts.associateWith { host ->
                pool.submit(Callable { InetAddress.getAllByName(host).toList() })
            }
            val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(RESOLVE_BUDGET_MS)
            pending.mapNotNull { (host, future) ->
                val left = (deadline - System.nanoTime()).coerceAtLeast(0)
                val addresses = runCatching { future.get(left, TimeUnit.NANOSECONDS) }.getOrNull()
                    ?: return@mapNotNull null
                orderAddresses(addresses, preferIpv6).takeIf { it.isNotEmpty() }?.let { host to it }
            }.toMap()
        } finally {
            pool.shutdownNow()
        }
    }

    private fun orderAddresses(addresses: List<InetAddress>, preferIpv6: Boolean): List<String> {
        val v4 = addresses.filterIsInstance<Inet4Address>().mapNotNull { it.hostAddress }
        if (!preferIpv6) return v4.distinct()
        val v6 = addresses.filterIsInstance<Inet6Address>().mapNotNull { it.hostAddress?.substringBefore('%') }
        return (v6 + v4).distinct()
    }

    private companion object {
        const val RESOLVE_BUDGET_MS = 2_500L
    }
}

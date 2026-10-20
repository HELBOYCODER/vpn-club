@file:OptIn(kotlin.io.encoding.ExperimentalEncodingApi::class)

package dev.valid7996.narcicgeteway.data.config

import dev.valid7996.narcicgeteway.platform.AppInfo
import dev.valid7996.narcicgeteway.platform.KeyValueStore
import dev.valid7996.narcicgeteway.platform.currentTimeMillis
import dev.valid7996.narcicgeteway.platform.HttpTextResponse
import dev.valid7996.narcicgeteway.platform.httpGetResponse
import dev.valid7996.narcicgeteway.platform.newId
import dev.valid7996.narcicgeteway.crypto.ZsxCrypto
import dev.valid7996.narcicgeteway.crypto.ZsxExpiredException
import dev.valid7996.narcicgeteway.crypto.ZsxMetadata
import dev.valid7996.narcicgeteway.crypto.ZsxSealRequest
import dev.valid7996.narcicgeteway.domain.config.AetherLink
import dev.valid7996.narcicgeteway.domain.config.AmneziaLink
import dev.valid7996.narcicgeteway.domain.config.AutoMember
import dev.valid7996.narcicgeteway.domain.config.AutoSelectBuild
import dev.valid7996.narcicgeteway.domain.config.AutoSelectIds
import dev.valid7996.narcicgeteway.domain.config.AutoSelectTuning
import dev.valid7996.narcicgeteway.domain.config.SniSpoofLink
import dev.valid7996.narcicgeteway.domain.config.NrgLink
import dev.valid7996.narcicgeteway.domain.config.ConfigParser
import dev.valid7996.narcicgeteway.domain.config.AwgConfig
import dev.valid7996.narcicgeteway.domain.config.ShareLink
import dev.valid7996.narcicgeteway.domain.config.CustomConfig
import dev.valid7996.narcicgeteway.domain.config.ProfileSource
import dev.valid7996.narcicgeteway.domain.config.OvpnConfig
import dev.valid7996.narcicgeteway.domain.config.Protocol
import dev.valid7996.narcicgeteway.domain.config.ServerConfig
import dev.valid7996.narcicgeteway.domain.config.SsdLink
import dev.valid7996.narcicgeteway.domain.config.SshLink
import dev.valid7996.narcicgeteway.domain.config.SingBoxJson
import dev.valid7996.narcicgeteway.domain.config.SingBoxLinks
import dev.valid7996.narcicgeteway.domain.config.SingBoxOnlyServers
import dev.valid7996.narcicgeteway.domain.config.Subscription
import dev.valid7996.narcicgeteway.domain.config.SubscriptionHeaders
import dev.valid7996.narcicgeteway.domain.config.SubscriptionMeta
import dev.valid7996.narcicgeteway.domain.config.SubscriptionParser
import dev.valid7996.narcicgeteway.domain.config.SubscriptionSchedule
import dev.valid7996.narcicgeteway.domain.config.VpnProfile
import dev.valid7996.narcicgeteway.domain.config.XrayJsonBuilder
import kotlin.io.encoding.Base64
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject

class ProxyChainMemberMissingException(val missing: List<String>) :
    Exception("Proxy chain is missing ${missing.size} member(s): ${missing.joinToString(", ")}")

class CrossChainUnsupportedException(val reason: String, val engine: String) :
    Exception("Cross chain unsupported ($reason): $engine") {
    companion object {
        const val CARRIER = "carrier"
        const val DIALER = "dialer"
    }
}

class CrossChainUdpUnsupportedException(val protocol: String, val carrier: String) :
    Exception("$protocol needs UDP, which $carrier cannot relay")

class ConfigRepository(private val store: KeyValueStore) {
    private val json = Json { ignoreUnknownKeys = true }
    private val lenientJson = Json { ignoreUnknownKeys = true; isLenient = true }

    private val _profiles = MutableStateFlow(loadProfiles())
    val profiles: StateFlow<List<VpnProfile>> = _profiles.asStateFlow()

    private val _subscriptions = MutableStateFlow(loadSubscriptions())
    val subscriptions: StateFlow<List<Subscription>> = _subscriptions.asStateFlow()

    private val _activeId = MutableStateFlow(store.getString(KEY_ACTIVE))
    val activeId: StateFlow<String?> = _activeId.asStateFlow()

    init {
        val savedIp = store.getString(KEY_SNISPOOF_IP)?.takeIf { it.isNotBlank() }
        if (savedIp != null) dev.valid7996.narcicgeteway.domain.config.SniSpoofConfig.defaultConnectIp = savedIp
        val savedPort = store.getInt(KEY_SNISPOOF_PORT, 0).takeIf { it in 1..65535 }
        if (savedPort != null) dev.valid7996.narcicgeteway.domain.config.SniSpoofConfig.defaultConnectPort = savedPort
        val savedSni = store.getString(KEY_SNISPOOF_SNI)?.takeIf { it.isNotBlank() }
        if (savedSni != null) dev.valid7996.narcicgeteway.domain.config.SniSpoofConfig.defaultFakeSni = savedSni
        val savedMethod = store.getString(KEY_SNISPOOF_METHOD)?.takeIf { it.isNotBlank() }
        if (savedMethod != null) dev.valid7996.narcicgeteway.domain.config.SniSpoofConfig.defaultMethod =
            dev.valid7996.narcicgeteway.domain.config.SniSpoofConfig.method(savedMethod)
    }

    fun activeProfile(): VpnProfile? = _activeId.value?.let { profile(it) }

    fun profile(id: String): VpnProfile? =
        _profiles.value.firstOrNull { it.id == id } ?: autoSelectProfile(id)

    private fun activeExists(): Boolean = _activeId.value?.let { profile(it) } != null

    fun importText(text: String, subscriptionId: String = ""): Result<Int> = runCatching {
        val trimmed = text.trim()
        require(trimmed.isNotEmpty()) { "empty" }

        val sshLines = trimmed.lines().map { it.trim() }.filter { SshLink.isSshLink(it) }
        if (sshLines.isNotEmpty()) {
            var added = 0
            sshLines.forEach { line ->
                SshLink.parse(line)?.let { (nm, prof) -> addSsh(prof, nm); added++ }
            }
            if (added > 0) return@runCatching added
            throw IllegalArgumentException("no usable ssh:// config found")
        }

        val aetherLines = trimmed.lines().map { it.trim() }.filter { AetherLink.isAetherLink(it) }
        if (aetherLines.isNotEmpty()) {
            var added = 0
            aetherLines.forEach { line ->
                AetherLink.parse(line)?.let { (nm, prof) -> addAether(prof, nm); added++ }
            }
            if (added > 0) return@runCatching added
            throw IllegalArgumentException("no usable aether:// config found")
        }

        if (NrgLink.isNrgLink(trimmed)) return@runCatching importNrgLink(trimmed)

        if (SniSpoofLink.isSniSpoofLink(trimmed)) {
            val (nm, prof) = SniSpoofLink.parse(trimmed)
                ?: throw IllegalArgumentException("invalid snispoof:// link")
            addSniSpoof(prof, nm)
            return@runCatching 1
        }

        val nrgMatch = dev.valid7996.narcicgeteway.domain.config.LockedConfigExtractor.extractLink(trimmed)
        if (nrgMatch != null) {
            val bytes = dev.valid7996.narcicgeteway.crypto.NrgLink.decode(nrgMatch)
            val meta = ZsxCrypto.peek(bytes)
            if (meta.isExpired) throw ZsxExpiredException()

            if (meta.deviceBound) {
                // First try automatic unlock with this device's own ID
                val autoImp = importLocked(bytes, null)
                if (autoImp.isSuccess) return@runCatching 1

                val extractedKey = dev.valid7996.narcicgeteway.domain.config.LockedConfigExtractor.extractKey(trimmed)
                if (extractedKey != null) {
                    val keyImp = importLocked(bytes, extractedKey)
                    if (keyImp.isSuccess) return@runCatching 1
                }

                // If both failed, this config was locked for a different device
                throw IllegalArgumentException("این کانفیگ اختصاصی برای دستگاه دیگری قفل شده است و روی این دستگاه فعال نمی‌شود.")
            }

            val extractedKey = dev.valid7996.narcicgeteway.domain.config.LockedConfigExtractor.extractKey(trimmed)
            if (meta.passwordProtected && extractedKey != null && !isKeyConsumed(extractedKey)) {
                val imp = importLocked(bytes, extractedKey)
                if (imp.isSuccess) return@runCatching 1
            }

            if (meta.passwordProtected) {
                throw dev.valid7996.narcicgeteway.crypto.ZsxPasswordRequiredException()
            }
            importLocked(bytes, null).getOrThrow()
            return@runCatching 1
        }

        if (SsdLink.isSsdLink(trimmed)) {
            val links = SsdLink.parse(trimmed)
            require(links.isNotEmpty()) { "no servers in this ssd:// subscription" }
            return@runCatching importText(links.joinToString("\n"), subscriptionId).getOrThrow()
        }

        if (AmneziaLink.isAmneziaLink(trimmed) || AmneziaLink.isAmneziaJson(trimmed) ||
            AmneziaLink.isAmneziaBlob(trimmed)
        ) {
            when (val payload = AmneziaLink.parse(trimmed)) {
                is AmneziaLink.Payload.Configs -> {
                    var added = 0
                    var lastError: Throwable? = null
                    payload.items.forEach { item ->

                        importText(item.text, subscriptionId)
                            .onSuccess { added += it }
                            .onFailure { lastError = it }
                    }
                    if (added == 0) {
                        throw IllegalArgumentException(
                            lastError?.message?.takeIf { it.isNotBlank() } ?: "unsupported vpn:// config",
                        )
                    }
                    return@runCatching added
                }

                AmneziaLink.Payload.Backup -> throw AmneziaLink.UnsupportedException(
                    AmneziaLink.Reason.Backup,
                    "this is an Amnezia backup - restore it, don't import it",
                )
                AmneziaLink.Payload.Subscription -> throw AmneziaLink.UnsupportedException(
                    AmneziaLink.Reason.Subscription,
                    "Amnezia Free/Premium subscriptions need Amnezia's own servers",
                )
                is AmneziaLink.Payload.Unusable -> throw AmneziaLink.UnsupportedException(
                    AmneziaLink.Reason.NoRunnableContainer,
                    "no runnable protocol in this Amnezia config (${payload.reason})",
                )
                AmneziaLink.Payload.Unsupported ->
                    throw IllegalArgumentException("unsupported vpn:// config")
            }
        }

        if (OvpnConfig.looksLikeOvpn(trimmed)) {
            addOvpn(trimmed, subscriptionId = subscriptionId).getOrThrow()
            return@runCatching 1
        }

        if (ConfigParser.looksLikeWireguardConf(trimmed)) {
            val link = ShareLink.build(ConfigParser.parseWireguardConf(trimmed))
            return@runCatching importText(link, subscriptionId).getOrThrow()
        }

        val bodies = buildList {
            runCatching { ConfigParser.decodeBase64Utf8(trimmed) }
                .getOrNull()
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?.let { add(it) }
            add(trimmed)
        }.distinct()

        bodies.forEach { body ->

            importSingBoxJson(body, subscriptionId)?.let { return@runCatching it }
        }

        bodies.forEach { body ->
            val custom = importCustomJson(body, subscriptionId)
            if (custom > 0) return@runCatching custom
        }

        val links = bodies.map { SubscriptionParser.extractLinks(it) }
            .firstOrNull { it.isNotEmpty() }
            ?: throw IllegalArgumentException("no supported links found")

        val current = _profiles.value
        val fresh = mutableListOf<VpnProfile>()
        var duplicates = 0
        links.forEach { link ->
            runCatching {
                if (SingBoxLinks.handles(link)) {
                    val parsed = SingBoxLinks.parse(link)
                    val server = SingBoxJson.servers(parsed.fragment).first()
                    VpnProfile.fromSingBox(
                        server = server,
                        id = newId(),
                        addedAt = currentTimeMillis(),
                        subscriptionId = subscriptionId,
                        name = parsed.name,
                        link = link,
                    )
                } else {
                    VpnProfile.fromLink(
                        link = link,
                        id = newId(),
                        addedAt = currentTimeMillis(),
                        subscriptionId = subscriptionId,
                    )
                }
            }.onSuccess { profile ->
                val duplicate = (current + fresh).any {
                    it.rawPayload() == link && it.subscriptionId == subscriptionId
                }
                if (duplicate) duplicates++ else fresh += profile
            }
        }

        lastImportDuplicates = duplicates

        _profiles.value =
            if (subscriptionId.isBlank()) fresh + current else current + fresh
        persistProfiles()
        if (_activeId.value == null) setActive(_profiles.value.firstOrNull()?.id)
        fresh.size
    }

    private var lastImportDuplicates: Int = 0

    private fun importSingBoxJson(body: String, subscriptionId: String): Int? {
        val shape = SingBoxJson.shapeOf(body)
        if (shape == SingBoxJson.Shape.None) return null
        if (shape == SingBoxJson.Shape.FullConfig && subscriptionId.isBlank()) {
            addSingBoxConfig(body).getOrThrow()
            return 1
        }
        val servers = SingBoxJson.servers(body)
        if (servers.isEmpty()) throw IllegalArgumentException("no sing-box servers found")
        val current = _profiles.value
        val fresh = mutableListOf<VpnProfile>()
        var duplicates = 0
        servers.forEach { server ->
            val profile = VpnProfile.fromSingBox(
                server = server,
                id = newId(),
                addedAt = currentTimeMillis(),
                subscriptionId = subscriptionId,
            )
            val duplicate = (current + fresh).any {
                it.subscriptionId == subscriptionId && (it.source as? ProfileSource.SingBox)?.json == server.fragment
            }
            if (duplicate) duplicates++ else fresh += profile
        }
        lastImportDuplicates = duplicates
        _profiles.value = if (subscriptionId.isBlank()) fresh + current else current + fresh
        persistProfiles()
        if (_activeId.value == null) setActive(_profiles.value.firstOrNull()?.id)
        return fresh.size
    }

    fun addOvpn(
        text: String,
        name: String? = null,
        username: String? = null,
        password: String? = null,
        subscriptionId: String = "",
    ): Result<VpnProfile> = runCatching {
        val fragment = OvpnConfig.toSingBoxFragment(text, name = name, username = username, password = password)
        val server = SingBoxJson.servers(fragment).firstOrNull()
            ?: throw IllegalArgumentException("no OpenVPN server in the profile")
        val profile = VpnProfile.fromSingBox(
            server = server,
            id = newId(),
            addedAt = currentTimeMillis(),
            subscriptionId = subscriptionId,
            name = name,
        )
        profile.toXrayConfigJson()
        _profiles.value =
            if (subscriptionId.isBlank()) listOf(profile) + _profiles.value else _profiles.value + profile
        persistProfiles()
        if (_activeId.value == null) setActive(profile.id)
        profile
    }

    fun addSingBoxConfig(json: String, name: String? = null, subscriptionId: String = ""): Result<VpnProfile> = runCatching {
        require(SingBoxJson.shapeOf(json) != SingBoxJson.Shape.None) { "not a sing-box config" }
        val profile = VpnProfile.fromSingBoxConfig(
            json = json,
            id = newId(),
            addedAt = currentTimeMillis(),
            name = name,
            subscriptionId = subscriptionId,
        )
        _profiles.value = if (subscriptionId.isBlank()) listOf(profile) + _profiles.value else _profiles.value + profile
        persistProfiles()
        if (_activeId.value == null) setActive(profile.id)
        profile
    }

    fun updateSingBox(id: String, json: String): Result<VpnProfile> = runCatching {
        val existing = profile(id) ?: throw IllegalArgumentException("unknown profile")
        val rebuilt = when (existing.source) {
            is ProfileSource.SingBox -> {
                val server = SingBoxJson.servers(json).firstOrNull()
                    ?: throw IllegalArgumentException("no sing-box server in the JSON")
                VpnProfile.fromSingBox(server, existing.id, existing.addedAt, existing.subscriptionId, existing.name)
            }
            is ProfileSource.SingBoxConfig -> {
                require(SingBoxJson.shapeOf(json) != SingBoxJson.Shape.None) { "not a sing-box config" }
                VpnProfile.fromSingBoxConfig(json, existing.id, existing.addedAt, existing.name, existing.subscriptionId)
            }
            else -> throw IllegalArgumentException("not a sing-box profile")
        }.copy(lastPingMs = existing.lastPingMs, countryCode = existing.countryCode)
        _profiles.value = _profiles.value.map { if (it.id == id) rebuilt else it }
        persistProfiles()
        rebuilt
    }

    private fun importCustomJson(body: String, subscriptionId: String): Int {
        if (!CustomConfig.looksLikeCustomJson(body)) return 0

        val clean = dev.valid7996.narcicgeteway.domain.config.Jsonc.strip(body)
        val element = runCatching { lenientJson.parseToJsonElement(clean) }.getOrNull() ?: return 0

        val objects: List<JsonObject> = when (element) {
            is JsonArray -> element.mapNotNull { it as? JsonObject }
            is JsonObject -> listOf(element)
            else -> emptyList()
        }
        if (objects.isEmpty()) return 0

        var added = 0
        objects.forEach { obj ->
            val raw = Json { prettyPrint = true }
                .encodeToString(JsonObject.serializer(), obj)
            runCatching { addRawJson(raw, subscriptionId = subscriptionId).getOrThrow() }
                .onSuccess { added++ }
        }
        return added
    }

    fun addRawJson(
        rawJson: String,
        name: String? = null,
        subscriptionId: String = "",
    ): Result<VpnProfile> = runCatching {
        val profile = VpnProfile.fromRawJson(
            rawJson = rawJson,
            id = newId(),
            addedAt = currentTimeMillis(),
            fallbackName = name,
            subscriptionId = subscriptionId,
        )

        profile.toXrayConfigJson()

        _profiles.value =
            if (subscriptionId.isBlank()) listOf(profile) + _profiles.value
            else _profiles.value + profile
        persistProfiles()
        if (_activeId.value == null) setActive(profile.id)
        profile
    }

    fun updateRawJson(id: String, rawJson: String): Result<VpnProfile> = runCatching {
        val existing = profile(id) ?: throw IllegalArgumentException("unknown profile")
        val rebuilt = VpnProfile.fromRawJson(
            rawJson = rawJson,
            id = existing.id,
            addedAt = existing.addedAt,
            fallbackName = existing.name,
            subscriptionId = existing.subscriptionId,
        ).copy(lastPingMs = existing.lastPingMs, countryCode = existing.countryCode)
        rebuilt.toXrayConfigJson()
        _profiles.value = _profiles.value.map { if (it.id == id) rebuilt else it }
        persistProfiles()
        rebuilt
    }

    private fun carryOver(existing: VpnProfile?, built: VpnProfile): VpnProfile =
        if (existing == null) built else built.copy(
            subscriptionId = existing.subscriptionId,
            lastPingMs = existing.lastPingMs,
            countryCode = existing.countryCode,
            bytesDown = existing.bytesDown,
            bytesUp = existing.bytesUp,
        )

    fun addPsiphon(
        settings: dev.valid7996.narcicgeteway.domain.config.PsiphonProfile,
        name: String,
        id: String? = null,
    ): VpnProfile {
        val existing = id?.let { profile(it) }
        val profile = VpnProfile.fromPsiphon(
            settings = settings,
            id = existing?.id ?: newId(),
            addedAt = existing?.addedAt ?: currentTimeMillis(),
            name = name,
        ).let { carryOver(existing, it) }
        _profiles.value = if (existing != null) {
            _profiles.value.map { if (it.id == profile.id) profile else it }
        } else {
            listOf(profile) + _profiles.value
        }
        persistProfiles()
        if (_activeId.value == null) setActive(profile.id)
        return profile
    }

    fun addDnsTunnel(
        settings: dev.valid7996.narcicgeteway.domain.config.DnsTunnelProfile,
        name: String,
        id: String? = null,
    ): VpnProfile {
        val existing = id?.let { profile(it) }
        val profile = VpnProfile.fromDnsTunnel(
            settings = settings,
            id = existing?.id ?: newId(),
            addedAt = existing?.addedAt ?: currentTimeMillis(),
            name = name,
        ).let { carryOver(existing, it) }
        _profiles.value = if (existing != null) {
            _profiles.value.map { if (it.id == profile.id) profile else it }
        } else {
            listOf(profile) + _profiles.value
        }
        persistProfiles()
        if (_activeId.value == null) setActive(profile.id)
        return profile
    }

    fun addMasterDns(
        settings: dev.valid7996.narcicgeteway.domain.config.MasterDnsProfile,
        name: String,
        id: String? = null,
    ): VpnProfile {
        val existing = id?.let { profile(it) }
        val profile = VpnProfile.fromMasterDns(
            settings = settings,
            id = existing?.id ?: newId(),
            addedAt = existing?.addedAt ?: currentTimeMillis(),
            name = name,
        ).let { carryOver(existing, it) }
        _profiles.value = if (existing != null) {
            _profiles.value.map { if (it.id == profile.id) profile else it }
        } else {
            listOf(profile) + _profiles.value
        }
        persistProfiles()
        if (_activeId.value == null) setActive(profile.id)
        return profile
    }

    fun addOpenConnect(
        settings: dev.valid7996.narcicgeteway.domain.config.OpenConnectProfile,
        name: String,
        id: String? = null,
    ): VpnProfile {
        val existing = id?.let { profile(it) }
        val profile = VpnProfile.fromOpenConnect(
            settings = settings,
            id = existing?.id ?: newId(),
            addedAt = existing?.addedAt ?: currentTimeMillis(),
            name = name,
        ).let { carryOver(existing, it) }
        _profiles.value = if (existing != null) {
            _profiles.value.map { if (it.id == profile.id) profile else it }
        } else {
            listOf(profile) + _profiles.value
        }
        persistProfiles()
        if (_activeId.value == null) setActive(profile.id)
        return profile
    }

    fun rememberOpenConnectCert(id: String, pin: String): Boolean {
        val target = profile(id) ?: return false
        val settings = target.openConnectSettings() ?: return false
        if (pin.isBlank() || settings.serverCertSha256 == pin) return false
        addOpenConnect(settings.copy(serverCertSha256 = pin), target.name, target.id)
        return true
    }

    fun addAether(
        settings: dev.valid7996.narcicgeteway.domain.config.AetherProfile,
        name: String,
        id: String? = null,
    ): VpnProfile {
        val existing = id?.let { profile(it) }
        val profile = VpnProfile.fromAether(
            settings = settings,
            id = existing?.id ?: newId(),
            addedAt = existing?.addedAt ?: currentTimeMillis(),
            name = name,
        ).let { carryOver(existing, it) }
        _profiles.value = if (existing != null) {
            _profiles.value.map { if (it.id == profile.id) profile else it }
        } else {
            listOf(profile) + _profiles.value
        }
        persistProfiles()
        if (_activeId.value == null) setActive(profile.id)
        return profile
    }

    fun addIkev2(
        settings: dev.valid7996.narcicgeteway.domain.config.Ikev2Profile,
        name: String,
        id: String? = null,
    ): VpnProfile {
        val existing = id?.let { profile(it) }
        val profile = VpnProfile.fromIkev2(
            settings = settings,
            id = existing?.id ?: newId(),
            addedAt = existing?.addedAt ?: currentTimeMillis(),
            name = name,
        ).let { carryOver(existing, it) }
        _profiles.value = if (existing != null) {
            _profiles.value.map { if (it.id == profile.id) profile else it }
        } else {
            listOf(profile) + _profiles.value
        }
        persistProfiles()
        if (_activeId.value == null) setActive(profile.id)
        return profile
    }

    fun addProxyChain(memberIds: List<String>, name: String, id: String? = null): VpnProfile {
        val existing = id?.let { profile(it) }
        val profile = VpnProfile.fromProxyChain(
            memberIds = memberIds,
            id = existing?.id ?: newId(),
            addedAt = existing?.addedAt ?: currentTimeMillis(),
            name = name,
        ).let { built ->

            if (existing == null) built else built.copy(
                lastPingMs = existing.lastPingMs,
                countryCode = existing.countryCode,
                bytesDown = existing.bytesDown,
                bytesUp = existing.bytesUp,
                subscriptionId = existing.subscriptionId,
            )
        }
        _profiles.value = if (existing != null) {
            _profiles.value.map { if (it.id == profile.id) profile else it }
        } else {
            listOf(profile) + _profiles.value
        }
        persistProfiles()
        if (_activeId.value == null) setActive(profile.id)
        return profile
    }

    fun addCrossChain(
        innerId: String,
        outerId: String,
        name: String,
        id: String? = null,
    ): VpnProfile {
        val existing = id?.let { profile(it) }
        val profile = VpnProfile.fromCrossChain(
            innerId = innerId,
            outerId = outerId,
            id = existing?.id ?: newId(),
            addedAt = existing?.addedAt ?: currentTimeMillis(),
            name = name,
            innerName = profile(innerId)?.name.orEmpty(),
            outerName = profile(outerId)?.name.orEmpty(),
        ).let { built ->
            if (existing == null) built else built.copy(
                lastPingMs = existing.lastPingMs,
                countryCode = existing.countryCode,
                bytesDown = existing.bytesDown,
                bytesUp = existing.bytesUp,
                subscriptionId = existing.subscriptionId,
            )
        }
        _profiles.value = if (existing != null) {
            _profiles.value.map { if (it.id == profile.id) profile else it }
        } else {
            listOf(profile) + _profiles.value
        }
        persistProfiles()
        if (_activeId.value == null) setActive(profile.id)
        return profile
    }

    fun crossChainMembers(profile: VpnProfile): Pair<VpnProfile, VpnProfile> {
        val (innerId, outerId) = profile.crossChainSettings()
            ?: throw IllegalStateException("not a cross-chain profile")
        val missing = mutableListOf<String>()
        val inner = profile(innerId) ?: run { missing += innerId.take(8); null }
        val outer = profile(outerId) ?: run { missing += outerId.take(8); null }
        if (missing.isNotEmpty()) throw ProxyChainMemberMissingException(missing)
        inner!!; outer!!
        if (!outer.canCarryChain) {
            throw CrossChainUnsupportedException(CrossChainUnsupportedException.CARRIER, outer.name)
        }
        if (!inner.canDialThroughProxy) {
            throw CrossChainUnsupportedException(CrossChainUnsupportedException.DIALER, inner.name)
        }
        udpMismatch(inner, outer)?.let { throw it }
        return inner to outer
    }

    fun udpMismatch(inner: VpnProfile, outer: VpnProfile): CrossChainUdpUnsupportedException? {
        if (outer.carrierRelaysUdp) return null
        val offending = udpProtocolsOf(inner) ?: return null
        return CrossChainUdpUnsupportedException(offending, outer.name)
    }

    private val SEALED_UNKNOWN = "locked config"

    private fun udpProtocolsOf(profile: VpnProfile): String? {
        fun offending(c: ServerConfig): String? {
            val udpProtocol = c.protocol.isWireguardFamily || c.protocol == Protocol.HYSTERIA
            val udpTransport = c.transport.network.lowercase() in setOf("kcp", "mkcp")
            return when {
                udpProtocol -> c.protocol.label
                udpTransport -> c.transport.network.uppercase()
                else -> null
            }
        }
        profile.proxyChainSettings()?.let { ids ->
            return ids.asSequence()
                .mapNotNull { profile(it)?.rawPayload() }
                .mapNotNull { runCatching { ConfigParser.parse(it) }.getOrNull() }
                .mapNotNull { offending(it) }
                .firstOrNull()
        }

        if (profile.source is ProfileSource.Sealed) return SEALED_UNKNOWN

        (profile.source as? ProfileSource.SingBox)?.let { src ->
            val server = SingBoxJson.servers(src.json).firstOrNull() ?: return null
            return server.label.takeIf { server.udp }
        }
        val payload = profile.rawPayload() ?: return null
        runCatching { ConfigParser.parse(payload) }.getOrNull()?.let { return offending(it) }

        val lower = payload.lowercase()
        return when {
            "\"protocol\": \"wireguard\"" in lower || "\"protocol\":\"wireguard\"" in lower -> "WireGuard"
            "\"protocol\": \"hysteria\"" in lower || "\"protocol\":\"hysteria\"" in lower -> "Hysteria2"
            "\"network\": \"kcp\"" in lower || "\"network\":\"kcp\"" in lower -> "mKCP"
            else -> null
        }
    }

    fun buildChainConfig(
        profile: VpnProfile,
        options: XrayJsonBuilder.BuildOptions,
        forSpeedtest: Boolean = false,
    ): String {
        val ids = profile.proxyChainSettings()
            ?: throw IllegalStateException("not a proxy-chain profile")
        val missing = mutableListOf<String>()
        val servers = ids.mapNotNull { memberId ->
            val member = profile(memberId)
            if (member == null) {
                missing += memberId.take(8)
                return@mapNotNull null
            }

            (member.source as? ProfileSource.SingBox)?.let { src ->
                val server = SingBoxJson.servers(src.json).firstOrNull()
                    ?: run { missing += member.name; return@mapNotNull null }
                return@mapNotNull XrayJsonBuilder.ChainHop.SingBox(src.json, src.carrier, server)
            }
            (member.source as? ProfileSource.RawJson)?.takeIf { member.isServerless }?.let { src ->
                return@mapNotNull XrayJsonBuilder.ChainHop.Serverless(src.json)
            }
            val payload = member.rawPayload()
            if (payload == null) {
                missing += member.name
                return@mapNotNull null
            }
            if (SingBoxLinks.handles(payload)) {
                val parsed = runCatching { SingBoxLinks.parse(payload) }.getOrNull()
                val server = parsed?.let { SingBoxJson.servers(it.fragment).firstOrNull() }
                    ?: run { missing += member.name; return@mapNotNull null }
                return@mapNotNull XrayJsonBuilder.ChainHop.SingBox(parsed.fragment, parsed.name, server)
            }
            runCatching { ConfigParser.parse(payload) }.getOrNull()?.let { XrayJsonBuilder.ChainHop.Xray(it) }
                ?: run { missing += member.name; null }
        }
        if (missing.isNotEmpty()) throw ProxyChainMemberMissingException(missing)
        require(servers.isNotEmpty()) { "proxy chain has no usable members" }
        return XrayJsonBuilder.buildChainHops(servers, options = options, forSpeedtest = forSpeedtest)
    }

    fun chainProbeConfig(profile: VpnProfile): String? {
        if (!profile.isProxyChain) return null
        return runCatching {
            buildChainConfig(profile, XrayJsonBuilder.BuildOptions(), forSpeedtest = true)
        }.getOrNull()
    }

    fun resolveRuleOutbounds(
        rulesets: List<dev.valid7996.narcicgeteway.domain.model.RulesetItem>,
    ): Map<String, ServerConfig> = rulesets
        .asSequence()
        .filter { it.enabled }
        .mapNotNull { rule ->
            val id = dev.valid7996.narcicgeteway.domain.model.RulesetItem.profileIdOf(rule.outboundTag)
                ?: return@mapNotNull null
            val payload = profile(id)?.takeIf { it.source is ProfileSource.Link }?.rawPayload()
                ?: return@mapNotNull null
            runCatching { ConfigParser.parse(payload) }.getOrNull()?.let { rule.outboundTag to it }
        }
        .toMap()

    fun plainWireguardAmong(ids: Collection<String>): List<String> =
        _profiles.value.filter { p ->
            p.id in ids && p.protocol == Protocol.WIREGUARD.name &&
                runCatching { ConfigParser.parse(p.rawPayload().orEmpty()).awg.isEmpty }
                    .getOrDefault(false)
        }.map { it.id }

    fun enableWireguardObfuscation(ids: Collection<String>): Int {
        var changed = 0
        _profiles.value = _profiles.value.map { p ->
            if (p.id !in ids) return@map p
            val link = p.rawPayload() ?: return@map p
            val parsed = runCatching { ConfigParser.parse(link) }.getOrNull() ?: return@map p
            if (parsed.protocol != Protocol.WIREGUARD || !parsed.awg.isEmpty) return@map p
            val obfuscated = parsed.copy(
                protocol = Protocol.AMNEZIAWG,
                awg = AwgConfig.amneziaDefaults(),
            )
            changed++
            p.copy(
                protocol = Protocol.AMNEZIAWG.name,
                transportLabel = obfuscated.transportLabel,
                source = ProfileSource.Link(ShareLink.build(obfuscated)),
            )
        }
        if (changed > 0) persistProfiles()
        return changed
    }

    fun shareLinkOf(profile: VpnProfile): String? {
        val src = profile.source
        val memberIds = when (src) {
            is ProfileSource.ProxyChain -> src.memberIds
            is ProfileSource.CrossChain -> listOf(src.innerId, src.outerId)
            else -> return profile.rawPayload()
        }
        val byId = _profiles.value.associateBy { it.id }
        val members = memberIds.map { id ->
            val member = byId[id] ?: return null

            shareLinkOf(member) ?: return null
        }
        return NrgLink.build(profile.name, src, members)
    }

    private fun importNrgLink(text: String): Int {
        val payload = NrgLink.parse(text)
            ?: throw IllegalArgumentException("invalid share link")
        val name = payload.name
        return when (val src = payload.source) {
            is ProfileSource.Psiphon -> { addPsiphon(src.settings, name); 1 }
            is ProfileSource.DnsTunnel -> { addDnsTunnel(src.settings, name); 1 }
            is ProfileSource.Tor -> { addTor(name); 1 }
            is ProfileSource.Ssh -> { addSsh(src.settings, name); 1 }
            is ProfileSource.MasterDns -> { addMasterDns(src.settings, name); 1 }
            is ProfileSource.OpenConnect -> { addOpenConnect(src.settings, name); 1 }
            is ProfileSource.Ikev2 -> { addIkev2(src.settings, name); 1 }
            is ProfileSource.Aether -> { addAether(src.settings, name); 1 }
            is ProfileSource.ProxyChain, is ProfileSource.CrossChain -> {
                val ids = payload.members.map { link ->
                    val before = _profiles.value.map { it.id }.toSet()
                    importText(link).getOrThrow()
                    _profiles.value.firstOrNull { it.id !in before }?.id
                        ?: throw IllegalArgumentException("chain member did not import")
                }
                if (src is ProfileSource.ProxyChain) addProxyChain(ids, name)
                else addCrossChain(ids[0], ids[1], name)
                1
            }
            else -> throw IllegalArgumentException("unsupported share link payload")
        }
    }

    fun autoSelectProfile(id: String): VpnProfile? {
        if (!AutoSelectIds.isAuto(id)) return null
        val subscriptionId = AutoSelectIds.subscriptionOf(id)
        val groupName = when (subscriptionId) {
            null, "" -> ""
            else -> _subscriptions.value.firstOrNull { it.id == subscriptionId }?.name ?: return null
        }
        return VpnProfile.fromAutoSelect(subscriptionId, groupName, autoSelectMembers(subscriptionId).size)
    }

    fun autoSelectMembers(subscriptionId: String?): List<VpnProfile> = _profiles.value.filter { p ->
        !p.isLocked &&
            (subscriptionId == null || p.subscriptionId == subscriptionId) &&
            (p.source is ProfileSource.Link || p.source is ProfileSource.RawJson || p.source is ProfileSource.SingBox)
    }

    fun buildAutoSelectConfig(
        profile: VpnProfile,
        options: XrayJsonBuilder.BuildOptions,
        tuning: AutoSelectTuning,
    ): AutoSelectBuild {
        val group = profile.autoSelectSettings() ?: throw IllegalStateException("not an auto-select profile")
        val members = autoSelectMembers(group.subscriptionId)
        val built = members.mapNotNull { p ->
            when (val src = p.source) {
                is ProfileSource.Link -> if (SingBoxLinks.handles(src.link)) {
                    runCatching { SingBoxLinks.parse(src.link) }.getOrNull()
                        ?.let { AutoMember.SingBox(p.id, it.fragment, it.name) }
                } else {
                    runCatching { ConfigParser.parse(src.link) }.getOrNull()
                        ?.let { AutoMember.Server(p.id, it) }
                }
                is ProfileSource.RawJson -> AutoMember.Custom(p.id, src.json)
                is ProfileSource.SingBox -> AutoMember.SingBox(p.id, src.json, src.carrier)
                else -> null
            }
        }
        val remembered = lastAutoSelectPick(profile.id)?.takeIf { id -> members.any { it.id == id } }
        val fastest = members.filter { (it.lastPingMs ?: -1) > 0 }.minByOrNull { it.lastPingMs!! }?.id
        return XrayJsonBuilder.buildAutoSelect(
            members = built,
            tuning = tuning.copy(initialProfileId = tuning.initialProfileId ?: remembered ?: fastest),
            options = options,
        )
    }

    fun rememberAutoSelectPick(autoId: String, memberProfileId: String) {
        if (lastAutoSelectPick(autoId) != memberProfileId) store.putString(KEY_AUTO_PICK + autoId, memberProfileId)
    }

    fun lastAutoSelectPick(autoId: String): String? = store.getString(KEY_AUTO_PICK + autoId)

    fun getSniSpoofDefaults(): dev.valid7996.narcicgeteway.domain.config.SniSpoofProfile =
        dev.valid7996.narcicgeteway.domain.config.SniSpoofProfile(
            link = "",
            cleanIp = dev.valid7996.narcicgeteway.domain.config.SniSpoofConfig.defaultConnectIp,
            connectPort = dev.valid7996.narcicgeteway.domain.config.SniSpoofConfig.defaultConnectPort,
            fakeSni = dev.valid7996.narcicgeteway.domain.config.SniSpoofConfig.defaultFakeSni,
            method = dev.valid7996.narcicgeteway.domain.config.SniSpoofConfig.defaultMethod,
            noRoot = true,
        )

    fun saveSniSpoofDefaults(cleanIp: String, connectPort: Int, fakeSni: String, method: String) {
        val ip = cleanIp.trim().ifBlank { dev.valid7996.narcicgeteway.domain.config.SniSpoofConfig.DEFAULT_CONNECT_IP }
        val port = if (connectPort in 1..65535) connectPort else dev.valid7996.narcicgeteway.domain.config.SniSpoofConfig.DEFAULT_CONNECT_PORT
        val sni = fakeSni.trim().ifBlank { dev.valid7996.narcicgeteway.domain.config.SniSpoofConfig.DEFAULT_FAKE_SNI }
        val m = dev.valid7996.narcicgeteway.domain.config.SniSpoofConfig.method(method)

        dev.valid7996.narcicgeteway.domain.config.SniSpoofConfig.defaultConnectIp = ip
        dev.valid7996.narcicgeteway.domain.config.SniSpoofConfig.defaultConnectPort = port
        dev.valid7996.narcicgeteway.domain.config.SniSpoofConfig.defaultFakeSni = sni
        dev.valid7996.narcicgeteway.domain.config.SniSpoofConfig.defaultMethod = m

        store.putString(KEY_SNISPOOF_IP, ip)
        store.putInt(KEY_SNISPOOF_PORT, port)
        store.putString(KEY_SNISPOOF_SNI, sni)
        store.putString(KEY_SNISPOOF_METHOD, m)

        var changed = false
        val updated = _profiles.value.map { profile ->
            (profile.source as? ProfileSource.SniSpoof)?.let { src ->
                changed = true
                val s = src.settings.copy(
                    cleanIp = ip,
                    connectPort = port,
                    fakeSni = sni,
                    method = m,
                )
                profile.copy(source = ProfileSource.SniSpoof(s))
            } ?: profile
        }
        if (changed) {
            _profiles.value = updated
            persistProfiles()
        }
    }

    fun addSniSpoof(
        settings: dev.valid7996.narcicgeteway.domain.config.SniSpoofProfile,
        name: String,
        id: String? = null,
    ): VpnProfile {
        saveSniSpoofDefaults(settings.cleanIp, settings.connectPort, settings.fakeSni, settings.method)
        val existing = id?.let { profile(it) }
        val profile = VpnProfile.fromSniSpoof(
            settings = settings,
            id = existing?.id ?: newId(),
            addedAt = existing?.addedAt ?: currentTimeMillis(),
            name = name,
        ).let { carryOver(existing, it) }

        _profiles.value = if (existing != null) {
            _profiles.value.map { if (it.id == profile.id) profile else it }
        } else {
            listOf(profile) + _profiles.value
        }
        persistProfiles()
        if (_activeId.value == null) setActive(profile.id)
        return profile
    }

    fun addSsh(settings: dev.valid7996.narcicgeteway.domain.config.SshProfile, name: String): VpnProfile {
        val profile = VpnProfile.fromSsh(
            settings = settings,
            id = newId(),
            addedAt = currentTimeMillis(),
            name = name,
        )
        _profiles.value = listOf(profile) + _profiles.value
        persistProfiles()
        if (_activeId.value == null) setActive(profile.id)
        return profile
    }

    fun addTor(name: String): VpnProfile {
        val profile = VpnProfile.fromTor(
            id = newId(),
            addedAt = currentTimeMillis(),
            name = name,
        )
        _profiles.value = listOf(profile) + _profiles.value
        persistProfiles()
        if (_activeId.value == null) setActive(profile.id)
        return profile
    }

    fun update(profile: VpnProfile) {
        _profiles.update { list -> list.map { if (it.id == profile.id) profile else it } }
        persistProfiles()
    }

    fun reorder(shownIdsInNewOrder: List<String>) {
        val current = _profiles.value
        val shownSet = shownIdsInNewOrder.toSet()
        val byId = current.associateBy { it.id }
        val newShown = shownIdsInNewOrder.mapNotNull { byId[it] }.iterator()
        _profiles.value = current.map { if (it.id in shownSet && newShown.hasNext()) newShown.next() else it }
        persistProfiles()
    }

    fun rename(id: String, newName: String) {
        profile(id)?.let { update(it.copy(name = newName)) }
    }

    fun remove(id: String) {
        _profiles.value = _profiles.value.filterNot { it.id == id }

        scrubChainMember(id)
        if (_activeId.value == id) setActive(_profiles.value.firstOrNull()?.id)
        persistProfiles()
    }

    private fun scrubChainMember(memberId: String) {
        val affected = _profiles.value.filter {
            it.proxyChainSettings()?.contains(memberId) == true
        }
        if (affected.isEmpty()) return
        val doomed = mutableSetOf<String>()
        _profiles.update { list ->
            list.mapNotNull { p ->
                val ids = p.proxyChainSettings() ?: return@mapNotNull p
                if (memberId !in ids) return@mapNotNull p
                val kept = ids.filterNot { it == memberId }
                if (kept.size < 2) {
                    doomed += p.id
                    null
                } else {
                    VpnProfile.fromProxyChain(
                        memberIds = kept,
                        id = p.id,
                        addedAt = p.addedAt,
                        name = p.name,
                    ).copy(
                        lastPingMs = p.lastPingMs,
                        countryCode = p.countryCode,
                        bytesDown = p.bytesDown,
                        bytesUp = p.bytesUp,
                        subscriptionId = p.subscriptionId,
                    )
                }
            }
        }
        if (_activeId.value in doomed) setActive(_profiles.value.firstOrNull()?.id)
    }

    fun removeAll(predicate: (VpnProfile) -> Boolean) {
        _profiles.value = _profiles.value.filterNot(predicate)
        if (!activeExists()) {
            setActive(_profiles.value.firstOrNull()?.id)
        }
        persistProfiles()
    }

    /**
     * Removes profiles imported from a specific subscription (if subscriptionId is given) or all
     * subscriptions (if subscriptionId is null). Locked vault profiles are kept. Returns the count
     * of removed profiles.
     */
    fun removeSubscriptionConfigs(subscriptionId: String? = null): Int {
        val before = _profiles.value.size
        _profiles.value = _profiles.value.filter {
            if (it.isLocked) return@filter true
            if (subscriptionId != null) {
                it.subscriptionId != subscriptionId
            } else {
                it.subscriptionId.isBlank()
            }
        }
        val removed = before - _profiles.value.size
        if (removed > 0) {
            if (!activeExists()) setActive(_profiles.value.firstOrNull()?.id)
            persistProfiles()
            _subscriptions.value = _subscriptions.value.map { sub ->
                if (subscriptionId == null || sub.id == subscriptionId) {
                    sub.copy(serverCount = _profiles.value.count { it.subscriptionId == sub.id })
                } else {
                    sub
                }
            }
            persistSubscriptions()
        }
        return removed
    }

    fun removeDuplicates(): Int {
        val seen = HashSet<String>()
        val kept = ArrayList<VpnProfile>()
        var removed = 0
        _profiles.value.forEach { profile ->
            val key = profile.rawPayload() ?: profile.id
            if (profile.isLocked || seen.add(key)) kept += profile else removed++
        }
        if (removed > 0) {
            _profiles.value = kept
            if (!activeExists()) setActive(kept.firstOrNull()?.id)
            persistProfiles()
        }
        return removed
    }

    fun removeInvalid(): Int {
        val before = _profiles.value.size
        _profiles.value = _profiles.value.filter { keepAsValid(it) }
        val removed = before - _profiles.value.size
        if (removed > 0) {
            if (!activeExists()) {
                setActive(_profiles.value.firstOrNull()?.id)
            }
            persistProfiles()
        }
        return removed
    }

    fun invalidCount(): Int = _profiles.value.count { !keepAsValid(it) }

    private fun keepAsValid(profile: VpnProfile): Boolean {
        if (profile.isLocked || profile.isManagedTunnel || profile.isProxyChain) return true

        if (!profile.isCustom && !profile.isSingBoxConfig &&
            (profile.address.isBlank() || profile.address == "-" || profile.port <= 0)
        ) {
            return false
        }

        profile.lastPingMs?.let { if (it < 0) return false }
        return runCatching { profile.toXrayConfigJson() }.isSuccess
    }

    fun sortByTestResults() {
        _profiles.value = _profiles.value.sortedWith(
            compareBy { it.lastPingMs?.takeIf { ms -> ms > 0 } ?: Int.MAX_VALUE },
        )
        persistProfiles()
    }

    fun exportAllPayloads(): String =
        _profiles.value.mapNotNull { it.rawPayload() }.joinToString("\n")

    fun setActive(id: String?) {
        _activeId.value = id
        store.putString(KEY_ACTIVE, id)
    }

    fun addUsage(id: String, downDelta: Long, upDelta: Long, persist: Boolean) {
        if (downDelta <= 0 && upDelta <= 0 && !persist) return
        _profiles.update { list ->
            list.map {
                if (it.id == id) it.copy(bytesDown = it.bytesDown + downDelta, bytesUp = it.bytesUp + upDelta)
                else it
            }
        }
        if (persist) persistProfiles()
    }

    fun resetUsage(id: String) {
        profile(id)?.let { update(it.copy(bytesDown = 0, bytesUp = 0)) }
    }

    fun setPing(id: String, pingMs: Int?, countryCode: String? = null, persist: Boolean = true) {
        _profiles.update { list ->
            list.map {
                if (it.id == id) it.copy(lastPingMs = pingMs, countryCode = countryCode ?: it.countryCode)
                else it
            }
        }
        if (persist) persistProfiles()
    }

    fun flushProfiles() = persistProfiles()

    fun clearPings(ids: Collection<String>, persist: Boolean = true) {
        if (ids.isEmpty()) return
        val target = ids.toSet()
        _profiles.update { list ->
            list.map { if (it.id in target && it.lastPingMs != null) it.copy(lastPingMs = null) else it }
        }
        if (persist) persistProfiles()
    }

    fun isSubscriptionUrl(text: String): Boolean = subscriptionUrlIn(text) != null

    suspend fun importPasted(rawText: String, subscriptionId: String = ""): Result<PastedImport> {
        val text = if (dev.valid7996.narcicgeteway.crypto.NrgLink.findRelay(rawText) != null) {
            resolveNrgRelay(rawText).getOrElse { return Result.failure(it) }
        } else {
            rawText
        }
        subscriptionUrlIn(text)?.let { return importSubscriptionUrl(it, name = null) }
        val parsed = runCatching {
            lastImportDuplicates = 0
            val added = importText(text, subscriptionId).getOrThrow()
            PastedImport(count = added, subscription = subscriptionId.isNotBlank(), duplicates = lastImportDuplicates)
        }
        if (parsed.getOrNull()?.let { it.count > 0 || it.duplicates > 0 } == true) return parsed
        val url = soleUrlIn(text) ?: return parsed
        return importSubscriptionUrl(url, name = null, keepIfUnreachable = false)
    }

    suspend fun importSubscriptionUrl(
        url: String,
        name: String?,
        keepIfUnreachable: Boolean = true,
    ): Result<PastedImport> = runCatching {
        val existing = _subscriptions.value.firstOrNull { it.url.equals(url, ignoreCase = true) }
        val sub = existing ?: addSubscription(name = name?.trim().orEmpty(), url = url)
        val added = updateSubscription(sub.id).getOrElse { e ->
            val savedForLater = existing == null && keepIfUnreachable && e is SubscriptionUnreachableException
            if (existing == null && !savedForLater) removeSubscription(sub.id)
            throw SubscriptionNotFetchedException(
                name = _subscriptions.value.firstOrNull { it.id == sub.id }?.name ?: sub.name,
                savedForLater = savedForLater,
                cause = e,
            )
        }

        val finalName = _subscriptions.value.firstOrNull { it.id == sub.id }?.name ?: sub.name
        PastedImport(count = added, subscription = true, subscriptionName = finalName)
    }

    class SubscriptionUnreachableException(cause: Throwable) : Exception(cause.message, cause)

    class SubscriptionNotFetchedException(
        val name: String,
        val savedForLater: Boolean,
        cause: Throwable,
    ) : Exception(cause.message, cause)

    data class PastedImport(
        val count: Int,
        val subscription: Boolean,
        val subscriptionName: String = "",

        val duplicates: Int = 0,
    )

    fun addSubscription(name: String, url: String, userAgent: String? = null): Subscription {
        val sub = Subscription(
            id = newId(),
            name = name.ifBlank { url.take(40) },
            url = url.trim(),
            userAgent = userAgent?.trim()?.takeIf { it.isNotBlank() },
        )
        _subscriptions.value = _subscriptions.value + sub
        persistSubscriptions()
        return sub
    }

    fun addGroup(name: String): Subscription {
        val group = Subscription(id = newId(), name = name.trim().ifBlank { "Group" }, url = "")
        _subscriptions.value = _subscriptions.value + group
        persistSubscriptions()
        return group
    }

    fun moveToGroup(profileId: String, groupId: String) = moveToGroup(listOf(profileId), groupId)

    fun moveToGroup(profileIds: Collection<String>, groupId: String) {
        val ids = profileIds.toSet()
        if (ids.isEmpty()) return
        _profiles.value = _profiles.value.map {
            if (it.id in ids) it.copy(subscriptionId = groupId) else it
        }
        persistProfiles()
    }

    fun removeAll(ids: Collection<String>) {
        val doomed = ids.toSet()
        if (doomed.isEmpty()) return
        _profiles.value = _profiles.value.filterNot { it.id in doomed }
        doomed.forEach { scrubChainMember(it) }
        if (_activeId.value in doomed) setActive(_profiles.value.firstOrNull()?.id)
        persistProfiles()
    }

    fun renameSubscription(id: String, newName: String) {
        val trimmed = newName.trim()
        if (trimmed.isEmpty()) return
        _subscriptions.value = _subscriptions.value.map {
            if (it.id == id) it.copy(name = trimmed) else it
        }
        persistSubscriptions()
    }

    fun editSubscription(id: String, newName: String, userAgent: String?, newUrl: String? = null) {
        val trimmedName = newName.trim()
        val trimmedUa = userAgent?.trim()?.takeIf { it.isNotBlank() }
        val trimmedUrl = newUrl?.trim()?.takeIf { it.isNotBlank() }
        _subscriptions.value = _subscriptions.value.map {
            if (it.id != id) it
            else it.copy(
                name = trimmedName.ifEmpty { it.name },
                url = trimmedUrl ?: it.url,
                userAgent = trimmedUa,
            )
        }
        persistSubscriptions()
    }

    fun looksLikeSubscriptionUrl(text: String): Boolean = subscriptionUrlIn(text) != null

    private fun subscriptionUrlIn(text: String): String? {
        val t = text.withoutFormatChars().trim()
        if (t.isEmpty() || t.any { it.isWhitespace() }) return null
        return t.takeIf { it.startsWith("http://", true) || it.startsWith("https://", true) }
    }

    private fun soleUrlIn(text: String): String? =
        URL_IN_TEXT.findAll(text.withoutFormatChars())
            .map { it.value.trimEnd(*URL_TRAILING_PUNCTUATION) }
            .filter { it.length > "https://".length }
            .distinct()
            .singleOrNull()

    private fun String.withoutFormatChars(): String = filterNot { it.category == CharCategory.FORMAT }

    private fun reuseIdentities(subscriptionId: String, previous: List<VpnProfile>) {
        if (previous.isEmpty()) return
        val taken = mutableSetOf<String>()
        fun keys(p: VpnProfile) = listOf(
            p.rawPayload()?.let { "link:$it" },
            "ep:${p.name}|${p.address}|${p.port}",
            "name:${p.name}",
        )

        val byKey = HashMap<String, VpnProfile>()
        for (tier in 0..2) {
            previous.forEach { old ->
                val k = keys(old).getOrNull(tier) ?: return@forEach
                byKey.putIfAbsent(k, old)
            }
        }
        _profiles.update { list ->
            list.map { p ->
                if (p.subscriptionId != subscriptionId) return@map p
                val old = keys(p).asSequence()
                    .filterNotNull()
                    .mapNotNull { byKey[it] }
                    .firstOrNull { it.id !in taken }
                    ?: return@map p
                taken += old.id
                p.copy(
                    id = old.id,
                    addedAt = old.addedAt,
                    lastPingMs = p.lastPingMs ?: old.lastPingMs,
                    countryCode = p.countryCode ?: old.countryCode,
                    bytesDown = old.bytesDown,
                    bytesUp = old.bytesUp,
                )
            }
        }
        persistProfiles()
    }

    fun removeSubscription(id: String, alsoRemoveServers: Boolean = true) {
        _subscriptions.value = _subscriptions.value.filterNot { it.id == id }
        persistSubscriptions()
        if (alsoRemoveServers) removeAll { it.subscriptionId == id }
    }

    fun setSubscriptionEnabled(id: String, enabled: Boolean) {
        _subscriptions.value = _subscriptions.value.map {
            if (it.id == id) it.copy(enabled = enabled) else it
        }
        persistSubscriptions()
    }

    suspend fun updateSubscription(id: String): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val sub = _subscriptions.value.firstOrNull { it.id == id }
                ?: throw IllegalArgumentException("unknown subscription")

            if (sub.url.isBlank()) return@runCatching 0
            val primaryUa = resolveUa(sub.userAgent)
            val response = runCatching { fetch(sub.url, primaryUa) }
                .getOrElse { throw SubscriptionUnreachableException(it) }
            val body = response.body
            var meta = SubscriptionHeaders.parse(response.headers)

            val previous = _profiles.value.filter { it.subscriptionId == id }

            val previousActive = _activeId.value

            removeAll { it.subscriptionId == id }
            val first = importText(body, subscriptionId = id)
            var added = first.getOrElse { 0 }
            if (added == 0) {
                val fallbackUas = listOf(
                    if (primaryUa.startsWith(V2RAYNG_UA_PREFIX)) ALT_UA else DEFAULT_UA,
                    SING_BOX_UA,
                ).filter { it != primaryUa }
                val seenBodies = mutableSetOf(body)
                for (fallbackUa in fallbackUas) {
                    val retry = runCatching { fetch(sub.url, fallbackUa) }.getOrNull() ?: continue
                    if (!seenBodies.add(retry.body)) continue
                    added = importText(retry.body, subscriptionId = id).getOrElse { 0 }
                    if (added > 0) {
                        if (!SubscriptionHeaders.parse(retry.headers).isEmpty) {
                            meta = SubscriptionHeaders.parse(retry.headers)
                        }
                        break
                    }
                }
            }

            if (added == 0) throw first.exceptionOrNull()
                ?: IllegalArgumentException("no supported configs found")

            previous.firstOrNull { it.id == previousActive && SingBoxOnlyServers.isPlaceholder(it) }?.let { old ->
                _profiles.value
                    .firstOrNull { it.subscriptionId == id && it.name == old.name && SingBoxOnlyServers.isPlaceholder(it) }
                    ?.let { setActive(it.id) }
            }

            added += replaceSingBoxPlaceholders(id, sub.url)
            reuseIdentities(id, previous)
            if (previousActive != null && _activeId.value != previousActive &&
                profile(previousActive) != null
            ) {
                setActive(previousActive)
            }
            _subscriptions.value = _subscriptions.value.map {
                if (it.id == id) {
                    it.withMeta(meta).copy(lastUpdated = currentTimeMillis(), serverCount = added)
                } else {
                    it
                }
            }
            persistSubscriptions()
            added
        }
    }

    private fun replaceSingBoxPlaceholders(subscriptionId: String, url: String): Int {
        val imported = _profiles.value.filter { it.subscriptionId == subscriptionId }
        val placeholders = imported.filter { SingBoxOnlyServers.isPlaceholder(it) }
        if (placeholders.isEmpty()) return 0
        val body = runCatching { fetch(url, SING_BOX_UA).body }.getOrNull() ?: return 0
        if (SingBoxJson.shapeOf(body) == SingBoxJson.Shape.None) return 0
        val missing = SingBoxOnlyServers.missing(imported, SingBoxJson.servers(body))
        if (missing.isEmpty()) return 0
        val now = currentTimeMillis()
        val fresh = missing.map {
            VpnProfile.fromSingBox(server = it, id = newId(), addedAt = now, subscriptionId = subscriptionId)
        }
        val activeIndex = placeholders.indexOfFirst { it.id == _activeId.value }
        _profiles.value = SingBoxOnlyServers.splice(_profiles.value, placeholders, fresh)
        persistProfiles()
        if (activeIndex >= 0) {
            setActive(fresh.getOrNull(activeIndex)?.takeIf { fresh.size == placeholders.size }?.id ?: fresh.first().id)
        }
        return fresh.size - placeholders.size
    }

    private fun Subscription.withMeta(meta: SubscriptionMeta): Subscription {
        val unnamed = name.isBlank() || name == url.take(40)
        return copy(
            name = if (unnamed && meta.title != null) meta.title else name,
            upload = meta.upload,
            download = meta.download,
            total = meta.total,
            expireAt = meta.expireAt,
            title = meta.title,
            updateIntervalHours = meta.updateIntervalHours,
            supportUrl = meta.supportUrl,
            webPageUrl = meta.webPageUrl,
        )
    }

    fun dueSubscriptions(globalIntervalHours: Int, nowMs: Long = currentTimeMillis()): List<Subscription> =
        _subscriptions.value.filter { SubscriptionSchedule.isDue(it, globalIntervalHours, nowMs) }

    suspend fun updateDueSubscriptions(globalIntervalHours: Int): Int {
        var ok = 0
        dueSubscriptions(globalIntervalHours).forEach { sub ->
            if (updateSubscription(sub.id).isSuccess) ok++
        }
        return ok
    }

    suspend fun updateAllSubscriptions(): Int {
        var total = 0
        _subscriptions.value.filter { it.enabled }.forEach { sub ->
            updateSubscription(sub.id).onSuccess { total += it }
        }
        return total
    }

    private fun fetch(url: String, userAgent: String? = null): HttpTextResponse {
        val ua = resolveUa(userAgent)
        val tunnelPort = dev.valid7996.narcicgeteway.core.VpnManager.activeSocksPort?.takeIf {
            it > 0 && dev.valid7996.narcicgeteway.core.VpnManager.status.value.state ==
                dev.valid7996.narcicgeteway.domain.model.ConnectionState.Connected
        }
        if (tunnelPort != null) {
            runCatching { return httpGetResponse(url, userAgent = ua, socksPort = tunnelPort) }
        }
        return httpGetResponse(url, userAgent = ua)
    }

    private fun resolveUa(userAgent: String?): String =
        userAgent?.trim()?.takeIf { it.isNotBlank() } ?: DEFAULT_UA

    fun isKeyConsumed(key: String): Boolean {
        val clean = key.trim().uppercase()
        val consumed = store.getString(KEY_CONSUMED_KEYS)?.split(",")?.toSet() ?: emptySet()
        return clean in consumed
    }

    private fun markKeyConsumed(key: String) {
        val clean = key.trim().uppercase()
        if (clean.isBlank()) return
        val current = store.getString(KEY_CONSUMED_KEYS)?.split(",")?.filter { it.isNotBlank() }?.toMutableSet() ?: mutableSetOf()
        current.add(clean)
        store.putString(KEY_CONSUMED_KEYS, current.joinToString(","))
    }

    fun createLockedZsx(request: ZsxSealRequest): ByteArray = ZsxCrypto.seal(request)

    fun peekLocked(bytes: ByteArray): ZsxMetadata = ZsxCrypto.peek(bytes)

    fun importLocked(bytes: ByteArray, password: String?): Result<VpnProfile> = runCatching {
        val meta = ZsxCrypto.peek(bytes)
        if (meta.isExpired) throw ZsxExpiredException()
        val cleanKey = password?.trim()?.takeIf { it.isNotBlank() }
        if (cleanKey != null && isKeyConsumed(cleanKey)) {
            throw dev.valid7996.narcicgeteway.crypto.ZsxKeyConsumedException()
        }
        val payload = if (meta.deviceBound) {
            val myDevice = nrgDeviceId()
            if (cleanKey != null) {
                runCatching { ZsxCrypto.open(bytes, cleanKey) }
                    .getOrElse {
                        runCatching { ZsxCrypto.open(bytes, cleanKey + "\u0000" + myDevice) }
                            .getOrElse { ZsxCrypto.open(bytes, myDevice) }
                    }
            } else {
                ZsxCrypto.open(bytes, myDevice)
            }
        } else {
            ZsxCrypto.open(bytes, cleanKey)
        }
        if (cleanKey != null) {
            markKeyConsumed(cleanKey)
        }
        val resealed = ZsxCrypto.seal(
            ZsxSealRequest(
                configPayload = payload,
                nameEn = meta.nameEn,
                nameFa = meta.nameFa,
                note = meta.note,
                expiresAt = meta.expiresAt,
                password = null,
            ),
        )
        val name = meta.nameEn.ifBlank { meta.nameFa }.ifBlank { "Locked config" }
        val profile = VpnProfile(
            id = newId(),
            name = name,
            protocol = "LOCKED",
            address = "•••",
            port = 0,
            transportLabel = "Locked · .nrg",
            source = ProfileSource.Sealed(Base64.encode(resealed)),
            addedAt = currentTimeMillis(),
            isLocked = true,
            expiresAt = meta.expiresAt,
            note = meta.note.takeIf { it.isNotBlank() },
        )
        runCatching { profile.toXrayConfigJson() }
        _profiles.value = _profiles.value + profile
        persistProfiles()
        setActive(profile.id)
        profile
    }

    fun importLockedLink(linkOrBase64: String, userKey: String?): Result<VpnProfile> = runCatching {
        val bytes = dev.valid7996.narcicgeteway.crypto.NrgLink.decode(linkOrBase64)
        importLocked(bytes, userKey?.ifBlank { null }).getOrThrow()
    }

    /**
     * If [text] contains a short relay link (nrg://r.<id>@<host>) downloads the encrypted blob from the relay and
     * returns the text with a normal self-contained nrg:// link in its place. Text without a relay link is returned as is.
     */
    suspend fun resolveNrgRelay(text: String, key: String? = null): Result<String> = runCatching {
        val relay = dev.valid7996.narcicgeteway.crypto.NrgLink.findRelay(text) ?: return@runCatching text
        val userKey = key?.trim()?.takeIf { it.isNotEmpty() }
            ?: dev.valid7996.narcicgeteway.domain.config.LockedConfigExtractor.extractKey(text)
        val blob = NrgRelay.download(relay, userKey, nrgDeviceId())
        val link = dev.valid7996.narcicgeteway.crypto.NrgLink.encode(blob)
        val replaced = dev.valid7996.narcicgeteway.crypto.NrgLink.replaceRelay(text, link)
        // A key typed in the prompt is not part of the pasted text: add it so the normal import path finds it.
        if (key != null && dev.valid7996.narcicgeteway.domain.config.LockedConfigExtractor.extractKey(text) == null) {
            replaced + "\nkey: " + key.trim()
        } else {
            replaced
        }
    }

    /** Random id of this installation; the relay uses it to tell "same device again" from "another device". */
    fun nrgDeviceId(): String {
        store.getString(KEY_NRG_DEVICE)?.takeIf { it.isNotBlank() }?.let { return it }
        val id = dev.valid7996.narcicgeteway.crypto.NrgLink.toUrlSafe(ZsxCrypto.secureRandomBytes(12))
        store.putString(KEY_NRG_DEVICE, id)
        return id
    }

    /** Reads the sealed bytes + metadata out of a pasted message (self-contained links only; resolve relay links first). */
    fun lockedFromText(text: String): Pair<ByteArray, ZsxMetadata>? {
        val link = dev.valid7996.narcicgeteway.domain.config.LockedConfigExtractor.extractLink(text) ?: return null
        val bytes = runCatching { dev.valid7996.narcicgeteway.crypto.NrgLink.decode(link) }.getOrNull() ?: return null
        val meta = runCatching { ZsxCrypto.peek(bytes) }.getOrNull() ?: return null
        return bytes to meta
    }

    /** Seller-side settings of the optional short-link relay. */
    var nrgRelayEndpoint: String
        get() = store.getString(KEY_NRG_RELAY_URL).orEmpty()
        set(value) = store.putString(KEY_NRG_RELAY_URL, value.trim().ifBlank { null })

    var nrgRelayToken: String
        get() = store.getString(KEY_NRG_RELAY_TOKEN).orEmpty()
        set(value) = store.putString(KEY_NRG_RELAY_TOKEN, value.trim().ifBlank { null })

    fun importZsxLink(linkOrBase64: String, userKey: String?): Result<VpnProfile> =
        importLockedLink(linkOrBase64, userKey)

    fun restoreBackupData(
        newProfiles: List<VpnProfile>?,
        newSubscriptions: List<Subscription>?,
        replaceProfiles: Boolean = false,
        replaceSubscriptions: Boolean = false,
    ): Pair<Int, Int> {
        var addedProfiles = 0
        var addedSubs = 0

        if (newSubscriptions != null) {
            if (replaceSubscriptions) {
                _subscriptions.value = newSubscriptions
                addedSubs = newSubscriptions.size
            } else {
                val existingIds = _subscriptions.value.map { it.id }.toSet()
                val existingUrls = _subscriptions.value.map { it.url.lowercase() }.toSet()
                val toAdd = newSubscriptions.filter { it.id !in existingIds && it.url.lowercase() !in existingUrls }
                _subscriptions.value = _subscriptions.value + toAdd
                addedSubs = toAdd.size
            }
            persistSubscriptions()
        }

        if (newProfiles != null) {
            if (replaceProfiles) {
                _profiles.value = newProfiles
                addedProfiles = newProfiles.size
            } else {
                val existingIds = _profiles.value.map { it.id }.toSet()
                val toAdd = newProfiles.filter { it.id !in existingIds }
                _profiles.value = _profiles.value + toAdd
                addedProfiles = toAdd.size
            }
            persistProfiles()
            if (_activeId.value == null || profile(_activeId.value!!) == null) {
                _profiles.value.firstOrNull()?.id?.let { setActive(it) }
            }
        }

        return Pair(addedProfiles, addedSubs)
    }

    private fun persistProfiles() {
        store.putString(KEY_PROFILES, json.encodeToString(_profiles.value))
    }

    private fun persistSubscriptions() {
        store.putString(KEY_SUBS, json.encodeToString(_subscriptions.value))
    }

    private fun loadProfiles(): List<VpnProfile> {
        val raw = store.getString(KEY_PROFILES) ?: return emptyList()
        val loaded = runCatching { json.decodeFromString<List<VpnProfile>>(raw) }.getOrDefault(emptyList())
        return repairOpenVpnType(loaded)
    }

    private fun repairOpenVpnType(profiles: List<VpnProfile>): List<VpnProfile> {
        var changed = false
        val repaired = profiles.map { profile ->
            val json = when (val source = profile.source) {
                is ProfileSource.SingBox -> source.json
                is ProfileSource.SingBoxConfig -> source.json
                else -> return@map profile
            }
            if (!json.contains("\"openvpn\"")) return@map profile
            val fixed = SingBoxJson.retypeOpenVpn(json) ?: return@map profile
            changed = true
            when (profile.source) {
                is ProfileSource.SingBox -> profile.copy(source = ProfileSource.SingBox(fixed))
                else -> profile.copy(source = ProfileSource.SingBoxConfig(fixed))
            }
        }
        if (changed) {
            store.putString(KEY_PROFILES, json.encodeToString(repaired))
        }
        return repaired
    }

    private fun loadSubscriptions(): List<Subscription> {
        val raw = store.getString(KEY_SUBS) ?: return emptyList()
        return runCatching { json.decodeFromString<List<Subscription>>(raw) }.getOrDefault(emptyList())
    }

    /** The subscriptions the app ships with, present the first time the store starts. */
    private fun defaultSubscriptions(): List<Subscription> = listOf(
        Subscription(id = DEFAULT_SUB_IRANCELL, name = "ایرانسل", url = DEFAULT_SUB_IRANCELL_URL),
        Subscription(id = DEFAULT_SUB_MCI, name = "همراه اول", url = DEFAULT_SUB_MCI_URL),
        Subscription(id = DEFAULT_SUB_WIREGUARD, name = "وایرگارد", url = DEFAULT_SUB_WIREGUARD_URL),
        Subscription(id = DEFAULT_SUB_SPOOF, name = "اسپوف", url = DEFAULT_SUB_SPOOF_URL),
    )

    /**
     * Writes the shipped subscriptions when the store has none, and with [autoFetch] fetches their
     * servers once, quietly. Also updates existing default subscription names to Persian and adds
     * any new default subscriptions (such as Spoof) on app updates.
     */
    fun ensureDefaultSubscriptions(autoFetch: Boolean) {
        val existing = loadSubscriptions().toMutableList()
        val defaults = defaultSubscriptions()

        if (existing.isEmpty()) {
            _subscriptions.value = defaults
            persistSubscriptions()
            if (!autoFetch) return
            CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                DEFAULT_SUB_IDS.forEach { id ->
                    runCatching { updateSubscription(id) }
                }
            }
            return
        }

        var modified = false
        val toFetch = mutableListOf<String>()

        defaults.forEach { def ->
            val idx = existing.indexOfFirst { it.id == def.id }
            if (idx >= 0) {
                val current = existing[idx]
                if (current.name != def.name) {
                    existing[idx] = current.copy(name = def.name)
                    modified = true
                }
                if (_profiles.value.none { it.subscriptionId == def.id }) {
                    toFetch.add(def.id)
                }
            } else {
                existing.add(def)
                modified = true
                toFetch.add(def.id)
            }
        }

        if (modified) {
            _subscriptions.value = existing
            persistSubscriptions()
        }

        if (autoFetch && toFetch.isNotEmpty()) {
            CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                toFetch.distinct().forEach { id ->
                    runCatching { updateSubscription(id) }
                }
            }
        }
    }

    companion object {
        private const val PREFS = "nrg_configs"
        private const val KEY_PROFILES = "profiles"
        private const val KEY_SUBS = "subscriptions"
        private const val KEY_ACTIVE = "active_id"
        private const val KEY_AUTO_PICK = "auto_pick:"
        private const val KEY_CONSUMED_KEYS = "consumed_nrg_keys"
        private const val KEY_NRG_DEVICE = "nrg_device_id"
        private const val KEY_NRG_RELAY_URL = "nrg_relay_url"
        private const val KEY_NRG_RELAY_TOKEN = "nrg_relay_token"
        const val KEY_SNISPOOF_IP = "snispoof_default_ip"
        const val KEY_SNISPOOF_PORT = "snispoof_default_port"
        const val KEY_SNISPOOF_SNI = "snispoof_default_sni"
        const val KEY_SNISPOOF_METHOD = "snispoof_default_method"

        const val DEFAULT_SUB_IRANCELL = "default-narcic-irancell"
        const val DEFAULT_SUB_MCI = "default-mci"
        const val DEFAULT_SUB_WIREGUARD = "default-wireguard"
        const val DEFAULT_SUB_SPOOF = "default-spoof"
        const val DEFAULT_SUB_IRANCELL_URL =
            "https://raw.githubusercontent.com/validbv7996/Narcic_APK/refs/heads/main/vlessirancell.txt"
        const val DEFAULT_SUB_MCI_URL =
            "https://raw.githubusercontent.com/validbv7996/Narcic_APK/refs/heads/main/vlessmci.txt"
        const val DEFAULT_SUB_WIREGUARD_URL =
            "https://raw.githubusercontent.com/validbv7996/Narcic_APK/refs/heads/main/ngwirguard.txt"
        const val DEFAULT_SUB_SPOOF_URL =
            "https://raw.githubusercontent.com/validbv7996/Narcic_APK/refs/heads/main/Narcic-Spooof.txt"

        val DEFAULT_SUB_IDS = setOf(
            DEFAULT_SUB_IRANCELL,
            DEFAULT_SUB_MCI,
            DEFAULT_SUB_WIREGUARD,
            DEFAULT_SUB_SPOOF,
        )

        fun isDefaultSubscription(id: String?): Boolean {
            if (id.isNullOrBlank()) return false
            return id in DEFAULT_SUB_IDS
        }

        val DEFAULT_UA = "v2rayNG/${AppInfo.versionName}"

        val ALT_UA = "NarcicGeteway/${AppInfo.versionName}"

        const val SING_BOX_UA = "SFA/1.14.0"

        const val V2RAYNG_UA_PREFIX = "v2rayNG/"

        private val URL_IN_TEXT = Regex("""https?://[^\s<>"'«»]+""", RegexOption.IGNORE_CASE)

        private val URL_TRAILING_PUNCTUATION = charArrayOf('.', ',', ';', ':', '!', '?', ')', ']', '}', '،', '؛')
    }
}

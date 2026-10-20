package dev.valid7996.narcicgeteway.data.tools.patt

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

data class EchResult(
    val jsonConfig: JsonObject,
    val formattedJson: String,
    val remarks: String,
    val protocol: String,
    val address: String,
    val port: Int,
    val network: String,
    val sni: String,
    val echConfigList: String,
    val fingerprint: String,
    val alpn: List<String>,
    val proxyCount: Int = 1,
)

object EchEngine {
    const val DEFAULT_ECH_CONFIG_LIST = "cloudflare-ech.com+udp://1.1.1.1"
    const val DEFAULT_DNS_SERVER = "https://cloudflare-dns.com/dns-query"
    const val DEFAULT_DOMESTIC_DNS = "223.5.5.5"

    private val nonProxyProtocols = setOf("freedom", "blackhole", "dns")
    private val nonProxyTags = setOf("direct", "block", "dns-out")

    private val jsonStrict = Json { ignoreUnknownKeys = true; isLenient = true }
    private val jsonPretty = Json { prettyPrint = true; prettyPrintIndent = "  " }

    fun isProxyOutbound(ob: JsonObject): Boolean {
        val proto = ob["protocol"]?.jsonPrimitive?.contentOrNull?.lowercase().orEmpty()
        val tag = ob["tag"]?.jsonPrimitive?.contentOrNull?.lowercase().orEmpty()
        if (proto.isEmpty() || nonProxyProtocols.contains(proto) || nonProxyTags.contains(tag)) {
            return false
        }
        return true
    }

    fun extractServerName(outbound: JsonObject): String {
        val streamSettings = outbound["streamSettings"]?.let { runCatching { it.jsonObject }.getOrNull() }
        val tlsSettings = streamSettings?.get("tlsSettings")?.let { runCatching { it.jsonObject }.getOrNull() }
        val realitySettings = streamSettings?.get("realitySettings")?.let { runCatching { it.jsonObject }.getOrNull() }

        val existingSni = tlsSettings?.get("serverName")?.jsonPrimitive?.contentOrNull
            ?: realitySettings?.get("serverName")?.jsonPrimitive?.contentOrNull
        if (!existingSni.isNullOrEmpty()) return existingSni

        val wsSettings = streamSettings?.get("wsSettings")?.let { runCatching { it.jsonObject }.getOrNull() }
        val wsHost = wsSettings?.get("headers")?.let { runCatching { it.jsonObject }.getOrNull() }?.let {
            it["Host"]?.jsonPrimitive?.contentOrNull ?: it["host"]?.jsonPrimitive?.contentOrNull
        } ?: wsSettings?.get("host")?.jsonPrimitive?.contentOrNull
        if (!wsHost.isNullOrEmpty()) return wsHost

        val httpSettings = streamSettings?.get("httpSettings")?.let { runCatching { it.jsonObject }.getOrNull() }
        val httpHost = httpSettings?.get("host")?.let { elem ->
            if (elem is JsonArray && elem.isNotEmpty()) elem[0].jsonPrimitive.contentOrNull
            else elem.jsonPrimitive.contentOrNull
        }
        if (!httpHost.isNullOrEmpty()) return httpHost

        val xhttpSettings = streamSettings?.get("xhttpSettings")?.let { runCatching { it.jsonObject }.getOrNull() }
        val xhttpHost = xhttpSettings?.get("host")?.jsonPrimitive?.contentOrNull
        if (!xhttpHost.isNullOrEmpty()) return xhttpHost

        val settings = outbound["settings"]?.let { runCatching { it.jsonObject }.getOrNull() }
        val vnext = settings?.get("vnext")?.let { runCatching { it.jsonArray }.getOrNull() }
        if (!vnext.isNullOrEmpty() && vnext[0] is JsonObject) {
            val addr = vnext[0].jsonObject["address"]?.jsonPrimitive?.contentOrNull
            if (!addr.isNullOrEmpty()) return addr
        }

        val servers = settings?.get("servers")?.let { runCatching { it.jsonArray }.getOrNull() }
        if (!servers.isNullOrEmpty() && servers[0] is JsonObject) {
            val addr = servers[0].jsonObject["address"]?.jsonPrimitive?.contentOrNull
            if (!addr.isNullOrEmpty()) return addr
        }

        return ""
    }

    fun applyEchToOutbound(
        outbound: JsonObject,
        echConfigList: String,
        fingerprint: String? = null,
        alpn: List<String>? = null,
        allowInsecure: Boolean = false,
    ): JsonObject {
        val serverName = extractServerName(outbound)
        val streamSettings = outbound["streamSettings"]?.let { runCatching { it.jsonObject }.getOrNull() } ?: buildJsonObject {}
        val existingTls = streamSettings["tlsSettings"]?.let { runCatching { it.jsonObject }.getOrNull() } ?: buildJsonObject {}

        val effectiveSni = existingTls["serverName"]?.jsonPrimitive?.contentOrNull?.ifEmpty { null } ?: serverName
        val effectiveAlpn = if (!alpn.isNullOrEmpty()) {
            alpn
        } else {
            val fromExisting = existingTls["alpn"]?.let { runCatching { it.jsonArray }.getOrNull() }
            if (fromExisting != null && fromExisting.isNotEmpty()) {
                fromExisting.mapNotNull { it.jsonPrimitive.contentOrNull }
            } else {
                listOf("http/1.1")
            }
        }

        val effectiveFp = if (!fingerprint.isNullOrEmpty()) {
            fingerprint
        } else {
            existingTls["fingerprint"]?.jsonPrimitive?.contentOrNull?.ifEmpty { "chrome" } ?: "chrome"
        }

        val effectiveEch = echConfigList.trim().ifEmpty { DEFAULT_ECH_CONFIG_LIST }

        val newTlsSettings = buildJsonObject {
            existingTls.forEach { (k, v) -> put(k, v) }
            put("allowInsecure", allowInsecure)
            putJsonArray("alpn") { effectiveAlpn.forEach { add(JsonPrimitive(it)) } }
            put("echConfigList", effectiveEch)
            put("fingerprint", effectiveFp)
            if (effectiveSni.isNotEmpty()) {
                put("serverName", effectiveSni)
            }
        }

        val newStreamSettings = buildJsonObject {
            streamSettings.forEach { (k, v) -> put(k, v) }
            put("security", "tls")
            put("tlsSettings", newTlsSettings)
        }

        return buildJsonObject {
            outbound.forEach { (k, v) ->
                if (k != "streamSettings" && k != "mux") put(k, v)
            }
            put("streamSettings", newStreamSettings)
            if (!outbound.containsKey("mux")) {
                putJsonObject("mux") {
                    put("concurrency", -1)
                    put("enabled", false)
                }
            } else {
                put("mux", outbound["mux"]!!)
            }
        }
    }

    private fun buildEchDns(
        dnsServer: String,
        domesticDns: String,
        existingDns: JsonObject? = null,
    ): JsonObject {
        val hostsMap = mutableMapOf<String, JsonElement>()
        CloudflareFixEngine.DEFAULT_DNS_HOSTS.forEach { (k, v) -> hostsMap[k] = v }
        existingDns?.get("hosts")?.let { runCatching { it.jsonObject }.getOrNull() }?.forEach { (k, v) ->
            hostsMap[k] = v
        }

        return buildJsonObject {
            putJsonObject("hosts") {
                hostsMap.forEach { (k, v) -> put(k, v) }
            }
            putJsonArray("servers") {
                add(buildJsonObject {
                    put("address", "fakedns")
                    putJsonArray("domains") {
                        add(JsonPrimitive("geosite:cn"))
                        add(JsonPrimitive("domain:ir"))
                        add(JsonPrimitive("geosite:category-ir"))
                    }
                })
                add(JsonPrimitive(dnsServer))
                add(buildJsonObject {
                    put("address", domesticDns)
                    putJsonArray("domains") {
                        add(JsonPrimitive("domain:ir"))
                        add(JsonPrimitive("geosite:category-ir"))
                    }
                    put("finalQuery", true)
                    put("skipFallback", true)
                    put("tag", "domestic-dns_0_0")
                })
            }
            put("tag", "dns-module")
        }
    }

    private fun enrichInboundsWithFakeDns(
        inbounds: List<JsonElement>,
        socksPort: Int = 10808,
        httpPort: Int? = null,
    ): List<JsonObject> {
        val enriched = mutableListOf<JsonObject>()
        val seenPorts = mutableSetOf<Int>()

        for (item in inbounds) {
            val ib = runCatching { item.jsonObject }.getOrNull() ?: continue
            val port = ib["port"]?.jsonPrimitive?.intOrNull

            val sniffing = ib["sniffing"]?.let { runCatching { it.jsonObject }.getOrNull() } ?: buildJsonObject {}
            val destOverrideList = mutableListOf("http", "tls", "quic")
            val existingDest = sniffing["destOverride"]?.let { runCatching { it.jsonArray }.getOrNull() }
            if (existingDest != null) {
                destOverrideList.clear()
                existingDest.forEach { d ->
                    d.jsonPrimitive.contentOrNull?.let { destOverrideList.add(it) }
                }
            }
            if (!destOverrideList.contains("fakedns")) {
                destOverrideList.add("fakedns")
            }

            val newSniffing = buildJsonObject {
                sniffing.forEach { (k, v) -> put(k, v) }
                put("enabled", true)
                put("routeOnly", false)
                putJsonArray("destOverride") {
                    destOverrideList.forEach { add(JsonPrimitive(it)) }
                }
            }

            val newIb = buildJsonObject {
                ib.forEach { (k, v) -> if (k != "sniffing") put(k, v) }
                put("sniffing", newSniffing)
            }

            if (port != null) seenPorts.add(port)
            enriched.add(newIb)
        }

        if (enriched.isEmpty()) {
            enriched.add(buildJsonObject {
                put("listen", "127.0.0.1")
                put("port", socksPort)
                put("protocol", "socks")
                putJsonObject("settings") {
                    put("auth", "noauth")
                    put("udp", true)
                }
                putJsonObject("sniffing") {
                    putJsonArray("destOverride") {
                        add(JsonPrimitive("http"))
                        add(JsonPrimitive("tls"))
                        add(JsonPrimitive("quic"))
                        add(JsonPrimitive("fakedns"))
                    }
                    put("enabled", true)
                    put("routeOnly", false)
                }
                put("tag", "socks")
            })
            seenPorts.add(socksPort)
        }

        if (httpPort != null && !seenPorts.contains(httpPort)) {
            enriched.add(buildJsonObject {
                put("listen", "127.0.0.1")
                put("port", httpPort)
                put("protocol", "http")
                putJsonObject("settings") {
                    put("userLevel", 8)
                }
                putJsonObject("sniffing") {
                    putJsonArray("destOverride") {
                        add(JsonPrimitive("http"))
                        add(JsonPrimitive("tls"))
                        add(JsonPrimitive("quic"))
                        add(JsonPrimitive("fakedns"))
                    }
                    put("enabled", true)
                    put("routeOnly", false)
                }
                put("tag", "http")
            })
        }

        return enriched
    }

    fun addEchToInput(
        rawInput: String,
        echConfigList: String = DEFAULT_ECH_CONFIG_LIST,
        socksPort: Int = 10808,
        httpPort: Int? = null,
        dnsServer: String = DEFAULT_DNS_SERVER,
        domesticDns: String = DEFAULT_DOMESTIC_DNS,
        remarkSuffix: String = "-ech",
        fingerprint: String = "chrome",
        alpn: List<String> = listOf("http/1.1"),
        allowInsecure: Boolean = false,
    ): EchResult {
        val trimmed = rawInput.trim()
        if (trimmed.isEmpty()) {
            throw IllegalArgumentException("Input configuration cannot be empty.")
        }

        // Check if input is a JSON object
        if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
            val root = runCatching { jsonStrict.parseToJsonElement(trimmed) }.getOrNull()
            if (root is JsonObject) {
                if (root.containsKey("outbounds") && root["outbounds"] is JsonArray) {
                    return transformFullJsonConfig(
                        root,
                        echConfigList = echConfigList,
                        socksPort = socksPort,
                        httpPort = httpPort,
                        dnsServer = dnsServer,
                        domesticDns = domesticDns,
                        remarkSuffix = remarkSuffix,
                        fingerprint = fingerprint,
                        alpn = alpn,
                        allowInsecure = allowInsecure,
                    )
                } else if (root.containsKey("protocol")) {
                    val applied = applyEchToOutbound(
                        root,
                        echConfigList = echConfigList,
                        fingerprint = fingerprint,
                        alpn = alpn,
                        allowInsecure = allowInsecure,
                    )
                    val appliedWithTag = buildJsonObject {
                        applied.forEach { (k, v) -> put(k, v) }
                        put("tag", "proxy")
                    }
                    return wrapOutboundIntoEchProfile(
                        appliedWithTag,
                        remarks = root["tag"]?.jsonPrimitive?.contentOrNull ?: "proxy",
                        echConfigList = echConfigList,
                        socksPort = socksPort,
                        httpPort = httpPort,
                        dnsServer = dnsServer,
                        domesticDns = domesticDns,
                        remarkSuffix = remarkSuffix,
                        fingerprint = fingerprint,
                        alpn = alpn,
                    )
                }
            }
        }

        // Otherwise parse share link into outbound
        val parsed = parseProxyLink(trimmed)
        val applied = applyEchToOutbound(
            parsed.outbound,
            echConfigList = echConfigList,
            fingerprint = fingerprint,
            alpn = alpn,
            allowInsecure = allowInsecure,
        )

        return wrapOutboundIntoEchProfile(
            applied,
            remarks = parsed.remarks.ifEmpty { "proxy" },
            echConfigList = echConfigList,
            socksPort = socksPort,
            httpPort = httpPort,
            dnsServer = dnsServer,
            domesticDns = domesticDns,
            remarkSuffix = remarkSuffix,
            fingerprint = fingerprint,
            alpn = alpn,
        )
    }

    private fun transformFullJsonConfig(
        rootJson: JsonObject,
        echConfigList: String,
        socksPort: Int,
        httpPort: Int?,
        dnsServer: String,
        domesticDns: String,
        remarkSuffix: String,
        fingerprint: String,
        alpn: List<String>,
        allowInsecure: Boolean,
    ): EchResult {
        val rawOutbounds = rootJson["outbounds"]?.let { runCatching { it.jsonArray }.getOrNull() } ?: JsonArray(emptyList())
        val updatedOutbounds = mutableListOf<JsonObject>()
        val proxyOutbounds = mutableListOf<JsonObject>()

        for (item in rawOutbounds) {
            val obMap = runCatching { item.jsonObject }.getOrNull() ?: continue
            if (isProxyOutbound(obMap)) {
                val withEch = applyEchToOutbound(
                    obMap,
                    echConfigList = echConfigList,
                    fingerprint = fingerprint,
                    alpn = alpn,
                    allowInsecure = allowInsecure,
                )
                updatedOutbounds.add(withEch)
                proxyOutbounds.add(withEch)
            } else {
                updatedOutbounds.add(obMap)
            }
        }

        if (proxyOutbounds.isEmpty()) {
            throw IllegalArgumentException("No proxy outbounds found in the configuration.")
        }

        val hasDirect = updatedOutbounds.any { it["tag"]?.jsonPrimitive?.contentOrNull == "direct" }
        val hasBlock = updatedOutbounds.any { it["tag"]?.jsonPrimitive?.contentOrNull == "block" }
        val hasDnsOut = updatedOutbounds.any { it["tag"]?.jsonPrimitive?.contentOrNull == "dns-out" }

        if (!hasDirect) {
            updatedOutbounds.add(buildJsonObject {
                put("protocol", "freedom")
                put("tag", "direct")
            })
        }
        if (!hasBlock) {
            updatedOutbounds.add(buildJsonObject {
                put("protocol", "blackhole")
                put("tag", "block")
            })
        }
        if (!hasDnsOut) {
            updatedOutbounds.add(buildJsonObject {
                put("protocol", "dns")
                putJsonObject("settings") { put("userLevel", 12) }
                put("tag", "dns-out")
            })
        }

        val routing = rootJson["routing"]?.let { runCatching { it.jsonObject }.getOrNull() } ?: buildJsonObject {}
        val balancers = routing["balancers"]?.let { runCatching { it.jsonArray }.getOrNull() }
        val isLoadBalancer = !balancers.isNullOrEmpty()

        val isChain = proxyOutbounds.size > 1 && (
            proxyOutbounds.any { ob ->
                val stream = ob["streamSettings"]?.let { runCatching { it.jsonObject }.getOrNull() }
                val sockopt = stream?.get("sockopt")?.let { runCatching { it.jsonObject }.getOrNull() }
                sockopt?.containsKey("dialerProxy") == true
            } || proxyOutbounds.first()["tag"]?.jsonPrimitive?.contentOrNull?.startsWith("hop") == true
        )

        val dnsSection = buildEchDns(
            dnsServer = dnsServer,
            domesticDns = domesticDns,
            existingDns = rootJson["dns"]?.let { runCatching { it.jsonObject }.getOrNull() },
        )

        val inboundsSection = enrichInboundsWithFakeDns(
            rootJson["inbounds"]?.let { runCatching { it.jsonArray }.getOrNull() }?.toList() ?: emptyList(),
            socksPort = socksPort,
            httpPort = httpPort,
        )

        val domainStrategy = routing["domainStrategy"]?.jsonPrimitive?.contentOrNull ?: "AsIs"
        val existingRules = routing["rules"]?.let { runCatching { it.jsonArray }.getOrNull() }?.toList() ?: emptyList()

        val updatedRules = mutableListOf<JsonObject>()

        // 1. port 53 -> dns-out
        updatedRules.add(buildJsonObject {
            putJsonArray("inboundTag") {
                add(JsonPrimitive("socks"))
                if (httpPort != null) add(JsonPrimitive("http"))
            }
            put("outboundTag", "dns-out")
            put("port", "53")
            put("type", "field")
        })

        // 2. domestic-dns_0_0 -> direct
        updatedRules.add(buildJsonObject {
            putJsonArray("inboundTag") { add(JsonPrimitive("domestic-dns_0_0")) }
            put("outboundTag", "direct")
            put("type", "field")
        })

        // 3. dns-module routing
        if (isLoadBalancer) {
            val balancerTag = balancers!!.first().jsonObject["tag"]?.jsonPrimitive?.contentOrNull ?: "balancer-main"
            updatedRules.add(buildJsonObject {
                put("balancerTag", balancerTag)
                putJsonArray("inboundTag") { add(JsonPrimitive("dns-module")) }
                put("type", "field")
            })
            updatedRules.add(buildJsonObject {
                put("balancerTag", balancerTag)
                put("network", "tcp,udp")
                put("type", "field")
            })
        } else if (isChain) {
            val finalHopTag = proxyOutbounds.last()["tag"]?.jsonPrimitive?.contentOrNull ?: "hop${proxyOutbounds.size - 1}"
            updatedRules.add(buildJsonObject {
                putJsonArray("inboundTag") { add(JsonPrimitive("dns-module")) }
                put("outboundTag", finalHopTag)
                put("type", "field")
            })
            updatedRules.add(buildJsonObject {
                put("outboundTag", finalHopTag)
                put("port", "0-65535")
                put("type", "field")
            })
        } else {
            val primaryProxyTag = proxyOutbounds.first()["tag"]?.jsonPrimitive?.contentOrNull ?: "proxy"
            updatedRules.add(buildJsonObject {
                putJsonArray("inboundTag") { add(JsonPrimitive("dns-module")) }
                put("outboundTag", primaryProxyTag)
                put("type", "field")
            })
        }

        // 4. Direct rules for IR
        updatedRules.add(buildJsonObject {
            putJsonArray("domain") {
                add(JsonPrimitive("domain:ir"))
                add(JsonPrimitive("geosite:category-ir"))
            }
            put("outboundTag", "direct")
            put("type", "field")
        })
        updatedRules.add(buildJsonObject {
            putJsonArray("ip") { add(JsonPrimitive("geoip:ir")) }
            put("outboundTag", "direct")
            put("type", "field")
        })

        // 5. Retain non-conflicting existing rules
        for (r in existingRules) {
            val rObj = runCatching { r.jsonObject }.getOrNull() ?: continue
            val inTag = rObj["inboundTag"]?.let { runCatching { it.jsonArray }.getOrNull() }
            val port = rObj["port"]?.jsonPrimitive?.contentOrNull
            val tags = inTag?.mapNotNull { it.jsonPrimitive.contentOrNull } ?: emptyList()
            val isDnsPort53 = (tags.contains("socks") || tags.contains("dns-module")) && port == "53"
            val isDomestic = tags.contains("domestic-dns_0_0")
            if (isDnsPort53 || isDomestic) {
                continue
            }
            updatedRules.add(rObj)
        }

        val rawRemarks = rootJson["remarks"]?.jsonPrimitive?.contentOrNull ?: "ech-profile"
        val finalRemarks = if (rawRemarks.endsWith(remarkSuffix)) rawRemarks else "$rawRemarks$remarkSuffix"

        val policy = rootJson["policy"]?.let { runCatching { it.jsonObject }.getOrNull() } ?: buildJsonObject {}
        val levels = policy["levels"]?.let { runCatching { it.jsonObject }.getOrNull() } ?: buildJsonObject {}
        val newLevels = buildJsonObject {
            levels.forEach { (k, v) -> put(k, v) }
            if (!levels.containsKey("0")) {
                putJsonObject("0") {
                    put("downlinkOnly", 0)
                    put("uplinkOnly", 0)
                }
            }
            if (!levels.containsKey("12")) {
                putJsonObject("12") {
                    put("connIdle", 12)
                    put("downlinkOnly", 0)
                    put("uplinkOnly", 0)
                }
            }
        }

        val resultJson = buildJsonObject {
            rootJson.forEach { (k, v) ->
                if (k != "dns" && k != "fakedns" && k != "inbounds" && k != "outbounds" && k != "policy" && k != "remarks" && k != "routing") {
                    put(k, v)
                }
            }
            put("dns", dnsSection)
            putJsonArray("fakedns") {
                add(buildJsonObject {
                    put("ipPool", "198.18.0.0/15")
                    put("poolSize", 10000)
                })
            }
            putJsonArray("inbounds") { inboundsSection.forEach { add(it) } }
            if (!rootJson.containsKey("log")) {
                putJsonObject("log") { put("loglevel", "warning") }
            } else {
                put("log", rootJson["log"]!!)
            }
            putJsonArray("outbounds") { updatedOutbounds.forEach { add(it) } }
            putJsonObject("policy") {
                policy.forEach { (k, v) -> if (k != "levels") put(k, v) }
                put("levels", newLevels)
            }
            put("remarks", finalRemarks)
            putJsonObject("routing") {
                routing.forEach { (k, v) -> if (k != "domainStrategy" && k != "rules") put(k, v) }
                put("domainStrategy", domainStrategy)
                putJsonArray("rules") { updatedRules.forEach { add(it) } }
            }
        }

        val formatted = jsonPretty.encodeToString(JsonObject.serializer(), resultJson)

        val displayProtocol: String
        val displayAddress: String
        if (isLoadBalancer) {
            displayProtocol = "Load Balancer (${proxyOutbounds.size} nodes)"
            displayAddress = "Least-Ping Balancer"
        } else if (isChain) {
            displayProtocol = "Chain (${proxyOutbounds.size} hops)"
            displayAddress = "${proxyOutbounds.first()["tag"]?.jsonPrimitive?.contentOrNull} → ${proxyOutbounds.last()["tag"]?.jsonPrimitive?.contentOrNull}"
        } else {
            displayProtocol = proxyOutbounds.first()["protocol"]?.jsonPrimitive?.contentOrNull?.uppercase() ?: "PROXY"
            displayAddress = extractServerName(proxyOutbounds.first())
        }

        return EchResult(
            jsonConfig = resultJson,
            formattedJson = formatted,
            remarks = finalRemarks,
            protocol = displayProtocol,
            address = displayAddress,
            port = socksPort,
            network = proxyOutbounds.first()["streamSettings"]?.let { runCatching { it.jsonObject }.getOrNull() }
                ?.get("network")?.jsonPrimitive?.contentOrNull?.uppercase() ?: "TCP",
            sni = extractServerName(proxyOutbounds.first()),
            echConfigList = echConfigList,
            fingerprint = fingerprint,
            alpn = alpn,
            proxyCount = proxyOutbounds.size,
        )
    }

    private fun wrapOutboundIntoEchProfile(
        appliedOutbound: JsonObject,
        remarks: String,
        echConfigList: String,
        socksPort: Int,
        httpPort: Int?,
        dnsServer: String,
        domesticDns: String,
        remarkSuffix: String,
        fingerprint: String,
        alpn: List<String>,
    ): EchResult {
        val rawRemarks = remarks.ifEmpty { "proxy" }
        val finalRemarks = if (rawRemarks.endsWith(remarkSuffix)) rawRemarks else "$rawRemarks$remarkSuffix"
        val effectiveSni = extractServerName(appliedOutbound)

        val inbounds = buildJsonArray {
            add(buildJsonObject {
                put("listen", "127.0.0.1")
                put("port", socksPort)
                put("protocol", "socks")
                putJsonObject("settings") {
                    put("auth", "noauth")
                    put("udp", true)
                }
                putJsonObject("sniffing") {
                    putJsonArray("destOverride") {
                        add(JsonPrimitive("http"))
                        add(JsonPrimitive("tls"))
                        add(JsonPrimitive("quic"))
                        add(JsonPrimitive("fakedns"))
                    }
                    put("enabled", true)
                    put("routeOnly", false)
                }
                put("tag", "socks")
            })
            if (httpPort != null) {
                add(buildJsonObject {
                    put("listen", "127.0.0.1")
                    put("port", httpPort)
                    put("protocol", "http")
                    putJsonObject("settings") {
                        put("userLevel", 8)
                    }
                    putJsonObject("sniffing") {
                        putJsonArray("destOverride") {
                            add(JsonPrimitive("http"))
                            add(JsonPrimitive("tls"))
                            add(JsonPrimitive("quic"))
                            add(JsonPrimitive("fakedns"))
                        }
                        put("enabled", true)
                        put("routeOnly", false)
                    }
                    put("tag", "http")
                })
            }
        }

        val outbounds = buildJsonArray {
            add(appliedOutbound)
            add(buildJsonObject {
                put("protocol", "freedom")
                put("tag", "direct")
            })
            add(buildJsonObject {
                put("protocol", "blackhole")
                put("tag", "block")
            })
            add(buildJsonObject {
                put("protocol", "dns")
                putJsonObject("settings") { put("userLevel", 12) }
                put("tag", "dns-out")
            })
        }

        val dnsSection = buildEchDns(dnsServer, domesticDns)

        val jsonMap = buildJsonObject {
            put("dns", dnsSection)
            putJsonArray("fakedns") {
                add(buildJsonObject {
                    put("ipPool", "198.18.0.0/15")
                    put("poolSize", 10000)
                })
            }
            put("inbounds", inbounds)
            putJsonObject("log") { put("loglevel", "warning") }
            put("outbounds", outbounds)
            putJsonObject("policy") {
                putJsonObject("levels") {
                    putJsonObject("0") {
                        put("downlinkOnly", 0)
                        put("uplinkOnly", 0)
                    }
                    putJsonObject("12") {
                        put("connIdle", 12)
                        put("downlinkOnly", 0)
                        put("uplinkOnly", 0)
                    }
                }
            }
            put("remarks", finalRemarks)
            putJsonObject("routing") {
                put("domainStrategy", "AsIs")
                putJsonArray("rules") {
                    add(buildJsonObject {
                        putJsonArray("inboundTag") {
                            add(JsonPrimitive("socks"))
                            if (httpPort != null) add(JsonPrimitive("http"))
                        }
                        put("outboundTag", "dns-out")
                        put("port", "53")
                        put("type", "field")
                    })
                    add(buildJsonObject {
                        putJsonArray("inboundTag") { add(JsonPrimitive("domestic-dns_0_0")) }
                        put("outboundTag", "direct")
                        put("type", "field")
                    })
                    add(buildJsonObject {
                        putJsonArray("inboundTag") { add(JsonPrimitive("dns-module")) }
                        put("outboundTag", "proxy")
                        put("type", "field")
                    })
                    add(buildJsonObject {
                        putJsonArray("domain") {
                            add(JsonPrimitive("domain:ir"))
                            add(JsonPrimitive("geosite:category-ir"))
                        }
                        put("outboundTag", "direct")
                        put("type", "field")
                    })
                    add(buildJsonObject {
                        putJsonArray("ip") { add(JsonPrimitive("geoip:ir")) }
                        put("outboundTag", "direct")
                        put("type", "field")
                    })
                }
            }
        }

        val formatted = jsonPretty.encodeToString(JsonObject.serializer(), jsonMap)

        return EchResult(
            jsonConfig = jsonMap,
            formattedJson = formatted,
            remarks = finalRemarks,
            protocol = appliedOutbound["protocol"]?.jsonPrimitive?.contentOrNull?.uppercase() ?: "PROXY",
            address = effectiveSni,
            port = socksPort,
            network = appliedOutbound["streamSettings"]?.let { runCatching { it.jsonObject }.getOrNull() }
                ?.get("network")?.jsonPrimitive?.contentOrNull?.uppercase() ?: "TCP",
            sni = effectiveSni,
            echConfigList = echConfigList,
            fingerprint = fingerprint,
            alpn = alpn,
            proxyCount = 1,
        )
    }

    private data class ParsedNode(
        val remarks: String,
        val outbound: JsonObject,
    )

    private fun parseProxyLink(link: String): ParsedNode {
        val scheme = link.substringBefore("://", "").lowercase()
        val rest = link.substringAfter("://", "")

        val fragmentParts = rest.split('#', limit = 2)
        val rawRemarks = if (fragmentParts.size > 1) urlDecode(fragmentParts[1]) else "proxy"
        val beforeFragment = fragmentParts[0]

        when (scheme) {
            "vless" -> {
                val queryParts = beforeFragment.split('?', limit = 2)
                val queryString = if (queryParts.size > 1) queryParts[1] else ""
                val mainPart = queryParts[0]

                val atParts = mainPart.split('@', limit = 2)
                val uuid = atParts.getOrNull(0).orEmpty()
                val hostPort = atParts.getOrNull(1).orEmpty()
                val colon = hostPort.lastIndexOf(':')
                val address = if (colon != -1) hostPort.substring(0, colon).removePrefix("[").removeSuffix("]") else hostPort
                val port = if (colon != -1) hostPort.substring(colon + 1).toIntOrNull() ?: 443 else 443

                val q = parseQuery(queryString)
                val network = (q["type"] ?: q["network"] ?: "tcp").lowercase().let { if (it == "splithttp") "xhttp" else it }
                val security = (q["security"] ?: "none").lowercase()
                val sni = q["sni"] ?: q["peer"] ?: q["host"] ?: address
                val path = q["path"]?.let { urlDecode(it) }.orEmpty()
                val host = q["host"].orEmpty()
                val flow = q["flow"].orEmpty()
                val enc = q["encryption"]?.ifEmpty { "none" } ?: "none"

                val outbound = buildJsonObject {
                    put("protocol", "vless")
                    putJsonObject("settings") {
                        putJsonArray("vnext") {
                            add(buildJsonObject {
                                put("address", address)
                                put("port", port)
                                putJsonArray("users") {
                                    add(buildJsonObject {
                                        put("id", uuid)
                                        put("encryption", enc)
                                        if (flow.isNotEmpty()) put("flow", flow)
                                    })
                                }
                            })
                        }
                    }
                    putJsonObject("streamSettings") {
                        put("network", network)
                        put("security", security)
                        if (network == "ws") {
                            putJsonObject("wsSettings") {
                                if (path.isNotEmpty()) put("path", path)
                                if (host.isNotEmpty()) putJsonObject("headers") { put("Host", host) }
                            }
                        } else if (network == "xhttp") {
                            putJsonObject("xhttpSettings") {
                                if (path.isNotEmpty()) put("path", path)
                                if (host.isNotEmpty()) put("host", host)
                            }
                        }
                        if (sni.isNotEmpty()) {
                            putJsonObject("tlsSettings") { put("serverName", sni) }
                        }
                    }
                    put("tag", "proxy")
                }
                return ParsedNode(rawRemarks, outbound)
            }
            "vmess" -> {
                // If it's standard base64 JSON payload
                val jsonPayload = decodeBase64Safe(beforeFragment)
                if (jsonPayload.startsWith("{")) {
                    val root = runCatching { jsonStrict.parseToJsonElement(jsonPayload).jsonObject }.getOrNull()
                    if (root != null) {
                        val address = root["add"]?.jsonPrimitive?.contentOrNull.orEmpty()
                        val port = root["port"]?.jsonPrimitive?.intOrNull ?: 443
                        val uuid = root["id"]?.jsonPrimitive?.contentOrNull.orEmpty()
                        val alterId = root["aid"]?.jsonPrimitive?.intOrNull ?: 0
                        val net = (root["net"]?.jsonPrimitive?.contentOrNull ?: "tcp").lowercase().let { if (it == "splithttp") "xhttp" else it }
                        val path = root["path"]?.jsonPrimitive?.contentOrNull.orEmpty()
                        val host = root["host"]?.jsonPrimitive?.contentOrNull.orEmpty()
                        val tls = (root["tls"]?.jsonPrimitive?.contentOrNull ?: "none").lowercase()
                        val sni = root["sni"]?.jsonPrimitive?.contentOrNull?.ifEmpty { host.ifEmpty { address } } ?: host.ifEmpty { address }
                        val ps = root["ps"]?.jsonPrimitive?.contentOrNull ?: rawRemarks

                        val outbound = buildJsonObject {
                            put("protocol", "vmess")
                            putJsonObject("settings") {
                                putJsonArray("vnext") {
                                    add(buildJsonObject {
                                        put("address", address)
                                        put("port", port)
                                        putJsonArray("users") {
                                            add(buildJsonObject {
                                                put("id", uuid)
                                                put("alterId", alterId)
                                                put("security", "auto")
                                            })
                                        }
                                    })
                                }
                            }
                            putJsonObject("streamSettings") {
                                put("network", net)
                                put("security", if (tls == "tls") "tls" else "none")
                                if (net == "ws") {
                                    putJsonObject("wsSettings") {
                                        if (path.isNotEmpty()) put("path", path)
                                        if (host.isNotEmpty()) putJsonObject("headers") { put("Host", host) }
                                    }
                                }
                                if (sni.isNotEmpty()) {
                                    putJsonObject("tlsSettings") { put("serverName", sni) }
                                }
                            }
                            put("tag", "proxy")
                        }
                        return ParsedNode(ps, outbound)
                    }
                }
                throw IllegalArgumentException("Unsupported VMess format")
            }
            "trojan" -> {
                val queryParts = beforeFragment.split('?', limit = 2)
                val queryString = if (queryParts.size > 1) queryParts[1] else ""
                val mainPart = queryParts[0]

                val atParts = mainPart.split('@', limit = 2)
                val password = urlDecode(atParts.getOrNull(0).orEmpty())
                val hostPort = atParts.getOrNull(1).orEmpty()
                val colon = hostPort.lastIndexOf(':')
                val address = if (colon != -1) hostPort.substring(0, colon).removePrefix("[").removeSuffix("]") else hostPort
                val port = if (colon != -1) hostPort.substring(colon + 1).toIntOrNull() ?: 443 else 443

                val q = parseQuery(queryString)
                val network = (q["type"] ?: q["network"] ?: "tcp").lowercase().let { if (it == "splithttp") "xhttp" else it }
                val sni = q["sni"] ?: q["peer"] ?: address

                val outbound = buildJsonObject {
                    put("protocol", "trojan")
                    putJsonObject("settings") {
                        putJsonArray("servers") {
                            add(buildJsonObject {
                                put("address", address)
                                put("password", password)
                                put("port", port)
                            })
                        }
                    }
                    putJsonObject("streamSettings") {
                        put("network", network)
                        put("security", "tls")
                        putJsonObject("tlsSettings") { put("serverName", sni) }
                    }
                    put("tag", "proxy")
                }
                return ParsedNode(rawRemarks, outbound)
            }
            "ss" -> {
                // ss://method:pass@host:port or ss://base64@host:port or ss://base64
                var decodedMain = beforeFragment
                if (!decodedMain.contains("@")) {
                    decodedMain = decodeBase64Safe(decodedMain)
                }
                val atParts = decodedMain.split('@', limit = 2)
                val userPass = if (atParts.size == 2) decodeBase64Safe(atParts[0]).ifEmpty { atParts[0] } else ""
                val hostPort = if (atParts.size == 2) atParts[1] else decodedMain

                val method = if (userPass.contains(":")) userPass.substringBefore(":") else "aes-256-gcm"
                val password = if (userPass.contains(":")) userPass.substringAfter(":") else ""

                val colon = hostPort.lastIndexOf(':')
                val address = if (colon != -1) hostPort.substring(0, colon).removePrefix("[").removeSuffix("]") else hostPort
                val port = if (colon != -1) hostPort.substring(colon + 1).toIntOrNull() ?: 8388 else 8388

                val outbound = buildJsonObject {
                    put("protocol", "shadowsocks")
                    putJsonObject("settings") {
                        putJsonArray("servers") {
                            add(buildJsonObject {
                                put("address", address)
                                put("method", method)
                                put("password", password)
                                put("port", port)
                            })
                        }
                    }
                    putJsonObject("streamSettings") {
                        put("network", "tcp")
                        put("security", "tls")
                        putJsonObject("tlsSettings") { put("serverName", address) }
                    }
                    put("tag", "proxy")
                }
                return ParsedNode(rawRemarks, outbound)
            }
            else -> throw IllegalArgumentException("Unsupported proxy protocol: $scheme")
        }
    }

    private fun parseQuery(q: String): Map<String, String> {
        val map = mutableMapOf<String, String>()
        if (q.isBlank()) return map
        for (pair in q.split('&')) {
            if (pair.isBlank()) continue
            val parts = pair.split('=', limit = 2)
            val k = urlDecode(parts[0])
            val v = if (parts.size > 1) urlDecode(parts[1]) else ""
            map[k] = v
        }
        return map
    }

    @OptIn(ExperimentalEncodingApi::class)
    private fun decodeBase64Safe(input: String): String {
        return runCatching {
            var padded = input.replace('-', '+').replace('_', '/')
            val pad = (4 - padded.length % 4) % 4
            padded += "=".repeat(pad)
            Base64.decode(padded).decodeToString()
        }.getOrDefault("")
    }

    private fun urlDecode(s: String): String {
        return runCatching {
            var res = s.replace("+", " ")
            val bytes = mutableListOf<Byte>()
            var i = 0
            while (i < res.length) {
                val c = res[i]
                if (c == '%' && i + 2 < res.length) {
                    val hex = res.substring(i + 1, i + 3)
                    val b = hex.toIntOrNull(16)
                    if (b != null) {
                        bytes.add(b.toByte())
                        i += 3
                        continue
                    }
                }
                bytes.addAll(c.toString().encodeToByteArray().toList())
                i++
            }
            bytes.toByteArray().decodeToString()
        }.getOrDefault(s)
    }
}

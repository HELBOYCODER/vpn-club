package dev.valid7996.narcicgeteway.data.tools.patt

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

data class CloudflareFixResult(
    val jsonConfig: JsonObject,
    val formattedJson: String,
    val remarks: String,
    val address: String,
    val port: Int,
    val network: String,
    val sni: String,
    val fingerprint: String,
    val alpn: List<String>,
)

val kAvailableFingerprints = listOf(
    "unsafe",
    "chrome",
    "firefox",
    "android",
    "randomized",
    "random",
    "edge",
    "safari",
    "360",
    "qq",
    "ios",
)

object CloudflareFixEngine {
    const val DEFAULT_CIPHER_SUITES =
        "TLS_AES_256_GCM_SHA384:TLS_CHACHA20_POLY1305_SHA256:TLS_AES_128_GCM_SHA256:TLS_ECDHE_ECDSA_WITH_AES_256_GCM_SHA384:TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384:TLS_ECDHE_ECDSA_WITH_AES_128_GCM_SHA256:TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256:TLS_ECDHE_ECDSA_WITH_CHACHA20_POLY1305_SHA256:TLS_ECDHE_RSA_WITH_CHACHA20_POLY1305_SHA256:TLS_ECDHE_ECDSA_WITH_AES_256_CBC_SHA:TLS_ECDHE_RSA_WITH_AES_256_CBC_SHA:TLS_ECDHE_ECDSA_WITH_AES_128_CBC_SHA256:TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA256"

    const val DEFAULT_DNS_SERVER = "https://cloudflare-dns.com/dns-query"

    private val jsonPretty = Json {
        prettyPrint = true
        prettyPrintIndent = "  "
    }

    val DEFAULT_DNS_HOSTS: Map<String, JsonElement> = mapOf(
        "domain:googleapis.cn" to JsonPrimitive("googleapis.com"),
        "dns.alidns.com" to buildJsonArray {
            add(JsonPrimitive("223.5.5.5"))
            add(JsonPrimitive("223.6.6.6"))
            add(JsonPrimitive("2400:3200::1"))
            add(JsonPrimitive("2400:3200:baba::1"))
        },
        "dns.sse.cisco.com" to buildJsonArray {
            add(JsonPrimitive("208.67.220.220"))
            add(JsonPrimitive("208.67.222.222"))
            add(JsonPrimitive("2620:119:35::35"))
            add(JsonPrimitive("2620:119:53::53"))
        },
        "dns.umbrella.com" to buildJsonArray {
            add(JsonPrimitive("208.67.220.220"))
            add(JsonPrimitive("208.67.222.222"))
            add(JsonPrimitive("2620:119:35::35"))
            add(JsonPrimitive("2620:119:53::53"))
        },
        "one.one.one.one" to buildJsonArray {
            add(JsonPrimitive("1.1.1.1"))
            add(JsonPrimitive("1.0.0.1"))
            add(JsonPrimitive("2606:4700:4700::1111"))
            add(JsonPrimitive("2606:4700:4700::1001"))
        },
        "1dot1dot1dot1.cloudflare-dns.com" to buildJsonArray {
            add(JsonPrimitive("1.1.1.1"))
            add(JsonPrimitive("1.0.0.1"))
            add(JsonPrimitive("2606:4700:4700::1111"))
            add(JsonPrimitive("2606:4700:4700::1001"))
        },
        "dns.cloudflare.com" to buildJsonArray {
            add(JsonPrimitive("162.159.61.8"))
            add(JsonPrimitive("172.64.41.8"))
            add(JsonPrimitive("2a06:98c1:52::8"))
            add(JsonPrimitive("2803:f800:53::8"))
        },
        "cloudflare-dns.com" to buildJsonArray {
            add(JsonPrimitive("104.16.248.249"))
            add(JsonPrimitive("104.16.249.249"))
            add(JsonPrimitive("2606:4700::6810:f8f9"))
            add(JsonPrimitive("2606:4700::6810:f9f9"))
        },
        "engage.cloudflareclient.com" to buildJsonArray {
            add(JsonPrimitive("162.159.192.1"))
            add(JsonPrimitive("2606:4700:d0::a29f:c001"))
        },
        "doh.pub" to buildJsonArray {
            add(JsonPrimitive("1.12.12.12"))
            add(JsonPrimitive("120.53.53.53"))
        },
        "dot.pub" to buildJsonArray {
            add(JsonPrimitive("1.12.12.12"))
            add(JsonPrimitive("120.53.53.53"))
        },
        "dns.google" to buildJsonArray {
            add(JsonPrimitive("8.8.8.8"))
            add(JsonPrimitive("8.8.4.4"))
            add(JsonPrimitive("2001:4860:4860::8888"))
            add(JsonPrimitive("2001:4860:4860::8844"))
        },
        "dns.quad9.net" to buildJsonArray {
            add(JsonPrimitive("9.9.9.9"))
            add(JsonPrimitive("149.112.112.112"))
            add(JsonPrimitive("2620:fe::fe"))
            add(JsonPrimitive("2620:fe::9"))
        },
        "dns.sb" to buildJsonArray {
            add(JsonPrimitive("45.11.45.11"))
            add(JsonPrimitive("185.222.222.222"))
            add(JsonPrimitive("2a09::"))
            add(JsonPrimitive("2a11::"))
        },
        "common.dot.dns.yandex.net" to buildJsonArray {
            add(JsonPrimitive("77.88.8.8"))
            add(JsonPrimitive("77.88.8.1"))
            add(JsonPrimitive("2a02:6b8::feed:0ff"))
            add(JsonPrimitive("2a02:6b8:0:1::feed:0ff"))
        },
    )

    fun transformVlessUrl(
        shareLink: String,
        socksPort: Int = 10808,
        httpPort: Int = 10809,
        dnsServer: String = DEFAULT_DNS_SERVER,
        remarkSuffix: String = "-custom",
        fingerprint: String = "unsafe",
        alpn: List<String> = listOf("http/1.1"),
        cipherSuites: String = DEFAULT_CIPHER_SUITES,
        enableFinalmask: Boolean = true,
        frag1Packets: String = "tlshello",
        frag1Lengths: List<String> = listOf("0", "104", "1"),
        frag1Delays: List<String> = listOf("0"),
        frag1MaxSplit: String = "0",
        frag2Packets: String = "1-1",
        frag2Lengths: List<String> = listOf("114", "1"),
        frag2Delays: List<String> = listOf("1"),
        frag2MaxSplit: String = "11",
    ): CloudflareFixResult {
        val trimmed = shareLink.trim()
        if (trimmed.isEmpty()) {
            throw IllegalArgumentException("VLESS configuration link cannot be empty.")
        }
        if (!trimmed.startsWith("vless://", ignoreCase = true)) {
            throw IllegalArgumentException("Invalid protocol. Only VLESS links starting with \"vless://\" are supported.")
        }

        val uriStr = trimmed.substring(8)
        val fragmentParts = uriStr.split('#', limit = 2)
        val rawRemarks = if (fragmentParts.size > 1) {
            urlDecode(fragmentParts[1]).ifBlank { "VLESS Node" }
        } else {
            "VLESS Node"
        }
        val beforeFragment = fragmentParts[0]

        val queryParts = beforeFragment.split('?', limit = 2)
        val queryString = if (queryParts.size > 1) queryParts[1] else ""
        val mainPart = queryParts[0]

        val atParts = mainPart.split('@', limit = 2)
        if (atParts.size != 2) {
            throw IllegalArgumentException("Invalid VLESS link format: missing \"@\" separating UUID and server address.")
        }

        val uuid = atParts[0]
        val hostPortStr = atParts[1]

        val colonIndex = hostPortStr.lastIndexOf(':')
        if (colonIndex == -1) {
            throw IllegalArgumentException("Invalid address/port in VLESS link: $hostPortStr")
        }
        val address = hostPortStr.substring(0, colonIndex).removePrefix("[").removeSuffix("]")
        val portStr = hostPortStr.substring(colonIndex + 1)
        val port = portStr.toIntOrNull()
        if (port == null || port <= 0 || port > 65535) {
            throw IllegalArgumentException("Invalid port number: $portStr")
        }

        val queryParams = parseQueryString(queryString)

        val security = (queryParams["security"] ?: queryParams["tls"] ?: "none").lowercase()
        val network = (queryParams["type"] ?: queryParams["network"] ?: "tcp").lowercase()

        // STRICT COMPATIBILITY CHECK: Only VLESS+TLS+WS and VLESS+TLS+xHTTP are supported!
        val isSupportedSecurity = security == "tls"
        val isSupportedNetwork = network == "ws" || network == "xhttp" || network == "splithttp"

        if (!isSupportedSecurity || !isSupportedNetwork) {
            throw IllegalArgumentException("Only VLESS+TLS+WS and VLESS+TLS+xHTTP configurations are supported.")
        }

        val normalizedNetwork = if (network == "xhttp" || network == "splithttp") "xhttp" else "ws"
        val sni = queryParams["sni"] ?: queryParams["peer"] ?: queryParams["host"] ?: address
        val path = queryParams["path"]?.let { urlDecode(it) }.orEmpty()
        val host = queryParams["host"].orEmpty()
        val flow = queryParams["flow"].orEmpty()
        val encryption = queryParams["encryption"]?.ifEmpty { "none" } ?: "none"
        val mode = queryParams["mode"] ?: "auto"

        val finalRemarks = if (rawRemarks.endsWith(remarkSuffix)) rawRemarks else "$rawRemarks$remarkSuffix"

        val streamSettings = buildJsonObject {
            if (enableFinalmask) {
                putJsonObject("finalmask") {
                    putJsonArray("tcp") {
                        add(buildJsonObject {
                            put("type", "fragment")
                            putJsonObject("settings") {
                                put("packets", frag1Packets)
                                putJsonArray("lengths") { frag1Lengths.forEach { add(JsonPrimitive(it)) } }
                                putJsonArray("delays") { frag1Delays.forEach { add(JsonPrimitive(it)) } }
                                put("maxSplit", frag1MaxSplit)
                            }
                        })
                        add(buildJsonObject {
                            put("type", "fragment")
                            putJsonObject("settings") {
                                put("packets", frag2Packets)
                                putJsonArray("lengths") { frag2Lengths.forEach { add(JsonPrimitive(it)) } }
                                putJsonArray("delays") { frag2Delays.forEach { add(JsonPrimitive(it)) } }
                                put("maxSplit", frag2MaxSplit)
                            }
                        })
                    }
                }
            }

            put("network", normalizedNetwork)
            put("security", "tls")

            putJsonObject("sockopt") {
                put("domainStrategy", "UseIP")
                putJsonObject("happyEyeballs") {
                    put("interleave", 2)
                    put("maxConcurrentTry", 4)
                    put("prioritizeIPv6", false)
                    put("tryDelayMs", 250)
                }
            }

            putJsonObject("tlsSettings") {
                put("allowInsecure", false)
                putJsonArray("alpn") {
                    val actualAlpn = alpn.ifEmpty { listOf("http/1.1") }
                    actualAlpn.forEach { add(JsonPrimitive(it)) }
                }
                put("cipherSuites", cipherSuites.ifEmpty { DEFAULT_CIPHER_SUITES })
                put("fingerprint", fingerprint)
                put("serverName", sni)
            }

            if (normalizedNetwork == "ws") {
                putJsonObject("wsSettings") {
                    put("host", host.ifEmpty { sni })
                    put("path", path)
                }
            } else if (normalizedNetwork == "xhttp") {
                putJsonObject("xhttpSettings") {
                    put("host", host.ifEmpty { sni })
                    put("path", path)
                    put("mode", mode)
                }
            }
        }

        val jsonConfig = buildJsonObject {
            putJsonObject("dns") {
                putJsonObject("hosts") {
                    DEFAULT_DNS_HOSTS.forEach { (k, v) -> put(k, v) }
                }
                putJsonArray("servers") {
                    add(JsonPrimitive(dnsServer))
                }
                put("tag", "dns-module")
            }

            putJsonArray("inbounds") {
                add(buildJsonObject {
                    put("listen", "127.0.0.1")
                    put("port", socksPort)
                    put("protocol", "socks")
                    putJsonObject("settings") {
                        put("auth", "noauth")
                        put("udp", true)
                        put("userLevel", 8)
                    }
                    putJsonObject("sniffing") {
                        putJsonArray("destOverride") {
                            add(JsonPrimitive("http"))
                            add(JsonPrimitive("tls"))
                            add(JsonPrimitive("quic"))
                        }
                        put("enabled", true)
                        put("routeOnly", false)
                    }
                    put("tag", "socks")
                })
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
                        }
                        put("enabled", true)
                        put("routeOnly", false)
                    }
                    put("tag", "http")
                })
            }

            putJsonObject("log") {
                put("loglevel", "warning")
            }

            putJsonArray("outbounds") {
                add(buildJsonObject {
                    putJsonObject("mux") {
                        put("concurrency", -1)
                        put("enabled", false)
                    }
                    put("protocol", "vless")
                    putJsonObject("settings") {
                        put("address", address)
                        put("encryption", encryption)
                        put("flow", flow)
                        put("id", uuid)
                        put("level", 8)
                        put("port", port)
                    }
                    put("streamSettings", streamSettings)
                    put("tag", "proxy")
                })
                add(buildJsonObject {
                    put("protocol", "freedom")
                    putJsonObject("streamSettings") {
                        put("network", "tcp")
                        putJsonObject("sockopt") {
                            put("domainStrategy", "UseIP")
                        }
                    }
                    put("tag", "direct")
                })
                add(buildJsonObject {
                    put("protocol", "blackhole")
                    putJsonObject("settings") {}
                    put("tag", "block")
                })
            }

            put("remarks", finalRemarks)

            putJsonObject("routing") {
                put("domainStrategy", "AsIs")
                putJsonArray("rules") {
                    add(buildJsonObject {
                        putJsonArray("inboundTag") {
                            add(JsonPrimitive("dns-module"))
                        }
                        put("outboundTag", "proxy")
                        put("type", "field")
                    })
                }
            }
        }

        val formatted = jsonPretty.encodeToString(JsonObject.serializer(), jsonConfig)

        return CloudflareFixResult(
            jsonConfig = jsonConfig,
            formattedJson = formatted,
            remarks = finalRemarks,
            address = address,
            port = port,
            network = normalizedNetwork.uppercase(),
            sni = sni,
            fingerprint = fingerprint,
            alpn = alpn.ifEmpty { listOf("http/1.1") },
        )
    }

    private fun parseQueryString(query: String): Map<String, String> {
        val result = mutableMapOf<String, String>()
        if (query.isBlank()) return result
        val pairs = query.split('&')
        for (pair in pairs) {
            if (pair.isBlank()) continue
            val parts = pair.split('=', limit = 2)
            val key = urlDecode(parts[0])
            val value = if (parts.size > 1) urlDecode(parts[1]) else ""
            result[key] = value
        }
        return result
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

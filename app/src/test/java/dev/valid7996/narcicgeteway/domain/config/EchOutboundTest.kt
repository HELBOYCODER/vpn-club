package dev.valid7996.narcicgeteway.domain.config

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EchOutboundTest {

    private val jsonParser = Json { ignoreUnknownKeys = true }

    private fun json(text: String): JsonObject = jsonParser.parseToJsonElement(text).jsonObject

    private fun outboundsOf(result: EchOutbound.Result): JsonArray =
        json((result as EchOutbound.Result.Done).content)["outbounds"]!!.jsonArray

    private fun tagsOf(outbounds: JsonArray): List<String> =
        outbounds.map { it.jsonObject["tag"]!!.jsonPrimitive.content }

    private fun sampleConfig(vararg outbounds: String): String {
        val outboundsJson = outbounds.joinToString(",")
        return """
        {
          "log": { "loglevel": "warning" },
          "inbounds": [],
          "outbounds": [
            $outboundsJson,
            { "tag": "direct", "protocol": "freedom" }
          ],
          "routing": { "rules": [] }
        }
        """.trimIndent()
    }

    private fun tlsOutbound(tag: String, echOutbound: String?, echConfigList: String? = "cloudflare-ech.com+https://1.1.1.1/dns-query"): String {
        val echField = if (echOutbound != null) """, "echOutbound": ${Json.encodeToString(kotlinx.serialization.serializer(), echOutbound)}""" else ""
        val echListField = if (echConfigList != null) """, "echConfigList": "$echConfigList"""" else ""
        return """
        {
          "tag": "$tag",
          "protocol": "vless",
          "streamSettings": {
            "security": "tls",
            "tlsSettings": {
              "serverName": "example.com"
              $echListField
              $echField
            }
          }
        }
        """.trimIndent()
    }

    @Test
    fun validate_acceptsAnEmptyOrValidEchOutbound() {
        val echList = "cloudflare-ech.com+https://1.1.1.1/dns-query"
        assertNull(EchOutbound.validate(null, echList))
        assertNull(EchOutbound.validate("  ", echList))
        assertNull(EchOutbound.validate("""{"tag": "ech-out", "protocol": "freedom"}""", echList))
        assertNull(EchOutbound.validate("""{"tag": "ech-proxy"}""", echList))
        assertNull(EchOutbound.validate("""{"tag": "Proxy"}""", echList))
    }

    @Test
    fun validate_rejectsWhatIsNotAJsonObject() {
        val echList = "cloudflare-ech.com+https://1.1.1.1/dns-query"
        for (invalid in listOf("freedom", """[{"tag": "ech-out"}]""", """{"tag": "ech-out"""", """{"tag": "ech-out"} x""")) {
            assertEquals(EchOutbound.Error.INVALID_JSON, EchOutbound.validate(invalid, echList))
        }
    }

    @Test
    fun validate_needsAnEchConfigList() {
        assertEquals(
            EchOutbound.Error.NEEDS_ECH_CONFIG_LIST,
            EchOutbound.validate("""{"tag": "ech-out"}""", ""),
        )
        assertEquals(
            EchOutbound.Error.NEEDS_ECH_CONFIG_LIST,
            EchOutbound.validate("""{"tag": "ech-out"}""", null),
        )
    }

    @Test
    fun validate_rejectsAMissingOrTakenTag() {
        val echList = "cloudflare-ech.com+https://1.1.1.1/dns-query"
        val invalidJsons = listOf(
            """{"protocol": "freedom"}""",
            """{"tag": ""}""",
            """{"tag": " "}""",
            """{"tag": 1}""",
            """{"tag": "direct"}""",
            """{"tag": "block"}""",
            """{"tag": "proxy"}""",
            """{"tag": "proxy-ech"}""",
        )
        for (j in invalidJsons) {
            assertEquals(EchOutbound.Error.INVALID_TAG, EchOutbound.validate(j, echList))
        }
    }

    @Test
    fun serialize_sharesAnEchOutboundAndNumbersADifferentOneUnderTheSameTag() {
        val shared = """{"tag": "ech", "protocol": "freedom"}"""
        val different = """{"tag": "ech", "protocol": "blackhole"}"""

        val cfg = sampleConfig(
            tlsOutbound("proxy-1", shared),
            tlsOutbound("proxy-2", different),
            tlsOutbound("proxy-3", shared),
            tlsOutbound("proxy-4", null),
        )

        val result = EchOutbound.serialize(cfg)
        assertTrue(result is EchOutbound.Result.Done)
        val appended = outboundsOf(result)

        assertEquals(
            listOf("proxy-1", "proxy-2", "proxy-3", "proxy-4", "direct", "ech", "ech-2"),
            tagsOf(appended),
        )
        assertEquals("blackhole", appended.last().jsonObject["protocol"]!!.jsonPrimitive.content)

        val parsed = json((result as EchOutbound.Result.Done).content)
        val outbounds = parsed["outbounds"]!!.jsonArray

        val p1 = outbounds[0].jsonObject["streamSettings"]!!.jsonObject["tlsSettings"]!!.jsonObject["echSockopt"]!!.jsonObject["dialerProxy"]!!.jsonPrimitive.content
        val p2 = outbounds[1].jsonObject["streamSettings"]!!.jsonObject["tlsSettings"]!!.jsonObject["echSockopt"]!!.jsonObject["dialerProxy"]!!.jsonPrimitive.content
        val p3 = outbounds[2].jsonObject["streamSettings"]!!.jsonObject["tlsSettings"]!!.jsonObject["echSockopt"]!!.jsonObject["dialerProxy"]!!.jsonPrimitive.content

        assertEquals("ech", p1)
        assertEquals("ech-2", p2)
        assertEquals("ech", p3)
    }

    @Test
    fun serialize_numbersClearOfTheTagsOfOtherOutbounds() {
        val cfg = sampleConfig(
            tlsOutbound("proxy-1", """{"tag": "ech", "protocol": "freedom"}"""),
            tlsOutbound("proxy-2", """{"tag": "ech", "protocol": "blackhole"}"""),
            """{"tag": "ech-2", "protocol": "freedom"}""",
        )

        val result = EchOutbound.serialize(cfg)
        assertTrue(result is EchOutbound.Result.Done)
        val appended = outboundsOf(result)

        assertEquals(
            listOf("proxy-1", "proxy-2", "ech-2", "direct", "ech", "ech-3"),
            tagsOf(appended),
        )
    }

    @Test
    fun serialize_tagConflictReturnsTagConflict() {
        val conflict = sampleConfig(
            tlsOutbound("proxy", """{"tag": "dns-out", "protocol": "freedom"}"""),
            """{"tag": "dns-out", "protocol": "dns"}""",
        )
        val result = EchOutbound.serialize(conflict)
        assertEquals(EchOutbound.Result.TagConflict("dns-out"), result)
    }

    @Test
    fun serialize_leavesAConfigurationWithoutEchOutboundsAsBefore() {
        val plain = sampleConfig(
            tlsOutbound("proxy-1", null),
        )
        val result = EchOutbound.serialize(plain)
        assertTrue(result is EchOutbound.Result.Done)
        assertEquals(plain, (result as EchOutbound.Result.Done).content)
    }

    @Test
    fun linkSerializationAndParsingRoundTrip() {
        val server = ServerConfig(
            protocol = Protocol.VLESS,
            remark = "ech-test",
            address = "1.2.3.4",
            port = 443,
            userId = "11111111-2222-3333-4444-555555555555",
            tls = SecurityConfig(
                security = "tls",
                sni = "example.com",
                echConfigList = "cloudflare-ech.com+https://1.1.1.1/dns-query",
                echOutbound = """{"tag": "ech-out", "protocol": "freedom"}""",
            ),
        )

        val uri = ShareLink.build(server)
        assertTrue("URI should carry echOutbound: $uri", uri.contains("echOutbound="))

        val reparsed = ConfigParser.parse(uri)
        assertEquals("""{"tag": "ech-out", "protocol": "freedom"}""", reparsed.tls.echOutbound)
        assertEquals("cloudflare-ech.com+https://1.1.1.1/dns-query", reparsed.tls.echConfigList)
    }

    @Test
    fun xrayJsonBuilder_emitsEchOutboundAndEchSockopt() {
        val server = ServerConfig(
            protocol = Protocol.VLESS,
            remark = "test",
            address = "1.2.3.4",
            port = 443,
            userId = "11111111-2222-3333-4444-555555555555",
            tls = SecurityConfig(
                security = "tls",
                sni = "example.com",
                echConfigList = "cloudflare-ech.com+https://1.1.1.1/dns-query",
                echOutbound = """{"tag": "ech-out", "protocol": "freedom"}""",
            ),
        )

        val config = XrayJsonBuilder.build(server)
        val root = json(config)
        val outbounds = root["outbounds"]!!.jsonArray

        val proxy = outbounds.first { it.jsonObject["tag"]!!.jsonPrimitive.content == "proxy" }
        val tlsSettings = proxy.jsonObject["streamSettings"]!!.jsonObject["tlsSettings"]!!.jsonObject
        assertEquals("ech-out", tlsSettings["echSockopt"]!!.jsonObject["dialerProxy"]!!.jsonPrimitive.content)
        assertFalse("temporary echOutbound key should be stripped", tlsSettings.containsKey("echOutbound"))

        val echOutbound = outbounds.first { it.jsonObject["tag"]!!.jsonPrimitive.content == "ech-out" }
        assertEquals("freedom", echOutbound.jsonObject["protocol"]!!.jsonPrimitive.content)
    }

    @Test
    fun xrayJsonBuilder_useXrayTunEmitsTunInboundAndDnsRule() {
        val server = ServerConfig(
            protocol = Protocol.VLESS,
            remark = "test",
            address = "1.2.3.4",
            port = 443,
            userId = "11111111-2222-3333-4444-555555555555",
            tls = SecurityConfig(
                security = "tls",
                sni = "example.com",
            ),
        )

        val opts = XrayJsonBuilder.BuildOptions(useXrayTun = true, vpnMtu = 1400)
        val config = XrayJsonBuilder.build(server, options = opts)
        val root = json(config)

        val inbounds = root["inbounds"]!!.jsonArray
        val tun = inbounds.firstOrNull { it.jsonObject["tag"]!!.jsonPrimitive.content == "tun" }
        assertTrue("tun inbound should be present", tun != null)
        assertEquals("tun", tun!!.jsonObject["protocol"]!!.jsonPrimitive.content)
        assertEquals("1400", tun.jsonObject["settings"]!!.jsonObject["MTU"]!!.jsonPrimitive.content)

        val rules = root["routing"]!!.jsonObject["rules"]!!.jsonArray
        val tunDnsRule = rules.firstOrNull { r ->
            r.jsonObject["outboundTag"]?.jsonPrimitive?.content == "dns-out" &&
                r.jsonObject["inboundTag"]?.jsonArray?.any { it.jsonPrimitive.content == "tun" } == true
        }
        assertTrue("tun port 53 DNS rule should be present", tunDnsRule != null)
    }
}

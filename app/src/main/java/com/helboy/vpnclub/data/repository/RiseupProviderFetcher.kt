package com.helboy.vpnclub.data.repository

import android.util.Log
import com.helboy.vpnclub.data.model.AuthMode
import com.helboy.vpnclub.data.model.VpnProvider
import com.helboy.vpnclub.data.model.VpnServer
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Fetches free servers from the Riseup VPN public API (riseup.net).
 *
 * Riseup is a donation-funded, no-account VPN built for censorship resistance.
 * Everything is fetched from public endpoints:
 *  - https://api.black.riseup.net/3/config/eip-service.json  → gateway list (hosts, IPs, ports)
 *  - https://black.riseup.net/ca.crt                          → VPN CA certificate (static)
 *  - https://api.black.riseup.net/3/cert                     → client cert + key (valid 90 days, renewed on refresh)
 *
 * The client credential response is a concatenated PEM: the RSA private key first, then the certificate.
 */
class RiseupProviderFetcher(private val client: OkHttpClient) {

    suspend fun fetchServers(): List<VpnServer> {
        val gateways = try {
            fetchGatewayJson()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to fetch Riseup gateway list: ${e.message}")
            return emptyList()
        }
        if (gateways.isEmpty()) return emptyList()

        // Fetch the CA cert and client credentials once for all gateways
        val caCert = fetchText(GATEWAYS_API_CA_URL)
        val credentials = fetchText(CLIENT_CREDENTIALS_URL)

        if (caCert.isNullOrBlank() || !caCert.contains("BEGIN CERTIFICATE")) {
            Log.w(TAG, "Riseup CA cert unavailable, skipping provider")
            return emptyList()
        }
        if (credentials.isNullOrBlank() || !credentials.contains("BEGIN RSA PRIVATE KEY")) {
            Log.w(TAG, "Riseup client credentials unavailable, skipping provider")
            return emptyList()
        }

        val (clientKey, clientCert) = splitCredentials(credentials)

        return gateways.mapIndexedNotNull { idx, gw ->
            try {
                VpnServer(
                    hostName = gw.getString("host"),
                    ip = gw.getString("ip_address"),
                    score = 1000 - idx,           // rank by our own ordering
                    ping = 0,
                    speed = 0L,
                    countryLong = "Riseup — " + gw.optString("location", "Unknown"),
                    countryShort = riseupCountryCode(gw.optString("location", "")),
                    provider = VpnProvider.RISEUP,
                    authMode = AuthMode.CLIENT_CERT,
                    caCertPem = caCert,
                    clientCertPem = clientCert,
                    clientKeyPem = clientKey
                )
            } catch (e: Exception) {
                null
            }
        }
    }

    private fun riseupCountryCode(location: String): String {
        // Riseup gateways are labeled by city; map the known ones to ISO codes for flags.
        return when (location.lowercase()) {
            "amsterdam", "ams" -> "NL"
            "atlanta", "atl" -> "US"
            "frankfurt", "fra" -> "DE"
            "madrid", "mad" -> "ES"
            "marseille", "mars" -> "FR"
            "montreal", "mtl", "toronto", "yyz" -> "CA"
            "new york", "nyc", "seattle", "sea", "sfo", "san francisco" -> "US"
            "paris", "par" -> "FR"
            "seattle" -> "US"
            "stockholm", "sto" -> "SE"
            "tokyo", "tyo" -> "JP"
            "warsaw", "waw" -> "PL"
            else -> "UN"
        }
    }

    private fun fetchGatewayJson(): List<JSONObject> {
        val body = fetchText(GATEWAYS_API_URL) ?: return emptyList()
        val json = JSONObject(body)
        val arr = json.optJSONArray("gateways") ?: return emptyList()
        val out = mutableListOf<JSONObject>()
        for (i in 0 until arr.length()) {
            val gw = arr.optJSONObject(i) ?: continue
            // Only OpenVPN-capable gateways with a TCP transport
            val caps = gw.optJSONObject("capabilities") ?: continue
            val transports = caps.optJSONArray("transport") ?: continue
            for (t in 0 until transports.length()) {
                val tr = transports.optJSONObject(t) ?: continue
                if (tr.optString("type") == "openvpn") {
                    out.add(gw)
                    break
                }
            }
        }
        return out
    }

    /**
     * The /3/cert response is: private key PEM, then certificate PEM (both concatenated).
     */
    private fun splitCredentials(raw: String): Pair<String, String> {
        val separator = "-----BEGIN CERTIFICATE-----"
        val parts = raw.split(separator)
        val key = parts.getOrNull(0)?.trim().orEmpty()
        val cert = if (parts.size > 1) (separator + parts[1]).trim() else ""
        return Pair(key, cert)
    }

    private fun fetchText(url: String): String? {
        return try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "VPNClub/1.2 (Android)")
                .build()
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) response.body?.string() else null
            }
        } catch (e: Exception) {
            Log.w(TAG, "Fetch failed $url: ${e.message}")
            null
        }
    }

    companion object {
        private const val TAG = "RiseupProviderFetcher"
        private const val GATEWAYS_API_URL = "https://api.black.riseup.net/3/config/eip-service.json"
        private const val GATEWAYS_API_CA_URL = "https://black.riseup.net/ca.crt"
        private const val CLIENT_CREDENTIALS_URL = "https://api.black.riseup.net/3/cert"
    }
}

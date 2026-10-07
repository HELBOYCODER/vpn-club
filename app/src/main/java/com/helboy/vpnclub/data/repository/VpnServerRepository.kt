package com.helboy.vpnclub.data.repository

import android.content.Context
import android.util.Log
import com.helboy.vpnclub.data.model.VpnServer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

class VpnServerRepository(private val context: Context) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private val cacheFile = File(context.filesDir, "vpngate_cache.csv")

    private val mirrorUrls = listOf(
        "https://raw.githubusercontent.com/ezedin63/vpngate-mirror/main/data/vpngate.csv",
        "https://raw.githubusercontent.com/NetLops/vpngate-mirror/main/vpngate.csv",
        "http://www.vpngate.net/api/iphone/",
        "https://www.vpngate.net/api/iphone/"
    )

    suspend fun getServers(forceRefresh: Boolean = false): List<VpnServer> = withContext(Dispatchers.IO) {
        // If not force refreshing and cache exists and is fresh (< 2 hours), load from cache first
        if (!forceRefresh && cacheFile.exists() && cacheFile.length() > 1000) {
            val cachedList = parseCsvFile(cacheFile)
            if (cachedList.isNotEmpty()) {
                return@withContext cachedList
            }
        }

        // Try downloading from live mirrors
        val networkList = fetchFromMirrors()
        if (networkList.isNotEmpty()) {
            return@withContext networkList
        }

        // If network failed, check cache
        if (cacheFile.exists() && cacheFile.length() > 1000) {
            val cachedList = parseCsvFile(cacheFile)
            if (cachedList.isNotEmpty()) {
                return@withContext cachedList
            }
        }

        // Ultimate fallback: bundled starter asset
        loadFromAssets()
    }

    private fun fetchFromMirrors(): List<VpnServer> {
        for (url in mirrorUrls) {
            try {
                Log.d(TAG, "Fetching servers from mirror: $url")
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", "VPNClub/1.0 (Android)")
                    .build()

                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val body = response.body?.string()
                        if (!body.isNullOrBlank() && body.contains("*vpn_servers")) {
                            val servers = parseCsvContent(body)
                            if (servers.isNotEmpty()) {
                                // Save to local cache
                                try {
                                    cacheFile.writeText(body)
                                    Log.i(TAG, "Successfully cached ${servers.size} servers from $url")
                                } catch (e: Exception) {
                                    Log.w(TAG, "Failed to write cache file", e)
                                }
                                return servers
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed fetching from $url: ${e.message}")
            }
        }
        return emptyList()
    }

    private fun parseCsvFile(file: File): List<VpnServer> {
        return try {
            parseCsvContent(file.readText())
        } catch (e: Exception) {
            Log.e(TAG, "Error reading cache file", e)
            emptyList()
        }
    }

    private fun loadFromAssets(): List<VpnServer> {
        return try {
            val inputStream = context.assets.open("starter_servers.csv")
            val content = inputStream.bufferedReader().use { it.readText() }
            Log.i(TAG, "Loaded fallback servers from assets")
            parseCsvContent(content)
        } catch (e: Exception) {
            Log.e(TAG, "Error reading starter_servers.csv from assets", e)
            emptyList()
        }
    }

    private fun parseCsvContent(csv: String): List<VpnServer> {
        val servers = mutableListOf<VpnServer>()
        val reader = BufferedReader(InputStreamReader(csv.byteInputStream()))
        var line: String?

        while (reader.readLine().also { line = it } != null) {
            val row = line ?: continue
            val server = VpnServer.fromCsvLine(row)
            if (server != null) {
                servers.add(server)
            }
        }

        // Sort by ping ascending (lowest ping first), then by speed descending
        return servers.sortedWith(
            compareBy<VpnServer> { if (it.ping <= 0) 9999 else it.ping }
                .thenByDescending { it.speed }
        )
    }

    companion object {
        private const val TAG = "VpnServerRepository"
    }
}

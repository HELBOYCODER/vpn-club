package dev.valid7996.narcicgeteway.core

import android.content.Context
import android.util.Base64
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.security.GeneralSecurityException
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.util.Locale
import java.util.zip.InflaterInputStream
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * The server list the Psiphon client of the Aether core starts with, ported from PattNG. It is the signed, compressed
 * list the client downloads for itself, kept as psiphon_servers.dat: in the app's files folder (where it can be
 * replaced by hand or by an update) or, failing that, in the APK's assets (tools/fetch-psiphon-servers.sh stages it
 * at build time). The client takes it only as a plain file of server entries, so the list is checked against
 * Psiphon's signature and unpacked beside the identity files, once per version of the file. A list without
 * Psiphon's signature is not handed on: the client trusts what it is given.
 *
 * The core's flag is `--psiphon-server-entries <file>`; the profile writes the word `shipped` there, which
 * [AetherCommands.withShippedList] replaces with the file this object unpacks, or drops when there is none.
 */
object PsiphonServerList {

    const val LIST_NAME = "psiphon_servers.dat"
    private const val ENTRIES_FILE = "psiphon-servers.txt"
    private const val SOURCE_MARK = "psiphon-servers.source"

    /** Psiphon's public key for its server list, the one its client checks downloads with; the core carries the same key. */
    internal const val SIGNING_KEY =
        "MIICIDANBgkqhkiG9w0BAQEFAAOCAg0AMIICCAKCAgEAt7Ls+/39r+T6zNW7GiVpJfzq/xvL9SBH" +
            "5rIFnk0RXYEYavax3WS6HOD35eTAqn8AniOwiH+DOkvgSKF2caqk/y1dfq47Pdymtwzp9ikpB1C5" +
            "OfAysXzBiwVJlCdajBKvBZDerV1cMvRzCKvKwRmvDmHgphQQ7WfXIGbRbmmk6opMBh3roE42Kcot" +
            "LFtqp0RRwLtcBRNtCdsrVsjiI1Lqz/lH+T61sGjSjQ3CHMuZYSQJZo/KrvzgQXpkaCTdbObxHqb6" +
            "/+i1qaVOfEsvjoiyzTxJADvSytVtcTjijhPEV6XskJVHE1Zgl+7rATr/pDQkw6DPCNBS1+Y6fy7G" +
            "stZALQXwEDN/qhQI9kWkHijT8ns+i1vGg00Mk/6J75arLhqcodWsdeG/M/moWgqQAnlZAGVtJI1O" +
            "geF5fsPpXu4kctOfuZlGjVZXQNW34aOzm8r8S0eVZitPlbhcPiR4gT/aSMz/wd8lZlzZYsje/Jr8" +
            "u/YtlwjjreZrGRmG8KMOzukV3lLmMppXFMvl4bxv6YFEmIuTsOhbLTwFgh7KYNjodLj/LsqRVfwz" +
            "31PgWQFTEPICV7GCvgVlPRxnofqKSjgTWI4mxDhBpVcATvaoBl1L/6WLbFvBsoAUBItWwctO2xal" +
            "KxF5szhGm8lccoc5MZr8kfE0uxMgsxz4er68iCID+rsCAQM="

    private val json = Json { ignoreUnknownKeys = true }

    /** The core's line with what Psiphon reported as AvailableEgressRegions: the countries of every server it knows. */
    private val leavesFrom = Regex("""psiphon can leave from: ([A-Z]{2}(?: [A-Z]{2})*)\s*$""")

    @Volatile
    private var reported: Set<String> = emptySet()

    /**
     * The entries file for the list the app holds, unpacked into [workDir] when the list is new or has changed since;
     * the entries kept from before when the list cannot be used (the reason goes to [onProblem]); null when there is
     * no list at all.
     */
    fun entriesFile(context: Context, workDir: File, onProblem: (IOException) -> Unit = {}): File? {
        val copy = File(context.filesDir, LIST_NAME)
        val packed: ByteArray
        val stamp: String
        try {
            if (copy.isFile) {
                packed = copy.readBytes()
                stamp = "${copy.length()}:${copy.lastModified()}"
            } else {
                packed = context.assets.open(LIST_NAME).use { it.readBytes() }
                stamp = "asset:${packed.size}"
            }
        } catch (_: IOException) {
            return File(workDir, ENTRIES_FILE).takeIf { it.isFile }
        }
        workDir.mkdirs()
        val entries = File(workDir, ENTRIES_FILE)
        val mark = File(workDir, SOURCE_MARK)
        if (entries.isFile && mark.isFile && runCatching { mark.readText() }.getOrNull() == stamp) return entries
        return try {
            val text = unpack(packed, SIGNING_KEY)
            val fresh = File(workDir, "$ENTRIES_FILE.${System.nanoTime()}.new")
            fresh.writeText(text)
            if (!fresh.renameTo(entries)) {
                entries.delete()
                if (!fresh.renameTo(entries)) throw IOException("could not put ${entries.name} in place")
            }
            mark.writeText(stamp)
            entries
        } catch (e: IOException) {
            onProblem(e)
            entries.takeIf { it.isFile }
        }
    }

    /**
     * The exit countries the servers in [entries] offer, as ISO 3166-1 alpha-2 codes, sorted. An entry is one
     * hex-encoded line, "address port secret certificate {json}", with the region in the JSON; a line that does not
     * read is skipped.
     */
    fun regions(entries: String): Set<String> = entries.lineSequence().mapNotNullTo(sortedSetOf()) { regionOf(it.trim()) }

    private fun regionOf(line: String): String? {
        if (line.isEmpty() || line.length % 2 != 0) return null
        val bytes = ByteArray(line.length / 2)
        for (index in bytes.indices) {
            val high = Character.digit(line[2 * index], 16)
            val low = Character.digit(line[2 * index + 1], 16)
            if (high < 0 || low < 0) return null
            bytes[index] = ((high shl 4) or low).toByte()
        }
        val body = bytes.toString(Charsets.UTF_8).split(' ', limit = 5).getOrNull(4) ?: return null
        val region = try {
            (json.parseToJsonElement(body) as? JsonObject)?.get("region")?.let { it as? JsonPrimitive }?.contentOrNull
        } catch (_: RuntimeException) {
            null
        }
        return region?.trim()?.uppercase(Locale.ROOT)?.takeIf { it.length == 2 && it.all(Char::isLetter) }
    }

    /** The countries in [line] when it is the core's report of what Psiphon can leave from, else null. */
    fun regionsOf(line: String): List<String>? = leavesFrom.find(line)?.groupValues?.get(1)?.split(' ')

    /** Keeps [regions] as Psiphon's last report; the report is the whole set, so the last one is the truth. */
    fun remember(regions: Collection<String>) {
        reported = regions.toSortedSet()
    }

    /** Psiphon's last report of the countries it can leave from in this process; empty before the first. */
    fun remembered(): Set<String> = reported

    /**
     * The server entries inside [packed], a signed, compressed list as Psiphon writes it; throws when it is not one,
     * or is not signed with [signingKey]. Psiphon names the key by the digest of its text as written, not of the
     * key's bytes, and signs the entry text with it.
     */
    @Throws(IOException::class)
    internal fun unpack(packed: ByteArray, signingKey: String): String {
        val text = try {
            InflaterInputStream(ByteArrayInputStream(packed)).use { it.readBytes() }.toString(Charsets.UTF_8)
        } catch (e: IOException) {
            throw IOException("the list is not compressed the way Psiphon writes it", e)
        }
        val root = try {
            json.parseToJsonElement(text).jsonObject
        } catch (e: RuntimeException) {
            throw IOException("the list is not the package Psiphon writes", e)
        }
        fun field(name: String, missing: String): String =
            root[name]?.let { runCatching { it.jsonPrimitive.content }.getOrNull() } ?: throw IOException(missing)
        val data = field("data", "the list carries no entries")
        val keyDigest = field("signingPublicKeyDigest", "the list names no key")
        val signature = field("signature", "the list is not signed")
        try {
            val expected = MessageDigest.getInstance("SHA-256").digest(signingKey.toByteArray(Charsets.US_ASCII))
            if (!Base64.decode(keyDigest, Base64.DEFAULT).contentEquals(expected)) throw IOException("the list is signed with another key")
            val key = KeyFactory.getInstance("RSA").generatePublic(X509EncodedKeySpec(Base64.decode(signingKey, Base64.DEFAULT)))
            val verifier = Signature.getInstance("SHA256withRSA")
            verifier.initVerify(key)
            verifier.update(data.toByteArray(Charsets.UTF_8))
            if (!verifier.verify(Base64.decode(signature, Base64.DEFAULT))) throw IOException("the signature of the list does not check out")
        } catch (e: GeneralSecurityException) {
            throw IOException("the signature of the list cannot be checked", e)
        } catch (e: IllegalArgumentException) {
            throw IOException("the list is not encoded the way Psiphon writes it", e)
        }
        if (data.lineSequence().none { it.isNotBlank() }) throw IOException("the list is empty")
        return data
    }
}

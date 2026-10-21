package com.helboy.vpnclub.ui.proto.editors

import dev.valid7996.narcicgeteway.ui.platform.FilePick
import dev.valid7996.narcicgeteway.ui.platform.Platform
import dev.valid7996.narcicgeteway.ui.servers.CertFile
import dev.valid7996.narcicgeteway.ui.servers.CertImport
import dev.valid7996.narcicgeteway.ui.servers.CertKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/** Reading PEM text from a picked file, as pure functions. */
object PemFiles {
    /** The file as text when it looks like PEM (has a `-----BEGIN` line), else null. [needs] narrows it, e.g. "PRIVATE KEY". */
    fun readText(bytes: ByteArray, needs: String? = null): String? {
        if (bytes.isEmpty() || bytes.size > MAX_PEM_BYTES) return null
        val text = bytes.decodeToString()
        if (!text.contains("-----BEGIN")) return null
        if (needs != null && !text.contains(needs)) return null
        return text.trim()
    }

    @OptIn(ExperimentalEncodingApi::class)
    fun base64(bytes: ByteArray): String = Base64.Default.encode(bytes)

    private const val MAX_PEM_BYTES = 256 * 1024
}

/** How picking and checking one certificate file ended. */
sealed interface CertPick {
    /** The person closed the picker. */
    data object Cancelled : CertPick

    /** The file is not what the field needs; [message] says so in Persian. */
    data class Rejected(val message: String) : CertPick

    data class Picked(val file: FilePick, val info: CertFile?) : CertPick
}

object CertFiles {
    const val NOT_PKCS12 = "این فایل بستهٔ PKCS#12 نیست (p12. یا pfx.)."
    const val NOT_CERT = "این فایل گواهی نیست."
    const val NOT_PEM = "این فایل متن PEM معتبر نیست."
    const val PICK_FAILED = "خواندن فایل ممکن نشد؛ دوباره تلاش کنید."

    /**
     * Opens the file picker and inspects the file with the platform's [CertImport.inspect] (off the main thread).
     * [wanted] is the kind the field needs; null accepts anything.
     */
    suspend fun pick(platform: Platform, wanted: CertKind?, notMatching: String): CertPick {
        val file = try {
            platform.pickFileBytes()
        } catch (e: Exception) {
            return CertPick.Rejected(PICK_FAILED)
        } ?: return CertPick.Cancelled
        val info = withContext(Dispatchers.Default) {
            runCatching { CertImport.inspect?.invoke(file.bytes, file.name) }.getOrNull()
        }
        if (wanted != null && info != null && info.kind != wanted) return CertPick.Rejected(notMatching)
        return CertPick.Picked(file, info)
    }

    /** The PEM of a certificate file, from the inspector or, failing that, from the bytes. */
    fun pemOf(info: CertFile?, bytes: ByteArray): String? = info?.pem ?: PemFiles.readText(bytes, "CERTIFICATE")
}

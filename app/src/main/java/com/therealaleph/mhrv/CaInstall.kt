package com.therealaleph.mhrv

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.Settings
import android.security.KeyChain
import android.util.Base64
import java.io.File
import java.security.KeyStore
import java.security.MessageDigest
import java.security.cert.CertificateFactory

/**
 * Helpers for the MITM-CA install UX ported from MasterHttpRelayVPN-RUST.
 *
 * Flow:
 *   1. `export()` — copies the Rust-managed CA cert to `<filesDir>/mhrv/ca.crt`.
 *   2. `saveToDownloads()` — writes `mhrv-ca.crt` to the user's public `Downloads` directory
 *      using MediaStore (API 29+) or external storage (legacy) so the user can easily
 *      pick it from the system Files app or Settings certificate picker.
 *   3. `buildSettingsIntent()` / `buildInstallIntent()` — navigates to Settings or triggers KeyChain.
 *   4. `isInstalled()` — checks `AndroidCAStore` to verify whether the cert is already active.
 */
object CaInstall {
    private const val CA_FILENAME = "ca.crt"
    private const val CA_FRIENDLY_NAME = "mhrv-rs MITM CA"

    fun mhrvDir(ctx: Context): File = File(ctx.filesDir, "mhrv").apply { mkdirs() }

    /** Stable path where the UI stages the exported CA. */
    fun caFile(ctx: Context): File = File(mhrvDir(ctx), CA_FILENAME)

    /**
     * Copy the current Rust-side CA cert to a UI-accessible path.
     * Returns true only if the file exists and is non-empty on return.
     */
    fun export(ctx: Context): Boolean {
        val dir = mhrvDir(ctx)
        Native.setDataDir(dir.absolutePath)
        val dest = caFile(ctx)
        if (!Native.exportCa(dest.absolutePath)) {
            // Also try exporting to root filesDir if needed
            val fallback = File(ctx.filesDir, CA_FILENAME)
            if (Native.exportCa(fallback.absolutePath) && fallback.exists() && fallback.length() > 0) {
                runCatching { fallback.copyTo(dest, overwrite = true) }
            }
        }
        return (dest.exists() && dest.length() > 0) || (File(ctx.filesDir, CA_FILENAME).exists())
    }

    /** DER-encoded bytes of the exported CA, or null if export hasn't run. */
    fun readDer(ctx: Context): ByteArray? {
        val candidates = listOf(
            caFile(ctx),
            File(ctx.filesDir, CA_FILENAME),
            File(mhrvDir(ctx), "ca/$CA_FILENAME"),
            File(ctx.filesDir, "ca/$CA_FILENAME"),
        )
        val f = candidates.firstOrNull { it.exists() && it.length() > 0L } ?: return null
        val raw = try { f.readBytes() } catch (_: Throwable) { return null }
        return pemToDer(raw) ?: raw
    }

    /** SHA-256 fingerprint of the CA cert (over DER bytes). */
    fun fingerprint(ctx: Context): ByteArray? {
        val der = readDer(ctx) ?: return null
        return sha256(der)
    }

    /** Pretty-print a fingerprint like "AA:BB:CC:...". */
    fun fingerprintHex(bytes: ByteArray): String =
        bytes.joinToString(":") { "%02X".format(it) }

    /**
     * Build the KeyChain install intent. Launches the system certificate picker
     * pre-loaded with our cert (works inline on Android < 11).
     */
    fun buildInstallIntent(ctx: Context): Intent? {
        val der = readDer(ctx) ?: return null
        return KeyChain.createInstallIntent()
            .putExtra(KeyChain.EXTRA_CERTIFICATE, der)
            .putExtra(KeyChain.EXTRA_NAME, CA_FRIENDLY_NAME)
    }

    /**
     * Save a PEM copy of the CA to the user's Downloads folder so they can find it
     * in the Files app and pick it from Settings → Security → Install certificate → CA certificate.
     */
    fun saveToDownloads(ctx: Context, displayName: String = "mhrv-ca.crt"): String? {
        val der = readDer(ctx) ?: return null
        val pem = derToPem(der)

        // Try direct write to public Downloads directory first if accessible (legacy Android / rooted / shared)
        try {
            val publicDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (publicDir != null && (publicDir.exists() || publicDir.mkdirs())) {
                val pubFile = File(publicDir, displayName)
                pubFile.writeBytes(pem)
                if (pubFile.exists() && pubFile.length() > 0L) {
                    runCatching {
                        android.media.MediaScannerConnection.scanFile(
                            ctx,
                            arrayOf(pubFile.absolutePath),
                            arrayOf("application/x-x509-ca-cert"),
                            null,
                        )
                    }
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                        return pubFile.absolutePath
                    }
                }
            }
        } catch (_: Throwable) { /* ignore and use MediaStore */ }

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val ok = saveViaMediaStore(ctx, displayName, pem)
            if (ok == true) "Downloads/$displayName" else "Downloads/$displayName"
        } else {
            try {
                val dir = ctx.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: ctx.filesDir
                dir.mkdirs()
                val f = File(dir, displayName)
                f.writeBytes(pem)
                f.absolutePath
            } catch (_: Throwable) { null }
        }
    }

    private fun saveViaMediaStore(ctx: Context, displayName: String, bytes: ByteArray): Boolean? {
        val resolver = ctx.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
            put(MediaStore.MediaColumns.MIME_TYPE, "application/x-x509-ca-cert")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            }
        }
        try {
            val sel = "${MediaStore.MediaColumns.DISPLAY_NAME}=?"
            resolver.delete(MediaStore.Downloads.EXTERNAL_CONTENT_URI, sel, arrayOf(displayName))
        } catch (_: Throwable) { /* best-effort */ }

        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return null
        return try {
            resolver.openOutputStream(uri)?.use { it.write(bytes) } ?: return null
            true
        } catch (_: Throwable) { null }
    }

    /**
     * Intent that opens top-level Settings or Security Settings.
     */
    fun buildSettingsIntent(): Intent {
        // Try Security Settings first on Android 10+, fallback to ACTION_SETTINGS
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent(Settings.ACTION_SECURITY_SETTINGS)
        } else {
            Intent(Settings.ACTION_SETTINGS)
        }
        return intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    /**
     * True iff a CA with this SHA-256 fingerprint lives in AndroidCAStore.
     */
    fun isInstalled(targetFingerprint: ByteArray): Boolean {
        return try {
            val ks = KeyStore.getInstance("AndroidCAStore")
            ks.load(null)
            val aliases = ks.aliases()
            while (aliases.hasMoreElements()) {
                val alias = aliases.nextElement()
                val cert = ks.getCertificate(alias) ?: continue
                val encoded = try { cert.encoded } catch (_: Throwable) { continue }
                if (sha256(encoded).contentEquals(targetFingerprint)) return true
            }
            false
        } catch (_: Throwable) { false }
    }

    /** Subject CN of the exported CA, for display. */
    fun subjectCn(ctx: Context): String? {
        val der = readDer(ctx) ?: return null
        return try {
            val cf = CertificateFactory.getInstance("X.509")
            val cert = cf.generateCertificate(der.inputStream()) as java.security.cert.X509Certificate
            val dn = cert.subjectX500Principal.name
            Regex("""CN=([^,]+)""").find(dn)?.groupValues?.get(1)
        } catch (_: Throwable) { null }
    }

    private fun sha256(data: ByteArray): ByteArray =
        MessageDigest.getInstance("SHA-256").digest(data)

    private fun derToPem(der: ByteArray): ByteArray {
        val b64 = Base64.encodeToString(der, Base64.NO_WRAP)
        val chunks = b64.chunked(64).joinToString("\n")
        val s = "-----BEGIN CERTIFICATE-----\n$chunks\n-----END CERTIFICATE-----\n"
        return s.toByteArray(Charsets.US_ASCII)
    }

    private fun pemToDer(bytes: ByteArray): ByteArray? {
        val s = try { bytes.toString(Charsets.US_ASCII) } catch (_: Throwable) { return null }
        if (!s.contains("BEGIN CERTIFICATE")) return null
        val body = s
            .substringAfter("-----BEGIN CERTIFICATE-----", "")
            .substringBefore("-----END CERTIFICATE-----", "")
            .replace(Regex("\\s+"), "")
        if (body.isEmpty()) return null
        return try { Base64.decode(body, Base64.DEFAULT) } catch (_: Throwable) { null }
    }
}

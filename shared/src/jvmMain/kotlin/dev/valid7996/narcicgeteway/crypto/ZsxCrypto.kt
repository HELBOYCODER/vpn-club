package dev.valid7996.narcicgeteway.crypto

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.bouncycastle.crypto.generators.Argon2BytesGenerator
import org.bouncycastle.crypto.params.Argon2Parameters
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.zip.DataFormatException
import java.util.zip.Deflater
import java.util.zip.Inflater
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * .nrg container.
 *
 *  v3 (current, compact):  MAGIC(4) | 0x03 | outerNonce(12) | AES-GCM(openKey, inner)
 *      inner = mode(1) flags(1) createdAt(8) [expiresAt(8)] nameEn [nameFa] note
 *              [salt(16) memKiB(4) iters(1) par(1)]  cfgNonce(12) cfgCipher(rest)
 *      cfgCipher = AES-GCM(configKey, deflate(config))   (deflate only when it makes it smaller)
 *
 *  v2 (JSON inner, base64 inside base64) is still readable so links that were already handed out keep working.
 */
@OptIn(ExperimentalEncodingApi::class)
actual object ZsxCrypto {
    private val MAGIC_ZSX = "ZSX1".toByteArray(Charsets.US_ASCII)
    private val MAGIC_NRG = "NRG1".toByteArray(Charsets.US_ASCII)
    private const val LEGACY_VERSION: Byte = 1
    private const val VERSION_JSON: Byte = 2
    private const val VERSION: Byte = 3
    private const val NONCE_LEN = 12
    private const val TAG_BITS = 128
    private const val KEY_LEN = 32
    private const val SALT_LEN = 16

    private const val ARGON_MEM_KIB = 65_536
    private const val ARGON_ITERATIONS = 3
    private const val ARGON_PARALLELISM = 1

    private const val FLAG_EXPIRY = 1
    private const val FLAG_SAME_NAME = 2
    private const val FLAG_DEFLATE = 4
    private const val FLAG_DEVICE = 8

    private const val MAX_PLAIN_BYTES = 8 * 1024 * 1024

    private const val CFG_AAD = "zsx-cfg-v1"

    private val openKey: ByteArray =
        MessageDigest.getInstance("SHA-256").digest("NarcicGeteway .zsx v2".toByteArray(Charsets.US_ASCII))

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val rng = SecureRandom()

    actual fun secureRandomBytes(n: Int): ByteArray = randomBytes(n)

    actual fun claimProof(key: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest("nrg-claim:${key.trim()}".toByteArray(Charsets.UTF_8))
        return Base64.encode(digest).trimEnd('=').replace('+', '-').replace('/', '_')
    }

    actual fun seal(request: ZsxSealRequest): ByteArray {
        val cfgNonce = randomBytes(NONCE_LEN)
        val mode: Int
        var salt: ByteArray? = null
        val configKey: ByteArray
        val password = request.password
        if (password.isNullOrEmpty()) {
            mode = 0
            configKey = openKey
        } else {
            mode = 1
            val s = randomBytes(SALT_LEN)
            salt = s
            configKey = argon2(password, s, ARGON_MEM_KIB, ARGON_ITERATIONS, ARGON_PARALLELISM)
        }

        val raw = request.configPayload.toByteArray(Charsets.UTF_8)
        val packed = deflate(raw)
        val useDeflate = packed.size < raw.size
        val cfgCipher = aesGcmEncrypt(
            configKey, cfgNonce, CFG_AAD.toByteArray(), if (useDeflate) packed else raw,
        )

        val sameName = request.nameEn == request.nameFa
        var flags = 0
        if (request.expiresAt != null) flags = flags or FLAG_EXPIRY
        if (sameName) flags = flags or FLAG_SAME_NAME
        if (useDeflate) flags = flags or FLAG_DEFLATE
        if (request.deviceBound) flags = flags or FLAG_DEVICE

        val buf = ByteArrayOutputStream(cfgCipher.size + 128)
        val d = DataOutputStream(buf)
        d.writeByte(mode)
        d.writeByte(flags)
        d.writeLong(System.currentTimeMillis())
        request.expiresAt?.let { d.writeLong(it) }
        writeStr(d, request.nameEn)
        if (!sameName) writeStr(d, request.nameFa)
        writeStr(d, request.note)
        if (mode == 1) {
            d.write(salt!!)
            d.writeInt(ARGON_MEM_KIB)
            d.writeByte(ARGON_ITERATIONS)
            d.writeByte(ARGON_PARALLELISM)
        }
        d.write(cfgNonce)
        d.write(cfgCipher)
        d.flush()
        val innerBytes = buf.toByteArray()

        val outerNonce = randomBytes(NONCE_LEN)
        val outerCipher = aesGcmEncrypt(openKey, outerNonce, aad(MAGIC_NRG, VERSION), innerBytes)

        return MAGIC_NRG + byteArrayOf(VERSION) + outerNonce + outerCipher
    }

    actual fun peek(bytes: ByteArray): ZsxMetadata = readInner(bytes).let { inner ->
        ZsxMetadata(
            passwordProtected = inner.mode == 1,
            nameEn = inner.nameEn,
            nameFa = inner.nameFa,
            note = inner.note,
            createdAt = inner.createdAt,
            expiresAt = inner.expiresAt,
            deviceBound = inner.deviceBound,
        )
    }

    actual fun open(bytes: ByteArray, password: String?): String {
        val inner = readInner(bytes)
        val exp = inner.expiresAt
        if (exp != null && System.currentTimeMillis() > exp) {
            throw ZsxExpiredException()
        }

        val configKey: ByteArray = if (inner.mode == 1) {
            if (password.isNullOrEmpty()) throw ZsxPasswordRequiredException()
            val salt = inner.salt ?: throw ZsxTamperException()
            argon2(password, salt, inner.memKiB, inner.iterations, inner.parallelism)
        } else {
            openKey
        }
        return try {
            val plain = aesGcmDecrypt(configKey, inner.cfgNonce, CFG_AAD.toByteArray(), inner.cfgCipher)
            String(if (inner.deflated) inflate(plain) else plain, Charsets.UTF_8)
        } catch (e: AEADBadTagException) {
            if (inner.mode == 1) throw ZsxWrongPasswordException() else throw ZsxTamperException()
        }
    }

    private class Inner(
        val mode: Int,
        val nameEn: String,
        val nameFa: String,
        val note: String,
        val createdAt: Long,
        val expiresAt: Long?,
        val salt: ByteArray?,
        val memKiB: Int,
        val iterations: Int,
        val parallelism: Int,
        val cfgNonce: ByteArray,
        val cfgCipher: ByteArray,
        val deflated: Boolean,
        val deviceBound: Boolean = false,
    )

    private fun readInner(bytes: ByteArray): Inner {
        if (bytes.size < 5) throw ZsxTamperException()
        val magic4 = bytes.copyOfRange(0, 4)
        val isNrg = magic4.contentEquals(MAGIC_NRG)
        val isZsx = magic4.contentEquals(MAGIC_ZSX)
        if (!isNrg && !isZsx) throw ZsxTamperException()
        val usedMagic = if (isNrg) MAGIC_NRG else MAGIC_ZSX

        val version = bytes[4]
        when (version) {
            VERSION, VERSION_JSON -> Unit
            LEGACY_VERSION -> throw ZsxLegacyException()
            else -> throw ZsxVersionException()
        }
        if (bytes.size < 4 + 1 + NONCE_LEN + 16) throw ZsxTamperException()
        val nonceStart = 4 + 1
        val outerNonce = bytes.copyOfRange(nonceStart, nonceStart + NONCE_LEN)
        val outerCipher = bytes.copyOfRange(nonceStart + NONCE_LEN, bytes.size)
        val innerBytes = try {
            aesGcmDecrypt(openKey, outerNonce, aad(usedMagic, version), outerCipher)
        } catch (e: AEADBadTagException) {
            throw ZsxTamperException()
        }
        return if (version == VERSION) parseV3(innerBytes) else parseV2(innerBytes)
    }

    private fun parseV3(buf: ByteArray): Inner {
        try {
            val d = DataInputStream(ByteArrayInputStream(buf))
            val mode = d.readUnsignedByte()
            val flags = d.readUnsignedByte()
            if (mode != 0 && mode != 1) throw ZsxTamperException()
            val createdAt = d.readLong()
            val expiresAt = if ((flags and FLAG_EXPIRY) != 0) d.readLong() else null
            val nameEn = readStr(d)
            val nameFa = if ((flags and FLAG_SAME_NAME) != 0) nameEn else readStr(d)
            val note = readStr(d)
            var salt: ByteArray? = null
            var mem = ARGON_MEM_KIB
            var iters = ARGON_ITERATIONS
            var par = ARGON_PARALLELISM
            if (mode == 1) {
                salt = ByteArray(SALT_LEN).also { d.readFully(it) }
                mem = d.readInt()
                iters = d.readUnsignedByte()
                par = d.readUnsignedByte()
                // A crafted link must not be able to ask for gigabytes of RAM.
                if (mem !in 8_192..262_144 || iters !in 1..10 || par !in 1..4) throw ZsxTamperException()
            }
            val nonce = ByteArray(NONCE_LEN).also { d.readFully(it) }
            val cipher = d.readBytes()
            if (cipher.size < 16) throw ZsxTamperException()
            return Inner(
                mode, nameEn, nameFa, note, createdAt, expiresAt,
                salt, mem, iters, par, nonce, cipher, (flags and FLAG_DEFLATE) != 0, (flags and FLAG_DEVICE) != 0,
            )
        } catch (e: IOException) {
            throw ZsxTamperException()
        }
    }

    private fun parseV2(innerBytes: ByteArray): Inner {
        val v2 = try {
            json.decodeFromString(ZsxInnerV2.serializer(), String(innerBytes, Charsets.UTF_8))
        } catch (e: Exception) {
            throw ZsxTamperException()
        }
        return try {
            Inner(
                v2.mode, v2.nameEn, v2.nameFa, v2.note, v2.createdAt, v2.expiresAt,
                v2.kdf?.let { Base64.decode(it.salt) }, ARGON_MEM_KIB, ARGON_ITERATIONS, ARGON_PARALLELISM,
                Base64.decode(v2.cfgNonce), Base64.decode(v2.cfgCipher), false,
            )
        } catch (e: IllegalArgumentException) {
            throw ZsxTamperException()
        }
    }

    private fun aad(magic: ByteArray, version: Byte): ByteArray = magic + byteArrayOf(version)

    private fun writeStr(d: DataOutputStream, s: String) {
        val b = s.toByteArray(Charsets.UTF_8)
        val len = minOf(b.size, 65_535)
        d.writeShort(len)
        d.write(b, 0, len)
    }

    private fun readStr(d: DataInputStream): String {
        val len = d.readUnsignedShort()
        val b = ByteArray(len)
        d.readFully(b)
        return String(b, Charsets.UTF_8)
    }

    private fun deflate(data: ByteArray): ByteArray {
        val deflater = Deflater(Deflater.BEST_COMPRESSION)
        try {
            deflater.setInput(data)
            deflater.finish()
            val out = ByteArrayOutputStream(maxOf(64, data.size / 2))
            val chunk = ByteArray(8192)
            while (!deflater.finished()) {
                val n = deflater.deflate(chunk)
                out.write(chunk, 0, n)
            }
            return out.toByteArray()
        } finally {
            deflater.end()
        }
    }

    private fun inflate(data: ByteArray): ByteArray {
        val inflater = Inflater()
        try {
            inflater.setInput(data)
            val out = ByteArrayOutputStream(data.size * 3)
            val chunk = ByteArray(8192)
            while (!inflater.finished()) {
                val n = inflater.inflate(chunk)
                if (n == 0) throw ZsxTamperException()
                out.write(chunk, 0, n)
                if (out.size() > MAX_PLAIN_BYTES) throw ZsxTamperException()
            }
            return out.toByteArray()
        } catch (e: DataFormatException) {
            throw ZsxTamperException()
        } finally {
            inflater.end()
        }
    }

    private fun aesGcmEncrypt(key: ByteArray, nonce: ByteArray, aad: ByteArray, plain: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, nonce))
        cipher.updateAAD(aad)
        return cipher.doFinal(plain)
    }

    private fun aesGcmDecrypt(key: ByteArray, nonce: ByteArray, aad: ByteArray, cipherText: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, nonce))
        cipher.updateAAD(aad)
        return cipher.doFinal(cipherText)
    }

    private fun argon2(password: String, salt: ByteArray, memKiB: Int, iterations: Int, parallelism: Int): ByteArray {
        val params = Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
            .withVersion(Argon2Parameters.ARGON2_VERSION_13)
            .withSalt(salt)
            .withMemoryAsKB(memKiB)
            .withIterations(iterations)
            .withParallelism(parallelism)
            .build()
        val generator = Argon2BytesGenerator().apply { init(params) }
        val out = ByteArray(KEY_LEN)
        generator.generateBytes(password.toCharArray(), out)
        return out
    }

    private fun randomBytes(n: Int): ByteArray = ByteArray(n).also { rng.nextBytes(it) }
}

@Serializable
private data class ZsxInnerV2(
    val mode: Int,
    val nameEn: String,
    val nameFa: String,
    val note: String,
    val createdAt: Long,
    val expiresAt: Long? = null,
    val kdf: ZsxKdfV2? = null,
    val cfgNonce: String,
    val cfgCipher: String,
)

@Serializable
private data class ZsxKdfV2(
    val salt: String,
    val memKiB: Int,
    val iterations: Int,
    val parallelism: Int,
)

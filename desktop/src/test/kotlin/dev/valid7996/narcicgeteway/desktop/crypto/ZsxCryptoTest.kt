package dev.valid7996.narcicgeteway.desktop.crypto

import dev.valid7996.narcicgeteway.crypto.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith

class ZsxCryptoTest {
    private val sampleConfig =
        "vless://11111111-2222-3333-4444-555555555555@example.com:443?encryption=none&security=reality#Node"

    private fun request(password: String?, expiresAt: Long? = null) = ZsxSealRequest(
        configPayload = sampleConfig,
        nameEn = "Berlin Fast",
        nameFa = "برلین سریع",
        note = "Join @mychannel for updates. 50GB cap.",
        expiresAt = expiresAt,
        password = password,
    )

    @Test
    fun roundtripWithoutPassword() {
        val file = ZsxCrypto.seal(request(password = null))
        assertEquals(sampleConfig, ZsxCrypto.open(file, password = null))
    }

    @Test
    fun roundtripWithPassword() {
        val file = ZsxCrypto.seal(request(password = "correct horse battery"))
        assertEquals(sampleConfig, ZsxCrypto.open(file, password = "correct horse battery"))
    }

    @Test
    fun peekExposesMetadataButNotConfig() {
        val file = ZsxCrypto.seal(request(password = "secret"))
        val meta = ZsxCrypto.peek(file)
        assertTrue(meta.passwordProtected)
        assertEquals("Berlin Fast", meta.nameEn)
        assertEquals("برلین سریع", meta.nameFa)
        assertTrue(meta.note.contains("mychannel"))
        assertFalse(meta.isExpired)
    }

    @Test
    fun newFilesUseVersionThree() {
        val file = ZsxCrypto.seal(request(password = null))
        val magic = String(file.copyOfRange(0, 4), Charsets.US_ASCII)
        assertTrue(magic == "NRG1" || magic == "ZSX1")
        assertEquals(3, file[4].toInt())
    }

    @Test
    fun configIsNotStoredAsPlainText() {
        val file = String(ZsxCrypto.seal(request(password = null)), Charsets.ISO_8859_1)
        assertFalse(file.contains("example.com"))
        assertFalse(file.contains("vless://"))
    }

    @Test
    fun wrongPasswordIsRejected() {
        val file = ZsxCrypto.seal(request(password = "the-real-password"))
        assertFailsWith<ZsxWrongPasswordException> {
            ZsxCrypto.open(file, password = "not-the-password")
        }
    }

    @Test
    fun passwordProtectedRequiresPassword() {
        val file = ZsxCrypto.seal(request(password = "pw"))
        assertFailsWith<ZsxPasswordRequiredException> {
            ZsxCrypto.open(file, password = null)
        }
    }

    @Test
    fun tamperedFileIsRejected() {
        val file = ZsxCrypto.seal(request(password = null))
        file[file.size - 1] = (file[file.size - 1] + 1).toByte()
        assertFailsWith<ZsxTamperException> {
            ZsxCrypto.open(file, password = null)
        }
    }

    @Test
    fun truncatedFileIsRejected() {
        val file = ZsxCrypto.seal(request(password = null))
        assertFailsWith<ZsxTamperException> {
            ZsxCrypto.open(file.copyOfRange(0, file.size - 8), password = null)
        }
    }

    @Test
    fun otherFilesAreRejected() {
        assertFailsWith<ZsxTamperException> {
            ZsxCrypto.peek("{\"outbounds\":[]}".toByteArray())
        }
    }

    @Test
    fun unknownVersionIsRejected() {
        val file = ZsxCrypto.seal(request(password = null))
        file[4] = 0x7F
        assertFailsWith<ZsxVersionException> {
            ZsxCrypto.open(file, password = null)
        }
    }

    @Test
    fun filesFromOlderVersionsAreReportedAsLegacy() {
        val file = "ZSX1".toByteArray(Charsets.US_ASCII) + byteArrayOf(1) + ByteArray(64) { it.toByte() }
        assertFailsWith<ZsxLegacyException> { ZsxCrypto.peek(file) }
        assertFailsWith<ZsxLegacyException> { ZsxCrypto.open(file, password = null) }
    }

    @Test
    fun expiredFileIsRejected() {
        val file = ZsxCrypto.seal(request(password = null, expiresAt = System.currentTimeMillis() - 1000))
        assertFailsWith<ZsxExpiredException> {
            ZsxCrypto.open(file, password = null)
        }
    }

    @Test
    fun differentSealsProduceDifferentCiphertext() {
        val a = ZsxCrypto.seal(request(password = null))
        val b = ZsxCrypto.seal(request(password = null))
        assertFalse(a.contentEquals(b))
    }

    @Test
    fun largeJsonConfigIsCompressed() {
        val big = buildString {
            append("{\"outbounds\":[")
            repeat(150) { i ->
                if (i > 0) append(',')
                append("{\"tag\":\"node-$i\",\"protocol\":\"vless\",\"settings\":{\"vnext\":[{\"address\":\"n$i.example.com\",\"port\":443}]}}")
            }
            append("]}")
        }
        val file = ZsxCrypto.seal(request(password = "k").copy(configPayload = big))
        assertTrue(file.size < big.length / 2, "file ${file.size} should be well under raw ${big.length}")
        assertEquals(big, ZsxCrypto.open(file, password = "k"))
    }

    @Test
    fun linkRoundtripAndLinkIsUrlSafe() {
        val file = ZsxCrypto.seal(request(password = "k"))
        val link = NrgLink.encode(file)
        assertTrue(link.startsWith("nrg://"))
        assertFalse(link.substring(6).any { it == '+' || it == '/' || it == '=' })
        assertEquals(sampleConfig, ZsxCrypto.open(NrgLink.decode(link), password = "k"))
    }

    @Test
    fun decoderAcceptsOldStandardBase64WrappedLinks() {
        val file = ZsxCrypto.seal(request(password = null))
        @OptIn(kotlin.io.encoding.ExperimentalEncodingApi::class)
        val std = kotlin.io.encoding.Base64.encode(file)
        val wrapped = "nrg://" + std.chunked(40).joinToString("\n")
        assertEquals(sampleConfig, ZsxCrypto.open(NrgLink.decode(wrapped), password = null))
        assertEquals(sampleConfig, ZsxCrypto.open(NrgLink.decode(std), password = null))
    }

    @Test
    fun relayLinkIsFoundInsideAMessage() {
        val msg = "🔗 لینک کانفیگ:\nnrg://r.Ab3dEf9hJk@nrg.example.workers.dev.\nبرنامه را باز کنید"
        val relay = NrgLink.findRelay(msg)
        assertEquals("Ab3dEf9hJk", relay?.id)
        assertEquals("nrg.example.workers.dev", relay?.host)
    }

    @Test
    fun secureRandomBytesHaveRequestedLength() {
        assertEquals(12, ZsxCrypto.secureRandomBytes(12).size)
    }

    @Test
    fun deviceBoundFlagIsExposedAndWrongDeviceFails() {
        val file = ZsxCrypto.seal(request(password = "NG-1\u0000device-A").copy(deviceBound = true))
        assertTrue(ZsxCrypto.peek(file).deviceBound)
        assertEquals(sampleConfig, ZsxCrypto.open(file, password = "NG-1\u0000device-A"))
        assertFailsWith<ZsxWrongPasswordException> {
            ZsxCrypto.open(file, password = "NG-1\u0000device-B")
        }
    }

    @Test
    fun pureDeviceLockRoundtripAndWrongDeviceFails() {
        val file = ZsxCrypto.seal(request(password = "device-A").copy(deviceBound = true))
        assertTrue(ZsxCrypto.peek(file).deviceBound)
        assertEquals(sampleConfig, ZsxCrypto.open(file, password = "device-A"))
        assertFailsWith<ZsxWrongPasswordException> {
            ZsxCrypto.open(file, password = "device-B")
        }
    }
}

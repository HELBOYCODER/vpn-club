package dev.valid7996.narcicgeteway.crypto

expect object ZsxCrypto {
    fun seal(request: ZsxSealRequest): ByteArray
    fun peek(bytes: ByteArray): ZsxMetadata
    fun open(bytes: ByteArray, password: String?): String

    /** Cryptographically secure random bytes (used for user keys and relay ids). */
    fun secureRandomBytes(n: Int): ByteArray

    /** base64url(SHA-256("nrg-claim:" + key)) — what the relay stores/compares instead of the key itself. */
    fun claimProof(key: String): String
}

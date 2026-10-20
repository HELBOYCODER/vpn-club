package dev.valid7996.narcicgeteway.crypto

import dev.valid7996.narcicgeteway.platform.currentTimeMillis

data class ZsxMetadata(
    val passwordProtected: Boolean,
    val nameEn: String,
    val nameFa: String,
    val note: String,
    val createdAt: Long,
    val expiresAt: Long?,
    val deviceBound: Boolean = false,
) {
    val isExpired: Boolean
        get() = expiresAt != null && currentTimeMillis() > expiresAt
}

data class ZsxSealRequest(
    val configPayload: String,
    val nameEn: String,
    val nameFa: String,
    val note: String,
    val expiresAt: Long?,
    val password: String?,
    /** The password already includes the buyer's device code; stored as a flag so the app knows to add its own. */
    val deviceBound: Boolean = false,
)

sealed class ZsxException(message: String) : Exception(message)
class ZsxVersionException : ZsxException("Unsupported .nrg version")
class ZsxTamperException : ZsxException("File is corrupt or has been tampered with")
class ZsxWrongPasswordException : ZsxException("Incorrect password")
class ZsxExpiredException : ZsxException("This locked config has expired")
class ZsxPasswordRequiredException : ZsxException("A password is required")
class ZsxLegacyException : ZsxException("This locked config was made by an older version of Narcic Getway")
class ZsxKeyConsumedException(message: String = "This one-time private key has already been consumed") : ZsxException(message)

fun zsxFileName(name: String): String {
    val base = name.trim().ifBlank { "config" }
        .replace(Regex("[^A-Za-z0-9._-]"), "_")
        .take(48)
    return if (base.endsWith(".zsx")) base else "$base.zsx"
}

fun nrgFileName(name: String): String {
    val base = name.trim().ifBlank { "config" }
        .replace(Regex("[^A-Za-z0-9._-]"), "_")
        .take(48)
    return if (base.endsWith(".nrg")) base else "$base.nrg"
}


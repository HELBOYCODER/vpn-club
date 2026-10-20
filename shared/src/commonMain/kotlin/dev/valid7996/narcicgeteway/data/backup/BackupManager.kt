package dev.valid7996.narcicgeteway.data.backup

import dev.valid7996.narcicgeteway.domain.config.Subscription
import dev.valid7996.narcicgeteway.domain.config.VpnProfile
import dev.valid7996.narcicgeteway.domain.model.AppSettings
import dev.valid7996.narcicgeteway.platform.currentTimeMillis
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class BackupPayload(
    val app: String = "Narcic Getway",
    val version: Int = 4,
    val createdAt: Long = currentTimeMillis(),
    val settings: AppSettings? = null,
    val profiles: List<VpnProfile> = emptyList(),
    val subscriptions: List<Subscription> = emptyList(),
)

object BackupManager {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
    }

    private val compactJson = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    fun exportBackup(
        settings: AppSettings,
        profiles: List<VpnProfile>,
        subscriptions: List<Subscription>,
    ): String {
        val payload = BackupPayload(
            app = "Narcic Getway",
            version = 4,
            createdAt = currentTimeMillis(),
            settings = settings,
            profiles = profiles,
            subscriptions = subscriptions,
        )
        return json.encodeToString(BackupPayload.serializer(), payload)
    }

    fun parseBackup(jsonText: String): Result<BackupPayload> = runCatching {
        val trimmed = jsonText.trim()
        require(trimmed.isNotEmpty()) { "متن نسخه پشتیبان خالی است" }
        compactJson.decodeFromString(BackupPayload.serializer(), trimmed)
    }
}

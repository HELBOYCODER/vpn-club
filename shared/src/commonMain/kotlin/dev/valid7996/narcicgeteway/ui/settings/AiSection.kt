package dev.valid7996.narcicgeteway.ui.settings

import dev.valid7996.narcicgeteway.domain.ai.AiAppBridge
import dev.valid7996.narcicgeteway.domain.ai.AiSettings

data class AiSection(
    val settings: AiSettings,
    val onUpdate: (AiSettings) -> Unit,
    val bridge: AiAppBridge,
    val onOpenUrl: (String) -> Unit,

    val languageName: String,
    val languageNative: String,

    val platform: String,
    val appVersion: String,
)

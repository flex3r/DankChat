package com.flxrs.dankchat.preferences.tools

import kotlinx.serialization.Serializable

@Serializable
data class ToolsSettings(
    val uploaderConfig: ImageUploaderConfig = ImageUploaderConfig.DEFAULT,
    val ttsEnabled: Boolean = false,
    val ttsPlayMode: TTSPlayMode = TTSPlayMode.Queue,
    val ttsMessageFormat: TTSMessageFormat = TTSMessageFormat.UserAndMessage,
    val ttsForceEnglish: Boolean = false,
    val ttsIgnoreUrls: Boolean = false,
    val ttsIgnoreEmotes: Boolean = false,
    val ttsUserIgnoreList: Set<String> = emptySet(),
)

@Serializable
data class ImageUploaderConfig(
    val uploadUrl: String,
    val formField: String,
    val headers: String?,
    val imageLinkPattern: String?,
    val deletionLinkPattern: String?,
) {
    companion object {
        val DEFAULT = ImageUploaderConfig(
            uploadUrl = "https://kappa.lol/api/upload",
            formField = "file",
            headers = null,
            imageLinkPattern = "{link}",
            deletionLinkPattern = "{delete}",
        )
    }
}

enum class TTSPlayMode {
    Queue,
    Newest,
}

enum class TTSMessageFormat {
    Message,
    UserAndMessage,
}

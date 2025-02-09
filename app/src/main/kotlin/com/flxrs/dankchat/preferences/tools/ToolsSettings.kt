package com.flxrs.dankchat.preferences.tools

import com.flxrs.dankchat.data.toUserNames
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

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
) {

    @Transient
    val ttsUserNameIgnores = ttsUserIgnoreList.toUserNames()
}

@Serializable
data class ImageUploaderConfig(
    val uploadUrl: String,
    val formField: String,
    val headers: String?,
    val imageLinkPattern: String?,
    val deletionLinkPattern: String?,
) {

    @Transient
    val parsedHeaders: List<Pair<String, String>> = headers
        ?.split(";")
        ?.mapNotNull {
            val splits = it.split(":", limit = 2)
            when {
                splits.size != 2 -> null
                else             -> Pair(splits[0].trim(), splits[1].trim())
            }
        }.orEmpty()

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

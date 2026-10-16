package com.flxrs.dankchat.data.api.dankchat.dto

import androidx.annotation.Keep
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Keep
@Serializable
data class DankChatAccountDto(
    @SerialName(value = "badges") val badges: List<DankChatBadgeDto>,
    @SerialName(value = "choice") val choice: DankChatBadgeChoiceDto,
    @SerialName(value = "shownBadgeId") val shownBadgeId: String?,
)

@Keep
@Serializable
sealed interface DankChatBadgeChoiceDto {
    @Keep
    @Serializable
    @SerialName(value = "automatic")
    data object Automatic : DankChatBadgeChoiceDto

    @Keep
    @Serializable
    @SerialName(value = "hidden")
    data object Hidden : DankChatBadgeChoiceDto

    @Keep
    @Serializable
    @SerialName(value = "badge")
    data class Badge(
        @SerialName(value = "id") val id: String,
    ) : DankChatBadgeChoiceDto
}

@Keep
@Serializable
data class DankChatLoginCodeDto(
    @SerialName(value = "url") val url: String,
)

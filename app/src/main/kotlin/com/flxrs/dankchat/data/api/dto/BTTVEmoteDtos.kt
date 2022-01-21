package com.flxrs.dankchat.data.api.dto

import androidx.annotation.Keep
import com.squareup.moshi.Json

@Keep
data class BTTVEmoteDto(
    @field:Json(name = "id") val id: String,
    @field:Json(name = "channel") val channel: String,
    @field:Json(name = "code") val code: String,
    @field:Json(name = "imageType") val imageType: String
)

@Keep
data class BTTVGlobalEmotesDto(
    @field:Json(name = "id") val id: String,
    @field:Json(name = "code") val code: String,
    @field:Json(name = "restrictions") val restrictions: BTTVRestrictionDto,
    @field:Json(name = "imageType") val imageType: String
)

@Keep
data class BTTVRestrictionDto(
    @field:Json(name = "channels") val channels: List<String>,
    @field:Json(name = "games") val games: List<String>,
    @field:Json(name = "emoticonSet") val emoticonSet: String
)

@Keep
data class BTTVChannelDto(
    @field:Json(name = "id") val id: String,
    @field:Json(name = "bots") val bots: List<String>,
    @field:Json(name = "channelEmotes") val emotes: List<BTTVEmoteDto>,
    @field:Json(name = "sharedEmotes") val sharedEmotes: List<BTTVEmoteDto>
)
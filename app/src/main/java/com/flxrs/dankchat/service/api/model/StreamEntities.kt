package com.flxrs.dankchat.service.api.model

import com.squareup.moshi.Json

sealed class StreamEntities {
    data class Stream(
        @field:Json(name = "_id") val id: Long,
        @field:Json(name = "game") val game: String,
        @field:Json(name = "video_height") val videoHeight: Int,
        @field:Json(name = "average_fps") val fps: Double,
        @field:Json(name = "delay") val delay: Int,
        @field:Json(name = "created_at") val createdAt: String,
        @field:Json(name = "is_playlist") val isPlaylist: Boolean,
        @field:Json(name = "preview") val thumbnails: Map<String, String>,
        @field:Json(name = "viewers") val viewers: Int
    )

    data class Result(
        @field:Json(name = "stream") val stream: Stream,
        @field:Json(name = "channel") val channel: Any
    )
}

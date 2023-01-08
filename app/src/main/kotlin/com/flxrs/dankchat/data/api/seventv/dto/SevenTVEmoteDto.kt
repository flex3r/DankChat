package com.flxrs.dankchat.data.api.seventv.dto

import androidx.annotation.Keep
import kotlinx.serialization.Serializable

@Keep
@Serializable
data class SevenTVEmoteDto(
    val id: String,
    val name: String,
    val flags: Long,
    val data: SevenTVEmoteDataDto?
) {
    val isZeroWidth get() = (ZERO_WIDTH_FLAG and flags) == ZERO_WIDTH_FLAG

    companion object {
        private const val ZERO_WIDTH_FLAG = 1L shl 0
    }
}

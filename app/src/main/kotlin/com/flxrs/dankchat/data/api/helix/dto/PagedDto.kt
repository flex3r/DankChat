package com.flxrs.dankchat.data.api.helix.dto

import androidx.annotation.Keep
import kotlinx.serialization.Serializable

@Keep
@Serializable
data class PagedDto<T>(val data: List<T>, val pagination: PaginationDto)


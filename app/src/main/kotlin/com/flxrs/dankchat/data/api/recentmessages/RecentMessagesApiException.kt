package com.flxrs.dankchat.data.api.recentmessages

import com.flxrs.dankchat.data.api.ApiException
import io.ktor.http.*

data class RecentMessagesApiException(
    val error: RecentMessagesError,
    override val status: HttpStatusCode,
    override val message: String? = null,
    override val cause: Throwable? = null
) : ApiException(status, message, cause)

enum class RecentMessagesError {
    ChannelNotJoined,
    ChannelIgnored,
    Unknown
}
package com.flxrs.dankchat.data.api

import android.util.Log
import com.flxrs.dankchat.BuildConfig
import com.flxrs.dankchat.data.api.dto.*
import com.flxrs.dankchat.preferences.DankChatPreferenceStore
import io.ktor.client.call.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.Response
import org.json.JSONObject
import java.io.File
import java.net.URLConnection
import java.time.Instant
import javax.inject.Inject

class ApiManager @Inject constructor(
    private val uploadClient: OkHttpClient,
    private val bttvApiService: BTTVApiService,
    private val dankChatApiService: DankChatApiService,
    private val ffzApiService: FFZApiService,
    private val helixApiService: HelixApiService,
    private val recentMessagesApiService: RecentMessagesApiService,
    private val supibotApiService: SupibotApiService,
    private val authApiService: AuthApiService,
    private val badgesApiService: BadgesApiService,
    private val tmiApiService: TmiApiService,
    private val sevenTVApiService: SevenTVApiService,
    private val dankChatPreferenceStore: DankChatPreferenceStore
) {

    suspend fun validateUser(token: String): ValidateResultDto {
        val response = authApiService.validateUser(token)
        return when {
            response.status.isSuccess() -> response.body<ValidateResultDto.ValidUser>()
            else                        -> response
                .bodyOrNull<ValidateResultDto.Error>()
                ?: ValidateResultDto.Error(response.status.value, response.status.description)
        }
    }

    suspend fun getUserIdByName(name: String): String? {
        return helixApiService.getUserByName(listOf(name))
            ?.bodyOrNull<HelixUsersDto>()
            ?.data?.firstOrNull()?.id
    }

    suspend fun getUsersByNames(names: List<String>): List<HelixUserDto>? {
        return helixApiService.getUserByName(names)
            ?.bodyOrNull<HelixUsersDto>()
            ?.data
    }

    suspend fun getUsersByIds(ids: List<String>): List<HelixUserDto>? {
        return helixApiService.getUsersByIds(ids)
            ?.bodyOrNull<HelixUsersDto>()
            ?.data
    }

    suspend fun getUser(userId: String): HelixUserDto? {
        return helixApiService.getUserById(userId)
            ?.bodyOrNull<HelixUsersDto>()
            ?.data?.firstOrNull()
    }

    suspend fun getUsersFollows(fromId: String, toId: String): UserFollowsDto? {
        return helixApiService.getUsersFollows(fromId, toId)
            ?.bodyOrNull()
    }

    suspend fun getStreams(channels: List<String>): StreamsDto? {
        return helixApiService.getStreams(channels)
            ?.bodyOrNull()
    }

    suspend fun getUserBlocks(userId: String): HelixUserBlockListDto? {
        return helixApiService.getUserBlocks(userId)
            ?.bodyOrNull()
    }

    suspend fun blockUser(targetUserId: String): Boolean {
        return helixApiService.putUserBlock(targetUserId)
            ?.status?.isSuccess() ?: false
    }

    suspend fun unblockUser(targetUserId: String): Boolean {
        return helixApiService.deleteUserBlock(targetUserId)
            ?.status?.isSuccess() ?: false
    }

    suspend fun getUserSets(sets: List<String>): List<DankChatEmoteSetDto>? = dankChatApiService.getSets(sets.joinToString(separator = ",")).bodyOrNull()
    suspend fun getDankChatBadges(): List<DankChatBadgeDto>? = dankChatApiService.getDankChatBadges().bodyOrNull()

    suspend fun getChannelBadges(channelId: String): TwitchBadgesDto? = badgesApiService.getChannelBadges(channelId).bodyOrNull()
    suspend fun getGlobalBadges(): TwitchBadgesDto? = badgesApiService.getGlobalBadges().bodyOrNull()

    suspend fun getFFZChannelEmotes(channelId: String): FFZChannelDto? = ffzApiService.getChannelEmotes(channelId).bodyOrNull()
    suspend fun getFFZGlobalEmotes(): FFZGlobalDto? = ffzApiService.getGlobalEmotes().bodyOrNull()

    suspend fun getBTTVChannelEmotes(channelId: String): BTTVChannelDto? = bttvApiService.getChannelEmotes(channelId).bodyOrNull()
    suspend fun getBTTVGlobalEmotes(): List<BTTVGlobalEmotesDto>? = bttvApiService.getGlobalEmotes().bodyOrNull()

    suspend fun getSevenTVChannelEmotes(channelId: String): List<SevenTVEmoteDto>? = sevenTVApiService.getChannelEmotes(channelId).bodyOrNull()
    suspend fun getSevenTVGlobalEmotes(): List<SevenTVEmoteDto>? = sevenTVApiService.getGlobalEmotes().bodyOrNull()

    suspend fun getRecentMessages(channel: String) = recentMessagesApiService.getRecentMessages(channel)

    suspend fun getSupibotCommands(): SupibotCommandsDto? = supibotApiService.getCommands().bodyOrNull()
    suspend fun getSupibotChannels(): SupibotChannelsDto? = supibotApiService.getChannels().bodyOrNull()
    suspend fun getSupibotUserAliases(user: String): SupibotUserAliasesDto? = supibotApiService.getUserAliases(user).bodyOrNull()

    suspend fun getChatters(channel: String): ChattersDto? = tmiApiService.getChatters(channel).bodyOrNull<ChattersResultDto>()?.chatters
    suspend fun getChatterCount(channel: String): Int? = tmiApiService.getChatters(channel).bodyOrNull<ChatterCountDto>()?.chatterCount

    suspend fun uploadMedia(file: File): Result<UploadDto> = withContext(Dispatchers.IO) {
        val uploader = dankChatPreferenceStore.customImageUploader
        val mimetype = URLConnection.guessContentTypeFromName(file.name)

        val requestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart(name = uploader.formField, filename = file.name, body = file.asRequestBody(mimetype.toMediaType()))
            .build()
        val request = Request.Builder()
            .url(uploader.uploadUrl)
            .header(HttpHeaders.UserAgent, "dankchat/${BuildConfig.VERSION_NAME}")
            .apply {
                uploader.parsedHeaders.forEach { (name, value) ->
                    header(name, value)
                }
            }
            .post(requestBody)
            .build()

        val response = runCatching {
            uploadClient.newCall(request).execute()
        }.getOrElse {
            return@withContext Result.failure(it)
        }

        when {
            response.isSuccessful -> {
                val imageLinkPattern = uploader.imageLinkPattern
                val deletionLinkPattern = uploader.deletionLinkPattern

                if (imageLinkPattern == null) {
                    return@withContext runCatching {
                        val body = response.body.string()
                        UploadDto(
                            imageLink = body,
                            deleteLink = null,
                            timestamp = Instant.now()
                        )
                    }
                }

                val jsonResult = response.asJsonObject()
                jsonResult.mapCatching { json ->
                    val deleteLink = deletionLinkPattern?.let { json.extractLink(it) }
                    val imageLink = json.extractLink(imageLinkPattern)
                    UploadDto(
                        imageLink = imageLink,
                        deleteLink = deleteLink,
                        timestamp = Instant.now()
                    )
                }

            }

            else                  -> {
                Log.e("ApiManager", "Upload failed with ${response.code} ${response.message}")
                Result.failure(ApiException(response.code, response.message))
            }
        }
    }

    @Suppress("RegExpRedundantEscape")
    private suspend fun JSONObject.extractLink(linkPattern: String): String = withContext(Dispatchers.Default) {
        var imageLink: String = linkPattern

        val regex = "\\{(.+)\\}".toRegex()
        regex.findAll(linkPattern).forEach {
            val jsonValue = getValue(it.groupValues[1])
            if (jsonValue != null) {
                imageLink = imageLink.replace(it.groupValues[0], jsonValue)
            }
        }
        imageLink
    }

    private fun Response.asJsonObject(): Result<JSONObject> = runCatching {
        val bodyString = body.string()
        JSONObject(bodyString)
    }.onFailure {
        Log.d("ApiManager", "Error creating JsonObject from response: ", it)
    }

    private fun JSONObject.getValue(pattern: String): String? {
        return runCatching {
            pattern
                .split(".")
                .fold(this) { acc, key ->
                    val value = acc.get(key)
                    if (value !is JSONObject) {
                        return value.toString()
                    }

                    value
                }
            null
        }.getOrNull()
    }

    companion object {
        private val TAG = ApiManager::class.java.simpleName

        private const val BASE_LOGIN_URL = "https://id.twitch.tv/oauth2/authorize?response_type=token"
        private const val REDIRECT_URL = "https://flxrs.com/dankchat"
        val SCOPES = setOf(
            "channel_editor", // TODO to be removed
            "channel_commercial", // TODO to be removed
            "channel:edit:commercial",
            "channel:manage:broadcast",
            "channel:manage:moderators",
            "channel:manage:polls",
            "channel:manage:predictions",
            "channel:manage:raids",
            "channel:manage:vips",
            "channel:moderate",
            "channel:read:polls",
            "channel:read:predictions",
            "channel:read:redemptions",
            "chat:edit",
            "chat:read",
            "moderator:manage:announcements",
            "moderator:manage:automod",
            "moderator:manage:banned_users",
            "moderator:manage:chat_messages",
            "moderator:manage:chat_settings",
            "moderator:manage:shield_mode",
            "moderator:read:chatters",
            "user:manage:blocked_users",
            "user:manage:chat_color",
            "user:manage:whispers",
            "user:read:blocked_users",
            "whispers:edit",
            "whispers:read",
        )
        const val CLIENT_ID = "xu7vd1i6tlr0ak45q1li2wdc0lrma8"
        val LOGIN_URL = "$BASE_LOGIN_URL&client_id=$CLIENT_ID&redirect_uri=$REDIRECT_URL&scope=${SCOPES.joinToString(separator = "+")}"
    }
}

// TODO Should return Result<T>
suspend inline fun <reified T> HttpResponse.bodyOrNull(): T? = runCatching { body<T>() }.getOrElse {
    Log.d("ApiManager", "Failed to parse body as ${T::class.java}: ", it)
    null
}

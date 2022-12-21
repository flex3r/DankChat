package com.flxrs.dankchat.data.api

import android.util.Log
import com.flxrs.dankchat.BuildConfig
import com.flxrs.dankchat.data.api.auth.AuthApi
import com.flxrs.dankchat.data.api.dto.*
import com.flxrs.dankchat.data.api.helix.dto.*
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
    private val recentMessagesApiService: RecentMessagesApiService,
    private val supibotApiService: SupibotApiService,
    private val badgesApiService: BadgesApiService,
    private val tmiApiService: TmiApiService,
    private val sevenTVApiService: SevenTVApiService,
    private val dankChatPreferenceStore: DankChatPreferenceStore
) {



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
                Result.failure(ApiException(HttpStatusCode.fromValue(response.code), response.message))
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
}

// TODO Should return Result<T>
suspend inline fun <reified T> HttpResponse.bodyOrNull(): T? = runCatching { body<T>() }.getOrElse {
    Log.d("ApiManager", "Failed to parse body as ${T::class.java}: ", it)
    null
}

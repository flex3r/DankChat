package com.flxrs.dankchat.service.api

import android.util.Log
import com.flxrs.dankchat.BuildConfig
import com.flxrs.dankchat.preferences.DankChatPreferenceStore
import com.flxrs.dankchat.service.api.dto.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.asRequestBody
import org.json.JSONObject
import retrofit2.Response
import java.io.File
import java.net.URLConnection
import java.time.Instant
import javax.inject.Inject

class ApiManager @Inject constructor(
    private val client: OkHttpClient,
    private val bttvApiService: BTTVApiService,
    private val dankChatApiService: DankChatApiService,
    private val ffzApiService: FFZApiService,
    private val helixApiService: HelixApiService,
    private val krakenApiService: KrakenApiService,
    private val recentMessagesApiService: RecentMessagesApiService,
    private val supibotApiService: SupibotApiService,
    private val authApiService: AuthApiService,
    private val badgesApiService: BadgesApiService,
    private val tmiApiService: TmiApiService,
    private val sevenTVApiService: SevenTVApiService,
    private val dankChatPreferenceStore: DankChatPreferenceStore
) {

    suspend fun validateUser(oAuth: String): ValidateUserDto? {
        try {
            val response = authApiService.validateUser("OAuth $oAuth")
            if (response.isSuccessful) return response.body()
        } catch (t: Throwable) {
            Log.e(TAG, Log.getStackTraceString(t))
        }
        return null
    }

    suspend fun getUserIdByName(oAuth: String, name: String): String? = helixApiService.getUserByName("Bearer $oAuth", name).bodyOrNull?.data?.getOrNull(0)?.id
    suspend fun getUser(oAuth: String, userId: String): HelixUserDto? = helixApiService.getUserById("Bearer $oAuth", userId).bodyOrNull?.data?.getOrNull(0)
    suspend fun getUsersFollows(oAuth: String, fromId: String, toId: String): UserFollowsDto? = helixApiService.getUsersFollows("Bearer $oAuth", fromId, toId).bodyOrNull
    suspend fun getStreams(oAuth: String, channels: List<String>): StreamsDto? = helixApiService.getStreams("Bearer $oAuth", channels).bodyOrNull
    suspend fun getUserBlocks(oAuth: String, userId: String): HelixUserBlockListDto? = helixApiService.getUserBlocks("Bearer $oAuth", userId).bodyOrNull
    suspend fun blockUser(oAuth: String, targetUserId: String): Boolean = helixApiService.putUserBlock("Bearer $oAuth", targetUserId).isSuccessful
    suspend fun unblockUser(oAuth: String, targetUserId: String): Boolean = helixApiService.deleteUserBlock("Bearer $oAuth", targetUserId).isSuccessful
    suspend fun getChannelBadges(oAuth: String, channelId: String): HelixBadgesDto? = helixApiService.getChannelBadges("Bearer $oAuth", channelId).bodyOrNull
    suspend fun getGlobalBadges(oAuth: String): HelixBadgesDto? = helixApiService.getGlobalBadges("Bearer $oAuth").bodyOrNull
    suspend fun getEmoteSets(oAuth: String, setIds: List<String>): HelixEmoteSetsDto? = helixApiService.getEmoteSets("Bearer $oAuth", setIds).bodyOrNull

    suspend fun getUserEmotes(oAuth: String, id: String): TwitchEmotesDto? = krakenApiService.getUserEmotes("OAuth $oAuth", id).bodyOrNull

    suspend fun getUserSet(set: String): DankChatEmoteSetDto? = dankChatApiService.getSet(set).bodyOrNull?.firstOrNull()
    suspend fun getUserSets(sets: List<String>): List<DankChatEmoteSetDto>? = dankChatApiService.getSets(sets.joinToString(separator = ",")).bodyOrNull
    suspend fun getDankChatBadges(): List<DankChatBadgeDto>? = dankChatApiService.getDankChatBadges().bodyOrNull

    suspend fun getChannelBadgesFallback(channelId: String): TwitchBadgesDto? = badgesApiService.getChannelBadges(channelId).bodyOrNull
    suspend fun getGlobalBadgesFallback(): TwitchBadgesDto? = badgesApiService.getGlobalBadges().bodyOrNull

    suspend fun getFFZChannelEmotes(channelId: String): FFZChannelDto? = ffzApiService.getChannelEmotes(channelId).bodyOrNull
    suspend fun getFFZGlobalEmotes(): FFZGlobalDto? = ffzApiService.getGlobalEmotes().bodyOrNull

    suspend fun getBTTVChannelEmotes(channelId: String): BTTVChannelDto? = bttvApiService.getChannelEmotes(channelId).bodyOrNull
    suspend fun getBTTVGlobalEmotes(): List<BTTVGlobalEmotesDto>? = bttvApiService.getGlobalEmotes().bodyOrNull

    suspend fun getSevenTVChannelEmotes(channel: String): List<SevenTVEmoteDto>? = sevenTVApiService.getChannelEmotes(channel).bodyOrNull
    suspend fun getSevenTVGlobalEmotes(): List<SevenTVEmoteDto>? = sevenTVApiService.getGlobalEmotes().bodyOrNull

    suspend fun getRecentMessages(channel: String): RecentMessagesDto? = recentMessagesApiService.getRecentMessages(channel).bodyOrNull

    suspend fun getSupibotCommands(): SupibotCommandsDto? = supibotApiService.getCommands().bodyOrNull
    suspend fun getSupibotChannels(): SupibotChannelsDto? = supibotApiService.getChannels("twitch").bodyOrNull

    suspend fun getChatters(channel: String): ChattersDto? = tmiApiService.getChatters(channel).bodyOrNull?.chatters
    suspend fun getChatterCount(channel: String): Int? = tmiApiService.getChatterCount(channel).bodyOrNull?.chatterCount

    @Suppress("BlockingMethodInNonBlockingContext")
    suspend fun uploadMedia(file: File): UploadDto? = withContext(Dispatchers.IO) {
        val uploader = dankChatPreferenceStore.customImageUploader
        val extension = file.extension.ifBlank { "png" }
        val mimetype = URLConnection.guessContentTypeFromName(file.name)
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart(uploader.formField, filename = "${uploader.formField}.$extension", body = file.asRequestBody(mimetype.toMediaType()))
            .build()

        val request = Request.Builder()
            .url(uploader.uploadUrl)
            .header("User-Agent", "dankchat/${BuildConfig.VERSION_NAME}")
            .apply {
                uploader.parsedHeaders.forEach { (name, value) ->
                    header(name, value)
                }
            }
            .post(body)
            .build()

        val response = client.newCall(request).execute()
        when {
            response.isSuccessful -> {
                val imageLinkPattern = uploader.imageLinkPattern
                val deletionLinkPattern = uploader.deletionLinkPattern

                if (imageLinkPattern == null) {
                    return@withContext response.bodyOrNull?.let {
                        UploadDto(
                            imageLink = it,
                            deleteLink = null,
                            timestamp = Instant.now()
                        )
                    }
                }

                val json = response.jsonObjectOrNull ?: return@withContext null
                val deleteLink = deletionLinkPattern?.let { json.extractLink(it) }
                val imageLink = json.extractLink(imageLinkPattern)

                UploadDto(
                    imageLink = imageLink,
                    deleteLink = deleteLink,
                    timestamp = Instant.now()
                )
            }
            else                  -> null
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

    private val okhttp3.Response.bodyOrNull: String?
        get() = runCatching {
            body?.string()
        }.getOrNull()

    private val okhttp3.Response.jsonObjectOrNull: JSONObject?
        get() = runCatching {
            val bodyString = bodyOrNull ?: return@runCatching null
            JSONObject(bodyString)
        }.getOrNull()

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
        private const val SCOPES = "chat:edit" +
                "+chat:read" +
                "+whispers:read" +
                "+whispers:edit" +
                "+channel_editor" +
                "+channel_commercial" +
                "+channel:moderate" +
                "+channel:edit:commercial" +
                //"+channel:manage:broadcast" +
                //"+channel:read:redemptions" +
                //"+moderator:manage:automod" +
                // "+clips:edit" +
                "+user_read" +
                "+user_subscriptions" +
                "+user_blocks_read" +
                "+user_blocks_edit" +
                "+user:edit:follows" +
                "+user:read:blocked_users" +
                "+user:manage:blocked_users"
        const val CLIENT_ID = "xu7vd1i6tlr0ak45q1li2wdc0lrma8"
        const val LOGIN_URL = "$BASE_LOGIN_URL&client_id=$CLIENT_ID&redirect_uri=$REDIRECT_URL&scope=$SCOPES"
    }
}

private val <T> Response<T>.bodyOrNull
    get() = when {
        isSuccessful -> body()
        else         -> null
    }

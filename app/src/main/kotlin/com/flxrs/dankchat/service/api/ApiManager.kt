package com.flxrs.dankchat.service.api

import android.util.Log
import com.flxrs.dankchat.BuildConfig
import com.flxrs.dankchat.service.api.dto.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import retrofit2.Response
import java.io.File
import java.net.URLConnection
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
    private val tmiApiService: TmiApiService
) {
    private val loadedRecentsInChannels = mutableListOf<String>()

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
    suspend fun followUser(oAuth: String, fromId: String, toId: String): Boolean = helixApiService.putUserFollows("Bearer $oAuth", UserFollowRequestBody(fromId, toId)).isSuccessful
    suspend fun unfollowUser(oAuth: String, fromId: String, toId: String): Boolean = helixApiService.deleteUserFollows("Bearer $oAuth", fromId, toId).isSuccessful
    suspend fun getStreams(oAuth: String, channels: List<String>): StreamsDto? = helixApiService.getStreams("Bearer $oAuth", channels).bodyOrNull
    suspend fun getUserBlocks(oAuth: String, userId: String): HelixUserBlockListDto? = helixApiService.getUserBlocks("Bearer $oAuth", userId).bodyOrNull
    suspend fun blockUser(oAuth: String, targetUserId: String): Boolean = helixApiService.putUserBlock("Bearer $oAuth", targetUserId).isSuccessful
    suspend fun unblockUser(oAuth: String, targetUserId: String): Boolean = helixApiService.deleteUserBlock("Bearer $oAuth", targetUserId).isSuccessful
    suspend fun getChannelBadges(oAuth: String, channelId: String): HelixBadgesDto? = helixApiService.getChannelBadges("Bearer $oAuth", channelId).bodyOrNull
    suspend fun getGlobalBadges(oAuth: String): HelixBadgesDto? = helixApiService.getGlobalBadges("Bearer $oAuth").bodyOrNull

    suspend fun getUserEmotes(oAuth: String, id: String): TwitchEmotesDto? = krakenApiService.getUserEmotes("OAuth $oAuth", id).bodyOrNull

    suspend fun getUserSet(set: String): TwitchEmoteSetDto? = dankChatApiService.getSet(set).bodyOrNull?.firstOrNull()
    suspend fun getDankChatBadges(): List<DankChatBadgeDto>? = dankChatApiService.getDankChatBadges().bodyOrNull

    suspend fun getChannelBadgesFallback(channelId: String): TwitchBadgesDto? = badgesApiService.getChannelBadges(channelId).bodyOrNull
    suspend fun getGlobalBadgesFallback(): TwitchBadgesDto? = badgesApiService.getGlobalBadges().bodyOrNull

    suspend fun getFFZChannelEmotes(channelId: String): FFZChannelDto? = ffzApiService.getChannelEmotes(channelId).bodyOrNull
    suspend fun getFFZGlobalEmotes(): FFZGlobalDto? = ffzApiService.getGlobalEmotes().bodyOrNull

    suspend fun getBTTVChannelEmotes(channelId: String): BTTVChannelDto? = bttvApiService.getChannelEmotes(channelId).bodyOrNull
    suspend fun getBTTVGlobalEmotes(): List<BTTVGlobalEmotesDto>? = bttvApiService.getGlobalEmotes().bodyOrNull

    suspend fun getRecentMessages(channel: String): RecentMessagesDto? {
        return when {
            loadedRecentsInChannels.contains(channel) -> null
            else -> {
                val response = recentMessagesApiService.getRecentMessages(channel)
                response.bodyOrNull?.also { loadedRecentsInChannels += channel }
            }
        }
    }

    suspend fun getSupibotCommands(): SupibotCommandsDto? = supibotApiService.getCommands().bodyOrNull
    suspend fun getSupibotChannels(): SupibotChannelsDto? = supibotApiService.getChannels("twitch").bodyOrNull

    suspend fun getChatters(channel: String): ChattersDto? = tmiApiService.getChatters(channel).bodyOrNull?.chatters

    @Suppress("BlockingMethodInNonBlockingContext")
    suspend fun uploadMedia(file: File): String? = withContext(Dispatchers.IO) {
        val extension = file.extension.ifBlank { "png" }
        val mimetype = URLConnection.guessContentTypeFromName(file.name)
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("abc", "abc.$extension", file.asRequestBody(mimetype.toMediaType()))
            .build()
        val request = Request.Builder()
            .url(NUULS_UPLOAD_URL)
            .header("User-Agent", "dankchat/${BuildConfig.VERSION_NAME}")
            .post(body)
            .build()

        val response = client.newCall(request).execute()
        when {
            response.isSuccessful -> response.body?.string()
            else -> null
        }
    }

    fun clearChannelFromLoaded(channel: String) {
        loadedRecentsInChannels.remove(channel)
    }

    companion object {
        private val TAG = ApiManager::class.java.simpleName
        private const val NUULS_UPLOAD_URL = "https://i.nuuls.com/upload"

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
        else -> null
    }

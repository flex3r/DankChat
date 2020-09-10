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
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.io.File
import java.net.URLConnection

object TwitchApi {
    private val TAG = TwitchApi::class.java.simpleName

    private const val KRAKEN_BASE_URL = "https://api.twitch.tv/kraken/"
    private const val HELIX_BASE_URL = "https://api.twitch.tv/helix/"
    private const val VALIDATE_URL = "https://id.twitch.tv/oauth2/validate"

    private const val TWITCH_SUBBADGES_BASE_URL = "https://badges.twitch.tv/v1/badges/channels/"
    private const val TWITCH_SUBBADGES_SUFFIX = "/display"
    private const val TWITCH_BADGES_URL = "https://badges.twitch.tv/v1/badges/global/display"

    private const val FFZ_BASE_URL = "https://api.frankerfacez.com/v1/room/id/"
    private const val FFZ_GLOBAL_URL = "https://api.frankerfacez.com/v1/set/global"

    private const val BTTV_CHANNEL_BASE_URL = "https://api.betterttv.net/3/cached/users/twitch/"
    private const val BTTV_GLOBAL_URL = "https://api.betterttv.net/3/cached/emotes/global"

    private const val RECENT_MSG_URL = "https://recent-messages.robotty.de/api/v2/recent-messages/"

    private const val NUULS_UPLOAD_URL = "https://i.nuuls.com/upload"

    private const val TWITCHEMOTES_SETS_URL = "https://api.twitchemotes.com/api/v4/sets?id="

    private const val SUPIBOT_URL = "https://supinic.com/api"

    private const val BASE_LOGIN_URL = "https://id.twitch.tv/oauth2/authorize?response_type=token"
    private const val REDIRECT_URL = "https://flxrs.com/dankchat"
    private const val SCOPES = "chat:edit+chat:read+user_read+user_subscriptions" +
            "+channel:moderate+user_blocks_read+user_blocks_edit+whispers:read+whispers:edit" +
            "+channel_editor"
    const val CLIENT_ID = "xu7vd1i6tlr0ak45q1li2wdc0lrma8"
    const val LOGIN_URL = "$BASE_LOGIN_URL&client_id=$CLIENT_ID&redirect_uri=$REDIRECT_URL&scope=$SCOPES"

    private val client: OkHttpClient = OkHttpClient.Builder().build()

    private val service = Retrofit.Builder()
        .baseUrl(KRAKEN_BASE_URL)
        .addConverterFactory(MoshiConverterFactory.create())
        .client(client)
        .build()
        .create(TwitchApiService::class.java)

    private val loadedRecentsInChannels = mutableListOf<String>()

    suspend fun validateUser(oAuth: String): UserDtos.ValidateUser? = withContext(Dispatchers.IO) {
        try {
            val response = service.validateUser(VALIDATE_URL, "OAuth $oAuth")
            if (response.isSuccessful) return@withContext response.body()
        } catch (t: Throwable) {
            Log.e(TAG, Log.getStackTraceString(t))
        }
        return@withContext null
    }

    suspend fun getUserEmotes(oAuth: String, id: String): EmoteDtos.Twitch.Result? = withContext(Dispatchers.IO) {
        val response = service.getUserEmotes("OAuth $oAuth", id)
        response.bodyOrNull
    }

    suspend fun getUserSets(sets: List<String>): List<EmoteDtos.Twitch.EmoteSet>? = withContext(Dispatchers.IO) {
        val ids = sets.joinToString(",")
        val response = service.getSets("${TWITCHEMOTES_SETS_URL}$ids")
        response.bodyOrNull
    }

    suspend fun getUserSet(set: String): EmoteDtos.Twitch.EmoteSet? = withContext(Dispatchers.IO) {
        val response = service.getSet("https://flxrs.com/api/set/$set")
        response.bodyOrNull?.firstOrNull()
    }

    suspend fun getDankChatBadges(): List<BadgeDtos.DankChatBadge>? = withContext(Dispatchers.IO) {
        val response = service.getDankChatBadges("https://flxrs.com/api/badges")
        response.bodyOrNull
    }

    suspend fun getStream(oAuth: String, channel: String): StreamDtos.Stream? = withContext(Dispatchers.IO) {
        return@withContext getUserIdFromName(oAuth, channel)?.let {
            val response = service.getStream(it.toInt())
            response.bodyOrNull?.stream
        }
    }

    suspend fun getChannelBadges(id: String): BadgeDtos.Result? = withContext(Dispatchers.IO) {
        val response = service.getBadgeSets("$TWITCH_SUBBADGES_BASE_URL$id$TWITCH_SUBBADGES_SUFFIX")
        response.bodyOrNull
    }

    suspend fun getGlobalBadges(): BadgeDtos.Result? = withContext(Dispatchers.IO) {
        val response = service.getBadgeSets(TWITCH_BADGES_URL)
        response.bodyOrNull
    }

    suspend fun getFFZChannelEmotes(id: String): EmoteDtos.FFZ.Result? = withContext(Dispatchers.IO) {
        val response = service.getFFZChannelEmotes("$FFZ_BASE_URL$id")
        response.bodyOrNull
    }

    suspend fun getFFZGlobalEmotes(): EmoteDtos.FFZ.GlobalResult? = withContext(Dispatchers.IO) {
        val response = service.getFFZGlobalEmotes(FFZ_GLOBAL_URL)
        response.bodyOrNull
    }

    suspend fun getBTTVChannelEmotes(id: String): EmoteDtos.BTTV.Result? = withContext(Dispatchers.IO) {
        val response = service.getBTTVChannelEmotes("$BTTV_CHANNEL_BASE_URL$id")
        response.bodyOrNull
    }

    suspend fun getBTTVGlobalEmotes(): List<EmoteDtos.BTTV.GlobalEmote>? = withContext(Dispatchers.IO) {
        val response = service.getBTTVGlobalEmotes(BTTV_GLOBAL_URL)
        response.bodyOrNull
    }

    suspend fun getRecentMessages(channel: String): RecentMessagesDto? = withContext(Dispatchers.IO) {
        when {
            loadedRecentsInChannels.contains(channel) -> null
            else -> {
                val response = service.getRecentMessages("$RECENT_MSG_URL$channel")
                response.bodyOrNull?.also { loadedRecentsInChannels += channel }
            }
        }
    }

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

    suspend fun getUserIdFromName(oAuth: String, name: String): String? = withContext(Dispatchers.IO) {
        val response = service.getUserHelix("Bearer $oAuth", "${HELIX_BASE_URL}users?login=$name")
        response.bodyOrNull?.data?.getOrNull(0)?.id
    }

    suspend fun getNameFromUserId(oAuth: String, id: String): String? = withContext(Dispatchers.IO) {
        val response = service.getUserHelix("Bearer $oAuth", "${HELIX_BASE_URL}users?id=$id")
        response.bodyOrNull?.data?.getOrNull(0)?.name
    }

    suspend fun getIgnores(oAuth: String, id: String): UserDtos.KrakenUsersBlocks? = withContext(Dispatchers.IO) {
        service.getIgnores("OAuth $oAuth", id).bodyOrNull

    }

    suspend fun getSupibotCommands(): SupibotDtos.Commands? = withContext(Dispatchers.IO) {
        service.getSupibotCommands("$SUPIBOT_URL/bot/command/list/").bodyOrNull
    }

    suspend fun getSupibotChannels(): SupibotDtos.Channels? = withContext(Dispatchers.IO) {
        service.getSupibotChannels("$SUPIBOT_URL/bot/channel/list", "twitch").bodyOrNull
    }

    fun clearChannelFromLoaded(channel: String) {
        loadedRecentsInChannels.remove(channel)
    }
}

private val <T> Response<T>.bodyOrNull
    get() = when {
        isSuccessful -> body()
        else -> null
    }

package com.flxrs.dankchat.service.api

import android.util.Log
import com.flxrs.dankchat.BuildConfig
import com.flxrs.dankchat.service.api.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.io.File

object TwitchApi {
    private val TAG = TwitchApi::class.java.simpleName

    private const val KRAKEN_BASE_URL = "https://api.twitch.tv/kraken/"
    private const val HELIX_BASE_URL = "https://api.twitch.tv/helix/"

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

    private const val BASE_LOGIN_URL = "https://id.twitch.tv/oauth2/authorize?response_type=token"
    private const val REDIRECT_URL = "https://flxrs.com/dankchat"
    private const val SCOPES = "chat:edit+chat:read+user_read+user_subscriptions" +
            "+channel:moderate+user_blocks_read+user_blocks_edit+whispers:read+whispers:edit"
    const val CLIENT_ID = "xu7vd1i6tlr0ak45q1li2wdc0lrma8"
    const val LOGIN_URL =
        "$BASE_LOGIN_URL&client_id=$CLIENT_ID&redirect_uri=$REDIRECT_URL&scope=$SCOPES"

    private val service = Retrofit.Builder()
        .baseUrl(KRAKEN_BASE_URL)
        .addConverterFactory(MoshiConverterFactory.create())
        .build()
        .create(TwitchApiService::class.java)

    private val nuulsUploadClient = OkHttpClient()

    private val loadedRecentsInChannels = mutableListOf<String>()

    suspend fun getUser(oAuth: String): UserEntities.KrakenUser? = withContext(Dispatchers.IO) {
        try {
            val response = service.getUser("OAuth $oAuth")
            if (response.isSuccessful) return@withContext response.body()
        } catch (t: Throwable) {
            Log.e(TAG, Log.getStackTraceString(t))
        }
        return@withContext null
    }

    suspend fun getUserEmotes(oAuth: String, id: Int): EmoteEntities.Twitch.Result? =
        withContext(Dispatchers.IO) {
            try {
                val response = service.getUserEmotes("OAuth $oAuth", id)
                if (response.isSuccessful) return@withContext response.body()
            } catch (t: Throwable) {
                Log.e(TAG, Log.getStackTraceString(t))
            }
            return@withContext null
        }

    suspend fun getUserSets(sets: List<String>): List<EmoteEntities.Twitch.EmoteSet>? =
        withContext(Dispatchers.IO) {
            try {
                val ids = sets.joinToString(",")
                val response = service.getSets("${TWITCHEMOTES_SETS_URL}$ids")
                if (response.isSuccessful) return@withContext response.body()
            } catch (t: Throwable) {
                Log.e(TAG, Log.getStackTraceString(t))
            }
            return@withContext null
        }

    suspend fun getStream(channel: String): StreamEntities.Stream? = withContext(Dispatchers.IO) {
        getUserIdFromName(channel)?.let {
            try {
                val response = service.getStream(it.toInt())
                return@withContext if (response.isSuccessful) response.body()?.stream else null
            } catch (t: Throwable) {
                Log.e(TAG, Log.getStackTraceString(t))
            }
        }
        return@withContext null
    }

    suspend fun getChannelBadges(id: String): BadgeEntities.Result? =
        withContext(Dispatchers.IO) {
            try {
                val response =
                    service.getBadgeSets("$TWITCH_SUBBADGES_BASE_URL$id$TWITCH_SUBBADGES_SUFFIX")
                return@withContext if (response.isSuccessful) response.body() else null
            } catch (t: Throwable) {
                Log.e(TAG, Log.getStackTraceString(t))
            }
            return@withContext null
        }

    suspend fun getGlobalBadges(): BadgeEntities.Result? = withContext(Dispatchers.IO) {
        try {
            val response = service.getBadgeSets(TWITCH_BADGES_URL)
            if (response.isSuccessful) return@withContext response.body()
        } catch (t: Throwable) {
            Log.e(TAG, Log.getStackTraceString(t))
        }
        return@withContext null
    }

    suspend fun getFFZChannelEmotes(id: String): EmoteEntities.FFZ.Result? =
        withContext(Dispatchers.IO) {
            try {
                val response = service.getFFZChannelEmotes("$FFZ_BASE_URL$id")
                if (response.isSuccessful) return@withContext response.body()
            } catch (t: Throwable) {
                Log.e(TAG, Log.getStackTraceString(t))
            }
            return@withContext null
        }

    suspend fun getFFZGlobalEmotes(): EmoteEntities.FFZ.GlobalResult? =
        withContext(Dispatchers.IO) {
            try {
                val response = service.getFFZGlobalEmotes(FFZ_GLOBAL_URL)
                if (response.isSuccessful) return@withContext response.body()
            } catch (t: Throwable) {
                Log.e(TAG, Log.getStackTraceString(t))
            }
            return@withContext null
        }

    suspend fun getBTTVChannelEmotes(id: String): EmoteEntities.BTTV.Result? =
        withContext(Dispatchers.IO) {
            try {
                val response = service.getBTTVChannelEmotes("$BTTV_CHANNEL_BASE_URL$id")
                if (response.isSuccessful) return@withContext response.body()
            } catch (t: Throwable) {
                Log.e(TAG, Log.getStackTraceString(t))
            }
            return@withContext null
        }

    suspend fun getBTTVGlobalEmotes(): List<EmoteEntities.BTTV.GlobalEmote>? =
        withContext(Dispatchers.IO) {
            try {
                val response = service.getBTTVGlobalEmotes(BTTV_GLOBAL_URL)
                if (response.isSuccessful) return@withContext response.body()
            } catch (t: Throwable) {
                Log.e(TAG, Log.getStackTraceString(t))
            }
            return@withContext null
        }

    suspend fun getRecentMessages(channel: String): RecentMessages? = withContext(Dispatchers.IO) {
        if (loadedRecentsInChannels.contains(channel)) {
            return@withContext null
        }
        try {
            val response =
                service.getRecentMessages("$RECENT_MSG_URL$channel")
            if (response.isSuccessful) {
                loadedRecentsInChannels.add(channel)
                return@withContext response.body()
            }
        } catch (t: Throwable) {
            Log.e(TAG, Log.getStackTraceString(t))
        }
        return@withContext null
    }

    suspend fun uploadImage(file: File): String? = withContext(Dispatchers.IO) {
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("abc", "abc.png", file.asRequestBody("image/png".toMediaType()))
            .build()
        val request = Request.Builder()
            .url(NUULS_UPLOAD_URL)
            .header("User-Agent", "dankchat/${BuildConfig.VERSION_NAME}")
            .post(body)
            .build()
        try {
            val response = nuulsUploadClient.newCall(request).execute()
            if (response.isSuccessful) return@withContext response.body?.string()
        } catch (t: Throwable) {
            Log.e(TAG, Log.getStackTraceString(t))
        }
        return@withContext null
    }

    suspend fun getUserIdFromName(name: String): String? = withContext(Dispatchers.IO) {
        try {
            val response = service.getUserHelix("${HELIX_BASE_URL}users?login=$name")
            if (response.isSuccessful) return@withContext response.body()?.data?.get(0)?.id
        } catch (t: Throwable) {
            Log.e(TAG, Log.getStackTraceString(t))
        }
        return@withContext null
    }

    suspend fun getNameFromUserId(id: Int): String? = withContext(Dispatchers.IO) {
        try {
            val response = service.getUserHelix("${HELIX_BASE_URL}users?id=$id")
            if (response.isSuccessful) return@withContext response.body()?.data?.get(0)?.name
        } catch (t: Throwable) {
            Log.e(TAG, Log.getStackTraceString(t))
        }
        return@withContext null
    }

    suspend fun getIgnores(oAuth: String, id: Int): UserEntities.KrakenUsersBlocks? =
        withContext(Dispatchers.IO) {
            try {
                val response = service.getIgnores("OAuth $oAuth", id)
                if (response.isSuccessful) return@withContext response.body()
            } catch (t: Throwable) {
                Log.e(TAG, Log.getStackTraceString(t))
            }
            return@withContext null
        }
}

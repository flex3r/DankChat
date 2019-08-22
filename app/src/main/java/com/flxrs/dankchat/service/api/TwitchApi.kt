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

    private const val FFZ_BASE_URL = "https://api.frankerfacez.com/v1/room/"
    private const val FFZ_GLOBAL_URL = "https://api.frankerfacez.com/v1/set/global"

    private const val BTTV_CHANNEL_BASE_URL = "https://api.betterttv.net/2/channels/"
    private const val BTTV_GLOBAL_URL = "https://api.betterttv.net/2/emotes/"

    private const val RECENT_MSG_URL = "https://recent-messages.robotty.de/api/v2/recent-messages/"
    private const val RECENT_MSG_URL_SUFFIX = "?clearchatToNotice=true"

    private const val NUULS_UPLOAD_URL = "https://i.nuuls.com/upload"

    private const val BASE_LOGIN_URL = "https://id.twitch.tv/oauth2/authorize?response_type=token"
    private const val REDIRECT_URL = "https://flxrs.com/dankchat"
    private const val SCOPES = "chat:edit+chat:read+user_read+user_subscriptions+channel:moderate"
    const val CLIENT_ID = "xu7vd1i6tlr0ak45q1li2wdc0lrma8"
    const val LOGIN_URL =
        "$BASE_LOGIN_URL&client_id=$CLIENT_ID&redirect_uri=$REDIRECT_URL&scope=$SCOPES"

    private val service = Retrofit.Builder()
        .baseUrl(KRAKEN_BASE_URL)
        .addConverterFactory(MoshiConverterFactory.create())
        .build()
        .create(TwitchApiService::class.java)

    private val nuulsUploadClient = OkHttpClient()

    suspend fun getUser(oAuth: String): UserEntities.FromKraken? = withContext(Dispatchers.IO) {
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

    suspend fun getChannelBadges(channel: String): BadgeEntities.Result? =
        withContext(Dispatchers.IO) {
            getUserIdFromName(channel)?.let {
                try {
                    val response =
                        service.getBadgeSets("$TWITCH_SUBBADGES_BASE_URL$it$TWITCH_SUBBADGES_SUFFIX")
                    return@withContext if (response.isSuccessful) response.body() else null
                } catch (t: Throwable) {
                    Log.e(TAG, Log.getStackTraceString(t))
                }
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

    suspend fun getFFZChannelEmotes(channel: String): EmoteEntities.FFZ.Result? =
        withContext(Dispatchers.IO) {
            try {
                val response = service.getFFZChannelEmotes("$FFZ_BASE_URL$channel")
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

    suspend fun getBTTVChannelEmotes(channel: String): EmoteEntities.BTTV.Result? =
        withContext(Dispatchers.IO) {
            try {
                val response = service.getBTTVChannelEmotes("$BTTV_CHANNEL_BASE_URL$channel")
                if (response.isSuccessful) return@withContext response.body()
            } catch (t: Throwable) {
                Log.e(TAG, Log.getStackTraceString(t))
            }
            return@withContext null
        }

    suspend fun getBTTVGlobalEmotes(): EmoteEntities.BTTV.GlobalResult? =
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
        try {
            val response =
                service.getRecentMessages("$RECENT_MSG_URL$channel")
            if (response.isSuccessful) return@withContext response.body()
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

    private suspend fun getUserIdFromName(name: String): String? = withContext(Dispatchers.IO) {
        try {
            val response = service.getUserHelix("${HELIX_BASE_URL}users?login=$name")
            if (response.isSuccessful) return@withContext response.body()?.data?.get(0)?.id
        } catch (t: Throwable) {
            Log.e(TAG, Log.getStackTraceString(t))
        }
        return@withContext null
    }
}

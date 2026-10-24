package com.flxrs.dankchat.data.api.cache

import com.flxrs.dankchat.data.UserId
import com.flxrs.dankchat.data.api.bttv.BTTVApiClient
import com.flxrs.dankchat.data.api.bttv.dto.BTTVChannelDto
import com.flxrs.dankchat.data.api.bttv.dto.BTTVGlobalEmoteDto
import com.flxrs.dankchat.data.api.ffz.FFZApiClient
import com.flxrs.dankchat.data.api.ffz.dto.FFZChannelDto
import com.flxrs.dankchat.data.api.ffz.dto.FFZGlobalDto
import com.flxrs.dankchat.data.api.seventv.SevenTVApiClient
import com.flxrs.dankchat.data.api.seventv.dto.SevenTVEmoteDto
import com.flxrs.dankchat.data.api.seventv.dto.SevenTVUserDto
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import org.koin.core.annotation.Single

@Single
class CachedEmoteProvider(
    private val sevenTVApiClient: SevenTVApiClient,
    private val bttvApiClient: BTTVApiClient,
    private val ffzApiClient: FFZApiClient,
    private val cache: EmoteDiskCache,
) {
    fun getSevenTVChannelEmotes(
        channelId: UserId,
        forceNetwork: Boolean = false,
    ): Flow<CachedResult<SevenTVUserDto?>> = cachedThenFetch(
        cacheKey = "seventv_channel_${channelId.value}",
        serializer = SevenTVUserDto.serializer(),
        forceNetwork = forceNetwork,
        fetch = { sevenTVApiClient.getSevenTVChannelEmotes(channelId) },
    )

    fun getSevenTVGlobalEmotes(forceNetwork: Boolean = false): Flow<CachedResult<List<SevenTVEmoteDto>?>> = cachedThenFetch(
        cacheKey = "seventv_global",
        serializer = ListSerializer(SevenTVEmoteDto.serializer()),
        forceNetwork = forceNetwork,
        fetch = { sevenTVApiClient.getSevenTVGlobalEmotes().map { it } },
    )

    fun getBTTVChannelEmotes(
        channelId: UserId,
        forceNetwork: Boolean = false,
    ): Flow<CachedResult<BTTVChannelDto?>> = cachedThenFetch(
        cacheKey = "bttv_channel_${channelId.value}",
        serializer = BTTVChannelDto.serializer(),
        forceNetwork = forceNetwork,
        fetch = { bttvApiClient.getBTTVChannelEmotes(channelId) },
    )

    fun getBTTVGlobalEmotes(forceNetwork: Boolean = false): Flow<CachedResult<List<BTTVGlobalEmoteDto>?>> = cachedThenFetch(
        cacheKey = "bttv_global",
        serializer = ListSerializer(BTTVGlobalEmoteDto.serializer()),
        forceNetwork = forceNetwork,
        fetch = { bttvApiClient.getBTTVGlobalEmotes().map { it } },
    )

    fun getFFZChannelEmotes(
        channelId: UserId,
        forceNetwork: Boolean = false,
    ): Flow<CachedResult<FFZChannelDto?>> = cachedThenFetch(
        cacheKey = "ffz_channel_${channelId.value}",
        serializer = FFZChannelDto.serializer(),
        forceNetwork = forceNetwork,
        fetch = { ffzApiClient.getFFZChannelEmotes(channelId) },
    )

    fun getFFZGlobalEmotes(forceNetwork: Boolean = false): Flow<CachedResult<FFZGlobalDto?>> = cachedThenFetch(
        cacheKey = "ffz_global",
        serializer = FFZGlobalDto.serializer(),
        forceNetwork = forceNetwork,
        fetch = { ffzApiClient.getFFZGlobalEmotes().map { it } },
    )

    private fun <T : Any> cachedThenFetch(
        cacheKey: String,
        serializer: KSerializer<T>,
        forceNetwork: Boolean,
        fetch: suspend () -> Result<T?>,
    ): Flow<CachedResult<T?>> = flow {
        var cacheHit = false

        if (!forceNetwork) {
            val cached = cache.read(cacheKey, serializer)
            if (cached != null) {
                cacheHit = true
                emit(CachedResult.Success(cached))
            }
        }

        val result = fetch()
        result.fold(
            onSuccess = { data ->
                // Null means the provider has nothing for this channel anymore, so the cached copy is stale
                when (data) {
                    null -> cache.delete(cacheKey)
                    else -> cache.write(cacheKey, data, serializer)
                }
                emit(CachedResult.Success(data))
            },
            onFailure = { error ->
                if (cacheHit) {
                    emit(CachedResult.CachedFallback(null, error))
                } else {
                    emit(CachedResult.Failure(error))
                }
            },
        )
    }
}

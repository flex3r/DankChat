package com.flxrs.dankchat.domain

import com.flxrs.dankchat.data.UserId
import com.flxrs.dankchat.data.UserName
import com.flxrs.dankchat.data.api.ApiException
import com.flxrs.dankchat.data.repo.PinnedMessageRepository
import com.flxrs.dankchat.data.repo.channel.Channel
import com.flxrs.dankchat.data.repo.channel.ChannelRepository
import com.flxrs.dankchat.data.repo.chat.ChatMessageRepository
import com.flxrs.dankchat.data.repo.chat.ChatRepository
import com.flxrs.dankchat.data.repo.data.DataRepository
import com.flxrs.dankchat.data.repo.data.EmoteLoadResult
import com.flxrs.dankchat.data.state.ChannelLoadingFailure
import com.flxrs.dankchat.data.state.ChannelLoadingState
import com.flxrs.dankchat.data.twitch.message.SystemMessageType
import com.flxrs.dankchat.di.DispatchersProvider
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.core.annotation.Single

@Single
class ChannelDataLoader(
    private val dataRepository: DataRepository,
    private val chatRepository: ChatRepository,
    private val chatMessageRepository: ChatMessageRepository,
    private val channelRepository: ChannelRepository,
    private val pinnedMessageRepository: PinnedMessageRepository,
    private val getChannelsUseCase: GetChannelsUseCase,
    private val userBlocksGate: UserBlocksGate,
    private val dispatchersProvider: DispatchersProvider,
) {
    suspend fun loadChannelData(
        channel: UserName,
        forceNetwork: Boolean = false,
        channelLookup: Job? = null,
    ): ChannelLoadingState = withContext(dispatchersProvider.io) {
        try {
            dataRepository.createFlowsIfNecessary(listOf(channel))
            chatRepository.createFlowsIfNecessary(channel)

            // Only recent messages needs the block list; everything else fetches in parallel.
            launch {
                userBlocksGate.awaitReady()
                chatRepository.loadRecentMessagesIfEnabled(channel)
            }

            // A lookup shared with other channels caches this channel, so it needs no request of its own
            channelLookup?.join()
            val channelInfo = channelRepository.getChannel(channel)
                ?: getChannelsUseCase(listOf(channel)).firstOrNull()
            if (channelInfo == null) {
                return@withContext ChannelLoadingState.Failed(emptyList())
            }

            launch { pinnedMessageRepository.fetch(channel) }

            val badgesResult = async { loadChannelBadges(channel, channelInfo.id) }
            val emotesResults = async { loadChannelEmotes(channel, channelInfo, forceNetwork) }

            val (emoteFailures, fallbacks) = emotesResults.await()
            val failures = listOfNotNull(badgesResult.await()) + emoteFailures

            failures.forEach { failure ->
                val status = (failure.error as? ApiException)?.status?.value?.toString() ?: "0"
                val systemMessageType =
                    when (failure) {
                        is ChannelLoadingFailure.SevenTVEmotes -> SystemMessageType.ChannelSevenTVEmotesFailed(status)
                        is ChannelLoadingFailure.BTTVEmotes -> SystemMessageType.ChannelBTTVEmotesFailed(status)
                        is ChannelLoadingFailure.FFZEmotes -> SystemMessageType.ChannelFFZEmotesFailed(status)
                        else -> null
                    }
                systemMessageType?.let {
                    chatMessageRepository.addSystemMessage(channel, it)
                }
            }

            fallbacks.forEach { chatMessageRepository.addSystemMessage(channel, it) }

            when {
                failures.isEmpty() -> ChannelLoadingState.Loaded
                else -> ChannelLoadingState.Failed(failures)
            }
        } catch (_: Exception) {
            ChannelLoadingState.Failed(emptyList())
        }
    }

    suspend fun loadChannelBadges(
        channel: UserName,
        channelId: UserId,
    ): ChannelLoadingFailure.Badges? = dataRepository.loadChannelBadges(channel, channelId).fold(
        onSuccess = { null },
        onFailure = { ChannelLoadingFailure.Badges(channel, channelId, it) },
    )

    suspend fun loadChannelEmotes(
        channel: UserName,
        channelInfo: Channel,
        forceNetwork: Boolean = false,
    ): Pair<List<ChannelLoadingFailure>, List<SystemMessageType>> = withContext(dispatchersProvider.io) {
        val bttvResult =
            async { dataRepository.loadChannelBTTVEmotes(channel, channelInfo.displayName, channelInfo.id, forceNetwork) }
        val ffzResult =
            async { dataRepository.loadChannelFFZEmotes(channel, channelInfo.id, forceNetwork) }
        val sevenTvResult =
            async { dataRepository.loadChannelSevenTVEmotes(channel, channelInfo.id, forceNetwork) }
        val cheermotesResult =
            async {
                dataRepository.loadChannelCheermotes(channel, channelInfo.id).fold(
                    onSuccess = { null },
                    onFailure = { ChannelLoadingFailure.Cheermotes(channel, it) },
                )
            }
        val followerEmotesResult =
            async {
                dataRepository.loadChannelFollowerEmotes(channel, channelInfo.id).fold(
                    onSuccess = { null },
                    onFailure = { ChannelLoadingFailure.FollowerEmotes(channel, it) },
                )
            }

        val failures = mutableListOf<ChannelLoadingFailure>()
        val fallbackMessages = mutableListOf<SystemMessageType>()

        bttvResult.await().fold(
            onSuccess = { if (it == EmoteLoadResult.CachedFallback) fallbackMessages += SystemMessageType.ChannelBTTVEmotesCachedFallback },
            onFailure = { failures += ChannelLoadingFailure.BTTVEmotes(channel, it) },
        )
        ffzResult.await().fold(
            onSuccess = { if (it == EmoteLoadResult.CachedFallback) fallbackMessages += SystemMessageType.ChannelFFZEmotesCachedFallback },
            onFailure = { failures += ChannelLoadingFailure.FFZEmotes(channel, it) },
        )
        sevenTvResult.await().fold(
            onSuccess = { if (it == EmoteLoadResult.CachedFallback) fallbackMessages += SystemMessageType.ChannelSevenTVEmotesCachedFallback },
            onFailure = { failures += ChannelLoadingFailure.SevenTVEmotes(channel, it) },
        )
        cheermotesResult.await()?.let { failures += it }
        followerEmotesResult.await()?.let { failures += it }

        failures to fallbackMessages
    }
}

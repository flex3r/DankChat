package com.flxrs.dankchat.domain

import com.flxrs.dankchat.data.UserName
import com.flxrs.dankchat.data.repo.channel.Channel
import com.flxrs.dankchat.data.repo.channel.ChannelRepository
import com.flxrs.dankchat.data.toDisplayName
import com.flxrs.dankchat.data.twitch.message.RoomState
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull
import org.koin.core.annotation.Single

@Single
class GetChannelsUseCase(private val channelRepository: ChannelRepository) {

    suspend operator fun invoke(names: List<UserName>): List<Channel> = coroutineScope {
        val channels = channelRepository.getChannels(names)
        val remaining = names - channels.mapTo(mutableSetOf(), Channel::name)
        val (roomStatePairs, remainingForRoomState) = remaining.fold(Pair(emptyList<RoomState>(), emptyList<UserName>())) { (states, remaining), user ->
            when (val state = channelRepository.getRoomState(user)) {
                null -> states to remaining + user
                else -> states + state to remaining
            }
        }

        val remainingPairs = remainingForRoomState.map { user ->
            async {
                withTimeoutOrNull(getRoomStateDelay(remainingForRoomState)) {
                    channelRepository.getRoomState(user)?.let { Channel(it.channelId, it.channel, it.channel.toDisplayName()) }
                }
            }
        }.awaitAll().filterNotNull()

        val roomStateChannels = roomStatePairs.map { Channel(it.channelId, it.channel, it.channel.toDisplayName()) } + remainingPairs
        channelRepository.cacheChannels(roomStateChannels)

        channels + roomStateChannels
    }

    companion object {
        private const val IRC_TIMEOUT_DELAY = 5_000L
        private const val IRC_TIMEOUT_CHANNEL_DELAY = 600L
        private fun getRoomStateDelay(channels: List<UserName>): Long = IRC_TIMEOUT_DELAY + channels.size * IRC_TIMEOUT_CHANNEL_DELAY
    }
}

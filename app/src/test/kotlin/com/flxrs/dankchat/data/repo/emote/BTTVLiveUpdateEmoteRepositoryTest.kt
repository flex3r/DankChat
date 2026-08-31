package com.flxrs.dankchat.data.repo.emote

import com.flxrs.dankchat.data.DisplayName
import com.flxrs.dankchat.data.UserName
import com.flxrs.dankchat.data.api.bttv.dto.BTTVChannelDto
import com.flxrs.dankchat.data.api.bttv.dto.BTTVEmoteDto
import com.flxrs.dankchat.data.api.helix.HelixApiClient
import com.flxrs.dankchat.data.repo.channel.ChannelRepository
import com.flxrs.dankchat.di.DispatchersProvider
import com.flxrs.dankchat.preferences.chat.ChatSettingsDataStore
import io.mockk.mockk
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
internal class BTTVLiveUpdateEmoteRepositoryTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val dispatchersProvider =
        object : DispatchersProvider {
            override val default: CoroutineDispatcher = dispatcher
            override val io: CoroutineDispatcher = dispatcher
            override val main: CoroutineDispatcher = dispatcher
            override val immediate: CoroutineDispatcher = dispatcher
        }
    private val repository =
        EmoteRepository(
            helixApiClient = mockk<HelixApiClient>(),
            chatSettingsDataStore = mockk<ChatSettingsDataStore>(),
            channelRepository = mockk<ChannelRepository>(),
            dispatchersProvider = dispatchersProvider,
        )

    @Test
    fun `applies added renamed and removed emotes`() = runTest(dispatcher) {
        val channel = UserName("forsen")
        repository.createFlowsIfNecessary(listOf(channel))
        repository.setBTTVEmotes(
            channel = channel,
            channelDisplayName = DisplayName("forsen"),
            bttvResult = BTTVChannelDto(id = "123", bots = emptyList(), emotes = listOf(BTTVEmoteDto("old-id", "OldEmote")), sharedEmotes = emptyList()),
        )

        repository.addBTTVEmote(channel, BTTVEmoteDto("new-id", "NewEmote"))
        assertEquals("NewEmote" to "RenamedEmote", repository.updateBTTVEmote(channel, BTTVEmoteDto("new-id", "RenamedEmote")))
        assertEquals("OldEmote", repository.removeBTTVEmote(channel, "old-id"))

        assertEquals(
            listOf("RenamedEmote"),
            repository
                .getEmotes(channel)
                .first()
                .bttvChannelEmotes
                .map { it.code },
        )
    }
}

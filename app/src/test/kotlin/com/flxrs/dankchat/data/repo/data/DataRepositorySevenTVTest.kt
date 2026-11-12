package com.flxrs.dankchat.data.repo.data

import com.flxrs.dankchat.data.UserId
import com.flxrs.dankchat.data.UserName
import com.flxrs.dankchat.data.api.cache.CachedEmoteProvider
import com.flxrs.dankchat.data.api.cache.CachedResult
import com.flxrs.dankchat.data.api.seventv.SevenTVUserDetails
import com.flxrs.dankchat.data.api.seventv.dto.SevenTVEmoteSetDto
import com.flxrs.dankchat.data.api.seventv.dto.SevenTVUserDataDto
import com.flxrs.dankchat.data.api.seventv.dto.SevenTVUserDto
import com.flxrs.dankchat.data.api.seventv.eventapi.SevenTVEventApiClient
import com.flxrs.dankchat.data.api.seventv.eventapi.SevenTVEventMessage
import com.flxrs.dankchat.data.repo.emote.EmoteRepository
import com.flxrs.dankchat.di.DispatchersProvider
import com.flxrs.dankchat.preferences.chat.ChatSettings
import com.flxrs.dankchat.preferences.chat.ChatSettingsDataStore
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
internal class DataRepositorySevenTVTest {
    private val testDispatcher = UnconfinedTestDispatcher()
    private val dispatchersProvider =
        object : DispatchersProvider {
            override val default: CoroutineDispatcher = testDispatcher
            override val io: CoroutineDispatcher = testDispatcher
            override val main: CoroutineDispatcher = testDispatcher
            override val immediate: CoroutineDispatcher = testDispatcher
        }
    private val channel = UserName("forsen")
    private val channelId = UserId("22484632")

    private val cachedEmoteProvider: CachedEmoteProvider = mockk()
    private val sevenTVEventApiClient: SevenTVEventApiClient = mockk(relaxed = true)
    private val emoteRepository: EmoteRepository = mockk(relaxed = true)
    private val chatSettingsDataStore: ChatSettingsDataStore = mockk()

    private lateinit var repository: DataRepository

    @BeforeEach
    fun setup() {
        every { sevenTVEventApiClient.messages } returns MutableSharedFlow<SevenTVEventMessage>()
        every { chatSettingsDataStore.settings } returns flowOf(ChatSettings())
        every { emoteRepository.hasChannel(channel) } returns true
        every { emoteRepository.getSevenTVUserDetails(channel) } returns null

        repository =
            DataRepository(
                helixApiClient = mockk(),
                dankChatApiClient = mockk(),
                badgesApiClient = mockk(),
                cachedEmoteProvider = cachedEmoteProvider,
                sevenTVApiClient = mockk(),
                sevenTVEventApiClient = sevenTVEventApiClient,
                uploadClient = mockk(),
                emoteRepository = emoteRepository,
                recentUploadsRepository = mockk(),
                authDataStore = mockk(),
                chatSettingsDataStore = chatSettingsDataStore,
                dispatchersProvider = dispatchersProvider,
            )
    }

    @Test
    fun `loaded channel subscribes to its user and emote set`() = runTest(testDispatcher) {
        val user = user(emoteSetId = "current")
        every { cachedEmoteProvider.getSevenTVChannelEmotes(channelId, false) } returns flowOf(CachedResult.Success(user))

        repository.loadChannelSevenTVEmotes(channel, channelId)

        coVerify { sevenTVEventApiClient.subscribeEmoteSet("current") }
        coVerify { sevenTVEventApiClient.subscribeUser("user") }
        coVerify { emoteRepository.setSevenTVEmotes(channel, user) }
        coVerify(exactly = 0) { sevenTVEventApiClient.unsubscribeEmoteSet(any()) }
    }

    @Test
    fun `emote set from the cache is unsubscribed once the current one differs`() = runTest(testDispatcher) {
        every { cachedEmoteProvider.getSevenTVChannelEmotes(channelId, false) } returns
            flowOf(CachedResult.Success(user(emoteSetId = "cached")), CachedResult.Success(user(emoteSetId = "current")))
        every { emoteRepository.getSevenTVUserDetails(channel) } returns null andThen SevenTVUserDetails(id = "user", activeEmoteSetId = "cached", connectionIndex = 0)
        every { emoteRepository.getChannelForSevenTVEmoteSet("cached") } returns null

        repository.loadChannelSevenTVEmotes(channel, channelId)

        coVerify { sevenTVEventApiClient.subscribeEmoteSet("current") }
        coVerify { sevenTVEventApiClient.unsubscribeEmoteSet("cached") }
    }

    @Test
    fun `load that finishes after its channel was removed does not subscribe`() = runTest(testDispatcher) {
        every { cachedEmoteProvider.getSevenTVChannelEmotes(channelId, false) } returns flowOf(CachedResult.Success(user(emoteSetId = "current")))
        every { emoteRepository.hasChannel(channel) } returns false

        repository.loadChannelSevenTVEmotes(channel, channelId)

        coVerify(exactly = 0) { sevenTVEventApiClient.subscribeEmoteSet(any()) }
        coVerify(exactly = 0) { sevenTVEventApiClient.subscribeUser(any()) }
        coVerify(exactly = 0) { emoteRepository.setSevenTVEmotes(any(), any()) }
    }

    @Test
    fun `removed channel is unsubscribed and its details are cleared`() = runTest(testDispatcher) {
        every { emoteRepository.hasChannel(channel) } returns false
        every { emoteRepository.getSevenTVUserDetails(channel) } returns SevenTVUserDetails(id = "user", activeEmoteSetId = "current", connectionIndex = 0)

        repository.removeChannels(listOf(channel))

        coVerify { sevenTVEventApiClient.unsubscribeUser("user") }
        coVerify { sevenTVEventApiClient.unsubscribeEmoteSet("current") }
        coVerify { emoteRepository.clearSevenTVEmotes(channel) }
    }

    @Test
    fun `channel that was added again keeps its subscriptions`() = runTest(testDispatcher) {
        every { emoteRepository.getSevenTVUserDetails(channel) } returns SevenTVUserDetails(id = "user", activeEmoteSetId = "current", connectionIndex = 0)

        repository.removeChannels(listOf(channel))

        coVerify(exactly = 0) { sevenTVEventApiClient.unsubscribeUser(any()) }
        coVerify(exactly = 0) { sevenTVEventApiClient.unsubscribeEmoteSet(any()) }
    }

    private fun user(emoteSetId: String): SevenTVUserDto = SevenTVUserDto(
        id = channelId.value,
        user = SevenTVUserDataDto(id = "user", connections = emptyList()),
        emoteSet = SevenTVEmoteSetDto(id = emoteSetId, name = emoteSetId, emotes = emptyList()),
    )
}

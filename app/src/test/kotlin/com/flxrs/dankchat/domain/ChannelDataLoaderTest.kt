package com.flxrs.dankchat.domain

import com.flxrs.dankchat.data.DisplayName
import com.flxrs.dankchat.data.UserId
import com.flxrs.dankchat.data.UserName
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
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.junit5.MockKExtension
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
@ExtendWith(MockKExtension::class)
internal class ChannelDataLoaderTest {
    private val testDispatcher = UnconfinedTestDispatcher()
    private val dispatchersProvider =
        object : DispatchersProvider {
            override val default: CoroutineDispatcher = testDispatcher
            override val io: CoroutineDispatcher = testDispatcher
            override val main: CoroutineDispatcher = testDispatcher
            override val immediate: CoroutineDispatcher = testDispatcher
        }

    private val dataRepository: DataRepository = mockk(relaxed = true)
    private val chatRepository: ChatRepository = mockk(relaxed = true)
    private val chatMessageRepository: ChatMessageRepository = mockk(relaxed = true)
    private val channelRepository: ChannelRepository = mockk()
    private val pinnedMessageRepository: PinnedMessageRepository = mockk(relaxed = true)
    private val getChannelsUseCase: GetChannelsUseCase = mockk()
    private val userBlocksGate: UserBlocksGate = mockk(relaxed = true)

    private lateinit var loader: ChannelDataLoader

    private val testChannel = UserName("testchannel")
    private val testChannelId = UserId("123")
    private val testChannelInfo =
        Channel(
            id = testChannelId,
            name = testChannel,
            displayName = DisplayName("TestChannel"),
            avatarUrl = null,
        )

    @BeforeEach
    fun setup() {
        loader =
            ChannelDataLoader(
                dataRepository = dataRepository,
                chatRepository = chatRepository,
                chatMessageRepository = chatMessageRepository,
                channelRepository = channelRepository,
                pinnedMessageRepository = pinnedMessageRepository,
                getChannelsUseCase = getChannelsUseCase,
                userBlocksGate = userBlocksGate,
                dispatchersProvider = dispatchersProvider,
            )
    }

    private fun stubAllEmotesAndBadgesSuccess() {
        coEvery { dataRepository.loadChannelBadges(testChannel, testChannelId) } returns Result.success(Unit)
        coEvery { dataRepository.loadChannelBTTVEmotes(testChannel, any(), testChannelId) } returns Result.success(EmoteLoadResult.Loaded)
        coEvery { dataRepository.loadChannelFFZEmotes(testChannel, testChannelId) } returns Result.success(EmoteLoadResult.Loaded)
        coEvery { dataRepository.loadChannelSevenTVEmotes(testChannel, testChannelId) } returns Result.success(EmoteLoadResult.Loaded)
        coEvery { dataRepository.loadChannelCheermotes(testChannel, testChannelId) } returns Result.success(Unit)
        coEvery { dataRepository.loadChannelFollowerEmotes(testChannel, testChannelId) } returns Result.success(Unit)
    }

    @Test
    fun `loadChannelData returns Loaded when all steps succeed`() = runTest(testDispatcher) {
        coEvery { channelRepository.getChannel(testChannel) } returns testChannelInfo
        stubAllEmotesAndBadgesSuccess()

        val result = loader.loadChannelData(testChannel)

        assertEquals(ChannelLoadingState.Loaded, result)
    }

    @Test
    fun `loadChannelData returns Failed with empty list when channel info is null`() = runTest(testDispatcher) {
        coEvery { channelRepository.getChannel(testChannel) } returns null
        coEvery { getChannelsUseCase(listOf(testChannel)) } returns emptyList()

        val result = loader.loadChannelData(testChannel)

        assertIs<ChannelLoadingState.Failed>(result)
        assertTrue(result.failures.isEmpty())
    }

    @Test
    fun `loadChannelData falls back to GetChannelsUseCase when channelRepository returns null`() = runTest(testDispatcher) {
        coEvery { channelRepository.getChannel(testChannel) } returns null
        coEvery { getChannelsUseCase(listOf(testChannel)) } returns listOf(testChannelInfo)
        stubAllEmotesAndBadgesSuccess()

        val result = loader.loadChannelData(testChannel)

        assertEquals(ChannelLoadingState.Loaded, result)
        coVerify { getChannelsUseCase(listOf(testChannel)) }
    }

    @Test
    fun `channel is read after a shared lookup has finished`() = runTest(testDispatcher) {
        val channelLookup = CompletableDeferred<Unit>()
        coEvery { channelRepository.getChannel(testChannel) } returns testChannelInfo
        stubAllEmotesAndBadgesSuccess()

        val result = async { loader.loadChannelData(testChannel, channelLookup = channelLookup) }
        coVerify(exactly = 0) { channelRepository.getChannel(testChannel) }

        channelLookup.complete(Unit)

        assertIs<ChannelLoadingState.Loaded>(result.await())
        coVerify(exactly = 1) { channelRepository.getChannel(testChannel) }
    }

    @Test
    fun `loadChannelData returns Failed with BTTV failure`() = runTest(testDispatcher) {
        coEvery { channelRepository.getChannel(testChannel) } returns testChannelInfo
        coEvery { dataRepository.loadChannelBadges(testChannel, testChannelId) } returns Result.success(Unit)
        coEvery { dataRepository.loadChannelBTTVEmotes(testChannel, any(), testChannelId) } returns Result.failure(RuntimeException("bttv down"))
        coEvery { dataRepository.loadChannelFFZEmotes(testChannel, testChannelId) } returns Result.success(EmoteLoadResult.Loaded)
        coEvery { dataRepository.loadChannelSevenTVEmotes(testChannel, testChannelId) } returns Result.success(EmoteLoadResult.Loaded)
        coEvery { dataRepository.loadChannelCheermotes(testChannel, testChannelId) } returns Result.success(Unit)
        coEvery { dataRepository.loadChannelFollowerEmotes(testChannel, testChannelId) } returns Result.success(Unit)

        val result = loader.loadChannelData(testChannel)

        assertIs<ChannelLoadingState.Failed>(result)
        assertEquals(1, result.failures.size)
        assertIs<ChannelLoadingFailure.BTTVEmotes>(result.failures.first())
    }

    @Test
    fun `loadChannelData collects multiple failures`() = runTest(testDispatcher) {
        coEvery { channelRepository.getChannel(testChannel) } returns testChannelInfo
        coEvery { dataRepository.loadChannelBadges(testChannel, testChannelId) } returns Result.failure(RuntimeException("badges down"))
        coEvery { dataRepository.loadChannelBTTVEmotes(testChannel, any(), testChannelId) } returns Result.failure(RuntimeException("bttv down"))
        coEvery { dataRepository.loadChannelFFZEmotes(testChannel, testChannelId) } returns Result.failure(RuntimeException("ffz down"))
        coEvery { dataRepository.loadChannelSevenTVEmotes(testChannel, testChannelId) } returns Result.success(EmoteLoadResult.Loaded)
        coEvery { dataRepository.loadChannelCheermotes(testChannel, testChannelId) } returns Result.success(Unit)
        coEvery { dataRepository.loadChannelFollowerEmotes(testChannel, testChannelId) } returns Result.success(Unit)

        val result = loader.loadChannelData(testChannel)

        assertIs<ChannelLoadingState.Failed>(result)
        assertEquals(3, result.failures.size)
        assertTrue(result.failures.any { it is ChannelLoadingFailure.Badges })
        assertTrue(result.failures.any { it is ChannelLoadingFailure.BTTVEmotes })
        assertTrue(result.failures.any { it is ChannelLoadingFailure.FFZEmotes })
    }

    @Test
    fun `loadChannelData posts system messages for emote failures`() = runTest(testDispatcher) {
        coEvery { channelRepository.getChannel(testChannel) } returns testChannelInfo
        coEvery { dataRepository.loadChannelBadges(testChannel, testChannelId) } returns Result.success(Unit)
        coEvery { dataRepository.loadChannelBTTVEmotes(testChannel, any(), testChannelId) } returns Result.failure(RuntimeException("bttv"))
        coEvery { dataRepository.loadChannelFFZEmotes(testChannel, testChannelId) } returns Result.success(EmoteLoadResult.Loaded)
        coEvery { dataRepository.loadChannelSevenTVEmotes(testChannel, testChannelId) } returns Result.failure(RuntimeException("7tv"))
        coEvery { dataRepository.loadChannelCheermotes(testChannel, testChannelId) } returns Result.success(Unit)
        coEvery { dataRepository.loadChannelFollowerEmotes(testChannel, testChannelId) } returns Result.success(Unit)

        loader.loadChannelData(testChannel)

        coVerify { chatMessageRepository.addSystemMessage(testChannel, match { it is SystemMessageType.ChannelBTTVEmotesFailed }) }
        coVerify { chatMessageRepository.addSystemMessage(testChannel, match { it is SystemMessageType.ChannelSevenTVEmotesFailed }) }
    }

    @Test
    fun `loadChannelData returns Failed on unexpected exception`() = runTest(testDispatcher) {
        coEvery { channelRepository.getChannel(testChannel) } throws RuntimeException("unexpected")

        val result = loader.loadChannelData(testChannel)

        assertIs<ChannelLoadingState.Failed>(result)
        assertTrue(result.failures.isEmpty())
    }

    @Test
    fun `loadChannelData creates flows and loads history before channel info`() = runTest(testDispatcher) {
        coEvery { channelRepository.getChannel(testChannel) } returns testChannelInfo
        stubAllEmotesAndBadgesSuccess()

        loader.loadChannelData(testChannel)

        coVerify(ordering = io.mockk.Ordering.ORDERED) {
            dataRepository.createFlowsIfNecessary(listOf(testChannel))
            chatRepository.createFlowsIfNecessary(testChannel)
            chatRepository.loadRecentMessagesIfEnabled(testChannel)
            channelRepository.getChannel(testChannel)
        }
    }

    @Test
    fun `loadChannelBadges returns null on success`() = runTest(testDispatcher) {
        coEvery { dataRepository.loadChannelBadges(testChannel, testChannelId) } returns Result.success(Unit)

        val result = loader.loadChannelBadges(testChannel, testChannelId)

        assertEquals(null, result)
    }

    @Test
    fun `loadChannelBadges returns failure on error`() = runTest(testDispatcher) {
        coEvery { dataRepository.loadChannelBadges(testChannel, testChannelId) } returns Result.failure(RuntimeException("fail"))

        val result = loader.loadChannelBadges(testChannel, testChannelId)

        assertIs<ChannelLoadingFailure.Badges>(result)
        assertEquals(testChannel, result.channel)
    }
}

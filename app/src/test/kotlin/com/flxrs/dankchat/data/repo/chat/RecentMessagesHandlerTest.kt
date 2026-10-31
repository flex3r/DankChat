package com.flxrs.dankchat.data.repo.chat

import com.flxrs.dankchat.data.api.recentmessages.RecentMessagesApiClient
import com.flxrs.dankchat.data.api.recentmessages.dto.RecentMessagesDto
import com.flxrs.dankchat.data.chat.ChatItem
import com.flxrs.dankchat.data.toUserName
import com.flxrs.dankchat.di.DispatchersProvider
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
internal class RecentMessagesHandlerTest {
    private val testDispatcher = UnconfinedTestDispatcher()
    private val dispatchersProvider =
        object : DispatchersProvider {
            override val default: CoroutineDispatcher = testDispatcher
            override val io: CoroutineDispatcher = testDispatcher
            override val main: CoroutineDispatcher = testDispatcher
            override val immediate: CoroutineDispatcher = testDispatcher
        }

    @Test
    fun `malformed history lines are skipped without failing the load`() = runTest(testDispatcher) {
        val channel = "forsen".toUserName()
        val validLine = "@id=1;tmi-sent-ts=1 :forsen!forsen@forsen.tmi.twitch.tv PRIVMSG #forsen :forsenE"
        val apiClient = mockk<RecentMessagesApiClient> {
            coEvery { getRecentMessages(channel, any()) } returns
                Result.success(RecentMessagesDto(messages = listOf("", "@badges=", validLine), error = null, errorCode = null))
        }
        val messageProcessor = mockk<MessageProcessor> {
            every { isUserBlocked(any()) } returns false
            coEvery { processIrcMessage(any(), any()) } returns null
        }
        val chatMessageRepository = mockk<ChatMessageRepository> {
            every { getMessagesFlow(channel) } returns MutableStateFlow<List<ChatItem>>(emptyList())
            every { scrollBackLength } returns 500
            justRun { handOffHistory(channel, any()) }
        }
        val handler = RecentMessagesHandler(
            recentMessagesApiClient = apiClient,
            messageProcessor = messageProcessor,
            chatMessageRepository = chatMessageRepository,
            usersRepository = mockk(relaxed = true),
            dispatchersProvider = dispatchersProvider,
        )

        handler.load(channel)

        coVerify(exactly = 1) { messageProcessor.processIrcMessage(match { it.command == "PRIVMSG" }, any()) }
    }
}

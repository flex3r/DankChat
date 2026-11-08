package com.flxrs.dankchat.data.repo

import com.flxrs.dankchat.data.auth.AuthDataStore
import com.flxrs.dankchat.data.repo.chat.UsersRepository
import com.flxrs.dankchat.data.toDisplayName
import com.flxrs.dankchat.data.toUserName
import com.flxrs.dankchat.data.twitch.message.PrivMessage
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

internal class RepliesRepositoryTest {
    private val authDataStore: AuthDataStore = mockk { every { userName } returns null }
    private val usersRepository: UsersRepository = mockk { every { getCachedUserColor(any()) } returns null }
    private val repository = RepliesRepository(authDataStore, usersRepository)

    @Test
    fun `reply added while thread is created elsewhere keeps the other processed reply`() = runTest {
        val first = reply(id = "first")
        val second = reply(id = "second")
        val processedSecond = second.copy(message = "processed")

        // The root lookup of the first reply runs while a second reply creates the same thread and finishes processing
        repository.calculateMessageThread(first) { _, _ ->
            repository.calculateMessageThread(second) { _, _ -> null }
            repository.updateMessageInThread(processedSecond)
            null
        }

        val replies = repository.getThreadItemsFlow(ROOT_ID).first().map { it.message as PrivMessage }
        assertEquals(listOf("second", "first"), replies.map { it.id })
        assertEquals("processed", replies.first().message)
    }

    @Test
    fun `same reply processed twice is added once`() = runTest {
        val message = reply(id = "first")

        repository.calculateMessageThread(message) { _, _ -> null }
        repository.calculateMessageThread(message) { _, _ -> null }

        val replies = repository.getThreadItemsFlow(ROOT_ID).first()
        assertEquals(1, replies.size)
    }

    private fun reply(id: String) = PrivMessage(
        id = id,
        channel = "channel".toUserName(),
        sourceChannel = null,
        name = "user".toUserName(),
        displayName = "user".toDisplayName(),
        message = "raw",
        tags =
            mapOf(
                "reply-thread-parent-msg-id" to ROOT_ID,
                "reply-thread-parent-user-login" to "root",
                "reply-parent-msg-id" to "other",
            ),
    )

    private companion object {
        const val ROOT_ID = "root-id"
    }
}

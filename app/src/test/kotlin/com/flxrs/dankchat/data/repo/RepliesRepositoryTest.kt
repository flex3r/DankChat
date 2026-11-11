package com.flxrs.dankchat.data.repo

import com.flxrs.dankchat.data.auth.AuthDataStore
import com.flxrs.dankchat.data.repo.chat.UsersRepository
import com.flxrs.dankchat.data.toDisplayName
import com.flxrs.dankchat.data.toUserName
import com.flxrs.dankchat.data.twitch.message.PrivMessage
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

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

    @Test
    fun `reply removed from the buffer leaves its thread`() = runTest {
        val first = reply(id = "first")
        val second = reply(id = "second")
        repository.calculateMessageThread(first) { _, _ -> null }
        repository.calculateMessageThread(second) { _, _ -> null }

        repository.cleanupMessageThread(first)

        val replies = repository.getThreadItemsFlow(ROOT_ID).first().map { it.message.id }
        assertEquals(listOf("second"), replies)
    }

    @Test
    fun `thread is dropped once its last reply is removed from the buffer`() = runTest {
        val message = reply(id = "first")
        repository.calculateMessageThread(message) { _, _ -> null }

        repository.cleanupMessageThread(message)

        assertFalse(repository.hasMessageThread(ROOT_ID))
    }

    @Test
    fun `thread is dropped once its root is removed from the buffer`() = runTest {
        val root = root()
        repository.calculateMessageThread(reply(id = "first")) { _, _ -> root }

        repository.cleanupMessageThread(root)

        assertFalse(repository.hasMessageThread(ROOT_ID))
    }

    @Test
    fun `observed thread keeps removed replies until it is no longer observed`() = runTest(UnconfinedTestDispatcher()) {
        val first = reply(id = "first")
        val second = reply(id = "second")
        repository.calculateMessageThread(first) { _, _ -> null }
        repository.calculateMessageThread(second) { _, _ -> null }

        val observed = mutableListOf<List<String>>()
        val observer = launch { repository.getThreadItemsFlow(ROOT_ID).collect { items -> observed += items.map { it.message.id } } }
        repository.cleanupMessageThread(first)
        assertEquals(listOf("first", "second"), observed.last())

        observer.cancelAndJoin()

        val replies = repository.getThreadItemsFlow(ROOT_ID).first().map { it.message.id }
        assertEquals(listOf("second"), replies)
    }

    @Test
    fun `observed thread without remaining replies is dropped once it is no longer observed`() = runTest(UnconfinedTestDispatcher()) {
        val message = reply(id = "first")
        repository.calculateMessageThread(message) { _, _ -> null }

        val observer = launch { repository.getThreadItemsFlow(ROOT_ID).collect { } }
        repository.cleanupMessageThread(message)
        assertTrue(repository.hasMessageThread(ROOT_ID))

        observer.cancelAndJoin()

        assertFalse(repository.hasMessageThread(ROOT_ID))
    }

    private fun root() = PrivMessage(
        id = ROOT_ID,
        channel = "channel".toUserName(),
        sourceChannel = null,
        name = "root".toUserName(),
        displayName = "root".toDisplayName(),
        message = "root message",
        tags = emptyMap(),
    )

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

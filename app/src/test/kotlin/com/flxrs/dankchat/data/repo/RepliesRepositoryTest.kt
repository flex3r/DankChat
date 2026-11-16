package com.flxrs.dankchat.data.repo

import com.flxrs.dankchat.data.UserName
import com.flxrs.dankchat.data.auth.AuthDataStore
import com.flxrs.dankchat.data.repo.chat.UsersRepository
import com.flxrs.dankchat.data.toDisplayName
import com.flxrs.dankchat.data.toUserName
import com.flxrs.dankchat.data.twitch.message.Message
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

        // The root lookup of the first reply runs while a second reply creates the same thread and finishes processing
        process(first) { _, _ ->
            process(second, processed = { it.copy(message = "processed") })
            null
        }

        val replies = repository.getThreadItemsFlow(ROOT_ID).first().map { it.message as PrivMessage }
        assertEquals(listOf("second", "first"), replies.map { it.id })
        assertEquals("processed", replies.first().message)
    }

    @Test
    fun `same reply processed twice is added once`() = runTest {
        val message = reply(id = "first")

        process(message)
        process(message)

        val replies = repository.getThreadItemsFlow(ROOT_ID).first()
        assertEquals(1, replies.size)
    }

    @Test
    fun `reply joins its thread only once it is fully processed`() = runTest {
        val message = reply(id = "first")

        val calculated = repository.calculateMessageThread(message) { _, _ -> null } as PrivMessage
        assertFalse(repository.hasMessageThread(ROOT_ID))

        repository.addMessageToThread(calculated.copy(message = "processed")) { _, _ -> null }
        val replies = repository.getThreadItemsFlow(ROOT_ID).first().map { it.message as PrivMessage }
        assertEquals(listOf("processed"), replies.map { it.message })
    }

    @Test
    fun `changed reply gets a new item tag`() = runTest {
        val message = process(reply(id = "first"))

        repository.updateMessageInThread(message)
        assertEquals(
            0,
            repository
                .getThreadItemsFlow(ROOT_ID)
                .first()
                .single()
                .tag,
        )

        repository.updateMessageInThread(message.copy(message = "reparsed"))
        val item = repository.getThreadItemsFlow(ROOT_ID).first().single()
        assertEquals(1, item.tag)
        assertEquals("reparsed", (item.message as PrivMessage).message)
    }

    @Test
    fun `changed root gets a new item tag`() = runTest {
        val root = root()
        process(reply(id = "first")) { _, _ -> root }

        repository.updateMessageInThread(root.copy(message = "reparsed"))

        val rootItem = repository.getThreadItemsFlow(ROOT_ID).first().first()
        assertEquals(ROOT_ID, rootItem.message.id)
        assertEquals(1, rootItem.tag)
    }

    @Test
    fun `reply removed from the buffer leaves its thread`() = runTest {
        val first = process(reply(id = "first"))
        process(reply(id = "second"))

        repository.cleanupMessageThread(first)

        val replies = repository.getThreadItemsFlow(ROOT_ID).first().map { it.message.id }
        assertEquals(listOf("second"), replies)
    }

    @Test
    fun `thread is dropped once its last reply is removed from the buffer`() = runTest {
        val message = process(reply(id = "first"))

        repository.cleanupMessageThread(message)

        assertFalse(repository.hasMessageThread(ROOT_ID))
    }

    @Test
    fun `thread is dropped once its root is removed from the buffer`() = runTest {
        val root = root()
        process(reply(id = "first")) { _, _ -> root }

        repository.cleanupMessageThread(root)

        assertFalse(repository.hasMessageThread(ROOT_ID))
    }

    @Test
    fun `observed thread keeps removed replies until it is no longer observed`() = runTest(UnconfinedTestDispatcher()) {
        val first = process(reply(id = "first"))
        process(reply(id = "second"))

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
        val message = process(reply(id = "first"))

        val observer = launch { repository.getThreadItemsFlow(ROOT_ID).collect { } }
        repository.cleanupMessageThread(message)
        assertTrue(repository.hasMessageThread(ROOT_ID))

        observer.cancelAndJoin()

        assertFalse(repository.hasMessageThread(ROOT_ID))
    }

    // Mirrors the message pipeline: thread header first, the processed message joins the thread last
    private fun process(
        message: PrivMessage,
        processed: (PrivMessage) -> PrivMessage = { it },
        findMessageById: (UserName, String) -> Message? = { _, _ -> null },
    ): PrivMessage {
        val calculated = processed(repository.calculateMessageThread(message, findMessageById) as PrivMessage)
        repository.addMessageToThread(calculated, findMessageById)
        return calculated
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

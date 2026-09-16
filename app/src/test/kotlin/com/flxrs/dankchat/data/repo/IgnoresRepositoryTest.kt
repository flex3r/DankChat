package com.flxrs.dankchat.data.repo

import com.flxrs.dankchat.data.api.helix.HelixApiClient
import com.flxrs.dankchat.data.database.dao.MessageIgnoreDao
import com.flxrs.dankchat.data.database.dao.UserIgnoreDao
import com.flxrs.dankchat.data.database.entity.MessageIgnoreEntity
import com.flxrs.dankchat.data.database.entity.MessageIgnoreEntityType
import com.flxrs.dankchat.data.toDisplayName
import com.flxrs.dankchat.data.toUserName
import com.flxrs.dankchat.data.twitch.message.PrivMessage
import com.flxrs.dankchat.data.twitch.message.TwitchGif
import com.flxrs.dankchat.data.twitch.message.TwitchGifData
import com.flxrs.dankchat.di.DispatchersProvider
import com.flxrs.dankchat.preferences.DankChatPreferenceStore
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
internal class IgnoresRepositoryTest {
    private val testDispatcher = UnconfinedTestDispatcher()
    private val dispatchersProvider =
        object : DispatchersProvider {
            override val default: CoroutineDispatcher = testDispatcher
            override val io: CoroutineDispatcher = testDispatcher
            override val main: CoroutineDispatcher = testDispatcher
            override val immediate: CoroutineDispatcher = testDispatcher
        }

    private val messageIgnoreDao = FakeMessageIgnoreDao()

    private fun createRepository(messageIgnoreDao: MessageIgnoreDao = this.messageIgnoreDao): IgnoresRepository = IgnoresRepository(
        helixApiClient = mockk<HelixApiClient>(),
        messageIgnoreDao = messageIgnoreDao,
        userIgnoreDao = mockk<UserIgnoreDao> { every { getUserIgnoresFlow() } returns flowOf(emptyList()) },
        preferences = mockk<DankChatPreferenceStore>(),
        dispatchersProvider = dispatchersProvider,
    )

    private fun ignoreEntity(
        id: Long,
        type: MessageIgnoreEntityType,
        enabled: Boolean = false,
        pattern: String = "",
        replacement: String? = null,
    ) = MessageIgnoreEntity(id = id, enabled = enabled, type = type, pattern = pattern, replacement = replacement)

    @Test
    fun `all defaults are added to an empty database`() = runTest(testDispatcher) {
        createRepository().runMigrationsIfNeeded().join()

        val ignores = messageIgnoreDao.getMessageIgnores()
        val expectedTypes = MessageIgnoreEntityType.entries - MessageIgnoreEntityType.Custom
        assertEquals(expectedTypes.toSet(), ignores.mapTo(mutableSetOf()) { it.type })
        assertEquals(ignores.size, ignores.mapTo(mutableSetOf()) { it.id }.size)
        assertTrue(ignores.all { it.id > 0 })
    }

    @Test
    fun `missing default types are added without touching existing rows`() = runTest(testDispatcher) {
        val adjustedDefault = ignoreEntity(id = 1, type = MessageIgnoreEntityType.Subscription, enabled = true)
        val custom = ignoreEntity(id = 2, type = MessageIgnoreEntityType.Custom, pattern = "dank")
        messageIgnoreDao.seed(adjustedDefault, custom)

        createRepository().runMigrationsIfNeeded().join()

        val ignores = messageIgnoreDao.getMessageIgnores()
        assertEquals(adjustedDefault, ignores.first { it.type == MessageIgnoreEntityType.Subscription })
        assertEquals(custom, ignores.first { it.type == MessageIgnoreEntityType.Custom })
        val expectedTypes = MessageIgnoreEntityType.entries.toSet()
        assertEquals(expectedTypes, ignores.mapTo(mutableSetOf()) { it.type })
        assertEquals(ignores.size, ignores.mapTo(mutableSetOf()) { it.id }.size)
    }

    @Test
    fun `duplicate rows of a non-custom type are removed keeping the first`() = runTest(testDispatcher) {
        val first = ignoreEntity(id = 1, type = MessageIgnoreEntityType.Subscription, enabled = true)
        val duplicate = ignoreEntity(id = 5, type = MessageIgnoreEntityType.Subscription)
        messageIgnoreDao.seed(first, duplicate)

        createRepository().runMigrationsIfNeeded().join()

        val subscriptions = messageIgnoreDao.getMessageIgnores().filter { it.type == MessageIgnoreEntityType.Subscription }
        assertEquals(listOf(first), subscriptions)
    }

    @Test
    fun `custom ignores are never deduplicated`() = runTest(testDispatcher) {
        val custom = ignoreEntity(id = 1, type = MessageIgnoreEntityType.Custom, pattern = "dank")
        val sameContent = ignoreEntity(id = 2, type = MessageIgnoreEntityType.Custom, pattern = "dank")
        messageIgnoreDao.seed(custom, sameContent)

        createRepository().runMigrationsIfNeeded().join()

        val customs = messageIgnoreDao.getMessageIgnores().filter { it.type == MessageIgnoreEntityType.Custom }
        assertEquals(listOf(custom, sameContent), customs)
    }

    @Test
    fun `running the migration twice changes nothing`() = runTest(testDispatcher) {
        messageIgnoreDao.seed(ignoreEntity(id = 1, type = MessageIgnoreEntityType.Subscription, enabled = true))
        val repository = createRepository()

        repository.runMigrationsIfNeeded().join()
        val afterFirstRun = messageIgnoreDao.getMessageIgnores()
        repository.runMigrationsIfNeeded().join()

        assertEquals(afterFirstRun, messageIgnoreDao.getMessageIgnores())
    }

    @Test
    fun `literal replacement escaping does not shift gifs by the escaped length`() = runTest(testDispatcher) {
        listOf("$", "\\").forEachIndexed { index, replacement ->
            val dao = FakeMessageIgnoreDao()
            dao.seed(
                ignoreEntity(
                    id = index + 1L,
                    enabled = true,
                    type = MessageIgnoreEntityType.Custom,
                    pattern = "x",
                    replacement = replacement,
                ),
            )
            val repository = createRepository(dao)
            val source = "x [GIF]"
            val gif = TwitchGif("gif", "https://example.com/a.gif", "[GIF]", 2..6)
            val message =
                PrivMessage(
                    channel = "forsen".toUserName(),
                    sourceChannel = null,
                    name = "forsen".toUserName(),
                    displayName = "forsen".toDisplayName(),
                    message = source,
                    tags = emptyMap(),
                    gifs = listOf(gif),
                    gifData = TwitchGifData(source, listOf(gif)),
                )

            val filtered = assertIs<PrivMessage>(repository.applyIgnores(message))

            assertEquals("$replacement [GIF]", filtered.message)
            assertEquals(2..6, filtered.gifs.single().position)
        }
    }

    @Test
    fun `replacement intersecting gif fallback removes the gif`() = runTest(testDispatcher) {
        messageIgnoreDao.seed(
            ignoreEntity(
                id = 1,
                enabled = true,
                type = MessageIgnoreEntityType.Custom,
                pattern = "GIF",
                replacement = "image",
            ),
        )
        val repository = createRepository()
        val source = "before [GIF] after"
        val gif = TwitchGif("gif", "https://example.com/a.gif", "[GIF]", 7..11)
        val message =
            PrivMessage(
                channel = "forsen".toUserName(),
                sourceChannel = null,
                name = "forsen".toUserName(),
                displayName = "forsen".toDisplayName(),
                message = source,
                tags = emptyMap(),
                gifs = listOf(gif),
                gifData = TwitchGifData(source, listOf(gif)),
            )

        val filtered = assertIs<PrivMessage>(repository.applyIgnores(message))

        assertEquals("before [image] after", filtered.message)
        assertTrue(filtered.gifs.isEmpty())
    }
}

private class FakeMessageIgnoreDao : MessageIgnoreDao {
    private val ignores = mutableListOf<MessageIgnoreEntity>()

    fun seed(vararg entities: MessageIgnoreEntity) {
        ignores += entities
    }

    override suspend fun getMessageIgnore(id: Long): MessageIgnoreEntity = ignores.first { it.id == id }

    override suspend fun getMessageIgnores(): List<MessageIgnoreEntity> = ignores.toList()

    override fun getMessageIgnoresFlow(): Flow<List<MessageIgnoreEntity>> = flowOf(ignores.toList())

    override suspend fun addIgnore(ignore: MessageIgnoreEntity): Long = upsert(ignore)

    override suspend fun addIgnores(ignores: List<MessageIgnoreEntity>) {
        ignores.forEach { upsert(it) }
    }

    override suspend fun deleteIgnore(ignore: MessageIgnoreEntity) {
        ignores.removeAll { it.id == ignore.id }
    }

    override suspend fun deleteAllIgnores() = ignores.clear()

    // Mirrors Room @Upsert with an autoGenerate primary key: id 0 inserts a new row with a generated id
    private fun upsert(entity: MessageIgnoreEntity): Long = when (entity.id) {
        0L -> {
            val id = (ignores.maxOfOrNull { it.id } ?: 0) + 1
            ignores += entity.copy(id = id)
            id
        }

        else -> {
            val index = ignores.indexOfFirst { it.id == entity.id }
            when {
                index >= 0 -> ignores[index] = entity
                else -> ignores += entity
            }
            entity.id
        }
    }
}

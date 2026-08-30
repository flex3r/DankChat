package com.flxrs.dankchat.data.repo

import com.flxrs.dankchat.data.UserName
import com.flxrs.dankchat.data.database.dao.BadgeHighlightDao
import com.flxrs.dankchat.data.database.dao.BlacklistedUserDao
import com.flxrs.dankchat.data.database.dao.MessageHighlightDao
import com.flxrs.dankchat.data.database.dao.UserHighlightDao
import com.flxrs.dankchat.data.database.entity.BadgeHighlightEntity
import com.flxrs.dankchat.data.database.entity.BlacklistedUserEntity
import com.flxrs.dankchat.data.database.entity.MessageHighlightEntity
import com.flxrs.dankchat.data.database.entity.MessageHighlightEntityType
import com.flxrs.dankchat.di.DispatchersProvider
import com.flxrs.dankchat.preferences.DankChatPreferenceStore
import com.flxrs.dankchat.preferences.notifications.NotificationsSettingsDataStore
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
internal class HighlightsRepositoryTest {
    private val testDispatcher = UnconfinedTestDispatcher()
    private val dispatchersProvider =
        object : DispatchersProvider {
            override val default: CoroutineDispatcher = testDispatcher
            override val io: CoroutineDispatcher = testDispatcher
            override val main: CoroutineDispatcher = testDispatcher
            override val immediate: CoroutineDispatcher = testDispatcher
        }

    private val messageHighlightDao = FakeMessageHighlightDao()
    private val blacklistedUsers = MutableStateFlow<List<BlacklistedUserEntity>>(emptyList())
    private val blacklistedUserDao =
        mockk<BlacklistedUserDao> {
            every { getBlacklistedUserFlow() } returns blacklistedUsers
            coEvery { getExactBlacklistedUsers(any()) } answers {
                val username = firstArg<String>()
                blacklistedUsers.value.filter { !it.isRegex && it.username.equals(username, ignoreCase = true) }
            }
            coEvery { addBlacklistedUser(any()) } answers {
                val user = firstArg<BlacklistedUserEntity>()
                val id = user.id.takeIf { it != 0L } ?: ((blacklistedUsers.value.maxOfOrNull { it.id } ?: 0L) + 1L)
                blacklistedUsers.value = blacklistedUsers.value.filterNot { it.id == id } + user.copy(id = id)
                id
            }
            coEvery { addBlacklistedUsers(any()) } answers {
                firstArg<List<BlacklistedUserEntity>>().forEach { user ->
                    val id = user.id.takeIf { it != 0L } ?: ((blacklistedUsers.value.maxOfOrNull { it.id } ?: 0L) + 1L)
                    blacklistedUsers.value = blacklistedUsers.value.filterNot { it.id == id } + user.copy(id = id)
                }
            }
            coEvery { deleteExactBlacklistedUsers(any()) } answers {
                val username = firstArg<String>()
                blacklistedUsers.value = blacklistedUsers.value.filterNot { !it.isRegex && it.username.equals(username, ignoreCase = true) }
            }
        }
    private val badgeHighlightDao =
        mockk<BadgeHighlightDao> {
            every { getBadgeHighlightsFlow() } returns flowOf(emptyList())
            coEvery { getBadgeHighlights() } returns listOf(mockk<BadgeHighlightEntity>())
        }

    private fun createRepository(): HighlightsRepository = HighlightsRepository(
        messageHighlightDao = messageHighlightDao,
        userHighlightDao = mockk<UserHighlightDao> { every { getUserHighlightsFlow() } returns flowOf(emptyList()) },
        badgeHighlightDao = badgeHighlightDao,
        blacklistedUserDao = blacklistedUserDao,
        preferences = mockk<DankChatPreferenceStore> { every { currentUserAndDisplayFlow } returns emptyFlow() },
        notificationsSettingsDataStore = mockk<NotificationsSettingsDataStore>(),
        dispatchersProvider = dispatchersProvider,
    )

    private fun highlightEntity(
        id: Long,
        type: MessageHighlightEntityType,
        enabled: Boolean = true,
        pattern: String = "",
    ) = MessageHighlightEntity(id = id, enabled = enabled, type = type, pattern = pattern)

    @Test
    fun `all defaults are added to an empty database`() = runTest(testDispatcher) {
        createRepository().runMigrationsIfNeeded().join()

        val highlights = messageHighlightDao.getMessageHighlights()
        val expectedTypes = MessageHighlightEntityType.entries - MessageHighlightEntityType.Custom
        assertEquals(expectedTypes.toSet(), highlights.mapTo(mutableSetOf()) { it.type })
        assertEquals(highlights.size, highlights.mapTo(mutableSetOf()) { it.id }.size)
        assertTrue(highlights.all { it.id > 0 })
    }

    @Test
    fun `missing default types are added without touching existing rows`() = runTest(testDispatcher) {
        val adjustedDefault = highlightEntity(id = 1, type = MessageHighlightEntityType.Subscription, enabled = false)
        val custom = highlightEntity(id = 2, type = MessageHighlightEntityType.Custom, pattern = "dank")
        messageHighlightDao.seed(adjustedDefault, custom)

        createRepository().runMigrationsIfNeeded().join()

        val highlights = messageHighlightDao.getMessageHighlights()
        assertEquals(adjustedDefault, highlights.first { it.type == MessageHighlightEntityType.Subscription })
        assertEquals(custom, highlights.first { it.type == MessageHighlightEntityType.Custom })
        val expectedTypes = MessageHighlightEntityType.entries.toSet()
        assertEquals(expectedTypes, highlights.mapTo(mutableSetOf()) { it.type })
        assertEquals(highlights.size, highlights.mapTo(mutableSetOf()) { it.id }.size)
    }

    @Test
    fun `duplicate rows of a non-custom type are removed keeping the first`() = runTest(testDispatcher) {
        val first = highlightEntity(id = 1, type = MessageHighlightEntityType.Username, enabled = false)
        val duplicate = highlightEntity(id = 7, type = MessageHighlightEntityType.Username)
        messageHighlightDao.seed(first, duplicate)

        createRepository().runMigrationsIfNeeded().join()

        val usernames = messageHighlightDao.getMessageHighlights().filter { it.type == MessageHighlightEntityType.Username }
        assertEquals(listOf(first), usernames)
    }

    @Test
    fun `custom highlights are never deduplicated`() = runTest(testDispatcher) {
        val custom = highlightEntity(id = 1, type = MessageHighlightEntityType.Custom, pattern = "dank")
        val sameContent = highlightEntity(id = 2, type = MessageHighlightEntityType.Custom, pattern = "dank")
        messageHighlightDao.seed(custom, sameContent)

        createRepository().runMigrationsIfNeeded().join()

        val customs = messageHighlightDao.getMessageHighlights().filter { it.type == MessageHighlightEntityType.Custom }
        assertEquals(listOf(custom, sameContent), customs)
    }

    @Test
    fun `running the migration twice changes nothing`() = runTest(testDispatcher) {
        messageHighlightDao.seed(highlightEntity(id = 1, type = MessageHighlightEntityType.Subscription, enabled = false))
        val repository = createRepository()

        repository.runMigrationsIfNeeded().join()
        val afterFirstRun = messageHighlightDao.getMessageHighlights()
        repository.runMigrationsIfNeeded().join()

        assertEquals(afterFirstRun, messageHighlightDao.getMessageHighlights())
    }

    @Test
    fun `badge highlight defaults are not added to a non-empty table`() = runTest(testDispatcher) {
        createRepository().runMigrationsIfNeeded().join()

        coVerify(exactly = 0) { badgeHighlightDao.addHighlights(any()) }
    }

    @Test
    fun `ignoring highlights enables all case insensitive exact entries`() = runTest(testDispatcher) {
        blacklistedUsers.value =
            listOf(
                BlacklistedUserEntity(id = 1, enabled = true, username = "for.*", isRegex = true),
                BlacklistedUserEntity(id = 2, enabled = false, username = "forsen"),
                BlacklistedUserEntity(id = 3, enabled = false, username = "FORSEN"),
            )
        val repository = createRepository()

        assertFalse(repository.isUserHighlightsIgnored(UserName("forsen")))

        repository.setUserHighlightsIgnored(UserName("forsen"), true)

        assertTrue(repository.isUserHighlightsIgnored(UserName("forsen")))
        assertEquals(3, blacklistedUsers.value.size)
        assertTrue(blacklistedUsers.value.filterNot { it.isRegex }.all { it.enabled })
    }

    @Test
    fun `ignoring highlights adds an exact entry when none exists`() = runTest(testDispatcher) {
        val regex = BlacklistedUserEntity(id = 1, enabled = true, username = "for.*", isRegex = true)
        blacklistedUsers.value = listOf(regex)
        val repository = createRepository()

        repository.setUserHighlightsIgnored(UserName("forsen"), true)

        assertEquals(
            listOf(regex, BlacklistedUserEntity(id = 2, enabled = true, username = "forsen")),
            blacklistedUsers.value,
        )
    }

    @Test
    fun `allowing highlights removes only exact entries for that user`() = runTest(testDispatcher) {
        val regex = BlacklistedUserEntity(id = 1, enabled = true, username = "for.*", isRegex = true)
        val otherUser = BlacklistedUserEntity(id = 2, enabled = true, username = "Iore")
        blacklistedUsers.value =
            listOf(
                regex,
                otherUser,
                BlacklistedUserEntity(id = 3, enabled = true, username = "forsen"),
                BlacklistedUserEntity(id = 4, enabled = false, username = "FORSEN"),
            )
        val repository = createRepository()

        repository.setUserHighlightsIgnored(UserName("Forsen"), false)

        assertEquals(listOf(regex, otherUser), blacklistedUsers.value)
    }
}

private class FakeMessageHighlightDao : MessageHighlightDao {
    private val highlights = mutableListOf<MessageHighlightEntity>()

    fun seed(vararg entities: MessageHighlightEntity) {
        highlights += entities
    }

    override suspend fun getMessageHighlight(id: Long): MessageHighlightEntity = highlights.first { it.id == id }

    override suspend fun getMessageHighlights(): List<MessageHighlightEntity> = highlights.toList()

    override fun getMessageHighlightsFlow(): Flow<List<MessageHighlightEntity>> = flowOf(highlights.toList())

    override suspend fun addHighlight(highlight: MessageHighlightEntity): Long = upsert(highlight)

    override suspend fun addHighlights(highlights: List<MessageHighlightEntity>) {
        highlights.forEach { upsert(it) }
    }

    override suspend fun deleteHighlight(highlight: MessageHighlightEntity) {
        highlights.removeAll { it.id == highlight.id }
    }

    override suspend fun deleteAllHighlights() = highlights.clear()

    // Mirrors Room @Upsert with an autoGenerate primary key: id 0 inserts a new row with a generated id
    private fun upsert(entity: MessageHighlightEntity): Long = when (entity.id) {
        0L -> {
            val id = (highlights.maxOfOrNull { it.id } ?: 0) + 1
            highlights += entity.copy(id = id)
            id
        }

        else -> {
            val index = highlights.indexOfFirst { it.id == entity.id }
            when {
                index >= 0 -> highlights[index] = entity
                else -> highlights += entity
            }
            entity.id
        }
    }
}

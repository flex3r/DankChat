package com.flxrs.dankchat.data.repo

import com.flxrs.dankchat.data.DisplayName
import com.flxrs.dankchat.data.UserName
import com.flxrs.dankchat.data.database.dao.UserDisplayDao
import com.flxrs.dankchat.data.database.entity.UserDisplayEntity
import com.flxrs.dankchat.data.twitch.message.PrivMessage
import com.flxrs.dankchat.di.DispatchersProvider
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
internal class UserDisplayRepositoryTest {
    private val userDisplayDao: UserDisplayDao = mockk()
    private val dispatchersProvider: DispatchersProvider = mockk { every { default } returns UnconfinedTestDispatcher() }

    @Test
    fun `disabled entry does not hide a later enabled entry of the same user`() {
        val repository = createRepository(entry(id = 1, enabled = false, alias = "first"), entry(id = 2, enabled = true, alias = "second"))

        val message = assertIs<PrivMessage>(repository.calculateUserDisplay(message()))

        assertEquals(expected = "second", actual = message.userDisplay?.alias)
    }

    @Test
    fun `user with only a disabled entry has no user display`() {
        val repository = createRepository(entry(id = 1, enabled = false, alias = "first"))

        val message = assertIs<PrivMessage>(repository.calculateUserDisplay(message()))

        assertNull(message.userDisplay)
    }

    private fun createRepository(vararg entries: UserDisplayEntity): UserDisplayRepository {
        every { userDisplayDao.getUserDisplaysFlow() } returns flowOf(entries.toList())
        return UserDisplayRepository(userDisplayDao, dispatchersProvider)
    }

    private fun entry(
        id: Int,
        enabled: Boolean,
        alias: String,
    ) = UserDisplayEntity(
        id = id,
        targetUser = "User",
        enabled = enabled,
        colorEnabled = false,
        color = 0,
        aliasEnabled = true,
        alias = alias,
    )

    private fun message() = PrivMessage(
        channel = UserName("channel"),
        sourceChannel = null,
        name = UserName("user"),
        displayName = DisplayName("User"),
        message = "message",
        tags = emptyMap(),
    )
}

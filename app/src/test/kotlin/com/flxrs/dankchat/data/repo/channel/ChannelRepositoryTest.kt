package com.flxrs.dankchat.data.repo.channel

import com.flxrs.dankchat.data.DisplayName
import com.flxrs.dankchat.data.UserId
import com.flxrs.dankchat.data.UserName
import com.flxrs.dankchat.data.api.helix.HelixApiClient
import com.flxrs.dankchat.data.api.helix.dto.UserDto
import com.flxrs.dankchat.data.auth.AuthDataStore
import com.flxrs.dankchat.data.irc.IrcMessage
import com.flxrs.dankchat.data.repo.chat.UsersRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

internal class ChannelRepositoryTest {
    private val channel = UserName("forsen")
    private val userDto =
        UserDto(
            id = UserId("22484632"),
            name = channel,
            displayName = DisplayName("Forsen"),
            type = "",
            broadcasterType = "partner",
            description = "",
            avatarUrl = "https://example.com/avatar.png",
            offlineImageUrl = "",
            viewCount = 0,
            createdAt = "",
        )
    private val usersRepository: UsersRepository = mockk()
    private val helixApiClient: HelixApiClient = mockk()
    private val authDataStore: AuthDataStore = mockk()
    private val repository = ChannelRepository(usersRepository, helixApiClient, authDataStore, dispatchersProvider = mockk())

    @Test
    fun `channel from the room state is replaced once the lookup works`() = runTest {
        every { authDataStore.isLoggedIn } returns true
        every { usersRepository.findDisplayName(channel, channel) } returns null
        coEvery { helixApiClient.getUserByName(channel) } returns Result.failure(IllegalStateException("timeout")) andThen Result.success(userDto)
        repository.initRoomState(channel)
        repository.handleRoomState(IrcMessage.parse("@room-id=22484632 :tmi.twitch.tv ROOMSTATE #forsen"))

        val fallback = repository.getChannel(channel)
        val resolved = repository.getChannel(channel)

        assertNull(fallback?.avatarUrl)
        assertEquals(expected = "https://example.com/avatar.png", actual = resolved?.avatarUrl)
        assertEquals(expected = DisplayName("Forsen"), actual = resolved?.displayName)
    }

    @Test
    fun `resolved channel is cached`() = runTest {
        every { authDataStore.isLoggedIn } returns true
        coEvery { helixApiClient.getUserByName(channel) } returns Result.success(userDto)

        repository.getChannel(channel)
        repository.getChannel(channel)

        coVerify(exactly = 1) { helixApiClient.getUserByName(channel) }
    }
}

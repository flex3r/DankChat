package com.flxrs.dankchat.data.twitch.command

import com.flxrs.dankchat.R
import com.flxrs.dankchat.data.api.helix.HelixApiClient
import com.flxrs.dankchat.data.api.helix.dto.UserDto
import com.flxrs.dankchat.data.api.helix.dto.WarnRequestDataDto
import com.flxrs.dankchat.data.api.helix.dto.WarnRequestDto
import com.flxrs.dankchat.data.auth.AuthDataStore
import com.flxrs.dankchat.data.repo.ShieldModeRepository
import com.flxrs.dankchat.data.repo.command.CommandResult
import com.flxrs.dankchat.data.toDisplayName
import com.flxrs.dankchat.data.toUserId
import com.flxrs.dankchat.data.toUserName
import com.flxrs.dankchat.data.twitch.message.RoomState
import com.flxrs.dankchat.utils.TextResource
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

internal class WarnCommandTest {
    private val helixApiClient: HelixApiClient = mockk()
    private val repository = TwitchCommandRepository(
        helixApiClient = helixApiClient,
        authDataStore = mockk<AuthDataStore> {
            every { userIdString } returns "moderator-id".toUserId()
        },
        shieldModeRepository = mockk<ShieldModeRepository>(),
    )

    @Test
    fun `warn requires a reason`() = runTest {
        val result = repository.handleTwitchCommand(
            TwitchCommand.Warn,
            context(args = listOf("forsen")),
        )

        assertEquals(
            CommandResult.AcceptedTwitchCommand(
                TwitchCommand.Warn,
                TextResource.Res(R.string.cmd_usage_warn, persistentListOf("/warn")),
            ),
            result,
        )
        coVerify(exactly = 0) { helixApiClient.getUserByName(any()) }
    }

    @Test
    fun `warn sends the full reason`() = runTest {
        val target = mockk<UserDto> {
            every { id } returns "target-id".toUserId()
            every { displayName } returns "Forsen".toDisplayName()
        }
        coEvery { helixApiClient.getUserByName("forsen".toUserName()) } returns Result.success(target)
        coEvery {
            helixApiClient.postWarning(
                "channel-id".toUserId(),
                "moderator-id".toUserId(),
                WarnRequestDto(WarnRequestDataDto("target-id".toUserId(), "stop doing that")),
            )
        } returns Result.success(Unit)

        val result = repository.handleTwitchCommand(
            TwitchCommand.Warn,
            context(args = listOf("forsen", "stop", "doing", "that")),
        )

        assertEquals(CommandResult.AcceptedTwitchCommand(TwitchCommand.Warn), result)
    }

    private fun context(args: List<String>): CommandContext {
        val channel = "channel".toUserName()
        val channelId = "channel-id".toUserId()
        return CommandContext(
            trigger = "/warn",
            channel = channel,
            channelId = channelId,
            roomState = RoomState(channel, channelId),
            originalMessage = (listOf("/warn") + args).joinToString(" "),
            args = args,
        )
    }
}

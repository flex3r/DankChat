package com.flxrs.dankchat.data.twitch.command

import com.flxrs.dankchat.R
import com.flxrs.dankchat.data.api.helix.HelixApiClient
import com.flxrs.dankchat.data.api.helix.dto.ModifyChannelRequestDto
import com.flxrs.dankchat.data.api.helix.dto.StreamCategoryDto
import com.flxrs.dankchat.data.auth.AuthDataStore
import com.flxrs.dankchat.data.repo.ShieldModeRepository
import com.flxrs.dankchat.data.repo.command.CommandResult
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

internal class TwitchCommandRepositoryTest {
    private val helixApiClient: HelixApiClient = mockk()
    private val authDataStore: AuthDataStore = mockk {
        every { userIdString } returns "current-user".toUserId()
    }
    private val repository = TwitchCommandRepository(
        helixApiClient = helixApiClient,
        authDataStore = authDataStore,
        shieldModeRepository = mockk(),
    )

    @Test
    fun `settitle updates the channel with the full title`() = runTest {
        coEvery {
            helixApiClient.patchChannel("channel-id".toUserId(), ModifyChannelRequestDto(title = "A new title"))
        } returns Result.success(Unit)

        val result = repository.handleTwitchCommand(
            TwitchCommand.SetTitle,
            context(trigger = "/settitle", args = listOf("A", "new", "title")),
        )

        assertEquals(
            CommandResult.AcceptedTwitchCommand(
                TwitchCommand.SetTitle,
                TextResource.Res(R.string.cmd_success_set_title, persistentListOf("A new title")),
            ),
            result,
        )
    }

    @Test
    fun `setgame prefers an exact case insensitive category match`() = runTest {
        val partial = StreamCategoryDto(id = "1", name = "Just Chatting Together")
        val exact = StreamCategoryDto(id = "2", name = "Just Chatting")
        coEvery { helixApiClient.searchCategories("just chatting") } returns Result.success(listOf(partial, exact))
        coEvery {
            helixApiClient.patchChannel("channel-id".toUserId(), ModifyChannelRequestDto(gameId = exact.id))
        } returns Result.success(Unit)

        val result = repository.handleTwitchCommand(
            TwitchCommand.SetGame,
            context(trigger = "/setgame", args = listOf("just", "chatting")),
        )

        assertEquals(
            CommandResult.AcceptedTwitchCommand(
                TwitchCommand.SetGame,
                TextResource.Res(R.string.cmd_success_set_game, persistentListOf(exact.name)),
            ),
            result,
        )
        coVerify(exactly = 1) {
            helixApiClient.patchChannel("channel-id".toUserId(), ModifyChannelRequestDto(gameId = exact.id))
        }
    }

    @Test
    fun `setgame reports when no category matches`() = runTest {
        coEvery { helixApiClient.searchCategories("missing game") } returns Result.success(emptyList())

        val result = repository.handleTwitchCommand(
            TwitchCommand.SetGame,
            context(trigger = "/setgame", args = listOf("missing", "game")),
        )

        assertEquals(
            CommandResult.AcceptedTwitchCommand(
                TwitchCommand.SetGame,
                TextResource.Res(R.string.cmd_game_not_found),
            ),
            result,
        )
        coVerify(exactly = 0) { helixApiClient.patchChannel(any(), any()) }
    }

    private fun context(
        trigger: String,
        args: List<String>,
    ): CommandContext {
        val channel = "forsen".toUserName()
        val channelId = "channel-id".toUserId()
        return CommandContext(
            trigger = trigger,
            channel = channel,
            channelId = channelId,
            roomState = RoomState(channel, channelId),
            originalMessage = (listOf(trigger) + args).joinToString(" "),
            args = args,
        )
    }
}

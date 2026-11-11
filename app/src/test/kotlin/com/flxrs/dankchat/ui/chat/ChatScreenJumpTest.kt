package com.flxrs.dankchat.ui.chat

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.v2.createComposeRule
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class ChatScreenJumpTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val scrollState = ChatScrollState()
    private var messages by mutableStateOf(messages(count = 40))
    private var handledCount = 0

    @Test
    fun `jumps to a target that is in the list`() {
        setContent(targetId = "message-5")
        composeRule.waitForIdle()

        assertEquals(expected = 1, actual = handledCount)
        assertFalse(scrollState.shouldAutoScroll)
    }

    @Test
    fun `keeps a missing target until the list contains it`() {
        setContent(targetId = "message-45")
        composeRule.waitForIdle()

        assertEquals(expected = 0, actual = handledCount)
        assertTrue(scrollState.shouldAutoScroll)

        messages = messages(count = 60)
        composeRule.waitForIdle()

        assertEquals(expected = 1, actual = handledCount)
        assertFalse(scrollState.shouldAutoScroll)
    }

    @Test
    fun `waits for messages before giving up on a target`() {
        messages = messages(count = 0)
        setContent(targetId = "message-5")
        composeRule.mainClock.advanceTimeBy(GIVE_UP_MILLIS)

        assertEquals(expected = 0, actual = handledCount)

        messages = messages(count = 40)
        composeRule.waitForIdle()

        assertEquals(expected = 1, actual = handledCount)
        assertFalse(scrollState.shouldAutoScroll)
    }

    @Test
    fun `gives up on a target that does not show up`() {
        setContent(targetId = "message-45")
        composeRule.mainClock.advanceTimeBy(GIVE_UP_MILLIS)

        assertEquals(expected = 1, actual = handledCount)
        assertTrue(scrollState.shouldAutoScroll)
    }

    private fun setContent(targetId: String) {
        composeRule.setContent {
            ChatScreen(
                messages = messages,
                fontSize = 14f,
                callbacks = ChatScreenCallbacks(
                    onUserClick = { _, _, _, _, _, _ -> },
                    onMessageLongClick = { _, _, _ -> },
                ),
                scrollState = scrollState,
                showFabs = false,
                scrollToMessageId = targetId,
                onScrollToMessageHandle = { handledCount++ },
            )
        }
    }

    private fun messages(count: Int): ImmutableList<ChatMessageUiState> = List(count) { index ->
        ChatMessageUiState.NoticeMessageUi(
            id = "message-$index",
            tag = 0,
            timestamp = "",
            lightBackgroundColor = Color.Transparent,
            darkBackgroundColor = Color.Transparent,
            textAlpha = 1f,
            message = "Notice $index",
        )
    }.toImmutableList()

    private companion object {
        const val GIVE_UP_MILLIS = 2_500L
    }
}

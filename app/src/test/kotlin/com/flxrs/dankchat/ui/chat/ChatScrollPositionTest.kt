package com.flxrs.dankchat.ui.chat

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

internal class ChatScrollPositionTest {
    @Test
    fun `resolves the anchor after its index changes`() {
        val position = ChatScrollPosition(firstVisibleItemId = "anchor", firstVisibleItemIndex = 1)

        val index = position.resolveFirstVisibleItemIndex(
            items = listOf("new-2", "new-1", "latest", "anchor", "older"),
            itemId = { it },
        )

        assertEquals(expected = 3, actual = index)
    }

    @Test
    fun `uses the saved index when the anchor was removed`() {
        val position = ChatScrollPosition(firstVisibleItemId = "removed", firstVisibleItemIndex = 2)

        val index = position.resolveFirstVisibleItemIndex(
            items = listOf("latest", "older-1", "older-2", "older-3"),
            itemId = { it },
        )

        assertEquals(expected = 2, actual = index)
    }

    @Test
    fun `clamps the saved index when scrollback shrank`() {
        val position = ChatScrollPosition(firstVisibleItemId = "removed", firstVisibleItemIndex = 20)

        val index = position.resolveFirstVisibleItemIndex(
            items = listOf("latest", "oldest"),
            itemId = { it },
        )

        assertEquals(expected = 1, actual = index)
    }
}

package com.flxrs.dankchat.ui.main

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

internal class CircularChatPagerTest {
    @Test
    fun `multiple channels provide pages for continuous swiping`() {
        assertEquals(expected = Int.MAX_VALUE, actual = circularPagerPageCount(channelCount = 2))
        assertEquals(expected = Int.MAX_VALUE, actual = circularPagerPageCount(channelCount = 10))
    }

    @Test
    fun `zero or one channel does not create repeating pages`() {
        assertEquals(expected = 0, actual = circularPagerPageCount(channelCount = 0))
        assertEquals(expected = 1, actual = circularPagerPageCount(channelCount = 1))
    }

    @Test
    fun `initial page maps to the selected channel near the middle`() {
        val initialPage = initialCircularPagerPage(channelIndex = 3, channelCount = 5)

        assertEquals(expected = 3, actual = circularPageToChannelIndex(initialPage, channelCount = 5))
        assertTrue(initialPage in (Int.MAX_VALUE / 2 - 4)..(Int.MAX_VALUE / 2 + 4))
    }

    @Test
    fun `pages wrap in both directions`() {
        val firstPage = initialCircularPagerPage(channelIndex = 0, channelCount = 4)

        assertEquals(expected = 3, actual = circularPageToChannelIndex(firstPage - 1, channelCount = 4))
        assertEquals(expected = 0, actual = circularPageToChannelIndex(firstPage, channelCount = 4))
        assertEquals(expected = 1, actual = circularPageToChannelIndex(firstPage + 1, channelCount = 4))
    }

    @Test
    fun `tab selection chooses the nearest circular page`() {
        val firstPage = initialCircularPagerPage(channelIndex = 0, channelCount = 5)
        val lastPage = firstPage - 1

        assertEquals(
            expected = lastPage,
            actual = closestCircularPagerPage(firstPage, channelIndex = 4, channelCount = 5),
        )
        assertEquals(
            expected = firstPage,
            actual = closestCircularPagerPage(lastPage, channelIndex = 0, channelCount = 5),
        )
    }
}

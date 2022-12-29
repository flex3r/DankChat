package com.flxrs.dankchat.utils

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals


internal class DateTimeUtilsTest {

    @Test
    fun `formats 10 seconds correctly`() {
        val result = DateTimeUtils.formatSeconds(10)
        assertEquals(expected = "10s", actual = result)
    }

    @Test
    fun `formats 100 seconds correctly`() {
        val result = DateTimeUtils.formatSeconds(100)
        assertEquals(expected = "1m 40s", actual = result)
    }

    @Test
    fun `formats 1000 seconds correctly`() {
        val result = DateTimeUtils.formatSeconds(1000)
        assertEquals(expected = "16m 40s", actual = result)
    }

    @Test
    fun `formats 10000 seconds correctly`() {
        val result = DateTimeUtils.formatSeconds(10000)
        assertEquals(expected = "2h 46m 40s", actual = result)
    }

    @Test
    fun `formats 604800 seconds correctly`() {
        val result = DateTimeUtils.formatSeconds(604800)
        assertEquals(expected = "7d", actual = result)
    }

    @Test
    fun `formats 1209600 seconds correctly`() {
        val result = DateTimeUtils.formatSeconds(1209600)
        assertEquals(expected = "14d", actual = result)
    }
}
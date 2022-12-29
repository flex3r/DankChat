package com.flxrs.dankchat.utils

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull


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

    @Test
    fun `returns null for blank input`() {
        val result = DateTimeUtils.durationToSeconds("")
        assertNull(result)
    }

    @Test
    fun `returns null for invalid input`() {
        val result = DateTimeUtils.durationToSeconds("d")
        assertNull(result)
    }

    @Test
    fun `parses number input correctly`() {
        val result = DateTimeUtils.durationToSeconds("123")
        assertEquals(expected = 123, actual = result)
    }

    @Test
    fun `parses seconds input correctly`() {
        val result = DateTimeUtils.durationToSeconds("123s")
        assertEquals(expected = 123, actual = result)
    }

    @Test
    fun `parses minutes input correctly`() {
        val result = DateTimeUtils.durationToSeconds("2m")
        assertEquals(expected = 120, actual = result)
    }

    @Test
    fun `parses hours input correctly`() {
        val result = DateTimeUtils.durationToSeconds("2h")
        assertEquals(expected = 7200, actual = result)
    }

    @Test
    fun `parses days input correctly`() {
        val result = DateTimeUtils.durationToSeconds("2d")
        assertEquals(expected = 172800, actual = result)
    }

    @Test
    fun `parses weeks input correctly`() {
        val result = DateTimeUtils.durationToSeconds("2w")
        assertEquals(expected = 1209600, actual = result)
    }

    @Test
    fun `parses combination of units correctly`() {
        val result = DateTimeUtils.durationToSeconds("3s1h4d5m")
        assertEquals(expected = 349503, actual = result)
    }

    @Test
    fun `parses combination of units with spaces correctly`() {
        val result = DateTimeUtils.durationToSeconds("3s 1h 4d 5m")
        assertEquals(expected = 349503, actual = result)
    }
}
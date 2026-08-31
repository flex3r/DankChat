package com.flxrs.dankchat.utils

import com.flxrs.dankchat.utils.DateTimeUtils.DurationPart
import com.flxrs.dankchat.utils.DateTimeUtils.DurationUnit.DAYS
import com.flxrs.dankchat.utils.DateTimeUtils.DurationUnit.HOURS
import com.flxrs.dankchat.utils.DateTimeUtils.DurationUnit.MINUTES
import com.flxrs.dankchat.utils.DateTimeUtils.DurationUnit.SECONDS
import com.flxrs.dankchat.utils.DateTimeUtils.DurationUnit.WEEKS
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

    @Test
    fun `decomposes 30 minutes`() {
        val result = DateTimeUtils.decomposeMinutes(30)
        assertEquals(expected = listOf(DurationPart(30, MINUTES)), actual = result)
    }

    @Test
    fun `decomposes 60 minutes to 1 hour`() {
        val result = DateTimeUtils.decomposeMinutes(60)
        assertEquals(expected = listOf(DurationPart(1, HOURS)), actual = result)
    }

    @Test
    fun `decomposes 90 minutes to 1 hour 30 minutes`() {
        val result = DateTimeUtils.decomposeMinutes(90)
        assertEquals(expected = listOf(DurationPart(1, HOURS), DurationPart(30, MINUTES)), actual = result)
    }

    @Test
    fun `decomposes 1440 minutes to 1 day`() {
        val result = DateTimeUtils.decomposeMinutes(1440)
        assertEquals(expected = listOf(DurationPart(1, DAYS)), actual = result)
    }

    @Test
    fun `decomposes 10080 minutes to 1 week`() {
        val result = DateTimeUtils.decomposeMinutes(10080)
        assertEquals(expected = listOf(DurationPart(1, WEEKS)), actual = result)
    }

    @Test
    fun `decomposes 20160 minutes to 2 weeks`() {
        val result = DateTimeUtils.decomposeMinutes(20160)
        assertEquals(expected = listOf(DurationPart(2, WEEKS)), actual = result)
    }

    @Test
    fun `decomposes 11520 minutes to 1 week 1 day`() {
        val result = DateTimeUtils.decomposeMinutes(11520)
        assertEquals(expected = listOf(DurationPart(1, WEEKS), DurationPart(1, DAYS)), actual = result)
    }

    @Test
    fun `decomposes 0 minutes to empty list`() {
        val result = DateTimeUtils.decomposeMinutes(0)
        assertEquals(expected = emptyList(), actual = result)
    }

    @Test
    fun `decomposes 30 seconds`() {
        val result = DateTimeUtils.decomposeSeconds(30)
        assertEquals(expected = listOf(DurationPart(30, SECONDS)), actual = result)
    }

    @Test
    fun `decomposes 60 seconds to 1 minute`() {
        val result = DateTimeUtils.decomposeSeconds(60)
        assertEquals(expected = listOf(DurationPart(1, MINUTES)), actual = result)
    }

    @Test
    fun `decomposes 125 seconds to 2 minutes 5 seconds`() {
        val result = DateTimeUtils.decomposeSeconds(125)
        assertEquals(expected = listOf(DurationPart(2, MINUTES), DurationPart(5, SECONDS)), actual = result)
    }

    @Test
    fun `decomposes 36000 seconds to 10 hours`() {
        val result = DateTimeUtils.decomposeSeconds(36000)
        assertEquals(expected = listOf(DurationPart(10, HOURS)), actual = result)
    }

    @Test
    fun `decomposes seconds to days hours minutes and seconds`() {
        val result = DateTimeUtils.decomposeSeconds(90061)
        assertEquals(
            expected = listOf(DurationPart(1, DAYS), DurationPart(1, HOURS), DurationPart(1, MINUTES), DurationPart(1, SECONDS)),
            actual = result,
        )
    }

    @Test
    fun `decomposes 0 seconds to empty list`() {
        val result = DateTimeUtils.decomposeSeconds(0)
        assertEquals(expected = emptyList(), actual = result)
    }
}

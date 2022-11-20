package com.flxrs.dankchat.utils

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test


internal class DateTimeUtilsTest {

    @Test
    fun formatSeconds() {
        var result = DateTimeUtils.formatSeconds("10")
        Assertions.assertEquals("10s", result)

        result = DateTimeUtils.formatSeconds("100")
        Assertions.assertEquals("1m 40s", result)

        result = DateTimeUtils.formatSeconds("1000")
        Assertions.assertEquals("16m 40s", result)

        result = DateTimeUtils.formatSeconds("10000")
        Assertions.assertEquals("2h 46m 40s", result)

        result = DateTimeUtils.formatSeconds("604800")
        Assertions.assertEquals("1w", result)

        result = DateTimeUtils.formatSeconds("1209600")
        Assertions.assertEquals("2w", result)
    }
}
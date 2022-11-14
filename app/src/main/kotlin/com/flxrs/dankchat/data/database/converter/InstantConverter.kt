package com.flxrs.dankchat.data.database.converter

import androidx.room.TypeConverter
import java.time.Instant

object InstantConverter {

    @TypeConverter
    fun fromTimestamp(value: Long): Instant = Instant.ofEpochMilli(value)

    @TypeConverter
    fun instantToTimestamp(value: Instant): Long = value.toEpochMilli()
}
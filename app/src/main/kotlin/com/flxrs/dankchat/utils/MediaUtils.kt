package com.flxrs.dankchat.utils

import android.content.Context
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.milliseconds

private val GPS_ATTRIBUTES = listOf(
    ExifInterface.TAG_GPS_LATITUDE,
    ExifInterface.TAG_GPS_LATITUDE_REF,
    ExifInterface.TAG_GPS_LONGITUDE,
    ExifInterface.TAG_GPS_LONGITUDE_REF,
    ExifInterface.TAG_GPS_ALTITUDE,
    ExifInterface.TAG_GPS_ALTITUDE_REF,
    ExifInterface.TAG_GPS_TIMESTAMP,
    ExifInterface.TAG_GPS_DATESTAMP
)

@Throws(IOException::class)
fun createMediaFile(context: Context, suffix: String = "jpg"): File {
    val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
    val storageDir = context.getExternalFilesDir("Media")
    return File.createTempFile(timeStamp, ".$suffix", storageDir)
}

fun tryClearEmptyFiles(context: Context) = runCatching {
    val cutoff = System.currentTimeMillis().milliseconds - 1.days
    context.getExternalFilesDir("Media")
        ?.listFiles()
        ?.filter { it.isFile && it.lastModified().milliseconds < cutoff }
        ?.onEach { it.delete() }
}

@Throws(IOException::class)
fun File.removeExifAttributes() = ExifInterface(this).run {
    GPS_ATTRIBUTES.forEach { if (getAttribute(it) != null) setAttribute(it, null) }
    saveAttributes()
}
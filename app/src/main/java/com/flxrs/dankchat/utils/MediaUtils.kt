package com.flxrs.dankchat.utils

import android.content.ContentUris
import android.content.Context
import android.media.ExifInterface
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*

object MediaUtils {
	private const val MEDIA_DOCUMENT_AUTHORITY = "com.android.providers.media.documents"
	private const val DOWNLOADS_DOCUMENT_AUTHORITY = "com.android.providers.downloads.documents"
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

	fun getImagePathFromUri(context: Context, uri: Uri?): String? {
		uri ?: return null

		when {
			DocumentsContract.isDocumentUri(context, uri) -> {
				val docId = DocumentsContract.getDocumentId(uri)
				when (uri.authority) {
					MEDIA_DOCUMENT_AUTHORITY     -> {
						val id = docId.split(":")[1]
						val selection = MediaStore.Images.Media._ID + "=" + id
						return getImagePath(context, MediaStore.Images.Media.EXTERNAL_CONTENT_URI, selection)
					}
					DOWNLOADS_DOCUMENT_AUTHORITY -> {
						val contentUri = ContentUris.withAppendedId(Uri.parse("content://downloads/public_downloads"), docId.toLong())
						return getImagePath(context, contentUri, null)
					}
				}
			}
			uri.scheme.equals("content", true)            -> return getImagePath(context, uri, null)
			uri.scheme.equals("file", true)               -> return uri.path
		}
		return null
	}

	@Throws(IOException::class)
	fun createImageFile(context: Context): File {
		val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
		val storageDir = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
		return File.createTempFile(
				"JPEG_${timeStamp}_",
				".jpg",
				storageDir
		)
	}

	@Throws(IOException::class)
	fun removeExifAttributes(path: String) {
		ExifInterface(path).run {
			GPS_ATTRIBUTES.forEach { if (getAttribute(it) != null) setAttribute(it, null) }
			saveAttributes()
		}
	}

	private fun getImagePath(context: Context, uri: Uri, selection: String?): String? {
		var path: String? = null
		val cursor = context.contentResolver.query(uri, null, selection, null, null)
		if (cursor != null) {
			if (cursor.moveToFirst()) {
				path = cursor.getString(cursor.getColumnIndex(MediaStore.Images.Media.DATA))
			}
			cursor.close()
		}
		return path
	}
}
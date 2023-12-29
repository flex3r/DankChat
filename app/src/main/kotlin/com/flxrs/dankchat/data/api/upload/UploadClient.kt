package com.flxrs.dankchat.data.api.upload

import android.util.Log
import com.flxrs.dankchat.BuildConfig
import com.flxrs.dankchat.data.api.ApiException
import com.flxrs.dankchat.data.api.upload.dto.UploadDto
import com.flxrs.dankchat.di.UploadOkHttpClient
import com.flxrs.dankchat.preferences.DankChatPreferenceStore
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.URLBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.Response
import org.json.JSONObject
import java.io.File
import java.net.URLConnection
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UploadClient @Inject constructor(
    @UploadOkHttpClient private val httpClient: OkHttpClient,
    private val dankChatPreferenceStore: DankChatPreferenceStore
) {

    suspend fun uploadMedia(file: File): Result<UploadDto> = withContext(Dispatchers.IO) {
        val uploader = dankChatPreferenceStore.customImageUploader
        val mimetype = URLConnection.guessContentTypeFromName(file.name)

        val requestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart(name = uploader.formField, filename = file.name, body = file.asRequestBody(mimetype.toMediaType()))
            .build()
        val request = Request.Builder()
            .url(uploader.uploadUrl)
            .header(HttpHeaders.UserAgent, "dankchat/${BuildConfig.VERSION_NAME}")
            .apply {
                uploader.parsedHeaders.forEach { (name, value) ->
                    header(name, value)
                }
            }
            .post(requestBody)
            .build()

        val response = runCatching {
            httpClient.newCall(request).execute()
        }.getOrElse {
            return@withContext Result.failure(it)
        }

        when {
            response.isSuccessful -> {
                val imageLinkPattern = uploader.imageLinkPattern
                val deletionLinkPattern = uploader.deletionLinkPattern

                if (imageLinkPattern == null) {
                    return@withContext runCatching {
                        val body = response.body.string()
                        UploadDto(
                            imageLink = body,
                            deleteLink = null,
                            timestamp = Instant.now()
                        )
                    }
                }

                response
                    .asJsonObject()
                    .mapCatching { json ->
                        val deleteLink = deletionLinkPattern?.let { json.extractLink(it) }
                        val imageLink = json.extractLink(imageLinkPattern)
                        UploadDto(
                            imageLink = imageLink,
                            deleteLink = deleteLink,
                            timestamp = Instant.now()
                        )
                    }

            }

            else                  -> {
                Log.e(TAG, "Upload failed with ${response.code} ${response.message}")
                val url = URLBuilder(response.request.url.toString()).build()
                Result.failure(ApiException(HttpStatusCode.fromValue(response.code), url, response.message))
            }
        }
    }

    @Suppress("RegExpRedundantEscape")
    private suspend fun JSONObject.extractLink(linkPattern: String): String = withContext(Dispatchers.Default) {
        var imageLink: String = linkPattern

        val regex = "\\{(.+?)\\}".toRegex()
        regex.findAll(linkPattern).forEach {
            val jsonValue = getValue(it.groupValues[1])
            if (jsonValue != null) {
                imageLink = imageLink.replace(it.groupValues[0], jsonValue)
            }
        }
        imageLink
    }

    private fun Response.asJsonObject(): Result<JSONObject> = runCatching {
        val bodyString = body.string()
        JSONObject(bodyString)
    }.onFailure {
        Log.d(TAG, "Error creating JsonObject from response: ", it)
    }

    private fun JSONObject.getValue(pattern: String): String? {
        return runCatching {
            pattern
                .split(".")
                .fold(this) { acc, key ->
                    val value = acc.get(key)
                    if (value !is JSONObject) {
                        return value.toString()
                    }

                    value
                }
            null
        }.getOrNull()
    }

    companion object {
        private val TAG = UploadClient::class.java.simpleName
    }
}

package com.flxrs.dankchat.data.api.cache

import android.content.Context
import com.flxrs.dankchat.di.DispatchersProvider
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import org.koin.core.annotation.Single
import java.io.File

@Single
class EmoteDiskCache(
    context: Context,
    private val dispatchersProvider: DispatchersProvider,
) {
    private val cacheDir = File(context.filesDir, "emote_cache").also { it.mkdirs() }
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun <T> read(
        key: String,
        serializer: KSerializer<T>,
    ): T? = withContext(dispatchersProvider.io) {
        val file = File(cacheDir, "$key.json")
        if (!file.exists()) {
            return@withContext null
        }

        runCatching {
            val content = file.readText()
            json.decodeFromString(serializer, content)
        }.onFailure {
            logger.warn(it) { "Failed to read emote cache for key=$key" }
        }.getOrNull()
    }

    suspend fun <T> write(
        key: String,
        data: T,
        serializer: KSerializer<T>,
    ) = withContext(dispatchersProvider.io) {
        runCatching {
            val content = json.encodeToString(serializer, data)
            File(cacheDir, "$key.json").writeText(content)
        }.onFailure {
            logger.warn(it) { "Failed to write emote cache for key=$key" }
        }
    }

    suspend fun delete(key: String) = withContext(dispatchersProvider.io) {
        File(cacheDir, "$key.json").delete()
    }

    companion object {
        private val logger = KotlinLogging.logger("EmoteDiskCache")
    }
}

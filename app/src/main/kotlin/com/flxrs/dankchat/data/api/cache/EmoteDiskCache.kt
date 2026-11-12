package com.flxrs.dankchat.data.api.cache

import android.content.Context
import com.flxrs.dankchat.di.DispatchersProvider
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import org.koin.core.annotation.Single
import java.io.File
import kotlin.time.Duration.Companion.days

@Single
class EmoteDiskCache(
    context: Context,
    private val dispatchersProvider: DispatchersProvider,
) {
    private val cacheDir = File(context.filesDir, "emote_cache").also { it.mkdirs() }
    private val json = Json { ignoreUnknownKeys = true }

    init {
        CoroutineScope(SupervisorJob() + dispatchersProvider.io).launch { deleteUnusedFiles() }
    }

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
            json.decodeFromString(serializer, content).also {
                // Counts as use, so a file that is only read is kept as well
                file.setLastModified(System.currentTimeMillis())
            }
        }.onFailure {
            logger.warn(it) { "Failed to read emote cache for key=$key" }
        }.getOrNull()
    }

    suspend fun <T> write(
        key: String,
        data: T,
        serializer: KSerializer<T>,
    ) = withContext(dispatchersProvider.io) {
        var tempFile: File? = null
        runCatching {
            val content = json.encodeToString(serializer, data)
            // Written to a temp file first, a write that is cut off must not leave a broken cache entry
            val file = File.createTempFile(key, TEMP_SUFFIX, cacheDir).also { tempFile = it }
            file.writeText(content)
            check(file.renameTo(File(cacheDir, "$key.json"))) { "Rename failed" }
        }.onFailure {
            tempFile?.delete()
            logger.warn(it) { "Failed to write emote cache for key=$key" }
        }
    }

    suspend fun delete(key: String) = withContext(dispatchersProvider.io) {
        File(cacheDir, "$key.json").delete()
    }

    // Channel entries are never deleted with their channel, so entries that were not used for a while are dropped
    private fun deleteUnusedFiles() {
        val threshold = System.currentTimeMillis() - MAX_UNUSED_AGE.inWholeMilliseconds
        cacheDir
            .listFiles()
            .orEmpty()
            .filter { it.lastModified() < threshold }
            .forEach { it.delete() }
    }

    companion object {
        private val logger = KotlinLogging.logger("EmoteDiskCache")
        private val MAX_UNUSED_AGE = 30.days
        private const val TEMP_SUFFIX = ".tmp"
    }
}

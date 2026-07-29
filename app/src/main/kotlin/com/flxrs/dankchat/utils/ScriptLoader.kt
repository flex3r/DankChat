package com.flxrs.dankchat.utils

import android.content.Context
import io.github.oshai.kotlinlogging.KotlinLogging
import java.util.concurrent.ConcurrentHashMap

object ScriptLoader {
    private val logger = KotlinLogging.logger("ScriptLoader")
    private val scriptCache = ConcurrentHashMap<String, String>()

    /**
     * Gets a script from cache or loads it if not present.
     * @param context Android context
     * @param path Path to the script relative to assets/
     * @return The script content
     */
    fun getScript(context: Context, path: String): String {
        return scriptCache.getOrPut(path) {
            loadAssetFromAssets(context, path)
        }
    }

    private fun loadAssetFromAssets(context: Context, path: String): String {
        return try {
            context.assets.open(path).bufferedReader().use { it.readText() }
        } catch (e: Exception) {
            logger.error(e) { "Error loading script from assets: $path" }
            ""
        }
    }
}

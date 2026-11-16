package com.flxrs.dankchat.ui.chat.emote

import android.graphics.ImageDecoder
import coil3.disk.DiskCache
import coil3.intercept.Interceptor
import coil3.request.ErrorResult
import coil3.request.ImageResult
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/**
 * Files that fail to decode are removed from the disk cache, so the next load fetches them again
 * instead of keeping a broken response for as long as its cache headers allow.
 */
class BrokenDiskEntryInterceptor(
    private val ioDispatcher: CoroutineDispatcher,
    private val diskCache: () -> DiskCache?,
) : Interceptor {
    override suspend fun intercept(chain: Interceptor.Chain): ImageResult {
        val result = chain.proceed()
        val url = chain.request.data as? String
        if (url == null || result !is ErrorResult || result.throwable !is ImageDecoder.DecodeException) {
            return result
        }

        val key = chain.request.diskCacheKey ?: url
        withContext(ioDispatcher) { diskCache()?.remove(key) }
        return result
    }
}

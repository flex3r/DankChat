package com.flxrs.dankchat.ui.chat.emote

import coil3.disk.DiskCache
import coil3.intercept.Interceptor
import coil3.request.ErrorResult
import coil3.request.ImageResult
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/**
 * Requests without network access fail right away when their file is not in the disk cache. Otherwise the miss
 * still goes through the HTTP client and waits behind running downloads.
 */
class DiskCacheOnlyInterceptor(
    private val ioDispatcher: CoroutineDispatcher,
    private val diskCache: () -> DiskCache?,
) : Interceptor {
    override suspend fun intercept(chain: Interceptor.Chain): ImageResult {
        val request = chain.request
        val url = request.data as? String
        if (url == null || request.networkCachePolicy.readEnabled || !request.diskCachePolicy.readEnabled) {
            return chain.proceed()
        }

        val key = request.diskCacheKey ?: url
        val isCached = withContext(ioDispatcher) { diskCache()?.openSnapshot(key)?.use { true } ?: false }
        return when {
            isCached -> chain.proceed()
            else -> ErrorResult(image = null, request = request, throwable = NotInDiskCacheException(url))
        }
    }
}

class NotInDiskCacheException(
    url: String,
) : IllegalStateException("Not in disk cache: $url")

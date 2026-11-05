package com.flxrs.dankchat.data.api.upload

import com.flxrs.dankchat.di.DispatchersProvider
import kotlinx.coroutines.withContext
import org.koin.core.annotation.Single
import java.net.InetAddress
import java.net.URI
import java.net.UnknownHostException

@Single
class UploadHostChecker(
    private val dispatchersProvider: DispatchersProvider,
) {
    suspend fun isUnresolvable(uploadUrl: String): Boolean = withContext(dispatchersProvider.io) {
        val host = runCatching { URI(uploadUrl.trim()).host }.getOrNull()
        when {
            host.isNullOrBlank() -> false

            resolves(host) -> false

            // Lookups also fail while offline, so only blame the host if a known one still resolves
            else -> resolves(CONTROL_HOST)
        }
    }

    private fun resolves(host: String): Boolean {
        val result = runCatching { InetAddress.getAllByName(host) }
        return result.exceptionOrNull() !is UnknownHostException
    }

    companion object {
        private const val CONTROL_HOST = "api.twitch.tv"
    }
}

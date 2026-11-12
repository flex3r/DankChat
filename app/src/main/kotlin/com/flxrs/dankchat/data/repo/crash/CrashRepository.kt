package com.flxrs.dankchat.data.repo.crash

import com.flxrs.dankchat.BuildConfig
import com.flxrs.dankchat.utils.datastore.safeData
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.koin.core.annotation.Single

@Single
class CrashRepository(
    crashHandler: CrashHandler,
) {
    private val dataStore = crashHandler.dataStore

    private fun currentData(): CrashData = runBlocking { dataStore.safeData(CrashData()).first() }

    fun hasUnshownCrash(): Boolean = currentData().hasUnshownCrash

    fun markCrashShown() {
        update { it.copy(hasUnshownCrash = false) }
    }

    fun getRecentCrashes(): List<CrashEntry> = currentData().crashes

    fun getMostRecentCrash(): CrashEntry? = getRecentCrashes().firstOrNull()

    fun getCrash(id: Long): CrashEntry? = getRecentCrashes().firstOrNull { it.id == id }

    fun deleteCrash(id: Long) {
        update { current ->
            val removed = current.crashes.firstOrNull { it.id == id }
            val updated = current.crashes.filter { it.id != id }
            when {
                updated.isEmpty() -> CrashData()

                else -> current.copy(
                    crashes = updated,
                    knownFingerprints = when (removed) {
                        null -> current.knownFingerprints
                        else -> current.knownFingerprints - removed.fingerprint
                    },
                )
            }
        }
    }

    fun deleteAllCrashes() {
        update { CrashData() }
    }

    private fun update(transform: suspend (CrashData) -> CrashData) {
        runBlocking {
            runCatching { dataStore.updateData(transform) }
        }
    }

    fun buildCrashReportMessage(entry: CrashEntry): String {
        val header = entry.exceptionHeader.take(MAX_EXCEPTION_HEADER_LENGTH)
        val frames = entry.stackTrace
            .lines()
            .filter { it.trimStart().startsWith("at ") }
            .take(REPORT_FRAME_COUNT)
            .joinToString(" | ") { it.trim() }

        val message = "[Crash] v${BuildConfig.VERSION_NAME} | $header | Thread: ${entry.threadName} | $frames"
        val codePoints = message.codePointCount(0, message.length)
        return when {
            codePoints > MAX_REPORT_CODE_POINTS -> {
                val endIndex = message.offsetByCodePoints(0, MAX_REPORT_CODE_POINTS)
                message.substring(0, endIndex)
            }

            else -> {
                message
            }
        }
    }

    fun buildEmailBody(
        entry: CrashEntry,
        userName: String?,
        userId: String?,
    ): String = buildString {
        appendLine("DankChat Crash Report")
        appendLine("=====================")
        appendLine("Version: ${entry.version}")
        appendLine("Android: ${entry.androidInfo}")
        appendLine("Device: ${entry.device}")
        appendLine("Thread: ${entry.threadName}")
        appendLine("Time: ${entry.timestamp}")
        if (userName != null) {
            appendLine("User: $userName (ID: ${userId.orEmpty()})")
        }
        appendLine()
        appendLine("Stack Trace:")
        appendLine(entry.stackTrace)
    }

    companion object {
        private const val MAX_REPORT_CODE_POINTS = 350
        private const val MAX_EXCEPTION_HEADER_LENGTH = 150
        private const val REPORT_FRAME_COUNT = 3
    }
}

package com.flxrs.dankchat.ui.log

import androidx.compose.runtime.Immutable
import com.flxrs.dankchat.data.repo.log.LogLevel
import com.flxrs.dankchat.data.repo.log.LogLine
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

@Immutable
data class LogViewerState(
    val lines: ImmutableList<LogLine> = persistentListOf(),
    val levelFilter: LogLevel? = null,
)

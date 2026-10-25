package com.flxrs.dankchat.ui.log

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.flxrs.dankchat.data.repo.log.LogLevel
import com.flxrs.dankchat.data.repo.log.LogLine
import com.flxrs.dankchat.data.repo.log.LogLineParser
import com.flxrs.dankchat.data.repo.log.LogRepository
import com.flxrs.dankchat.di.DispatchersProvider
import com.flxrs.dankchat.ui.main.LogViewer
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.core.annotation.KoinViewModel
import java.io.File

@KoinViewModel
class LogViewerViewModel(
    savedStateHandle: SavedStateHandle,
    private val logRepository: LogRepository,
    dispatchersProvider: DispatchersProvider,
) : ViewModel() {
    val searchFieldState = TextFieldState()

    private val initialFileName = savedStateHandle.toRoute<LogViewer>().fileName
    private val fileName by lazy {
        initialFileName.takeIf { it.isNotEmpty() }
            ?: logRepository
                .getLogFiles()
                .firstOrNull()
                ?.name
                .orEmpty()
    }
    private val _levelFilter = MutableStateFlow<LogLevel?>(null)

    // Holds LogLine.index values, which stay stable when the file grows
    private val _selectedIndices = MutableStateFlow<Set<Int>>(emptySet())
    val selectedIndices: StateFlow<Set<Int>> = _selectedIndices.asStateFlow()

    init {
        viewModelScope.launch {
            combine(_levelFilter, snapshotFlow { searchFieldState.text.toString() }) { _, _ -> }.collect {
                _selectedIndices.value = emptySet()
            }
        }
    }

    // Read and parsed once per collection instead of on every filter or search change
    private val lines =
        flow {
            val lines =
                when {
                    fileName.isNotEmpty() -> LogLineParser.parseAll(logRepository.readLogFile(fileName))
                    else -> emptyList()
                }
            emit(lines)
        }.flowOn(dispatchersProvider.io)

    val state: StateFlow<LogViewerState> =
        combine(
            lines,
            _levelFilter,
            snapshotFlow { searchFieldState.text.toString() },
        ) { lines, levelFilter, searchQuery ->
            LogViewerState(
                lines = filterLines(lines, levelFilter, searchQuery).asReversed().toImmutableList(),
                levelFilter = levelFilter,
            )
        }.flowOn(dispatchersProvider.default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), LogViewerState())

    fun setLevelFilter(level: LogLevel?) {
        _levelFilter.update { current ->
            if (current == level) null else level
        }
    }

    fun toggleSelection(index: Int) {
        _selectedIndices.update { current ->
            when (index) {
                in current -> current - index
                else -> current + index
            }
        }
    }

    fun clearSelection() {
        _selectedIndices.value = emptySet()
    }

    fun getSelectedLinesText(): String {
        val selected = _selectedIndices.value
        return state.value.lines
            .filter { it.index in selected }
            .sortedBy { it.index }
            .joinToString("\n") { it.raw }
    }

    fun getShareableLogFile(): File? {
        if (fileName.isEmpty()) return null
        return logRepository.getLogFile(fileName)
    }

    private fun filterLines(
        lines: List<LogLine>,
        levelFilter: LogLevel?,
        searchQuery: String,
    ): List<LogLine> {
        if (levelFilter == null && searchQuery.isBlank()) return lines

        return lines.filter { line ->
            val matchesLevel = levelFilter == null || line.level >= levelFilter
            val matchesSearch = searchQuery.isBlank() || line.raw.contains(searchQuery, ignoreCase = true)
            matchesLevel && matchesSearch
        }
    }
}

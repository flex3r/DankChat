package com.flxrs.dankchat.preferences.tools.export

import android.net.Uri
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flxrs.dankchat.data.backup.BackupRepository
import com.flxrs.dankchat.data.backup.SettingsBackup
import com.flxrs.dankchat.preferences.DankChatPreferenceStore
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.WhileSubscribed
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.koin.core.annotation.KoinViewModel
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.time.Duration.Companion.seconds

@KoinViewModel
class DataExportViewModel(
    private val backupRepository: BackupRepository,
    preferenceStore: DankChatPreferenceStore,
) : ViewModel() {
    private val pendingImport = MutableStateFlow<SettingsBackup?>(null)

    private val _events = MutableSharedFlow<DataExportEvent>()
    val events = _events.asSharedFlow()

    val state: StateFlow<DataExportState> =
        combine(
            preferenceStore.getChannelsWithRenamesFlow().map { it.isNotEmpty() },
            pendingImport.map { it != null },
        ) { hasChannels, isConfirmingImport ->
            DataExportState(hasChannels = hasChannels, isConfirmingImport = isConfirmingImport)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5.seconds),
            initialValue = DataExportState(hasChannels = preferenceStore.channels.isNotEmpty(), isConfirmingImport = false),
        )

    fun settingsFileName(): String = "dankchat-backup-${LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)}.json"

    fun exportChannels(uri: Uri) = viewModelScope.launch {
        val event =
            backupRepository.exportChannels(uri).fold(
                onSuccess = { DataExportEvent.ChannelsExported(it) },
                onFailure = { DataExportEvent.ExportFailed },
            )
        _events.emit(event)
    }

    fun copyChannels() = viewModelScope.launch {
        val event =
            backupRepository.exportChannelsAsText().fold(
                onSuccess = { DataExportEvent.CopyToClipboard(text = it, isSensitive = false) },
                onFailure = { DataExportEvent.ExportFailed },
            )
        _events.emit(event)
    }

    fun importChannels(uri: Uri) = viewModelScope.launch {
        _events.emit(backupRepository.importChannels(uri).toImportEvent(failure = DataExportEvent.ImportFailed))
    }

    fun pasteChannels(text: String?) = viewModelScope.launch {
        val event =
            when (text) {
                null -> DataExportEvent.ClipboardInvalid
                else -> backupRepository.importChannels(text).toImportEvent(failure = DataExportEvent.ClipboardInvalid)
            }
        _events.emit(event)
    }

    fun exportSettings(uri: Uri) = viewModelScope.launch {
        val event =
            backupRepository.exportSettings(uri).fold(
                onSuccess = { DataExportEvent.SettingsExported },
                onFailure = { DataExportEvent.ExportFailed },
            )
        _events.emit(event)
    }

    fun copySettings() = viewModelScope.launch {
        val event =
            backupRepository.exportSettingsAsText().fold(
                onSuccess = { DataExportEvent.CopyToClipboard(text = it, isSensitive = true) },
                onFailure = { DataExportEvent.ExportFailed },
            )
        _events.emit(event)
    }

    fun readSettings(uri: Uri) = viewModelScope.launch {
        backupRepository
            .readSettings(uri)
            .onSuccess { backup -> pendingImport.value = backup }
            .onFailure { _events.emit(DataExportEvent.ImportFailed) }
    }

    fun pasteSettings(text: String?) = viewModelScope.launch {
        val result =
            when (text) {
                null -> null
                else -> backupRepository.readSettings(text).getOrNull()
            }
        when (result) {
            null -> _events.emit(DataExportEvent.ClipboardInvalid)
            else -> pendingImport.value = result
        }
    }

    fun confirmImport() = viewModelScope.launch {
        val backup = pendingImport.value ?: return@launch
        pendingImport.value = null
        val event =
            backupRepository.restoreSettings(backup).fold(
                onSuccess = { DataExportEvent.Restart },
                onFailure = { DataExportEvent.ImportFailed },
            )
        _events.emit(event)
    }

    fun dismissImport() {
        pendingImport.value = null
    }

    private fun Result<Int>.toImportEvent(failure: DataExportEvent): DataExportEvent = fold(
        onSuccess = { added ->
            when (added) {
                0 -> DataExportEvent.NoNewChannels
                else -> DataExportEvent.ChannelsImported(added)
            }
        },
        onFailure = { failure },
    )
}

@Immutable
data class DataExportState(
    val hasChannels: Boolean,
    val isConfirmingImport: Boolean,
)

sealed interface DataExportEvent {
    data class ChannelsExported(
        val count: Int,
    ) : DataExportEvent

    data class ChannelsImported(
        val count: Int,
    ) : DataExportEvent

    data object NoNewChannels : DataExportEvent

    data object SettingsExported : DataExportEvent

    data object ExportFailed : DataExportEvent

    data object ImportFailed : DataExportEvent

    data class CopyToClipboard(
        val text: String,
        val isSensitive: Boolean,
    ) : DataExportEvent

    data object ClipboardInvalid : DataExportEvent

    data object Restart : DataExportEvent
}

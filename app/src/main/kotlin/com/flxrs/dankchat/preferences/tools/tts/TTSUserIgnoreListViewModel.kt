package com.flxrs.dankchat.preferences.tools.tts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flxrs.dankchat.preferences.tools.ToolsSettingsDataStore
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.WhileSubscribed
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.koin.android.annotation.KoinViewModel
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.Uuid

@KoinViewModel
class TTSUserIgnoreListViewModel(
    private val toolsSettingsDataStore: ToolsSettingsDataStore,
) : ViewModel() {

    val userIgnores = toolsSettingsDataStore.settings
        .map { it.ttsUserIgnoreList.mapToUserIgnores() }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5.seconds),
            initialValue = toolsSettingsDataStore.current().ttsUserIgnoreList.mapToUserIgnores()
        )

    fun save(ignores: List<UserIgnore>) = viewModelScope.launch {
        val filtered = ignores.mapNotNullTo(mutableSetOf()) { it.user.takeIf { it.isNotBlank() } }
        toolsSettingsDataStore.update { it.copy(ttsUserIgnoreList = filtered) }
    }

    private fun Set<String>.mapToUserIgnores() = map { UserIgnore(user = it) }.toImmutableList()
}

data class UserIgnore(val id: String = Uuid.random().toString(), val user: String)

package com.flxrs.dankchat.changelog

import androidx.lifecycle.ViewModel
import com.flxrs.dankchat.preferences.DankChatPreferenceStore
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class ChangelogSheetViewModel @Inject constructor(
    dankChatPreferenceStore: DankChatPreferenceStore,
) : ViewModel() {

    init {
        dankChatPreferenceStore.setCurrentInstalledVersionCode()
    }

    val state: ChangelogState? = DankChatVersion.LATEST_CHANGELOG?.let {
        ChangelogState(it.version.copy(patch = 0).formattedString(), it.stringRes)
    }
}

package com.flxrs.dankchat.changelog

import androidx.lifecycle.ViewModel
import com.flxrs.dankchat.preferences.DankChatPreferenceStore
import org.koin.android.annotation.KoinViewModel

@KoinViewModel
class ChangelogSheetViewModel(
    dankChatPreferenceStore: DankChatPreferenceStore,
) : ViewModel() {

    init {
        dankChatPreferenceStore.setCurrentInstalledVersionCode()
    }

    val state: ChangelogState? = DankChatVersion.LATEST_CHANGELOG?.let {
        ChangelogState(it.version.copy(patch = 0).formattedString(), it.string)
    }
}

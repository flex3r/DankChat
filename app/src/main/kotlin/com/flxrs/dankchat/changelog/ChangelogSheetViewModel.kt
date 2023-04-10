package com.flxrs.dankchat.changelog

import androidx.lifecycle.ViewModel
import com.flxrs.dankchat.R
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

    val state: ChangelogState? = when (val current = DankChatVersion.CURRENT) {
        DankChatVersion(major = 3, minor = 6, patch = 0) -> ChangelogState(current.formattedString(), R.string.changelog_3_6)
        else                                             -> null
    }
}

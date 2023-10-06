package com.flxrs.dankchat.changelog

import androidx.annotation.StringRes
import com.flxrs.dankchat.R

@Suppress("unused")
enum class DankChatChangelog(val version: DankChatVersion, @get:StringRes val stringRes: Int) {
    VERSION_3_6_0(DankChatVersion(major = 3, minor = 6, patch = 0), R.string.changelog_3_6),
    VERSION_3_7_3(DankChatVersion(major = 3, minor = 7, patch = 3), R.string.changelog_3_7_3),
    VERSION_3_8_0(DankChatVersion(major = 3, minor = 8, patch = 2), R.string.changelog_3_8_2),
}

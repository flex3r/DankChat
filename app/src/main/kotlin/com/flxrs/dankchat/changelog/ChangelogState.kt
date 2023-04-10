package com.flxrs.dankchat.changelog

import androidx.annotation.StringRes

data class ChangelogState(val version: String, @StringRes val changelog: Int)

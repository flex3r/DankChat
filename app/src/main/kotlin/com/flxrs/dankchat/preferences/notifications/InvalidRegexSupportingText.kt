package com.flxrs.dankchat.preferences.notifications

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.flxrs.dankchat.R

/** Supporting text of a pattern field whose regex does not compile. */
val InvalidRegexSupportingText: @Composable () -> Unit = { Text(stringResource(R.string.multi_entry_invalid_regex)) }

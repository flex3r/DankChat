package com.flxrs.dankchat.preferences.ui

import androidx.lifecycle.ViewModel
import com.flxrs.dankchat.data.repo.HighlightsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class HighlightsViewModel @Inject constructor(
    private val highlightsRepository: HighlightsRepository
): ViewModel()
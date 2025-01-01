package com.flxrs.dankchat.chat.replies

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flxrs.dankchat.data.repo.RepliesRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.WhileSubscribed
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import org.koin.android.annotation.KoinViewModel
import kotlin.time.Duration.Companion.seconds

@KoinViewModel
class RepliesViewModel(
    repliesRepository: RepliesRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val args = RepliesFragmentArgs.fromSavedStateHandle(savedStateHandle)

    val state = repliesRepository.getThreadItemsFlow(args.rootMessageId)
        .map {
            when {
                it.isEmpty() -> RepliesState.NotFound
                else         -> RepliesState.Found(it)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5.seconds), RepliesState.Found(emptyList()))
}

package com.flxrs.dankchat.preferences.ui.ignores

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flxrs.dankchat.data.repo.IgnoresRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds

@HiltViewModel
class IgnoresViewModel @Inject constructor(
    private val ignoresRepository: IgnoresRepository
) : ViewModel() {

    private val messageIgnoresTab = MutableStateFlow(IgnoresTabItem(IgnoresTab.Messages, listOf(AddItem)))
    private val userIgnoresTab = MutableStateFlow(IgnoresTabItem(IgnoresTab.Users, listOf(AddItem)))
    private val twitchBlocksTab = MutableStateFlow(IgnoresTabItem(IgnoresTab.Twitch, emptyList()))

    val ignoreTabs = combine(messageIgnoresTab, userIgnoresTab, twitchBlocksTab) { messageIgnoresTab, userIgnoresTab, twitchBlocksTab ->
        listOf(messageIgnoresTab, userIgnoresTab, twitchBlocksTab)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(stopTimeout = 5.seconds), INITIAL_STATE)

    fun fetchIgnores() {
        val messageIgnores = ignoresRepository.messageIgnores.value.map { it.toItem() }
        val userIgnores = ignoresRepository.userIgnores.value.map { it.toItem() }
        val twitchBlocks = ignoresRepository.twitchBlocks.value.map { it.toItem() }

        messageIgnoresTab.update { it.copy(items = listOf(AddItem) + messageIgnores) }
        userIgnoresTab.update { it.copy(items = listOf(AddItem) + userIgnores) }
        twitchBlocksTab.update { it.copy(items = twitchBlocks) }
    }

    fun addIgnore(tab: IgnoresTab) = viewModelScope.launch {
        when (tab) {
            IgnoresTab.Messages -> {
                val entity = ignoresRepository.addMessageIgnore()
                messageIgnoresTab.update {
                    it.copy(items = it.items + entity.toItem())
                }
            }

            IgnoresTab.Users    -> {
                val entity = ignoresRepository.addUserIgnore()
                userIgnoresTab.update {
                    it.copy(items = it.items + entity.toItem())
                }
            }

            IgnoresTab.Twitch   -> Unit
        }
    }

    fun removeIgnore(item: IgnoreItem) = viewModelScope.launch {
        when (item) {
            is MessageIgnoreItem -> {
                ignoresRepository.removeMessageIgnore(item.toEntity())
                messageIgnoresTab.update { it.copy(items = it.items - item) }
            }

            is UserIgnoreItem    -> {
                ignoresRepository.removeUserIgnore(item.toEntity())
                userIgnoresTab.update { it.copy(items = it.items - item) }
            }

            is TwitchBlockItem   -> {
                runCatching {
                    ignoresRepository.removeUserBlock(item.userId, item.username)
                    twitchBlocksTab.update { it.copy(items = it.items - item) }
                    // TODO snackbar event
                }.getOrElse {
                    // TODO snackbar event
                }
            }

            else                 -> Unit
        }
    }

    fun updateIgnores(tabItems: List<IgnoresTabItem>) = viewModelScope.launch {
        tabItems.forEach { tab ->
            when (tab.tab) {
                IgnoresTab.Messages -> {
                    val (blankEntities, entities) = tab.items
                        .filterIsInstance<MessageIgnoreItem>()
                        .map { it.toEntity() }
                        .partition { it.pattern.isBlank() }

                    ignoresRepository.updateMessageIgnores(entities)
                    blankEntities.forEach { ignoresRepository.removeMessageIgnore(it) }
                }

                IgnoresTab.Users    -> {
                    val (blankEntities, entities) = tab.items
                        .filterIsInstance<UserIgnoreItem>()
                        .map { it.toEntity() }
                        .partition { it.username.isBlank() }

                    ignoresRepository.updateUserIgnores(entities)
                    blankEntities.forEach { ignoresRepository.removeUserIgnore(it) }
                }

                else                -> Unit
            }
        }
    }

    companion object {
        private val INITIAL_STATE = listOf(
            IgnoresTabItem(IgnoresTab.Messages, listOf(AddItem)),
            IgnoresTabItem(IgnoresTab.Users, listOf(AddItem)),
            IgnoresTabItem(IgnoresTab.Twitch, emptyList()),
        )
    }
}
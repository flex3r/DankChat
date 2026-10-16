package com.flxrs.dankchat.preferences.dankchataccount

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flxrs.dankchat.data.api.ApiException
import com.flxrs.dankchat.data.api.dankchat.DankChatApiClient
import com.flxrs.dankchat.data.api.dankchat.dto.DankChatAccountDto
import com.flxrs.dankchat.data.api.dankchat.dto.DankChatBadgeChoiceDto
import com.flxrs.dankchat.data.auth.AuthDataStore
import com.flxrs.dankchat.data.auth.StartupValidationHolder
import com.flxrs.dankchat.data.repo.data.DataRepository
import io.ktor.http.HttpStatusCode
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.core.annotation.KoinViewModel

@KoinViewModel
class DankChatAccountViewModel(
    private val dankChatApiClient: DankChatApiClient,
    private val dataRepository: DataRepository,
    private val authDataStore: AuthDataStore,
    private val startupValidationHolder: StartupValidationHolder,
    private val knownBadgeOwnersDataStore: KnownBadgeOwnersDataStore,
) : ViewModel() {
    private val _state = MutableStateFlow(DankChatAccountState(badge = DankChatBadgeState.Loading, isOpeningAccount = false))
    val state: StateFlow<DankChatAccountState> = _state.asStateFlow()

    private val eventChannel = Channel<DankChatAccountEvent>(Channel.BUFFERED)
    val events = eventChannel.receiveAsFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            updateBadge(DankChatBadgeState.Loading)
            startupValidationHolder.awaitResolved()
            if (!authDataStore.isLoggedIn || !startupValidationHolder.isAuthAvailable) {
                updateBadge(DankChatBadgeState.LoggedOut)
                return@launch
            }

            val badge =
                dankChatApiClient
                    .getAccount()
                    .onSuccess { rememberBadgeOwner(ownsBadges = it.badges.isNotEmpty()) }
                    .fold(
                        onSuccess = { it.toState() },
                        onFailure = { it.toFailureState() },
                    )
            updateBadge(badge)
        }
    }

    fun choose(choice: DankChatBadgeChoice) {
        val current = _state.value.badge as? DankChatBadgeState.Loaded ?: return
        if (current.isSaving || current.choice == choice) {
            return
        }

        updateBadge(current.copy(choice = choice, isSaving = true))
        viewModelScope.launch {
            dankChatApiClient
                .chooseBadge(choice.toDto())
                .onSuccess {
                    updateBadge(it.toState())
                    dataRepository.loadDankChatBadges()
                }.onFailure {
                    updateBadge(current)
                    eventChannel.send(DankChatAccountEvent.SaveFailed)
                }
        }
    }

    /** Opens the account page signed in, or the regular dank.chat login when the app login can't be used. */
    fun manageAccount() {
        if (_state.value.isOpeningAccount) {
            return
        }

        _state.update { it.copy(isOpeningAccount = true) }
        viewModelScope.launch {
            val url =
                dankChatApiClient
                    .createLoginCode()
                    .map { it.url }
                    .getOrDefault(ACCOUNT_LOGIN_URL)
            eventChannel.send(DankChatAccountEvent.OpenUrl(url))
        }
    }

    /** The account page covers this screen until the user comes back, so taps in between can't open it twice. */
    fun onScreenResumed() {
        _state.update { it.copy(isOpeningAccount = false) }
    }

    private fun updateBadge(badge: DankChatBadgeState) {
        _state.update { it.copy(badge = badge) }
    }

    private suspend fun rememberBadgeOwner(ownsBadges: Boolean) {
        val userId = authDataStore.userIdString ?: return
        knownBadgeOwnersDataStore.update {
            when {
                ownsBadges -> it.copy(userIds = it.userIds + userId)
                else -> it.copy(userIds = it.userIds - userId)
            }
        }
    }

    private fun DankChatAccountDto.toState(): DankChatBadgeState {
        val items = badges.map { DankChatBadgeItem(id = it.id, title = it.title, url = it.url) }
        val automaticBadge = items.firstOrNull() ?: return DankChatBadgeState.NoBadges
        val selected =
            when (val dto = choice) {
                DankChatBadgeChoiceDto.Automatic -> DankChatBadgeChoice.Automatic
                DankChatBadgeChoiceDto.Hidden -> DankChatBadgeChoice.Hidden
                is DankChatBadgeChoiceDto.Badge -> DankChatBadgeChoice.Badge(dto.id)
            }
        return DankChatBadgeState.Loaded(
            badges = items.toImmutableList(),
            automaticBadge = automaticBadge,
            choice = selected,
            isSaving = false,
        )
    }

    private fun Throwable.toFailureState(): DankChatBadgeState = when {
        this is ApiException && status == HttpStatusCode.Unauthorized -> DankChatBadgeState.Unsupported
        else -> DankChatBadgeState.Failed
    }

    private fun DankChatBadgeChoice.toDto(): DankChatBadgeChoiceDto = when (this) {
        DankChatBadgeChoice.Automatic -> DankChatBadgeChoiceDto.Automatic
        DankChatBadgeChoice.Hidden -> DankChatBadgeChoiceDto.Hidden
        is DankChatBadgeChoice.Badge -> DankChatBadgeChoiceDto.Badge(id)
    }

    companion object {
        private const val ACCOUNT_LOGIN_URL = "https://dank.chat/login"
    }
}

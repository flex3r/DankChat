package com.flxrs.dankchat.ui.chat.user

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flxrs.dankchat.data.UserId
import com.flxrs.dankchat.data.UserName
import com.flxrs.dankchat.data.repo.HighlightsRepository
import com.flxrs.dankchat.data.repo.IgnoresRepository
import com.flxrs.dankchat.data.repo.channel.ChannelRepository
import com.flxrs.dankchat.data.repo.chat.UserStateRepository
import com.flxrs.dankchat.data.repo.data.DataRepository
import com.flxrs.dankchat.data.twitch.badge.Badge
import com.flxrs.dankchat.preferences.DankChatPreferenceStore
import com.flxrs.dankchat.utils.DateTimeUtils.asParsedZonedDateTime
import com.flxrs.dankchat.utils.extensions.runCatchingCancellable
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.koin.core.annotation.KoinViewModel

data class UserPopupUiState(
    val popupState: UserPopupState,
    val channel: UserName?,
    val badges: List<Badge>,
    val isOwnUser: Boolean,
    val ignoresHighlights: Boolean,
)

@KoinViewModel
class UserPopupViewModel(
    private val channelRepository: ChannelRepository,
    private val dataRepository: DataRepository,
    private val highlightsRepository: HighlightsRepository,
    private val ignoresRepository: IgnoresRepository,
    private val userStateRepository: UserStateRepository,
    private val preferenceStore: DankChatPreferenceStore,
) : ViewModel() {
    private val _state = MutableStateFlow<UserPopupUiState?>(null)
    val state: StateFlow<UserPopupUiState?> = _state.asStateFlow()
    val isActive = _state.map { it != null }

    private var currentParams: UserPopupStateParams? = null
    private var loadJob: Job? = null

    fun show(params: UserPopupStateParams) {
        currentParams = params
        loadData(params)
    }

    fun dismiss() {
        loadJob?.cancel()
        currentParams = null
        _state.value = null
    }

    fun blockUser() = updateStateWith { targetUserId, targetUsername ->
        ignoresRepository.addUserBlock(targetUserId, targetUsername)
    }

    fun unblockUser() = updateStateWith { targetUserId, targetUsername ->
        ignoresRepository.removeUserBlock(targetUserId, targetUsername)
    }

    fun setIgnoreHighlights(ignored: Boolean) {
        val params = currentParams ?: return
        viewModelScope.launch {
            runCatchingCancellable {
                highlightsRepository.setUserHighlightsIgnored(params.targetUserName, ignored)
            }.onSuccess {
                if (currentParams == params) {
                    _state.value = _state.value?.copy(ignoresHighlights = ignored)
                }
            }
        }
    }

    private fun emitState(
        params: UserPopupStateParams,
        popupState: UserPopupState,
    ) {
        // Results of a dismissed or replaced popup must not bring it back
        if (currentParams != params) {
            return
        }

        _state.value = UserPopupUiState(
            popupState = popupState,
            channel = params.channel,
            badges = params.badges,
            isOwnUser = params.targetUserId != null && preferenceStore.userIdString == params.targetUserId,
            ignoresHighlights = highlightsRepository.isUserHighlightsIgnored(params.targetUserName),
        )
    }

    private inline fun updateStateWith(crossinline block: suspend (targetUserId: UserId, targetUsername: UserName) -> Result<Unit>) {
        val params = currentParams ?: return
        viewModelScope.launch {
            if (!preferenceStore.isLoggedIn) {
                return@launch
            }

            val resolvedUserId = (_state.value?.popupState as? UserPopupState.Success)?.userId ?: params.targetUserId ?: return@launch
            block(resolvedUserId, params.targetUserName).fold(
                onSuccess = {
                    when (val loaded = _state.value?.popupState as? UserPopupState.Success) {
                        null -> loadData(params)
                        else -> emitState(params, loaded.copy(isBlocked = ignoresRepository.isUserBlocked(loaded.userId)))
                    }
                },
                onFailure = { emitState(params, UserPopupState.Error(it)) },
            )
        }
    }

    private fun loadData(params: UserPopupStateParams) {
        if (currentParams != params) {
            return
        }

        loadJob?.cancel()
        val cachedUser = params.targetUserId?.let { channelRepository.getCachedUserDto(it) }
        val initialState = when (cachedUser) {
            null -> UserPopupState.Loading(params.targetUserName, params.targetDisplayName)

            else -> UserPopupState.Success(
                userId = cachedUser.id,
                userName = cachedUser.name,
                displayName = cachedUser.displayName,
                avatarUrl = cachedUser.avatarUrl,
                created = cachedUser.createdAt.asParsedZonedDateTime(),
                isBlocked = ignoresRepository.isUserBlocked(cachedUser.id),
            )
        }
        emitState(params, initialState)

        loadJob = viewModelScope.launch {
            val currentUserId = preferenceStore.userIdString
            if (!preferenceStore.isLoggedIn || currentUserId == null) {
                emitState(params, UserPopupState.NotLoggedIn(params.targetUserName, params.targetDisplayName))
                return@launch
            }

            val result =
                runCatchingCancellable {
                    val user = when {
                        params.targetUserId != null -> channelRepository.getUserDto(params.targetUserId)
                        else -> channelRepository.getUserDtoByName(params.targetUserName)
                    }

                    val resolvedUserId = user?.id ?: params.targetUserId ?: UserId("")
                    val channelId = params.channel?.let { channelRepository.getChannel(it)?.id }
                    val canLoadFollows = channelId != resolvedUserId && (currentUserId == channelId || userStateRepository.isModeratorInChannel(params.channel))
                    val channelUserFollows = channelId?.takeIf { canLoadFollows }?.let { dataRepository.getChannelFollowers(channelId, resolvedUserId) }

                    user ?: return@runCatchingCancellable UserPopupState.Error()

                    UserPopupState.Success(
                        userId = user.id,
                        userName = user.name,
                        displayName = user.displayName,
                        avatarUrl = user.avatarUrl,
                        created = user.createdAt.asParsedZonedDateTime(),
                        showFollowingSince = canLoadFollows,
                        followingSince = channelUserFollows
                            ?.data
                            ?.firstOrNull()
                            ?.followedAt
                            ?.asParsedZonedDateTime(),
                        // Read last, a block or unblock can complete while the requests above are running
                        isBlocked = ignoresRepository.isUserBlocked(user.id),
                    )
                }

            val popupState = result.getOrElse { UserPopupState.Error(it) }
            emitState(params, popupState)
        }
    }
}

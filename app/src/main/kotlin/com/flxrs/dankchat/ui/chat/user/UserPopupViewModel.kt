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
            runCatching {
                highlightsRepository.setUserHighlightsIgnored(params.targetUserName, ignored)
            }.onSuccess {
                _state.value = _state.value?.copy(ignoresHighlights = ignored)
            }
        }
    }

    private fun emitState(
        params: UserPopupStateParams,
        popupState: UserPopupState,
    ) {
        _state.value = UserPopupUiState(
            popupState = popupState,
            channel = params.channel,
            badges = params.badges,
            isOwnUser = params.targetUserId != null && preferenceStore.userIdString == params.targetUserId,
            ignoresHighlights = highlightsRepository.isUserHighlightsIgnored(params.targetUserName),
        )
    }

    private inline fun updateStateWith(crossinline block: suspend (targetUserId: UserId, targetUsername: UserName) -> Unit) {
        val params = currentParams ?: return
        viewModelScope.launch {
            if (!preferenceStore.isLoggedIn) {
                return@launch
            }

            val resolvedUserId = (_state.value?.popupState as? UserPopupState.Success)?.userId ?: params.targetUserId ?: return@launch
            val result = runCatching { block(resolvedUserId, params.targetUserName) }
            when {
                result.isFailure -> emitState(params, UserPopupState.Error(result.exceptionOrNull()))
                else -> loadData(params)
            }
        }
    }

    private fun loadData(params: UserPopupStateParams) {
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
                runCatching {
                    val user = when {
                        params.targetUserId != null -> channelRepository.getUserDto(params.targetUserId)
                        else -> channelRepository.getUserDtoByName(params.targetUserName)
                    }

                    val resolvedUserId = user?.id ?: params.targetUserId ?: UserId("")
                    val channelId = params.channel?.let { channelRepository.getChannel(it)?.id }
                    val isBlocked = ignoresRepository.isUserBlocked(resolvedUserId)
                    val canLoadFollows = channelId != resolvedUserId && (currentUserId == channelId || userStateRepository.isModeratorInChannel(params.channel))
                    val channelUserFollows = channelId?.takeIf { canLoadFollows }?.let { dataRepository.getChannelFollowers(channelId, resolvedUserId) }

                    user ?: return@runCatching UserPopupState.Error()

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
                        isBlocked = isBlocked,
                    )
                }

            val popupState = result.getOrElse { UserPopupState.Error(it) }
            emitState(params, popupState)
        }
    }
}

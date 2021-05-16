package com.flxrs.dankchat.chat.user

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flxrs.dankchat.service.ChatRepository
import com.flxrs.dankchat.service.DataRepository
import com.flxrs.dankchat.service.api.dto.HelixUserDto
import com.flxrs.dankchat.service.api.dto.UserFollowsDto
import com.flxrs.dankchat.utils.DateTimeUtils.asParsedZonedDateTime
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

@HiltViewModel
class UserPopupViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val chatRepository: ChatRepository,
    private val dataRepository: DataRepository
) : ViewModel() {
    private val targetUserId = savedStateHandle.get<String>(UserPopupDialogFragment.TARGET_USER_ID_ARG)
    private val currentUserId = savedStateHandle.get<String>(UserPopupDialogFragment.CURRENT_USER_ID_ARG)
    private val oAuth = savedStateHandle.get<String>(UserPopupDialogFragment.OAUTH_ARG)

    sealed class UserPopupState {
        object Loading : UserPopupState()
        data class Error(val throwable: Throwable? = null) : UserPopupState()
        data class Success(
            val userId: String,
            val userName: String,
            val displayName: String,
            val created: String,
            val avatarUrl: String,
            val isFollowing: Boolean = false,
            val followingSince: String? = null,
            val isBlocked: Boolean = false
        ) : UserPopupState()
    }

    private val _userPopupState = MutableStateFlow<UserPopupState>(UserPopupState.Loading)
    val userPopupState: StateFlow<UserPopupState> = _userPopupState.asStateFlow()

    val isFollowing: Boolean
        get() {
            val state = userPopupState.value
            return state is UserPopupState.Success && state.isFollowing
        }
    val isBlocked: Boolean
        get() {
            val state = userPopupState.value
            return state is UserPopupState.Success && state.isBlocked
        }

    val displayNameOrNull: String?
        get() = (userPopupState.value as? UserPopupState.Success)?.displayName

    init {
        loadData()
    }

    fun followUser() = updateStateWith { targetUserId, currentUserId, oAuth ->
        dataRepository.followUser(oAuth, currentUserId, targetUserId)
    }

    fun blockUser() = updateStateWith { targetUserId, _, oAuth ->
        dataRepository.blockUser(oAuth, targetUserId)
        chatRepository.addUserBlock(targetUserId)
    }

    fun unfollowUser() = updateStateWith { targetUserId, currentUserId, oAuth ->
        dataRepository.unfollowUser(oAuth, currentUserId, targetUserId)
    }

    fun unblockUser() = updateStateWith { targetUserId, _, oAuth ->
        dataRepository.unblockUser(oAuth, targetUserId)
        chatRepository.removeUserBlock(targetUserId)
    }

    private inline fun updateStateWith(crossinline block: suspend (String, String, String) -> Unit) = viewModelScope.launch {
        if (targetUserId == null || currentUserId == null || oAuth == null) {
            _userPopupState.value = UserPopupState.Error()
            return@launch
        }

        val result = runCatching { block(targetUserId, currentUserId, oAuth) }
        when {
            result.isFailure -> _userPopupState.value = UserPopupState.Error(result.exceptionOrNull())
            else -> loadData()
        }
    }

    private fun loadData() = viewModelScope.launch {
        if (targetUserId == null || currentUserId == null || oAuth == null) {
            _userPopupState.value = UserPopupState.Error()
            return@launch
        }

        _userPopupState.value = UserPopupState.Loading

        val result = runCatching {
            val user = dataRepository.getUser(oAuth, targetUserId)
            val targetUserFollows = dataRepository.getUsersFollows(oAuth, targetUserId, currentUserId)
            val currentUserFollows = dataRepository.getUsersFollows(oAuth, currentUserId, targetUserId)
            val isBlocked = chatRepository.isUserBlocked(targetUserId)

            mapToState(
                user = user,
                targetUserFollows = targetUserFollows,
                currentUserFollows = currentUserFollows,
                isBlocked = isBlocked
            )
        }

        val state = result.getOrElse { UserPopupState.Error(it) }
        _userPopupState.value = state
    }

    private fun mapToState(user: HelixUserDto?, targetUserFollows: UserFollowsDto?, currentUserFollows: UserFollowsDto?, isBlocked: Boolean): UserPopupState {
        user ?: return UserPopupState.Error()

        return UserPopupState.Success(
            userId = user.id,
            userName = user.name,
            displayName = user.displayName,
            avatarUrl = user.avatarUrl,
            created = user.createdAt.asParsedZonedDateTime(),
            isFollowing = currentUserFollows?.total == 1,
            followingSince = targetUserFollows?.data?.firstOrNull()?.followedAt?.asParsedZonedDateTime(),
            isBlocked = isBlocked
        )
    }
}
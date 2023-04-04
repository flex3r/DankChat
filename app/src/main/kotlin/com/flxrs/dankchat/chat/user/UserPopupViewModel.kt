package com.flxrs.dankchat.chat.user

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flxrs.dankchat.data.DisplayName
import com.flxrs.dankchat.data.UserId
import com.flxrs.dankchat.data.UserName
import com.flxrs.dankchat.data.api.helix.dto.UserDto
import com.flxrs.dankchat.data.api.helix.dto.UserFollowsDto
import com.flxrs.dankchat.data.repo.IgnoresRepository
import com.flxrs.dankchat.data.repo.data.DataRepository
import com.flxrs.dankchat.preferences.DankChatPreferenceStore
import com.flxrs.dankchat.utils.DateTimeUtils.asParsedZonedDateTime
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class UserPopupViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val dataRepository: DataRepository,
    private val ignoresRepository: IgnoresRepository,
    private val preferenceStore: DankChatPreferenceStore,
) : ViewModel() {

    private val args = UserPopupDialogFragmentArgs.fromSavedStateHandle(savedStateHandle)

    private val _userPopupState = MutableStateFlow<UserPopupState>(UserPopupState.Loading(args.targetUserName, args.targetDisplayName))

    val userPopupState: StateFlow<UserPopupState> = _userPopupState.asStateFlow()

    val isBlocked: Boolean
        get() {
            val state = userPopupState.value
            return state is UserPopupState.Success && state.isBlocked
        }

    val userName: UserName
        get() = (userPopupState.value as? UserPopupState.Success)?.userName ?: args.targetUserName

    val displayName: DisplayName
        get() = (userPopupState.value as? UserPopupState.Success)?.displayName ?: args.targetDisplayName

    init {
        loadData()
    }

    fun blockUser() = updateStateWith { targetUserId, targetUsername ->
        ignoresRepository.addUserBlock(targetUserId, targetUsername)
    }

    fun unblockUser() = updateStateWith { targetUserId, targetUsername ->
        ignoresRepository.removeUserBlock(targetUserId, targetUsername)
    }

    private inline fun updateStateWith(crossinline block: suspend (targetUserId: UserId, targetUsername: UserName) -> Unit) = viewModelScope.launch {
        if (!preferenceStore.isLoggedIn) {
            return@launch
        }

        val result = runCatching { block(args.targetUserId, args.targetUserName) }
        when {
            result.isFailure -> _userPopupState.value = UserPopupState.Error(result.exceptionOrNull())
            else             -> loadData()
        }
    }

    private fun loadData() = viewModelScope.launch {
        if (!preferenceStore.isLoggedIn) {
            _userPopupState.value = UserPopupState.NotLoggedIn(args.targetUserName, args.targetDisplayName)
            return@launch
        }

        _userPopupState.value = UserPopupState.Loading(args.targetUserName, args.targetDisplayName)
        val currentUserId = preferenceStore.userIdString
        if (!preferenceStore.isLoggedIn || currentUserId == null) {
            _userPopupState.value = UserPopupState.Error()
            return@launch
        }

        val targetUserId = args.targetUserId
        val result = runCatching {
            val channelId = args.channel?.let { dataRepository.getUserIdByName(it) }
            val channelUserFollows = channelId?.let { dataRepository.getUserFollows(targetUserId, channelId) }
            val user = dataRepository.getUser(targetUserId)
            val currentUserFollows = dataRepository.getUserFollows(currentUserId, targetUserId)
            val isBlocked = ignoresRepository.isUserBlocked(targetUserId)

            mapToState(
                user = user,
                channelUserFollows = channelUserFollows,
                currentUserFollows = currentUserFollows,
                isBlocked = isBlocked
            )
        }

        val state = result.getOrElse { UserPopupState.Error(it) }
        _userPopupState.value = state
    }

    private fun mapToState(user: UserDto?, channelUserFollows: UserFollowsDto?, currentUserFollows: UserFollowsDto?, isBlocked: Boolean): UserPopupState {
        user ?: return UserPopupState.Error()

        return UserPopupState.Success(
            userId = user.id,
            userName = user.name,
            displayName = user.displayName,
            avatarUrl = user.avatarUrl,
            created = user.createdAt.asParsedZonedDateTime(),
            isFollowing = currentUserFollows?.total == 1,
            followingSince = channelUserFollows?.data?.firstOrNull()?.followedAt?.asParsedZonedDateTime(),
            isBlocked = isBlocked
        )
    }

    companion object {
        private val TAG = UserPopupViewModel::class.java.simpleName
    }
}

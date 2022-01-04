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
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class UserPopupViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val chatRepository: ChatRepository,
    private val dataRepository: DataRepository
) : ViewModel() {

    private val args = UserPopupDialogFragmentArgs.fromSavedStateHandle(savedStateHandle)

    private val _userPopupState = MutableStateFlow<UserPopupState>(UserPopupState.Loading)
    val userPopupState: StateFlow<UserPopupState> = _userPopupState.asStateFlow()

    private val hasModInChannel: StateFlow<Boolean> = chatRepository.userStateFlow
        .map { args.channel != null && args.channel in it.moderationChannels }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), false)

    val canShowModeration: StateFlow<Boolean> =
        combine(userPopupState, hasModInChannel) { state, hasMod ->
            state is UserPopupState.Success && hasMod
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), false)

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

    val userNameOrNull: String?
        get() = (userPopupState.value as? UserPopupState.Success)?.userName

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

    fun timeoutUser(index: Int) {
        val userName = userNameOrNull ?: return
        val duration = TIMEOUT_MAP[index] ?: return
        chatRepository.sendMessage(".timeout $userName $duration")
    }

    fun banUser() {
        val userName = userNameOrNull ?: return
        chatRepository.sendMessage(".ban $userName")
    }

    fun unbanUser() {
        val userName = userNameOrNull ?: return
        chatRepository.sendMessage(".unban $userName")
    }

    private inline fun updateStateWith(crossinline block: suspend (String, String, String) -> Unit) = viewModelScope.launch {
        val result = runCatching { block(args.targetUserId, args.currentUserId, args.oAuth) }
        when {
            result.isFailure -> _userPopupState.value = UserPopupState.Error(result.exceptionOrNull())
            else             -> loadData()
        }
    }

    private fun loadData() = viewModelScope.launch {
        _userPopupState.value = UserPopupState.Loading

        val result = runCatching {
            val channelId = args.channel?.let { dataRepository.getUserIdByName(args.oAuth, it) }
            val channelUserFollows = channelId?.let { dataRepository.getUserFollows(args.oAuth, args.targetUserId, channelId) }
            val user = dataRepository.getUser(args.oAuth, args.targetUserId)
            val currentUserFollows = dataRepository.getUserFollows(args.oAuth, args.currentUserId, args.targetUserId)
            val isBlocked = chatRepository.isUserBlocked(args.targetUserId)

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

    private fun mapToState(user: HelixUserDto?, channelUserFollows: UserFollowsDto?, currentUserFollows: UserFollowsDto?, isBlocked: Boolean): UserPopupState {
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
        private val TIMEOUT_MAP = mapOf(
            0 to "1",
            1 to "30",
            2 to "60",
            3 to "300",
            4 to "1800",
            5 to "3600",
            6 to "86400",
            7 to "604800",
        )
    }
}
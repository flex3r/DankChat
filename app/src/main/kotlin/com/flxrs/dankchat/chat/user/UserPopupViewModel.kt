package com.flxrs.dankchat.chat.user

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flxrs.dankchat.data.api.helix.dto.UserDto
import com.flxrs.dankchat.data.api.helix.dto.UserFollowsDto
import com.flxrs.dankchat.data.repo.chat.ChatRepository
import com.flxrs.dankchat.data.repo.data.DataRepository
import com.flxrs.dankchat.data.repo.IgnoresRepository
import com.flxrs.dankchat.preferences.DankChatPreferenceStore
import com.flxrs.dankchat.utils.DateTimeUtils.asParsedZonedDateTime
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds

@HiltViewModel
class UserPopupViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val chatRepository: ChatRepository,
    private val dataRepository: DataRepository,
    private val ignoresRepository: IgnoresRepository,
    private val preferenceStore: DankChatPreferenceStore,
) : ViewModel() {

    private val args = UserPopupDialogFragmentArgs.fromSavedStateHandle(savedStateHandle)

    private val _userPopupState = MutableStateFlow<UserPopupState>(UserPopupState.Loading(args.targetUserName))

    val canShowModeration: StateFlow<Boolean> = chatRepository.userStateFlow
        .map { args.channel != null && args.channel in it.moderationChannels }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(stopTimeout = 5.seconds), false)

    val userPopupState: StateFlow<UserPopupState> = _userPopupState.asStateFlow()

    val isBlocked: Boolean
        get() {
            val state = userPopupState.value
            return state is UserPopupState.Success && state.isBlocked
        }

    val userName: String
        get() = (userPopupState.value as? UserPopupState.Success)?.userName ?: args.targetUserName

    val displayOrUsername: String
        get() {
            val state = userPopupState.value as? UserPopupState.Success ?: return args.targetUserName
            return state.displayName.takeIf { it.equals(state.userName, ignoreCase = true) } ?: state.userName
        }

    init {
        loadData()
    }

    fun blockUser() = updateStateWith { targetUserId, targetUsername ->
        ignoresRepository.addUserBlock(targetUserId, targetUsername)
    }

    fun unblockUser() = updateStateWith { targetUserId, targetUsername ->
        ignoresRepository.removeUserBlock(targetUserId, targetUsername)
    }

    fun timeoutUser(index: Int) {
        val userName = userName
        val duration = TIMEOUT_MAP[index] ?: return
        chatRepository.sendMessage(".timeout $userName $duration")
    }

    fun banUser() {
        val userName = userName
        chatRepository.sendMessage(".ban $userName")
    }

    fun unbanUser() {
        val userName = userName
        chatRepository.sendMessage(".unban $userName")
    }

    fun deleteMessage() {
        val messageId = args.messageId
        chatRepository.sendMessage(".delete $messageId")
    }

    private inline fun updateStateWith(crossinline block: suspend (targetUserId: String, targetUsername: String) -> Unit) = viewModelScope.launch {
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
            _userPopupState.value = UserPopupState.NotLoggedIn(args.targetUserName)
            return@launch
        }

        _userPopupState.value = UserPopupState.Loading(args.targetUserName)
        val currentUserId = preferenceStore.userIdString
        if (!preferenceStore.isLoggedIn || currentUserId == null) {
            _userPopupState.value = UserPopupState.Error()
            return@launch
        }

        val result = runCatching {
            val channelId = args.channel?.let { dataRepository.getUserIdByName(it) }
            val channelUserFollows = channelId?.let { dataRepository.getUserFollows(args.targetUserId, channelId) }
            val user = dataRepository.getUser(args.targetUserId)
            val currentUserFollows = dataRepository.getUserFollows(currentUserId, args.targetUserId)
            val isBlocked = ignoresRepository.isUserBlocked(args.targetUserId)

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
        private val TIMEOUT_MAP = mapOf(
            0 to "1",
            1 to "30",
            2 to "60",
            3 to "300",
            4 to "600",
            5 to "1800",
            6 to "3600",
            7 to "86400",
            8 to "604800",
        )
    }
}
package com.flxrs.dankchat.chat.user

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flxrs.dankchat.data.ChatRepository
import com.flxrs.dankchat.data.DataRepository
import com.flxrs.dankchat.data.api.dto.HelixUserDto
import com.flxrs.dankchat.data.api.dto.UserFollowsDto
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

    init {
        loadData()
    }

    fun blockUser() = updateStateWith { targetUserId, _ ->
        dataRepository.blockUser(targetUserId)
        chatRepository.addUserBlock(targetUserId)
    }

    fun unblockUser() = updateStateWith { targetUserId, _ ->
        dataRepository.unblockUser(targetUserId)
        chatRepository.removeUserBlock(targetUserId)
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

    private inline fun updateStateWith(crossinline block: suspend (String, String) -> Unit) = viewModelScope.launch {
        if (!preferenceStore.isLoggedIn) {
            return@launch
        }
        val currentUserId = preferenceStore.userIdString ?: return@launch
        val result = runCatching { block(args.targetUserId, currentUserId) }
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
            4 to "600",
            5 to "1800",
            6 to "3600",
            7 to "86400",
            8 to "604800",
        )
    }
}
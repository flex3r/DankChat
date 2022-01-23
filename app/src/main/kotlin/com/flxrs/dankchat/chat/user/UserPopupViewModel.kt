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
import com.flxrs.dankchat.utils.extensions.withoutOAuthSuffix
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class UserPopupViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val chatRepository: ChatRepository,
    private val dataRepository: DataRepository,
    private val preferenceStore: DankChatPreferenceStore,
) : ViewModel() {

    private val args = UserPopupDialogFragmentArgs.fromSavedStateHandle(savedStateHandle)

    private val _userPopupState = MutableStateFlow<UserPopupState>(UserPopupState.Loading)

    private val hasModInChannel: StateFlow<Boolean> = chatRepository.userStateFlow
        .map { args.channel != null && args.channel in it.moderationChannels }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), false)

    val userPopupState: StateFlow<UserPopupState> = _userPopupState.asStateFlow()

    val canShowModeration: StateFlow<Boolean> =
        combine(userPopupState, hasModInChannel) { state, hasMod ->
            state is UserPopupState.Success && hasMod
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), false)

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

    fun blockUser() = updateStateWith { targetUserId, _, oAuth ->
        dataRepository.blockUser(oAuth, targetUserId)
        chatRepository.addUserBlock(targetUserId)
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

    fun deleteMessage() {
        val messageId = args.messageId
        chatRepository.sendMessage(".delete $messageId")
    }

    private inline fun updateStateWith(crossinline block: suspend (String, String, String) -> Unit) = viewModelScope.launch {
        val oAuth = preferenceStore.oAuthKey?.withoutOAuthSuffix ?: return@launch
        val currentUserId = preferenceStore.userIdString ?: return@launch
        val result = runCatching { block(args.targetUserId, currentUserId, oAuth) }
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

        _userPopupState.value = UserPopupState.Loading
        val oAuth = preferenceStore.oAuthKey?.withoutOAuthSuffix
        val currentUserId = preferenceStore.userIdString
        if (oAuth == null || currentUserId == null) {
            _userPopupState.value = UserPopupState.Error()
            return@launch
        }

        val result = runCatching {
            val channelId = args.channel?.let { dataRepository.getUserIdByName(oAuth, it) }
            val channelUserFollows = channelId?.let { dataRepository.getUserFollows(oAuth, args.targetUserId, channelId) }
            val user = dataRepository.getUser(oAuth, args.targetUserId)
            val currentUserFollows = dataRepository.getUserFollows(oAuth, currentUserId, args.targetUserId)
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
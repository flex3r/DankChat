package com.flxrs.dankchat.chat.user

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flxrs.dankchat.data.DisplayName
import com.flxrs.dankchat.data.UserId
import com.flxrs.dankchat.data.UserName
import com.flxrs.dankchat.data.api.helix.dto.UserDto
import com.flxrs.dankchat.data.api.helix.dto.UserFollowsDto
import com.flxrs.dankchat.data.repo.CommandRepository
import com.flxrs.dankchat.data.repo.CommandResult
import com.flxrs.dankchat.data.repo.IgnoresRepository
import com.flxrs.dankchat.data.repo.chat.ChatRepository
import com.flxrs.dankchat.data.repo.data.DataRepository
import com.flxrs.dankchat.preferences.DankChatPreferenceStore
import com.flxrs.dankchat.utils.DateTimeUtils.asParsedZonedDateTime
import com.flxrs.dankchat.utils.extensions.firstValueOrNull
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.WhileSubscribed
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds

@HiltViewModel
class UserPopupViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val chatRepository: ChatRepository,
    private val dataRepository: DataRepository,
    private val ignoresRepository: IgnoresRepository,
    private val commandRepository: CommandRepository,
    private val preferenceStore: DankChatPreferenceStore,
) : ViewModel() {

    private val args = UserPopupDialogFragmentArgs.fromSavedStateHandle(savedStateHandle)

    private val _userPopupState = MutableStateFlow<UserPopupState>(UserPopupState.Loading(args.targetUserName, args.targetDisplayName))

    val canShowModeration: StateFlow<Boolean> = chatRepository.userStateFlow
        .map { args.channel != null && args.channel in it.moderationChannels }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(stopTimeout = 5.seconds), false)

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

    suspend fun timeoutUser(index: Int) {
        val userName = userName
        val duration = TIMEOUT_MAP[index] ?: return
        sendCommand(".timeout $userName $duration")
    }

    suspend fun banUser() {
        val userName = userName
        sendCommand(".ban $userName")
    }

    suspend fun unbanUser() {
        val userName = userName
        sendCommand(".unban $userName")
    }

    suspend fun deleteMessage() {
        val messageId = args.messageId
        sendCommand(".delete $messageId")
    }

    private suspend fun sendCommand(message: String) {
        val channel = args.channel ?: return
        val roomState = chatRepository.getRoomState(channel).firstValueOrNull ?: return
        val result = runCatching {
            commandRepository.checkForCommands(message, channel, roomState)
        }.getOrNull() ?: return

        when (result) {
            is CommandResult.IrcCommand            -> chatRepository.sendMessage(message)
            is CommandResult.AcceptedTwitchCommand -> result.response?.let { chatRepository.makeAndPostCustomSystemMessage(it, channel) }
            else                                   -> Log.d(TAG, "Unhandled command result: $result")
        }
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

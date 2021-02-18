package com.flxrs.dankchat.chat.user

import android.util.Log
import androidx.lifecycle.*
import com.flxrs.dankchat.service.ChatRepository
import com.flxrs.dankchat.service.DataRepository
import com.flxrs.dankchat.service.api.dto.UserDtos
import com.flxrs.dankchat.utils.asParsedZonedDateTime
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class UserPopupViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val dataRepository: DataRepository,
    private val chatRepository: ChatRepository
) : ViewModel() {
    private val coroutineExceptionHandler = CoroutineExceptionHandler { _, t ->
        Log.e(TAG, Log.getStackTraceString(t))
    }

    private val targetUserId = savedStateHandle.get<String>(UserPopupFragment.TARGET_USER_ID_ARG)
    private val currentUserId = savedStateHandle.get<String>(UserPopupFragment.CURRENT_USER_ID_ARG)
    private val channel = savedStateHandle.get<String>(UserPopupFragment.CHANNEL_ARG)
    private val oAuth = savedStateHandle.get<String>(UserPopupFragment.OAUTH_ARG)
    private val loadTrigger = MutableLiveData(Unit)

    val user: LiveData<User> = loadTrigger.switchMap { loadUser() }
    val userFollows: LiveData<UserFollows> = loadTrigger.switchMap { loadUserFollows() }
    val isFollowing: Boolean
        get() = userFollows.value?.isFollowing == true

    fun refresh() {
        loadTrigger.value = Unit
    }

    fun followUser() = viewModelScope.launch(coroutineExceptionHandler) {
        if (currentUserId == null || targetUserId == null || oAuth == null) {
            return@launch
        }

        dataRepository.followUser(oAuth, currentUserId, targetUserId)
        refresh()
    }

    fun unfollowUser() = viewModelScope.launch(coroutineExceptionHandler) {
        if (currentUserId == null || targetUserId == null || oAuth == null) {
            return@launch
        }

        dataRepository.unfollowUser(oAuth, currentUserId, targetUserId)
        refresh()
    }

    private fun loadUserFollows() = liveData(coroutineExceptionHandler) {
        currentUserId ?: return@liveData
        targetUserId ?: return@liveData
        oAuth ?: return@liveData
        val targetToCurrentUserFollow = dataRepository.getUsersFollows(oAuth, targetUserId, currentUserId)
        val currentToTargetUserFollow = dataRepository.getUsersFollows(oAuth, currentUserId, targetUserId)

        val userFollowData = UserFollows(
            isFollowing = currentToTargetUserFollow?.total == 1,
            followingSince = targetToCurrentUserFollow?.data?.firstOrNull()?.followedAt?.asParsedZonedDateTime()
        )
        emit(userFollowData)
    }

    private fun loadUser() = liveData(coroutineExceptionHandler) {
        targetUserId ?: return@liveData
        oAuth ?: return@liveData
        val dto = dataRepository.getUser(targetUserId, oAuth) ?: return@liveData

        emit(dto.toUser())
    }

    private fun UserDtos.HelixUser.toUser() = User(
        userId = id,
        userName = name,
        displayName = displayName,
        avatarUrl = avatarUrl,
        created = createdAt.asParsedZonedDateTime()
    )

    companion object {
        private val TAG = UserPopupViewModel::class.java.simpleName
    }
}
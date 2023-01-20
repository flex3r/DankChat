package com.flxrs.dankchat.data.repo

import com.flxrs.dankchat.data.UserName
import com.flxrs.dankchat.data.database.UserDisplayDao
import com.flxrs.dankchat.data.database.UserDisplayEntity
import com.flxrs.dankchat.data.twitch.message.Message
import com.flxrs.dankchat.data.twitch.message.PointRedemptionMessage
import com.flxrs.dankchat.data.twitch.message.PrivMessage
import com.flxrs.dankchat.data.twitch.message.UserDisplay
import com.flxrs.dankchat.data.twitch.message.UserNoticeMessage
import com.flxrs.dankchat.data.twitch.message.WhisperMessage
import com.flxrs.dankchat.data.twitch.message.toUserDisplay
import com.flxrs.dankchat.di.ApplicationScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserDisplayRepository @Inject constructor(
    private val userDisplayDao: UserDisplayDao,
    @ApplicationScope val coroutineScope: CoroutineScope,
) {

    val userDisplays = userDisplayDao.getUserDisplaysFlow()
        .map { entities ->
            entities.map {
                it.copy(targetUser = it.targetUser.trim())
            }
        }
        .stateIn(
            scope = coroutineScope,
            started = SharingStarted.Eagerly,
            initialValue = emptyList()
        )

    suspend fun addUserDisplays(userDisplays: List<UserDisplayEntity>) {
        userDisplayDao.insertAll(userDisplays)
    }

    suspend fun addUserDisplay(userDisplay: UserDisplayEntity) {
        userDisplayDao.insert(userDisplay)
    }

    suspend fun delete(userDisplay: UserDisplayEntity) {
        userDisplayDao.delete(userDisplay)
    }

    fun calculateUserDisplay(message: Message): Message {
        return when (message) {
            is PointRedemptionMessage -> message.applyUserDisplay()
            is PrivMessage            -> message.applyUserDisplay()
            is UserNoticeMessage      -> message.applyUserDisplay()
            is WhisperMessage         -> message.applyUserDisplay()
            else                      -> return message
        }
    }

    private fun PrivMessage.applyUserDisplay(): PrivMessage {
        val match = findMatchingUserDisplay(name) ?: return this
        return copy(userDisplay = match)
    }

    private fun PointRedemptionMessage.applyUserDisplay(): PointRedemptionMessage {
        val match = findMatchingUserDisplay(name) ?: return this
        return copy(userDisplay = match)
    }

    // e.g. announcement -> have child message
    private fun UserNoticeMessage.applyUserDisplay(): UserNoticeMessage {
        val processedChildMessage = childMessage?.applyUserDisplay() ?: return this
        return copy(childMessage = processedChildMessage)
    }

    private fun WhisperMessage.applyUserDisplay(): WhisperMessage {
        val senderMatch = findMatchingUserDisplay(name)
        val recipientMatch = findMatchingUserDisplay(recipientName)
        if (senderMatch == null && recipientMatch == null) {
            return this
        }

        return copy(
            userDisplay = senderMatch,
            recipientDisplay = recipientMatch
        )
    }

    private fun findMatchingUserDisplay(name: UserName): UserDisplay? {
        return userDisplays.value.find { name.matches(it.targetUser) }?.toUserDisplay()
    }
}



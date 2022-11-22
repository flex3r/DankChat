package com.flxrs.dankchat.data.repo

import com.flxrs.dankchat.data.database.UserDisplayDao
import com.flxrs.dankchat.data.database.UserDisplayEntity
import com.flxrs.dankchat.data.twitch.message.*
import com.flxrs.dankchat.di.ApplicationScope
import com.flxrs.dankchat.preferences.userdisplay.UserDisplayDto
import com.flxrs.dankchat.preferences.userdisplay.UserDisplayDto.Companion.toDto
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
    suspend fun getAllUserDisplays(): List<UserDisplayDto> = userDisplayDao.getAll().map { it.toDto() }

    private val userDisplays = userDisplayDao.getUserDisplaysFlow().map { users -> users.map { it.toDto() } }.stateIn(
        coroutineScope,
        SharingStarted.Eagerly,
        emptyList()
    )

    /** add (if ID == 0) or update (ID != 0) user displays */
    suspend fun addUserDisplays(userDisplays: List<UserDisplayDto>) {
        userDisplayDao.insertAll(userDisplays.map { it.toEntity() })
    }

    suspend fun deleteByIds(ids: List<Int>): Int {
        return userDisplayDao.deleteAll(ids.map { UserDisplayEntity.makeDummy(it) })
    }

    fun calculateUserDisplay(message: Message): Message {
        return when (message) {
            is ClearChatMessage       -> message.applyUserDisplay()
            is PointRedemptionMessage -> message.applyUserDisplay()
            is PrivMessage            -> message.applyUserDisplay()
            is UserNoticeMessage      -> message.applyUserDisplay()
            is WhisperMessage         -> message.applyUserDisplay()
            else                      -> return message
        }

    }

    private fun findMatchingUserDisplay(name: String): UserDisplayDto? {
        return userDisplays.value.find { it.username.equals(name, true) }
    }

    private fun PrivMessage.applyUserDisplay(): PrivMessage {
        val match = findMatchingUserDisplay(name) ?: return this
        return copy(userDisplay = match)
    }

    private fun ClearChatMessage.applyUserDisplay(): ClearChatMessage {
        if (targetUser == null) return this
        val match = findMatchingUserDisplay(targetUser) ?: return this
        return copy(userDisplay = match)
    }

    private fun PointRedemptionMessage.applyUserDisplay(): PointRedemptionMessage {
        val match = findMatchingUserDisplay(name) ?: return this
        return copy(userDisplay = match)
    }

    // e.g. announcement ->have child message
    private fun UserNoticeMessage.applyUserDisplay(): UserNoticeMessage {
        val processedChildMessage = childMessage?.applyUserDisplay()
        // optimize to prevent unnecessary copy if the childMessage is still the same
        if (processedChildMessage == childMessage) return this
        return copy(childMessage = processedChildMessage)
    }

    private fun WhisperMessage.applyUserDisplay(): WhisperMessage {
        val senderMatch = findMatchingUserDisplay(name)
        val recipientMatch = findMatchingUserDisplay(recipientName)
        if (senderMatch == null && recipientMatch == null) return this
        return copy(
            userDisplay = senderMatch,
            recipientDisplay = recipientMatch
        )
    }
}



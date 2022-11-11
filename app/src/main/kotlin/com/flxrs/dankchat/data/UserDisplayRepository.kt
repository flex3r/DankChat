package com.flxrs.dankchat.data

import com.flxrs.dankchat.data.database.UserDisplayDao
import com.flxrs.dankchat.data.database.UserDisplayEntity
import com.flxrs.dankchat.data.database.UserDisplayEntity.Companion.toEntity
import com.flxrs.dankchat.preferences.userdisplay.UserDisplayDto
import javax.inject.Inject

class UserDisplayRepository @Inject constructor(
    private val userDisplayDao: UserDisplayDao
) {
    suspend fun getAllUserDisplays(): List<UserDisplayDto> = userDisplayDao.getAll().map {
        UserDisplayDto(id = it.id, username = it.targetUser, colorHex = it.colorHex.orEmpty(), alias = it.alias.orEmpty())
    }

    /** add (if ID == 0) or update (ID != 0) user displays */
    suspend fun addUserDisplays(userDisplays: List<UserDisplayDto>) {
        userDisplayDao.insertAll(userDisplays.map { it.toEntity() })
    }

    suspend fun deleteByIds(ids: List<Int>): Int {
        return userDisplayDao.deleteAll(ids.map { UserDisplayEntity.makeDummy(it) })
    }

}
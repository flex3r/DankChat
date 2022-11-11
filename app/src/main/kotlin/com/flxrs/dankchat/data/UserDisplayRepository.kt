package com.flxrs.dankchat.data

import com.flxrs.dankchat.data.database.UserDisplayDao
import com.flxrs.dankchat.data.database.UserDisplayEntity
import com.flxrs.dankchat.preferences.userdisplay.UserDisplayDto
import javax.inject.Inject

class UserDisplayRepository @Inject constructor(
    private val userDisplayDao: UserDisplayDao
) {
    fun getAllUserDisplays(): List<UserDisplayEntity> = userDisplayDao.getAll()
    fun addUserDisplay(userDisplay: UserDisplayDto) {
        val entity = UserDisplayEntity(
            id = 0,
            targetUser = userDisplay.username,
            colorHex = userDisplay.colorHex,
            alias = userDisplay.alias,
        )
        userDisplayDao.insertAll(entity)
    }

    fun deleteById(id: Int): Int {
        val toDelete = UserDisplayEntity(id = id, targetUser = "dummy", colorHex = "dummy", alias = "dummy")
        return userDisplayDao.delete(toDelete)
    }

}
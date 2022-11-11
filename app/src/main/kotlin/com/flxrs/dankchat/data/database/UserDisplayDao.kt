package com.flxrs.dankchat.data.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query

@Dao
interface UserDisplayDao {
    @Query("SELECT * from user_display")
    fun getAll(): List<UserDisplayEntity>

    @Insert
    fun insertAll(vararg users: UserDisplayEntity)

    @Delete
    fun delete(user: UserDisplayEntity): Int
}
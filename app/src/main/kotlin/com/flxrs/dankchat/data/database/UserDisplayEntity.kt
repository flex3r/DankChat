package com.flxrs.dankchat.data.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "user_display")
data class UserDisplayEntity(
    @PrimaryKey(autoGenerate = true) val id: Int,
    @ColumnInfo(name = "target_user") val targetUser: String,
    @ColumnInfo(name = "color_hex") val colorHex: String?,
    @ColumnInfo(name = "alias") val alias: String?
) {
    companion object {
        /** make dummy entity with specified ID, useful for deleting by ID */
        fun makeDummy(id: Int) = UserDisplayEntity(id = id, targetUser = "dummy", colorHex = "dummy", alias = "dummy")

    }

}

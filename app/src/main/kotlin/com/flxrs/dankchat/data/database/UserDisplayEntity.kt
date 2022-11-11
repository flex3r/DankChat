package com.flxrs.dankchat.data.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "user_display")
data class UserDisplayEntity(
    @PrimaryKey(autoGenerate = true) val id: Int,
    @ColumnInfo(name = "target_user") val targetUser: String, // target user to apply the color and/or alias
    @ColumnInfo(name = "color_hex") val colorHex: String?, // effect color: leaving blank or invalid color will have no effect
    @ColumnInfo(name = "alias") val alias: String? // aliased name
) {
    companion object {
        /** make dummy entity with specified ID, useful for deleting by ID */
        fun makeDummy(id: Int) = UserDisplayEntity(id = id, targetUser = "dummy", colorHex = "dummy", alias = "dummy")

    }

}

package com.flxrs.dankchat.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.flxrs.dankchat.data.database.entity.UploadEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RecentUploadsDao {

    @Query("SELECT * FROM upload ORDER BY timestamp DESC LIMIT $RECENT_UPLOADS_LIMIT")
    fun getRecentUploads(): Flow<List<UploadEntity>>

    @Insert
    suspend fun addUpload(uploadEntity: UploadEntity)

    @Query("DELETE FROM upload")
    suspend fun clearUploads()

    companion object {
        private const val RECENT_UPLOADS_LIMIT = 100
    }
}
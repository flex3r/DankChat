package com.flxrs.dankchat.data.repo

import com.flxrs.dankchat.data.api.upload.dto.UploadDto
import com.flxrs.dankchat.data.database.dao.RecentUploadsDao
import com.flxrs.dankchat.data.database.entity.UploadEntity
import kotlinx.coroutines.flow.Flow
import org.koin.core.annotation.Single

@Single
class RecentUploadsRepository(
    private val recentUploadsDao: RecentUploadsDao
) {

    fun getRecentUploads(): Flow<List<UploadEntity>> = recentUploadsDao.getRecentUploads()

    suspend fun addUpload(upload: UploadDto) {
        val entity = UploadEntity(
            id = 0,
            timestamp = upload.timestamp,
            imageLink = upload.imageLink,
            deleteLink = upload.deleteLink
        )
        recentUploadsDao.addUpload(entity)
    }

    suspend fun clearUploads() = recentUploadsDao.clearUploads()
}

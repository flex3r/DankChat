package com.flxrs.dankchat.preferences.upload

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flxrs.dankchat.data.repo.RecentUploadsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class RecentUploadsViewModel @Inject constructor(
    private val recentUploadsRepository: RecentUploadsRepository
) : ViewModel() {

    fun clearUploads() = viewModelScope.launch {
        recentUploadsRepository.clearUploads()
    }

    fun getRecentUploads(): Flow<List<RecentUpload>> = recentUploadsRepository
        .getRecentUploads()
        .map { uploads ->
            uploads.map {
                RecentUpload(
                    id = it.id,
                    imageUrl = it.imageLink,
                    deleteUrl = it.deleteLink,
                    formattedUploadTime = it.timestamp.formatWithLocale(Locale.getDefault())
                )
            }
        }

    companion object {
        private val formatter = DateTimeFormatter
            .ofLocalizedDateTime(FormatStyle.SHORT)
            .withZone(ZoneId.systemDefault())

        private fun Instant.formatWithLocale(locale: Locale) = formatter
            .withLocale(locale)
            .format(this)
    }
}
package com.flxrs.dankchat.preferences.tools.upload

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flxrs.dankchat.data.repo.RecentUploadsRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.WhileSubscribed
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.koin.android.annotation.KoinViewModel
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import kotlin.time.Duration.Companion.seconds

@KoinViewModel
class RecentUploadsViewModel(
    private val recentUploadsRepository: RecentUploadsRepository
) : ViewModel() {

    val recentUploads = recentUploadsRepository
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
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5.seconds),
            initialValue = emptyList(),
        )

    fun clearUploads() = viewModelScope.launch {
        recentUploadsRepository.clearUploads()
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

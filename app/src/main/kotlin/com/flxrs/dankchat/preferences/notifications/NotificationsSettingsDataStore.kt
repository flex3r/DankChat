package com.flxrs.dankchat.preferences.notifications

import android.content.Context
import com.flxrs.dankchat.R
import com.flxrs.dankchat.di.DispatchersProvider
import com.flxrs.dankchat.utils.datastore.PreferenceKeys
import com.flxrs.dankchat.utils.datastore.booleanOrDefault
import com.flxrs.dankchat.utils.datastore.createDataStore
import com.flxrs.dankchat.utils.datastore.dankChatPreferencesMigration
import com.flxrs.dankchat.utils.datastore.stringOrNull
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.runBlocking
import org.koin.core.annotation.Single

@Single
class NotificationsSettingsDataStore(
    context: Context,
    dispatchersProvider: DispatchersProvider,
) {

    private enum class NotificationsPreferenceKeys(override val id: Int) : PreferenceKeys {
        ShowNotifications(R.string.preference_notification_key),
        ShowWhisperNotifications(R.string.preference_notification_whisper_key),
        MentionFormat(R.string.preference_mention_format_key),
    }

    private val initialMigration = dankChatPreferencesMigration<NotificationsPreferenceKeys, NotificationsSettings>(context) { acc, key, value ->
        when (key) {
            NotificationsPreferenceKeys.ShowNotifications           -> acc.copy(showNotifications = value.booleanOrDefault(acc.showNotifications))
            NotificationsPreferenceKeys.ShowWhisperNotifications    -> acc.copy(showWhisperNotifications = value.booleanOrDefault(acc.showWhisperNotifications))
            NotificationsPreferenceKeys.MentionFormat               -> acc.copy(
                mentionFormat = value.stringOrNull()?.let { format ->
                    MentionFormat.entries.find { it.template == format }
                } ?: acc.mentionFormat
            )
        }
    }

    private val dataStore = createDataStore(
        fileName = "notifications",
        context = context,
        defaultValue = NotificationsSettings(),
        serializer = NotificationsSettings.serializer(),
        scope = CoroutineScope(dispatchersProvider.io + SupervisorJob()),
        migrations = listOf(initialMigration),
    )

    val settings = dataStore.data
    val currentSettings = settings.stateIn(
        scope = CoroutineScope(dispatchersProvider.io),
        started = SharingStarted.Eagerly,
        initialValue = runBlocking { settings.first() }
    )

    val showNotifications = settings
        .map { it.showNotifications }
        .distinctUntilChanged()

    fun current() = currentSettings.value

    suspend fun update(transform: suspend (NotificationsSettings) -> NotificationsSettings) {
        runCatching { dataStore.updateData(transform) }
    }
}

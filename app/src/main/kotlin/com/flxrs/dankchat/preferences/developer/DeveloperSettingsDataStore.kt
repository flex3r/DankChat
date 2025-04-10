package com.flxrs.dankchat.preferences.developer

import android.content.Context
import com.flxrs.dankchat.R
import com.flxrs.dankchat.di.DispatchersProvider
import com.flxrs.dankchat.utils.datastore.PreferenceKeys
import com.flxrs.dankchat.utils.datastore.booleanOrDefault
import com.flxrs.dankchat.utils.datastore.createDataStore
import com.flxrs.dankchat.utils.datastore.dankChatPreferencesMigration
import com.flxrs.dankchat.utils.datastore.safeData
import com.flxrs.dankchat.utils.datastore.stringOrDefault
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.runBlocking
import org.koin.core.annotation.Single

@Single
class DeveloperSettingsDataStore(
    context: Context,
    dispatchersProvider: DispatchersProvider,
) {

    private enum class DeveloperPreferenceKeys(override val id: Int) : PreferenceKeys {
        DebugMode(R.string.preference_debug_mode_key),
        RepeatedSending(R.string.preference_repeated_sending_key),
        BypassCommandHandling(R.string.preference_bypass_command_handling_key),
        CustomRecentMessagesHost(R.string.preference_rm_host_key)
    }

    private val initialMigration = dankChatPreferencesMigration<DeveloperPreferenceKeys, DeveloperSettings>(context) { acc, key, value ->
        when (key) {
            DeveloperPreferenceKeys.DebugMode                -> acc.copy(debugMode = value.booleanOrDefault(acc.debugMode))
            DeveloperPreferenceKeys.RepeatedSending          -> acc.copy(repeatedSending = value.booleanOrDefault(acc.repeatedSending))
            DeveloperPreferenceKeys.BypassCommandHandling    -> acc.copy(bypassCommandHandling = value.booleanOrDefault(acc.bypassCommandHandling))
            DeveloperPreferenceKeys.CustomRecentMessagesHost -> acc.copy(customRecentMessagesHost = value.stringOrDefault(acc.customRecentMessagesHost))
        }
    }

    private val dataStore = createDataStore(
        fileName = "developer",
        context = context,
        defaultValue = DeveloperSettings(),
        serializer = DeveloperSettings.serializer(),
        scope = CoroutineScope(dispatchersProvider.io + SupervisorJob()),
        migrations = listOf(initialMigration),
    )

    val settings = dataStore.safeData(DeveloperSettings())
    val currentSettings = settings.stateIn(
        scope = CoroutineScope(dispatchersProvider.io),
        started = SharingStarted.Eagerly,
        initialValue = runBlocking { settings.first() }
    )

    fun current() = currentSettings.value

    suspend fun update(transform: suspend (DeveloperSettings) -> DeveloperSettings) {
        runCatching { dataStore.updateData(transform) }
    }
}

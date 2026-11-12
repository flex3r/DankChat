package com.flxrs.dankchat.preferences.battery

import android.content.Context
import androidx.datastore.core.DataMigration
import com.flxrs.dankchat.di.DispatchersProvider
import com.flxrs.dankchat.preferences.chat.ChatSettingsDataStore
import com.flxrs.dankchat.preferences.chat.LiveUpdatesBackgroundBehavior
import com.flxrs.dankchat.utils.datastore.createDataStore
import com.flxrs.dankchat.utils.datastore.safeData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.runBlocking
import org.koin.core.annotation.Single

@Single
class BatterySettingsDataStore(
    context: Context,
    chatSettingsDataStore: ChatSettingsDataStore,
    dispatchersProvider: DispatchersProvider,
) {
    // One-time import of the 7TV live emote updates background behavior previously configured in
    // the chat settings. "Always" active in the background maps to not pausing, every timeout
    // variant maps to pausing after the shared background delay.
    @Suppress("DEPRECATION")
    private val liveUpdatesBehaviorMigration =
        object : DataMigration<BatterySettings> {
            override suspend fun shouldMigrate(currentData: BatterySettings): Boolean = !currentData.sevenTvBehaviorMigrated

            override suspend fun migrate(currentData: BatterySettings): BatterySettings = currentData.copy(
                pauseSevenTvLiveUpdates = chatSettingsDataStore.current().sevenTVLiveEmoteUpdatesBehavior != LiveUpdatesBackgroundBehavior.Always,
                sevenTvBehaviorMigrated = true,
            )

            override suspend fun cleanUp() = Unit
        }

    private val dataStore =
        createDataStore(
            fileName = "battery",
            context = context,
            defaultValue = BatterySettings(),
            serializer = BatterySettings.serializer(),
            scope = CoroutineScope(dispatchersProvider.io + SupervisorJob()),
            migrations = listOf(liveUpdatesBehaviorMigration),
        )

    val settings = dataStore.safeData(BatterySettings())
    val currentSettings =
        settings.stateIn(
            scope = CoroutineScope(dispatchersProvider.io),
            started = SharingStarted.Eagerly,
            initialValue = runBlocking { settings.first() },
        )

    fun current() = currentSettings.value

    suspend fun update(transform: suspend (BatterySettings) -> BatterySettings): Result<BatterySettings> = runCatching { dataStore.updateData(transform) }
}

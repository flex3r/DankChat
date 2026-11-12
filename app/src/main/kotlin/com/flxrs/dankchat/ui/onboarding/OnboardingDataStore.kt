package com.flxrs.dankchat.ui.onboarding

import android.content.Context
import androidx.datastore.core.DataMigration
import com.flxrs.dankchat.di.DispatchersProvider
import com.flxrs.dankchat.preferences.DankChatPreferenceStore
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
class OnboardingDataStore(
    context: Context,
    dispatchersProvider: DispatchersProvider,
    dankChatPreferenceStore: DankChatPreferenceStore,
) {
    // Detect existing users by checking if they already acknowledged the message history disclaimer.
    // If so, they've used the app before and should skip onboarding.
    private val existingUserMigration =
        object : DataMigration<OnboardingSettings> {
            override suspend fun shouldMigrate(currentData: OnboardingSettings): Boolean = !currentData.hasRunExistingUserMigration && dankChatPreferenceStore.hasMessageHistoryAcknowledged

            override suspend fun migrate(currentData: OnboardingSettings): OnboardingSettings = currentData.copy(
                hasCompletedOnboarding = true,
                hasRunExistingUserMigration = true,
                hasShownAddChannelHint = true,
                hasShownToolbarHint = true,
            )

            override suspend fun cleanUp() = Unit
        }

    private val scope = CoroutineScope(dispatchersProvider.io + SupervisorJob())

    private val dataStore =
        createDataStore(
            fileName = "onboarding",
            context = context,
            defaultValue = OnboardingSettings(),
            serializer = OnboardingSettings.serializer(),
            scope = scope,
            migrations = listOf(existingUserMigration),
        )

    val settings = dataStore.safeData(OnboardingSettings())
    val currentSettings =
        settings.stateIn(
            scope = scope,
            started = SharingStarted.Eagerly,
            initialValue = runBlocking { settings.first() },
        )

    fun current() = currentSettings.value

    suspend fun update(transform: suspend (OnboardingSettings) -> OnboardingSettings): Result<OnboardingSettings> = runCatching { dataStore.updateData(transform) }
}

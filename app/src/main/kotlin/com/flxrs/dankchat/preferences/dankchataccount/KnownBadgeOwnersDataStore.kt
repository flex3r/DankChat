package com.flxrs.dankchat.preferences.dankchataccount

import android.content.Context
import com.flxrs.dankchat.di.DispatchersProvider
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
class KnownBadgeOwnersDataStore(
    context: Context,
    dispatchersProvider: DispatchersProvider,
) {
    private val scope = CoroutineScope(dispatchersProvider.io + SupervisorJob())

    private val dataStore =
        createDataStore(
            fileName = "dankchat_badge_owners",
            context = context,
            defaultValue = KnownBadgeOwners(),
            serializer = KnownBadgeOwners.serializer(),
            scope = scope,
        )

    val settings = dataStore.safeData(KnownBadgeOwners())
    val currentSettings =
        settings.stateIn(
            scope = scope,
            started = SharingStarted.Eagerly,
            initialValue = runBlocking { settings.first() },
        )

    suspend fun update(transform: suspend (KnownBadgeOwners) -> KnownBadgeOwners) {
        dataStore.updateData(transform)
    }
}

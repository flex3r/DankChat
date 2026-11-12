package com.flxrs.dankchat.data.repo.chat

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
class ChannelSelectionDataStore(
    context: Context,
    dispatchersProvider: DispatchersProvider,
) {
    private val scope = CoroutineScope(dispatchersProvider.io + SupervisorJob())

    private val dataStore =
        createDataStore(
            fileName = "channel_selection",
            context = context,
            defaultValue = ChannelSelection(),
            serializer = ChannelSelection.serializer(),
            scope = scope,
            migrations = emptyList(),
        )

    val settings = dataStore.safeData(ChannelSelection())
    val currentSettings =
        settings.stateIn(
            scope = scope,
            started = SharingStarted.Eagerly,
            initialValue = runBlocking { settings.first() },
        )

    fun current() = currentSettings.value

    suspend fun update(transform: suspend (ChannelSelection) -> ChannelSelection): Result<ChannelSelection> = runCatching { dataStore.updateData(transform) }
}

package com.flxrs.dankchat.preferences.stream

import android.content.Context
import com.flxrs.dankchat.R
import com.flxrs.dankchat.di.DispatchersProvider
import com.flxrs.dankchat.utils.datastore.PreferenceKeys
import com.flxrs.dankchat.utils.datastore.booleanOrDefault
import com.flxrs.dankchat.utils.datastore.createDataStore
import com.flxrs.dankchat.utils.datastore.dankChatPreferencesMigration
import com.flxrs.dankchat.utils.datastore.safeData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.koin.core.annotation.Single

@Single
class StreamsSettingsDataStore(
    context: Context,
    dispatchersProvider: DispatchersProvider,
) {

    private enum class StreamsPreferenceKeys(override val id: Int) : PreferenceKeys {
        FetchStreams(R.string.preference_fetch_streams_key),
        ShowStreamInfo(R.string.preference_streaminfo_key),
        PreventStreamReloads(R.string.preference_retain_webview_new_key),
        EnablePiP(R.string.preference_pip_key),
    }

    private val initialMigration = dankChatPreferencesMigration<StreamsPreferenceKeys, StreamsSettings>(context) { acc, key, value ->
        when (key) {
            StreamsPreferenceKeys.FetchStreams         -> acc.copy(fetchStreams = value.booleanOrDefault(acc.fetchStreams))
            StreamsPreferenceKeys.ShowStreamInfo       -> acc.copy(showStreamInfo = value.booleanOrDefault(acc.showStreamInfo))
            StreamsPreferenceKeys.PreventStreamReloads -> acc.copy(preventStreamReloads = value.booleanOrDefault(acc.preventStreamReloads))
            StreamsPreferenceKeys.EnablePiP            -> acc.copy(enablePiP = value.booleanOrDefault(acc.enablePiP))
        }
    }

    private val dataStore = createDataStore(
        fileName = "streams",
        context = context,
        defaultValue = StreamsSettings(),
        serializer = StreamsSettings.serializer(),
        scope = CoroutineScope(dispatchersProvider.io + SupervisorJob()),
        migrations = listOf(initialMigration),
    )

    val settings = dataStore.safeData(StreamsSettings())
    fun current() = runBlocking { settings.first() }

    suspend fun update(transform: suspend (StreamsSettings) -> StreamsSettings) {
        runCatching { dataStore.updateData(transform) }
    }
}

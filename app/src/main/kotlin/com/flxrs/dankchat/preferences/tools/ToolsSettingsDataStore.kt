package com.flxrs.dankchat.preferences.tools

import android.content.Context
import androidx.core.content.edit
import androidx.datastore.core.DataMigration
import com.flxrs.dankchat.R
import com.flxrs.dankchat.di.DispatchersProvider
import com.flxrs.dankchat.utils.datastore.PreferenceKeys
import com.flxrs.dankchat.utils.datastore.booleanOrDefault
import com.flxrs.dankchat.utils.datastore.booleanOrNull
import com.flxrs.dankchat.utils.datastore.createDataStore
import com.flxrs.dankchat.utils.datastore.dankChatPreferencesMigration
import com.flxrs.dankchat.utils.datastore.stringSetOrDefault
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import org.koin.core.annotation.Single

@Single
class ToolsSettingsDataStore(
    context: Context,
    dispatchersProvider: DispatchersProvider,
) {

    private enum class ToolsPreferenceKeys(override val id: Int) : PreferenceKeys {
        TTS(R.string.preference_tts_key),
        TTSQueue(R.string.preference_tts_queue_key),
        TTSMessageFormat(R.string.preference_tts_message_format_key),
        TTSForceEnglish(R.string.preference_tts_force_english_key),
        TTSMessageIgnoreUrl(R.string.preference_tts_message_ignore_url_key),
        TTSMessageIgnoreEmote(R.string.preference_tts_message_ignore_emote_key),
        TTSUserIgnoreList(R.string.preference_tts_user_ignore_list_key),
    }

    private enum class UploaderKeys(val key: String) {
        UploadUrl("uploaderUrl"),
        FormField("uploaderFormField"),
        Headers("uploaderHeaders"),
        ImageLinkPattern("uploaderImageLink"),
        DeletionLinkPattern("uploaderDeletionLink"),
    }

    private val initialMigration = dankChatPreferencesMigration<ToolsPreferenceKeys, ToolsSettings>(context) { acc, key, value ->
        when (key) {
            ToolsPreferenceKeys.TTS                   -> acc.copy(ttsEnabled = value.booleanOrDefault(acc.ttsEnabled))
            ToolsPreferenceKeys.TTSQueue              -> acc.copy(
                ttsPlayMode = value.booleanOrNull()?.let {
                    if (it) TTSPlayMode.Queue else TTSPlayMode.Newest
                } ?: acc.ttsPlayMode
            )

            ToolsPreferenceKeys.TTSMessageFormat      -> acc.copy(
                ttsMessageFormat = value.booleanOrNull()?.let {
                    if (it) TTSMessageFormat.UserAndMessage else TTSMessageFormat.Message
                } ?: acc.ttsMessageFormat
            )

            ToolsPreferenceKeys.TTSForceEnglish       -> acc.copy(ttsForceEnglish = value.booleanOrDefault(acc.ttsForceEnglish))
            ToolsPreferenceKeys.TTSMessageIgnoreUrl   -> acc.copy(ttsIgnoreUrls = value.booleanOrDefault(acc.ttsIgnoreUrls))
            ToolsPreferenceKeys.TTSMessageIgnoreEmote -> acc.copy(ttsIgnoreEmotes = value.booleanOrDefault(acc.ttsIgnoreEmotes))
            ToolsPreferenceKeys.TTSUserIgnoreList     -> acc.copy(ttsUserIgnoreList = value.stringSetOrDefault(acc.ttsUserIgnoreList))
        }
    }

    private val dankchatPreferences = context.getSharedPreferences(context.getString(R.string.shared_preference_key), Context.MODE_PRIVATE)
    private val uploaderMigration = object : DataMigration<ToolsSettings> {
        override suspend fun migrate(currentData: ToolsSettings): ToolsSettings {
            val current = currentData.uploaderConfig
            val url = dankchatPreferences.getString(UploaderKeys.UploadUrl.key, current.uploadUrl) ?: current.uploadUrl
            val field = dankchatPreferences.getString(UploaderKeys.FormField.key, current.formField) ?: current.formField
            val isDefault = url == ImageUploaderConfig.DEFAULT.uploadUrl && field == ImageUploaderConfig.DEFAULT.formField
            val headers = dankchatPreferences.getString(UploaderKeys.Headers.key, null)
            val link = dankchatPreferences.getString(UploaderKeys.ImageLinkPattern.key, null)
            val delete = dankchatPreferences.getString(UploaderKeys.DeletionLinkPattern.key, null)
            return currentData.copy(
                uploaderConfig = current.copy(
                    uploadUrl = url,
                    formField = field,
                    headers = headers,
                    imageLinkPattern = if (isDefault) ImageUploaderConfig.DEFAULT.imageLinkPattern else link.orEmpty(),
                    deletionLinkPattern = if (isDefault) ImageUploaderConfig.DEFAULT.deletionLinkPattern else delete.orEmpty(),
                )
            )
        }

        override suspend fun shouldMigrate(currentData: ToolsSettings): Boolean = UploaderKeys.entries.any { dankchatPreferences.contains(it.key) }
        override suspend fun cleanUp() = dankchatPreferences.edit { UploaderKeys.entries.forEach { remove(it.key) } }
    }

    private val dataStore = createDataStore(
        fileName = "tools",
        context = context,
        defaultValue = ToolsSettings(),
        serializer = ToolsSettings.serializer(),
        scope = CoroutineScope(dispatchersProvider.io + SupervisorJob()),
        migrations = listOf(initialMigration, uploaderMigration),
    )

    val settings = dataStore.data
    val uploadConfig = settings
        .map { it.uploaderConfig }
        .distinctUntilChanged()

    fun current() = runBlocking { settings.first() }

    val ttsEnabled = settings
        .map { it.ttsEnabled }
        .distinctUntilChanged()
    val ttsForceEnglishChanged = settings
        .map { it.ttsForceEnglish }
        .distinctUntilChanged()
        .drop(1)

    suspend fun update(transform: suspend (ToolsSettings) -> ToolsSettings) {
        runCatching { dataStore.updateData(transform) }
    }
}

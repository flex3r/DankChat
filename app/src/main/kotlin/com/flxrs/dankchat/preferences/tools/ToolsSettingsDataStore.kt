package com.flxrs.dankchat.preferences.tools

import android.content.Context
import com.flxrs.dankchat.R
import com.flxrs.dankchat.di.DispatchersProvider
import com.flxrs.dankchat.utils.datastore.PreferenceKeys
import com.flxrs.dankchat.utils.datastore.booleanOrDefault
import com.flxrs.dankchat.utils.datastore.booleanOrNull
import com.flxrs.dankchat.utils.datastore.createDataStore
import com.flxrs.dankchat.utils.datastore.dankChatMigration
import com.flxrs.dankchat.utils.datastore.dankChatPreferencesMigration
import com.flxrs.dankchat.utils.datastore.stringOrDefault
import com.flxrs.dankchat.utils.datastore.stringOrNull
import com.flxrs.dankchat.utils.datastore.stringSetOrDefault
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
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
            ToolsPreferenceKeys.TTS -> acc.copy(ttsEnabled = value.booleanOrDefault(acc.ttsEnabled))
            ToolsPreferenceKeys.TTSQueue -> acc.copy(
                ttsPlayMode = value.booleanOrNull()?.let {
                    if (it) TTSPlayMode.Queue else TTSPlayMode.Newest
                } ?: acc.ttsPlayMode
            )
            ToolsPreferenceKeys.TTSMessageFormat -> acc.copy(
                ttsMessageFormat = value.booleanOrNull()?.let {
                    if (it) TTSMessageFormat.UserAndMessage else TTSMessageFormat.Message
                } ?: acc.ttsMessageFormat
            )
            ToolsPreferenceKeys.TTSForceEnglish -> acc.copy(ttsForceEnglish = value.booleanOrDefault(acc.ttsForceEnglish))
            ToolsPreferenceKeys.TTSMessageIgnoreUrl -> acc.copy(ttsIgnoreUrls = value.booleanOrDefault(acc.ttsIgnoreUrls))
            ToolsPreferenceKeys.TTSMessageIgnoreEmote -> acc.copy(ttsIgnoreEmotes = value.booleanOrDefault(acc.ttsIgnoreEmotes))
            ToolsPreferenceKeys.TTSUserIgnoreList -> acc.copy(ttsUserIgnoreList = value.stringSetOrDefault(acc.ttsUserIgnoreList))
        }
    }

    private val dankchatPreferences = context.getSharedPreferences(context.getString(R.string.shared_preference_key), Context.MODE_PRIVATE)
    private val uploaderMigration = dankChatMigration<UploaderKeys, ToolsSettings>(context, dankchatPreferences, UploaderKeys::key) { acc, key, value ->
        val config = acc.uploaderConfig
        when (key) {
            UploaderKeys.UploadUrl           -> acc.copy(uploaderConfig = config.copy(uploadUrl = value.stringOrDefault(config.uploadUrl)))
            UploaderKeys.FormField           -> acc.copy(uploaderConfig = config.copy(formField = value.stringOrDefault(config.formField)))
            UploaderKeys.Headers             -> acc.copy(uploaderConfig = config.copy(headers = value.stringOrNull() ?: config.headers))
            UploaderKeys.ImageLinkPattern    -> acc.copy(uploaderConfig = config.copy(imageLinkPattern = value.stringOrNull() ?: config.imageLinkPattern))
            UploaderKeys.DeletionLinkPattern -> acc.copy(uploaderConfig = config.copy(deletionLinkPattern = value.stringOrNull() ?: config.deletionLinkPattern))
        }
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
    fun current() = runBlocking { settings.first() }

    suspend fun update(transform: suspend (ToolsSettings) -> ToolsSettings) {
        runCatching { dataStore.updateData(transform) }
    }
}

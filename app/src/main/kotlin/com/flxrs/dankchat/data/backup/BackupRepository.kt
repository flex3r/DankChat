package com.flxrs.dankchat.data.backup

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.flxrs.dankchat.BuildConfig
import com.flxrs.dankchat.data.database.DankChatDatabase
import com.flxrs.dankchat.data.repo.chat.ChatChannelProvider
import com.flxrs.dankchat.di.DispatchersProvider
import com.flxrs.dankchat.preferences.DankChatPreferenceStore
import com.flxrs.dankchat.preferences.appearance.AppearanceSettingsDataStore
import com.flxrs.dankchat.preferences.battery.BatterySettingsDataStore
import com.flxrs.dankchat.preferences.chat.ChatSettingsDataStore
import com.flxrs.dankchat.preferences.developer.DeveloperSettingsDataStore
import com.flxrs.dankchat.preferences.notifications.NotificationsSettingsDataStore
import com.flxrs.dankchat.preferences.stream.StreamsSettingsDataStore
import com.flxrs.dankchat.preferences.tools.ToolsSettingsDataStore
import com.flxrs.dankchat.utils.extensions.runCatchingCancellable
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.json.encodeToStream
import org.koin.core.annotation.Single

@Single
class BackupRepository(
    private val context: Context,
    private val database: DankChatDatabase,
    private val preferenceStore: DankChatPreferenceStore,
    private val chatChannelProvider: ChatChannelProvider,
    private val appearanceSettingsDataStore: AppearanceSettingsDataStore,
    private val chatSettingsDataStore: ChatSettingsDataStore,
    private val notificationsSettingsDataStore: NotificationsSettingsDataStore,
    private val streamsSettingsDataStore: StreamsSettingsDataStore,
    private val batterySettingsDataStore: BatterySettingsDataStore,
    private val toolsSettingsDataStore: ToolsSettingsDataStore,
    private val developerSettingsDataStore: DeveloperSettingsDataStore,
    private val dispatchersProvider: DispatchersProvider,
) {
    private val json =
        Json {
            ignoreUnknownKeys = true
            coerceInputValues = true
            prettyPrint = true
        }

    /** Returns the number of exported channels. */
    suspend fun exportChannels(uri: Uri): Result<Int> = withContext(dispatchersProvider.io) {
        runCatchingCancellable {
            val backup = createChannelListBackup()
            write(uri, backup)
            backup.channels.size
        }
    }

    suspend fun exportChannelsAsText(): Result<String> = withContext(dispatchersProvider.io) {
        runCatchingCancellable { json.encodeToString(createChannelListBackup()) }
    }

    /** Adds the channels that are not added yet and returns how many were added. */
    suspend fun importChannels(uri: Uri): Result<Int> = withContext(dispatchersProvider.io) {
        runCatchingCancellable { addChannels(read<ChannelListBackup>(uri)) }
    }

    /** Adds the channels that are not added yet and returns how many were added. */
    suspend fun importChannels(text: String): Result<Int> = withContext(dispatchersProvider.io) {
        runCatchingCancellable { addChannels(json.decodeFromString<ChannelListBackup>(text)) }
    }

    suspend fun exportSettings(uri: Uri): Result<Unit> = withContext(dispatchersProvider.io) {
        runCatchingCancellable { write(uri, createSettingsBackup()) }
    }

    suspend fun exportSettingsAsText(): Result<String> = withContext(dispatchersProvider.io) {
        runCatchingCancellable { json.encodeToString(createSettingsBackup()) }
    }

    suspend fun readSettings(uri: Uri): Result<SettingsBackup> = withContext(dispatchersProvider.io) {
        runCatchingCancellable { read<SettingsBackup>(uri) }
    }

    suspend fun readSettings(text: String): Result<SettingsBackup> = withContext(dispatchersProvider.io) {
        runCatchingCancellable { json.decodeFromString<SettingsBackup>(text) }
    }

    /** Replaces everything the backup contains, the app has to restart afterwards. */
    suspend fun restoreSettings(backup: SettingsBackup): Result<Unit> = withContext(dispatchersProvider.io) {
        runCatchingCancellable {
            database.withTransaction {
                with(database.messageHighlightDao()) {
                    deleteAllHighlights()
                    addHighlights(backup.messageHighlights)
                }
                with(database.userHighlightDao()) {
                    deleteAllHighlights()
                    addHighlights(backup.userHighlights)
                }
                with(database.badgeHighlightDao()) {
                    deleteAllHighlights()
                    addHighlights(backup.badgeHighlights)
                }
                with(database.blacklistedUserDao()) {
                    deleteAllBlacklistedUsers()
                    addBlacklistedUsers(backup.highlightBlacklist)
                }
                with(database.messageIgnoreDao()) {
                    deleteAllIgnores()
                    addIgnores(backup.messageIgnores)
                }
                with(database.userIgnoreDao()) {
                    deleteAllIgnores()
                    addIgnores(backup.userIgnores)
                }
                with(database.userDisplayDao()) {
                    deleteAll()
                    upsertAll(backup.userDisplays)
                }
            }
            appearanceSettingsDataStore.update { backup.appearance }
            chatSettingsDataStore.update { backup.chat }
            notificationsSettingsDataStore.update { backup.notifications }
            streamsSettingsDataStore.update { backup.streams }
            batterySettingsDataStore.update { backup.battery }
            toolsSettingsDataStore.update { backup.tools }
            developerSettingsDataStore.update { backup.developer }
            preferenceStore.replaceChannels(backup.channels.validChannels().map(BackupChannel::toChannelWithRename))
        }
    }

    private fun currentChannels(): List<BackupChannel> = preferenceStore.getChannelsWithRenames().map { BackupChannel(name = it.channel, rename = it.rename) }

    private fun createChannelListBackup() = ChannelListBackup(version = BACKUP_VERSION, channels = currentChannels())

    private suspend fun createSettingsBackup() = SettingsBackup(
        version = BACKUP_VERSION,
        appVersion = BuildConfig.VERSION_NAME,
        channels = currentChannels(),
        appearance = appearanceSettingsDataStore.settings.first(),
        chat = chatSettingsDataStore.settings.first(),
        notifications = notificationsSettingsDataStore.settings.first(),
        streams = streamsSettingsDataStore.settings.first(),
        battery = batterySettingsDataStore.settings.first(),
        tools = toolsSettingsDataStore.settings.first(),
        developer = developerSettingsDataStore.settings.first(),
        messageHighlights = database.messageHighlightDao().getMessageHighlights(),
        userHighlights = database.userHighlightDao().getUserHighlights(),
        badgeHighlights = database.badgeHighlightDao().getBadgeHighlights(),
        highlightBlacklist = database.blacklistedUserDao().getBlacklistedUserFlow().first(),
        messageIgnores = database.messageIgnoreDao().getMessageIgnores(),
        userIgnores = database.userIgnoreDao().getUserIgnoresFlow().first(),
        userDisplays = database.userDisplayDao().getUserDisplaysFlow().first(),
    )

    private fun addChannels(backup: ChannelListBackup): Int {
        val current = preferenceStore.channels
        val added =
            backup.channels
                .validChannels()
                .filterNot { it.name in current }
        if (added.isNotEmpty()) {
            preferenceStore.channels = current + added.map { it.name }
            added.forEach { preferenceStore.setRenamedChannel(it.toChannelWithRename()) }
            if (chatChannelProvider.activeChannel.value == null) {
                chatChannelProvider.setActiveChannel(added.first().name)
            }
        }
        return added.size
    }

    private inline fun <reified T> read(uri: Uri): T {
        val stream = checkNotNull(context.contentResolver.openInputStream(uri)) { "Failed to open $uri" }
        return stream.use { json.decodeFromStream<T>(it) }
    }

    private inline fun <reified T> write(
        uri: Uri,
        value: T,
    ) {
        // Truncates, so overwriting a longer file leaves no trailing data
        val stream = checkNotNull(context.contentResolver.openOutputStream(uri, "wt")) { "Failed to open $uri" }
        stream.use { json.encodeToStream(value, it) }
    }

    private fun List<BackupChannel>.validChannels(): List<BackupChannel> = this
        .map { it.copy(name = it.name.lowercase()) }
        .filter { it.name.value.matches(CHANNEL_NAME_REGEX) }
        .distinctBy { it.name }

    companion object {
        private const val BACKUP_VERSION = 1
        private val CHANNEL_NAME_REGEX = "\\w+".toRegex()
    }
}

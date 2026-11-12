package com.flxrs.dankchat.data.auth

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.datastore.core.DataMigration
import com.flxrs.dankchat.data.DisplayName
import com.flxrs.dankchat.data.UserId
import com.flxrs.dankchat.data.UserName
import com.flxrs.dankchat.data.toDisplayName
import com.flxrs.dankchat.data.toUserId
import com.flxrs.dankchat.data.toUserName
import com.flxrs.dankchat.di.DispatchersProvider
import com.flxrs.dankchat.utils.datastore.createDataStore
import com.flxrs.dankchat.utils.datastore.safeData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.koin.core.annotation.Single

@Single
class AuthDataStore(
    context: Context,
    dispatchersProvider: DispatchersProvider,
) {
    private val legacyPrefs: SharedPreferences =
        context.getSharedPreferences(
            "com.flxrs.dankchat_preferences",
            Context.MODE_PRIVATE,
        )

    private val sharedPrefsMigration =
        object : DataMigration<AuthSettings> {
            override suspend fun shouldMigrate(currentData: AuthSettings): Boolean = legacyPrefs.contains(LEGACY_LOGGED_IN_KEY) ||
                legacyPrefs.contains(LEGACY_OAUTH_KEY) ||
                legacyPrefs.contains(LEGACY_NAME_KEY)

            override suspend fun migrate(currentData: AuthSettings): AuthSettings {
                val isLoggedIn = legacyPrefs.getBoolean(LEGACY_LOGGED_IN_KEY, false)
                val oAuthKey = legacyPrefs.getString(LEGACY_OAUTH_KEY, null)
                val userName = legacyPrefs.getString(LEGACY_NAME_KEY, null)?.ifBlank { null }
                val displayName = legacyPrefs.getString(LEGACY_DISPLAY_NAME_KEY, null)?.ifBlank { null }
                val userId = legacyPrefs.getString(LEGACY_ID_STRING_KEY, null)?.ifBlank { null }
                val clientId = legacyPrefs.getString(LEGACY_CLIENT_ID_KEY, null) ?: AuthSettings.DEFAULT_CLIENT_ID

                return currentData.copy(
                    oAuthKey = oAuthKey,
                    userName = userName,
                    displayName = displayName,
                    userId = userId,
                    clientId = clientId,
                    isLoggedIn = isLoggedIn,
                )
            }

            override suspend fun cleanUp() {
                legacyPrefs.edit {
                    remove(LEGACY_LOGGED_IN_KEY)
                    remove(LEGACY_OAUTH_KEY)
                    remove(LEGACY_NAME_KEY)
                    remove(LEGACY_DISPLAY_NAME_KEY)
                    remove(LEGACY_ID_STRING_KEY)
                    remove(LEGACY_CLIENT_ID_KEY)
                }
            }
        }

    private val dataStore =
        createDataStore(
            fileName = "auth",
            context = context,
            defaultValue = AuthSettings(),
            serializer = AuthSettings.serializer(),
            scope = CoroutineScope(dispatchersProvider.io + SupervisorJob()),
            migrations = listOf(sharedPrefsMigration),
        )

    val settings = dataStore.safeData(AuthSettings())
    val currentSettings =
        settings.stateIn(
            scope = CoroutineScope(dispatchersProvider.io),
            started = SharingStarted.Eagerly,
            initialValue = runBlocking { settings.first() },
        )

    private val persistScope = CoroutineScope(dispatchersProvider.io + SupervisorJob())

    fun current() = currentSettings.value

    val isLoggedIn: Boolean get() = current().isLoggedIn
    val oAuthKey: String? get() = current().oAuthKey
    val userName: UserName? get() = current().userName?.toUserName()
    val displayName: DisplayName? get() = current().displayName?.toDisplayName()
    val userIdString: UserId? get() = current().userId?.toUserId()
    val clientId: String get() = current().clientId

    suspend fun update(transform: suspend (AuthSettings) -> AuthSettings): Result<AuthSettings> = runCatching { dataStore.updateData(transform) }

    /** Fire-and-forget update that survives caller cancellation (e.g. config change). */
    fun updateAsync(transform: suspend (AuthSettings) -> AuthSettings) {
        persistScope.launch { update(transform) }
    }

    suspend fun login(
        oAuthKey: String,
        userName: String,
        userId: String,
        clientId: String,
    ) {
        update {
            it.copy(
                oAuthKey = "oauth:$oAuthKey",
                userName = userName,
                userId = userId,
                clientId = clientId,
                isLoggedIn = true,
            )
        }
    }

    suspend fun clearLogin() {
        update {
            it.copy(
                oAuthKey = null,
                userName = null,
                displayName = null,
                userId = null,
                clientId = AuthSettings.DEFAULT_CLIENT_ID,
                isLoggedIn = false,
            )
        }
    }

    companion object {
        private const val LEGACY_LOGGED_IN_KEY = "loggedIn"
        private const val LEGACY_OAUTH_KEY = "oAuthKey"
        private const val LEGACY_NAME_KEY = "nameKey"
        private const val LEGACY_DISPLAY_NAME_KEY = "displayNameKey"
        private const val LEGACY_ID_STRING_KEY = "idStringKey"
        private const val LEGACY_CLIENT_ID_KEY = "clientIdKey"
    }
}

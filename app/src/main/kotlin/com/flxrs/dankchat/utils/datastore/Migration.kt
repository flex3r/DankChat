package com.flxrs.dankchat.utils.datastore

import android.content.Context
import androidx.annotation.StringRes
import androidx.core.content.edit
import androidx.datastore.core.DataMigration
import androidx.preference.PreferenceManager
import kotlin.enums.enumEntries

interface PreferenceKeys {
    @get:StringRes
    val id: Int
}

inline fun <reified K, T> dankChatPreferencesMigration(
    context: Context,
    crossinline migrateValue: suspend (currentData: T, key: K, value: Any?) -> T,
): DataMigration<T> where K : Enum<K>, K : PreferenceKeys = object : DataMigration<T> {
    val prefs = PreferenceManager.getDefaultSharedPreferences(context)
    val map = enumEntries<K>().associateBy { context.getString(it.id) }
    override suspend fun migrate(currentData: T): T {
        return runCatching {
            prefs.all.filterKeys { it in map.keys }.entries.fold(currentData) { acc, (key, value) ->
                val mapped = map[key] ?: return@fold acc
                migrateValue(acc, mapped, value)
            }
        }.getOrDefault(currentData)
    }
    override suspend fun shouldMigrate(currentData: T): Boolean = map.keys.any(prefs::contains)
    override suspend fun cleanUp() = prefs.edit { map.keys.forEach(::remove) }
}

fun Any?.booleanOrDefault(default: Boolean) = if (this is Boolean) this else default
fun Any?.intOrDefault(default: Int) = if (this is Int) this else default
fun Any?.stringOrDefault(default: String) = if (this is String) this else default
fun <T> Any?.mappedStringOrDefault(default: T, transform: (String) -> T?) = if (this is String) transform(this) ?: default else default

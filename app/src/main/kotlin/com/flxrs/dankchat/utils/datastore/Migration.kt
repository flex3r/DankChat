package com.flxrs.dankchat.utils.datastore

import android.content.Context
import android.content.SharedPreferences
import androidx.annotation.StringRes
import androidx.core.content.edit
import androidx.datastore.core.DataMigration
import kotlin.enums.EnumEntries
import kotlin.enums.enumEntries

interface PreferenceKeys {
    @get:StringRes
    val id: Int
}

inline fun <reified K, T> dankChatPreferencesMigration(
    context: Context,
    prefs: SharedPreferences = context.getSharedPreferences("${context.packageName}_preferences", Context.MODE_PRIVATE),
    crossinline migrateValue: suspend (currentData: T, key: K, value: Any?) -> T,
): DataMigration<T> where K : Enum<K>, K : PreferenceKeys = dankChatMigration(
    context = context,
    prefs = prefs,
    keyMapper = { context.getString(it.id) },
    migrateValue = migrateValue,
)

inline fun <reified K, T> dankChatMigration(
    context: Context,
    prefs: SharedPreferences = context.getSharedPreferences("${context.packageName}_preferences", Context.MODE_PRIVATE),
    crossinline keyMapper: (K) -> String,
    crossinline migrateValue: suspend (currentData: T, key: K, value: Any?) -> T,
): DataMigration<T> where K : Enum<K> = object : DataMigration<T> {
    val map = enumEntries<K>().associateBy(keyMapper)
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

fun Any?.booleanOrNull() = this as? Boolean
fun Any?.booleanOrDefault(default: Boolean) = this as? Boolean ?: default
fun Any?.intOrDefault(default: Int) = this as? Int ?: default

fun Any?.stringOrNull() = this as? String
fun Any?.stringOrDefault(default: String) = this as? String ?: default
fun <T : Enum<T>> Any?.mappedStringOrDefault(original: Array<String>, enumEntries: EnumEntries<T>, default: T): T {
    return stringOrNull()?.let { enumEntries.getOrNull(original.indexOf(it)) } ?: default
}

@Suppress("UNCHECKED_CAST")
fun Any?.stringSetOrNull() = this as? Set<String>
@Suppress("UNCHECKED_CAST")
fun Any?.stringSetOrDefault(default: Set<String>) = this as? Set<String> ?: default
fun <T : Enum<T>> Any?.mappedStringSetOrDefault(original: Array<String>, enumEntries: EnumEntries<T>, default: List<T>): List<T> {
    return stringSetOrNull()?.toList()?.mapNotNull { enumEntries.getOrNull(original.indexOf(it)) } ?: default
}

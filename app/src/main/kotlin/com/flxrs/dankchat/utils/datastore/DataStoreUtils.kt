package com.flxrs.dankchat.utils.datastore

import android.content.Context
import androidx.datastore.core.DataMigration
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.IOException
import androidx.datastore.core.okio.OkioStorage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.serialization.KSerializer
import okio.FileSystem
import okio.Path.Companion.toPath

fun <T> createDataStore(
    fileName: String,
    context: Context,
    defaultValue: T,
    serializer: KSerializer<T>,
    scope: CoroutineScope,
    migrations: List<DataMigration<T>> = emptyList(),
) = DataStoreFactory.create(
    storage = OkioStorage(
        fileSystem = FileSystem.SYSTEM,
        serializer = DataStoreKotlinxSerializer(
            defaultValue = defaultValue,
            serializer = serializer,
        ),
        producePath = { context.filesDir.resolve(fileName).absolutePath.toPath() },
    ),
    scope = scope,
    migrations = migrations,
)

inline fun <reified T> DataStore<T>.safeData(defaultValue: T): Flow<T> = data.catch { e ->
    when (e) {
        is IOException -> emit(defaultValue)
        else           -> throw e
    }
}

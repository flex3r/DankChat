package com.flxrs.dankchat.utils.datastore

import androidx.datastore.core.okio.OkioSerializer
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.okio.decodeFromBufferedSource
import kotlinx.serialization.json.okio.encodeToBufferedSink
import kotlinx.serialization.modules.SerializersModule
import okio.BufferedSink
import okio.BufferedSource

class DataStoreKotlinxSerializer<T>(
    override val defaultValue: T,
    private val serializer: KSerializer<T>,
    private val customSerializersModule: SerializersModule? = null,
) : OkioSerializer<T> {

    private val json = Json {
        ignoreUnknownKeys = true
        customSerializersModule?.let {
            serializersModule = it
        }
    }

    override suspend fun readFrom(source: BufferedSource): T = runCatching {
        json.decodeFromBufferedSource(serializer, source)
    }.getOrDefault(defaultValue)

    override suspend fun writeTo(t: T, sink: BufferedSink) {
        json.encodeToBufferedSink(serializer, t, sink)
    }
}

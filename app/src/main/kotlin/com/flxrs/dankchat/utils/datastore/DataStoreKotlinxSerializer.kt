package com.flxrs.dankchat.utils.datastore

import androidx.datastore.core.okio.OkioSerializer
import io.github.oshai.kotlinlogging.KotlinLogging
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
    customSerializersModule: SerializersModule? = null,
) : OkioSerializer<T> {
    private val json =
        Json {
            ignoreUnknownKeys = true
            // An enum value this version does not know resets its own setting instead of the whole store
            coerceInputValues = true
            customSerializersModule?.let {
                serializersModule = it
            }
        }

    override suspend fun readFrom(source: BufferedSource): T = runCatching {
        json.decodeFromBufferedSource(serializer, source)
    }.getOrElse {
        logger.error(it) { "Failed to read ${serializer.descriptor.serialName}, using the default values" }
        defaultValue
    }

    override suspend fun writeTo(
        t: T,
        sink: BufferedSink,
    ) {
        json.encodeToBufferedSink(serializer, t, sink)
    }

    companion object {
        private val logger = KotlinLogging.logger("DataStoreKotlinxSerializer")
    }
}

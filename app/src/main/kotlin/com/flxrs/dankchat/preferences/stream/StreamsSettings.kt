package com.flxrs.dankchat.preferences.stream

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

@Serializable
data class StreamsSettings(
    val fetchStreams: Boolean = true,
    val showStreamInfo: Boolean = true,
    val preventStreamReloads: Boolean = true,
    val enablePiP: Boolean = false,
) {

    @Transient
    val pipAllowed = fetchStreams && preventStreamReloads && enablePiP
}

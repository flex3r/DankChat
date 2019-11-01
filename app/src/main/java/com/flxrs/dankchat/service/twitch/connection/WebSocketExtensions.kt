package com.flxrs.dankchat.service.twitch.connection

import okhttp3.WebSocket

fun WebSocket.sendMessage(msg: String) {
    send("${msg.trimEnd()}\r\n")
}

fun WebSocket.handlePing() {
    sendMessage("PONG :tmi.twitch.tv")
}

fun WebSocket.joinChannels(channels: Collection<String>) {
    if (channels.isNotEmpty()) {
        sendMessage("JOIN ${channels.joinToString(",") { "#$it" }}")
    }
}
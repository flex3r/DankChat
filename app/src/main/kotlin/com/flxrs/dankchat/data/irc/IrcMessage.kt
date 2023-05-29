package com.flxrs.dankchat.data.irc

import java.text.ParseException

data class IrcMessage(
    val raw: String,
    val prefix: String,
    val command: String,
    val params: List<String> = listOf(),
    val tags: Map<String, String> = mapOf()
) {

    fun isLoginFailed(): Boolean {
        return command == "NOTICE" && params.getOrNull(0) == "*" && params.getOrNull(1) == "Login authentication failed"
    }

    companion object {

        fun parse(message: String): IrcMessage {
            var pos = 0
            var nextSpace: Int
            var prefix = ""
            var command = ""
            val params = mutableListOf<String>()
            val tags = mutableMapOf<String, String>()

            fun skipTrailingWhitespace() {
                while (message[pos] == ' ') pos++
            }

            //tags
            if (message[pos] == '@') {
                nextSpace = message.indexOf(' ')

                if (nextSpace == -1) {
                    throw ParseException("Malformed IRC message", pos)
                }

                tags.putAll(
                    message
                        .substring(1, nextSpace)
                        .split(';')
                        .associate {
                            val kv = it.split('=')
                            val v = when (kv.size) {
                                2 -> kv[1].replace("\\:", ";")
                                    .replace("\\s", " ")
                                    .replace("\\r", "\r")
                                    .replace("\\n", "\n")
                                    .replace("\\\\", "\\")

                                else -> "true"
                            }
                            kv[0] to v
                        })
                pos = nextSpace + 1
            }

            skipTrailingWhitespace()

            //prefix
            if (message[pos] == ':') {
                nextSpace = message.indexOf(' ', pos)

                if (nextSpace == -1) {
                    throw ParseException("Malformed IRC message", pos)
                }

                prefix = message.substring(pos + 1, nextSpace)
                pos = nextSpace + 1
                skipTrailingWhitespace()
            }

            nextSpace = message.indexOf(' ', pos)

            if (nextSpace == -1) {
                if (message.length > pos) command = message.substring(pos)
            } else {
                command = message.substring(pos, nextSpace)
                pos = nextSpace + 1
                skipTrailingWhitespace()

                while (pos < message.length) {
                    nextSpace = message.indexOf(' ', pos)

                    if (message[pos] == ':') {
                        params += message.substring(pos + 1)
                        break
                    }

                    if (nextSpace != -1) {
                        params += message.substring(pos, nextSpace)
                        pos = nextSpace + 1
                        skipTrailingWhitespace()
                        continue
                    } else {
                        params += message.substring(pos)
                        break
                    }
                }
            }
            return IrcMessage(message, prefix, command, params, tags)
        }
    }
}

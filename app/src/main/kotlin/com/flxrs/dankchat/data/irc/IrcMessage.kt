package com.flxrs.dankchat.data.irc

import java.text.ParseException
import java.util.concurrent.ConcurrentHashMap

data class IrcMessage(
    val raw: String,
    val prefix: String,
    val command: String,
    val params: List<String> = listOf(),
    val tags: Map<String, String> = mapOf(),
) {
    fun isLoginFailed(): Boolean = command == "NOTICE" && params.getOrNull(0) == "*" && params.getOrNull(1) == "Login authentication failed"

    companion object {
        private const val MAX_SHARED_TAG_KEYS = 256

        // Messages keep their tags and every message has the same keys, so equal keys share one instance
        private val sharedTagKeys = ConcurrentHashMap<String, String>()

        private fun sharedTagKey(key: String): String = when {
            sharedTagKeys.size >= MAX_SHARED_TAG_KEYS -> sharedTagKeys[key] ?: key
            else -> sharedTagKeys.putIfAbsent(key, key) ?: key
        }

        private fun unescapeIrcTagValue(value: String): String {
            val idx = value.indexOf('\\')
            if (idx == -1) return value // fast path: no escapes (most values)

            return buildString(value.length) {
                var i = 0
                while (i < value.length) {
                    if (value[i] == '\\' && i + 1 < value.length) {
                        when (value[i + 1]) {
                            ':' -> {
                                append(';')
                            }

                            's' -> {
                                append(' ')
                            }

                            'r' -> {
                                append('\r')
                            }

                            'n' -> {
                                append('\n')
                            }

                            '\\' -> {
                                append('\\')
                            }

                            else -> {
                                append(value[i])
                                append(value[i + 1])
                            }
                        }
                        i += 2
                    } else {
                        append(value[i])
                        i++
                    }
                }
            }
        }

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

            // tags
            if (message[pos] == '@') {
                nextSpace = message.indexOf(' ')

                if (nextSpace == -1) {
                    throw ParseException("Malformed IRC message", pos)
                }

                // Index-based tag parsing: walk the tag section without split() allocations
                var tagStart = 1 // skip '@'
                while (tagStart < nextSpace) {
                    val semiIdx = message.indexOf(';', tagStart)
                    val tagEnd = if (semiIdx == -1 || semiIdx > nextSpace) nextSpace else semiIdx

                    val eqIdx = message.indexOf('=', tagStart)
                    if (eqIdx != -1 && eqIdx < tagEnd) {
                        val key = sharedTagKey(message.substring(tagStart, eqIdx))
                        val rawValue = message.substring(eqIdx + 1, tagEnd)
                        tags[key] = unescapeIrcTagValue(rawValue)
                    } else {
                        // IRCv3: a tag without '=' is equivalent to an empty value
                        val key = sharedTagKey(message.substring(tagStart, tagEnd))
                        tags[key] = ""
                    }

                    tagStart = tagEnd + 1
                }
                pos = nextSpace + 1
            }

            skipTrailingWhitespace()

            // prefix
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

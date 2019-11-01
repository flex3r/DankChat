package com.flxrs.dankchat.service.twitch.message

import com.flxrs.dankchat.service.irc.IrcMessage

data class Roomstate(
    val channel: String,
    val flags: MutableMap<String, Int> = mutableMapOf(
        "emote" to 0,
        "follow" to -1,
        "r9k" to 0,
        "slow" to 0,
        "subs" to 0
    )
) {

    override fun toString(): String {
        return flags.filter { (it.key == "follow" && it.value >= 0) || it.value > 0 }.map {
            when (it.key) {
                "follow" -> if (it.value == 0) "follow" else "follow(${it.value})"
                "slow"   -> "slow(${it.value})"
                else     -> it.key
            }
        }.joinToString()
    }

    fun updateState(msg: IrcMessage) {
        msg.tags.entries.forEach {
            when (it.key) {
                "emote-only"     -> flags["emote"] = it.value.toInt()
                "followers-only" -> flags["follow"] = it.value.toInt()
                "r9k"            -> flags["r9k"] = it.value.toInt()
                "slow"           -> flags["slow"] = it.value.toInt()
                "subs-only"      -> flags["subs"] = it.value.toInt()
            }
        }
    }
}
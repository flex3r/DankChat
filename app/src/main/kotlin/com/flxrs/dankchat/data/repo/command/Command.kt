package com.flxrs.dankchat.data.repo.command

enum class Command(val trigger: String) {
    Block(trigger = "/block"),
    Unblock(trigger = "/unblock"),
    //Chatters(trigger = "/chatters"),
    Uptime(trigger = "/uptime"),
    Help(trigger = "/help")
}

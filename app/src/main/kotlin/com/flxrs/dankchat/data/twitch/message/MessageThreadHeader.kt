package com.flxrs.dankchat.data.twitch.message

import com.flxrs.dankchat.data.UserName

data class MessageThreadHeader(val id: String, val name: UserName, val message: String, val participated: Boolean)

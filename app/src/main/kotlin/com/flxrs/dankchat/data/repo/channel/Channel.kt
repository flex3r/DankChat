package com.flxrs.dankchat.data.repo.channel

import com.flxrs.dankchat.data.DisplayName
import com.flxrs.dankchat.data.UserId
import com.flxrs.dankchat.data.UserName

data class Channel(val id: UserId, val name: UserName, val displayName: DisplayName)

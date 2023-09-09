package com.flxrs.dankchat.preferences.model

import android.os.Parcelable
import com.flxrs.dankchat.data.UserName
import kotlinx.parcelize.Parcelize

@Parcelize
data class ChannelWithRename(val channel: UserName, val rename: UserName?) : Parcelable

package com.flxrs.dankchat.utils

import android.os.Parcel
import kotlinx.parcelize.Parceler

object IntRangeParceler : Parceler<IntRange> {

    override fun create(parcel: Parcel): IntRange = IntRange(parcel.readInt(), parcel.readInt())

    override fun IntRange.write(parcel: Parcel, flags: Int) {
        parcel.writeInt(first)
        parcel.writeInt(endInclusive)
    }
}

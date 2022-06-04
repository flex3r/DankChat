package com.flxrs.dankchat.utils.extensions

import java.util.*

fun <T> MutableCollection<T>.replaceAll(values: Collection<T>) {
    clear()
    addAll(values)
}

fun <T> MutableList<T>.swap(i: Int, j: Int) = Collections.swap(this, i, j)
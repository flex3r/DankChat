package com.flxrs.dankchat.utils.extensions

fun <T> MutableCollection<T>.replaceAll(values: Collection<T>) {
    clear()
    addAll(values)
}
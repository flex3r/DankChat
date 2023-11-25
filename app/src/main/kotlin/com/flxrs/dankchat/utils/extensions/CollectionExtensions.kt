package com.flxrs.dankchat.utils.extensions

import java.util.Collections

fun <T> MutableCollection<T>.replaceAll(values: Collection<T>) {
    clear()
    addAll(values)
}

fun <T> MutableList<T>.swap(i: Int, j: Int) = Collections.swap(this, i, j)

inline fun <reified T : P, P> Collection<P>.partitionIsInstance(): Pair<List<T>, List<P>> {
    val first = mutableListOf<T>()
    val second = mutableListOf<P>()
    for (element in this) {
        when (element) {
            is T -> first.add(element)
            else -> second.add(element)
        }
    }
    return Pair(first, second)
}

inline fun <T> Collection<T>.replaceIf(replacement: T, predicate: (T) -> Boolean): List<T> {
    return map { if (predicate(it)) replacement else it }
}

inline fun <T> List<T>.chunkedBy(maxSize: Int, selector: (T) -> Int): List<List<T>> {
    val result = mutableListOf<List<T>>()
    var currentChunk = mutableListOf<T>()
    var currentChunkSize = 0
    for (index in indices) {
        val item = get(index)
        val itemSize = selector(item)
        if (currentChunkSize + itemSize > maxSize && currentChunk.isNotEmpty()) {
            result += currentChunk
            currentChunk = mutableListOf()
            currentChunkSize = 0
        }

        currentChunk.add(item)
        currentChunkSize += itemSize
    }

    if (currentChunk.isNotEmpty()) {
        result += currentChunk
    }

    return result
}

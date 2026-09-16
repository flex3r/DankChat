package com.flxrs.dankchat.ui.main

internal fun circularPagerPageCount(channelCount: Int): Int = when {
    channelCount > 1 -> Int.MAX_VALUE
    else -> channelCount
}

internal fun initialCircularPagerPage(
    channelIndex: Int,
    channelCount: Int,
): Int {
    if (channelCount <= 1) return 0
    val middlePage = Int.MAX_VALUE / 2
    return middlePage - middlePage.mod(channelCount) + channelIndex
}

internal fun circularPageToChannelIndex(
    page: Int,
    channelCount: Int,
): Int = when {
    channelCount > 0 -> page.mod(channelCount)
    else -> 0
}

internal fun closestCircularPagerPage(
    currentPage: Int,
    channelIndex: Int,
    channelCount: Int,
): Int {
    if (channelCount <= 1) return 0

    val currentChannelIndex = circularPageToChannelIndex(currentPage, channelCount)
    val forwardDistance = (channelIndex - currentChannelIndex).mod(channelCount)
    val backwardDistance = forwardDistance - channelCount
    val distance =
        when {
            forwardDistance <= -backwardDistance -> forwardDistance
            else -> backwardDistance
        }
    return currentPage + distance
}

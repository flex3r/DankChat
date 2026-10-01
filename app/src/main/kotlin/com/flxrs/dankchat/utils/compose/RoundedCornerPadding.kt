package com.flxrs.dankchat.utils.compose

import android.os.Build
import android.view.RoundedCorner
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.view.ViewCompat
import kotlin.math.max
import kotlin.math.sin

/**
 * Adds padding to avoid content being clipped by rounded display corners.
 *
 * This modifier:
 * 1. Gets the component's position in window coordinates
 * 2. Checks if the component intersects with any rounded corner boundaries
 * 3. Adds padding only where needed to push content into the safe area
 *
 * Uses the 45-degree boundary method from Android documentation.
 */
@Suppress("ModifierComposed") // TODO: Replace with custom ModifierNodeElement
fun Modifier.avoidRoundedCorners(fallback: PaddingValues): Modifier = composed {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
        return@composed this.padding(fallback)
    }

    val view = LocalView.current
    val density = LocalDensity.current
    val direction = LocalLayoutDirection.current

    var paddingStart by remember { mutableStateOf(fallback.calculateStartPadding(direction)) }
    var paddingTop by remember { mutableStateOf(0.dp) }
    var paddingEnd by remember { mutableStateOf(fallback.calculateEndPadding(direction)) }
    var paddingBottom by remember { mutableStateOf(0.dp) }

    this
        .onGloballyPositioned { coordinates ->
            val compatInsets = ViewCompat.getRootWindowInsets(view) ?: return@onGloballyPositioned
            val windowInsets = compatInsets.toWindowInsets() ?: return@onGloballyPositioned

            // Get component position and size in window coordinates
            val position = coordinates.positionInWindow()
            val componentLeft = position.x.toInt()
            val componentTop = position.y.toInt()
            val componentRight = componentLeft + coordinates.size.width
            val componentBottom = componentTop + coordinates.size.height

            // Check all four corners
            val topLeft = windowInsets.getRoundedCorner(RoundedCorner.POSITION_TOP_LEFT)
            val topRight = windowInsets.getRoundedCorner(RoundedCorner.POSITION_TOP_RIGHT)
            val bottomLeft = windowInsets.getRoundedCorner(RoundedCorner.POSITION_BOTTOM_LEFT)
            val bottomRight = windowInsets.getRoundedCorner(RoundedCorner.POSITION_BOTTOM_RIGHT)

            // Calculate padding for each side
            paddingTop =
                with(density) {
                    maxOf(
                        topLeft?.calculateTopPaddingForComponent(componentLeft, componentTop) ?: 0,
                        topRight?.calculateTopPaddingForComponent(componentRight, componentTop) ?: 0,
                    ).toDp()
                }

            paddingBottom =
                with(density) {
                    maxOf(
                        bottomLeft?.calculateBottomPaddingForComponent(componentLeft, componentBottom) ?: 0,
                        bottomRight?.calculateBottomPaddingForComponent(componentRight, componentBottom) ?: 0,
                    ).toDp()
                }

            paddingStart =
                with(density) {
                    maxOf(
                        topLeft?.calculateStartPaddingForComponent(componentLeft, componentTop) ?: 0,
                        bottomLeft?.calculateStartPaddingForComponent(componentLeft, componentBottom) ?: 0,
                    ).toDp()
                }

            paddingEnd =
                with(density) {
                    maxOf(
                        topRight?.calculateEndPaddingForComponent(componentRight, componentTop) ?: 0,
                        bottomRight?.calculateEndPaddingForComponent(componentRight, componentBottom) ?: 0,
                    ).toDp()
                }
        }.padding(
            start = paddingStart,
            top = paddingTop,
            end = paddingEnd,
            bottom = paddingBottom,
        )
}

/**
 * Returns the bottom padding needed to avoid rounded display corners.
 * Uses a 25-degree boundary — a practical middle ground between the strict 45-degree
 * safe line (~29% of radius) and the full radius (100%). Gives ~58% of the radius,
 * keeping content comfortably clear of rounded corners without excessive spacing.
 *
 * On API < 31 returns [fallback].
 */
@Composable
fun rememberRoundedCornerBottomPadding(fallback: Dp = 0.dp): Dp {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
        return fallback
    }

    val view = LocalView.current
    val density = LocalDensity.current
    val compatInsets =
        ViewCompat.getRootWindowInsets(view)
            ?: return fallback
    val windowInsets =
        compatInsets.toWindowInsets()
            ?: return fallback

    val bottomLeft = windowInsets.getRoundedCorner(RoundedCorner.POSITION_BOTTOM_LEFT)
    val bottomRight = windowInsets.getRoundedCorner(RoundedCorner.POSITION_BOTTOM_RIGHT)
    val screenHeight = view.rootView.height
    val safePadding =
        maxOf(
            bottomLeft?.safeBottomPadding(screenHeight) ?: 0,
            bottomRight?.safeBottomPadding(screenHeight) ?: 0,
        )
    if (safePadding == 0) return fallback

    return with(density) { safePadding.toDp() }
}

@RequiresApi(api = 31)
private fun RoundedCorner.safeBottomPadding(screenHeight: Int): Int {
    val offset = (radius * sin(Math.toRadians(25.0))).toInt()
    val safeBottom = center.y + offset
    return max(0, screenHeight - safeBottom)
}

/**
 * Returns horizontal padding needed to avoid the bottom rounded display corners.
 * Uses [RoundedCorner.center] to determine where content is safe, matching
 * the approach in MainFragment for fullscreenHintText.
 *
 * On API < 31 or when no rounded corners are present, returns [fallback].
 */
@Composable
@ReadOnlyComposable
fun rememberRoundedCornerHorizontalPadding(fallback: Dp = 0.dp): PaddingValues {
    val fallbackPadding = PaddingValues(horizontal = fallback)
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
        return fallbackPadding
    }

    val view = LocalView.current
    val density = LocalDensity.current
    val compatInsets =
        ViewCompat.getRootWindowInsets(view)
            ?: return fallbackPadding
    val windowInsets =
        compatInsets.toWindowInsets()
            ?: return fallbackPadding

    val bottomLeft = windowInsets.getRoundedCorner(RoundedCorner.POSITION_BOTTOM_LEFT)
    val bottomRight = windowInsets.getRoundedCorner(RoundedCorner.POSITION_BOTTOM_RIGHT)
    if (bottomLeft == null || bottomRight == null) {
        return fallbackPadding
    }

    val screenWidth = view.rootView.width
    if (screenWidth <= 0) return fallbackPadding
    val start = with(density) { bottomLeft.center.x.toDp() }
    val end = with(density) { (screenWidth - bottomRight.center.x).toDp() }
    if (start < 0.dp || end < 0.dp) return fallbackPadding

    return PaddingValues(start = start, end = end)
}

@RequiresApi(api = 31)
private fun RoundedCorner.calculateTopPaddingForComponent(
    componentX: Int,
    componentTop: Int,
): Int {
    val offset = (radius * sin(Math.toRadians(45.0))).toInt()
    val topBoundary = center.y - offset
    val leftBoundary = center.x - offset
    val rightBoundary = center.x + offset

    if (componentX !in leftBoundary..rightBoundary) {
        return 0
    }

    return max(0, topBoundary - componentTop)
}

@RequiresApi(api = 31)
private fun RoundedCorner.calculateBottomPaddingForComponent(
    componentX: Int,
    componentBottom: Int,
): Int {
    val offset = (radius * sin(Math.toRadians(45.0))).toInt()
    val bottomBoundary = center.y + offset
    val leftBoundary = center.x - offset
    val rightBoundary = center.x + offset

    if (componentX !in leftBoundary..rightBoundary) {
        return 0
    }

    return max(0, componentBottom - bottomBoundary)
}

@RequiresApi(api = 31)
private fun RoundedCorner.calculateStartPaddingForComponent(
    componentLeft: Int,
    componentY: Int,
): Int {
    val offset = (radius * sin(Math.toRadians(45.0))).toInt()
    val leftBoundary = center.x - offset
    val topBoundary = center.y - offset
    val bottomBoundary = center.y + offset

    if (componentY !in topBoundary..bottomBoundary) {
        return 0
    }

    return max(0, leftBoundary - componentLeft)
}

@RequiresApi(api = 31)
private fun RoundedCorner.calculateEndPaddingForComponent(
    componentRight: Int,
    componentY: Int,
): Int {
    val offset = (radius * sin(Math.toRadians(45.0))).toInt()
    val rightBoundary = center.x + offset
    val topBoundary = center.y - offset
    val bottomBoundary = center.y + offset

    if (componentY !in topBoundary..bottomBoundary) {
        return 0
    }

    return max(0, componentRight - rightBoundary)
}

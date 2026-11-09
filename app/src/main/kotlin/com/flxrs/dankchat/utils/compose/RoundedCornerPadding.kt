package com.flxrs.dankchat.utils.compose

import android.os.Build
import android.view.RoundedCorner
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.absolutePadding
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
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.core.view.ViewCompat
import kotlin.math.max
import kotlin.math.sin

/**
 * Adds padding where a corner of the component reaches into a rounded display corner, moving it
 * onto the 45-degree point of the corner's arc, the boundary method from the Android documentation.
 */
@Suppress("ModifierComposed") // TODO: Replace with custom ModifierNodeElement
fun Modifier.avoidRoundedCorners(fallback: PaddingValues): Modifier = composed {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
        return@composed this.padding(fallback)
    }

    val view = LocalView.current
    val density = LocalDensity.current

    var paddingLeft by remember { mutableStateOf(0.dp) }
    var paddingTop by remember { mutableStateOf(0.dp) }
    var paddingRight by remember { mutableStateOf(0.dp) }
    var paddingBottom by remember { mutableStateOf(0.dp) }

    this
        .onGloballyPositioned { coordinates ->
            val compatInsets = ViewCompat.getRootWindowInsets(view) ?: return@onGloballyPositioned
            val windowInsets = compatInsets.toWindowInsets() ?: return@onGloballyPositioned

            val position = coordinates.positionInWindow()
            val left = position.x.toInt()
            val top = position.y.toInt()
            val right = left + coordinates.size.width
            val bottom = top + coordinates.size.height

            val topLeft = windowInsets.getRoundedCorner(RoundedCorner.POSITION_TOP_LEFT)?.overlap(left, top) ?: IntOffset.Zero
            val topRight = windowInsets.getRoundedCorner(RoundedCorner.POSITION_TOP_RIGHT)?.overlap(right, top) ?: IntOffset.Zero
            val bottomLeft = windowInsets.getRoundedCorner(RoundedCorner.POSITION_BOTTOM_LEFT)?.overlap(left, bottom) ?: IntOffset.Zero
            val bottomRight = windowInsets.getRoundedCorner(RoundedCorner.POSITION_BOTTOM_RIGHT)?.overlap(right, bottom) ?: IntOffset.Zero

            with(density) {
                paddingLeft = maxOf(topLeft.x, bottomLeft.x).toDp()
                paddingTop = maxOf(topLeft.y, topRight.y).toDp()
                paddingRight = maxOf(topRight.x, bottomRight.x).toDp()
                paddingBottom = maxOf(bottomLeft.y, bottomRight.y).toDp()
            }
        }.absolutePadding(
            left = paddingLeft,
            top = paddingTop,
            right = paddingRight,
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

// How far the component corner at x, y reaches past the 45-degree point of the arc on each axis,
// zero unless it does on both
@RequiresApi(api = 31)
private fun RoundedCorner.overlap(
    x: Int,
    y: Int,
): IntOffset {
    val offset = (radius * sin(Math.toRadians(45.0))).toInt()
    val isLeft = position == RoundedCorner.POSITION_TOP_LEFT || position == RoundedCorner.POSITION_BOTTOM_LEFT
    val isTop = position == RoundedCorner.POSITION_TOP_LEFT || position == RoundedCorner.POSITION_TOP_RIGHT
    val horizontal =
        when {
            isLeft -> center.x - offset - x
            else -> x - (center.x + offset)
        }
    val vertical =
        when {
            isTop -> center.y - offset - y
            else -> y - (center.y + offset)
        }
    return when {
        horizontal > 0 && vertical > 0 -> IntOffset(horizontal, vertical)
        else -> IntOffset.Zero
    }
}

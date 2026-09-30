package com.flxrs.dankchat.ui.main

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.Build
import android.os.SystemClock
import android.util.Rational
import android.view.OrientationEventListener
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.systemGestures
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.VerticalSplit
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.flxrs.dankchat.R
import com.flxrs.dankchat.ui.chat.emotemenu.EmoteMenu
import com.flxrs.dankchat.ui.main.stream.StreamViewModel
import kotlin.math.abs
import kotlin.math.roundToInt

// A device lying nearly flat reports noisy angles, so readings must hold their zone
private const val ROTATION_SUSTAIN_MS = 500L

@Composable
internal fun observePipMode(streamViewModel: StreamViewModel): Boolean {
    val context = LocalContext.current
    val activity = context as? Activity
    var isInPipMode by remember { mutableStateOf(false) }
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer =
            LifecycleEventObserver { _, _ ->
                isInPipMode = activity?.isInPictureInPictureMode == true
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(Unit) {
        streamViewModel.shouldEnablePipAutoMode.collect { enabled ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && activity != null) {
                activity.setPictureInPictureParams(
                    PictureInPictureParams
                        .Builder()
                        .setAutoEnterEnabled(enabled)
                        .setAspectRatio(Rational(16, 9))
                        .build(),
                )
            }
        }
    }

    return isInPipMode
}

@Composable
internal fun FullscreenSystemBarsEffect(isFullscreen: Boolean) {
    val context = LocalContext.current
    val window = (context as? Activity)?.window
    val view = LocalView.current

    DisposableEffect(isFullscreen, window, view) {
        if (window == null) return@DisposableEffect onDispose { }
        val controller = WindowCompat.getInsetsController(window, view)
        if (isFullscreen) {
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            controller.show(WindowInsetsCompat.Type.systemBars())
        }
        onDispose {
            controller.show(WindowInsetsCompat.Type.systemBars())
        }
    }
}

// Theater mode forces landscape, restoring the sensor orientation on exit. The flag lives in
// the StreamViewModel, so the effect re-applies after the rotation recreates the activity.
@Composable
internal fun TheaterOrientationEffect(isTheaterMode: Boolean) {
    val activity = LocalActivity.current
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    DisposableEffect(isTheaterMode, activity, isLandscape) {
        when {
            activity == null || !isTheaterMode -> onDispose { }

            else -> {
                // Keep a resume pin until the display reaches it, then relax so 180° flips work
                val pinnedVariant = activity.requestedOrientation == ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE ||
                    activity.requestedOrientation == ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE
                if (!pinnedVariant || isLandscape) {
                    activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                }
                onDispose { activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED }
            }
        }
    }
}

@Composable
internal fun TheaterRotationEffect(
    isTheaterMode: Boolean,
    isRotationSuspended: Boolean,
    onSuspendTheater: () -> Unit,
    onResumeTheater: () -> Unit,
) {
    val context = LocalContext.current
    val activity = LocalActivity.current
    val currentOnSuspendTheater by rememberUpdatedState(onSuspendTheater)
    val currentOnResumeTheater by rememberUpdatedState(onResumeTheater)

    DisposableEffect(isTheaterMode, isRotationSuspended, context) {
        when {
            !isTheaterMode && !isRotationSuspended -> onDispose { }

            else -> {
                val watchingForPortrait = isTheaterMode
                var wasLandscape = false
                var targetZoneSince = 0L
                val listener =
                    object : OrientationEventListener(context) {
                        override fun onOrientationChanged(orientation: Int) {
                            if (orientation == ORIENTATION_UNKNOWN) {
                                targetZoneSince = 0L
                                return
                            }
                            val nearLandscape = orientation in 60..120 || orientation in 240..300
                            val nearPortrait = orientation <= 30 || orientation >= 330 || orientation in 150..210
                            val inTargetZone =
                                when {
                                    watchingForPortrait -> {
                                        if (nearLandscape) {
                                            wasLandscape = true
                                        }
                                        nearPortrait && wasLandscape
                                    }

                                    else -> nearLandscape
                                }
                            val now = SystemClock.elapsedRealtime()
                            when {
                                !inTargetZone -> targetZoneSince = 0L

                                targetZoneSince == 0L -> targetZoneSince = now

                                now - targetZoneSince >= ROTATION_SUSTAIN_MS -> {
                                    when {
                                        watchingForPortrait -> currentOnSuspendTheater()

                                        else -> {
                                            // SENSOR_LANDSCAPE alone rotates to the default variant first, then flips 180°
                                            activity?.requestedOrientation =
                                                when {
                                                    orientation in 60..120 -> ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE
                                                    else -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                                                }
                                            currentOnResumeTheater()
                                        }
                                    }
                                }
                            }
                        }
                    }
                listener.enable()
                onDispose { listener.disable() }
            }
        }
    }
}

// Modified vertical split icon with an strike-through variant when docked.
@Composable
internal fun TheaterChatModeIcon(isDocked: Boolean) {
    val tint = LocalContentColor.current
    Icon(
        imageVector = Icons.Outlined.VerticalSplit,
        contentDescription =
            stringResource(
                when {
                    isDocked -> R.string.menu_theater_chat_overlay
                    else -> R.string.menu_theater_chat_side_by_side
                },
            ),
        modifier =
            when {
                isDocked ->
                    Modifier
                        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                        .drawWithContent {
                            drawContent()
                            val inset = size.width * 0.12f
                            val start = Offset(inset, inset)
                            val end = Offset(size.width - inset, size.height - inset)
                            val strokeWidth = 2.dp.toPx()
                            val gapShift = 1.8.dp.toPx()
                            drawLine(
                                color = Color.Black,
                                start = start + Offset(gapShift, -gapShift),
                                end = end + Offset(gapShift, -gapShift),
                                strokeWidth = strokeWidth * 1.6f,
                                cap = StrokeCap.Round,
                                blendMode = BlendMode.Clear,
                            )
                            drawLine(
                                color = tint,
                                start = start,
                                end = end,
                                strokeWidth = strokeWidth,
                                cap = StrokeCap.Round,
                            )
                        }

                else -> Modifier
            },
    )
}

@Composable
internal fun AnimatedStatusBarScrim(
    visible: Boolean,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier,
    ) {
        StatusBarScrim()
    }
}

@Composable
internal fun StatusBarScrim(
    modifier: Modifier = Modifier,
    colorAlpha: Float = 0.7f,
) {
    val density = LocalDensity.current
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .height(with(density) { WindowInsets.statusBars.getTop(density).toDp() })
                .background(MaterialTheme.colorScheme.surface.copy(alpha = colorAlpha)),
    )
}

@Composable
internal fun InputDismissScrim(
    forceOpen: Boolean,
    onDismiss: () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .coverWindow()
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                ) {
                    if (!forceOpen) {
                        onDismiss()
                    }
                },
    )
}

// Measures the content at window size and shifts it to the window origin, so a scrim inside a
// pane also catches taps over the stream while staying below the pane's own popups
@Composable
internal fun Modifier.coverWindow(): Modifier {
    val windowSize = LocalWindowInfo.current.containerSize
    return layout { measurable, constraints ->
        val placeable = measurable.measure(Constraints.fixed(windowSize.width, windowSize.height))
        layout(constraints.maxWidth, constraints.maxHeight) {
            val position = coordinates?.positionInWindow() ?: Offset.Zero
            placeable.place(-position.x.roundToInt(), -position.y.roundToInt())
        }
    }
}

/**
 * Modifier that consumes horizontal drags originating from system gesture edge zones
 * to prevent the HorizontalPager from intercepting system back/edge gestures.
 * Uses [PointerEventPass.Initial] so the pager never sees these drags,
 * while taps pass through normally to the content underneath.
 */
@Composable
internal fun Modifier.edgeGestureGuard(): Modifier {
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val systemGestureInsets = WindowInsets.systemGestures
    val leftEdgePx = systemGestureInsets.getLeft(density, layoutDirection).toFloat()
    val rightEdgePx = systemGestureInsets.getRight(density, layoutDirection).toFloat()

    return pointerInput(leftEdgePx, rightEdgePx) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            val isInEdge = down.position.x < leftEdgePx || down.position.x > (size.width - rightEdgePx)
            if (!isInEdge) return@awaitEachGesture

            var totalDx = 0f
            var claimed = false

            do {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                if (!change.pressed) break

                totalDx += change.positionChange().x
                if (!claimed && abs(totalDx) > viewConfiguration.touchSlop) {
                    claimed = true
                }
                if (claimed) {
                    change.consume()
                }
            } while (true)
        }
    }
}

/**
 * Animated emote menu overlay that slides in from the bottom.
 * Supports predictive back gesture scaling.
 */
@Composable
internal fun EmoteMenuOverlay(
    isVisible: Boolean,
    totalMenuHeight: Dp,
    backProgress: Float,
    onEmoteClick: (code: String, id: String) -> Unit,
    onBackspace: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = isVisible,
        enter = slideInVertically(animationSpec = tween(durationMillis = 140), initialOffsetY = { it }),
        exit = slideOutVertically(animationSpec = tween(durationMillis = 140), targetOffsetY = { it }),
        modifier = modifier,
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(totalMenuHeight)
                    .graphicsLayer {
                        val scale = 1f - (backProgress * 0.1f)
                        scaleX = scale
                        scaleY = scale
                        alpha = 1f - backProgress
                        translationY = backProgress * 100f
                    }.background(MaterialTheme.colorScheme.surfaceContainerHighest),
        ) {
            EmoteMenu(
                onEmoteClick = onEmoteClick,
                onBackspace = onBackspace,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

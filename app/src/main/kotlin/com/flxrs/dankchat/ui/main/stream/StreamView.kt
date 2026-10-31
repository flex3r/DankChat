package com.flxrs.dankchat.ui.main.stream

import android.content.res.Configuration
import android.os.SystemClock
import android.view.MotionEvent
import android.view.ViewGroup
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CommentsDisabled
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.doOnAttach
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.window.core.layout.WindowSizeClass
import com.flxrs.dankchat.R
import com.flxrs.dankchat.data.UserName
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.delay
import org.koin.compose.viewmodel.koinViewModel
import kotlin.math.abs

private const val OVERLAY_AUTO_HIDE_MS = 5_000L
private const val DOUBLE_TAP_TIMEOUT_MS = 300L

@Suppress("LambdaParameterEventTrailing")
@Composable
fun StreamView(
    channel: UserName,
    onClose: () -> Unit,
    onAudioOnly: () -> Unit,
    onToggleTheater: () -> Unit,
    modifier: Modifier = Modifier,
    isInPipMode: Boolean = false,
    fillPane: Boolean = false,
    isTheaterMode: Boolean = false,
    isTheaterChatVisible: Boolean = false,
    onToggleTheaterChat: () -> Unit = {},
    onTheaterChatDrag: (Float) -> Unit = {},
    onTheaterChatDragEnd: () -> Unit = {},
    onTheaterDoubleTap: () -> Unit = {},
    overlayEndPadding: Dp = 0.dp,
    webViewHorizontalInset: Dp = 0.dp,
) {
    val streamViewModel: StreamViewModel = koinViewModel()
    val touchSlop = LocalViewConfiguration.current.touchSlop
    // The touch listener outlives recompositions, so it must read the latest values
    val currentIsTheaterMode by rememberUpdatedState(isTheaterMode)
    val currentOnTheaterChatDrag by rememberUpdatedState(onTheaterChatDrag)
    val currentOnTheaterChatDragEnd by rememberUpdatedState(onTheaterChatDragEnd)
    val currentOnTheaterDoubleTap by rememberUpdatedState(onTheaterDoubleTap)
    // Bumped when the render process dies, forcing a fresh WebView through the first-open flow
    val webViewGeneration by streamViewModel.webViewGeneration.collectAsStateWithLifecycle()
    // Track whether the WebView has been attached to a window before.
    // First open: load URL while detached, attach after page loads (avoids white SurfaceView flash).
    // Subsequent opens: attach immediately, load URL while attached (video surface already initialized).
    var hasBeenAttached by remember(webViewGeneration) { mutableStateOf(streamViewModel.hasWebViewBeenAttached) }
    var isPageLoaded by remember(webViewGeneration) { mutableStateOf(hasBeenAttached) }
    var overlayTapTrigger by remember { mutableIntStateOf(0) }
    var showOverlayButtons by remember { mutableStateOf(false) }

    // Also fires once the page first loads and again after configuration changes
    // (isPageLoaded starts true then), briefly revealing the buttons without a tap
    LaunchedEffect(isPageLoaded, overlayTapTrigger) {
        if (isPageLoaded) {
            showOverlayButtons = true
            delay(OVERLAY_AUTO_HIDE_MS)
            showOverlayButtons = false
        }
    }
    val webView =
        remember(webViewGeneration) {
            streamViewModel.getOrCreateWebView().also { wv ->
                wv.setBackgroundColor(android.graphics.Color.TRANSPARENT)
                wv.webViewClient =
                    StreamComposeWebViewClient(
                        onPageFinished = { isPageLoaded = true },
                        onRendererGone = { didCrash ->
                            (wv.parent as? ViewGroup)?.removeView(wv)
                            streamViewModel.onRenderProcessGone(wv, didCrash)
                        },
                    )
                var blockingGesture = false
                var downX = 0f
                var downY = 0f
                var lastX = 0f
                var chatDragQualifies = false
                var chatDragActive = false
                var lastTapTime = 0L
                var forwardTapAllowed = false
                var forwardingTap = false
                var pendingTapForward: Runnable? = null
                @Suppress("ClickableViewAccessibility")
                wv.setOnTouchListener { view, event ->
                    if (forwardingTap) {
                        return@setOnTouchListener false
                    }
                    when (event.action) {
                        MotionEvent.ACTION_DOWN -> {
                            // A new gesture invalidates a not-yet-forwarded single tap, so a tap
                            // followed by a quick drag never pokes the player UI mid-drag
                            pendingTapForward?.let(view::removeCallbacks)
                            pendingTapForward = null
                            downX = event.x
                            downY = event.y
                            lastX = event.x
                            // Only drags starting on the chat's side of the stream move the chat
                            chatDragQualifies = currentIsTheaterMode && downX > view.width / 2f
                            chatDragActive = false
                            forwardTapAllowed = showOverlayButtons
                            // Theater mode consumes all taps so quick double taps never reach the
                            // player, single taps are re-dispatched after the double tap window
                            blockingGesture = !showOverlayButtons || currentIsTheaterMode
                            blockingGesture
                        }

                        MotionEvent.ACTION_MOVE -> {
                            // With the pager gone in theater mode, horizontal drags on the
                            // stream drag the chat overlay along with the finger
                            if (chatDragQualifies && !chatDragActive) {
                                val totalDx = event.x - downX
                                val totalDy = event.y - downY
                                if (abs(totalDx) > touchSlop && abs(totalDx) > abs(totalDy)) {
                                    chatDragActive = true
                                    lastX = event.x
                                }
                            }
                            if (chatDragActive) {
                                currentOnTheaterChatDrag(event.x - lastX)
                                lastX = event.x
                            }
                            blockingGesture
                        }

                        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                            val isTap = event.action == MotionEvent.ACTION_UP &&
                                abs(event.x - downX) < touchSlop &&
                                abs(event.y - downY) < touchSlop
                            when {
                                // Only actual taps reveal the overlay buttons, swipes never do
                                isTap -> overlayTapTrigger++

                                // A swipe must not pair with a later tap into a double tap
                                else -> lastTapTime = 0L
                            }
                            when {
                                chatDragActive -> {
                                    chatDragActive = false
                                    currentOnTheaterChatDragEnd()
                                }

                                // Double-tapping the stream switches between the theater chat modes
                                isTap && currentIsTheaterMode -> {
                                    when {
                                        event.eventTime - lastTapTime < DOUBLE_TAP_TIMEOUT_MS -> {
                                            lastTapTime = 0L
                                            currentOnTheaterDoubleTap()
                                        }

                                        else -> {
                                            lastTapTime = event.eventTime
                                            if (forwardTapAllowed) {
                                                val tapX = event.x
                                                val tapY = event.y
                                                val forward =
                                                    Runnable {
                                                        forwardingTap = true
                                                        val now = SystemClock.uptimeMillis()
                                                        val downEvent = MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, tapX, tapY, 0)
                                                        val upEvent = MotionEvent.obtain(now, now + 1, MotionEvent.ACTION_UP, tapX, tapY, 0)
                                                        view.dispatchTouchEvent(downEvent)
                                                        view.dispatchTouchEvent(upEvent)
                                                        downEvent.recycle()
                                                        upEvent.recycle()
                                                        forwardingTap = false
                                                        pendingTapForward = null
                                                    }
                                                pendingTapForward = forward
                                                view.postDelayed(forward, DOUBLE_TAP_TIMEOUT_MS)
                                            }
                                        }
                                    }
                                }
                            }
                            blockingGesture
                        }

                        else -> {
                            blockingGesture
                        }
                    }
                }
            }
        }

    // For first open: load URL on detached WebView
    if (!hasBeenAttached) {
        DisposableEffect(channel, webView) {
            streamViewModel.setStream(channel, webView)
            onDispose { }
        }
    }

    DisposableEffect(webView) {
        onDispose {
            (webView.parent as? ViewGroup)?.removeView(webView)
            streamViewModel.onWebViewDisposed(webView)
        }
    }

    Box(
        modifier =
            modifier
                .then(if (isInPipMode || fillPane) Modifier else Modifier.statusBarsPadding())
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface),
    ) {
        val webViewModifier =
            when {
                isInPipMode || fillPane -> {
                    Modifier.fillMaxSize().then(
                        when {
                            // The inset area stands in for the player's letterbox
                            webViewHorizontalInset > 0.dp -> Modifier.background(Color.Black).padding(horizontal = webViewHorizontalInset)

                            else -> Modifier
                        },
                    )
                }

                else -> {
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                }
            }

        if (isPageLoaded) {
            AndroidView(
                factory = { _ ->
                    (webView.parent as? ViewGroup)?.removeView(webView)
                    webView.layoutParams =
                        ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT,
                        )
                    if (!hasBeenAttached) {
                        hasBeenAttached = true
                        streamViewModel.hasWebViewBeenAttached = true
                    } else {
                        // Resume playback after config change — the Twitch player pauses
                        // when the WebView detaches from the old window during Activity recreation.
                        webView.doOnAttach { view ->
                            view.postDelayed({
                                (view as? WebView)?.evaluateJavascript("document.querySelector('video')?.play()", null)
                            }, 100)
                        }
                    }
                    webView
                },
                update = { _ ->
                    streamViewModel.setStream(channel, webView)
                },
                modifier =
                    webViewModifier.graphicsLayer {
                        compositingStrategy = CompositingStrategy.Offscreen
                    },
            )
        } else {
            Box(modifier = webViewModifier)
        }

        // Follows the chat overlay in theater mode, so the buttons stay reachable next to it
        val animatedOverlayEndPadding by animateDpAsState(overlayEndPadding)
        // Clearing the display cutout and rounded corners only matters while the buttons sit at
        // the actual screen edge — next to the visible chat they need neither
        val cutoutEndPadding = WindowInsets.displayCutout.asPaddingValues().calculateEndPadding(LocalLayoutDirection.current)
        val isAtScreenEdge = isTheaterMode && overlayEndPadding == 0.dp
        val edgeEndInset by animateDpAsState(
            when {
                isAtScreenEdge -> max(cutoutEndPadding, 8.dp)
                else -> 0.dp
            },
        )
        val edgeTopInset by animateDpAsState(
            when {
                isAtScreenEdge -> 8.dp
                else -> 0.dp
            },
        )
        // Only small portrait screens shrink the buttons, large screens show a big stream in either orientation
        val configuration = LocalConfiguration.current
        val isCompact =
            configuration.orientation == Configuration.ORIENTATION_PORTRAIT &&
                configuration.smallestScreenWidthDp < WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND
        val buttonSize =
            when {
                isCompact -> 34.dp
                else -> 44.dp
            }
        val iconSize =
            when {
                isCompact -> 20.dp
                else -> 26.dp
            }
        AnimatedVisibility(
            visible = !isInPipMode && showOverlayButtons,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopEnd),
        ) {
            Row(
                modifier =
                    Modifier
                        .statusBarsPadding()
                        .padding(top = edgeTopInset, end = animatedOverlayEndPadding + edgeEndInset)
                        .padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                when {
                    isTheaterMode && isTheaterChatVisible -> {
                        StreamOverlayButton(
                            icon = Icons.Default.CommentsDisabled,
                            contentDescription = stringResource(R.string.menu_hide_theater_chat),
                            onClick = onToggleTheaterChat,
                            buttonSize = buttonSize,
                            iconSize = iconSize,
                        )
                    }

                    isTheaterMode -> {
                        StreamOverlayButton(
                            icon = Icons.AutoMirrored.Filled.Chat,
                            contentDescription = stringResource(R.string.menu_show_theater_chat),
                            onClick = onToggleTheaterChat,
                            buttonSize = buttonSize,
                            iconSize = iconSize,
                        )
                    }

                    else -> {
                        StreamOverlayButton(
                            icon = Icons.Default.Headphones,
                            contentDescription = stringResource(R.string.menu_audio_only),
                            onClick = onAudioOnly,
                            buttonSize = buttonSize,
                            iconSize = iconSize,
                        )
                    }
                }
                when {
                    isTheaterMode -> {
                        StreamOverlayButton(
                            icon = Icons.Default.FullscreenExit,
                            contentDescription = stringResource(R.string.menu_exit_theater_mode),
                            onClick = onToggleTheater,
                            buttonSize = buttonSize,
                            iconSize = iconSize,
                        )
                    }

                    else -> {
                        StreamOverlayButton(
                            icon = Icons.Default.Fullscreen,
                            contentDescription = stringResource(R.string.menu_theater_mode),
                            onClick = onToggleTheater,
                            buttonSize = buttonSize,
                            iconSize = iconSize,
                        )
                    }
                }
                StreamOverlayButton(
                    icon = Icons.Default.Close,
                    contentDescription = stringResource(R.string.dialog_dismiss),
                    onClick = onClose,
                    buttonSize = buttonSize,
                    iconSize = iconSize,
                )
            }
        }
    }
}

@Composable
private fun StreamOverlayButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    buttonSize: Dp,
    iconSize: Dp,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier =
            Modifier
                .size(buttonSize)
                .background(
                    color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.75f),
                    shape = CircleShape,
                ).clickable(onClick = onClick),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(iconSize),
        )
    }
}

private class StreamComposeWebViewClient(
    private val onPageFinished: () -> Unit,
    private val onRendererGone: (didCrash: Boolean) -> Unit,
) : WebViewClient() {
    override fun onPageFinished(
        view: WebView?,
        url: String?,
    ) {
        if (url != null && url != BLANK_URL) {
            onPageFinished()
        }
    }

    // Default behavior would crash the whole app when the render process dies
    override fun onRenderProcessGone(
        view: WebView?,
        detail: RenderProcessGoneDetail?,
    ): Boolean {
        onRendererGone(detail?.didCrash() == true)
        return true
    }

    @Deprecated("Deprecated in Java")
    override fun shouldOverrideUrlLoading(
        view: WebView?,
        url: String?,
    ): Boolean {
        if (url.isNullOrBlank()) return true
        return ALLOWED_PATHS.none { url.startsWith(it) }
    }

    override fun shouldOverrideUrlLoading(
        view: WebView?,
        request: WebResourceRequest?,
    ): Boolean {
        val url = request?.url?.toString()
        if (url.isNullOrBlank()) return true
        return ALLOWED_PATHS.none { url.startsWith(it) }
    }

    override fun onReceivedError(
        view: WebView?,
        request: WebResourceRequest?,
        error: WebResourceError?,
    ) {
        super.onReceivedError(view, request, error)
        if (request?.isForMainFrame != true) return
        val description = error?.description?.toString().orEmpty()
        logger.warn { "Stream WebView failed to load ${request.url}: $description" }
    }

    companion object {
        private val logger = KotlinLogging.logger("StreamComposeWebViewClient")
        private const val BLANK_URL = "about:blank"
        private val ALLOWED_PATHS =
            listOf(
                BLANK_URL,
                "https://id.twitch.tv/",
                "https://www.twitch.tv/passport-callback",
                "https://player.twitch.tv/",
            )
    }
}

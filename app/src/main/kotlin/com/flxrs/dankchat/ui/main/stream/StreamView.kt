package com.flxrs.dankchat.ui.main.stream

import android.view.MotionEvent
import android.view.ViewGroup
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.doOnAttach
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flxrs.dankchat.R
import com.flxrs.dankchat.data.UserName
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.delay
import org.koin.compose.viewmodel.koinViewModel

private const val OVERLAY_AUTO_HIDE_MS = 3_000L

@Suppress("LambdaParameterEventTrailing")
@Composable
fun StreamView(
    channel: UserName,
    onClose: () -> Unit,
    onAudioOnly: () -> Unit,
    modifier: Modifier = Modifier,
    isInPipMode: Boolean = false,
    fillPane: Boolean = false,
) {
    val streamViewModel: StreamViewModel = koinViewModel()
    // Bumped when the render process dies, forcing a fresh WebView through the first-open flow
    val webViewGeneration by streamViewModel.webViewGeneration.collectAsStateWithLifecycle()
    // Track whether the WebView has been attached to a window before.
    // First open: load URL while detached, attach after page loads (avoids white SurfaceView flash).
    // Subsequent opens: attach immediately, load URL while attached (video surface already initialized).
    var hasBeenAttached by remember(webViewGeneration) { mutableStateOf(streamViewModel.hasWebViewBeenAttached) }
    var isPageLoaded by remember(webViewGeneration) { mutableStateOf(hasBeenAttached) }
    var overlayTapTrigger by remember { mutableIntStateOf(0) }
    var showOverlayButtons by remember { mutableStateOf(false) }

    LaunchedEffect(overlayTapTrigger) {
        if (overlayTapTrigger > 0) {
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
                @Suppress("ClickableViewAccessibility")
                wv.setOnTouchListener { _, event ->
                    when (event.action) {
                        MotionEvent.ACTION_DOWN -> {
                            overlayTapTrigger++
                            if (!showOverlayButtons) {
                                blockingGesture = true
                                true
                            } else {
                                blockingGesture = false
                                false
                            }
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
                    Modifier.fillMaxSize()
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
                        .padding(8.dp),
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement
                    .spacedBy(6.dp),
            ) {
                StreamOverlayButton(
                    icon = Icons.Default.Headphones,
                    contentDescription = stringResource(R.string.menu_audio_only),
                    onClick = onAudioOnly,
                )
                StreamOverlayButton(
                    icon = Icons.Default.Close,
                    contentDescription = stringResource(R.string.dialog_dismiss),
                    onClick = onClose,
                )
            }
        }
    }
}

@Composable
private fun StreamOverlayButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier =
            Modifier
                .size(28.dp)
                .background(
                    color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f),
                    shape = CircleShape,
                ).clickable(onClick = onClick),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(18.dp),
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

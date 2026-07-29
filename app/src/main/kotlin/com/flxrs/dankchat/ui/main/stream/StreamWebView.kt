package com.flxrs.dankchat.ui.main.stream

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.webkit.JavascriptInterface
import android.webkit.WebView

@SuppressLint("SetJavaScriptEnabled")
class StreamWebView
    @JvmOverloads
    constructor(
        context: Context,
        attrs: AttributeSet? = null,
        defStyleAttr: Int = android.R.attr.webViewStyle,
        defStyleRes: Int = 0,
    ) : WebView(context, attrs, defStyleAttr, defStyleRes) {
        init {
            with(settings) {
                javaScriptEnabled = true
                setSupportZoom(false)
                mediaPlaybackRequiresUserGesture = false
                domStorageEnabled = true
            }
        }

        fun addTwitchPlayerBridge(
            onPlaybackStarted: () -> Unit,
            onLoadingStatus: (String) -> Unit,
            onAdblocked: (String) -> Unit,
        ) {
            addJavascriptInterface(
                TwitchPlayerBridge(
                    onPlaybackStartedCallback = { post { onPlaybackStarted() } },
                    onLoadingStatusCallback = { message -> post { onLoadingStatus(message) } },
                    onAdblockedCallback = { text -> post { onAdblocked(text) } }
                ),
                "TwitchPlayerBridge"
            )
        }

        private class TwitchPlayerBridge(
            private val onPlaybackStartedCallback: () -> Unit,
            private val onLoadingStatusCallback: (String) -> Unit,
            private val onAdblockedCallback: (String) -> Unit,
        ) {
            @JavascriptInterface
            fun onPlaybackStarted() {
                onPlaybackStartedCallback()
            }

            @JavascriptInterface
            fun onLoadingStatus(message: String) {
                onLoadingStatusCallback(message)
            }

            @JavascriptInterface
            fun onAdblocked(text: String) {
                onAdblockedCallback(text)
            }
        }
    }

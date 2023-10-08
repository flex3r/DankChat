package com.flxrs.dankchat.main.stream

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.core.view.isVisible
import com.flxrs.dankchat.data.UserName

@SuppressLint("SetJavaScriptEnabled")
class StreamWebView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = android.R.attr.webViewStyle,
    defStyleRes: Int = 0
) : WebView(context, attrs, defStyleAttr, defStyleRes) {

    var shouldOverrideWindowVisibilityHandling = false

    init {
        with(settings) {
            javaScriptEnabled = true
            setSupportZoom(false)
            mediaPlaybackRequiresUserGesture = false
            domStorageEnabled = true
        }
        webViewClient = StreamWebViewClient()
    }

    fun setStream(channel: UserName?) {
        val isActive = channel != null
        isVisible = isActive
        val url = when {
            isActive -> "https://player.twitch.tv/?channel=$channel&enableExtensions=true&muted=false&parent=twitch.tv"
            else     -> BLANK_URL
        }

        stopLoading()
        loadUrl(url)
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        if (!shouldOverrideWindowVisibilityHandling) {
            super.onWindowVisibilityChanged(visibility)
            return
        }

        if (visibility != GONE || url == null || url == BLANK_URL) {
            super.onWindowVisibilityChanged(visibility)
        }
    }

    private class StreamWebViewClient : WebViewClient() {
        @Deprecated("Deprecated in Java")
        override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
            if (url.isNullOrBlank()) {
                return true
            }

            return ALLOWED_PATHS.none { url.startsWith(it) }
        }

        override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
            val url = request?.url?.toString()
            if (url.isNullOrBlank()) {
                return true
            }

            return ALLOWED_PATHS.none { url.startsWith(it) }
        }
    }

    companion object {
        private const val BLANK_URL = "about:blank"
        private val ALLOWED_PATHS = listOf(
            BLANK_URL,
            "https://id.twitch.tv/",
            "https://www.twitch.tv/passport-callback",
            "https://player.twitch.tv/",
        )
    }

}

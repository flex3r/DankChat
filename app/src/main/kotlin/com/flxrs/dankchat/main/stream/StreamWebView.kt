package com.flxrs.dankchat.main.stream

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.util.Log
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.core.view.doOnAttach
import androidx.core.view.isVisible
import androidx.lifecycle.*
import com.flxrs.dankchat.main.MainViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

@SuppressLint("SetJavaScriptEnabled")
class StreamWebView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = android.R.attr.webViewStyle,
    defStyleRes: Int = 0
) : WebView(context, attrs, defStyleAttr, defStyleRes) {
    var lastChannel = ""

    init {
        with(settings) {
            javaScriptEnabled = true
            setSupportZoom(false)
            mediaPlaybackRequiresUserGesture = false
            domStorageEnabled = true
        }
        webViewClient = StreamWebViewClient()
    }

    fun setStream(channel: String) {
        if (channel == lastChannel) { return }
        lastChannel = channel

        val isActive = channel.isNotBlank()
        isVisible = isActive
        val url = when {
            isActive -> "https://player.twitch.tv/?channel=$channel&enableExtensions=true&muted=false&parent=twitch.tv"
            else     -> BLANK_URL
        }

        Log.d("DANK", "gonig to $url")
        stopLoading()
        loadUrl(url)
    }

    private class StreamWebViewClient : WebViewClient() {
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

    override fun destroy() {
        super.destroy()
        Log.d("DANK", "Stream view was destroyed")
    }

    private companion object {
        private const val BLANK_URL = "about:blank"
        private val ALLOWED_PATHS = listOf(
            BLANK_URL,
            "https://id.twitch.tv/",
            "https://www.twitch.tv/passport-callback",
            "https://player.twitch.tv/",
        )
    }

}
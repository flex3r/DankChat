package com.flxrs.dankchat.main

import android.annotation.SuppressLint
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.flxrs.dankchat.data.UserName
import com.flxrs.dankchat.main.stream.StreamWebView
import com.flxrs.dankchat.preferences.DankChatPreferenceStore
import org.koin.android.annotation.KoinViewModel

@KoinViewModel
class StreamWebViewModel(
    application: Application,
    private val dankChatPreferenceStore: DankChatPreferenceStore
) : AndroidViewModel(application) {

    private var lastStreamedChannel: UserName? = null

    @SuppressLint("StaticFieldLeak")
    private var streamWebView: StreamWebView? = null

    fun getOrCreateStreamWebView(): StreamWebView {
        return when {
            !dankChatPreferenceStore.retainWebViewEnabled -> StreamWebView(getApplication())
            else                                          -> streamWebView ?: StreamWebView(getApplication()).also {
                streamWebView = it
            }
        }
    }

    fun setStream(channel: UserName?, webView: StreamWebView) {
        if (!dankChatPreferenceStore.retainWebViewEnabled) {
            // Clear previous retained WebView instance
            streamWebView?.let {
                it.destroy()
                streamWebView = null
                lastStreamedChannel = null
            }

            webView.setStream(channel)
            return
        }

        // Prevent unnecessary stream loading
        if (channel == lastStreamedChannel) {
            return
        }

        lastStreamedChannel = channel
        webView.setStream(channel)
    }

    override fun onCleared() {
        streamWebView?.destroy()
        streamWebView = null
        lastStreamedChannel = null
        super.onCleared()
    }
}

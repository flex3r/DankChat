package com.flxrs.dankchat.main

import android.annotation.SuppressLint
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.flxrs.dankchat.main.stream.StreamWebView
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class StreamWebViewModel @Inject constructor(application: Application) : AndroidViewModel(application) {

    private var lastStreamedChannel: String? = null

    @SuppressLint("StaticFieldLeak")
    private var streamWebView: StreamWebView? = null

    fun getOrCreateStreamWebView(): StreamWebView = streamWebView ?: StreamWebView(getApplication()).also { streamWebView = it }

    fun setStream(channel: String) {
        val webView = streamWebView ?: return
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

package com.flxrs.dankchat.ui.main.stream

import android.annotation.SuppressLint
import android.app.Application
import androidx.compose.runtime.Immutable
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.flxrs.dankchat.data.UserName
import com.flxrs.dankchat.data.repo.chat.ChatChannelProvider
import com.flxrs.dankchat.data.repo.stream.StreamDataRepository
import com.flxrs.dankchat.preferences.stream.StreamsSettingsDataStore
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.core.annotation.KoinViewModel

private val logger = KotlinLogging.logger("StreamViewModel")

@KoinViewModel
class StreamViewModel(
    application: Application,
    private val chatChannelProvider: ChatChannelProvider,
    private val streamDataRepository: StreamDataRepository,
    internal val streamsSettingsDataStore: StreamsSettingsDataStore,
) : AndroidViewModel(application) {
    private val _currentStreamedChannel = MutableStateFlow<UserName?>(null)

    private val hasStreamData: StateFlow<Boolean> =
        combine(
            chatChannelProvider.activeChannel,
            streamDataRepository.streamData,
        ) { activeChannel, streamData ->
            activeChannel != null && streamData.any { it.channel == activeChannel }
        }.distinctUntilChanged()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    private val _isAudioOnly = MutableStateFlow(false)
    private val _loadingStatus = MutableStateFlow("")
    private val _adblockStatus = MutableStateFlow("")

    val streamState: StateFlow<StreamState> =
        combine(
            _currentStreamedChannel,
            hasStreamData,
            _isAudioOnly,
            _loadingStatus,
            _adblockStatus,
        ) { currentStream, hasData, audioOnly, loading, adblock ->
            val message = adblock.ifBlank { loading }
            StreamState(
                currentStream = currentStream,
                hasStreamData = hasData,
                isAudioOnly = audioOnly,
                adblockMessage = message
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), StreamState())

    val shouldEnablePipAutoMode: StateFlow<Boolean> =
        combine(
            _currentStreamedChannel,
            _isAudioOnly,
            streamsSettingsDataStore.pipEnabled,
        ) { currentStream, audioOnly, pipEnabled ->
            currentStream != null && !audioOnly && pipEnabled
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    init {
        viewModelScope.launch {
            chatChannelProvider.channels.collect { channels ->
                if (channels != null) {
                    streamDataRepository.fetchStreamData(channels)
                    val current = _currentStreamedChannel.value
                    if (current != null && current !in channels) {
                        closeStream()
                    }
                }
            }
        }
    }

    private var lastStreamedChannel: UserName? = null
    var hasWebViewBeenAttached: Boolean = false

    @SuppressLint("StaticFieldLeak")
    private var cachedWebView: StreamWebView? = null

    private val _webViewGeneration = MutableStateFlow(0)
    val webViewGeneration: StateFlow<Int> = _webViewGeneration.asStateFlow()

    fun getOrCreateWebView(): StreamWebView {
        val preventReloads = streamsSettingsDataStore.current().preventStreamReloads
        return if (preventReloads) {
            cachedWebView ?: StreamWebView(getApplication()).also { cachedWebView = it }
        } else {
            StreamWebView(getApplication())
        }
    }

    fun setStream(
        channel: UserName,
        webView: StreamWebView,
    ) {
        if (channel == lastStreamedChannel) return
        lastStreamedChannel = channel
        _loadingStatus.value = ""
        _adblockStatus.value = ""
        loadStream(channel, webView)
    }

    fun onWebViewDisposed(webView: StreamWebView) {
        // Only the cached WebView is kept alive across compositions, and only while the stream stays open
        if (cachedWebView !== webView || _currentStreamedChannel.value == null) {
            destroyWebView(webView)
        }
    }

    fun onRenderProcessGone(
        webView: StreamWebView,
        didCrash: Boolean,
    ) {
        logger.warn { "Stream WebView render process gone (didCrash=$didCrash), recreating" }
        destroyWebView(webView)
        _webViewGeneration.update { it + 1 }
    }

    private fun destroyWebView(webView: StreamWebView) {
        webView.stopLoading()
        webView.destroy()
        if (cachedWebView === webView) {
            cachedWebView = null
        }
        lastStreamedChannel = null
        hasWebViewBeenAttached = false
        _loadingStatus.value = ""
        _adblockStatus.value = ""
    }

    private fun loadStream(
        channel: UserName,
        webView: StreamWebView,
    ) {
        val enableExtensions = streamsSettingsDataStore.current().showStreamExtensions
        val url = "https://player.twitch.tv/?channel=$channel&enableExtensions=$enableExtensions&muted=false&parent=twitch.tv"
        webView.stopLoading()
        webView.loadUrl(url)
    }

    fun toggleStream(channel: UserName) {
        _currentStreamedChannel.update { if (it == channel) null else channel }
        _isAudioOnly.value = false
        _loadingStatus.value = ""
        _adblockStatus.value = ""
    }

    fun toggleAudioOnly() {
        _isAudioOnly.update { !it }
    }

    fun closeStream() {
        _currentStreamedChannel.value = null
        _isAudioOnly.value = false
        _loadingStatus.value = ""
        _adblockStatus.value = ""
    }

    fun onAdblocked(text: String) {
        _adblockStatus.value = text
    }

    fun onLoadingStatus(message: String) {
        _loadingStatus.value = message
    }

    fun onPlaybackStarted() {
        _loadingStatus.value = ""
        _adblockStatus.value = ""
    }

    override fun onCleared() {
        streamDataRepository.cancelStreamData()
        cachedWebView?.destroy()
        cachedWebView = null
        lastStreamedChannel = null
        super.onCleared()
    }
}

@Immutable
data class StreamState(
    val currentStream: UserName? = null,
    val hasStreamData: Boolean = false,
    val isAudioOnly: Boolean = false,
    val adblockMessage: String = "",
)

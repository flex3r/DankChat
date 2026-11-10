package com.flxrs.dankchat.data.repo.stream

import com.flxrs.dankchat.data.UserName
import com.flxrs.dankchat.data.auth.AuthDataStore
import com.flxrs.dankchat.data.auth.StartupValidation
import com.flxrs.dankchat.data.auth.StartupValidationHolder
import com.flxrs.dankchat.data.repo.chat.ChatMessageRepository
import com.flxrs.dankchat.data.repo.data.DataRepository
import com.flxrs.dankchat.data.twitch.message.SystemMessageType
import com.flxrs.dankchat.di.DispatchersProvider
import com.flxrs.dankchat.preferences.DankChatPreferenceStore
import com.flxrs.dankchat.preferences.stream.StreamsSettingsDataStore
import com.flxrs.dankchat.utils.AppLifecycleListener
import com.flxrs.dankchat.utils.AppLifecycleListener.AppLifecycle
import com.flxrs.dankchat.utils.DateTimeUtils
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.koin.core.annotation.Single
import java.util.concurrent.ConcurrentHashMap
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.plusAssign
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

@Single
class StreamDataRepository(
    private val dataRepository: DataRepository,
    private val dankChatPreferenceStore: DankChatPreferenceStore,
    private val streamsSettingsDataStore: StreamsSettingsDataStore,
    private val chatMessageRepository: ChatMessageRepository,
    private val appLifecycleListener: AppLifecycleListener,
    authDataStore: AuthDataStore,
    startupValidationHolder: StartupValidationHolder,
    dispatchersProvider: DispatchersProvider,
) {
    private val scope = CoroutineScope(dispatchersProvider.io + SupervisorJob())
    private val activeChannels = MutableStateFlow<List<UserName>>(emptyList())
    private val liveStates = ConcurrentHashMap<UserName, Boolean>()
    private val _streamData = MutableStateFlow<ImmutableList<StreamData>>(persistentListOf())
    val streamData: StateFlow<ImmutableList<StreamData>> = _streamData.asStateFlow()
    private val _fetchCount = AtomicInt(0)
    val fetchCount: Int get() = _fetchCount.load()

    init {
        // Helix rejects requests until the startup validation resolved, a later login restarts the polling
        val authState =
            combine(startupValidationHolder.state, authDataStore.settings.map { it.isLoggedIn }) { validation, isLoggedIn ->
                when {
                    validation is StartupValidation.Pending -> AuthState.Pending
                    validation is StartupValidation.TokenInvalid || !isLoggedIn -> AuthState.Unavailable
                    else -> AuthState.Available
                }
            }.distinctUntilChanged()
        scope.launch {
            var pausedAt: TimeSource.Monotonic.ValueTimeMark? = null
            combine(
                activeChannels,
                streamsSettingsDataStore.settings
                    .map { FetchConfig(fetchStreams = it.fetchStreams, showStreamCategory = it.showStreamCategory) }
                    .distinctUntilChanged(),
                authState,
                appLifecycleListener.appState,
            ) { channels, config, auth, lifecycle -> PollingState(channels, config, auth, lifecycle) }
                .collectLatest { (channels, config, auth, lifecycle) ->
                    if (auth == AuthState.Pending) {
                        return@collectLatest
                    }
                    if (channels.isEmpty() || !config.fetchStreams || auth == AuthState.Unavailable) {
                        liveStates.clear()
                        _streamData.value = persistentListOf()
                        return@collectLatest
                    }
                    if (lifecycle == AppLifecycle.Background) {
                        pausedAt = pausedAt ?: TimeSource.Monotonic.markNow()
                        return@collectLatest
                    }
                    // Transitions that happened while not polling would be reported with a
                    // wrong timestamp, forget the states instead so they are re-baselined
                    if (pausedAt?.let { it.elapsedNow() > STALE_LIVE_STATE_THRESHOLD } == true) {
                        liveStates.clear()
                    }
                    pausedAt = null
                    coroutineScope {
                        while (isActive) {
                            runCatching { fetchOnce(channels) }
                            delay(STREAM_REFRESH_RATE)
                        }
                    }
                }
        }
    }

    fun fetchStreamData(channels: List<UserName>) {
        activeChannels.value = channels
    }

    private suspend fun fetchOnce(channels: List<UserName>) {
        val currentSettings = streamsSettingsDataStore.settings.first()
        _fetchCount += 1
        val streams = dataRepository.getStreams(channels)
        val data =
            streams
                ?.map {
                    val uptime = DateTimeUtils.calculateUptime(it.startedAt)
                    val category =
                        it.category
                            ?.takeIf { currentSettings.showStreamCategory }
                            ?.ifBlank { null }
                    val formatted = dankChatPreferenceStore.formatViewersString(it.viewerCount, uptime, category)
                    val formattedCompact = dankChatPreferenceStore.formatViewersStringCompact(it.viewerCount, uptime, category)

                    StreamData(
                        channel = it.userLogin,
                        formattedData = formatted,
                        formattedDataCompact = formattedCompact,
                        viewerCount = it.viewerCount,
                        startedAt = it.startedAt,
                        category = it.category,
                        title = it.title?.ifBlank { null },
                    )
                }.orEmpty()

        if (streams != null) {
            postLiveStatusMessages(channels, data, currentSettings.showLiveMessages)
        }
        _streamData.value = data.toImmutableList()
    }

    fun cancelStreamData() {
        activeChannels.value = emptyList()
    }

    private fun postLiveStatusMessages(
        channels: List<UserName>,
        data: List<StreamData>,
        enabled: Boolean,
    ) {
        val liveStreams = data.associateBy { it.channel }
        channels.forEach { channel ->
            val stream = liveStreams[channel]
            val wasLive = liveStates.put(channel, stream != null)
            when {
                // First observation of a channel only records its state
                wasLive == null || !enabled -> Unit

                stream != null && !wasLive -> chatMessageRepository.addSystemMessage(channel, SystemMessageType.StreamLive(channel, stream.title))

                stream == null && wasLive -> chatMessageRepository.addSystemMessage(channel, SystemMessageType.StreamOffline(channel))
            }
        }
        liveStates.keys.retainAll(channels.toSet())
    }

    private data class FetchConfig(
        val fetchStreams: Boolean,
        val showStreamCategory: Boolean,
    )

    private enum class AuthState {
        Pending,
        Available,
        Unavailable,
    }

    private data class PollingState(
        val channels: List<UserName>,
        val config: FetchConfig,
        val auth: AuthState,
        val lifecycle: AppLifecycle,
    )

    companion object {
        private val STREAM_REFRESH_RATE = 30.seconds

        // Live states older than this are considered stale, quick app switches still report
        // transitions with an acceptable timestamp error
        private val STALE_LIVE_STATE_THRESHOLD = 60.seconds
    }
}

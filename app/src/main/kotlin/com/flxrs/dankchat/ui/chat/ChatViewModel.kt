package com.flxrs.dankchat.ui.chat

import android.util.LruCache
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flxrs.dankchat.data.UserName
import com.flxrs.dankchat.data.api.helix.HelixApiClient
import com.flxrs.dankchat.data.api.helix.HelixApiException
import com.flxrs.dankchat.data.auth.AuthDataStore
import com.flxrs.dankchat.data.chat.ChatItem
import com.flxrs.dankchat.data.repo.chat.ChatMessageRepository
import com.flxrs.dankchat.data.twitch.message.SystemMessageType
import com.flxrs.dankchat.di.DispatchersProvider
import com.flxrs.dankchat.preferences.DankChatPreferenceStore
import com.flxrs.dankchat.preferences.appearance.AppearanceSettingsDataStore
import com.flxrs.dankchat.preferences.appearance.FabAnchor
import com.flxrs.dankchat.preferences.chat.ChatSettings
import com.flxrs.dankchat.preferences.chat.ChatSettingsDataStore
import com.flxrs.dankchat.utils.DateTimeUtils
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.koin.core.annotation.InjectedParam
import org.koin.core.annotation.KoinViewModel
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

private val logger = KotlinLogging.logger("ChatViewModel")

private const val MAPPING_CACHE_MIN_SIZE = 512
private const val MAPPING_CACHE_MARGIN = 64

@KoinViewModel
class ChatViewModel(
    @InjectedParam private val channel: UserName,
    private val chatMessageRepository: ChatMessageRepository,
    chatMessageMapper: ChatMessageMapper,
    private val helixApiClient: HelixApiClient,
    private val authDataStore: AuthDataStore,
    private val preferenceStore: DankChatPreferenceStore,
    private val appearanceSettingsDataStore: AppearanceSettingsDataStore,
    chatSettingsDataStore: ChatSettingsDataStore,
    dispatchersProvider: DispatchersProvider,
) : ViewModel() {
    var scrollPosition = ChatScrollPosition()
        private set

    val chatDisplaySettings: StateFlow<ChatDisplaySettings> =
        combine(
            appearanceSettingsDataStore.currentSettings,
            chatSettingsDataStore.currentSettings,
        ) { appearance, chat ->
            ChatDisplaySettings(
                fontSize = appearance.fontSize.toFloat(),
                animateGifs = chat.animateGifs,
                fullscreenButtonOpacity = appearance.fullscreenButtonOpacity,
                requireFullscreenExitConfirmation = appearance.requireFullscreenExitConfirmation,
                fabAnchor = appearance.fabAnchor,
                fabOffsetXFraction = appearance.fabOffsetXFraction,
                fabOffsetYFraction = appearance.fabOffsetYFraction,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(stopTimeoutMillis = 500L), ChatDisplaySettings())

    private val chat: StateFlow<List<ChatItem>> = chatMessageRepository.getChat(channel)

    private val mappingCache = LruCache<String, ChatMessageUiState>(MAPPING_CACHE_MIN_SIZE)
    private val checkeredTracker = CheckeredMessageTracker()
    private var lastChatSettings: ChatSettings? = null

    private val dateFormatter: DateTimeFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG).withLocale(Locale.getDefault())

    val chatUiStates: StateFlow<ImmutableList<ChatMessageUiState>> =
        combine(
            chat,
            appearanceSettingsDataStore.currentSettings,
            chatSettingsDataStore.currentSettings,
        ) { messages, appearanceSettings, chatSettings ->
            // Mapped results only depend on chat settings; appearance settings are either part
            // of the cache key (checkered background) or applied after mapping
            if (chatSettings != lastChatSettings) {
                mappingCache.evictAll()
                // The cache must fit the whole scrollback, an LruCache smaller than the
                // sequentially scanned message list degrades to a 100% miss rate
                mappingCache.resize(maxOf(chatSettings.scrollbackLength + MAPPING_CACHE_MARGIN, MAPPING_CACHE_MIN_SIZE))
                lastChatSettings = chatSettings
            }

            val zone = ZoneId.systemDefault()
            val result = ArrayList<ChatMessageUiState>(messages.size + 8)
            // Cache keys aligned with result, null for uncached items (date separators)
            val resultCacheKeys = ArrayList<String?>(messages.size + 8)
            var cachedDayStartMillis = 0L
            var cachedDayEndMillis = 0L
            var previousEpochDay = Long.MIN_VALUE
            for (index in messages.indices) {
                val item = messages[index]
                val altBg = appearanceSettings.checkeredMessages && checkeredTracker.isAlternate(item.message.id)
                val cacheKey = if (altBg) "${item.mappingCacheKey}-true" else item.mappingCacheKey

                val mapped =
                    mappingCache[cacheKey] ?: chatMessageMapper
                        .mapToUiState(
                            item = item,
                            chatSettings = chatSettings,
                            preferenceStore = preferenceStore,
                            isAlternateBackground = altBg,
                        ).also { mappingCache.put(cacheKey, it) }

                val ts = item.message.timestamp

                @Suppress("EmptyRange")
                val currentEpochDay = when {
                    ts in cachedDayStartMillis..<cachedDayEndMillis -> {
                        previousEpochDay
                    }

                    else -> {
                        val day = Instant.ofEpochMilli(ts).atZone(zone).toLocalDate()
                        cachedDayStartMillis = day.atStartOfDay(zone).toInstant().toEpochMilli()
                        cachedDayEndMillis = day
                            .plusDays(1)
                            .atStartOfDay(zone)
                            .toInstant()
                            .toEpochMilli()
                        day.toEpochDay()
                    }
                }
                if (previousEpochDay != Long.MIN_VALUE && currentEpochDay != previousEpochDay) {
                    val day = LocalDate.ofEpochDay(currentEpochDay)
                    val timestamp =
                        if (chatSettings.showTimestamps) {
                            DateTimeUtils.timestampToLocalTime(cachedDayStartMillis, chatSettings.formatter)
                        } else {
                            ""
                        }
                    result +=
                        ChatMessageUiState.DateSeparatorUi(
                            id = "date-sep-$day",
                            timestamp = timestamp,
                            dateText = day.format(dateFormatter),
                        )
                    resultCacheKeys += null
                }
                previousEpochDay = currentEpochDay

                result += mapped
                resultCacheKeys += cacheKey
            }

            // Write laid-out items back into the cache so unchanged layouts skip the copy on
            // the next emission
            result.applyHighlightLayout(showLineSeparator = appearanceSettings.lineSeparator) { index, updated ->
                resultCacheKeys[index]?.let { mappingCache.put(it, updated) }
            }
            result.toImmutableList()
        }.flowOn(dispatchersProvider.default + CoroutineName("ChatViewModel[$channel]"))
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(stopTimeoutMillis = 500L), persistentListOf())

    fun manageAutomodMessage(
        heldMessageId: String,
        channel: UserName,
        allow: Boolean,
    ) {
        viewModelScope.launch {
            val userId = authDataStore.userIdString ?: return@launch
            val action = if (allow) "ALLOW" else "DENY"

            helixApiClient
                .manageAutomodMessage(userId, heldMessageId, action)
                .onFailure { error ->
                    logger.error(error) { "Failed to $action automod message $heldMessageId" }
                    val statusCode = (error as? HelixApiException)?.status?.value
                    chatMessageRepository.addSystemMessage(
                        channel,
                        SystemMessageType.AutomodActionFailed(statusCode = statusCode, allow = allow),
                    )
                }
        }
    }

    fun persistFabPosition(
        anchor: FabAnchor,
        xFraction: Float,
        yFraction: Float,
    ) {
        viewModelScope.launch {
            appearanceSettingsDataStore.update {
                it.copy(
                    fabAnchor = anchor,
                    fabOffsetXFraction = xFraction,
                    fabOffsetYFraction = yFraction,
                )
            }
        }
    }

    fun updateScrollPosition(position: ChatScrollPosition) {
        scrollPosition = position
    }
}

@Immutable
data class ChatDisplaySettings(
    val fontSize: Float = 14f,
    val animateGifs: Boolean = true,
    val fullscreenButtonOpacity: Float = 0.75f,
    val requireFullscreenExitConfirmation: Boolean = false,
    val fabAnchor: FabAnchor = FabAnchor.BottomEnd,
    val fabOffsetXFraction: Float = 0f,
    val fabOffsetYFraction: Float = 0f,
)

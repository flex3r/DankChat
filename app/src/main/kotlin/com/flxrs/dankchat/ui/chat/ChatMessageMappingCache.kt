package com.flxrs.dankchat.ui.chat

import android.util.LruCache
import com.flxrs.dankchat.preferences.chat.ChatSettings

private const val MAPPING_CACHE_MIN_SIZE = 512
private const val MAPPING_CACHE_MARGIN = 64

// Mapped results only depend on chat settings, appearance settings are either part of the key
// (checkered background) or applied after mapping
class ChatMessageMappingCache {
    private val cache = LruCache<String, ChatMessageUiState>(MAPPING_CACHE_MIN_SIZE)
    private var lastChatSettings: ChatSettings? = null

    fun prepare(
        chatSettings: ChatSettings,
        itemCount: Int,
    ) {
        if (chatSettings != lastChatSettings) {
            cache.evictAll()
            lastChatSettings = chatSettings
        }
        // The cache must fit the whole list, an LruCache smaller than the sequentially scanned
        // list degrades to a 100% miss rate
        val size = maxOf(itemCount + MAPPING_CACHE_MARGIN, MAPPING_CACHE_MIN_SIZE)
        if (size != cache.maxSize()) {
            cache.resize(size)
        }
    }

    fun getOrMap(
        key: String,
        map: () -> ChatMessageUiState,
    ): ChatMessageUiState = cache[key] ?: map().also { cache.put(key, it) }

    fun put(
        key: String,
        state: ChatMessageUiState,
    ) {
        cache.put(key, state)
    }
}

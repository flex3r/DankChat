package com.flxrs.dankchat.data.repo.chat

import androidx.collection.LruCache
import com.flxrs.dankchat.data.DisplayName
import com.flxrs.dankchat.data.UserName
import com.flxrs.dankchat.di.DispatchersProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.core.annotation.Single
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration.Companion.milliseconds

@Single
class UsersRepository(
    dispatchersProvider: DispatchersProvider,
) {
    private val scope = CoroutineScope(SupervisorJob() + dispatchersProvider.default)
    private val users = ConcurrentHashMap<UserName, LruCache<UserName, DisplayName>>()
    private val usersFlows = ConcurrentHashMap<UserName, MutableStateFlow<Set<DisplayName>>>()
    private val userColors = LruCache<UserName, Int>(USER_COLOR_CACHE_SIZE)
    private val pendingPublications: MutableSet<UserName> = ConcurrentHashMap.newKeySet()

    fun getUsersFlow(channel: UserName): StateFlow<Set<DisplayName>> = usersFlows.getOrPut(channel) { MutableStateFlow(emptySet()) }

    fun findDisplayName(
        channel: UserName,
        userName: UserName,
    ): DisplayName? = users[channel]?.get(userName)

    fun updateUsers(
        channel: UserName,
        new: List<Pair<UserName, DisplayName>>,
    ) {
        val current = users.getOrPut(channel) { LruCache(USER_CACHE_SIZE) }
        var changed = false
        new.forEach { (name, displayName) ->
            if (current.put(name, displayName) != displayName) {
                changed = true
            }
        }

        if (changed) {
            schedulePublication(channel)
        }
    }

    fun updateUser(
        channel: UserName,
        name: UserName,
        displayName: DisplayName,
    ) {
        val current = users.getOrPut(channel) { LruCache(USER_CACHE_SIZE) }
        if (current.put(name, displayName) != displayName) {
            schedulePublication(channel)
        }
    }

    // Publishing snapshots the whole cache, changes are coalesced so bursts of new chatters
    // produce a bounded amount of copies
    private fun schedulePublication(channel: UserName) {
        if (!pendingPublications.add(channel)) {
            return
        }

        scope.launch {
            delay(PUBLICATION_INTERVAL)
            pendingPublications.remove(channel)
            val cache = users[channel] ?: return@launch
            usersFlows
                .getOrPut(channel) { MutableStateFlow(emptySet()) }
                .update { cache.snapshot().values.toSet() }
        }
    }

    fun updateGlobalUser(
        name: UserName,
        displayName: DisplayName,
    ) = updateUser(GLOBAL_CHANNEL_TAG, name, displayName)

    fun isGlobalChannel(channel: UserName) = channel == GLOBAL_CHANNEL_TAG

    fun clearGlobalUsers() {
        users[GLOBAL_CHANNEL_TAG]?.evictAll()
        usersFlows[GLOBAL_CHANNEL_TAG]?.value = emptySet()
    }

    fun initChannel(channel: UserName) {
        users.getOrPut(channel) { LruCache(USER_CACHE_SIZE) }
        usersFlows.getOrPut(channel) { MutableStateFlow(emptySet()) }
    }

    fun removeChannel(channel: UserName) {
        users.remove(channel)
        usersFlows.remove(channel)
    }

    fun cacheUserColor(
        userName: UserName,
        color: Int,
    ) {
        userColors.put(userName, color)
    }

    fun getCachedUserColor(userName: UserName): Int? = userColors.get(userName)

    companion object {
        private const val USER_CACHE_SIZE = 5000
        private const val USER_COLOR_CACHE_SIZE = 1000
        private val PUBLICATION_INTERVAL = 500.milliseconds
        private val GLOBAL_CHANNEL_TAG = UserName("*")
    }
}

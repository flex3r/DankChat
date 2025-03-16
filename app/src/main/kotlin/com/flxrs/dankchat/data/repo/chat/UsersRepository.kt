package com.flxrs.dankchat.data.repo.chat

import androidx.collection.LruCache
import com.flxrs.dankchat.data.DisplayName
import com.flxrs.dankchat.data.UserName
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import org.koin.core.annotation.Single
import java.util.concurrent.ConcurrentHashMap

@Single
class UsersRepository {
    private val users = ConcurrentHashMap<UserName, LruCache<UserName, DisplayName>>()
    private val usersFlows = ConcurrentHashMap<UserName, MutableStateFlow<Set<DisplayName>>>()

    fun getUsersFlow(channel: UserName): StateFlow<Set<DisplayName>> = usersFlows.getOrPut(channel) { MutableStateFlow(emptySet()) }
    fun findDisplayName(channel: UserName, userName: UserName): DisplayName? = users[channel]?.get(userName)

    fun updateUsers(channel: UserName, new: List<Pair<UserName, DisplayName>>) {
        val current = users.getOrPut(channel) { LruCache(USER_CACHE_SIZE) }
        new.forEach { current.put(it.first, it.second) }

        usersFlows
            .getOrPut(channel) { MutableStateFlow(emptySet()) }
            .update { current.snapshot().values.toSet() }
    }

    fun updateUser(channel: UserName, name: UserName, displayName: DisplayName) {
        val current = users.getOrPut(channel) { LruCache(USER_CACHE_SIZE) }
        current.put(name, displayName)

        usersFlows
            .getOrPut(channel) { MutableStateFlow(emptySet()) }
            .update { current.snapshot().values.toSet() }
    }

    fun updateGlobalUser(name: UserName, displayName: DisplayName) = updateUser(GLOBAL_CHANNEL_TAG, name, displayName)

    fun isGlobalChannel(channel: UserName) = channel == GLOBAL_CHANNEL_TAG

    fun initChannel(channel: UserName) {
        users.getOrPut(channel) { LruCache(USER_CACHE_SIZE) }
        usersFlows.getOrPut(channel) { MutableStateFlow(emptySet()) }
    }

    fun removeChannel(channel: UserName) {
        users.remove(channel)
        usersFlows.remove(channel)
    }

    companion object {
        private const val USER_CACHE_SIZE = 5000
        private val GLOBAL_CHANNEL_TAG = UserName("*")
    }
}

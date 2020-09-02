package com.flxrs.dankchat.service

import android.util.Log
import androidx.collection.LruCache
import com.flxrs.dankchat.chat.ChatItem
import com.flxrs.dankchat.preferences.multientry.MultiEntryItem
import com.flxrs.dankchat.service.api.TwitchApi
import com.flxrs.dankchat.service.irc.IrcMessage
import com.flxrs.dankchat.service.twitch.connection.SystemMessageType
import com.flxrs.dankchat.service.twitch.connection.WebSocketConnection
import com.flxrs.dankchat.service.twitch.message.Mention
import com.flxrs.dankchat.service.twitch.message.Message
import com.flxrs.dankchat.service.twitch.message.Roomstate
import com.flxrs.dankchat.service.twitch.message.matches
import com.flxrs.dankchat.utils.extensions.addAndLimit
import com.flxrs.dankchat.utils.extensions.mapToMention
import com.flxrs.dankchat.utils.extensions.replaceWithTimeOut
import com.flxrs.dankchat.utils.extensions.replaceWithTimeOuts
import com.squareup.moshi.Moshi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ConflatedBroadcastChannel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.nio.ByteBuffer
import kotlin.collections.set
import kotlin.system.measureTimeMillis

class ChatRepository {

    private val _notificationMessageChannel = Channel<List<ChatItem>>(10) // TODO replace with SharedFlow when available
    private val _mentionCounts = ConflatedBroadcastChannel<MutableMap<String, Int>>(mutableMapOf())
    private val messages = mutableMapOf<String, MutableStateFlow<List<ChatItem>>>()
    private val connectionState = mutableMapOf<String, MutableStateFlow<SystemMessageType>>()
    private val roomStates = mutableMapOf<String, MutableStateFlow<Roomstate>>()
    private val users = mutableMapOf<String, MutableStateFlow<LruCache<String, Boolean>>>()
    private val ignoredList = mutableListOf<Int>()

    private val moshi = Moshi.Builder().build()
    private val adapter = moshi.adapter(MultiEntryItem.Entry::class.java)

    private val client = OkHttpClient.Builder().retryOnConnectionFailure(true).build()
    private val request = Request.Builder().url("wss://irc-ws.chat.twitch.tv").build()
    private val readConnection = WebSocketConnection("reader", CoroutineScope(Dispatchers.IO + Job()), client, request, ::handleDisconnect, ::onReaderMessage)
    private val writeConnection = WebSocketConnection("writer", CoroutineScope(Dispatchers.IO + Job()), client, request, null, ::onWriterMessage)

    private var hasDisconnected = true
    private var customMentionEntries = listOf<Mention>()
    private var blacklistEntries = listOf<Mention>()
    private var name: String = ""

    val notificationMessageChannel: ReceiveChannel<List<ChatItem>>
        get() = _notificationMessageChannel
    val mentionCounts: Flow<Map<String, Int>>
        get() = _mentionCounts.asFlow()
    var startedConnection = false
    var lastMessage = mutableMapOf<String, String>()
    var scrollbackLength = 500
        set(value) {
            messages.forEach { (_, messagesFlow) ->
                if (messagesFlow.value.size > scrollbackLength) {
                    messagesFlow.value = messagesFlow.value.take(value)
                }
            }
            field = value
        }

    fun getChat(channel: String): StateFlow<List<ChatItem>> = messages.getOrPut(channel) { MutableStateFlow(emptyList()) }
    fun getConnectionState(channel: String): StateFlow<SystemMessageType> = connectionState.getOrPut(channel) { MutableStateFlow(SystemMessageType.DISCONNECTED) }
    fun getRoomState(channel: String): StateFlow<Roomstate> = roomStates.getOrPut(channel) { MutableStateFlow(Roomstate(channel)) }
    fun getUsers(channel: String): StateFlow<LruCache<String, Boolean>> = users.getOrPut(channel) { MutableStateFlow(createUserCache()) }

    suspend fun loadRecentMessages(channel: String, loadHistory: Boolean) {
        if (loadHistory) {
            loadRecentMessages(channel)
        } else {
            val currentChat = messages[channel]?.value ?: emptyList()
            messages[channel]?.value = listOf(ChatItem(Message.SystemMessage(state = SystemMessageType.NO_HISTORY_LOADED), false)) + currentChat
        }
    }

    suspend fun loadIgnores(oAuth: String, id: String) {
        if (oAuth.isNotBlank()) {
            TwitchApi.getIgnores(oAuth, id)?.let { result ->
                ignoredList.clear()
                ignoredList.addAll(result.blocks.map { it.user.id })
            }
        }
    }

    fun removeChannelData(channel: String) {
        messages[channel]?.value = emptyList()
        messages.remove(channel)
        lastMessage.remove(channel)
        _mentionCounts.value.remove(channel)
        TwitchApi.clearChannelFromLoaded(channel)
    }

    fun clearMentionCount(channel: String) = with(_mentionCounts) {
        offer(value.apply { set(channel, 0) })
    }

    @Synchronized
    fun handleDisconnect() {
        if (!hasDisconnected) {
            hasDisconnected = true
            val state = SystemMessageType.DISCONNECTED
            connectionState.keys.forEach {
                connectionState[it]?.value = state
            }
            makeAndPostConnectionMessage(state)
        }
    }

    fun clear(channel: String) {
        messages[channel]?.value = emptyList()
    }

    fun close(onClosed: () -> Unit = { }): Boolean {
        startedConnection = false
        return writeConnection.close(onClosed) && readConnection.close(onClosed)
    }

    fun reconnect(onlyIfNecessary: Boolean) {
        readConnection.reconnect(onlyIfNecessary)
        writeConnection.reconnect(onlyIfNecessary)
    }

    fun sendMessage(channel: String, input: String) = prepareMessage(channel, input) {
        writeConnection.sendMessage(it)
    }

    fun connect(nick: String, oauth: String, forceConnect: Boolean = false) {
        if (!startedConnection) {
            name = nick
            readConnection.connect(nick, oauth, forceConnect)
            writeConnection.connect(nick, oauth, forceConnect)
            startedConnection = true
        }
    }

    fun joinChannel(channel: String) {
        createFlowsIfNecessary(channel)
        readConnection.joinChannel(channel)
        writeConnection.joinChannel(channel)
    }

    fun partChannel(channel: String) {
        readConnection.partChannel(channel)
        writeConnection.partChannel(channel)
    }

    private fun createFlowsIfNecessary(channel: String) {
        if (!messages.contains(channel)) messages[channel] = MutableStateFlow(emptyList())
        if (!connectionState.contains(channel)) connectionState[channel] = MutableStateFlow(SystemMessageType.DISCONNECTED)
        if (!roomStates.contains(channel)) roomStates[channel] = MutableStateFlow(Roomstate(channel))
        if (!users.contains(channel)) users[channel] = MutableStateFlow(createUserCache())
        with(_mentionCounts) {
            if (!value.contains(channel)) offer(value.apply { set(channel, 0) })
        }
    }

    private inline fun prepareMessage(channel: String, message: String, onResult: (msg: String) -> Unit) {
        if (message.isNotBlank()) {
            val messageWithSuffix = when (lastMessage[channel] ?: "") {
                message -> "$message $INVISIBLE_CHAR"
                else -> message
            }

            onResult("PRIVMSG #$channel :$messageWithSuffix")
        }
    }

    private fun onWriterMessage(message: IrcMessage) {
        if (message.isLoginFailed()) {
            startedConnection = false
        }
        when (message.command) {
            "PRIVMSG" -> Unit
            "366" -> handleConnected(message.params[1].substring(1), writeConnection.isAnonymous)
            else -> onMessage(message)
        }
    }

    private fun onReaderMessage(message: IrcMessage) {
        if (message.command == "PRIVMSG") {
            onMessage(message)
        }
    }

    private fun onMessage(msg: IrcMessage): List<ChatItem>? {
        when (msg.command) {
            "353" -> handleNames(msg)
            "JOIN" -> handleJoin(msg)
            "CLEARCHAT" -> handleClearchat(msg)
            "ROOMSTATE" -> handleRoomstate(msg)
            "CLEARMSG" -> handleClearmsg(msg)
            else -> handleMessage(msg)
        }
        return null
    }

    fun clearIgnores() {
        ignoredList.clear()
    }

    suspend fun setMentionEntries(stringSet: Set<String>?) = withContext(Dispatchers.Default) {
        customMentionEntries = stringSet.mapToMention(adapter)
    }

    suspend fun setBlacklistEntries(stringSet: Set<String>?) = withContext(Dispatchers.Default) {
        blacklistEntries = stringSet.mapToMention(adapter)
    }

    private fun handleConnected(channel: String, isAnonymous: Boolean) {
        hasDisconnected = false

        val connection = when {
            isAnonymous -> SystemMessageType.NOT_LOGGED_IN
            else -> SystemMessageType.CONNECTED
        }
        makeAndPostConnectionMessage(connection, setOf(channel))
        connectionState[channel]?.value = connection
    }

    private fun handleClearchat(msg: IrcMessage) {
        val parsed = Message.TwitchMessage.parse(msg).map { ChatItem(it) }
        val target = if (msg.params.size > 1) msg.params[1] else ""
        val channel = msg.params[0].substring(1)

        messages[channel]?.value?.replaceWithTimeOuts(target)?.also {
            messages[channel]?.value = it.addAndLimit(parsed[0], scrollbackLength)
        }
    }

    private fun handleRoomstate(msg: IrcMessage) {
        val channel = msg.params[0].substring(1)
        val state = roomStates[channel]?.value ?: Roomstate(channel)
        state.updateState(msg)

        roomStates[channel]?.value = state
    }

    private fun handleClearmsg(msg: IrcMessage) {
        val channel = msg.params[0].substring(1)
        val targetId = msg.tags["target-msg-id"] ?: return

        messages[channel]?.value?.replaceWithTimeOut(targetId)?.also {
            messages[channel]?.value = it
        }
    }

    private fun handleMessage(msg: IrcMessage) {
        msg.tags["user-id"]?.let { userId ->
            if (ignoredList.any { it == userId.toInt() }) return
        }
        val parsed = Message.TwitchMessage.parse(msg).map {
            if (it.name == name) lastMessage[it.channel] = it.message
            if (blacklistEntries.matches(it.message, it.name to it.displayName, it.emotes)) return

            it.checkForMention(name, customMentionEntries)
            val currentUsers = users[it.channel]?.value ?: createUserCache()
            currentUsers.put(it.name, true)
            users[it.channel]?.value = currentUsers

            if (it.isMention) {
                with(_mentionCounts) {
                    offer(value.apply {
                        val count = get(it.channel) ?: 0
                        set(it.channel, count + 1)
                    })
                }
            }

            ChatItem(it)
        }
        if (parsed.isNotEmpty()) {
            if (msg.params[0] == "*" || msg.command == "WHISPER") {
                messages.keys.forEach {
                    val currentChat = messages[it]?.value ?: emptyList()
                    messages[it]?.value = currentChat.addAndLimit(parsed, scrollbackLength)
                }
            } else {
                val channel = msg.params[0].substring(1)
                val currentChat = messages[channel]?.value ?: emptyList()
                messages[channel]?.value = currentChat.addAndLimit(parsed, scrollbackLength)
                _notificationMessageChannel.offer(parsed)
            }
        }
    }

    private fun handleNames(msg: IrcMessage) {
        val channel = msg.params.getOrNull(2)?.substring(1) ?: return
        val names = msg.params.getOrNull(3)?.split(' ') ?: return
        val currentUsers = users[channel]?.value ?: createUserCache()
        names.forEach { currentUsers.put(it, true) }
        users[channel]?.value = currentUsers
    }

    private fun handleJoin(msg: IrcMessage) {
        val channel = msg.params.getOrNull(0)?.substring(1) ?: return
        val name = msg.prefix.substringBefore('!')
        val currentUsers = users[channel]?.value ?: createUserCache()
        currentUsers.put(name, true)
        users[channel]?.value = currentUsers
    }

    private fun createUserCache(): LruCache<String, Boolean> {
        return LruCache(500)
    }

    private fun makeAndPostConnectionMessage(state: SystemMessageType, channels: Set<String> = messages.keys) {
        channels.forEach {
            val currentChat = messages[it]?.value ?: emptyList()
            messages[it]?.value = currentChat.addAndLimit(ChatItem(Message.SystemMessage(state = state)), scrollbackLength)
        }
    }

    private suspend fun loadRecentMessages(channel: String): Unit? = withContext(Dispatchers.Default) {
        val result = TwitchApi.getRecentMessages(channel) ?: return@withContext
        val items = mutableListOf<ChatItem>()
        measureTimeMillis {
            for (message in result.messages) {
                val parsedIrc = IrcMessage.parse(message)
                if (ignoredList.any { parsedIrc.tags["user-id"]?.toInt() == it }) continue

                for (msg in Message.TwitchMessage.parse(parsedIrc)) {
                    if (!blacklistEntries.matches(msg.message, msg.name to msg.displayName, msg.emotes)) {
                        msg.checkForMention(name, customMentionEntries)
                        items += ChatItem(msg)
                    }
                }
            }
        }.let { Log.i(TAG, "Parsing message history for #$channel took $it ms") }

        val current = messages[channel]?.value ?: emptyList()
        messages[channel]?.value = items.addAndLimit(current, scrollbackLength, checkForDuplications = true)
    }

    companion object {
        private val TAG = ChatRepository::class.java.simpleName
        private val INVISIBLE_CHAR = String(ByteBuffer.allocate(4).putInt(0x000E0000).array(), Charsets.UTF_32)
    }
}
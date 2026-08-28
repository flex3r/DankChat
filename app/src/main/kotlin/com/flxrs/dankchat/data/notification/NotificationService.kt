package com.flxrs.dankchat.data.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.graphics.Bitmap
import android.os.Binder
import android.os.IBinder
import android.util.LruCache
import androidx.core.app.NotificationCompat
import androidx.core.app.Person
import androidx.core.app.ServiceCompat
import androidx.core.content.LocusIdCompat
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.core.graphics.drawable.toBitmap
import coil3.asDrawable
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.size.Size
import com.flxrs.dankchat.R
import com.flxrs.dankchat.data.UserName
import com.flxrs.dankchat.data.auth.AuthDataStore
import com.flxrs.dankchat.data.repo.channel.ChannelRepository
import com.flxrs.dankchat.data.repo.chat.ChatNotificationRepository
import com.flxrs.dankchat.data.repo.chat.NotificationClearScope
import com.flxrs.dankchat.data.repo.data.DataRepository
import com.flxrs.dankchat.di.DispatchersProvider
import com.flxrs.dankchat.preferences.notifications.NotificationsSettingsDataStore
import com.flxrs.dankchat.ui.main.MainActivity
import com.flxrs.dankchat.utils.AppLifecycleListener
import com.flxrs.dankchat.utils.AppLifecycleListener.AppLifecycle
import com.flxrs.dankchat.utils.ForegroundServiceState
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.util.collections.ConcurrentSet
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.koin.android.ext.android.inject
import kotlin.concurrent.atomics.AtomicInt
import kotlin.coroutines.CoroutineContext
import kotlin.time.Duration.Companion.seconds

private val logger = KotlinLogging.logger("NotificationService")

class NotificationService :
    Service(),
    CoroutineScope {
    private val binder = LocalBinder()
    private val manager: NotificationManager by lazy { getSystemService(NOTIFICATION_SERVICE) as NotificationManager }

    private val conversationStore = NotificationConversationStore(NotificationCompat.MessagingStyle.MAXIMUM_RETAINED_MESSAGES)
    private val notificationMutex = Mutex()
    private val notifiedMessageIds: MutableSet<String> = ConcurrentSet()
    private val notificationIcons = LruCache<String, Bitmap>(MAX_NOTIFICATION_ICONS)
    private val appIcon: Bitmap by lazy {
        applicationInfo.loadIcon(packageManager).toBitmap(CONVERSATION_ICON_SIZE, CONVERSATION_ICON_SIZE, Bitmap.Config.ARGB_8888)
    }

    private val authDataStore: AuthDataStore by inject()
    private val chatNotificationRepository: ChatNotificationRepository by inject()
    private val channelRepository: ChannelRepository by inject()
    private val dataRepository: DataRepository by inject()
    private val notificationsSettingsDataStore: NotificationsSettingsDataStore by inject()
    private val appLifecycleListener: AppLifecycleListener by inject()
    private val dispatchersProvider: DispatchersProvider by inject()
    private val foregroundServiceState: ForegroundServiceState by inject()

    private val immutablePendingIntentFlag = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT

    private val job = SupervisorJob()
    override val coroutineContext: CoroutineContext
        get() = dispatchersProvider.io + job

    inner class LocalBinder(
        val service: NotificationService = this@NotificationService,
    ) : Binder()

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onDestroy() {
        coroutineContext.cancelChildren()
        manager.cancelAll()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
        super.onDestroy()
    }

    override fun onCreate() {
        super.onCreate()
        val name = getString(R.string.app_name)
        val channel = NotificationChannel(CHANNEL_ID_LOW, name, NotificationManager.IMPORTANCE_LOW).apply {
            enableVibration(false)
            enableLights(false)
            setShowBadge(false)
        }
        manager.createNotificationChannel(NotificationChannel(CHANNEL_ID_DEFAULT, "Mentions", NotificationManager.IMPORTANCE_DEFAULT))
        manager.createNotificationChannel(channel)

        launch {
            appLifecycleListener.appState
                .flatMapLatest { state ->
                    when (state) {
                        AppLifecycle.Foreground -> {
                            notifiedMessageIds.clear()
                            emptyFlow()
                        }

                        AppLifecycle.Background -> combine(
                            chatNotificationRepository.messageUpdates,
                            notificationsSettingsDataStore.showNotifications,
                        ) { items, enabled -> items to enabled }
                    }
                }.collect { (items, enabled) ->
                    if (!enabled) return@collect
                    items.forEach { (message) ->
                        if (!notifiedMessageIds.add(message.id)) return@forEach
                        if (notifiedMessageIds.size > MAX_NOTIFIED_IDS) {
                            notifiedMessageIds.iterator().run {
                                next()
                                remove()
                            }
                        }
                        message.toNotificationData()?.let { data ->
                            notificationMutex.withLock { data.createNotification() }
                        }
                    }
                }
        }

        launch {
            chatNotificationRepository.notificationClearRequests.collect { scope ->
                notificationMutex.withLock {
                    when (scope) {
                        NotificationClearScope.Mentions -> clearAllMentionNotifications()
                        NotificationClearScope.Whispers -> clearNotificationsForChannelLocked(UserName.EMPTY)
                    }
                }
            }
        }
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        when (intent?.action) {
            STOP_COMMAND -> launch { dataRepository.sendShutdownCommand() }

            DISMISS_CHANNEL_COMMAND -> intent.channelExtra()?.let { channel ->
                launch { notificationMutex.withLock { clearNotificationsForChannelLocked(channel) } }
            }

            DISMISS_WHISPER_COMMAND -> intent.userNameExtra()?.let { key ->
                launch { notificationMutex.withLock { clearWhisperNotification(key) } }
            }

            else -> startForeground()
        }
        return START_NOT_STICKY
    }

    override fun onTimeout(
        startId: Int,
        fgsType: Int,
    ) {
        logger.warn { "Stopping foreground service due to 6h timeout restriction.." }
        foregroundServiceState.setActive(false)
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    fun clearNotificationsForChannel(channel: UserName) {
        launch {
            notificationMutex.withLock { clearNotificationsForChannelLocked(channel) }
        }
    }

    private fun clearNotificationsForChannelLocked(channel: UserName) {
        if (channel == UserName.EMPTY) {
            conversationStore.whisperKeys().forEach(::clearWhisperNotification)
            return
        }
        conversationStore.clearChannel(channel)
        manager.cancel(channelTag(channel), CHANNEL_NOTIFICATION_ID)
    }

    private fun clearAllMentionNotifications() {
        conversationStore.channelKeys().forEach(::clearNotificationsForChannelLocked)
    }

    private fun startForeground(allowRetry: Boolean = true) {
        val startIntent = PendingIntent.getActivity(
            this,
            NOTIFICATION_START_INTENT_CODE,
            Intent(this, MainActivity::class.java),
            immutablePendingIntentFlag,
        )
        val stopIntent = PendingIntent.getService(
            this,
            NOTIFICATION_STOP_INTENT_CODE,
            Intent(this, NotificationService::class.java).setAction(STOP_COMMAND),
            immutablePendingIntentFlag,
        )
        val notification = NotificationCompat
            .Builder(this, CHANNEL_ID_LOW)
            .setSound(null)
            .setVibrate(null)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(R.string.notification_message))
            .addAction(R.drawable.ic_clear, getString(R.string.notification_stop), stopIntent)
            .setContentIntent(startIntent)
            .setSmallIcon(R.drawable.ic_notification_icon)
            .build()
        try {
            startForeground(NOTIFICATION_ID, notification)
        } catch (e: IllegalStateException) {
            logger.warn(e) { "Failed to promote service to foreground" }
            if (allowRetry) {
                launch {
                    delay(FOREGROUND_RETRY_DELAY)
                    startForeground(allowRetry = false)
                }
            }
        }
    }

    private suspend fun NotificationData.createNotification() {
        if (isWhisper) createWhisperNotification() else createChannelNotification()
    }

    private suspend fun NotificationData.createChannelNotification() {
        conversationStore.addChannelMessage(channel, this)
        updateChannelNotification(channel)
    }

    private suspend fun updateChannelNotification(channel: UserName) {
        val messages = conversationStore.channelMessages(channel)
        if (messages.isEmpty()) {
            manager.cancel(channelTag(channel), CHANNEL_NOTIFICATION_ID)
            return
        }
        val latest = messages.last()
        val channelIcon = latest.channelIcon()
        val messagesWithSenders = messages.map { it to it.senderPerson(it.senderIcon()) }
        val shortcutId = channelShortcutId(channel)
        publishShortcut(shortcutId, "#$channel", messagesWithSenders.map { it.second }.distinctBy { it.key }, openChannelIntent(channel), channelIcon)
        val style = NotificationCompat
            .MessagingStyle(currentUserPerson())
            .setConversationTitle("#$channel")
            .setGroupConversation(true)
        messagesWithSenders.forEach { (message, sender) -> style.addMessage(message.message, message.timestamp, sender) }
        val notification = NotificationCompat
            .Builder(this, CHANNEL_ID_DEFAULT)
            .setContentTitle("#$channel")
            .setContentText(
                getString(R.string.notification_message_with_sender, latest.sender().name, latest.message),
            ).setContentIntent(openChannelPendingIntent(latest, includeMessage = true))
            .setDeleteIntent(dismissChannelPendingIntent(channel))
            .setSmallIcon(R.drawable.ic_notification_icon)
            .setLargeIcon(channelIcon)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setShortcutId(shortcutId)
            .setLocusId(LocusIdCompat(shortcutId))
            .setStyle(style)
            .setAutoCancel(true)
            .build()
        manager.notify(channelTag(channel), CHANNEL_NOTIFICATION_ID, notification)
    }

    private suspend fun NotificationData.createWhisperNotification() {
        val key = name.lowercase()
        conversationStore.addWhisperMessage(key, this, ConversationMessage(message, timestamp, sender()))
        updateWhisperNotification(key, senderIcon())
    }

    private suspend fun updateWhisperNotification(
        key: UserName,
        icon: Bitmap? = null,
    ) {
        val state = conversationStore.whisper(key) ?: return
        val target = state.target
        val conversationIcon = icon ?: target.senderIcon()
        val shortcutId = whisperShortcutId(target)
        val conversationTitle = getString(R.string.notification_whisper_conversation_title, target.displayName.value)
        publishShortcut(shortcutId, conversationTitle, listOf(target.senderPerson(conversationIcon)), openWhisperIntent(target.name), conversationIcon)
        val currentUser = currentUserSender()
        val currentUserIcon = currentUserIcon()
        val style = NotificationCompat
            .MessagingStyle(currentUser.toPerson(currentUserIcon))
            .setConversationTitle(conversationTitle)
            .setGroupConversation(true)
        val targetKey = target.sender().key
        state.messages.forEach { message ->
            val senderIcon = when (message.sender.key) {
                targetKey -> conversationIcon
                currentUser.key -> currentUserIcon
                else -> null
            }
            style.addMessage(message.text, message.timestamp, message.sender.toPerson(senderIcon))
        }
        val builder = NotificationCompat
            .Builder(this, CHANNEL_ID_DEFAULT)
            .setContentTitle(getString(R.string.notification_whisper_mention, target.name))
            .setContentText(state.messages.last().text)
            .setSubText(getString(R.string.whispers))
            .setContentIntent(openWhisperPendingIntent(target.name))
            .setDeleteIntent(dismissWhisperPendingIntent(key))
            .setSmallIcon(R.drawable.ic_notification_icon)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setShortcutId(shortcutId)
            .setLocusId(LocusIdCompat(shortcutId))
            .setStyle(style)
            .setAutoCancel(true)
        manager.notify(whisperTag(key), WHISPER_NOTIFICATION_ID, builder.build())
    }

    private fun clearWhisperNotification(key: UserName) {
        conversationStore.clearWhisper(key)
        manager.cancel(whisperTag(key), WHISPER_NOTIFICATION_ID)
    }

    private fun publishShortcut(
        id: String,
        label: String,
        people: List<Person>,
        intent: Intent,
        icon: Bitmap,
    ) {
        runCatching {
            val shortcut = ShortcutInfoCompat
                .Builder(this, id)
                .setShortLabel(label)
                .setLongLabel(label)
                .setIcon(IconCompat.createWithBitmap(icon))
                .setIntent(intent)
                .setPersons(people.toTypedArray())
                .setIsConversation()
                .build()
            if (!ShortcutManagerCompat.pushDynamicShortcut(this, shortcut)) {
                logger.warn { "Failed to publish conversation shortcut: $id" }
            }
        }.onFailure { logger.warn(it) { "Failed to publish conversation shortcut: $id" } }
    }

    private suspend fun NotificationData.channelIcon(): Bitmap = loadNotificationIcon("channel:${channel.value}") {
        channelRepository.getUserDtoByName(channel)?.avatarUrl
    }

    private suspend fun NotificationData.senderIcon(): Bitmap = loadNotificationIcon("user:${userId?.value ?: name.value}") {
        userId?.let { channelRepository.getUserDto(it) }?.avatarUrl
            ?: channelRepository.getUserDtoByName(name)?.avatarUrl
    }

    private suspend fun loadNotificationIcon(
        cacheKey: String,
        findUrl: suspend () -> String?,
    ): Bitmap {
        notificationIcons[cacheKey]?.let { return it }
        val url = try {
            findUrl()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.warn(e) { "Failed to find profile image for notification" }
            null
        }
        if (url.isNullOrBlank()) return appIcon

        return try {
            val request = ImageRequest
                .Builder(this@NotificationService)
                .data(url)
                .allowHardware(false)
                .size(Size(CONVERSATION_ICON_SIZE, CONVERSATION_ICON_SIZE))
                .build()
            imageLoader
                .execute(request)
                .image
                ?.asDrawable(resources)
                ?.toBitmap(CONVERSATION_ICON_SIZE, CONVERSATION_ICON_SIZE, Bitmap.Config.ARGB_8888)
                ?.also { notificationIcons.put(cacheKey, it) }
                ?: appIcon
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.warn(e) { "Failed to load profile image for notification" }
            appIcon
        }
    }

    private fun NotificationData.sender() = ConversationSender(
        name = name.formatWithDisplayName(displayName),
        key = userId?.value ?: name.value,
    )

    private fun NotificationData.senderPerson(icon: Bitmap? = null): Person = sender().toPerson(icon)

    private fun currentUserSender() = ConversationSender(
        name = authDataStore.displayName?.value ?: authDataStore.userName?.value ?: getString(R.string.app_name),
        key = authDataStore.userIdString?.value ?: authDataStore.userName?.value ?: "self",
    )

    private suspend fun currentUserIcon(): Bitmap = loadNotificationIcon("user:${currentUserSender().key}") {
        authDataStore.userIdString?.let { channelRepository.getUserDto(it) }?.avatarUrl
            ?: authDataStore.userName?.let { channelRepository.getUserDtoByName(it) }?.avatarUrl
    }

    private suspend fun currentUserPerson(): Person = currentUserSender().toPerson(currentUserIcon())

    private fun ConversationSender.toPerson(icon: Bitmap? = null): Person = Person
        .Builder()
        .setName(name)
        .setKey(key)
        .apply { icon?.let { setIcon(IconCompat.createWithBitmap(it)) } }
        .build()

    private fun openChannelPendingIntent(
        data: NotificationData,
        includeMessage: Boolean,
    ): PendingIntent = PendingIntent.getActivity(
        this,
        notificationIntentCode.fetchAndAdd(1),
        openChannelIntent(data.channel).apply {
            if (includeMessage) putExtra(MainActivity.OPEN_MESSAGE_KEY, data.id)
        },
        immutablePendingIntentFlag,
    )

    private fun openWhisperPendingIntent(target: UserName): PendingIntent = PendingIntent.getActivity(
        this,
        notificationIntentCode.fetchAndAdd(1),
        openWhisperIntent(target),
        immutablePendingIntentFlag,
    )

    private fun openChannelIntent(channel: UserName): Intent = Intent(this, MainActivity::class.java).apply {
        action = Intent.ACTION_VIEW
        putExtra(MainActivity.OPEN_CHANNEL_KEY, channel.value)
    }

    private fun openWhisperIntent(target: UserName): Intent = openChannelIntent(UserName.EMPTY).apply {
        putExtra(MainActivity.OPEN_WHISPER_TARGET_KEY, target.value)
    }

    private fun dismissChannelPendingIntent(channel: UserName): PendingIntent = servicePendingIntent(DISMISS_CHANNEL_COMMAND) { putExtra(EXTRA_CHANNEL, channel) }

    private fun dismissWhisperPendingIntent(key: UserName): PendingIntent = servicePendingIntent(DISMISS_WHISPER_COMMAND) { putExtra(EXTRA_USER_NAME, key) }

    private fun servicePendingIntent(
        command: String,
        configure: Intent.() -> Unit,
    ): PendingIntent = PendingIntent.getService(
        this,
        notificationIntentCode.fetchAndAdd(1),
        Intent(this, NotificationService::class.java).apply {
            action = command
            configure()
        },
        immutablePendingIntentFlag,
    )

    @Suppress("DEPRECATION")
    private fun Intent.channelExtra(): UserName? = getParcelableExtra(EXTRA_CHANNEL)

    @Suppress("DEPRECATION")
    private fun Intent.userNameExtra(): UserName? = getParcelableExtra(EXTRA_USER_NAME)

    private fun channelTag(channel: UserName) = "channel_${channel.value}"

    private fun whisperTag(key: UserName) = "whisper_${key.value}"

    private fun channelShortcutId(channel: UserName) = "channel_${channel.value}"

    private fun whisperShortcutId(data: NotificationData) = "whisper_${data.userId?.value ?: data.name.value}"

    companion object {
        private const val CHANNEL_ID_LOW = "com.flxrs.dankchat.dank_id"
        private const val CHANNEL_ID_DEFAULT = "com.flxrs.dankchat.very_dank_id"
        private const val NOTIFICATION_ID = 77777
        private const val NOTIFICATION_START_INTENT_CODE = 66666
        private const val NOTIFICATION_STOP_INTENT_CODE = 55555
        private const val CHANNEL_NOTIFICATION_ID = 12345
        private const val WHISPER_NOTIFICATION_ID = 23456

        private const val STOP_COMMAND = "STOP_DANKING"
        private const val DISMISS_CHANNEL_COMMAND = "com.flxrs.dankchat.notification.DISMISS_CHANNEL"
        private const val DISMISS_WHISPER_COMMAND = "com.flxrs.dankchat.notification.DISMISS_WHISPER"

        private const val EXTRA_CHANNEL = "notification_channel"
        private const val EXTRA_USER_NAME = "notification_user_name"

        private const val MAX_NOTIFIED_IDS = 500
        private const val MAX_NOTIFICATION_ICONS = 100
        private const val CONVERSATION_ICON_SIZE = 128
        private val FOREGROUND_RETRY_DELAY = 5.seconds
        private val notificationIntentCode = AtomicInt(420)
    }
}

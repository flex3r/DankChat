package com.flxrs.dankchat.data.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.flxrs.dankchat.R
import com.flxrs.dankchat.data.UserName
import com.flxrs.dankchat.data.repo.chat.ChatChannelProvider
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import java.util.concurrent.ConcurrentHashMap
import kotlin.concurrent.atomics.AtomicInt
import kotlin.coroutines.CoroutineContext
import kotlin.time.Duration.Companion.seconds

private val logger = KotlinLogging.logger("NotificationService")

class NotificationService :
    Service(),
    CoroutineScope {
    private val binder = LocalBinder()
    private val manager: NotificationManager by lazy { getSystemService(NOTIFICATION_SERVICE) as NotificationManager }

    private val notifications = ConcurrentHashMap<UserName, MutableList<Int>>()
    private val notifiedMessageIds: MutableSet<String> = ConcurrentSet()

    private val chatNotificationRepository: ChatNotificationRepository by inject()
    private val chatChannelProvider: ChatChannelProvider by inject()
    private val dataRepository: DataRepository by inject()
    private val notificationsSettingsDataStore: NotificationsSettingsDataStore by inject()
    private val appLifecycleListener: AppLifecycleListener by inject()
    private val dispatchersProvider: DispatchersProvider by inject()
    private val foregroundServiceState: ForegroundServiceState by inject()

    private val pendingIntentFlag: Int = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT

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
        val channel =
            NotificationChannel(CHANNEL_ID_LOW, name, NotificationManager.IMPORTANCE_LOW).apply {
                enableVibration(false)
                enableLights(false)
                setShowBadge(false)
            }

        val mentionChannel = NotificationChannel(CHANNEL_ID_DEFAULT, "Mentions", NotificationManager.IMPORTANCE_DEFAULT)
        manager.createNotificationChannel(mentionChannel)
        manager.createNotificationChannel(channel)

        launch {
            appLifecycleListener.appState
                .flatMapLatest { state ->
                    when (state) {
                        AppLifecycle.Foreground -> {
                            notifiedMessageIds.clear()
                            emptyFlow()
                        }

                        AppLifecycle.Background -> {
                            combine(
                                chatNotificationRepository.messageUpdates,
                                notificationsSettingsDataStore.settings,
                            ) { items, settings -> items to settings }
                        }
                    }
                }.collect { (items, settings) ->
                    if (!settings.showNotifications) {
                        return@collect
                    }

                    items.forEach { (message) ->
                        if (!notifiedMessageIds.add(message.id)) {
                            return@forEach
                        }
                        if (notifiedMessageIds.size > MAX_NOTIFIED_IDS) {
                            val iterator = notifiedMessageIds.iterator()
                            iterator.next()
                            iterator.remove()
                        }
                        val notificationData = message.toNotificationData() ?: return@forEach
                        if (!notificationData.isWhisper && !settings.areChannelNotificationsEnabled(notificationData.channel)) {
                            return@forEach
                        }
                        notificationData.createMentionNotification()
                    }
                }
        }

        // Clear Android notifications when switching channels while the app is in the foreground
        launch {
            combine(
                appLifecycleListener.appState,
                chatChannelProvider.activeChannel.filterNotNull(),
            ) { lifecycle, channel -> lifecycle to channel }
                .collect { (lifecycle, channel) ->
                    if (lifecycle == AppLifecycle.Foreground) {
                        clearNotificationsForChannel(channel)
                    }
                }
        }

        // React to UI signals that mention/whisper notifications were viewed.
        launch {
            chatNotificationRepository.notificationClearRequests.collect { scope ->
                when (scope) {
                    NotificationClearScope.Mentions -> clearAllMentionNotifications()
                    NotificationClearScope.Whispers -> clearNotificationsForChannel(UserName.EMPTY)
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
        // stopSelf alone does not stop the service while clients are bound, demote explicitly to satisfy the timeout deadline
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    fun clearNotificationsForChannel(channel: UserName) {
        val ids = notifications.remove(channel)
        ids?.forEach { manager.cancel(it) }

        if (notifications.isEmpty()) {
            manager.cancel(SUMMARY_NOTIFICATION_ID)
            manager.cancelAll()
        }
    }

    private fun clearAllMentionNotifications() {
        val mentionChannels = notifications.keys.filter { it != UserName.EMPTY }
        mentionChannels.forEach { clearNotificationsForChannel(it) }
    }

    private fun startForeground(allowRetry: Boolean = true) {
        val title = getString(R.string.notification_title)
        val message = getString(R.string.notification_message)

        val pendingStartActivityIntent =
            Intent(this, MainActivity::class.java).let {
                PendingIntent.getActivity(this, NOTIFICATION_START_INTENT_CODE, it, pendingIntentFlag)
            }

        val pendingStopIntent =
            Intent(this, NotificationService::class.java).let {
                it.action = STOP_COMMAND
                PendingIntent.getService(this, NOTIFICATION_STOP_INTENT_CODE, it, pendingIntentFlag)
            }

        val notification =
            NotificationCompat
                .Builder(this, CHANNEL_ID_LOW)
                .setSound(null)
                .setVibrate(null)
                .setContentTitle(title)
                .setContentText(message)
                .addAction(R.drawable.ic_clear, getString(R.string.notification_stop), pendingStopIntent)
                .setContentIntent(pendingStartActivityIntent)
                .setSmallIcon(R.drawable.ic_notification_icon)
                .build()

        try {
            startForeground(NOTIFICATION_ID, notification)
        } catch (e: IllegalStateException) {
            // Android 15+ throws when the 6h dataSync budget is exhausted, even on starts racing
            // the foreground transition.
            logger.warn(e) { "Failed to promote service to foreground" }
            if (allowRetry) {
                launch {
                    delay(FOREGROUND_RETRY_DELAY)
                    startForeground(allowRetry = false)
                }
            }
        }
    }

    private fun NotificationData.createMentionNotification() {
        val pendingStartActivityIntent =
            Intent(this@NotificationService, MainActivity::class.java).let {
                it.putExtra(MainActivity.OPEN_CHANNEL_KEY, channel)
                PendingIntent.getActivity(this@NotificationService, notificationIntentCode.fetchAndAdd(1), it, pendingIntentFlag)
            }

        val summary =
            NotificationCompat
                .Builder(this@NotificationService, CHANNEL_ID_DEFAULT)
                .setContentTitle(getString(R.string.notification_new_mentions))
                .setContentText("")
                .setSmallIcon(R.drawable.ic_notification_icon)
                .setGroup(MENTION_GROUP)
                .setGroupSummary(true)
                .setAutoCancel(true)
                .build()

        val title =
            when {
                isWhisper -> getString(R.string.notification_whisper_mention, name)
                isNotify -> getString(R.string.notification_notify_mention, channel)
                else -> getString(R.string.notification_mention, name, channel)
            }

        val notification =
            NotificationCompat
                .Builder(this@NotificationService, CHANNEL_ID_DEFAULT)
                .setContentTitle(title)
                .setContentText(message)
                .setContentIntent(pendingStartActivityIntent)
                .setSmallIcon(R.drawable.ic_notification_icon)
                .setGroup(MENTION_GROUP)
                .build()

        val id = notificationId.fetchAndAdd(1)
        notifications.getOrPut(channel) { mutableListOf() } += id

        manager.notify(id, notification)
        manager.notify(SUMMARY_NOTIFICATION_ID, summary)
    }

    companion object {
        private const val CHANNEL_ID_LOW = "com.flxrs.dankchat.dank_id"
        private const val CHANNEL_ID_DEFAULT = "com.flxrs.dankchat.very_dank_id"
        private const val NOTIFICATION_ID = 77777
        private const val NOTIFICATION_START_INTENT_CODE = 66666
        private const val NOTIFICATION_STOP_INTENT_CODE = 55555
        private const val SUMMARY_NOTIFICATION_ID = 12345
        private const val MENTION_GROUP = "dank_group"
        private const val STOP_COMMAND = "STOP_DANKING"

        private const val MAX_NOTIFIED_IDS = 500
        private val FOREGROUND_RETRY_DELAY = 5.seconds

        private val notificationId = AtomicInt(42)
        private val notificationIntentCode = AtomicInt(420)
    }
}

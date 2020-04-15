package com.flxrs.dankchat.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.media.app.NotificationCompat.MediaStyle
import androidx.preference.PreferenceManager
import coil.Coil
import coil.api.load
import com.flxrs.dankchat.MainActivity
import com.flxrs.dankchat.R
import com.flxrs.dankchat.chat.ChatItem
import com.flxrs.dankchat.service.twitch.message.Message
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import org.koin.core.KoinComponent
import org.koin.core.inject
import kotlin.coroutines.CoroutineContext

class TwitchService : Service(), CoroutineScope {

    private val binder = LocalBinder()
    private lateinit var manager: NotificationManager
    private lateinit var sharedPreferences: SharedPreferences
    private val notifications = mutableMapOf<String, MutableList<Int>>()
    var shouldNotifyOnMention = false
    override val coroutineContext: CoroutineContext
        get() = Dispatchers.IO + Job()

    inner class LocalBinder(val service: TwitchService = this@TwitchService) : Binder()

    override fun onBind(intent: Intent?): IBinder? = binder

    override fun onDestroy() {
        coroutineContext.cancel()
        shouldNotifyOnMention = false
        if (::manager.isInitialized) {
            manager.cancelAll()
        }

        stopForeground(true)
        stopSelf()
    }

    override fun onCreate() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            val name = getString(R.string.app_name)
            val channel = NotificationChannel(CHANNEL_ID_LOW, name, NotificationManager.IMPORTANCE_LOW).apply {
                enableVibration(false)
                enableLights(false)
                setShowBadge(false)
            }

            val mentionChannel = NotificationChannel(CHANNEL_ID_DEFAULT, "Mentions", NotificationManager.IMPORTANCE_DEFAULT)
            manager.createNotificationChannel(mentionChannel)
            manager.createNotificationChannel(channel)
        }

        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            STOP_COMMAND -> {
                LocalBroadcastManager.getInstance(this).sendBroadcast(Intent(MainActivity.SHUTDOWN_REQUEST_FILTER))
                stopForeground(true)
                stopSelf()
            }
            else -> startForeground()
        }

        return START_NOT_STICKY
    }

    fun clearNotificationsOfChannel(channel: String) {
        val ids = notifications.remove(channel)
        ids?.forEach { manager.cancel(it) }

        if (notifications.isEmpty()) {
            manager.cancel(SUMMARY_NOTIFICATION_ID)
        }
    }

    private fun startForeground() {
        val title = getString(R.string.notification_title)
        val message = getString(R.string.notification_message)

        val pendingStartActivityIntent = Intent(this, MainActivity::class.java).let {
            PendingIntent.getActivity(this, NOTIFICATION_START_INTENT_CODE, it, PendingIntent.FLAG_UPDATE_CURRENT)
        }

        val pendingStopIntent = Intent(this, TwitchService::class.java).let {
            it.action = STOP_COMMAND
            PendingIntent.getService(this, NOTIFICATION_STOP_INTENT_CODE, it, PendingIntent.FLAG_UPDATE_CURRENT)
        }

        val notification = NotificationCompat.Builder(this, CHANNEL_ID_LOW)
            .setSound(null)
            .setVibrate(null)
            .setContentTitle(title)
            .setContentText(message)
            .addAction(R.drawable.ic_clear_24dp, getString(R.string.notification_stop), pendingStopIntent).apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    setStyle(MediaStyle().setShowActionsInCompactView(0))
                }
            }
            .setContentIntent(pendingStartActivityIntent)
            .setSmallIcon(R.drawable.ic_notification_icon)
            .build()

        startForeground(NOTIFICATION_ID, notification)
    }

    fun checkForNotification(messageChannel: Channel<List<ChatItem>>) = launch {
        val notificationsEnabled = sharedPreferences.getBoolean(getString(R.string.preference_notification_key), true)
        for (items in messageChannel) {
            items.forEach {item ->
                with(item.message as Message.TwitchMessage) {
                    // Preload emotes
                    emotes.forEach { Coil.load(this@TwitchService, it.url) }

                    if (shouldNotifyOnMention && isMention && notificationsEnabled) {
                        createMentionNotification(channel, name, message)
                    }
                }
            }
        }
    }

    private fun createMentionNotification(channel: String, user: String, message: String) {
        val pendingStartActivityIntent = Intent(this, MainActivity::class.java).let {
            it.putExtra(MainActivity.OPEN_CHANNEL_KEY, channel)
            PendingIntent.getActivity(this, notificationIntentCode, it, PendingIntent.FLAG_UPDATE_CURRENT)
        }

        val summary = NotificationCompat.Builder(this, CHANNEL_ID_DEFAULT)
            .setContentTitle(getString(R.string.notification_new_mentions))
            .setContentText("")
            .setSmallIcon(R.drawable.ic_notification_icon)
            .setGroup(MENTION_GROUP)
            .setGroupSummary(true)
            .setAutoCancel(true)
            .build()

        val notification = NotificationCompat.Builder(this, CHANNEL_ID_DEFAULT)
            .setContentTitle(getString(R.string.notification_mention, user, channel))
            .setContentText(message)
            .setContentIntent(pendingStartActivityIntent)
            .setSmallIcon(R.drawable.ic_notification_icon)
            .setGroup(MENTION_GROUP)
            .build()

        val id = notificationId
        notifications.getOrPut(channel) { mutableListOf() }.add(id)

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

        private var notificationId = 42
            get() = field++
        private var notificationIntentCode = 420
            get() = field++
    }
}
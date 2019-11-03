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
import com.flxrs.dankchat.MainActivity
import com.flxrs.dankchat.R
import com.flxrs.dankchat.service.irc.IrcMessage
import com.flxrs.dankchat.service.twitch.connection.WebSocketConnection
import okhttp3.OkHttpClient
import okhttp3.Request
import org.koin.core.KoinComponent
import org.koin.core.get
import org.koin.core.parameter.parametersOf
import java.util.concurrent.TimeUnit

class TwitchService : Service(), KoinComponent {

    private val client = OkHttpClient.Builder()
        .retryOnConnectionFailure(true)
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .writeTimeout(5, TimeUnit.SECONDS)
        .build()

    private val request = Request.Builder()
        .url("wss://irc-ws.chat.twitch.tv")
        .build()

    private val binder = LocalBinder()
    private val repository: TwitchRepository = get()
    private val readConnection: WebSocketConnection = get { parametersOf(client, request, ::onDisconnect, ::onReaderMessage) }
    private val writeConnection: WebSocketConnection = get { parametersOf(client, request, null, ::onWriterMessage) }
    private lateinit var manager: NotificationManager
    private lateinit var sharedPreferences: SharedPreferences
    private var nick = ""

    var shouldNotifyOnMention = false
    var startedConnection = false
        private set

    inner class LocalBinder(val service: TwitchService = this@TwitchService) : Binder()

    override fun onBind(p0: Intent?): IBinder? = binder

    override fun onDestroy() {
        shouldNotifyOnMention = false
        close()
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
            val channel = NotificationChannel(
                CHANNEL_ID_LOW,
                name,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                enableVibration(false)
                enableLights(false)
                setShowBadge(false)
            }

            val mentionChannel = NotificationChannel(
                CHANNEL_ID_DEFAULT,
                "Mentions",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            manager.createNotificationChannel(mentionChannel)
            manager.createNotificationChannel(channel)
        }

        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == STOP_COMMAND) {
            LocalBroadcastManager.getInstance(this)
                .sendBroadcast(Intent(MainActivity.SHUTDOWN_REQUEST_FILTER))
            stopForeground(true)
            stopSelf()
        } else {
            startForeground()
        }

        return START_NOT_STICKY
    }

    fun connect(nick: String, oauth: String) {
        if (!startedConnection) {
            readConnection.connect(nick, oauth)
            writeConnection.connect(nick, oauth)
            startedConnection = true
            this.nick = nick
        }
    }

    fun joinChannel(channel: String) {
        readConnection.joinChannel(channel)
        writeConnection.joinChannel(channel)
    }

    fun partChannel(channel: String) {
        readConnection.partChannel(channel)
        writeConnection.partChannel(channel)
    }

    fun sendMessage(channel: String, input: String) = repository.prepareMessage(channel, input) {
        writeConnection.sendMessage(it)
    }

    fun reconnect(onlyIfNecessary: Boolean) {
        readConnection.reconnect(onlyIfNecessary)
        writeConnection.reconnect(onlyIfNecessary)
    }

    fun close(onClosed: () -> Unit = { }) {
        startedConnection = false
        writeConnection.close(onClosed)
        readConnection.close(onClosed)
    }

    private fun startForeground() {
        val title = getString(R.string.notification_title)
        val message = getString(R.string.notification_message)

        val pendingStartActivityIntent = Intent(this, MainActivity::class.java).let {
            PendingIntent.getActivity(this, 0, it, 0)
        }

        val pendingStopIntent = Intent(this, TwitchService::class.java).let {
            it.action = STOP_COMMAND
            PendingIntent.getService(this, 0, it, 0)
        }

        val notification = NotificationCompat.Builder(this, CHANNEL_ID_LOW)
            .setSound(null)
            .setVibrate(null)
            .setContentTitle(title)
            .setContentText(message)
            .addAction(
                R.drawable.ic_clear_24dp,
                getString(R.string.notification_stop),
                pendingStopIntent
            ).apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    setStyle(MediaStyle().setShowActionsInCompactView(0))
                }
            }
            .setContentIntent(pendingStartActivityIntent)
            .setSmallIcon(R.drawable.ic_notification_icon)
            .build()

        startForeground(NOTIFICATION_ID, notification)
    }

    private fun onMessage(message: IrcMessage) {
        val messages = repository.onMessage(message)
        if (shouldNotifyOnMention) {
            messages?.filter { it.message.isMention }
                ?.takeIf {
                    sharedPreferences.getBoolean(getString(R.string.preference_notification_key), true)
                }?.map {
                    createMentionNotification(it.message.channel, it.message.name, it.message.message)
                }
        }
    }

    private fun onWriterMessage(message: IrcMessage) {
        when (message.command) {
            "PRIVMSG" -> Unit
            "366"     -> onConnect(message.params[1].substring(1), writeConnection.isAnonymous)
            else      -> onMessage(message)
        }
    }

    private fun onReaderMessage(message: IrcMessage) {
        if (message.command == "PRIVMSG") {
            onMessage(message)
        }
    }

    private fun createMentionNotification(channel: String, user: String, message: String) {
        val pendingStartActivityIntent = Intent(this, MainActivity::class.java).let {
            PendingIntent.getActivity(this, 0, it, 0)
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
            .setAutoCancel(true)
            .setGroup(MENTION_GROUP)
            .build()

        manager.notify(notificationId, notification)
        manager.notify(SUMMARY_NOTIFICATION_ID, summary)
    }

    private fun onDisconnect() = repository.handleDisconnect(getString(R.string.system_message_disconnected))

    private fun onConnect(channel: String, isAnonymous: Boolean) = repository.handleConnected(channel, isAnonymous, getString(R.string.system_message_connected))

    companion object {
        private const val CHANNEL_ID_LOW = "com.flxrs.dankchat.dank_id"
        private const val CHANNEL_ID_DEFAULT = "com.flxrs.dankchat.very_dank_id"
        private const val NOTIFICATION_ID = 77777
        private const val SUMMARY_NOTIFICATION_ID = 12345
        private const val MENTION_GROUP = "dank_group"
        private const val STOP_COMMAND = "STOP_DANKING"

        private var notificationId = 42
            get() = field++
    }
}
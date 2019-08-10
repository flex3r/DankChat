package com.flxrs.dankchat.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.media.app.NotificationCompat.MediaStyle
import com.flxrs.dankchat.MainActivity
import com.flxrs.dankchat.R
import com.flxrs.dankchat.service.irc.IrcMessage
import com.flxrs.dankchat.service.twitch.connection.WebSocketConnection
import org.koin.core.KoinComponent
import org.koin.core.get
import org.koin.core.parameter.parametersOf

class TwitchService : Service(), KoinComponent {

    private val binder = LocalBinder()
    private val repository: TwitchRepository = get()
    private val connection: WebSocketConnection = get { parametersOf(::onDisconnect, ::onMessage) }
    private var startedConnection = false
    private var changingConfiguration = false

    override fun onBind(p0: Intent?): IBinder? {
        stopForeground(true)
        changingConfiguration = false
        return binder
    }

    override fun onRebind(intent: Intent?) {
        stopForeground(true)
        changingConfiguration = false

        super.onRebind(intent)
    }

    override fun onUnbind(intent: Intent?): Boolean {
        if (!changingConfiguration) {
            val title = getString(R.string.notification_title)
            val message = getString(R.string.notification_message)

            val pendingStartActivityIntent = Intent(this, MainActivity::class.java).let {
                PendingIntent.getActivity(this, 0, it, 0)
            }

            val pendingStopIntent = Intent(this, TwitchService::class.java).apply { action = STOP_COMMAND }.let {
                PendingIntent.getService(this, 0, it, 0)
            }

            val notification = NotificationCompat.Builder(this, CHANNEL_ID)
                .setSound(null)
                .setVibrate(null)
                .setContentTitle(title)
                .setContentText(message)
                .addAction(R.drawable.ic_clear_24dp, getString(R.string.notification_stop), pendingStopIntent)
                .setStyle(MediaStyle().setShowActionsInCompactView(0))
                .setContentIntent(pendingStartActivityIntent)
                .setSmallIcon(R.mipmap.ic_launcher_foreground)
                .build()

            startForeground(NOTIFICATION_ID, notification)
        }

        return true
    }

    inner class LocalBinder(val service: TwitchService = this@TwitchService) : Binder()

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        changingConfiguration = true
    }

    override fun onCreate() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val name = getString(R.string.app_name)
            val channel = NotificationChannel(CHANNEL_ID, name, NotificationManager.IMPORTANCE_LOW).apply {
                enableVibration(false)
                enableLights(false)
                setShowBadge(false)
            }

            manager.createNotificationChannel(channel)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == STOP_COMMAND) {
            stopForeground(true)
            stopSelf()
        }

        return START_NOT_STICKY
    }

    fun connect(nick: String, oauth: String) {
        if (!startedConnection) connection.connect(nick, oauth)
    }

    fun joinChannel(channel: String) = connection.joinChannel(channel)

    fun partChannel(channel: String) = connection.partChannel(channel)

    fun sendMessage(channel: String, input: String) = repository.sendMessage(channel, input) {
        connection.sendMessage(it)
    }

    @Synchronized
    fun reconnect(onlyIfNecessary: Boolean) {
        connection.reconnect(onlyIfNecessary)
    }

    @Synchronized
    fun close(onClosed: () -> Unit) {
        connection.close(onClosed)
    }

    private fun onMessage(message: IrcMessage) = repository.onMessage(message, connection.isJustinFan)

    private fun onDisconnect() = repository.handleDisconnect()


    companion object {
        private const val CHANNEL_ID = "com.fxlrs.dankchat.dank_id"
        private const val NOTIFICATION_ID = 77777
        private const val STOP_COMMAND = "STOP_DANKING"
    }
}
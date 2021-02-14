package com.flxrs.dankchat.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.media.AudioManager
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.speech.tts.TextToSpeech
import androidx.core.app.NotificationCompat
import androidx.core.content.edit
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.media.app.NotificationCompat.MediaStyle
import androidx.preference.PreferenceManager
import com.flxrs.dankchat.MainActivity
import com.flxrs.dankchat.R
import com.flxrs.dankchat.service.twitch.message.TwitchMessage
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect
import java.util.*
import javax.inject.Inject
import kotlin.coroutines.CoroutineContext

@AndroidEntryPoint
class NotificationService : Service(), CoroutineScope {

    private val binder = LocalBinder()
    private val manager: NotificationManager by lazy { getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager }
    private val sharedPreferences by lazy { PreferenceManager.getDefaultSharedPreferences(this) }
    private val preferenceListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        when (key) {
            getString(R.string.preference_notification_key) -> notificationsEnabled = sharedPreferences.getBoolean(key, true)
            getString(R.string.preference_tts_queue_key) -> ttsMessageQueue = sharedPreferences.getBoolean(key, true)
            getString(R.string.preference_tts_message_format_key) -> combinedTTSFormat = sharedPreferences.getBoolean(key, true)
        }
    }

    private var notificationsEnabled = false
    private var combinedTTSFormat = false
    private var ttsMessageQueue = false

    private var notificationsJob: Job? = null

    private val notifications = mutableMapOf<String, MutableList<Int>>()

    @Inject
    lateinit var chatRepository: ChatRepository

    private var tts: TextToSpeech? = null
    private var audioManager: AudioManager? = null
    private var previousTTSUser: String? = null

    var activeTTSChannel: String? = null
    var shouldNotifyOnMention = false
    override val coroutineContext: CoroutineContext
        get() = Dispatchers.IO + Job()

    inner class LocalBinder(val service: NotificationService = this@NotificationService) : Binder()

    override fun onBind(intent: Intent?): IBinder? = binder

    override fun onDestroy() {
        cancel()
        manager.cancelAll()
        shutdownTTS()
        sharedPreferences.unregisterOnSharedPreferenceChangeListener(preferenceListener)

        stopForeground(true)
        stopSelf()
        super.onDestroy()
    }

    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

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

        sharedPreferences.apply {
            notificationsEnabled = sharedPreferences.getBoolean(getString(R.string.preference_notification_key), true)
            ttsMessageQueue = sharedPreferences.getBoolean(getString(R.string.preference_tts_queue_key), true)
            combinedTTSFormat = sharedPreferences.getBoolean(getString(R.string.preference_tts_message_format_key), true)
            registerOnSharedPreferenceChangeListener(preferenceListener)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            STOP_COMMAND -> LocalBroadcastManager.getInstance(this).sendBroadcast(Intent(MainActivity.SHUTDOWN_REQUEST_FILTER))
            else -> startForeground()
        }

        return START_NOT_STICKY
    }

    fun clearNotificationsOfChannel(channel: String) {
        val ids = notifications.remove(channel)
        ids?.forEach { manager.cancel(it) }

        if (notifications.isEmpty()) {
            manager.cancel(SUMMARY_NOTIFICATION_ID)
            manager.cancelAll()
        }
    }

    fun setTTSEnabled(enabled: Boolean) = when {
        enabled -> initTTS()
        else -> shutdownTTS()
    }

    private fun initTTS() {
        tts = TextToSpeech(this) { status ->
            when (status) {
                TextToSpeech.SUCCESS -> tts?.language = Locale.US
                else -> {
                    shutdownTTS()
                    sharedPreferences.edit { putBoolean(getString(R.string.preference_tts_key), false) }
                }
            }
        }
        audioManager = getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    }

    private fun shutdownTTS() {
        tts?.shutdown()
        tts = null
        previousTTSUser = null
        audioManager = null
    }

    private fun startForeground() {
        val title = getString(R.string.notification_title)
        val message = getString(R.string.notification_message)

        val pendingStartActivityIntent = Intent(this, MainActivity::class.java).let {
            PendingIntent.getActivity(this, NOTIFICATION_START_INTENT_CODE, it, PendingIntent.FLAG_UPDATE_CURRENT)
        }

        val pendingStopIntent = Intent(this, NotificationService::class.java).let {
            it.action = STOP_COMMAND
            PendingIntent.getService(this, NOTIFICATION_STOP_INTENT_CODE, it, PendingIntent.FLAG_UPDATE_CURRENT)
        }

        val notification = NotificationCompat.Builder(this, CHANNEL_ID_LOW)
            .setSound(null)
            .setVibrate(null)
            .setContentTitle(title)
            .setContentText(message)
            .addAction(R.drawable.ic_clear, getString(R.string.notification_stop), pendingStopIntent).apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    setStyle(MediaStyle().setShowActionsInCompactView(0))
                }
            }
            .setContentIntent(pendingStartActivityIntent)
            .setSmallIcon(R.drawable.ic_notification_icon)
            .build()

        startForeground(NOTIFICATION_ID, notification)
    }

    fun checkForNotification() {
        shouldNotifyOnMention = false

        notificationsJob?.cancel()
        notificationsJob = launch {
            chatRepository.notificationsFlow.collect { items ->
                items.forEach { (message) ->
                    with(message as TwitchMessage) {
                        if (shouldNotifyOnMention && isMention && notificationsEnabled) {
                            createMentionNotification()
                        }

                        if (tts != null && channel == activeTTSChannel && compareValues(audioManager?.getStreamVolume(AudioManager.STREAM_MUSIC), 0) > 0) {
                            playTTSMessage()
                        }
                    }
                }
            }
        }
    }

    private fun TwitchMessage.playTTSMessage() {
        val messageFormat = when {
            isSystem || !combinedTTSFormat -> message
            else -> "$name said $message"
        }
        val queueMode = when {
            ttsMessageQueue -> TextToSpeech.QUEUE_ADD
            else -> TextToSpeech.QUEUE_FLUSH
        }
        val ttsMessage = when (name) {
            previousTTSUser -> message
            else -> messageFormat.also { previousTTSUser = name }
        }
        tts?.speak(ttsMessage, queueMode, null, null)
    }

    private fun TwitchMessage.createMentionNotification() {
        val pendingStartActivityIntent = Intent(this@NotificationService, MainActivity::class.java).let {
            it.putExtra(MainActivity.OPEN_CHANNEL_KEY, channel)
            PendingIntent.getActivity(this@NotificationService, notificationIntentCode, it, PendingIntent.FLAG_UPDATE_CURRENT)
        }

        val summary = NotificationCompat.Builder(this@NotificationService, CHANNEL_ID_DEFAULT)
            .setContentTitle(getString(R.string.notification_new_mentions))
            .setContentText("")
            .setSmallIcon(R.drawable.ic_notification_icon)
            .setGroup(MENTION_GROUP)
            .setGroupSummary(true)
            .setAutoCancel(true)
            .build()

        val title = when {
            isWhisper -> getString(R.string.notification_whisper_mention, name)
            isNotify -> getString(R.string.notification_notify_mention, channel)
            else -> getString(R.string.notification_mention, name, channel)
        }

        val notification = NotificationCompat.Builder(this@NotificationService, CHANNEL_ID_DEFAULT)
            .setContentTitle(title)
            .setContentText(message)
            .setContentIntent(pendingStartActivityIntent)
            .setSmallIcon(R.drawable.ic_notification_icon)
            .setGroup(MENTION_GROUP)
            .build()

        val id = notificationId
        notifications.getOrPut(channel) { mutableListOf() } += id

        manager.notify(id, notification)
        manager.notify(SUMMARY_NOTIFICATION_ID, summary)
    }

    companion object {
        private val TAG = NotificationService::class.simpleName

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
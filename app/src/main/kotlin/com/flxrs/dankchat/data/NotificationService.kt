package com.flxrs.dankchat.data

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
import androidx.core.app.ServiceCompat
import androidx.core.content.edit
import androidx.core.content.getSystemService
import androidx.media.app.NotificationCompat.MediaStyle
import androidx.preference.PreferenceManager
import com.flxrs.dankchat.R
import com.flxrs.dankchat.data.NotificationService.NotificationData.Companion.toNotificationData
import com.flxrs.dankchat.data.twitch.message.Message
import com.flxrs.dankchat.data.twitch.message.TwitchMessage
import com.flxrs.dankchat.data.twitch.message.WhisperMessage
import com.flxrs.dankchat.main.MainActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.forEach
import java.util.*
import java.util.regex.Matcher
import java.util.regex.Pattern
import javax.inject.Inject
import kotlin.coroutines.CoroutineContext

@AndroidEntryPoint
class NotificationService : Service(), CoroutineScope {

    private val binder = LocalBinder()
    private val manager: NotificationManager by lazy { getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager }
    private val sharedPreferences by lazy { PreferenceManager.getDefaultSharedPreferences(this) }
    private val preferenceListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        when (key) {
            getString(R.string.preference_notification_key)             -> notificationsEnabled = sharedPreferences.getBoolean(key, true)
            getString(R.string.preference_tts_queue_key)                -> ttsMessageQueue = sharedPreferences.getBoolean(key, true)
            getString(R.string.preference_tts_message_format_key)       -> combinedTTSFormat = sharedPreferences.getBoolean(key, true)
            getString(R.string.preference_tts_key)                      -> ttsEnabled = sharedPreferences.getBoolean(key, false).also { setTTSEnabled(it) }
            getString(R.string.preference_tts_message_filter_url_key)   -> removeURL = sharedPreferences.getBoolean(key, false)
            getString(R.string.preference_tts_message_filter_emote_key) -> removeEmote = sharedPreferences.getBoolean(key, false)
            getString(R.string.preference_tts_user_ignore_list_key)     -> ignoredTtsUsers = sharedPreferences.getStringSet(key, emptySet()).orEmpty()
            getString(R.string.preference_tts_force_english_key)        -> {
                forceEnglishTTS = sharedPreferences.getBoolean(key, false)
                setTTSVoice()
            }
        }
    }

    private var notificationsEnabled = false
    private var ttsEnabled = false
    private var combinedTTSFormat = false
    private var ttsMessageQueue = false
    private var forceEnglishTTS = false
    private var removeURL = false
    private var removeEmote = false
    private var ignoredTtsUsers = emptySet<String>()

    private var notificationsJob: Job? = null
    private val notifications = mutableMapOf<String, MutableList<Int>>()

    @Inject
    lateinit var chatRepository: ChatRepository

    @Inject
    lateinit var dataRepository: DataRepository

    private var tts: TextToSpeech? = null
    private var audioManager: AudioManager? = null
    private var previousTTSUser: String? = null

    private val pendingIntentFlag: Int = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.M -> PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        else                                           -> PendingIntent.FLAG_UPDATE_CURRENT
    }

    var activeTTSChannel: String? = null
    var shouldNotifyOnMention = false

    override val coroutineContext: CoroutineContext
        get() = Dispatchers.IO + Job()

    inner class LocalBinder(val service: NotificationService = this@NotificationService) : Binder()

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onDestroy() {
        coroutineContext.cancelChildren()
        manager.cancelAll()
        shutdownTTS()
        sharedPreferences.unregisterOnSharedPreferenceChangeListener(preferenceListener)

        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
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
            forceEnglishTTS = sharedPreferences.getBoolean(getString(R.string.preference_tts_force_english_key), false)
            ttsEnabled = sharedPreferences.getBoolean(getString(R.string.preference_tts_key), false).also { setTTSEnabled(it) }
            removeURL = sharedPreferences.getBoolean(getString(R.string.preference_tts_message_filter_url_key), false)
            removeEmote = sharedPreferences.getBoolean(getString(R.string.preference_tts_message_filter_emote_key), false)
            ignoredTtsUsers = sharedPreferences.getStringSet(getString(R.string.preference_tts_user_ignore_list_key), emptySet()).orEmpty()
            registerOnSharedPreferenceChangeListener(preferenceListener)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            STOP_COMMAND -> launch { dataRepository.sendShutdownCommand() }
            else         -> startForeground()
        }

        return START_NOT_STICKY
    }

    fun setActiveChannel(channel: String) {
        activeTTSChannel = channel
        val ids = notifications.remove(channel)
        ids?.forEach { manager.cancel(it) }

        if (notifications.isEmpty()) {
            manager.cancel(SUMMARY_NOTIFICATION_ID)
            manager.cancelAll()
        }
    }

    private fun setTTSEnabled(enabled: Boolean) = when {
        enabled -> initTTS()
        else    -> shutdownTTS()
    }

    private fun initTTS() {
        audioManager = getSystemService()
        tts = TextToSpeech(this) { status ->
            when (status) {
                TextToSpeech.SUCCESS -> setTTSVoice()
                else                 -> shutdownAndDisableTTS()
            }
        }
    }

    private fun setTTSVoice() {
        val voice = when {
            forceEnglishTTS -> tts?.voices?.find { it.locale == Locale.US && !it.isNetworkConnectionRequired }
            else            -> tts?.defaultVoice
        }

        voice?.takeUnless { tts?.setVoice(it) == TextToSpeech.ERROR } ?: shutdownAndDisableTTS()
    }

    private fun shutdownAndDisableTTS() {
        shutdownTTS()
        sharedPreferences.edit { putBoolean(getString(R.string.preference_tts_key), false) }
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
            PendingIntent.getActivity(this, NOTIFICATION_START_INTENT_CODE, it, pendingIntentFlag)
        }

        val pendingStopIntent = Intent(this, NotificationService::class.java).let {
            it.action = STOP_COMMAND
            PendingIntent.getService(this, NOTIFICATION_STOP_INTENT_CODE, it, pendingIntentFlag)
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
                    if (shouldNotifyOnMention && notificationsEnabled) {
                        val data = message.toNotificationData()
                        data?.createMentionNotification()
                    }

                    if (message !is TwitchMessage) {
                        return@forEach
                    }

                    val volume = audioManager?.getStreamVolume(AudioManager.STREAM_MUSIC) ?: 0

                    if (!ttsEnabled || message.channel != activeTTSChannel || volume <= 0) {
                        return@forEach
                    }

                    if (tts == null) {
                        initTTS()
                    }

                    if (ignoredTtsUsers.any { it.equals(message.name, ignoreCase = true) || it.equals(message.displayName, ignoreCase = true) }) {
                        return@forEach
                    }

                    message.playTTSMessage()
                }
            }
        }
    }

    private fun TwitchMessage.playTTSMessage() {
        val messageFormat = when {
            isSystem || !combinedTTSFormat                          -> message
            tts?.voice?.locale?.language == Locale.ENGLISH.language -> "$name said $message"
            else                                                    -> "$name. $message"
        }
        val queueMode = when {
            ttsMessageQueue -> TextToSpeech.QUEUE_ADD
            else            -> TextToSpeech.QUEUE_FLUSH
        }
        var ttsMessage = when (name) {
            previousTTSUser -> message
            else            -> messageFormat.also { previousTTSUser = name }
        }
        if (removeURL) {
            ttsMessage = ttsMessage.replace("[(http(s)?):\\/\\/(www\\.)?a-zA-Z0-9@:%._\\+~#=]{2,256}\\.[a-z]{2,6}\\b([-a-zA-Z0-9@:%_\\+.~#?&//=]*)".toRegex(RegexOption.IGNORE_CASE), "")
        }
        if (removeEmote) {
            for (emote in this.emotes) {
                ttsMessage = ttsMessage.replace(emote.code, "", ignoreCase = true)
            }
            ttsMessage = ttsMessage.replace(Regex("(\\u00a9|\\u00ae|[\\u2000-\\u3300]|\\ud83c[\\ud000-\\udfff]|\\ud83d[\\ud000-\\udfff]|\\ud83e[\\ud000-\\udfff])"), "")
        }
        tts?.speak(ttsMessage, queueMode, null, null)
    }


    private data class NotificationData(
        val channel: String,
        val name: String,
        val message: String,
        val isWhisper: Boolean = false,
        val isNotify: Boolean = false,
    ) {
        companion object {
            fun Message.toNotificationData(): NotificationData? = when (this) {
                is TwitchMessage  -> if (isMention) NotificationData(channel, name, originalMessage, isNotify = isNotify) else null
                is WhisperMessage -> NotificationData(
                    channel = "",
                    name = name,
                    message = originalMessage,
                    isWhisper = true,
                )
                else              -> null
            }
        }
    }

    private fun NotificationData.createMentionNotification() {
        val pendingStartActivityIntent = Intent(this@NotificationService, MainActivity::class.java).let {
            it.putExtra(MainActivity.OPEN_CHANNEL_KEY, channel)
            PendingIntent.getActivity(this@NotificationService, notificationIntentCode, it, pendingIntentFlag)
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
            isNotify  -> getString(R.string.notification_notify_mention, channel)
            else      -> getString(R.string.notification_mention, name, channel)
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
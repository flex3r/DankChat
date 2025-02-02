package com.flxrs.dankchat.data.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.media.AudioManager
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.speech.tts.TextToSpeech
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.getSystemService
import androidx.media.app.NotificationCompat.MediaStyle
import com.flxrs.dankchat.R
import com.flxrs.dankchat.data.UserName
import com.flxrs.dankchat.data.repo.chat.ChatRepository
import com.flxrs.dankchat.data.repo.data.DataRepository
import com.flxrs.dankchat.data.twitch.emote.ChatMessageEmote
import com.flxrs.dankchat.data.twitch.message.Message
import com.flxrs.dankchat.data.twitch.message.NoticeMessage
import com.flxrs.dankchat.data.twitch.message.PrivMessage
import com.flxrs.dankchat.data.twitch.message.UserNoticeMessage
import com.flxrs.dankchat.main.MainActivity
import com.flxrs.dankchat.preferences.notifications.NotificationsSettingsDataStore
import com.flxrs.dankchat.preferences.tools.TTSMessageFormat
import com.flxrs.dankchat.preferences.tools.TTSPlayMode
import com.flxrs.dankchat.preferences.tools.ToolsSettings
import com.flxrs.dankchat.preferences.tools.ToolsSettingsDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import java.util.Locale
import kotlin.coroutines.CoroutineContext

class NotificationService : Service(), CoroutineScope {

    private val binder = LocalBinder()
    private val manager: NotificationManager by lazy { getSystemService(NOTIFICATION_SERVICE) as NotificationManager }

    private var notificationsEnabled = false
    private var toolSettings = ToolsSettings()

    private var notificationsJob: Job? = null
    private val notifications = mutableMapOf<UserName, MutableList<Int>>()

    private val chatRepository: ChatRepository by inject()
    private val dataRepository: DataRepository by inject()
    private val toolsSettingsDataStore: ToolsSettingsDataStore by inject()
    private val notificationsSettingsDataStore: NotificationsSettingsDataStore by inject()

    private var tts: TextToSpeech? = null
    private var audioManager: AudioManager? = null
    private var previousTTSUser: UserName? = null

    private val pendingIntentFlag: Int = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.M -> PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        else                                           -> PendingIntent.FLAG_UPDATE_CURRENT
    }

    private var activeTTSChannel: UserName? = null
    private var shouldNotifyOnMention = false

    override val coroutineContext: CoroutineContext
        get() = Dispatchers.IO + Job()

    inner class LocalBinder(val service: NotificationService = this@NotificationService) : Binder()

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onDestroy() {
        coroutineContext.cancelChildren()
        manager.cancelAll()
        shutdownTTS()

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


        notificationsSettingsDataStore.settings
            .onEach { notificationsEnabled = it.showNotifications }
            .launchIn(this)
        toolsSettingsDataStore.ttsEnabled
            .onEach { setTTSEnabled(enabled = it) }
            .launchIn(this)
        toolsSettingsDataStore.ttsForceEnglishChanged
            .onEach { setTTSVoice(forceEnglish = it) }
            .launchIn(this)
        toolsSettingsDataStore.settings
            .onEach { toolSettings = it }
            .launchIn(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            STOP_COMMAND -> launch { dataRepository.sendShutdownCommand() }
            else         -> startForeground()
        }

        return START_NOT_STICKY
    }

    fun setActiveChannel(channel: UserName) {
        activeTTSChannel = channel
        val ids = notifications.remove(channel)
        ids?.forEach { manager.cancel(it) }

        if (notifications.isEmpty()) {
            manager.cancel(SUMMARY_NOTIFICATION_ID)
            manager.cancelAll()
        }
    }

    fun enableNotifications() {
        shouldNotifyOnMention = true
    }

    private fun setTTSEnabled(enabled: Boolean) = when {
        enabled -> initTTS()
        else    -> shutdownTTS()
    }

    private fun initTTS() {
        audioManager = getSystemService()
        tts = TextToSpeech(this) { status ->
            when (status) {
                TextToSpeech.SUCCESS -> setTTSVoice(forceEnglish = toolsSettingsDataStore.current().ttsForceEnglish)
                else                 -> shutdownAndDisableTTS()
            }
        }
    }

    private fun setTTSVoice(forceEnglish: Boolean) {
        val voice = when {
            forceEnglish -> tts?.voices?.find { it.locale == Locale.US && !it.isNetworkConnectionRequired }
            else         -> tts?.defaultVoice
        }

        voice?.takeUnless { tts?.setVoice(it) == TextToSpeech.ERROR } ?: shutdownAndDisableTTS()
    }

    private fun shutdownAndDisableTTS() {
        shutdownTTS()
        launch {
            toolsSettingsDataStore.update { it.copy(ttsEnabled = false) }
        }
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

                    if (!message.shouldPlayTTS()) {
                        return@forEach
                    }

                    val channel = when (message) {
                        is PrivMessage       -> message.channel
                        is UserNoticeMessage -> message.channel
                        is NoticeMessage     -> message.channel
                        else                 -> return@forEach
                    }

                    if (!toolSettings.ttsEnabled || channel != activeTTSChannel || (audioManager?.getStreamVolume(AudioManager.STREAM_MUSIC) ?: 0) <= 0) {
                        return@forEach
                    }

                    if (tts == null) {
                        initTTS()
                    }

                    if (message is PrivMessage && toolSettings.ttsUserNameIgnores.any { it.matches(message.name) || it.matches(message.displayName) }) {
                        return@forEach
                    }

                    message.playTTSMessage()
                }
            }
        }
    }

    private fun Message.shouldPlayTTS(): Boolean = this is PrivMessage || this is NoticeMessage || this is UserNoticeMessage

    private fun Message.playTTSMessage() {
        val message = when (this) {
            is UserNoticeMessage -> message
            is NoticeMessage     -> message
            else                 -> {
                if (this !is PrivMessage) return
                val filtered = message
                    .filterEmotes(emotes)
                    .filterUnicodeSymbols()
                    .filterUrls()

                if (filtered.isBlank()) {
                    return
                }

                when {
                    toolSettings.ttsMessageFormat == TTSMessageFormat.Message || name == previousTTSUser -> filtered
                    tts?.voice?.locale?.language == Locale.ENGLISH.language                              -> "$name said $filtered"
                    else                                                                                 -> "$name. $filtered"
                }.also { previousTTSUser = name }
            }
        }

        val queueMode = when (toolSettings.ttsPlayMode) {
            TTSPlayMode.Queue  -> TextToSpeech.QUEUE_ADD
            TTSPlayMode.Newest -> TextToSpeech.QUEUE_FLUSH
        }
        tts?.speak(message, queueMode, null, null)
    }

    private fun String.filterEmotes(emotes: List<ChatMessageEmote>): String = when {
        toolSettings.ttsIgnoreEmotes -> emotes.fold(this) { acc, emote ->
            acc.replace(emote.code, newValue = "", ignoreCase = true)
        }

        else                         -> this
    }

    private fun String.filterUnicodeSymbols(): String = when {
        // Replaces all unicode character that are: So - Symbol Other, Sc - Symbol Currency, Sm - Symbol Math, Cn - Unassigned.
        // This will not filter out non latin script (Arabic and Japanese for example works fine.)
        toolSettings.ttsIgnoreEmotes -> replace(UNICODE_SYMBOL_REGEX, replacement = "")
        else                         -> this
    }

    private fun String.filterUrls(): String = when {
        toolSettings.ttsIgnoreUrls -> replace(URL_REGEX, replacement = "")
        else                       -> this
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

        private val UNICODE_SYMBOL_REGEX = "\\p{So}|\\p{Sc}|\\p{Sm}|\\p{Cn}".toRegex()
        private val URL_REGEX = "[(http(s)?):\\/\\/(www\\.)?a-zA-Z0-9@:%._\\+~#=]{2,256}\\.[a-z]{2,6}\\b([-a-zA-Z0-9@:%_\\+.~#?&//=]*)".toRegex(RegexOption.IGNORE_CASE)

        private var notificationId = 42
            get() = field++
        private var notificationIntentCode = 420
            get() = field++
    }
}

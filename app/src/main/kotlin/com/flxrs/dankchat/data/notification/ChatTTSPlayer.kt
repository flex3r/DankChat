package com.flxrs.dankchat.data.notification

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import androidx.core.content.getSystemService
import com.flxrs.dankchat.data.UserName
import com.flxrs.dankchat.data.repo.chat.ChatChannelProvider
import com.flxrs.dankchat.data.repo.chat.ChatNotificationRepository
import com.flxrs.dankchat.data.twitch.message.Message
import com.flxrs.dankchat.data.twitch.message.NoticeMessage
import com.flxrs.dankchat.data.twitch.message.PrivMessage
import com.flxrs.dankchat.data.twitch.message.UserNoticeMessage
import com.flxrs.dankchat.di.DispatchersProvider
import com.flxrs.dankchat.preferences.tools.TTSMessageFormat
import com.flxrs.dankchat.preferences.tools.TTSPlayMode
import com.flxrs.dankchat.preferences.tools.ToolsSettings
import com.flxrs.dankchat.preferences.tools.ToolsSettingsDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.core.annotation.Single
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger

@Single
class ChatTTSPlayer(
    private val context: Context,
    private val chatNotificationRepository: ChatNotificationRepository,
    private val chatChannelProvider: ChatChannelProvider,
    private val toolsSettingsDataStore: ToolsSettingsDataStore,
    dispatchersProvider: DispatchersProvider,
) {
    // Single parallelism serializes the settings and message collectors around the shared engine state
    private val scope = CoroutineScope(dispatchersProvider.io.limitedParallelism(1) + SupervisorJob())

    private var tts: TextToSpeech? = null
    private var audioManager: AudioManager? = null
    private var previousTTSUser: UserName? = null
    private var audioFocusRequest: AudioFocusRequest? = null
    private var utteranceId = 0
    private var collectJob: Job? = null
    private val pendingUtterances = AtomicInteger(0)

    private val audioAttributes: AudioAttributes = AudioAttributes
        .Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()

    fun start() {
        // Every Activity onCreate calls this, keep a single collector across recreations
        if (collectJob?.isActive == true) {
            return
        }

        collectJob = scope.launch {
            launch {
                toolsSettingsDataStore.settings.collect(::ensureTTSState)
            }
            // Message updates are events, so read settings at processing time instead of combining and conflating them
            chatNotificationRepository.messageUpdates.collect { items ->
                val settings = toolsSettingsDataStore.current()
                val activeChannel = chatChannelProvider.activeChannel.value
                ensureTTSState(settings)
                items.forEach { (message) ->
                    processMessage(message, settings, activeChannel)
                }
            }
        }
    }

    private fun ensureTTSState(settings: ToolsSettings) {
        when {
            settings.ttsEnabled && tts == null -> {
                audioManager = context.getSystemService()
                tts = TextToSpeech(context) { status ->
                    when (status) {
                        TextToSpeech.SUCCESS -> {
                            applyVoice(settings.ttsForceEnglish)
                            tts?.setAudioAttributes(audioAttributes)
                            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                                override fun onStart(utteranceId: String?) = Unit

                                override fun onDone(utteranceId: String?) = onUtteranceFinished()

                                @Suppress("OVERRIDE_DEPRECATION")
                                override fun onError(utteranceId: String?) = onUtteranceFinished()

                                override fun onError(
                                    utteranceId: String?,
                                    errorCode: Int,
                                ) = onUtteranceFinished()

                                override fun onStop(
                                    utteranceId: String?,
                                    interrupted: Boolean,
                                ) = onUtteranceFinished()
                            })
                        }

                        else -> {
                            shutdownTTS()
                            scope.launch {
                                toolsSettingsDataStore.update { it.copy(ttsEnabled = false) }
                            }
                        }
                    }
                }
            }

            !settings.ttsEnabled && tts != null -> {
                shutdownTTS()
            }
        }
    }

    private fun applyVoice(forceEnglish: Boolean) {
        val voice = when {
            forceEnglish -> findEnglishVoice()
            else -> tts?.defaultVoice
        }

        if (voice != null && tts?.setVoice(voice) != TextToSpeech.ERROR) {
            return
        }

        // The engine may not support the system language, fall back to English
        val fallbackVoice = when {
            forceEnglish -> null
            else -> findEnglishVoice()
        }
        if (fallbackVoice != null && tts?.setVoice(fallbackVoice) != TextToSpeech.ERROR) {
            scope.launch {
                toolsSettingsDataStore.update { it.copy(ttsForceEnglish = true) }
            }
            return
        }

        shutdownTTS()
        scope.launch {
            toolsSettingsDataStore.update { it.copy(ttsEnabled = false) }
        }
    }

    private fun findEnglishVoice(): Voice? = tts?.voices?.find { it.locale == Locale.US && !it.isNetworkConnectionRequired }

    private fun shutdownTTS() {
        pendingUtterances.set(0)
        abandonAudioFocus()
        tts?.shutdown()
        tts = null
        previousTTSUser = null
        audioManager = null
    }

    // isSpeaking can lag behind the engine, so track pending utterances instead of querying it
    private fun onUtteranceFinished() {
        if (pendingUtterances.updateAndGet { (it - 1).coerceAtLeast(0) } == 0) {
            abandonAudioFocus()
        }
    }

    private fun processMessage(
        message: Message,
        settings: ToolsSettings,
        activeChannel: UserName?,
    ) {
        val channel = when (message) {
            is PrivMessage -> message.channel
            is UserNoticeMessage -> message.channel
            is NoticeMessage -> message.channel
            else -> return
        }

        if (!settings.ttsEnabled || tts == null || channel != activeChannel) {
            return
        }

        if ((audioManager?.getStreamVolume(AudioManager.STREAM_MUSIC) ?: 0) <= 0) {
            return
        }

        if (message is PrivMessage && settings.ttsUserNameIgnores.any { it.matches(message.name) || it.matches(message.displayName) }) {
            return
        }

        if (message is PrivMessage && settings.ttsIgnoreCommands && message.message.firstOrNull() in TTS_COMMAND_PREFIXES) {
            return
        }

        playTTSMessage(message, settings)
    }

    private fun requestAudioFocus() {
        val manager = audioManager ?: return
        if (audioFocusRequest != null) return

        val request = AudioFocusRequest
            .Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            .setAudioAttributes(audioAttributes)
            .build()

        if (manager.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            audioFocusRequest = request
        }
    }

    private fun abandonAudioFocus() {
        val request = audioFocusRequest ?: return
        audioManager?.abandonAudioFocusRequest(request)
        audioFocusRequest = null
    }

    private fun playTTSMessage(
        message: Message,
        settings: ToolsSettings,
    ) {
        val text = when (message) {
            is UserNoticeMessage -> {
                message.message
            }

            is NoticeMessage -> {
                message.message
            }

            is PrivMessage -> {
                val filtered = message.message
                    .let { text ->
                        when {
                            settings.ttsIgnoreEmotes -> message.emotes.fold(text) { acc, emote -> acc.replace(emote.code, newValue = "", ignoreCase = true) }
                            else -> text
                        }
                    }.let { text ->
                        when {
                            settings.ttsIgnoreEmotes -> text.replace(UNICODE_SYMBOL_REGEX, replacement = "")
                            else -> text
                        }
                    }.let { text ->
                        when {
                            settings.ttsIgnoreUrls -> text.replace(URL_REGEX, replacement = "")
                            else -> text
                        }
                    }

                if (filtered.isBlank()) return

                when {
                    settings.ttsMessageFormat == TTSMessageFormat.Message || message.name == previousTTSUser -> filtered
                    tts?.voice?.locale?.language == Locale.ENGLISH.language -> "${message.name} said $filtered"
                    else -> "${message.name}. $filtered"
                }.also { previousTTSUser = message.name }
            }

            else -> {
                return
            }
        }

        val queueMode = when (settings.ttsPlayMode) {
            TTSPlayMode.Queue -> TextToSpeech.QUEUE_ADD
            TTSPlayMode.Newest -> TextToSpeech.QUEUE_FLUSH
        }

        if (settings.ttsAudioDucking) {
            requestAudioFocus()
        }

        val params = Bundle().apply {
            putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, settings.ttsVolume)
        }
        pendingUtterances.incrementAndGet()
        val result = tts?.speak(text, queueMode, params, "tts_${utteranceId++}")
        if (result != TextToSpeech.SUCCESS) {
            onUtteranceFinished()
        }
    }

    companion object {
        private val TTS_COMMAND_PREFIXES = setOf('!', '$', '%', '+', '-', '~')
        private val UNICODE_SYMBOL_REGEX = "\\p{So}|\\p{Sc}|\\p{Sm}|\\p{Cn}".toRegex()
        private val URL_REGEX = "[(http(s)?):\\/\\/(www\\.)?a-zA-Z0-9@:%._\\+~#=]{2,256}\\.[a-z]{2,6}\\b([-a-zA-Z0-9@:%_\\+.~#?&//=]*)".toRegex(RegexOption.IGNORE_CASE)
    }
}

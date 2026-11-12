package com.flxrs.dankchat.data.notification

import android.app.Application
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import com.flxrs.dankchat.R
import com.flxrs.dankchat.data.repo.chat.ChatChannelProvider
import com.flxrs.dankchat.data.repo.chat.ChatNotificationRepository
import com.flxrs.dankchat.di.DispatchersProvider
import com.flxrs.dankchat.preferences.tools.ToolsSettings
import com.flxrs.dankchat.preferences.tools.ToolsSettingsDataStore
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowTextToSpeech
import org.robolectric.shadows.ShadowToast
import java.util.Locale
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class ChatTTSPlayerTest {
    private val scheduler = TestCoroutineScheduler()
    private val dispatcher = StandardTestDispatcher(scheduler)
    private val context = RuntimeEnvironment.getApplication()
    private val settings = MutableStateFlow(ToolsSettings(ttsEnabled = true))

    private val chatNotificationRepository: ChatNotificationRepository = mockk()
    private val chatChannelProvider: ChatChannelProvider = mockk()
    private val toolsSettingsDataStore: ToolsSettingsDataStore = mockk()
    private val dispatchersProvider: DispatchersProvider = mockk()

    private lateinit var player: ChatTTSPlayer

    @Before
    fun setup() {
        every { chatNotificationRepository.messageUpdates } returns MutableSharedFlow()
        every { chatChannelProvider.activeChannel } returns MutableStateFlow(null)
        every { toolsSettingsDataStore.settings } returns settings
        every { toolsSettingsDataStore.current() } answers { settings.value }
        coEvery { toolsSettingsDataStore.update(any()) } coAnswers {
            val transform = firstArg<suspend (ToolsSettings) -> ToolsSettings>()
            settings.value = transform(settings.value)
            Result.success(settings.value)
        }
        every { dispatchersProvider.io } returns dispatcher
        every { dispatchersProvider.main } returns dispatcher

        player = ChatTTSPlayer(context, chatNotificationRepository, chatChannelProvider, toolsSettingsDataStore, dispatchersProvider)
    }

    @Test
    fun `engine that fails to start turns TTS off with a message`() {
        val engine = startEngine()

        engine.reportInit(TextToSpeech.ERROR)

        assertFalse(settings.value.ttsEnabled)
        assertTrue(shadowOf(engine).isShutdown)
        assertEquals(expected = context.getString(R.string.tts_start_failed), actual = ShadowToast.getTextOfLatestToast())
    }

    @Test
    fun `engine without a usable voice turns TTS off with a message`() {
        val engine = startEngine()

        engine.reportInit(TextToSpeech.SUCCESS)

        assertFalse(settings.value.ttsEnabled)
        assertEquals(expected = context.getString(R.string.tts_start_failed), actual = ShadowToast.getTextOfLatestToast())
    }

    @Test
    fun `report of a replaced engine is ignored`() {
        val firstEngine = startEngine()
        settings.update { it.copy(ttsEnabled = false) }
        scheduler.advanceUntilIdle()
        settings.update { it.copy(ttsEnabled = true) }
        scheduler.advanceUntilIdle()
        val secondEngine = ShadowTextToSpeech.getLastTextToSpeechInstance()
        assertNotSame(illegal = firstEngine, actual = secondEngine)

        firstEngine.reportInit(TextToSpeech.ERROR)

        assertTrue(settings.value.ttsEnabled)
        assertFalse(shadowOf(secondEngine).isShutdown)
        assertNull(ShadowToast.getLatestToast())
    }

    @Test
    fun `voice is applied again when force English changes`() {
        ShadowTextToSpeech.addVoice(ENGLISH_VOICE)
        settings.value = ToolsSettings(ttsEnabled = true, ttsForceEnglish = true)
        val engine = startEngine()
        engine.reportInit(TextToSpeech.SUCCESS)
        assertEquals(expected = ENGLISH_VOICE, actual = shadowOf(engine).currentVoice)

        // The test engine has no default voice, so the English fallback switches the setting back on
        settings.update { it.copy(ttsForceEnglish = false) }
        scheduler.advanceUntilIdle()

        assertTrue(settings.value.ttsForceEnglish)
        assertTrue(settings.value.ttsEnabled)
    }

    private fun startEngine(): TextToSpeech {
        player.start()
        scheduler.advanceUntilIdle()
        return ShadowTextToSpeech.getLastTextToSpeechInstance()
    }

    private fun TextToSpeech.reportInit(status: Int) {
        shadowOf(this).onInitListener.onInit(status)
        scheduler.advanceUntilIdle()
    }

    private companion object {
        val ENGLISH_VOICE = Voice("en-us", Locale.US, Voice.QUALITY_NORMAL, Voice.LATENCY_NORMAL, false, emptySet())
    }
}

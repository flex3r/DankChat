package com.flxrs.dankchat.ui.chat.emote

import android.app.Application
import android.graphics.Color
import android.graphics.drawable.Animatable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.LayerDrawable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.v2.createComposeRule
import com.flxrs.dankchat.data.twitch.emote.ChatMessageEmote
import com.flxrs.dankchat.data.twitch.emote.ChatMessageEmoteType
import com.flxrs.dankchat.di.DispatchersProvider
import com.flxrs.dankchat.preferences.chat.ChatSettings
import com.flxrs.dankchat.preferences.chat.ChatSettingsDataStore
import com.flxrs.dankchat.ui.chat.EmoteUi
import io.mockk.every
import io.mockk.mockk
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class StackedEmoteAnimationTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val settingsFlow = MutableStateFlow(ChatSettings())
    private val chatSettingsDataStore: ChatSettingsDataStore =
        mockk {
            every { currentSettings } returns settingsFlow
        }
    private val dispatchersProvider =
        object : DispatchersProvider {
            override val default: CoroutineDispatcher = Dispatchers.Main.immediate
            override val io: CoroutineDispatcher = Dispatchers.Main.immediate
            override val main: CoroutineDispatcher = Dispatchers.Main.immediate
            override val immediate: CoroutineDispatcher = Dispatchers.Main.immediate
        }
    private val coordinator = EmoteAnimationCoordinator(chatSettingsDataStore, dispatchersProvider)

    @Test
    fun `offscreen occurrence of a cached emote does not start its animation`() {
        val emote = emote(SINGLE_URL)
        val drawable = cacheSingle(SINGLE_URL)

        composeRule.setContent { Occurrence(emote, isPageVisible = false) }
        composeRule.waitForIdle()

        assertFalse(drawable.isRunning)
    }

    @Test
    fun `offscreen occurrence of a cached stacked emote does not start its animations`() {
        val emote = emote(SINGLE_URL, OVERLAY_URL)
        val layers = cacheStacked(emote)

        composeRule.setContent { Occurrence(emote, isPageVisible = false) }
        composeRule.waitForIdle()

        layers.forEach { assertFalse(it.isRunning) }
    }

    @Test
    fun `enabling animated emotes does not start offscreen animations`() {
        val emote = emote(SINGLE_URL)
        val drawable = cacheSingle(SINGLE_URL)
        settingsFlow.value = ChatSettings(animateGifs = false)

        composeRule.setContent { Occurrence(emote, isPageVisible = false) }
        composeRule.waitForIdle()
        settingsFlow.value = ChatSettings(animateGifs = true)
        composeRule.waitForIdle()

        assertFalse(drawable.isRunning)
    }

    @Test
    fun `toggling animated emotes stops and restarts visible animations`() {
        val emote = emote(SINGLE_URL)
        val drawable = cacheSingle(SINGLE_URL)

        composeRule.setContent { Occurrence(emote, isPageVisible = true) }
        composeRule.waitForIdle()
        assertTrue(drawable.isRunning)

        settingsFlow.value = ChatSettings(animateGifs = false)
        composeRule.waitForIdle()
        assertFalse(drawable.isRunning)

        settingsFlow.value = ChatSettings(animateGifs = true)
        composeRule.waitForIdle()
        assertTrue(drawable.isRunning)
    }

    @Test
    fun `visible occurrence starts the animation and stops it when it leaves`() {
        val emote = emote(SINGLE_URL)
        val drawable = cacheSingle(SINGLE_URL)
        var isShown by mutableStateOf(true)

        composeRule.setContent {
            if (isShown) {
                Occurrence(emote, isPageVisible = true)
            }
        }
        composeRule.waitForIdle()
        assertTrue(drawable.isRunning)

        isShown = false
        composeRule.waitForIdle()
        assertFalse(drawable.isRunning)
    }

    @Test
    fun `occurrence on a page that moves offscreen stops the animation`() {
        val emote = emote(SINGLE_URL)
        val drawable = cacheSingle(SINGLE_URL)
        var isPageVisible by mutableStateOf(true)

        composeRule.setContent { Occurrence(emote, isPageVisible = isPageVisible) }
        composeRule.waitForIdle()
        assertTrue(drawable.isRunning)

        isPageVisible = false
        composeRule.waitForIdle()
        assertFalse(drawable.isRunning)
    }

    @Composable
    private fun Occurrence(
        emote: EmoteUi,
        isPageVisible: Boolean,
    ) {
        CompositionLocalProvider(LocalChatPageVisible provides isPageVisible) {
            StackedEmote(
                emote = emote,
                fontSize = FONT_SIZE,
                emoteCoordinator = coordinator,
            )
        }
    }

    private fun baseHeightPx(): Int = with(composeRule.density) { emoteBaseHeight(FONT_SIZE).toPx().toInt() }

    private fun cacheSingle(url: String): FakeAnimatedDrawable {
        val drawable = FakeAnimatedDrawable()
        coordinator.putInCache(singleEmoteCacheKey(url, baseHeightPx()), drawable)
        return drawable
    }

    private fun cacheStacked(emote: EmoteUi): List<FakeAnimatedDrawable> {
        val layers = emote.urls.map { FakeAnimatedDrawable() }
        val layerDrawable = LayerDrawable(layers.toTypedArray<Drawable>()).apply { setBounds(0, 0, EMOTE_SIZE_PX, EMOTE_SIZE_PX) }
        coordinator.putLayerInCache(emote.stackedCacheKey(baseHeightPx()), layerDrawable)
        return layers
    }

    private fun emote(vararg urls: String): EmoteUi = EmoteUi(
        code = "forsenE",
        urls = urls.toList().toImmutableList(),
        position = 0..6,
        isAnimated = true,
        isTwitch = true,
        scale = 1,
        emotes =
            urls
                .mapIndexed { index, url ->
                    ChatMessageEmote(
                        position = 0..6,
                        url = url,
                        id = "$index",
                        code = "forsenE",
                        scale = 1,
                        type = ChatMessageEmoteType.TwitchEmote,
                        isTwitch = true,
                    )
                }.toImmutableList(),
    )

    private class FakeAnimatedDrawable :
        ColorDrawable(Color.RED),
        Animatable {
        private var running = false

        init {
            setBounds(0, 0, EMOTE_SIZE_PX, EMOTE_SIZE_PX)
        }

        override fun start() {
            running = true
        }

        override fun stop() {
            running = false
        }

        override fun isRunning(): Boolean = running
    }

    private companion object {
        const val FONT_SIZE = 14f
        const val EMOTE_SIZE_PX = 28
        const val SINGLE_URL = "https://static-cdn.jtvnw.net/emoticons/v2/1/default/dark/3.0"
        const val OVERLAY_URL = "https://cdn.7tv.app/emote/2/4x.webp"
    }
}

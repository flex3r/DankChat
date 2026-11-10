package com.flxrs.dankchat.ui.main

import com.flxrs.dankchat.data.repo.chat.UserStateRepository
import com.flxrs.dankchat.data.state.GlobalLoadingState
import com.flxrs.dankchat.domain.ChannelDataCoordinator
import com.flxrs.dankchat.preferences.DankChatPreferenceStore
import com.flxrs.dankchat.preferences.appearance.AppearanceSettings
import com.flxrs.dankchat.preferences.appearance.AppearanceSettingsDataStore
import com.flxrs.dankchat.preferences.developer.DeveloperSettings
import com.flxrs.dankchat.preferences.developer.DeveloperSettingsDataStore
import com.flxrs.dankchat.preferences.stream.StreamsSettingsDataStore
import com.flxrs.dankchat.preferences.tools.ToolsSettingsDataStore
import io.mockk.coEvery
import io.mockk.every
import io.mockk.junit5.MockKExtension
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
@ExtendWith(MockKExtension::class)
internal class KeyboardHeightTrackingTest {
    private val testDispatcher = UnconfinedTestDispatcher()

    private val channelDataCoordinator: ChannelDataCoordinator = mockk()
    private val appearanceSettingsDataStore: AppearanceSettingsDataStore = mockk()
    private val preferenceStore: DankChatPreferenceStore = mockk()
    private val developerSettingsDataStore: DeveloperSettingsDataStore = mockk()
    private val streamsSettingsDataStore: StreamsSettingsDataStore = mockk()
    private val userStateRepository: UserStateRepository = mockk()
    private val toolsSettingsDataStore: ToolsSettingsDataStore = mockk()

    private lateinit var viewModel: MainScreenViewModel

    @BeforeEach
    fun setup() {
        Dispatchers.setMain(testDispatcher)

        every { channelDataCoordinator.globalLoadingState } returns MutableStateFlow(GlobalLoadingState.Idle)
        justRun { channelDataCoordinator.loadGlobalData() }
        every { appearanceSettingsDataStore.settings } returns MutableStateFlow(AppearanceSettings())
        coEvery { appearanceSettingsDataStore.update(any()) } returns Unit
        every { developerSettingsDataStore.settings } returns MutableStateFlow(DeveloperSettings())
        every { streamsSettingsDataStore.keepStreamWhileTyping } returns MutableStateFlow(false)
        every { toolsSettingsDataStore.ttsEnabled } returns MutableStateFlow(false)
        every { preferenceStore.keyboardHeightPortrait } returns 0
        every { preferenceStore.keyboardHeightLandscape } returns 0
        justRun { preferenceStore.keyboardHeightPortrait = any() }
        justRun { preferenceStore.keyboardHeightLandscape = any() }

        viewModel = MainScreenViewModel(
            channelDataCoordinator = channelDataCoordinator,
            appearanceSettingsDataStore = appearanceSettingsDataStore,
            preferenceStore = preferenceStore,
            developerSettingsDataStore = developerSettingsDataStore,
            userStateRepository = userStateRepository,
            toolsSettingsDataStore = toolsSettingsDataStore,
            streamsSettingsDataStore = streamsSettingsDataStore,
        )
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `height the keyboard opens with is kept`() = runTest(testDispatcher) {
        track(LETTERS)
        advanceTimeBy(SETTLED)

        assertEquals(LETTERS, viewModel.keyboardHeightPx.value)
        verify { preferenceStore.keyboardHeightPortrait = LETTERS }
    }

    @Test
    fun `taller panel while the keyboard stays open is ignored`() = runTest(testDispatcher) {
        track(LETTERS)
        advanceTimeBy(SETTLED)
        track(EMOJI_PANEL)
        advanceTimeBy(SETTLED)

        assertEquals(LETTERS, viewModel.keyboardHeightPx.value)
    }

    @Test
    fun `keyboard that opens with a taller panel drops to the lower height once the panel closes`() = runTest(testDispatcher) {
        track(EMOJI_PANEL)
        advanceTimeBy(SETTLED)
        track(LETTERS)
        advanceTimeBy(SETTLED)

        assertEquals(LETTERS, viewModel.keyboardHeightPx.value)
        verify { preferenceStore.keyboardHeightPortrait = LETTERS }
    }

    @Test
    fun `lower height in passing is ignored`() = runTest(testDispatcher) {
        track(LETTERS_WITH_SUGGESTIONS)
        advanceTimeBy(SETTLED)
        track(LETTERS)
        advanceTimeBy(100)
        track(LETTERS_WITH_SUGGESTIONS)
        advanceTimeBy(SETTLED)

        assertEquals(LETTERS_WITH_SUGGESTIONS, viewModel.keyboardHeightPx.value)
    }

    @Test
    fun `height change right after opening replaces the first height`() = runTest(testDispatcher) {
        track(LETTERS)
        advanceTimeBy(100)
        track(LETTERS_WITH_SUGGESTIONS)
        advanceTimeBy(SETTLED)

        assertEquals(LETTERS_WITH_SUGGESTIONS, viewModel.keyboardHeightPx.value)
    }

    @Test
    fun `keyboard that closes before it settles is not kept`() = runTest(testDispatcher) {
        track(LETTERS)
        advanceTimeBy(100)
        track(CLOSED)
        advanceTimeBy(SETTLED)

        assertEquals(0, viewModel.keyboardHeightPx.value)
    }

    @Test
    fun `reopened keyboard updates the height`() = runTest(testDispatcher) {
        track(EMOJI_PANEL)
        advanceTimeBy(SETTLED)
        track(CLOSED)
        track(LETTERS)
        advanceTimeBy(SETTLED)

        assertEquals(LETTERS, viewModel.keyboardHeightPx.value)
    }

    @Test
    fun `height below the minimum counts as closed`() = runTest(testDispatcher) {
        track(LETTERS)
        advanceTimeBy(SETTLED)
        track(BELOW_MINIMUM)
        track(EMOJI_PANEL)
        advanceTimeBy(SETTLED)

        assertEquals(EMOJI_PANEL, viewModel.keyboardHeightPx.value)
    }

    @Test
    fun `rotating with the keyboard open keeps a height per orientation`() = runTest(testDispatcher) {
        track(LETTERS)
        advanceTimeBy(SETTLED)
        track(LANDSCAPE_LETTERS, isLandscape = true)
        advanceTimeBy(SETTLED)

        verify { preferenceStore.keyboardHeightPortrait = LETTERS }
        verify { preferenceStore.keyboardHeightLandscape = LANDSCAPE_LETTERS }
    }

    private fun track(
        heightPx: Int,
        isLandscape: Boolean = false,
    ) {
        viewModel.trackKeyboardHeight(heightPx, isLandscape, MIN_HEIGHT)
    }

    companion object {
        private const val MIN_HEIGHT = 300f
        private const val SETTLED = 301L
        private const val CLOSED = 0
        private const val BELOW_MINIMUM = 120
        private const val LETTERS = 880
        private const val LETTERS_WITH_SUGGESTIONS = 960
        private const val EMOJI_PANEL = 1260
        private const val LANDSCAPE_LETTERS = 520
    }
}

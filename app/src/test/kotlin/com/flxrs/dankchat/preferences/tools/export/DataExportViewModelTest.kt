package com.flxrs.dankchat.preferences.tools.export

import com.flxrs.dankchat.data.backup.BackupRepository
import com.flxrs.dankchat.data.backup.NewerBackupVersionException
import com.flxrs.dankchat.preferences.DankChatPreferenceStore
import com.flxrs.dankchat.preferences.tools.ImageUploaderConfig
import com.flxrs.dankchat.preferences.tools.ToolsSettings
import com.flxrs.dankchat.preferences.tools.ToolsSettingsDataStore
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.SerializationException
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
internal class DataExportViewModelTest {
    private val testDispatcher = UnconfinedTestDispatcher()
    private val backupRepository: BackupRepository = mockk()
    private val preferenceStore: DankChatPreferenceStore = mockk()
    private val toolsSettingsDataStore: ToolsSettingsDataStore = mockk()

    private lateinit var viewModel: DataExportViewModel

    @BeforeEach
    fun setup() {
        Dispatchers.setMain(testDispatcher)

        every { preferenceStore.getChannelsWithRenamesFlow() } returns flowOf(emptyList())
        every { preferenceStore.channels } returns emptyList()
        every { toolsSettingsDataStore.uploadConfig } returns flowOf(ImageUploaderConfig.DEFAULT)
        every { toolsSettingsDataStore.current() } returns ToolsSettings()

        viewModel = DataExportViewModel(backupRepository, preferenceStore, toolsSettingsDataStore)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `pasted backup of a newer version is reported as such`() = runTest(testDispatcher) {
        coEvery { backupRepository.readSettings(any<String>()) } coAnswers { Result.failure(NewerBackupVersionException()) }
        val events = collectEvents()

        viewModel.pasteSettings("backup")

        assertEquals(expected = listOf<DataExportEvent>(DataExportEvent.ImportNewerVersion), actual = events)
    }

    @Test
    fun `pasted text that is no backup is reported as invalid clipboard`() = runTest(testDispatcher) {
        coEvery { backupRepository.readSettings(any<String>()) } coAnswers { Result.failure(SerializationException("no backup")) }
        val events = collectEvents()

        viewModel.pasteSettings("text")

        assertEquals(expected = listOf<DataExportEvent>(DataExportEvent.ClipboardInvalid), actual = events)
    }

    private fun TestScope.collectEvents(): List<DataExportEvent> {
        val events = mutableListOf<DataExportEvent>()
        backgroundScope.launch { viewModel.events.collect { events += it } }
        return events
    }
}

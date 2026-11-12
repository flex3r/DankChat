package com.flxrs.dankchat.data.backup

import com.flxrs.dankchat.di.DispatchersProvider
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

@OptIn(ExperimentalCoroutinesApi::class)
internal class BackupRepositoryTest {
    private val testDispatcher = UnconfinedTestDispatcher()
    private val dispatchersProvider: DispatchersProvider = mockk { every { io } returns testDispatcher }

    private val repository =
        BackupRepository(
            context = mockk(),
            database = mockk(),
            preferenceStore = mockk(),
            chatChannelProvider = mockk(),
            appearanceSettingsDataStore = mockk(),
            chatSettingsDataStore = mockk(),
            notificationsSettingsDataStore = mockk(),
            streamsSettingsDataStore = mockk(),
            batterySettingsDataStore = mockk(),
            toolsSettingsDataStore = mockk(),
            developerSettingsDataStore = mockk(),
            dispatchersProvider = dispatchersProvider,
        )

    @Test
    fun `settings backup of the current version is read`() = runTest(testDispatcher) {
        val result = repository.readSettings("""{"version":1,"appVersion":"4.0.0"}""")

        assertEquals(expected = "4.0.0", actual = result.getOrThrow().appVersion)
    }

    @Test
    fun `settings backup of a newer version is rejected`() = runTest(testDispatcher) {
        val result = repository.readSettings("""{"version":2,"appVersion":"9.0.0"}""")

        assertIs<NewerBackupVersionException>(result.exceptionOrNull())
    }
}

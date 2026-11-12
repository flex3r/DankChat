package com.flxrs.dankchat.data.api.upload

import com.flxrs.dankchat.di.DispatchersProvider
import com.flxrs.dankchat.preferences.tools.ImageUploaderConfig
import com.flxrs.dankchat.preferences.tools.ToolsSettings
import com.flxrs.dankchat.preferences.tools.ToolsSettingsDataStore
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

internal class UploadClientTest {
    private val server = MockWebServer()
    private val toolsSettingsDataStore: ToolsSettingsDataStore = mockk()
    private val dispatchersProvider: DispatchersProvider = mockk()

    @TempDir
    lateinit var directory: File

    private lateinit var client: UploadClient

    @BeforeEach
    fun setup() {
        server.start()
        val uploader =
            ImageUploaderConfig(
                uploadUrl = server.url("/upload").toString(),
                formField = "file",
                headers = null,
                imageLinkPattern = null,
                deletionLinkPattern = null,
            )
        every { toolsSettingsDataStore.settings } returns flowOf(ToolsSettings(uploaderConfig = uploader))
        every { dispatchersProvider.io } returns Dispatchers.IO
        every { dispatchersProvider.default } returns Dispatchers.Default

        client = UploadClient(OkHttpClient(), toolsSettingsDataStore, dispatchersProvider)
    }

    @AfterEach
    fun tearDown() {
        server.close()
    }

    @Test
    fun `upload returns the link of the response`() = runBlocking {
        server.enqueue(MockResponse.Builder().body("https://example.com/image.png").build())

        val result = client.uploadMedia(mediaFile())

        assertEquals(expected = "https://example.com/image.png", actual = result.getOrThrow().imageLink)
    }

    @Test
    fun `cancelling the upload does not wait for the response`() = runBlocking {
        server.enqueue(
            MockResponse
                .Builder()
                .headersDelay(RESPONSE_DELAY_SECONDS, TimeUnit.SECONDS)
                .body("https://example.com/image.png")
                .build(),
        )
        val upload = async(Dispatchers.IO) { client.uploadMedia(mediaFile()) }
        server.takeRequest()

        withTimeout(5.seconds) { upload.cancelAndJoin() }

        assertTrue(upload.isCancelled)
    }

    private fun mediaFile(): File = File(directory, "image.png").apply { writeText("image") }

    private companion object {
        const val RESPONSE_DELAY_SECONDS = 30L
    }
}

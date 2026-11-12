package com.flxrs.dankchat.data.api.cache

import android.content.Context
import com.flxrs.dankchat.di.DispatchersProvider
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days

@OptIn(ExperimentalCoroutinesApi::class)
internal class EmoteDiskCacheTest {
    @TempDir
    lateinit var filesDir: File

    private val testDispatcher = UnconfinedTestDispatcher()
    private val dispatchersProvider =
        object : DispatchersProvider {
            override val default: CoroutineDispatcher = testDispatcher
            override val io: CoroutineDispatcher = testDispatcher
            override val main: CoroutineDispatcher = testDispatcher
            override val immediate: CoroutineDispatcher = testDispatcher
        }
    private val serializer = ListSerializer(String.serializer())
    private val cacheDir get() = File(filesDir, "emote_cache")

    @Test
    fun `entries that were not used for a while are deleted on creation`() {
        val unused = cacheFile("bttv_channel_1").also { it.setLastModified(System.currentTimeMillis() - 31.days.inWholeMilliseconds) }
        val used = cacheFile("bttv_channel_2").also { it.setLastModified(System.currentTimeMillis() - 29.days.inWholeMilliseconds) }

        createCache()

        assertFalse(unused.exists())
        assertTrue(used.exists())
    }

    @Test
    fun `reading an entry counts as use`() = runTest(testDispatcher) {
        val cache = createCache()
        val file = cacheFile("bttv_channel_1").also { it.setLastModified(System.currentTimeMillis() - 29.days.inWholeMilliseconds) }

        cache.read("bttv_channel_1", serializer)

        assertTrue(file.lastModified() > System.currentTimeMillis() - 1.days.inWholeMilliseconds)
    }

    @Test
    fun `written entry is read back and leaves no temp file`() = runTest(testDispatcher) {
        val cache = createCache()

        cache.write("bttv_global", listOf("forsenE"), serializer)

        assertEquals(expected = listOf("forsenE"), actual = cache.read("bttv_global", serializer))
        assertEquals(expected = listOf("bttv_global.json"), actual = cacheDir.list().orEmpty().toList())
    }

    @Test
    fun `broken entry reads as missing`() = runTest(testDispatcher) {
        val cache = createCache()
        cacheFile("bttv_global").writeText("""["forsenE",""")

        assertNull(cache.read("bttv_global", serializer))
    }

    private fun createCache(): EmoteDiskCache {
        val context = mockk<Context>()
        every { context.filesDir } returns filesDir
        return EmoteDiskCache(context, dispatchersProvider)
    }

    private fun cacheFile(key: String): File {
        cacheDir.mkdirs()
        return File(cacheDir, "$key.json").also { it.writeText("""["forsenE"]""") }
    }
}

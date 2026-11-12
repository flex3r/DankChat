package com.flxrs.dankchat.utils.datastore

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import okio.Buffer
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

internal class DataStoreKotlinxSerializerTest {
    private val serializer = DataStoreKotlinxSerializer(defaultValue = TestSettings(), serializer = TestSettings.serializer())

    @Test
    fun `unknown enum value resets only its own setting`() = runTest {
        val source = Buffer().writeUtf8("""{"name":"custom","mode":"Removed","count":3}""")

        val settings = serializer.readFrom(source)

        assertEquals(expected = TestSettings(name = "custom", mode = TestMode.First, count = 3), actual = settings)
    }

    @Test
    fun `unreadable content falls back to the default values`() = runTest {
        val source = Buffer().writeUtf8("""{"name":"custom","count":""")

        val settings = serializer.readFrom(source)

        assertEquals(expected = TestSettings(), actual = settings)
    }

    @Test
    fun `written settings are read back`() = runTest {
        val settings = TestSettings(name = "custom", mode = TestMode.Second, count = 3)
        val buffer = Buffer()

        serializer.writeTo(settings, buffer)

        assertEquals(expected = settings, actual = serializer.readFrom(buffer))
    }

    @Serializable
    private data class TestSettings(
        val name: String = "default",
        val mode: TestMode = TestMode.First,
        val count: Int = 0,
    )

    private enum class TestMode { First, Second }
}

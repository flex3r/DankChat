package com.flxrs.dankchat.data.api.upload

import com.flxrs.dankchat.data.api.upload.UploadClient.Companion.extractJsonLink
import com.flxrs.dankchat.data.api.upload.UploadClient.Companion.getJsonValue
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

internal class UploadLinkPatternTest {
    private fun parse(json: String) = Json.parseToJsonElement(json)

    @Test
    fun `getJsonValue extracts simple object key`() {
        val json = parse("""{"link": "https://example.com/image.png"}""")
        assertEquals("https://example.com/image.png", json.getJsonValue("link"))
    }

    @Test
    fun `getJsonValue extracts nested object key`() {
        val json = parse("""{"data": {"url": "https://example.com/image.png"}}""")
        assertEquals("https://example.com/image.png", json.getJsonValue("data.url"))
    }

    @Test
    fun `getJsonValue extracts from array by index`() {
        val json = parse("""[{"url": "https://example.com/image.png"}]""")
        assertEquals("https://example.com/image.png", json.getJsonValue("0.url"))
    }

    @Test
    fun `getJsonValue extracts from nested object with array`() {
        val json = parse("""{"files": [{"url": "https://example.com/image.png"}]}""")
        assertEquals("https://example.com/image.png", json.getJsonValue("files.0.url"))
    }

    @Test
    fun `getJsonValue extracts from deeply nested path`() {
        val json = parse("""{"a": {"b": [{"c": "value"}]}}""")
        assertEquals("value", json.getJsonValue("a.b.0.c"))
    }

    @Test
    fun `getJsonValue extracts non-first array element`() {
        val json = parse("""["first", "second", "third"]""")
        assertEquals("second", json.getJsonValue("1"))
    }

    @Test
    fun `getJsonValue returns null for missing key`() {
        val json = parse("""{"link": "https://example.com"}""")
        assertNull(json.getJsonValue("missing"))
    }

    @Test
    fun `getJsonValue returns null for out of bounds index`() {
        val json = parse("""["only"]""")
        assertNull(json.getJsonValue("5"))
    }

    @Test
    fun `getJsonValue returns null for invalid index on array`() {
        val json = parse("""["item"]""")
        assertNull(json.getJsonValue("notanumber"))
    }

    @Test
    fun `extractJsonLink replaces single placeholder`() {
        val json = parse("""{"link": "https://example.com/image.png"}""")
        assertEquals("https://example.com/image.png", json.extractJsonLink("{link}"))
    }

    @Test
    fun `extractJsonLink replaces array path placeholder`() {
        val json = parse("""[{"url": "https://example.com/image.png"}]""")
        assertEquals("https://example.com/image.png", json.extractJsonLink("{0.url}"))
    }

    @Test
    fun `extractJsonLink interpolates multiple placeholders`() {
        val json = parse("""{"host": "https://example.com", "path": "image.png"}""")
        assertEquals("https://example.com/image.png", json.extractJsonLink("{host}/{path}"))
    }

    @Test
    fun `extractJsonLink returns null for unmatched placeholders`() {
        val json = parse("""{"link": "https://example.com"}""")
        assertNull(json.extractJsonLink("{missing}"))
    }

    @Test
    fun `extractJsonLink with real uploader response`() {
        val response = """[{"id":"cmnh6sm1z","name":"zKjLeT.png","type":"image/png","url":"https://sus.link/u/zKjLeT.png"}]"""
        val json = parse(response)
        assertEquals("https://sus.link/u/zKjLeT.png", json.extractJsonLink("{0.url}"))
    }

    @Test
    fun `getJsonValue handles numeric value`() {
        val json = parse("""{"count": 42}""")
        assertEquals("42", json.getJsonValue("count"))
    }

    @Test
    fun `getJsonValue handles boolean value`() {
        val json = parse("""{"success": true}""")
        assertEquals("true", json.getJsonValue("success"))
    }
}

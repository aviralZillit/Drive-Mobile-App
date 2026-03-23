package com.zillit.drive.util

import android.graphics.Color
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class FileUtilsTest {

    @Before
    fun setup() {
        // Mock Color.parseColor since it's an Android framework method
        mockkStatic(Color::class)
        every { Color.parseColor(any()) } answers {
            val hex = firstArg<String>()
            // Simple hex-to-int conversion for testing
            java.lang.Long.decode(hex.replace("#", "0xFF")).toInt()
        }
    }

    @After
    fun tearDown() {
        unmockkStatic(Color::class)
    }

    // ─── formatFileSize ───

    @Test
    fun `formatFileSize bytes`() {
        val result = FileUtils.formatFileSize(500)
        assertEquals("500.0 B", result)
    }

    @Test
    fun `formatFileSize kilobytes`() {
        val result = FileUtils.formatFileSize(1536) // 1.5 KB
        assertEquals("1.5 KB", result)
    }

    @Test
    fun `formatFileSize megabytes`() {
        val result = FileUtils.formatFileSize(5_242_880) // 5.0 MB
        assertEquals("5.0 MB", result)
    }

    @Test
    fun `formatFileSize gigabytes`() {
        val result = FileUtils.formatFileSize(2_147_483_648) // 2.0 GB
        assertEquals("2.0 GB", result)
    }

    @Test
    fun `formatFileSize zero`() {
        val result = FileUtils.formatFileSize(0)
        assertEquals("0 B", result)
    }

    @Test
    fun `formatFileSize negative returns zero`() {
        val result = FileUtils.formatFileSize(-100)
        assertEquals("0 B", result)
    }

    // ─── getExtensionColor ───

    @Test
    fun `getExtensionColor pdf returns red`() {
        val color = FileUtils.getExtensionColor("pdf")
        // Verify Color.parseColor was called with the red hex
        val expectedColor = Color.parseColor("#E53935")
        assertEquals(expectedColor, color)
    }

    @Test
    fun `getExtensionColor doc returns blue`() {
        val color = FileUtils.getExtensionColor("doc")
        val expectedColor = Color.parseColor("#1565C0")
        assertEquals(expectedColor, color)
    }

    @Test
    fun `getExtensionColor docx returns blue`() {
        val color = FileUtils.getExtensionColor("docx")
        val expectedColor = Color.parseColor("#1565C0")
        assertEquals(expectedColor, color)
    }

    @Test
    fun `getExtensionColor xls returns green`() {
        val color = FileUtils.getExtensionColor("xls")
        val expectedColor = Color.parseColor("#2E7D32")
        assertEquals(expectedColor, color)
    }

    @Test
    fun `getExtensionColor image returns purple`() {
        val color = FileUtils.getExtensionColor("jpg")
        val expectedColor = Color.parseColor("#7B1FA2")
        assertEquals(expectedColor, color)
    }

    @Test
    fun `getExtensionColor png returns purple`() {
        val color = FileUtils.getExtensionColor("png")
        val expectedColor = Color.parseColor("#7B1FA2")
        assertEquals(expectedColor, color)
    }

    @Test
    fun `getExtensionColor unknown returns gray`() {
        val color = FileUtils.getExtensionColor("xyz")
        val expectedColor = Color.parseColor("#78909C")
        assertEquals(expectedColor, color)
    }

    @Test
    fun `getExtensionColor is case insensitive`() {
        val colorUpper = FileUtils.getExtensionColor("PDF")
        val colorLower = FileUtils.getExtensionColor("pdf")
        assertEquals(colorUpper, colorLower)
    }

    // ─── Utility methods ───

    @Test
    fun `isImageFile recognizes common image extensions`() {
        assertTrue(FileUtils.isImageFile("jpg"))
        assertTrue(FileUtils.isImageFile("jpeg"))
        assertTrue(FileUtils.isImageFile("png"))
        assertTrue(FileUtils.isImageFile("gif"))
        assertTrue(FileUtils.isImageFile("webp"))
        assertTrue(FileUtils.isImageFile("svg"))
        assertFalse(FileUtils.isImageFile("pdf"))
        assertFalse(FileUtils.isImageFile("mp4"))
    }

    @Test
    fun `isVideoFile recognizes common video extensions`() {
        assertTrue(FileUtils.isVideoFile("mp4"))
        assertTrue(FileUtils.isVideoFile("mov"))
        assertTrue(FileUtils.isVideoFile("avi"))
        assertTrue(FileUtils.isVideoFile("mkv"))
        assertFalse(FileUtils.isVideoFile("jpg"))
        assertFalse(FileUtils.isVideoFile("mp3"))
    }

    @Test
    fun `isAudioFile recognizes common audio extensions`() {
        assertTrue(FileUtils.isAudioFile("mp3"))
        assertTrue(FileUtils.isAudioFile("wav"))
        assertTrue(FileUtils.isAudioFile("aac"))
        assertTrue(FileUtils.isAudioFile("flac"))
        assertFalse(FileUtils.isAudioFile("mp4"))
    }

    @Test
    fun `isPdfFile recognizes pdf`() {
        assertTrue(FileUtils.isPdfFile("pdf"))
        assertTrue(FileUtils.isPdfFile("PDF"))
        assertFalse(FileUtils.isPdfFile("doc"))
    }

    @Test
    fun `isOfficeFile recognizes office extensions`() {
        assertTrue(FileUtils.isOfficeFile("doc"))
        assertTrue(FileUtils.isOfficeFile("docx"))
        assertTrue(FileUtils.isOfficeFile("xls"))
        assertTrue(FileUtils.isOfficeFile("xlsx"))
        assertTrue(FileUtils.isOfficeFile("ppt"))
        assertTrue(FileUtils.isOfficeFile("pptx"))
        assertFalse(FileUtils.isOfficeFile("pdf"))
    }

    @Test
    fun `isTextFile recognizes text extensions`() {
        assertTrue(FileUtils.isTextFile("txt"))
        assertTrue(FileUtils.isTextFile("csv"))
        assertTrue(FileUtils.isTextFile("md"))
        assertTrue(FileUtils.isTextFile("json"))
        assertTrue(FileUtils.isTextFile("xml"))
        assertFalse(FileUtils.isTextFile("pdf"))
    }

    @Test
    fun `getMimeType returns correct mime types`() {
        assertEquals("application/pdf", FileUtils.getMimeType("pdf"))
        assertEquals("image/jpeg", FileUtils.getMimeType("jpg"))
        assertEquals("image/png", FileUtils.getMimeType("png"))
        assertEquals("video/mp4", FileUtils.getMimeType("mp4"))
        assertEquals("audio/mpeg", FileUtils.getMimeType("mp3"))
        assertEquals("text/plain", FileUtils.getMimeType("txt"))
        assertEquals("application/json", FileUtils.getMimeType("json"))
        assertEquals("application/octet-stream", FileUtils.getMimeType("unknownext"))
    }
}

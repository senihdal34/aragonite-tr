package com.ethran.notable.io

import org.junit.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals

/**
 * Tests for updated PageCard / Home screen UI logic.
 * Run with: ./gradlew test
 */
class HomeUiLogicTest {

    @Test
    fun `title falls back to Isimsiz Not when empty`() {
        val title = ""
        val display = title.ifBlank { "İsimsiz Not" }
        assertEquals("İsimsiz Not", display)
    }

    @Test
    fun `title truncates at 50 chars`() {
        val title = "A".repeat(100)
        val truncated = title.take(50)
        assertEquals(50, truncated.length)
    }

    @Test
    fun `title sanitizes special characters`() {
        val title = "Bugünkü toplantı / notları: *önemli*"
        val sanitized = title.replace(Regex("[/\\\\:*?\"<>|]"), "-")
            .replace(Regex("\\s+"), " ").trim()
        assertEquals("Bugünkü toplantı - notları- -önemli-", sanitized)
    }

    @Test
    fun `baseName combines title and timestamp`() {
        val titleLine = "Toplantı notları"
        val timestamp = "2026-09-13-14-30-00"
        val baseName = if (titleLine.isNotBlank()) "$titleLine-$timestamp" else timestamp
        assertEquals("Toplantı notları-2026-09-13-14-30-00", baseName)
    }

    @Test
    fun `generateMarkdown embed uses baseName prefix`() {
        val result = InboxSyncEngine.generateMarkdown(
            createdDate = "2026-09-13",
            tags = emptyList(),
            content = "Test content",
            pages = 1,
            baseName = "Toplantı notları-2026-09-13-14-30-00"
        )
        assertContains(result, "![[jpg_archive/Toplantı notları-2026-09-13-14-30-00-page-1.jpg]]")
    }
}
package com.ethran.notable.reading

import java.io.File
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReadingSearchTest {
    private fun b(title: String, vararg authors: String, series: String? = null) =
        RBook(title, "/sdcard/Books/$title.pdf", title, authors.toList(), series)

    private val books = listOf(
        b("The Dispossessed", "Ursula K. Le Guin", series = "Hainish Cycle"),
        b("İstanbul Hatıraları", "Ahmet Hamdi Tanpınar"),
        b("Dune", "Frank Herbert", series = "Dune Chronicles"),
        b("Dune Messiah", "Frank Herbert", series = "Dune Chronicles"),
        b("Café Society", "Anon")
    )

    @Test fun blankQueryFindsNothing() {
        assertTrue(ReadingSearch.filter(books, "").isEmpty())
        assertTrue(ReadingSearch.filter(books, "   ").isEmpty())
    }

    @Test fun matchesTitleAuthorAndSeriesIgnoringCase() {
        assertEquals(listOf("The Dispossessed"), ReadingSearch.filter(books, "DISPOS").map { it.title })
        assertEquals(listOf("The Dispossessed"), ReadingSearch.filter(books, "le guin").map { it.title })
        assertEquals(listOf("The Dispossessed"), ReadingSearch.filter(books, "hainish").map { it.title })
    }

    @Test fun everyWordMustMatchSomewhere() {
        assertEquals(listOf("Dune", "Dune Messiah"), ReadingSearch.filter(books, "herbert dune").map { it.title })
        assertTrue(ReadingSearch.filter(books, "herbert guin").isEmpty())
    }

    @Test fun accentsAndTurkishDottedLettersFold() {
        assertEquals(listOf("İstanbul Hatıraları"), ReadingSearch.filter(books, "istanbul hatiralari").map { it.title })
        assertEquals(listOf("İstanbul Hatıraları"), ReadingSearch.filter(books, "tanpinar").map { it.title })
        assertEquals(listOf("Café Society"), ReadingSearch.filter(books, "cafe").map { it.title })
    }

    @Test fun titleStartsComeFirst() {
        val list = listOf(b("A Study in Dune", "X"), b("Dune", "Y"), b("Something", "Dune Writer"))
        assertEquals(listOf("Dune", "A Study in Dune", "Something"), ReadingSearch.filter(list, "dune").map { it.title })
    }

    @Test fun goalStaysInRange() {
        assertEquals(16, ReadingGoal.step(15, 1))
        assertEquals(14, ReadingGoal.step(15, -1))
        assertEquals(ReadingGoal.MIN, ReadingGoal.step(ReadingGoal.MIN, -1))
        assertEquals(ReadingGoal.MAX, ReadingGoal.step(ReadingGoal.MAX, 1))
        assertEquals(ReadingGoal.MIN, ReadingGoal.clamp(-5))
        assertEquals(ReadingGoal.MAX, ReadingGoal.clamp(9999))
        assertEquals(15, ReadingGoal.DEFAULT)
    }

    @Test fun coversToCopyAreTheOnesMissingThatTheSourceHas() {
        val a = "/sdcard/Books/a.pdf"; val c = "/storage/emulated/0/Books/c.pdf"; val d = "/sdcard/Books/d.pdf"
        fun name(p: String) = ReadingStats.coverKey(p) + ".jpg"
        val available = setOf(name(a), name(c), "stray.jpg")
        val have = setOf(name(a))
        // a is already there, c is available (the same key for /storage/emulated/0 and /sdcard), d has no cover anywhere.
        assertEquals(listOf(name(c)), ReadingCovers.toCopy(listOf(a, c, d, c), have, available))
        assertTrue(ReadingCovers.toCopy(emptyList(), have, available).isEmpty())
    }

    @Test fun coversAreCopiedWithoutOverwriting() {
        val root = kotlin.io.path.createTempDirectory("covers").toFile()
        try {
            val src = File(root, "src").apply { mkdirs() }
            val dest = File(root, "dest")            // does not exist yet
            File(src, "a.jpg").writeText("new"); File(src, "b.jpg").writeText("b")
            assertEquals(2, ReadingCovers.copy(src, dest, listOf("a.jpg", "b.jpg")))
            File(src, "a.jpg").writeText("changed")
            assertEquals(0, ReadingCovers.copy(src, dest, listOf("a.jpg")))  // exists: skipped, no crash
            assertEquals("new", File(dest, "a.jpg").readText())
            assertEquals(0, ReadingCovers.copy(src, dest, listOf("missing.jpg")))
            assertEquals(0, ReadingCovers.copy(src, dest, emptyList()))
        } finally {
            root.deleteRecursively()
        }
    }

    @Test fun wordOfTheDayIsStableForADayAndChangesNextDay() {
        val d = LocalDate.of(2026, 10, 31)
        assertEquals(ReadingWords.of(d), ReadingWords.of(d))
        assertTrue(ReadingWords.of(d) != ReadingWords.of(d.plusDays(1)))
        assertEquals(ReadingWords.of(d), ReadingWords.of(d.plusDays(ReadingWords.WORDS.size.toLong())))
        // Dates before 1970 still land in the list.
        assertTrue(ReadingWords.of(LocalDate.of(1960, 1, 1)) in ReadingWords.WORDS)
    }

    @Test fun wordsHaveMeaningsAndAreUnique() {
        assertTrue(ReadingWords.WORDS.size >= 30)
        assertTrue(ReadingWords.WORDS.all { it.word.isNotBlank() && it.meaning.length > 5 })
        assertEquals(ReadingWords.WORDS.size, ReadingWords.WORDS.map { it.word }.distinct().size)
    }

    @Test fun lastReadIsAPlainPhrase() {
        val zone = ZoneId.of("Europe/Istanbul")
        fun ms(m: Int, d: Int, h: Int = 12) = ZonedDateTime.of(2026, m, d, h, 0, 0, 0, zone).toInstant().toEpochMilli()
        val now = ms(10, 31)
        assertEquals("Never", ReadingText.lastRead(null, now, zone))
        assertEquals("Never", ReadingText.lastRead(0L, now, zone))
        assertEquals("Today", ReadingText.lastRead(ms(10, 31, 9), now, zone))
        assertEquals("Yesterday", ReadingText.lastRead(ms(10, 30), now, zone))
        assertEquals("5 days ago", ReadingText.lastRead(ms(10, 26), now, zone))
        assertEquals("2 weeks ago", ReadingText.lastRead(ms(10, 15), now, zone))
        assertEquals("1 Aug 2026", ReadingText.lastRead(ms(8, 1), now, zone))
    }
}

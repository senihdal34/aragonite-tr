package com.ethran.notable.io

import org.junit.Test
import java.nio.file.Files
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HwrWordCorrectorTest {
    private val glossary = HwrGlossary(
        listOf("Rasyonet", "Papara", "Innovance", "Cronus", "Vodafone", "Turkcell", "LinkedIn", "Şirket", "BIST")
    )

    private fun fix(label: String, vararg candidates: String) =
        HwrWordCorrector.correctWord(HwrWord(label, candidates.toList()), glossary)

    @Test fun `one wrong letter is fixed from the glossary`() = assertEquals("Rasyonet", fix("Rasyonel"))
    @Test fun `candidate from the engine wins over a wrong label`() = assertEquals("Papara", fix("Rapara", "Papara", "Rapera"))
    @Test fun `case and Turkish accents are ignored`() {
        assertEquals("Şirket", fix("sirket"))
        assertEquals("LinkedIn", fix("linkedın"))
    }
    @Test fun `turkish suffix after apostrophe is kept`() = assertEquals("Cronus'a", fix("Cronus'a"))
    @Test fun `stem is corrected and suffix kept`() = assertEquals("Cronus'a", fix("Cronas'a"))
    @Test fun `punctuation around the word is kept`() = assertEquals("(Rasyonet),", fix("(Rasyonel),"))
    @Test fun `all caps stays all caps`() = assertEquals("BIST", fix("B1ST", "BIST"))
    @Test fun `ordinary words and numbers are left alone`() {
        assertEquals("hesapları", fix("hesapları", "hesaplari", "hesaplan"))
        assertEquals("27200", fix("27200"))
        assertEquals("ile", fix("ile"))
    }
    @Test fun `glossary word with a turkish suffix is not shortened`() {
        assertEquals("Papara'dan", fix("Papara'dan"))
        assertEquals("Papara", fix("Papara"))
        val g = HwrGlossary(listOf("Windows"))
        assertEquals("Windowsta", HwrWordCorrector.correctWord(HwrWord("Windowsta"), g))
    }
    @Test fun `two glossary words equally close means no change`() {
        val g = HwrGlossary(listOf("Tarama", "Parama"))
        assertEquals("Xarama", HwrWordCorrector.correctWord(HwrWord("Xarama"), g))
    }
    @Test fun `distance two needs the same first letter`() {
        assertEquals("Innovance", fix("Inrovanse"))        // 2 edits, same first letter, long word
        assertEquals("Onrovanse", fix("Onrovanse"))        // first letter differs: untouched
        assertEquals("Vodefane", fix("Vodefane"))          // short word: only 1 edit allowed
    }
    @Test fun `empty glossary changes nothing`() =
        assertEquals("Rasyonel", HwrWordCorrector.correctWord(HwrWord("Rasyonel"), HwrGlossary(emptyList())))

    @Test fun `rebuild keeps spaces and line breaks`() {
        val words = listOf(HwrWord("Rasyonel"), HwrWord(" "), HwrWord("ile"), HwrWord("\n"), HwrWord("Papara"))
        assertEquals("Rasyonet ile\nPapara", HwrWordCorrector.rebuild(words, glossary))
    }

    @Test fun `loader reads glossary file, note titles and tags`() {
        val dir = Files.createTempDirectory("inbox").toFile()
        try {
            HwrGlossaryLoader.ensureDefaultFile(dir)
            java.io.File(dir, "Yeni Finans Planı-2026-09-28-11-26-29.md")
                .writeText("---\ntags:\n  - rasyonetplan\n---\nx")
            java.io.File(dir, HwrGlossaryLoader.FILE_NAME).appendText("Kalemsoft # sirket adı\n")
            val g = HwrGlossaryLoader.load(dir)
            assertEquals("Papara", g.exact("papara"))
            assertEquals("Kalemsoft", g.exact("kalemsoft"))
            assertEquals("Finans", g.exact("finans"))
            assertEquals("rasyonetplan", g.exact("rasyonetplan"))
            assertTrue(g.size > 20)
        } finally { dir.deleteRecursively() }
    }
}

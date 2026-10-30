package com.ethran.notable.io

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class HwrGlossaryLoaderTest {
    private fun dir() = Files.createTempDirectory("glossary").toFile().also { it.deleteOnExit() }

    @Test fun multiWordLineAddsEachWord() {
        val d = dir()
        File(d, "glossary.txt").writeText("# firmalar\nDeus Minds   # ekip\nTradesoft\n")
        val g = HwrGlossaryLoader.load(d)
        assertEquals("Deus", g.exact("deus"))
        assertEquals("Minds", g.exact("MINDS"))
        assertEquals("Tradesoft", g.exact("tradesoft"))
    }

    @Test fun starterFileKnowsCompaniesAndDevTerms() {
        val d = dir()
        HwrGlossaryLoader.ensureDefaultFile(d)
        val g = HwrGlossaryLoader.load(d)
        for (w in listOf("Tradesoft", "Papara", "Edgecase", "Deus", "Minds", "deployment", "backend", "Kubernetes")) {
            assertTrue("$w missing", g.exact(w) != null)
        }
        assertNull("common Turkish word must not be rewritten", g.exact("git"))
        assertEquals("Kubernetes", HwrWordCorrector.correctWord(HwrWord("Kuberentes"), g))
    }
}

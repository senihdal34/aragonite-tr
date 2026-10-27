package com.ethran.notable.io

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ExportedExcerptsTest {
    private fun md(body: String) = "---\ncreated: \"[[2026-10-12]]\"\npages: 1\nsource: aragonite\n---\n\n$body\n\n---\n![page 1](jpg_archive/x.jpg)\n"

    @Test fun `headline is dropped and following lines are joined`() =
        assertEquals("senaryo ve senaryo aralıkları gereksiz.", ExportedExcerpts.excerptFrom(md("THY Rapor\nsenaryo ve senaryo\naralıkları gereksiz.")).replace("  ", " "))

    @Test fun `strike marks and wiki brackets are removed`() =
        assertEquals("Vekil hesapları [x] Cronus", ExportedExcerpts.excerptFrom(md("Gün 1\n~~Vekil hesapları~~\n[x] [[Cronus]]")).replace("[x]", "[x]"))

    @Test fun `title only note has no excerpt`() =
        assertEquals("", ExportedExcerpts.excerptFrom(md("THY Rapor")))

    @Test fun `image trailer is never part of the excerpt`() =
        assertTrue(!ExportedExcerpts.excerptFrom(md("Başlık\nbir iki üç")).contains("jpg"))

    @Test fun `long text is cut with an ellipsis`() {
        val long = (1..60).joinToString(" ") { "kelime$it" }
        val e = ExportedExcerpts.excerptFrom(md("Başlık\n$long"))
        assertTrue(e.endsWith("…") && e.length <= 171)
    }

    @Test fun `unrecognized placeholder is ignored`() =
        assertEquals("", ExportedExcerpts.excerptFrom(md("_Handwriting could not be recognized. See the page image below._")))
}

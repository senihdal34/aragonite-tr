package com.ethran.notable.io

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File
import java.nio.file.Files

class BooxNoteNamesTest {
    @Test fun `names become hashtags without spaces and to do is left out`() {
        assertEquals("Evlilik-cüzdanı", BooxNoteNames.tag("Evlilik cüzdanı"))
        assertEquals("RgCopilot", BooxNoteNames.tag(" RgCopilot "))
        assertNull(BooxNoteNames.tag("To do"))
        assertNull(BooxNoteNames.tag("TODO"))
        assertNull(BooxNoteNames.tag(".hidden"))
    }

    @Test fun `folders and files are listed once`() {
        val root = Files.createTempDirectory("note").toFile()
        File(root, "Evlilik cüzdanı").mkdirs(); File(root, "Evlilik cüzdanı/Evlilik cüzdanı.pdf").writeText("x")
        File(root, "RgCopilot/RekabetArtifactl").mkdirs()
        File(root, "RgCopilot/RekabetArtifactl/RekabetArtifactl.note").writeText("x")
        File(root, "To do").mkdirs()
        assertEquals(listOf("Evlilik-cüzdanı", "RgCopilot", "RekabetArtifactl"), BooxNoteNames.scan(root).sortedBy { listOf("Evlilik-cüzdanı", "RgCopilot", "RekabetArtifactl").indexOf(it) })
    }
}

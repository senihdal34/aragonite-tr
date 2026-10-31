package com.ethran.notable.reading.chapters

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ChapterRepositoryTest {
    @get:Rule val tmp = TemporaryFolder()

    private val found = ChapterResult.Found(
        ChapterMap(ChapterSource.Pdf, 120, listOf(Chapter("One", 1, 0.0, 1), Chapter("Two Ç", 2, 0.5, 61)))
    )

    private class Counting(val result: ChapterResult) : (File) -> ChapterResult {
        var calls = 0
        override fun invoke(f: File): ChapterResult { calls++; return result }
    }

    private fun book(name: String = "b.pdf") = tmp.newFile(name).apply { writeText("content") }

    @Test fun aBookIsReadOnceAndThenComesFromMemoryAndDisk() {
        val dir = File(tmp.root, "cache")
        val reader = Counting(found)
        val file = book()
        val repo = ChapterRepository(dir, reader)
        assertEquals(found, repo.load(file))
        assertEquals(found, repo.load(file))
        assertEquals(1, reader.calls)
        // A new repository (a new process) reads the JSON, not the book.
        val again = ChapterRepository(dir, reader)
        assertEquals(found, again.load(file))
        assertEquals(1, reader.calls)
        assertEquals(1, dir.listFiles { f -> f.name.endsWith(".json") }?.size)
    }

    @Test fun aChangedFileIsReadAgain() {
        val reader = Counting(found)
        val repo = ChapterRepository(File(tmp.root, "cache"), reader)
        val file = book()
        repo.load(file)
        file.writeText("different and longer content")
        repo.load(file)
        assertEquals(2, reader.calls)
        file.setLastModified(file.lastModified() + 5_000)
        repo.load(file)
        assertEquals(3, reader.calls)
    }

    @Test fun noChaptersIsRememberedToo() {
        val dir = File(tmp.root, "cache")
        for (reason in NoChapters.entries) {
            val reader = Counting(ChapterResult.None(reason))
            val file = book("${reason.name}.pdf")
            assertEquals(ChapterResult.None(reason), ChapterRepository(dir, reader).load(file))
            assertEquals(ChapterResult.None(reason), ChapterRepository(dir, reader).load(file))
            assertEquals(reason.name, 1, reader.calls)
        }
    }

    @Test fun aDamagedCacheFileIsReadFromTheBookAgain() {
        val dir = File(tmp.root, "cache")
        val reader = Counting(found)
        val file = book()
        ChapterRepository(dir, reader).load(file)
        dir.listFiles { f -> f.name.endsWith(".json") }?.forEach { it.writeText("{ not json") }
        assertEquals(found, ChapterRepository(dir, reader).load(file))
        assertEquals(2, reader.calls)
    }

    @Test fun aCacheFromAnotherVersionOrFileIsIgnored() {
        val dir = File(tmp.root, "cache")
        val reader = Counting(found)
        val file = book()
        ChapterRepository(dir, reader).load(file)
        dir.listFiles { f -> f.name.endsWith(".json") }?.forEach { it.writeText(it.readText().replace("\"version\":${ChapterRepository.VERSION}", "\"version\":0")) }
        ChapterRepository(dir, reader).load(file)
        assertEquals(2, reader.calls)
    }

    @Test fun aReaderThatThrowsGivesUnreadableNotACrash() {
        val repo = ChapterRepository(File(tmp.root, "cache")) { error("boom") }
        assertEquals(ChapterResult.None(NoChapters.Unreadable), repo.load(book()))
    }

    @Test fun anUnwritableCacheFolderOnlyCostsTheRereading() {
        val blocker = tmp.newFile("not-a-dir")
        val reader = Counting(found)
        val repo = ChapterRepository(File(blocker, "cache"), reader)
        assertEquals(found, repo.load(book()))
    }

    @Test fun oldCacheFilesArePruned() {
        val dir = File(tmp.root, "cache")
        val repo = ChapterRepository(dir, Counting(found))
        repeat(ChapterRepository.KEEP + 10) { repo.load(book("book$it.pdf")) }
        assertTrue((dir.listFiles { f -> f.name.endsWith(".json") }?.size ?: 0) <= ChapterRepository.KEEP)
    }

    @Test fun stateOfMapsResultsToBoardStates() {
        assertEquals(ChapterState.Ready("id", (found).map), ChapterRepository.stateOf("id", found))
        assertEquals(ChapterState.Empty("id", NoChapters.NoOutline), ChapterRepository.stateOf("id", ChapterResult.None(NoChapters.NoOutline)))
        assertEquals("id", ChapterState.Loading("id").bookIdOrNull)
        assertEquals(null, ChapterState.Idle.bookIdOrNull)
    }
}

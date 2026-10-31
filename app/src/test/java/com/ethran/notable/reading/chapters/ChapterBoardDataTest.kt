package com.ethran.notable.reading.chapters

import com.ethran.notable.reading.RBook
import com.ethran.notable.reading.ReadingData
import com.ethran.notable.reading.board.BoardData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** The Next stop box's data as the board derives it: the book on top, the chapters read from its file, the reader's progress. */
class ChapterBoardDataTest {
    private val today = LocalDate.of(2026, 10, 31)

    private fun book(progress: Double, pages: Int, left: Int?) =
        RBook("/sdcard/Books/a.pdf", "/sdcard/Books/a.pdf", "A Book", listOf("Someone"), progress = progress, pages = pages, minutesLeft = left)

    private val pdfMap = ChapterMap(ChapterSource.Pdf, 235, List(8) { Chapter("Chapter ${it + 1}", 1, it * 30 / 235.0, it * 30 + 1) })

    private fun data(b: RBook?) = ReadingData.Empty.copy(continueReading = listOfNotNull(b))

    @Test fun theLineComesFromTheChaptersOfTheBookOnTop() {
        val b = book(progress = 100.0 * 61 / 235, pages = 235, left = 90)         // NeoReader says "61/235"
        val bd = BoardData(data(b), today, chapters = ChapterState.Ready(b.id, pdfMap))
        val line = requireNotNull(bd.chapterLine)
        assertEquals(2, line.cur)                       // page 61 is in the chapter that starts on page 61
        assertEquals(3, line.next)
        assertEquals(90, line.minutesLeft)
        assertEquals(false, line.approximate)
    }

    @Test fun noLineWhileLoadingOrWithoutChaptersOrForAnotherBook() {
        val b = book(25.0, 235, 90)
        assertNull(BoardData(data(b), today).chapterLine)
        assertNull(BoardData(data(b), today, chapters = ChapterState.Loading(b.id)).chapterLine)
        assertNull(BoardData(data(b), today, chapters = ChapterState.Empty(b.id, NoChapters.NoOutline)).chapterLine)
        assertNull(BoardData(data(b), today, chapters = ChapterState.Ready("some other book", pdfMap)).chapterLine)
        assertNull(BoardData(data(null), today, chapters = ChapterState.Ready(b.id, pdfMap)).chapterLine)
    }

    @Test fun tooFewChaptersGiveNoLine() {
        val b = book(25.0, 235, 90)
        val one = ChapterMap(ChapterSource.Epub, 0, listOf(Chapter("Only", 1, 0.0)))
        assertNull(BoardData(data(b), today, chapters = ChapterState.Ready(b.id, one)).chapterLine)
    }

    @Test fun anEpubLineIsMarkedApproximate() {
        val b = book(40.0, 300, 60)
        val epub = ChapterMap(ChapterSource.Epub, 0, List(8) { Chapter("C$it", 1, it / 8.0) })
        val line = requireNotNull(BoardData(data(b), today, chapters = ChapterState.Ready(b.id, epub)).chapterLine)
        assertTrue(line.approximate)
        assertEquals(3, line.cur)                       // 40% is past the stop at 37.5%
    }
}

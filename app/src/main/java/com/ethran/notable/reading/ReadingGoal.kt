package com.ethran.notable.reading

import java.io.File

/** The yearly book goal. Pure helpers, so they are unit tested. */
object ReadingGoal {
    const val DEFAULT = 15
    const val MIN = 1
    const val MAX = 200

    fun clamp(goal: Int): Int = goal.coerceIn(MIN, MAX)

    /** The goal after pressing − or +. */
    fun step(goal: Int, delta: Int): Int = clamp(goal + delta)
}

/** Copying book covers made earlier (by the KOReader board) into the board's own folder. */
object ReadingCovers {
    /**
     * File names to copy into the board's cover folder: one `<md5>.jpg` per book that the cover source has and the
     * board's folder lacks. Each name is returned once.
     */
    fun toCopy(bookPaths: List<String>, have: Set<String>, available: Set<String>): List<String> =
        bookPaths.map { ReadingStats.coverKey(it) + ".jpg" }.distinct().filter { it in available && it !in have }

    /** Copies [names] from [src] to [dest], never overwriting. Returns how many were copied; a failed file is skipped. */
    fun copy(src: File, dest: File, names: List<String>): Int {
        if (names.isEmpty()) return 0
        if (!dest.isDirectory && !dest.mkdirs()) return 0
        return names.count { n -> runCatching { File(src, n).copyTo(File(dest, n), overwrite = false) }.isSuccess }
    }
}

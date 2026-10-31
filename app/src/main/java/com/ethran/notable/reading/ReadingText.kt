package com.ethran.notable.reading

/** Small text helpers of the board, kept apart from the UI so they are unit tested. */
object ReadingText {
    fun greeting(hour: Int, name: String): String {
        val part = when (hour) {
            in 5..11 -> "Good morning"
            in 12..16 -> "Good afternoon"
            in 17..21 -> "Good evening"
            else -> "Good night"
        }
        return if (name.isBlank()) part else "$part, $name"
    }

    /** "<1m", "45m", "1h 20m", "2h". */
    fun minutes(m: Int): String = when {
        m <= 0 -> "0m"
        m < 60 -> "${m}m"
        m % 60 == 0 -> "${m / 60}h"
        else -> "${m / 60}h ${m % 60}m"
    }

    /** Books a reader with [goal] books a year should have finished by [dayOfYear] of [daysInYear]. */
    fun expectedByNow(goal: Int, dayOfYear: Int, daysInYear: Int): Double = goal * dayOfYear.toDouble() / daysInYear

    /** "On pace", "2 books ahead", "1 book behind". */
    fun pace(finished: Int, goal: Int, dayOfYear: Int, daysInYear: Int): String {
        if (finished >= goal) return "Goal reached"
        val diff = finished - expectedByNow(goal, dayOfYear, daysInYear)
        val n = kotlin.math.abs(Math.round(diff)).toInt()
        return when {
            n == 0 -> "On pace"
            diff > 0 -> "$n ${if (n == 1) "book" else "books"} ahead"
            else -> "$n ${if (n == 1) "book" else "books"} behind"
        }
    }
}

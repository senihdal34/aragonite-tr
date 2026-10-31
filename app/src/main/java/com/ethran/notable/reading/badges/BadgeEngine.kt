package com.ethran.notable.reading.badges

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale

/**
 * The badge rules: what each badge counts, when it is earned, which one is next and what to say about it. Pure and
 * clock-free (the time comes in as an argument), so all of it is unit tested. The rules follow BookOrbit's evaluators;
 * where NeoReader's log is coarser than BookOrbit's session log, docs/reading-badges.md says how.
 */
object BadgeEngine {
    const val DAY_MS = 86_400_000L

    /** Marks that the store has been filled once (so later finds are news and the first fill is backlog). */
    const val INIT_KEY = "__init__"

    private fun count(value: Int, threshold: Int?) = Measure(value.toDouble(), threshold != null && value >= threshold)
    private fun flag(met: Boolean) = Measure(null, met)

    private fun startOfDay(epochDay: Long, zone: ZoneId): Long = LocalDate.ofEpochDay(epochDay).atStartOfDay(zone).toInstant().toEpochMilli()

    /**
     * How far along each badge is and whether its rule holds. [earnedKeys] are the badges already earned for good (the
     * Category Sweeper counts categories among them). The date a badge was earned is given where the log shows it.
     */
    fun measures(f: BadgeFacts, earnedKeys: Set<String> = emptySet(), catalogue: List<BadgeDef> = BadgeCatalogue.ORDERED): Map<String, Measure> {
        val out = LinkedHashMap<String, Measure>()
        for (def in catalogue) {
            if (def.key == "category_sweeper") continue
            out[def.key] = measure(def, f)
        }
        // The sweeper counts the categories the reader has a badge in, apart from itself.
        val earnedNow = earnedKeys + out.filterValues { it.met }.keys
        val categories = catalogue.filter { it.key in earnedNow && it.key != "category_sweeper" }.map { it.category }.toSet().size
        catalogue.firstOrNull { it.key == "category_sweeper" }?.let { out[it.key] = count(categories, it.threshold) }
        return out
    }

    private fun measure(def: BadgeDef, f: BadgeFacts): Measure {
        val t = def.threshold
        return when (def.group) {
            "books_finished" -> count(f.booksFinished, t).withAt(f.finished.getOrNull((t ?: 1) - 1)?.finishedAt)
            "pages_read" -> count(f.pageTurns, t)
            "hours_read" -> {
                val hours = f.totalMinutes / 60.0
                Measure(hours, t != null && hours >= t, if (t != null) hoursReachedAt(f, t) else null)
            }
            "long_book" -> count(f.maxFinishedPages, t).withAt(f.finished.firstOrNull { t != null && it.pages >= t }?.finishedAt)
            "library_builder" -> count(f.libraryCount, t)
            "annotator" -> count(f.highlightCount, t)
            "streak" -> count(f.longestStreak, t).withAt(t?.let { BadgeMetrics.runReachedOn(f.readDays, it) }?.let { startOfDay(it, f.zone) })
            else -> when (def.key) {
                "marathoner" -> count(f.longestSessionMin, t)
                "all_nighter" -> flag(f.readNight)
                "early_bird" -> flag(f.readEarly)
                "sprint" -> flag(f.sprint)
                "binge_reader" -> count(f.bingeMax, t)
                "monthly_reader_2" -> count(f.monthlyMax, t)
                "slow_burn" -> flag(f.slowBurn)
                "power_hour" -> count(f.maxSessionPages, t)
                "speed_reader" -> count(f.maxPagesInDay, t)
                "multi_format" -> count(f.formats, t)
                "curator" -> count(f.folders, t)
                "deep_dive_session" -> count(f.maxHighlightsInDay, t)
                "bookmarked" -> flag(f.highlightCount > 0)
                "author_deep_dive" -> count(f.maxBooksPerAuthor, t)
                "short_story_fan" -> count(f.shortFinished, t)
                "thick_and_thin" -> flag(f.thickAndThin)
                "trilogy_master" -> flag(f.completeSeries)
                "first_series" -> flag(f.firstSeries).withAt(f.finished.firstOrNull { it.series != null }?.finishedAt)
                "year_round_reader" -> count(f.monthsInYear, t)
                "comeback_kid" -> count(f.breakDays, t)
                "seasonal_reader" -> count(f.seasonsInYear, t)
                "perfect_month" -> flag(f.perfectMonth)
                "consistent_reader" -> count(f.consistentRun, t)
                "weekend_rhythm" -> count(f.weekendRun, t)
                "weekend_warrior" -> {
                    val hours = f.weekendMinutes / 60.0
                    Measure(hours, t != null && hours >= t)
                }
                "yearly_finisher_12" -> count(f.yearlyMax, t)
                "new_year_reader" -> flag(f.newYear)
                else -> Measure(null, false)
            }
        }
    }

    private fun Measure.withAt(at: Long?): Measure = if (met && at != null) copy(at = at) else this

    /** The start of the day on which the total minutes read first reached [hours] hours. */
    fun hoursReachedAt(f: BadgeFacts, hours: Int): Long? {
        var sum = 0
        val goal = hours * 60
        for (day in f.readDays) {
            sum += f.minutesByDay[day] ?: 0
            if (sum >= goal) return startOfDay(day, f.zone)
        }
        return null
    }

    /**
     * Brings the stored first-seen dates up to date and returns the board. A badge that holds for the first time is
     * stored with the date the log gives for it, or [now]; one already stored keeps its date whatever the data says now.
     * The first fill of an empty store is marked backlog, so a reader with a long history is not shown a pile of "new".
     */
    fun evaluate(
        f: BadgeFacts, store: BadgeStore, now: Long, catalogue: List<BadgeDef> = BadgeCatalogue.ORDERED
    ): BadgeBoard {
        val stored = store.load()
        val firstFill = INIT_KEY !in stored
        val measures = measures(f, stored.keys, catalogue)
        val updated = LinkedHashMap(stored)
        if (firstFill) updated[INIT_KEY] = EarnedRecord(now, true)
        for (def in catalogue) {
            if (def.key in updated) continue
            val m = measures[def.key] ?: continue
            if (m.met) updated[def.key] = EarnedRecord((m.at ?: now).coerceAtMost(now), backlog = firstFill)
        }
        if (updated != stored) store.save(updated)
        return board(catalogue, measures, updated, now, f.pace)
    }

    /** The board for given measures and stored dates. */
    fun board(
        catalogue: List<BadgeDef>, measures: Map<String, Measure>, stored: Map<String, EarnedRecord>, now: Long, pace: BadgePace?
    ): BadgeBoard {
        val badges = catalogue.map { def ->
            val rec = stored[def.key]
            Badge(def, measures[def.key]?.progress, earned = rec != null, earnedAt = rec?.at, backlog = rec?.backlog == true)
        }
        val next = nextBadge(badges)
        val latest = latestBadge(badges)
        val fresh = latest != null && !latest.backlog && now - (latest.earnedAt ?: 0L) < DAY_MS
        return BadgeBoard(badges, next, latest, fresh, next?.let { etaLabel(it, pace) })
    }

    /**
     * The next badge: of the badges not earned and not secret that have a threshold and a count, the one with the largest
     * share of its threshold done (below a whole); on a tie the first in catalogue order. This is the rule of BookOrbit's
     * dashboard (koreader-catalog.service.ts buildAchievementSummary); a badge with no progress at all still qualifies.
     */
    fun nextBadge(badges: List<Badge>): Badge? {
        var best: Badge? = null
        var bestRatio = -1.0
        for (b in badges) {
            if (b.earned || b.def.hidden) continue
            val t = b.def.threshold ?: continue
            val p = b.progress ?: continue
            if (t <= 0) continue
            val ratio = p / t
            if (ratio < 1 && ratio > bestRatio) { best = b; bestRatio = ratio }
        }
        return best
    }

    /** The badge earned last; on a tie the first in catalogue order. The date marker of the first fill is not a badge. */
    fun latestBadge(badges: List<Badge>): Badge? {
        var best: Badge? = null
        for (b in badges) {
            val at = b.earnedAt ?: continue
            if (!b.earned) continue
            if (best == null || (best.earnedAt ?: 0L) < at) best = b
        }
        return best
    }

    /**
     * "About 3 weeks to go" for a count of days, or null when it is too far off to be worth saying
     * (BoardData.etaLabel).
     */
    fun etaLabel(days: Double?): String? {
        if (days == null || days.isNaN() || days <= 0) return null
        return when {
            days < 1.5 -> "About a day to go"
            days < 10 -> "About ${Math.floor(days + 0.5).toInt()} days to go"
            days < 60 -> Math.floor(days / 7 + 0.5).toInt().let { if (it == 1) "About a week to go" else "About $it weeks to go" }
            days < 730 -> "About ${Math.floor(days / 30 + 0.5).toInt()} months to go"
            else -> null
        }
    }

    /** The time to go for the next badge, from the reader's pace; only the sets the pace can tell (hours, books, pages). */
    fun etaLabel(next: Badge, pace: BadgePace?): String? {
        val t = next.def.threshold ?: return null
        val p = next.progress ?: return null
        val pace0 = pace ?: return null
        val perDay = when (next.def.group) {
            "hours_read" -> pace0.minutesPerDay?.takeIf { it > 0 }?.let { it / 60 }
            "books_finished" -> pace0.booksPerDay?.takeIf { it > 0 }
            "pages_read" -> {
                val min = pace0.minutesPerDay?.takeIf { it > 0 }
                val pph = pace0.pagesPerHour?.takeIf { it > 0 }
                if (min != null && pph != null) min / 60 * pph else null
            }
            else -> null
        } ?: return null
        val left = t - p
        return if (left > 0) etaLabel(left / perDay) else null
    }

    /** "Earned today", "Earned yesterday", "Earned Monday" within a week, then "Earned 12 Oct" (BoardData.earnedLabel). */
    fun earnedLabel(at: Long?, now: Long, zone: ZoneId): String? {
        if (at == null) return null
        val day = Instant.ofEpochMilli(at).atZone(zone).toLocalDate()
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val days = today.toEpochDay() - day.toEpochDay()
        return when {
            days <= 0 -> "Earned today"
            days == 1L -> "Earned yesterday"
            days < 7 -> "Earned ${day.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.ENGLISH)}"
            else -> "Earned ${day.dayOfMonth} ${day.month.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)}"
        }
    }

    /** What the list screen groups: earned (newest first), in progress (closest first) and the rest. */
    data class Sections(val earned: List<Badge>, val inProgress: List<Badge>, val locked: List<Badge>)

    /**
     * Earned badges newest first; then each set's next tier (and each single badge) that has some progress, closest
     * first; everything else that is not earned is locked. Secret badges are always locked.
     */
    fun sections(badges: List<Badge>): Sections {
        val earned = badges.filter { it.earned }.sortedWith(compareByDescending<Badge> { it.earnedAt ?: 0L }.thenBy { badges.indexOf(it) })
        val nextTier = HashSet<String>()
        val seenGroups = HashSet<String>()
        for (b in badges) {
            if (b.earned) continue
            val g = b.def.group
            if (g == null) nextTier += b.key else if (seenGroups.add(g)) nextTier += b.key
        }
        val unearned = badges.filter { !it.earned }
        val inProgress = unearned.filter { it.key in nextTier && !it.secret && (it.progress ?: 0.0) > 0.0 }
            .sortedWith(compareByDescending<Badge> { it.fraction ?: -1.0 }.thenBy { badges.indexOf(it) })
        val locked = unearned.filter { it !in inProgress }
        return Sections(earned, inProgress, locked)
    }
}

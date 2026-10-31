package com.ethran.notable.reading

import com.ethran.notable.reading.badges.Badge
import com.ethran.notable.reading.badges.BadgeCatalogue
import com.ethran.notable.reading.badges.BadgeEngine
import com.ethran.notable.reading.badges.BadgeFacts
import com.ethran.notable.reading.badges.BadgePace
import com.ethran.notable.reading.badges.BadgeCategory
import com.ethran.notable.reading.badges.EarnedRecord
import com.ethran.notable.reading.badges.InMemoryBadgeStore
import com.ethran.notable.reading.badges.Rarity
import com.ethran.notable.reading.board.BoardIcons
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BadgeEngineTest {
    private val zone = ZoneId.of("Europe/Istanbul")
    private val today = LocalDate.of(2026, 10, 31)
    private fun ms(m: Int, d: Int, h: Int = 12) = ZonedDateTime.of(2026, m, d, h, 0, 0, 0, zone).toInstant().toEpochMilli()
    private val now = ms(10, 31, 15)
    private val empty = BadgeFacts.from(ReadingData.Empty, today, zone)
    private fun facts(f: (BadgeFacts) -> BadgeFacts) = f(empty)
    private fun measure(key: String, f: BadgeFacts) = BadgeEngine.measures(f).getValue(key)
    private fun board(f: BadgeFacts, store: InMemoryBadgeStore = InMemoryBadgeStore(), at: Long = now) = BadgeEngine.evaluate(f, store, at)

    // ------------------------------------------------------------------ catalogue
    @Test fun theCatalogueHasFiftySixUniqueBadgesInFourCategories() {
        assertEquals(56, BadgeCatalogue.ALL.size)
        assertEquals(56, BadgeCatalogue.ALL.map { it.key }.toSet().size)
        assertEquals(setOf(BadgeCategory.Reading, BadgeCategory.Library, BadgeCategory.Exploration, BadgeCategory.Dedication), BadgeCatalogue.ALL.map { it.category }.toSet())
    }

    @Test fun tiersOfASetRiseInThresholdAndRarity() {
        for ((group, tiers) in BadgeCatalogue.ALL.filter { it.group != null }.groupBy { it.group }) {
            val sorted = tiers.sortedBy { it.tier }
            assertEquals("$group tiers 1..n", (1..sorted.size).toList(), sorted.map { it.tier })
            assertEquals("$group thresholds rise", sorted.map { it.threshold }.sortedBy { it }, sorted.map { it.threshold })
            assertEquals("$group rarities rise", listOf(Rarity.Common, Rarity.Rare, Rarity.Epic, Rarity.Legendary), sorted.map { it.rarity })
        }
    }

    @Test fun everyBadgeIconIsDrawable() {
        for (def in BadgeCatalogue.ALL) assertTrue("${def.key} icon ${def.icon}", BoardIcons.has(def.icon))
        assertTrue(BoardIcons.has("lock"))
    }

    @Test fun orderedFollowsBookOrbitsCategoryOrder() {
        val cats = BadgeCatalogue.ORDERED.map { it.category.ordinal }
        assertEquals(cats.sorted(), cats)
        assertEquals("books_finished_1", BadgeCatalogue.ORDERED.first().key)
        assertEquals("category_sweeper", BadgeCatalogue.ORDERED.last().key)
    }

    @Test fun threeBadgesAreSecretsUntilEarned() {
        assertEquals(setOf("all_nighter", "early_bird", "new_year_reader"), BadgeCatalogue.ALL.filter { it.hidden }.map { it.key }.toSet())
    }

    @Test fun rarityFamiliesAreTheSameInEveryScheme() {
        assertEquals(listOf("green", "blue", "violet", "gold"), Rarity.entries.map { it.family })
        assertEquals(Rarity.Common, Rarity.of("mythic"))
        assertEquals(Rarity.Epic, Rarity.of("EPIC"))
        assertEquals(Rarity.Common, Rarity.of(null))
    }

    // ------------------------------------------------------------------ thresholds
    @Test fun booksFinishedEarnsAtTheThresholdNotBefore() {
        val book = RBook("b", "/p", "t", emptyList(), finishedAt = 1L)
        fun with(n: Int) = facts { it.copy(booksFinished = n, finished = List(n) { book }) }
        assertFalse(measure("books_finished_1", with(0)).met)
        assertTrue(measure("books_finished_1", with(1)).met)
        assertFalse(measure("books_finished_2", with(9)).met)
        assertTrue(measure("books_finished_2", with(10)).met)
        assertEquals(9.0, measure("books_finished_2", with(9)).progress!!, 0.0)
        assertTrue(measure("books_finished_4", with(100)).met)
    }

    @Test fun booksFinishedEarnedDateIsTheDateOfTheNthBook() {
        val books = listOf(5L, 3L, 9L).sorted().map { RBook("b$it", "/p", "t", emptyList(), finishedAt = it * 1000) }
        val f = facts { it.copy(booksFinished = 3, finished = books) }
        assertEquals(3000L, measure("books_finished_1", f).at)
        assertNull(measure("books_finished_2", f).at)          // not earned: no date
    }

    @Test fun hoursAreMinutesOverSixtyAndTheDateIsTheDayTheyPassedTheMark() {
        val d1 = LocalDate.of(2026, 10, 1).toEpochDay(); val d2 = d1 + 1
        val f = facts { it.copy(totalMinutes = 599, readDays = listOf(d1, d2), minutesByDay = mapOf(d1 to 300, d2 to 299)) }
        assertFalse(measure("hours_read_1", f.copy(totalMinutes = 599)).met)
        assertEquals(599 / 60.0, measure("hours_read_1", f).progress!!, 1e-9)
        val g = f.copy(totalMinutes = 600, minutesByDay = mapOf(d1 to 300, d2 to 300))
        assertTrue(measure("hours_read_1", g).met)
        assertEquals(LocalDate.ofEpochDay(d2).atStartOfDay(zone).toInstant().toEpochMilli(), measure("hours_read_1", g).at)
    }

    @Test fun pagesComeFromPageTurns() {
        assertFalse(measure("pages_read_1", facts { it.copy(pageTurns = 499) }).met)
        assertTrue(measure("pages_read_1", facts { it.copy(pageTurns = 500) }).met)
        assertTrue(measure("pages_read_2", facts { it.copy(pageTurns = 5000) }).met)
    }

    @Test fun longBookUsesTheLongestFinishedBook() {
        val big = RBook("b", "/p", "t", emptyList(), pages = 520, finishedAt = 7000L)
        val f = facts { it.copy(maxFinishedPages = 520, finished = listOf(big)) }
        assertTrue(measure("long_book_1", f).met); assertTrue(measure("page_turner", f).met)
        assertFalse(measure("long_book_3", f).met)
        assertEquals(7000L, measure("page_turner", f).at)
    }

    @Test fun streakTiersUseTheLongestRunAndDateItsDay() {
        val days = (1L..8L).toList()
        val f = facts { it.copy(longestStreak = 8, readDays = days) }
        assertTrue(measure("streak_1", f).met)
        assertFalse(measure("streak_2", f).met)
        assertEquals(LocalDate.ofEpochDay(7).atStartOfDay(zone).toInstant().toEpochMilli(), measure("streak_1", f).at)
    }

    @Test fun sessionAndDayRecords() {
        assertFalse(measure("marathoner", facts { it.copy(longestSessionMin = 89) }).met)
        assertTrue(measure("marathoner", facts { it.copy(longestSessionMin = 90) }).met)
        assertTrue(measure("power_hour", facts { it.copy(maxSessionPages = 75) }).met)
        assertFalse(measure("power_hour", facts { it.copy(maxSessionPages = 74) }).met)
        assertTrue(measure("speed_reader", facts { it.copy(maxPagesInDay = 100) }).met)
        assertFalse(measure("speed_reader", facts { it.copy(maxPagesInDay = 99) }).met)
    }

    @Test fun flagBadgesHaveNoCount() {
        val m = measure("sprint", facts { it.copy(sprint = true) })
        assertTrue(m.met); assertNull(m.progress)
        assertFalse(measure("sprint", empty).met)
        assertTrue(measure("all_nighter", facts { it.copy(readNight = true) }).met)
        assertTrue(measure("early_bird", facts { it.copy(readEarly = true) }).met)
        assertTrue(measure("bookmarked", facts { it.copy(highlightCount = 1) }).met)
        assertFalse(measure("bookmarked", empty).met)
        assertTrue(measure("perfect_month", facts { it.copy(perfectMonth = true) }).met)
        assertTrue(measure("new_year_reader", facts { it.copy(newYear = true) }).met)
    }

    @Test fun libraryAndNoteCounts() {
        assertTrue(measure("library_builder_1", facts { it.copy(libraryCount = 50) }).met)
        assertFalse(measure("library_builder_1", facts { it.copy(libraryCount = 49) }).met)
        assertTrue(measure("annotator_1", facts { it.copy(highlightCount = 10) }).met)
        assertFalse(measure("annotator_2", facts { it.copy(highlightCount = 49) }).met)
        assertTrue(measure("deep_dive_session", facts { it.copy(maxHighlightsInDay = 10) }).met)
        assertTrue(measure("multi_format", facts { it.copy(formats = 3) }).met)
        assertTrue(measure("curator", facts { it.copy(folders = 10) }).met)
    }

    @Test fun explorationAndDedicationCounts() {
        assertTrue(measure("author_deep_dive", facts { it.copy(maxBooksPerAuthor = 5) }).met)
        assertFalse(measure("short_story_fan", facts { it.copy(shortFinished = 4) }).met)
        assertTrue(measure("thick_and_thin", facts { it.copy(thickAndThin = true) }).met)
        assertTrue(measure("trilogy_master", facts { it.copy(completeSeries = true) }).met)
        assertTrue(measure("first_series", facts { it.copy(firstSeries = true) }).met)
        assertTrue(measure("year_round_reader", facts { it.copy(monthsInYear = 12) }).met)
        assertFalse(measure("year_round_reader", facts { it.copy(monthsInYear = 11) }).met)
        assertTrue(measure("comeback_kid", facts { it.copy(breakDays = 30) }).met)
        assertTrue(measure("seasonal_reader", facts { it.copy(seasonsInYear = 4) }).met)
        assertTrue(measure("consistent_reader", facts { it.copy(consistentRun = 14) }).met)
        assertTrue(measure("weekend_rhythm", facts { it.copy(weekendRun = 8) }).met)
        assertTrue(measure("yearly_finisher_12", facts { it.copy(yearlyMax = 12) }).met)
        assertTrue(measure("binge_reader", facts { it.copy(bingeMax = 3) }).met)
        assertTrue(measure("monthly_reader_2", facts { it.copy(monthlyMax = 10) }).met)
        assertTrue(measure("slow_burn", facts { it.copy(slowBurn = true) }).met)
    }

    @Test fun weekendWarriorCountsHoursNotMinutes() {
        val m = measure("weekend_warrior", facts { it.copy(weekendMinutes = 299) })
        assertFalse(m.met); assertEquals(299 / 60.0, m.progress!!, 1e-9)
        assertTrue(measure("weekend_warrior", facts { it.copy(weekendMinutes = 300) }).met)
    }

    @Test fun categorySweeperCountsCategoriesOfEarnedBadgesAndIsMetAtFour() {
        val oneEach = setOf("books_finished_1", "annotator_1", "first_series", "streak_1")
        val m = BadgeEngine.measures(empty, oneEach).getValue("category_sweeper")
        assertTrue(m.met); assertEquals(4.0, m.progress!!, 0.0)
        val three = BadgeEngine.measures(empty, setOf("books_finished_1", "books_finished_2", "annotator_1", "first_series")).getValue("category_sweeper")
        assertFalse(three.met); assertEquals(3.0, three.progress!!, 0.0)
        // Badges met by today's data count as well, and the sweeper does not count itself.
        val live = BadgeEngine.measures(facts { it.copy(booksFinished = 1, finished = listOf(RBook("b", "/p", "t", emptyList(), finishedAt = 1L)), highlightCount = 10, firstSeries = true, longestStreak = 7) })
        assertTrue(live.getValue("category_sweeper").met)
        assertEquals(0.0, BadgeEngine.measures(empty).getValue("category_sweeper").progress!!, 0.0)
    }

    // ------------------------------------------------------------------ next badge
    private fun badges(f: BadgeFacts, earned: Set<String> = emptySet()): List<Badge> {
        val measures = BadgeEngine.measures(f, earned)
        return BadgeCatalogue.ORDERED.map { Badge(it, measures[it.key]?.progress, it.key in earned, if (it.key in earned) 1L else null) }
    }

    @Test fun nextIsTheLargestShareOfItsThreshold() {
        // 4 of 10 books (rare tier 2 is next at 40%), 200 of 500 pages (40%): books come first in the list.
        val f = facts { it.copy(booksFinished = 4, pageTurns = 200, finished = List(4) { RBook("b$it", "/p", "t", emptyList(), finishedAt = 1L) }) }
        val earned = setOf("books_finished_1")
        assertEquals("books_finished_2", BadgeEngine.nextBadge(badges(f, earned))?.key)
        val g = f.copy(pageTurns = 450)
        assertEquals("pages_read_1", BadgeEngine.nextBadge(badges(g, earned))?.key)    // 90% beats 40%
    }

    @Test fun nextTieGoesToTheFirstInCatalogueOrder() {
        val f = facts { it.copy(booksFinished = 0, pageTurns = 0) }
        // Everything is at zero: the first badge with a count and a threshold wins.
        assertEquals("books_finished_1", BadgeEngine.nextBadge(badges(f))?.key)
    }

    @Test fun nextSkipsEarnedSecretFullAndCountlessBadges() {
        val all = BadgeCatalogue.ORDERED.map { Badge(it, 1.0, earned = true, earnedAt = 1L) }
        assertNull(BadgeEngine.nextBadge(all))
        val def = BadgeCatalogue.get("books_finished_1")!!
        assertNull(BadgeEngine.nextBadge(listOf(Badge(def, 1.0, false, null))))      // progress at the threshold is not "close"
        assertNull(BadgeEngine.nextBadge(listOf(Badge(def, null, false, null))))     // no progress known
        val secret = BadgeCatalogue.get("all_nighter")!!
        assertNull(BadgeEngine.nextBadge(listOf(Badge(secret, 0.5, false, null))))
        val flagOnly = BadgeCatalogue.get("sprint")!!
        assertNull(BadgeEngine.nextBadge(listOf(Badge(flagOnly, 0.5, false, null))))
    }

    @Test fun nextPicksTheLowestUnearnedTierOfASet() {
        val f = facts { it.copy(booksFinished = 12, finished = List(12) { RBook("b$it", "/p", "t", emptyList(), finishedAt = it.toLong()) }) }
        val next = BadgeEngine.nextBadge(badges(f, setOf("books_finished_1", "books_finished_2")))
        assertEquals("books_finished_3", next?.key)
        assertEquals(12.0 / 35, next?.fraction ?: 0.0, 1e-9)
    }

    @Test fun latestIsTheMostRecentlyEarnedWithTiesToTheFirstInOrder() {
        val d = BadgeCatalogue.ORDERED
        val list = listOf(
            Badge(d[0], null, true, 100L), Badge(d[1], null, true, 300L), Badge(d[2], null, true, 300L), Badge(d[3], null, false, null)
        )
        assertEquals(d[1].key, BadgeEngine.latestBadge(list)?.key)
        assertNull(BadgeEngine.latestBadge(emptyList()))
        assertNull(BadgeEngine.latestBadge(listOf(Badge(d[3], null, false, null))))
    }

    // ------------------------------------------------------------------ persistence
    @Test fun theFirstFillIsBacklogAndNotNews() {
        val store = InMemoryBadgeStore()
        val b = board(facts { it.copy(highlightCount = 12) })
        assertTrue(b.badges.first { it.key == "annotator_1" }.earned)
        assertTrue(store.load().isEmpty())
        val b2 = board(facts { it.copy(highlightCount = 12) }, store)
        assertTrue(b2.badges.first { it.key == "annotator_1" }.backlog)
        assertFalse(b2.fresh)
        assertTrue(store.records.containsKey(BadgeEngine.INIT_KEY))
    }

    @Test fun aBadgeEarnedLaterIsNewsAndGetsTheTimeItWasSeen() {
        val store = InMemoryBadgeStore()
        board(empty, store)                                          // first fill, nothing earned
        val later = now + 3_600_000L
        val b = board(facts { it.copy(highlightCount = 1) }, store, later)
        val bookmarked = b.badges.first { it.key == "bookmarked" }
        assertTrue(bookmarked.earned); assertFalse(bookmarked.backlog)
        assertEquals(later, bookmarked.earnedAt)
        assertEquals("bookmarked", b.latest?.key)
        assertTrue(b.fresh)
        assertFalse(board(facts { it.copy(highlightCount = 1) }, store, later + BadgeEngine.DAY_MS + 1).fresh)    // a day later it is no longer new
    }

    @Test fun anEarnedDateNeverChangesOrDisappears() {
        val store = InMemoryBadgeStore()
        board(empty, store)
        val first = now
        board(facts { it.copy(highlightCount = 1) }, store, first)
        // The data shrinks (the reader deleted every highlight) and the clock moves on: the badge stays, with its first date.
        val b = board(empty, store, first + 40 * BadgeEngine.DAY_MS)
        val bookmarked = b.badges.first { it.key == "bookmarked" }
        assertTrue(bookmarked.earned)
        assertEquals(first, bookmarked.earnedAt)
        assertEquals(first, store.records.getValue("bookmarked").at)
    }

    @Test fun theDateTheLogGivesIsUsedAndNeverInTheFuture() {
        val store = InMemoryBadgeStore()
        board(empty, store)
        val books = listOf(RBook("b", "/p", "t", emptyList(), finishedAt = ms(9, 1)))
        val b = board(facts { it.copy(booksFinished = 1, finished = books) }, store)
        assertEquals(ms(9, 1), b.badges.first { it.key == "books_finished_1" }.earnedAt)
        // A date after "now" (a clock set back) is cut to now.
        val s2 = InMemoryBadgeStore(); board(empty, s2)
        val future = listOf(RBook("b", "/p", "t", emptyList(), finishedAt = now + 10 * BadgeEngine.DAY_MS))
        assertEquals(now, board(facts { it.copy(booksFinished = 1, finished = future) }, s2).badges.first { it.key == "books_finished_1" }.earnedAt)
    }

    @Test fun nothingIsSavedWhenNothingChanged() {
        val store = InMemoryBadgeStore()
        board(facts { it.copy(highlightCount = 1) }, store)
        val saves = store.saves
        board(facts { it.copy(highlightCount = 1) }, store, now + 1000)
        assertEquals(saves, store.saves)
    }

    @Test fun earnedBadgesFromAnExistingStoreSurvive() {
        val store = InMemoryBadgeStore(mapOf(BadgeEngine.INIT_KEY to EarnedRecord(1L, true), "marathoner" to EarnedRecord(42L)))
        val b = board(empty, store)
        val m = b.badges.first { it.key == "marathoner" }
        assertTrue(m.earned); assertEquals(42L, m.earnedAt)
        assertEquals(1, b.earned)
        assertEquals(56, b.total)
    }

    @Test fun anUnknownStoredKeyIsIgnored() {
        val store = InMemoryBadgeStore(mapOf(BadgeEngine.INIT_KEY to EarnedRecord(1L, true), "retired_badge" to EarnedRecord(5L)))
        assertEquals(0, board(empty, store).earned)
    }

    @Test fun theBoardCarriesNextLatestAndEta() {
        val store = InMemoryBadgeStore()
        board(empty, store)
        val f = facts { it.copy(totalMinutes = 300, pace = BadgePace(60.0, null, null)) }       // 5 of 10 hours, an hour a day
        val b = board(f, store)
        assertEquals("hours_read_1", b.next?.key)
        assertEquals("About 5 days to go", b.eta)
    }

    // ------------------------------------------------------------------ time to go
    @Test fun etaLabelsFollowThePlugin() {
        assertNull(BadgeEngine.etaLabel(null as Double?))
        assertNull(BadgeEngine.etaLabel(0.0)); assertNull(BadgeEngine.etaLabel(Double.NaN)); assertNull(BadgeEngine.etaLabel(-3.0))
        assertEquals("About a day to go", BadgeEngine.etaLabel(1.0))
        assertEquals("About 2 days to go", BadgeEngine.etaLabel(1.5))
        assertEquals("About a week to go", BadgeEngine.etaLabel(10.0))
        assertEquals("About 3 weeks to go", BadgeEngine.etaLabel(21.0))
        assertEquals("About 2 months to go", BadgeEngine.etaLabel(60.0))
        assertEquals("About 12 months to go", BadgeEngine.etaLabel(365.0))
        assertNull(BadgeEngine.etaLabel(730.0))
    }

    @Test fun etaUsesThePaceOfTheBadgesSet() {
        val hours = Badge(BadgeCatalogue.get("hours_read_2")!!, 90.0, false, null)
        assertEquals("About a week to go", BadgeEngine.etaLabel(hours, BadgePace(60.0, null, null)))      // 10 hours at 1 hour a day
        val books = Badge(BadgeCatalogue.get("books_finished_2")!!, 8.0, false, null)
        assertEquals("About 2 days to go", BadgeEngine.etaLabel(books, BadgePace(null, 1.0, null)))
        val pages = Badge(BadgeCatalogue.get("pages_read_2")!!, 4000.0, false, null)
        assertEquals("About 3 weeks to go", BadgeEngine.etaLabel(pages, BadgePace(60.0, null, 50)))    // 1000 pages at 50 a day = 20 days
    }

    @Test fun etaIsNullWithoutPaceOrForOtherSets() {
        val hours = Badge(BadgeCatalogue.get("hours_read_2")!!, 90.0, false, null)
        assertNull(BadgeEngine.etaLabel(hours, null))
        assertNull(BadgeEngine.etaLabel(hours, BadgePace(null, 1.0, 50)))
        assertNull(BadgeEngine.etaLabel(hours, BadgePace(0.0, null, null)))
        val streak = Badge(BadgeCatalogue.get("streak_2")!!, 10.0, false, null)
        assertNull(BadgeEngine.etaLabel(streak, BadgePace(60.0, 1.0, 50)))
        val pagesNoSpeed = Badge(BadgeCatalogue.get("pages_read_2")!!, 4000.0, false, null)
        assertNull(BadgeEngine.etaLabel(pagesNoSpeed, BadgePace(60.0, null, null)))
        val done = Badge(BadgeCatalogue.get("hours_read_2")!!, 100.0, false, null)
        assertNull(BadgeEngine.etaLabel(done, BadgePace(60.0, null, null)))
    }

    // ------------------------------------------------------------------ labels and lists
    @Test fun earnedLabels() {
        val n = ms(10, 31, 15)
        assertNull(BadgeEngine.earnedLabel(null, n, zone))
        assertEquals("Earned today", BadgeEngine.earnedLabel(ms(10, 31, 1), n, zone))
        assertEquals("Earned yesterday", BadgeEngine.earnedLabel(ms(10, 30, 23), n, zone))
        assertEquals("Earned Wednesday", BadgeEngine.earnedLabel(ms(10, 28), n, zone))      // 28 Oct 2026 is a Wednesday, three days back
        assertEquals("Earned 24 Oct", BadgeEngine.earnedLabel(ms(10, 24), n, zone))         // a week or more back: day and month
        assertEquals("Earned 1 Sep", BadgeEngine.earnedLabel(ms(9, 1), n, zone))
    }

    @Test fun sectionsGroupEarnedProgressAndLocked() {
        val f = facts { it.copy(booksFinished = 4, pageTurns = 100, finished = List(4) { RBook("b$it", "/p", "t", emptyList(), finishedAt = 1L) }) }
        val earned = setOf("books_finished_1")
        val measures = BadgeEngine.measures(f, earned)
        val list = BadgeCatalogue.ORDERED.map { Badge(it, measures[it.key]?.progress, it.key in earned, if (it.key in earned) 5L else null) }
        val s = BadgeEngine.sections(list)
        assertEquals(listOf("books_finished_1"), s.earned.map { it.key })
        assertEquals(listOf("books_finished_2", "category_sweeper", "pages_read_1"), s.inProgress.map { it.key })    // 40%, then 1 of 4 categories, then 20%
        assertTrue(s.locked.any { it.key == "books_finished_3" })       // a higher tier of a started set is locked
        assertTrue(s.locked.any { it.key == "all_nighter" })
        assertEquals(list.size, s.earned.size + s.inProgress.size + s.locked.size)
    }

    @Test fun earnedSectionIsNewestFirst() {
        val d = BadgeCatalogue.ORDERED
        val list = listOf(Badge(d[0], null, true, 10L), Badge(d[1], null, true, 30L), Badge(d[2], null, true, 20L))
        assertEquals(listOf(d[1].key, d[2].key, d[0].key), BadgeEngine.sections(list).earned.map { it.key })
    }

    @Test fun aSecretBadgeShowsAMaskUntilEarned() {
        val def = BadgeCatalogue.get("early_bird")!!
        val locked = Badge(def, null, false, null)
        assertEquals(Badge.SECRET_NAME, locked.name); assertEquals("lock", locked.icon); assertEquals(Rarity.Common, locked.rarity)
        assertNull(locked.threshold); assertNull(locked.fraction)
        val earned = Badge(def, null, true, 5L)
        assertEquals("Early Bird", earned.name); assertEquals("sunrise", earned.icon); assertEquals(Rarity.Rare, earned.rarity)
    }

    @Test fun fractionIsClampedAndAbsentWhenEarnedOrCountless() {
        val def = BadgeCatalogue.get("books_finished_2")!!
        assertEquals(0.5, Badge(def, 5.0, false, null).fraction!!, 0.0)
        assertEquals(1.0, Badge(def, 50.0, false, null).fraction!!, 0.0)
        assertNull(Badge(def, 5.0, true, 1L).fraction)
        assertNull(Badge(def, null, false, null).fraction)
        assertNull(Badge(BadgeCatalogue.get("sprint")!!, 1.0, false, null).fraction)
    }
}

package com.ethran.notable.reading.badges

/**
 * The badges this app can award on its own, taken from BookOrbit's achievement catalogue (its server seed, see
 * docs/reading-badges.md): same keys, names, descriptions, icons, rarities, thresholds and order. BookOrbit has 87;
 * the 56 here are the ones NeoReader's reading log can decide. The rest (ratings, genres, languages, publication
 * years, reading devices, abandoned books, notes) need data this app does not have.
 */
object BadgeCatalogue {
    val ALL: List<BadgeDef> = listOf(
        // ---- Reading
        BadgeDef("books_finished_1", "books_finished", 1, BadgeCategory.Reading, "Ink Initiate", "Finish your first book", "book-open", Rarity.Common, 1, false, 1),
        BadgeDef("books_finished_2", "books_finished", 2, BadgeCategory.Reading, "Spine Scout", "Finish 10 books", "book-open", Rarity.Rare, 10, false, 2),
        BadgeDef("books_finished_3", "books_finished", 3, BadgeCategory.Reading, "Story Stalwart", "Finish 35 books", "book-open", Rarity.Epic, 35, false, 3),
        BadgeDef("books_finished_4", "books_finished", 4, BadgeCategory.Reading, "Grand Archivist", "Finish 100 books", "book-open", Rarity.Legendary, 100, false, 4),
        BadgeDef("pages_read_1", "pages_read", 1, BadgeCategory.Reading, "First Leaves", "Read 500 pages", "file-text", Rarity.Common, 500, false, 5),
        BadgeDef("pages_read_2", "pages_read", 2, BadgeCategory.Reading, "Chapter Weaver", "Read 5,000 pages", "file-text", Rarity.Rare, 5000, false, 6),
        BadgeDef("pages_read_3", "pages_read", 3, BadgeCategory.Reading, "Tome Tactician", "Read 25,000 pages", "file-text", Rarity.Epic, 25000, false, 7),
        BadgeDef("pages_read_4", "pages_read", 4, BadgeCategory.Reading, "Grand Lexicon", "Read 100,000 pages", "file-text", Rarity.Legendary, 100000, false, 8),
        BadgeDef("hours_read_1", "hours_read", 1, BadgeCategory.Reading, "Hour Initiate", "Read for 10 hours", "clock", Rarity.Common, 10, false, 9),
        BadgeDef("hours_read_2", "hours_read", 2, BadgeCategory.Reading, "Timebound Reader", "Read for 100 hours", "clock", Rarity.Rare, 100, false, 10),
        BadgeDef("hours_read_3", "hours_read", 3, BadgeCategory.Reading, "Chronicle Veteran", "Read for 500 hours", "clock", Rarity.Epic, 500, false, 11),
        BadgeDef("hours_read_4", "hours_read", 4, BadgeCategory.Reading, "Timeless Scholar", "Read for 1,000 hours", "clock", Rarity.Legendary, 1000, false, 12),
        BadgeDef("marathoner", null, null, BadgeCategory.Reading, "Marathoner", "Complete a reading session of 90 minutes or more", "timer", Rarity.Rare, 90, false, 13),
        BadgeDef("all_nighter", null, null, BadgeCategory.Reading, "All-nighter", "Read between 1am and 4am", "moon", Rarity.Epic, null, true, 14),
        BadgeDef("early_bird", null, null, BadgeCategory.Reading, "Early Bird", "Start a reading session between 5am and 7am", "sunrise", Rarity.Rare, null, true, 15),
        BadgeDef("long_book_1", "long_book", 1, BadgeCategory.Reading, "Story Stretch", "Finish a book with 300 or more pages", "scroll-text", Rarity.Common, 300, false, 16),
        BadgeDef("page_turner", "long_book", 2, BadgeCategory.Reading, "Page Pathfinder", "Finish a book with 500 or more pages", "scroll-text", Rarity.Rare, 500, false, 17),
        BadgeDef("long_book_3", "long_book", 3, BadgeCategory.Reading, "Epic Voyager", "Finish a book with 750 or more pages", "book-marked", Rarity.Epic, 750, false, 18),
        BadgeDef("tome_tamer", "long_book", 4, BadgeCategory.Reading, "Tome Sovereign", "Finish a book with 1,000 or more pages", "mountain", Rarity.Legendary, 1000, false, 19),
        BadgeDef("sprint", null, null, BadgeCategory.Reading, "Sprint", "Finish a book in a single day", "zap", Rarity.Epic, null, false, 20),
        BadgeDef("binge_reader", null, null, BadgeCategory.Reading, "Binge Reader", "Finish 3 or more books in a single week", "fast-forward", Rarity.Epic, 3, false, 21),
        BadgeDef("monthly_reader_2", null, null, BadgeCategory.Reading, "Monthly Machine", "Finish 10 books in a single calendar month", "calendar-check", Rarity.Legendary, 10, false, 22),
        BadgeDef("slow_burn", null, null, BadgeCategory.Reading, "Slow Burn", "Take over 90 days from start to finish a book", "hourglass", Rarity.Rare, null, false, 23),
        BadgeDef("power_hour", null, null, BadgeCategory.Reading, "Power Hour", "Read 75 or more pages in a single reading session", "gauge", Rarity.Rare, 75, false, 25),
        BadgeDef("speed_reader", null, null, BadgeCategory.Reading, "Speed Reader", "Read 100 or more pages in a single day", "rabbit", Rarity.Epic, 100, false, 26),
        // ---- Library
        BadgeDef("library_builder_1", "library_builder", 1, BadgeCategory.Library, "Shelf Seed", "Have 50 books in your library", "library", Rarity.Common, 50, false, 1),
        BadgeDef("library_builder_2", "library_builder", 2, BadgeCategory.Library, "Collection Architect", "Have 250 books in your library", "library", Rarity.Rare, 250, false, 2),
        BadgeDef("library_builder_3", "library_builder", 3, BadgeCategory.Library, "Stack Sovereign", "Have 1,000 books in your library", "library", Rarity.Epic, 1000, false, 3),
        BadgeDef("library_builder_4", "library_builder", 4, BadgeCategory.Library, "Library of Alexandria", "Have 5,000 books in your library", "library", Rarity.Legendary, 5000, false, 4),
        BadgeDef("annotator_1", "annotator", 1, BadgeCategory.Library, "Note Taker", "Create 10 annotations", "highlighter", Rarity.Common, 10, false, 5),
        BadgeDef("annotator_2", "annotator", 2, BadgeCategory.Library, "Active Annotator", "Create 50 annotations", "highlighter", Rarity.Rare, 50, false, 6),
        BadgeDef("annotator_3", "annotator", 3, BadgeCategory.Library, "Prolific Annotator", "Create 200 annotations", "highlighter", Rarity.Epic, 200, false, 7),
        BadgeDef("annotator_4", "annotator", 4, BadgeCategory.Library, "Master Annotator", "Create 500 annotations", "highlighter", Rarity.Legendary, 500, false, 8),
        BadgeDef("multi_format", null, null, BadgeCategory.Library, "Multi-Format", "Own books in 3 or more different formats", "files", Rarity.Rare, 3, false, 9),
        BadgeDef("curator", null, null, BadgeCategory.Library, "Curator", "Create 10 or more collections", "folder-open", Rarity.Rare, 10, false, 10),
        BadgeDef("deep_dive_session", null, null, BadgeCategory.Library, "Deep Dive", "Create 10 annotations in a single day", "highlighter", Rarity.Rare, 10, false, 12),
        BadgeDef("bookmarked", null, null, BadgeCategory.Library, "Bookmarked", "Create your first annotation or highlight", "bookmark-check", Rarity.Common, null, false, 13),
        // ---- Exploration
        BadgeDef("author_deep_dive", null, null, BadgeCategory.Exploration, "Author Deep Dive", "Read 5 or more books by the same author", "feather", Rarity.Rare, 5, false, 13),
        BadgeDef("short_story_fan", null, null, BadgeCategory.Exploration, "Short Story Fan", "Finish 5 books with fewer than 100 pages", "minimize-2", Rarity.Rare, 5, false, 15),
        BadgeDef("thick_and_thin", null, null, BadgeCategory.Exploration, "Thick and Thin", "Finish at least one book under 100 pages and one over 800 pages", "arrow-up-down", Rarity.Rare, null, false, 16),
        BadgeDef("trilogy_master", null, null, BadgeCategory.Exploration, "Trilogy Master", "Finish a complete 3-book series", "list-ordered", Rarity.Rare, null, false, 17),
        BadgeDef("first_series", null, null, BadgeCategory.Exploration, "Series Begun", "Finish your first book that belongs to a named series", "play", Rarity.Common, null, false, 19),
        // ---- Dedication
        BadgeDef("streak_1", "streak", 1, BadgeCategory.Dedication, "Getting Started", "Maintain a 7-day reading streak", "flame", Rarity.Common, 7, false, 1),
        BadgeDef("streak_2", "streak", 2, BadgeCategory.Dedication, "Habitual", "Maintain a 30-day reading streak", "flame", Rarity.Rare, 30, false, 2),
        BadgeDef("streak_3", "streak", 3, BadgeCategory.Dedication, "Unstoppable", "Maintain a 100-day reading streak", "flame", Rarity.Epic, 100, false, 3),
        BadgeDef("streak_4", "streak", 4, BadgeCategory.Dedication, "Year-Long Flame", "Maintain a 365-day reading streak", "flame", Rarity.Legendary, 365, false, 4),
        BadgeDef("year_round_reader", null, null, BadgeCategory.Dedication, "Year-Round Reader", "Read in all 12 months of a calendar year", "calendar", Rarity.Epic, 12, false, 5),
        BadgeDef("comeback_kid", null, null, BadgeCategory.Dedication, "Comeback Kid", "Resume reading after a 30-day break", "corner-up-right", Rarity.Rare, 30, false, 7),
        BadgeDef("seasonal_reader", null, null, BadgeCategory.Dedication, "Four Seasons", "Read in all 4 seasons within a single year", "sun", Rarity.Epic, 4, false, 9),
        BadgeDef("perfect_month", null, null, BadgeCategory.Dedication, "Perfect Month", "Read every day of a calendar month", "calendar-check", Rarity.Legendary, null, false, 10),
        BadgeDef("consistent_reader", null, null, BadgeCategory.Dedication, "Consistency Is Key", "Read at least 30 minutes every day for 14 consecutive days", "activity", Rarity.Rare, 14, false, 11),
        BadgeDef("weekend_rhythm", null, null, BadgeCategory.Dedication, "Weekend Rhythm", "Read on 8 consecutive weekends", "repeat", Rarity.Epic, 8, false, 12),
        BadgeDef("weekend_warrior", null, null, BadgeCategory.Dedication, "Weekend Warrior", "Read for 5 or more hours in a single weekend", "swords", Rarity.Rare, 5, false, 13),
        BadgeDef("yearly_finisher_12", null, null, BadgeCategory.Dedication, "Yearly Closer", "Finish 12 books in a single calendar year", "award", Rarity.Epic, 12, false, 14),
        BadgeDef("new_year_reader", null, null, BadgeCategory.Dedication, "New Year Reader", "Complete a reading session on January 1st", "party-popper", Rarity.Epic, null, true, 15),
        BadgeDef("category_sweeper", null, null, BadgeCategory.Dedication, "Category Sweeper", "Earn at least one achievement in all 4 categories", "trophy", Rarity.Legendary, 4, false, 16)
    )

    private val byKey = ALL.associateBy { it.key }
    fun get(key: String): BadgeDef? = byKey[key]

    /** The catalogue in BookOrbit's list order: category by category, then each category's own sort order. */
    val ORDERED: List<BadgeDef> = ALL.sortedWith(compareBy({ it.category.ordinal }, { it.sortOrder }))
}

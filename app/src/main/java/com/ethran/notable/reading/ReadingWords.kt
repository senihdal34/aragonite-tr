package com.ethran.notable.reading

import java.time.LocalDate

data class WordOfDay(val word: String, val meaning: String)

/** A short list of words worth knowing, one a day. Chosen by date, so the word stays the same all day and on every launch. */
object ReadingWords {
    val WORDS: List<WordOfDay> = listOf(
        WordOfDay("ephemeral", "lasting a very short time"),
        WordOfDay("sonder", "the sense that every passer-by has a life as vivid as your own"),
        WordOfDay("petrichor", "the smell of rain on dry earth"),
        WordOfDay("liminal", "on the threshold between two states"),
        WordOfDay("sanguine", "cheerful and hopeful, especially when things look bad"),
        WordOfDay("laconic", "using very few words"),
        WordOfDay("ineffable", "too great to be put into words"),
        WordOfDay("mellifluous", "sweet and smooth to hear"),
        WordOfDay("equanimity", "calm of mind under strain"),
        WordOfDay("serendipity", "a lucky find made by chance"),
        WordOfDay("obfuscate", "to make something unclear on purpose"),
        WordOfDay("perspicacious", "quick to see and understand things"),
        WordOfDay("halcyon", "calm and happy, as in days gone by"),
        WordOfDay("recondite", "known to few; hard to understand"),
        WordOfDay("tenacious", "holding on firmly; not giving up"),
        WordOfDay("verdant", "green with growing plants"),
        WordOfDay("quixotic", "idealistic in a way that is not practical"),
        WordOfDay("sedulous", "working at something with care and patience"),
        WordOfDay("lucid", "clear and easy to understand"),
        WordOfDay("crepuscular", "of twilight; active at dusk and dawn"),
        WordOfDay("palimpsest", "a page written over an older text that still shows through"),
        WordOfDay("eloquent", "speaking or writing clearly and with force"),
        WordOfDay("fastidious", "very careful about detail and accuracy"),
        WordOfDay("gregarious", "fond of company"),
        WordOfDay("juxtapose", "to set two things side by side to compare them"),
        WordOfDay("limpid", "perfectly clear, like clean water"),
        WordOfDay("numinous", "having a strong spiritual or mysterious feeling"),
        WordOfDay("reverie", "a pleasant, dreamy train of thought"),
        WordOfDay("solace", "comfort in a time of sadness"),
        WordOfDay("zenith", "the highest point")
    )

    /** The word for [date]: the same word all day, a new one the next day. */
    fun of(date: LocalDate): WordOfDay = WORDS[Math.floorMod(date.toEpochDay(), WORDS.size.toLong()).toInt()]
}

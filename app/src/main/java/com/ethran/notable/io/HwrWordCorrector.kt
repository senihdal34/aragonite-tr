package com.ethran.notable.io

/** One recognized word with the alternatives the Onyx/MyScript engine offered for it. */
data class HwrWord(val label: String, val candidates: List<String> = emptyList())

/**
 * The user's own vocabulary: names, companies, English terms the engine tends to get wrong.
 * Matching ignores case and Turkish diacritics (ı/i, ş/s, ğ/g, ü/u, ö/o, ç/c).
 */
class HwrGlossary(words: Collection<String>) {
    private val byKey: Map<String, String> = words
        .map { it.trim() }
        .filter { it.length >= 3 && !it.startsWith("#") }
        .associateBy { fold(it) }          // later entries win; canonical spelling is kept

    val size get() = byKey.size
    fun isEmpty() = byKey.isEmpty()

    fun exact(word: String): String? = byKey[fold(word)]

    /** True for "Windowsta", "Cronusu": a glossary word followed by a short Turkish suffix. */
    fun isSuffixedForm(word: String): Boolean {
        val key = fold(word)
        return byKey.keys.any { k -> key.length > k.length && key.length - k.length <= 4 && key.startsWith(k) }
    }

    /** Closest entry within [maxDistance] edits, or null when none or when two entries tie. */
    fun fuzzy(word: String, maxDistance: Int): String? {
        val key = fold(word)
        var best: String? = null
        var bestDistance = Int.MAX_VALUE
        var tie = false
        for ((k, canonical) in byKey) {
            if (kotlin.math.abs(k.length - key.length) > maxDistance) continue
            val d = editDistance(key, k, maxDistance)
            if (d > maxDistance) continue
            // Distance 2 is only trusted when the first letter agrees (handwriting rarely gets it wrong).
            if (d == 2 && k.first() != key.first()) continue
            if (d < bestDistance) { best = canonical; bestDistance = d; tie = false }
            else if (d == bestDistance && canonical != best) tie = true
        }
        return if (tie) null else best
    }

    companion object {
        fun fold(s: String): String {
            val sb = StringBuilder(s.length)
            for (c in s) {
                sb.append(
                    when (c) {
                        'ı', 'İ', 'I' -> 'i'
                        'ş', 'Ş' -> 's'
                        'ğ', 'Ğ' -> 'g'
                        'ü', 'Ü' -> 'u'
                        'ö', 'Ö' -> 'o'
                        'ç', 'Ç' -> 'c'
                        else -> c.lowercaseChar()
                    }
                )
            }
            return sb.toString()
        }

        /** Damerau-Levenshtein (adjacent swaps count as one edit); stops early above [limit]. */
        fun editDistance(a: String, b: String, limit: Int = Int.MAX_VALUE): Int {
            if (a == b) return 0
            val n = a.length
            val m = b.length
            var prev2 = IntArray(m + 1)
            var prev = IntArray(m + 1) { it }
            var cur = IntArray(m + 1)
            for (i in 1..n) {
                cur[0] = i
                var rowMin = cur[0]
                for (j in 1..m) {
                    val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                    var v = minOf(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + cost)
                    if (i > 1 && j > 1 && a[i - 1] == b[j - 2] && a[i - 2] == b[j - 1]) {
                        v = minOf(v, prev2[j - 2] + 1)
                    }
                    cur[j] = v
                    if (v < rowMin) rowMin = v
                }
                if (rowMin > limit) return limit + 1
                val t = prev2; prev2 = prev; prev = cur; cur = t
            }
            return prev[m]
        }
    }
}

/**
 * Picks the user's spelling for words the engine got slightly wrong.
 * Order: label in glossary -> any engine candidate in glossary -> a glossary word 1-2 edits away.
 */
object HwrWordCorrector {
    private val tokenRegex = Regex("""^([^\p{L}\p{N}]*)(.*?)([^\p{L}\p{N}]*)$""", RegexOption.DOT_MATCHES_ALL)

    fun rebuild(words: List<HwrWord>, glossary: HwrGlossary): String =
        words.joinToString("") { if (it.label.isBlank()) it.label else correctWord(it, glossary) }

    fun correctWord(word: HwrWord, glossary: HwrGlossary): String {
        if (glossary.isEmpty()) return word.label
        val m = tokenRegex.matchEntire(word.label) ?: return word.label
        val (lead, core, trail) = m.destructured
        if (core.length < 3 || core.count { it.isDigit() } * 2 >= core.length) return word.label   // numbers stay as written

        // "Cronus'a": correct the stem, keep the Turkish suffix after the apostrophe.
        val apostrophe = core.indexOfFirst { it == '\'' || it == '’' }
        val stem = if (apostrophe > 0) core.substring(0, apostrophe) else core
        val suffix = if (apostrophe > 0) core.substring(apostrophe) else ""
        if (stem.length < 3) return word.label

        if (glossary.exact(stem) == null && glossary.isSuffixedForm(stem)) return word.label

        val fixed: String = glossary.exact(stem)
            ?: candidateInGlossary(word.candidates, glossary)
            ?: fuzzyMatch(stem, glossary)
            ?: return word.label

        val cased = if (stem.length > 1 && stem.all { !it.isLetter() || it.isUpperCase() }) fixed.uppercase() else fixed
        return lead + cased + suffix + trail
    }

    private fun candidateInGlossary(candidates: List<String>, glossary: HwrGlossary): String? {
        for (c in candidates) {
            val core = tokenRegex.matchEntire(c)?.groupValues?.get(2) ?: c
            val stem = core.substringBefore('\'').substringBefore('’')
            if (stem.length >= 3) glossary.exact(stem)?.let { return it }
        }
        return null
    }

    private fun fuzzyMatch(stem: String, glossary: HwrGlossary): String? =
        if (stem.length >= 5) glossary.fuzzy(stem, if (stem.length >= 9) 2 else 1) else null
}

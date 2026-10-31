package com.ethran.notable.reading.badges

/** BookOrbit's four medal rarities (packages/types/src/achievement.ts). The rim of the medal and its colour follow it. */
enum class Rarity(val label: String) {
    Common("Common"), Rare("Rare"), Epic("Epic"), Legendary("Legendary");

    /** The colour family the board draws the rarity in (lb_board_colour.lua RARITY: green, blue, violet, gold). */
    val family: String get() = when (this) { Common -> "green"; Rare -> "blue"; Epic -> "violet"; Legendary -> "gold" }

    companion object {
        /** A rarity this build does not know is drawn as common, as BookOrbit's client does. */
        fun of(name: String?): Rarity = entries.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: Common
    }
}

/** BookOrbit's achievement categories, in the order its catalogue lists them. Devices has no local meaning and is not used. */
enum class BadgeCategory(val label: String) {
    Reading("Reading"), Library("Library"), Exploration("Exploration"), Dedication("Dedication")
}

/**
 * One badge of the catalogue, as BookOrbit's seed defines it (server/src/modules/achievement/seed/achievement-seed.ts):
 * [group] and [tier] join the tiers of one set, [threshold] is the number progress counts up to (null for a badge that
 * is simply earned or not), [hidden] badges show as secrets until earned.
 */
data class BadgeDef(
    val key: String, val group: String?, val tier: Int?, val category: BadgeCategory, val name: String, val description: String,
    val icon: String, val rarity: Rarity, val threshold: Int?, val hidden: Boolean, val sortOrder: Int
)

/** What the data says about one badge right now: how far along ([progress], null when it has no count) and whether its rule holds. */
data class Measure(val progress: Double?, val met: Boolean, val at: Long? = null)

/** A badge with its state: [earnedAt] is the first time it was seen earned (epoch ms), kept for good. */
data class Badge(
    val def: BadgeDef, val progress: Double?, val earned: Boolean, val earnedAt: Long?,
    /** Earned when the store was first filled, so not worth celebrating as new. */
    val backlog: Boolean = false
) {
    val key: String get() = def.key
    val secret: Boolean get() = def.hidden && !earned
    val name: String get() = if (secret) SECRET_NAME else def.name
    val description: String get() = if (secret) SECRET_TEXT else def.description
    val icon: String get() = if (secret) "lock" else def.icon
    val rarity: Rarity get() = if (secret) Rarity.Common else def.rarity
    val threshold: Int? get() = if (secret) null else def.threshold

    /** 0..1 towards the threshold, or null when nothing counts. */
    val fraction: Double?
        get() {
            val t = threshold ?: return null
            val p = progress ?: return null
            if (earned || t <= 0) return null
            return (p / t).coerceIn(0.0, 1.0)
        }

    companion object {
        const val SECRET_NAME = "Secret badge"
        const val SECRET_TEXT = "Keep reading to reveal this badge."
    }
}

/** What the Next badge box and the list screen show. */
data class BadgeBoard(
    val badges: List<Badge>,
    /** The unearned badge closest to done. */
    val next: Badge?,
    /** The badge earned last. */
    val latest: Badge?,
    /** True when [latest] was earned in the last day (and not just found in the backlog). */
    val fresh: Boolean,
    /** The time-to-go line for [next], when the pace allows one. */
    val eta: String?
) {
    val earned: Int get() = badges.count { it.earned }
    val total: Int get() = badges.size
}

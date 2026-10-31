package com.ethran.notable.planner

import com.ethran.notable.data.db.Kv
import com.ethran.notable.data.db.KvDao
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/** Lines ticked off per day (1-based). The page drawing reads this cache, so a ticked line is drawn struck through. */
object PlannerDone {
    @Volatile var days: Map<LocalDate, Set<Int>> = emptyMap()
    fun lines(day: LocalDate): Set<Int> = days[day].orEmpty()
}

/** Stores the ticked lines in the key-value table (`plannerdone:<date>` = "1,3,4"); no schema change. */
@Singleton
class PlannerDoneRepository @Inject constructor(private val dao: KvDao) {
    private fun key(day: LocalDate) = "plannerdone:$day"

    suspend fun load(dates: List<LocalDate>) {
        val loaded = dates.associateWith { d ->
            dao.get(key(d))?.value?.split(",")?.mapNotNull { it.trim().toIntOrNull() }?.toSet().orEmpty()
        }
        PlannerDone.days = PlannerDone.days + loaded
    }

    /** Ticks the line or un-ticks it; returns the new state of that day. */
    suspend fun toggle(day: LocalDate, line: Int): Set<Int> {
        val now = PlannerDone.lines(day)
        val next = if (line in now) now - line else now + line
        PlannerDone.days = PlannerDone.days + (day to next)
        if (next.isEmpty()) dao.delete(key(day)) else dao.set(Kv(key(day), next.sorted().joinToString(",")))
        return next
    }
}

package com.ethran.notable.data.db

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class TodoItem(val id: String = UUID.randomUUID().toString(), val text: String = "", val done: Boolean = false)

/**
 * The typed to-do list of each day on the Week page. One key-value row per day (`todo:<date>`, JSON), so no
 * schema change. Writes are saved automatically a moment after the last change, on a scope that outlives the
 * screen; [latest] always holds the newest list, so a reload never shows an older one.
 */
@Singleton
class PlannerTodoRepository @Inject constructor(private val dao: KvDao) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val jobs = HashMap<LocalDate, Job>()
    private val latest = HashMap<LocalDate, List<TodoItem>>()
    private val serializer = ListSerializer(TodoItem.serializer())

    suspend fun get(day: LocalDate): List<TodoItem> {
        synchronized(latest) { latest[day] }?.let { return it }
        val stored = dao.get(KEY + day)?.value?.let { runCatching { Json.decodeFromString(serializer, it) }.getOrNull() }.orEmpty()
        synchronized(latest) { return latest.getOrPut(day) { stored } }
    }

    suspend fun week(monday: LocalDate): Map<LocalDate, List<TodoItem>> =
        (0L..6L).map { monday.plusDays(it) }.associateWith { get(it) }

    /** Remembers the list now and writes it after [SAVE_DELAY_MS] without further changes. */
    fun save(day: LocalDate, items: List<TodoItem>) {
        synchronized(latest) { latest[day] = items }
        synchronized(jobs) {
            jobs[day]?.cancel()
            jobs[day] = scope.launch {
                delay(SAVE_DELAY_MS)
                if (items.isEmpty()) dao.delete(KEY + day) else dao.set(Kv(KEY + day, Json.encodeToString(serializer, items)))
            }
        }
    }

    companion object {
        private const val KEY = "todo:"
        const val SAVE_DELAY_MS = 300L
    }
}

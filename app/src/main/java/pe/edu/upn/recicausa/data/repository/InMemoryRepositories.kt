package pe.edu.upn.recicausa.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import pe.edu.upn.recicausa.domain.RecycleActivity

// Sprint 1 - Implementaciones en memoria. Permiten construir y probar la interfaz
// antes de tener persistencia. Se pierden al cerrar la app (por eso en Sprint 2 se reemplazan).

class InMemoryActivityRepository : ActivityRepository {
    private val items = MutableStateFlow<List<RecycleActivity>>(emptyList())
    private var nextId = 1L

    override fun observeAll(): Flow<List<RecycleActivity>> =
        items.map { list -> list.sortedByDescending { it.createdAt } }

    override suspend fun add(activity: RecycleActivity) {
        items.update { it + activity.copy(id = nextId++) }
    }

    override suspend fun getById(id: Long): RecycleActivity? = items.value.firstOrNull { it.id == id }

    override suspend fun update(activity: RecycleActivity) {
        items.update { list -> list.map { if (it.id == activity.id) activity else it } }
    }

    override suspend fun delete(id: Long) {
        items.update { list -> list.filterNot { it.id == id } }
    }
}

class InMemoryPreferencesRepository : PreferencesRepository {
    private val enabled = MutableStateFlow(false)
    private val hour = MutableStateFlow(DEFAULT_REMINDER_HOUR)

    override val remindersEnabled: Flow<Boolean> = enabled
    override val reminderHour: Flow<Int> = hour

    override suspend fun setRemindersEnabled(enabled: Boolean) { this.enabled.value = enabled }
    override suspend fun setReminderHour(hour: Int) { this.hour.value = hour }
}

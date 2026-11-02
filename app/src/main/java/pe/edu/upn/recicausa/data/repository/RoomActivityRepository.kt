package pe.edu.upn.recicausa.data.repository

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import pe.edu.upn.recicausa.data.local.ActivityDao
import pe.edu.upn.recicausa.data.local.toDomain
import pe.edu.upn.recicausa.data.local.toEntity
import pe.edu.upn.recicausa.domain.RecycleActivity

// Repository sobre Room. Filtra por el usuario con sesión, de modo que cada cuenta
// ve solo sus actividades y los ViewModels no necesitan saber de usuarios.

class RoomActivityRepository(
    private val dao: ActivityDao,
    private val session: SessionRepository
) : ActivityRepository {

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeAll(): Flow<List<RecycleActivity>> =
        session.userId.flatMapLatest { id ->
            if (id == null) flowOf(emptyList())
            else dao.observeByUser(id).map { list -> list.map { it.toDomain() } }
        }

    override suspend fun add(activity: RecycleActivity) {
        val userId = session.userId.first() ?: return // sin sesión no se guarda nada
        dao.insert(activity.toEntity(userId))
    }

    override suspend fun getById(id: Long): RecycleActivity? {
        val userId = session.userId.first() ?: return null
        return dao.getById(id, userId)?.toDomain()
    }

    override suspend fun update(activity: RecycleActivity) {
        val userId = session.userId.first() ?: return
        // Solo se modifica una actividad que pertenece a la cuenta activa.
        if (dao.getById(activity.id, userId) == null) return
        dao.update(activity.toEntity(userId))
    }

    override suspend fun delete(id: Long) {
        val userId = session.userId.first() ?: return
        dao.deleteById(id, userId)
    }
}

package pe.edu.upn.recicausa.data.repository

import java.time.Instant
import kotlinx.coroutines.flow.first
import pe.edu.upn.recicausa.data.local.ActivityDao
import pe.edu.upn.recicausa.data.local.ActivityEntity
import pe.edu.upn.recicausa.data.remote.ActividadDto
import pe.edu.upn.recicausa.data.remote.ApiError
import pe.edu.upn.recicausa.data.remote.ApiResult
import pe.edu.upn.recicausa.data.remote.Filters
import pe.edu.upn.recicausa.data.remote.SupabaseApi
import pe.edu.upn.recicausa.data.remote.safeApiCall
import pe.edu.upn.recicausa.domain.Catalog

// Sprint 5 - Respaldo de actividades en Supabase (HU13).
// Regla RN12: el respaldo refleja el teléfono que sincroniza; el último en sincronizar gana.

data class SyncReport(val restored: Int, val uploaded: Int, val deleted: Int)

interface ActivitySyncRepository {
    suspend fun sync(): ApiResult<SyncReport>
}

/** Recuerda si en este teléfono ya se restauró la nube de una cuenta (evita "resucitar" borrados). */
interface SyncMarker {
    suspend fun isRestored(userId: String): Boolean
    suspend fun markRestored(userId: String)
}

class SupabaseActivitySync(
    private val api: SupabaseApi,
    private val dao: ActivityDao,
    private val session: SessionRepository,
    private val marker: SyncMarker
) : ActivitySyncRepository {

    override suspend fun sync(): ApiResult<SyncReport> {
        val uid = session.userId.first() ?: return ApiResult.Failure(ApiError.Unauthorized)

        // 1) Estado de la nube.
        val remote = when (val r = safeApiCall { api.getActividades() }) {
            is ApiResult.Failure -> return r
            is ApiResult.Success -> r.value
        }

        // 2) Primera vez en este teléfono: traer lo que falte (CA13.2, reinstalar y restaurar).
        var restored = 0
        if (!marker.isRestored(uid)) {
            val localIds = dao.getAllByUser(uid).map { it.id }.toSet()
            val missing = remote.filter { it.localId !in localIds && it.isValid() }.map { it.toEntity(uid) }
            if (missing.isNotEmpty()) dao.insertAllIgnore(missing)
            restored = missing.size
            marker.markRestored(uid)
        }

        // 3) Subir lo local (upsert) y borrar de la nube lo que ya no existe en el teléfono.
        val local = dao.getAllByUser(uid)
        var uploaded = 0
        if (local.isNotEmpty()) {
            when (val r = safeApiCall { api.upsertActividades(local.map { it.toDto(uid) }) }) {
                is ApiResult.Failure -> return r
                is ApiResult.Success -> uploaded = local.size
            }
        }
        val stale = remote.map { it.localId }.toSet() - local.map { it.id }.toSet()
        if (stale.isNotEmpty()) {
            when (val r = safeApiCall { api.borrarActividades(Filters.inList(stale)) }) {
                is ApiResult.Failure -> return r
                is ApiResult.Success -> Unit
            }
        }
        return ApiResult.Success(SyncReport(restored, uploaded, stale.size))
    }
}

// ---------- Conversión Room <-> DTO ----------
fun ActivityEntity.toDto(userId: String) = ActividadDto(
    userId = userId, localId = id, categoriaId = categoryId, cantidad = quantity,
    nota = note, puntos = points, creadoEn = Instant.ofEpochMilli(createdAt).toString()
)

fun ActividadDto.toEntity(userId: String) = ActivityEntity(
    id = localId, userId = userId, categoryId = categoriaId, quantity = cantidad,
    note = nota, points = puntos,
    createdAt = creadoEn?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() } ?: System.currentTimeMillis(),
    photoPath = null // las fotos no se respaldan en la nube
)

/** Una categoría desconocida (dato corrupto) no debe entrar al teléfono. */
fun ActividadDto.isValid(): Boolean =
    Catalog.categoryById(categoriaId) != null && cantidad in 1..50

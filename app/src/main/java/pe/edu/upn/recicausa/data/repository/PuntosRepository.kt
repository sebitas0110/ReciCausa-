package pe.edu.upn.recicausa.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import pe.edu.upn.recicausa.data.local.PuntoCacheDao
import pe.edu.upn.recicausa.data.local.toDomain
import pe.edu.upn.recicausa.data.local.toEntity
import pe.edu.upn.recicausa.data.remote.ApiError
import pe.edu.upn.recicausa.data.remote.ApiResult
import pe.edu.upn.recicausa.data.remote.Filters
import pe.edu.upn.recicausa.data.remote.PuntoDto
import pe.edu.upn.recicausa.data.remote.PuntoWriteDto
import pe.edu.upn.recicausa.data.remote.SupabaseApi
import pe.edu.upn.recicausa.data.remote.map
import pe.edu.upn.recicausa.data.remote.safeApiCall
import pe.edu.upn.recicausa.domain.PuntoEstado
import pe.edu.upn.recicausa.domain.PuntoReciclaje

// Sprint 5 - Puntos de reciclaje: lectura con caché local (HU10, CA10.4) y CRUD de sugerencias (HU12).

/** Datos del formulario de sugerencia, ya validados. */
data class PuntoInput(
    val nombre: String,
    val descripcion: String,
    val direccion: String,
    val categorias: List<String>,
    val latitud: Double,
    val longitud: Double,
    val horario: String,
    val telefono: String
)

interface PuntosRepository {
    /** Puntos aprobados guardados en el teléfono (los últimos descargados). */
    fun observeAprobados(): Flow<List<PuntoReciclaje>>
    /** R: descarga los puntos aprobados y reemplaza la caché. Si falla, la caché queda intacta. */
    suspend fun refresh(): ApiResult<Unit>
    /** R: sugerencias del usuario con sesión. */
    suspend fun misSugerencias(): ApiResult<List<PuntoReciclaje>>
    /** C */
    suspend fun sugerir(input: PuntoInput): ApiResult<PuntoReciclaje>
    /** U (solo pendientes: lo exige RLS) */
    suspend fun editar(id: Long, input: PuntoInput): ApiResult<PuntoReciclaje>
    /** D (solo pendientes) */
    suspend fun eliminar(id: Long): ApiResult<Unit>
}

class RemotePuntosRepository(
    private val api: SupabaseApi,
    private val cache: PuntoCacheDao,
    private val session: SessionRepository
) : PuntosRepository {

    override fun observeAprobados(): Flow<List<PuntoReciclaje>> =
        cache.observeAll().map { list -> list.map { it.toDomain() } }

    override suspend fun refresh(): ApiResult<Unit> =
        safeApiCall { api.getPuntosAprobados() }.map { list ->
            cache.replaceAll(list.map { it.toDomain().toEntity() })
        }

    override suspend fun misSugerencias(): ApiResult<List<PuntoReciclaje>> {
        val uid = session.userId.first() ?: return ApiResult.Failure(ApiError.Unauthorized)
        return safeApiCall { api.getMisPuntos(Filters.eq(uid)) }.map { list -> list.map { it.toDomain() } }
    }

    override suspend fun sugerir(input: PuntoInput): ApiResult<PuntoReciclaje> =
        safeApiCall { api.crearPunto(input.toDto()) }.firstOrNotFound()

    override suspend fun editar(id: Long, input: PuntoInput): ApiResult<PuntoReciclaje> =
        // PATCH sin filas afectadas (no es tuyo o ya no está pendiente) devuelve lista vacía.
        safeApiCall { api.editarPunto(Filters.eq(id), input.toDto()) }.firstOrNotFound()

    override suspend fun eliminar(id: Long): ApiResult<Unit> =
        safeApiCall { api.borrarPunto(Filters.eq(id)) }

    private fun ApiResult<List<PuntoDto>>.firstOrNotFound(): ApiResult<PuntoReciclaje> = when (this) {
        is ApiResult.Failure -> this
        is ApiResult.Success -> value.firstOrNull()?.let { ApiResult.Success(it.toDomain()) }
            ?: ApiResult.Failure(ApiError.Forbidden)
    }
}

fun PuntoInput.toDto() = PuntoWriteDto(
    nombre = nombre.trim(), descripcion = descripcion.trim(), direccion = direccion.trim(),
    latitud = latitud, longitud = longitud, categorias = categorias.distinct(),
    horario = horario.trim(), telefono = telefono.trim()
)

fun PuntoDto.toDomain() = PuntoReciclaje(
    id = id, nombre = nombre, descripcion = descripcion.orEmpty(), direccion = direccion,
    distrito = distrito, latitud = latitud, longitud = longitud, categorias = categorias,
    horario = horario.orEmpty(), telefono = telefono.orEmpty(), estado = PuntoEstado.fromWire(estado)
)

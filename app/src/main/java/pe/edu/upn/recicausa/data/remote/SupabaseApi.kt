package pe.edu.upn.recicausa.data.remote

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Query

/**
 * Sprint 5 - Contrato de la API REST de Supabase usada por la app.
 *
 *  Auth      POST /auth/v1/signup · POST /auth/v1/token · POST /auth/v1/logout
 *  PostgREST GET/POST/PATCH/DELETE /rest/v1/<tabla>   (filtros con ?columna=op.valor)
 *
 * Los filtros se pasan como parámetros ya escritos (por ejemplo "eq.5"); ver [Filters].
 */
interface SupabaseApi {

    // ---------- Auth ----------
    @POST("auth/v1/signup")
    suspend fun signUp(@Body body: SignUpRequest): Response<AuthResponse>

    @POST("auth/v1/token?grant_type=password")
    suspend fun signIn(@Body body: SignInRequest): Response<AuthResponse>

    @POST("auth/v1/token?grant_type=refresh_token")
    suspend fun refresh(@Body body: RefreshRequest): Response<AuthResponse>

    @POST("auth/v1/logout")
    suspend fun signOut(): Response<Unit>

    // ---------- puntos_reciclaje (CRUD) ----------
    /** R: puntos aprobados (lectura pública). */
    @GET("rest/v1/puntos_reciclaje")
    suspend fun getPuntosAprobados(
        @Query("estado") estado: String = Filters.eq("aprobado"),
        @Query("order") order: String = "nombre.asc",
        @Query("select") select: String = "*"
    ): Response<List<PuntoDto>>

    /** R: sugerencias del usuario (cualquier estado). */
    @GET("rest/v1/puntos_reciclaje")
    suspend fun getMisPuntos(
        @Query("creado_por") creadoPor: String,
        @Query("order") order: String = "creado_en.desc",
        @Query("select") select: String = "*"
    ): Response<List<PuntoDto>>

    /** C: sugerir un punto nuevo (queda "pendiente"). */
    @POST("rest/v1/puntos_reciclaje")
    suspend fun crearPunto(
        @Body body: PuntoWriteDto,
        @Header("Prefer") prefer: String = "return=representation"
    ): Response<List<PuntoDto>>

    /** U: editar una sugerencia pendiente. */
    @PATCH("rest/v1/puntos_reciclaje")
    suspend fun editarPunto(
        @Query("id") id: String,
        @Body body: PuntoWriteDto,
        @Header("Prefer") prefer: String = "return=representation"
    ): Response<List<PuntoDto>>

    /** D: borrar una sugerencia pendiente. */
    @DELETE("rest/v1/puntos_reciclaje")
    suspend fun borrarPunto(@Query("id") id: String): Response<Unit>

    // ---------- actividades (respaldo) ----------
    @GET("rest/v1/actividades")
    suspend fun getActividades(
        @Query("select") select: String = "local_id,categoria_id,cantidad,nota,puntos,creado_en",
        @Query("order") order: String = "creado_en.desc",
        @Query("limit") limit: Int = 1000
    ): Response<List<ActividadDto>>

    /** C/U: upsert por (user_id, local_id). */
    @POST("rest/v1/actividades")
    suspend fun upsertActividades(
        @Body body: List<ActividadDto>,
        @Query("on_conflict") onConflict: String = "user_id,local_id",
        @Header("Prefer") prefer: String = "resolution=merge-duplicates,return=minimal"
    ): Response<Unit>

    /** D: borra por lista de local_id, p. ej. "in.(1,2,3)". */
    @DELETE("rest/v1/actividades")
    suspend fun borrarActividades(@Query("local_id") filter: String): Response<Unit>
}

/** Constructores de filtros PostgREST. */
object Filters {
    fun eq(value: Any): String = "eq.$value"
    fun inList(values: Collection<Long>): String = "in.(${values.joinToString(",")})"
}

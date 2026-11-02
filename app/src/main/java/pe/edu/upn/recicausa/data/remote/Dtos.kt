package pe.edu.upn.recicausa.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Sprint 5 - Contrato JSON con Supabase (Auth + PostgREST). Los nombres de campo coinciden con
// las columnas de recicausa_supabase.sql; @SerialName hace explícita la correspondencia.

// ---------- Auth ----------
@Serializable
data class SignUpRequest(val email: String, val password: String, val data: SignUpData)

@Serializable
data class SignUpData(val nombre: String)

@Serializable
data class SignInRequest(val email: String, val password: String)

@Serializable
data class RefreshRequest(@SerialName("refresh_token") val refreshToken: String)

@Serializable
data class UserMetadata(val nombre: String? = null)

@Serializable
data class UserDto(
    val id: String,
    val email: String? = null,
    @SerialName("user_metadata") val userMetadata: UserMetadata? = null
)

/**
 * Respuesta de /signup y /token. Con "Confirm email" activo, /signup devuelve el usuario sin
 * tokens (access_token == null); con la confirmación desactivada devuelve la sesión completa.
 */
@Serializable
data class AuthResponse(
    @SerialName("access_token") val accessToken: String? = null,
    @SerialName("refresh_token") val refreshToken: String? = null,
    @SerialName("expires_in") val expiresIn: Long? = null,
    val user: UserDto? = null
)

// ---------- puntos_reciclaje ----------
@Serializable
data class PuntoDto(
    val id: Long,
    val nombre: String,
    val descripcion: String? = null,
    val direccion: String,
    val distrito: String = "Trujillo",
    val latitud: Double,
    val longitud: Double,
    val categorias: List<String> = emptyList(),
    val horario: String? = null,
    val telefono: String? = null,
    val estado: String = "pendiente"
)

/** POST/PATCH de una sugerencia. estado, fuente y creado_por los fija la base (RN11). */
@Serializable
data class PuntoWriteDto(
    val nombre: String,
    val descripcion: String,
    val direccion: String,
    val distrito: String = "Trujillo",
    val latitud: Double,
    val longitud: Double,
    val categorias: List<String>,
    val horario: String,
    val telefono: String
)

// ---------- actividades ----------
@Serializable
data class ActividadDto(
    @SerialName("user_id") val userId: String? = null,
    @SerialName("local_id") val localId: Long,
    @SerialName("categoria_id") val categoriaId: String,
    val cantidad: Int,
    val nota: String = "",
    val puntos: Int,
    @SerialName("creado_en") val creadoEn: String? = null
)

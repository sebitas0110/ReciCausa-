package pe.edu.upn.recicausa.data.repository

import java.io.File
import kotlinx.coroutines.flow.Flow
import pe.edu.upn.recicausa.domain.RecycleActivity
import pe.edu.upn.recicausa.domain.User

// Contratos del patrón Repository.
// Los ViewModels solo conocen estas interfaces; no saben si detrás hay memoria, Room o DataStore.

interface ActivityRepository {
    /** Actividades del usuario con sesión, de la más reciente a la más antigua. */
    fun observeAll(): Flow<List<RecycleActivity>>
    suspend fun add(activity: RecycleActivity)
    /** Una actividad del usuario con sesión; null si no existe o es de otra cuenta. */
    suspend fun getById(id: Long): RecycleActivity?
    suspend fun update(activity: RecycleActivity)
    suspend fun delete(id: Long)
}

interface PreferencesRepository {
    val remindersEnabled: Flow<Boolean>
    val reminderHour: Flow<Int>
    suspend fun setRemindersEnabled(enabled: Boolean)
    suspend fun setReminderHour(hour: Int)
}

/** Sesión de Supabase guardada en el teléfono (RN08: la contraseña nunca se guarda). */
data class StoredSession(
    val accessToken: String,
    val refreshToken: String,
    val userId: String,
    val name: String,
    val email: String
)

/** Sesión iniciada; null si no hay. Persiste al cerrar la app. */
interface SessionRepository {
    val session: Flow<StoredSession?>
    /** Id (uuid de Supabase) del usuario con sesión. */
    val userId: Flow<String?>
    /** Lectura síncrona desde memoria, para el interceptor HTTP. */
    fun peek(): StoredSession?
    suspend fun save(session: StoredSession)
    suspend fun clear()
}

sealed interface AuthResult {
    data object Success : AuthResult
    data class Failure(val message: String) : AuthResult
}

interface AuthRepository {
    /** Usuario con sesión, o null si no hay sesión. */
    val currentUser: Flow<User?>
    suspend fun register(name: String, email: String, password: String): AuthResult
    suspend fun login(email: String, password: String): AuthResult
    suspend fun logout()
}

/** Almacenamiento de las fotos de las actividades (carpeta privada de la app). */
interface PhotoStore {
    fun newPhotoFile(): File
    fun delete(path: String?)
}

const val DEFAULT_REMINDER_HOUR = 18

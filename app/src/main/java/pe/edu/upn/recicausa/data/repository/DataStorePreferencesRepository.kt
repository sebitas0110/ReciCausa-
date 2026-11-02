package pe.edu.upn.recicausa.data.repository

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.runBlocking
import pe.edu.upn.recicausa.data.local.appDataStore

// Configuración del usuario (HU06) y sesión en Preferences DataStore.
// Se usa DataStore (clave-valor) para ajustes pequeños y Room para registros.

class DataStorePreferencesRepository(private val context: Context) : PreferencesRepository {

    private object Keys {
        val REMINDERS_ENABLED = booleanPreferencesKey("reminders_enabled")
        val REMINDER_HOUR = intPreferencesKey("reminder_hour")
    }

    override val remindersEnabled: Flow<Boolean> =
        context.appDataStore.data.map { it[Keys.REMINDERS_ENABLED] ?: false }

    override val reminderHour: Flow<Int> =
        context.appDataStore.data.map { it[Keys.REMINDER_HOUR] ?: DEFAULT_REMINDER_HOUR }

    override suspend fun setRemindersEnabled(enabled: Boolean) {
        context.appDataStore.edit { it[Keys.REMINDERS_ENABLED] = enabled }
    }

    override suspend fun setReminderHour(hour: Int) {
        context.appDataStore.edit { it[Keys.REMINDER_HOUR] = hour }
    }
}

class DataStoreSessionRepository(private val context: Context) : SessionRepository {

    private object K {
        val ACCESS = stringPreferencesKey("session_access_token")
        val REFRESH = stringPreferencesKey("session_refresh_token")
        val USER_ID = stringPreferencesKey("session_user_uuid")
        val NAME = stringPreferencesKey("session_user_name")
        val EMAIL = stringPreferencesKey("session_user_email")
    }

    // Copia en memoria para que el interceptor HTTP lea el token sin bloquear en DataStore.
    @Volatile private var cache: StoredSession? = null
    @Volatile private var loaded = false

    private fun Preferences.toSession(): StoredSession? {
        val access = this[K.ACCESS] ?: return null
        val refresh = this[K.REFRESH] ?: return null
        val id = this[K.USER_ID] ?: return null
        return StoredSession(access, refresh, id, this[K.NAME].orEmpty(), this[K.EMAIL].orEmpty())
    }

    override val session: Flow<StoredSession?> = context.appDataStore.data
        .map { it.toSession() }
        .onEach { cache = it; loaded = true }

    override val userId: Flow<String?> = session.map { it?.userId }.distinctUntilChanged()

    override fun peek(): StoredSession? {
        if (!loaded) {
            cache = runBlocking { context.appDataStore.data.first().toSession() }
            loaded = true
        }
        return cache
    }

    override suspend fun save(session: StoredSession) {
        cache = session; loaded = true
        context.appDataStore.edit {
            it[K.ACCESS] = session.accessToken
            it[K.REFRESH] = session.refreshToken
            it[K.USER_ID] = session.userId
            it[K.NAME] = session.name
            it[K.EMAIL] = session.email
        }
    }

    override suspend fun clear() {
        cache = null; loaded = true
        context.appDataStore.edit { prefs ->
            listOf(K.ACCESS, K.REFRESH, K.USER_ID, K.NAME, K.EMAIL).forEach { prefs.remove(it) }
        }
    }
}

/** Marca por cuenta: ya se restauraron en este teléfono las actividades guardadas en la nube. */
class DataStoreSyncMarker(private val context: Context) : SyncMarker {
    private fun key(userId: String) = booleanPreferencesKey("restored_$userId")
    override suspend fun isRestored(userId: String): Boolean =
        context.appDataStore.data.first()[key(userId)] ?: false
    override suspend fun markRestored(userId: String) {
        context.appDataStore.edit { it[key(userId)] = true }
    }
}

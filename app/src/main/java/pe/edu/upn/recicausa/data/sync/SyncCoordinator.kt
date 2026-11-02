package pe.edu.upn.recicausa.data.sync

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import pe.edu.upn.recicausa.data.remote.ApiResult
import pe.edu.upn.recicausa.data.repository.ActivityRepository
import pe.edu.upn.recicausa.data.repository.ActivitySyncRepository
import pe.edu.upn.recicausa.data.repository.SessionRepository

// Sprint 5 - Estado del respaldo (CA13.3: "veo el estado").
sealed interface SyncState {
    data object Idle : SyncState
    data object Syncing : SyncState
    data object Offline : SyncState
    data class Synced(val at: Long) : SyncState
    data class Failed(val message: String) : SyncState
}

/**
 * Sincroniza las actividades con la nube (HU13) cuando:
 *  - se inicia sesión,
 *  - cambia alguna actividad (se esperan 1,5 s para juntar cambios seguidos),
 *  - vuelve la conexión,
 *  - el usuario toca "Sincronizar ahora".
 */
class SyncCoordinator(
    scope: CoroutineScope,
    private val session: SessionRepository,
    activities: ActivityRepository,
    private val sync: ActivitySyncRepository,
    connectivity: ConnectivityObserver,
    private val debounceMs: Long = 1_500
) {
    private val _state = MutableStateFlow<SyncState>(SyncState.Idle)
    val state: StateFlow<SyncState> = _state.asStateFlow()
    private val lock = Mutex()
    private var online = true

    init {
        scope.launch {
            combine(session.userId, activities.observeAll(), connectivity.online) { uid, _, isOnline ->
                uid to isOnline
            }.collectLatest { (uid, isOnline) ->
                online = isOnline
                when {
                    uid == null -> _state.value = SyncState.Idle
                    !isOnline -> _state.value = SyncState.Offline
                    else -> {
                        delay(debounceMs)
                        performSync()
                    }
                }
            }
        }
    }

    /** Botón "Sincronizar ahora". */
    suspend fun syncNow() {
        if (!online) {
            _state.value = SyncState.Offline
            return
        }
        performSync()
    }

    private suspend fun performSync() = lock.withLock {
        _state.value = SyncState.Syncing
        // Una vez iniciada, la sincronización termina aunque la pantalla cambie: no queda a medias.
        val result = withContext(NonCancellable) { sync.sync() }
        _state.value = when (result) {
            is ApiResult.Success -> SyncState.Synced(System.currentTimeMillis())
            is ApiResult.Failure -> SyncState.Failed(result.error.userMessage)
        }
    }
}

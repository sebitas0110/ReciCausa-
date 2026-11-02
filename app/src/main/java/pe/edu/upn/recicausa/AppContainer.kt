package pe.edu.upn.recicausa

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import pe.edu.upn.recicausa.data.FilePhotoStore
import pe.edu.upn.recicausa.data.local.ReciCausaDatabase
import pe.edu.upn.recicausa.data.location.FusedLocationProvider
import pe.edu.upn.recicausa.data.location.LocationProvider
import pe.edu.upn.recicausa.data.reminders.ReminderScheduler
import pe.edu.upn.recicausa.data.reminders.WorkManagerReminderScheduler
import pe.edu.upn.recicausa.data.remote.SupabaseApi
import pe.edu.upn.recicausa.data.remote.SupabaseClient
import pe.edu.upn.recicausa.data.repository.ActivityRepository
import pe.edu.upn.recicausa.data.repository.AuthRepository
import pe.edu.upn.recicausa.data.repository.DataStorePreferencesRepository
import pe.edu.upn.recicausa.data.repository.DataStoreSessionRepository
import pe.edu.upn.recicausa.data.repository.DataStoreSyncMarker
import pe.edu.upn.recicausa.data.repository.PhotoStore
import pe.edu.upn.recicausa.data.repository.PreferencesRepository
import pe.edu.upn.recicausa.data.repository.PuntosRepository
import pe.edu.upn.recicausa.data.repository.RemoteAuthRepository
import pe.edu.upn.recicausa.data.repository.RemotePuntosRepository
import pe.edu.upn.recicausa.data.repository.RoomActivityRepository
import pe.edu.upn.recicausa.data.repository.SupabaseActivitySync
import pe.edu.upn.recicausa.data.sync.SyncCoordinator
import pe.edu.upn.recicausa.data.sync.SystemConnectivityObserver

/**
 * Inyección de dependencias manual (suficiente para el alcance del curso).
 * Los repositorios en memoria (InMemoryRepositories.kt) quedan solo para pruebas unitarias.
 *
 * Sprint 5: la cuenta y el respaldo viven en Supabase; Room sigue siendo la fuente de verdad
 * en el teléfono (la app funciona sin conexión después de la primera vez).
 */
class AppContainer(context: Context) {

    private val appContext = context.applicationContext
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val database = ReciCausaDatabase.create(appContext)
    private val session = DataStoreSessionRepository(appContext)

    /** Cliente Retrofit de Supabase (Auth + PostgREST), con renovación automática de sesión. */
    val api: SupabaseApi = SupabaseClient.create(BuildConfig.SUPABASE_URL, BuildConfig.SUPABASE_KEY, session)

    val authRepository: AuthRepository = RemoteAuthRepository(api, session)
    val activityRepository: ActivityRepository = RoomActivityRepository(database.activityDao(), session)
    val puntosRepository: PuntosRepository = RemotePuntosRepository(api, database.puntoCacheDao(), session)
    val preferencesRepository: PreferencesRepository = DataStorePreferencesRepository(appContext)
    val photoStore: PhotoStore = FilePhotoStore(appContext)
    val reminderScheduler: ReminderScheduler = WorkManagerReminderScheduler(appContext)
    val locationProvider: LocationProvider = FusedLocationProvider(appContext)

    val syncCoordinator = SyncCoordinator(
        scope = appScope,
        session = session,
        activities = activityRepository,
        sync = SupabaseActivitySync(api, database.activityDao(), session, DataStoreSyncMarker(appContext)),
        connectivity = SystemConnectivityObserver(appContext)
    )
}

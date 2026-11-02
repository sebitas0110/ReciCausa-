package pe.edu.upn.recicausa.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import pe.edu.upn.recicausa.data.reminders.ReminderScheduler
import pe.edu.upn.recicausa.data.location.LocationProvider
import pe.edu.upn.recicausa.data.remote.ApiError
import pe.edu.upn.recicausa.data.remote.ApiResult
import pe.edu.upn.recicausa.data.repository.PuntoInput
import pe.edu.upn.recicausa.data.repository.PuntosRepository
import pe.edu.upn.recicausa.data.sync.SyncCoordinator
import pe.edu.upn.recicausa.data.sync.SyncState
import pe.edu.upn.recicausa.domain.PuntoConDistancia
import pe.edu.upn.recicausa.domain.PuntoEstado
import pe.edu.upn.recicausa.domain.PuntoFilter
import pe.edu.upn.recicausa.domain.PuntoReciclaje
import pe.edu.upn.recicausa.domain.PuntoValidator
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import pe.edu.upn.recicausa.AppContainer
import pe.edu.upn.recicausa.data.repository.ActivityRepository
import pe.edu.upn.recicausa.data.repository.AuthRepository
import pe.edu.upn.recicausa.data.repository.AuthResult
import pe.edu.upn.recicausa.data.repository.PhotoStore
import pe.edu.upn.recicausa.data.repository.PreferencesRepository
import pe.edu.upn.recicausa.domain.ActivityValidator
import pe.edu.upn.recicausa.domain.AuthValidator
import pe.edu.upn.recicausa.domain.Catalog
import pe.edu.upn.recicausa.domain.CategoryCount
import pe.edu.upn.recicausa.domain.ChallengeProgress
import pe.edu.upn.recicausa.domain.ProgressCalculator
import pe.edu.upn.recicausa.domain.RecycleActivity
import pe.edu.upn.recicausa.domain.User
import pe.edu.upn.recicausa.domain.WasteCategory

// ViewModels con UI State inmutable expuesto como StateFlow.
// Ninguno importa clases de interfaz (Context, View, Compose): se pueden probar sin dispositivo.

// ---------- Sesión (decide qué grafo de navegación se muestra) ----------
sealed interface SessionState {
    data object Loading : SessionState
    data object LoggedOut : SessionState
    data class LoggedIn(val user: User) : SessionState
}

class SessionViewModel(private val auth: AuthRepository) : ViewModel() {
    val state: StateFlow<SessionState> = auth.currentUser
        .map { user -> if (user == null) SessionState.LoggedOut else SessionState.LoggedIn(user) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SessionState.Loading)

    fun logout() {
        viewModelScope.launch { auth.logout() }
    }
}

// ---------- Iniciar sesión ----------
data class LoginUiState(
    val email: String = "",
    val password: String = "",
    val errors: AuthValidator.LoginResult = AuthValidator.LoginResult(),
    val formError: String? = null,
    val loading: Boolean = false
)

class LoginViewModel(private val auth: AuthRepository) : ViewModel() {
    private val _state = MutableStateFlow(LoginUiState())
    val state: StateFlow<LoginUiState> = _state.asStateFlow()

    fun onEmailChange(text: String) = _state.update {
        it.copy(email = text, errors = it.errors.copy(emailError = null), formError = null)
    }

    fun onPasswordChange(text: String) = _state.update {
        it.copy(password = text, errors = it.errors.copy(passwordError = null), formError = null)
    }

    fun submit() {
        val current = _state.value
        if (current.loading) return
        val result = AuthValidator.validateLogin(current.email, current.password)
        if (!result.isValid) {
            _state.update { it.copy(errors = result) }
            return
        }
        _state.update { it.copy(loading = true, formError = null) }
        viewModelScope.launch {
            // Si sale bien, la sesión cambia y la navegación pasa sola a Inicio.
            when (val r = auth.login(current.email, current.password)) {
                is AuthResult.Failure -> _state.update { it.copy(loading = false, formError = r.message) }
                AuthResult.Success -> _state.update { it.copy(loading = false) }
            }
        }
    }
}

// ---------- Crear cuenta ----------
data class SignupUiState(
    val name: String = "",
    val email: String = "",
    val password: String = "",
    val confirm: String = "",
    val errors: AuthValidator.SignupResult = AuthValidator.SignupResult(),
    val formError: String? = null,
    val loading: Boolean = false
)

class SignupViewModel(private val auth: AuthRepository) : ViewModel() {
    private val _state = MutableStateFlow(SignupUiState())
    val state: StateFlow<SignupUiState> = _state.asStateFlow()

    fun onNameChange(text: String) = _state.update {
        it.copy(name = text, errors = it.errors.copy(nameError = null), formError = null)
    }

    fun onEmailChange(text: String) = _state.update {
        it.copy(email = text, errors = it.errors.copy(emailError = null), formError = null)
    }

    fun onPasswordChange(text: String) = _state.update {
        it.copy(password = text, errors = it.errors.copy(passwordError = null), formError = null)
    }

    fun onConfirmChange(text: String) = _state.update {
        it.copy(confirm = text, errors = it.errors.copy(confirmError = null), formError = null)
    }

    fun submit() {
        val current = _state.value
        if (current.loading) return
        val result = AuthValidator.validateSignup(current.name, current.email, current.password, current.confirm)
        if (!result.isValid) {
            _state.update { it.copy(errors = result) }
            return
        }
        _state.update { it.copy(loading = true, formError = null) }
        viewModelScope.launch {
            when (val r = auth.register(current.name, current.email, current.password)) {
                is AuthResult.Failure -> _state.update { it.copy(loading = false, formError = r.message) }
                AuthResult.Success -> _state.update { it.copy(loading = false) }
            }
        }
    }
}

// ---------- Inicio: resumen del progreso ----------
data class HomeUiState(
    val userName: String = "",
    val totalPoints: Int = 0,
    val totalActivities: Int = 0,
    val challengesDone: Int = 0,
    val challengesTotal: Int = 0,
    val nextChallenge: ChallengeProgress? = null,
    val topCategories: List<CategoryCount> = emptyList(),
    val recent: List<RecycleActivity> = emptyList()
)

class HomeViewModel(auth: AuthRepository, activities: ActivityRepository) : ViewModel() {
    val state: StateFlow<HomeUiState> = combine(auth.currentUser, activities.observeAll()) { user, list ->
        val summary = ProgressCalculator.summary(list)
        val challenges = ProgressCalculator.challengeProgress(Catalog.challenges, list)
        HomeUiState(
            userName = user?.name?.trim()?.substringBefore(' ').orEmpty(),
            totalPoints = summary.totalPoints,
            totalActivities = summary.totalActivities,
            challengesDone = challenges.count { it.completed },
            challengesTotal = challenges.size,
            nextChallenge = ProgressCalculator.nextChallenge(challenges),
            topCategories = ProgressCalculator.topCategories(list),
            recent = list.take(3)
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())
}

// ---------- Guía (HU01) ----------
data class GuideUiState(val categories: List<WasteCategory> = Catalog.categories)

class GuideViewModel : ViewModel() {
    private val _state = MutableStateFlow(GuideUiState())
    val state: StateFlow<GuideUiState> = _state.asStateFlow()
}

// ---------- Registro de actividad con foto opcional (HU02, HU03) ----------
data class RegisterUiState(
    val categoryId: String? = null,
    val quantity: String = "1",
    val note: String = "",
    val photoPath: String? = null,
    val errors: ActivityValidator.Result = ActivityValidator.Result(),
    val lastPointsEarned: Int? = null,
    /** Id de la actividad que se edita; null si se está creando una nueva. */
    val editingId: Long? = null,
    val notFound: Boolean = false
) {
    val isEditing: Boolean get() = editingId != null

    /** Puntos que ganaría con los datos actuales; null si aún no se puede calcular. */
    val pointsPreview: Int?
        get() {
            val category = categoryId?.let(Catalog::categoryById) ?: return null
            val qty = quantity.trim().toIntOrNull() ?: return null
            if (qty < 1 || qty > ActivityValidator.MAX_QUANTITY) return null
            return ActivityValidator.pointsFor(category, qty)
        }
}

class RegisterViewModel(
    private val repository: ActivityRepository,
    private val photos: PhotoStore
) : ViewModel() {
    private val _state = MutableStateFlow(RegisterUiState())
    val state: StateFlow<RegisterUiState> = _state.asStateFlow()

    fun onCategoryChange(id: String) = _state.update {
        it.copy(categoryId = id, errors = it.errors.copy(categoryError = null), lastPointsEarned = null)
    }

    fun onQuantityChange(text: String) = _state.update {
        // Solo dígitos y máximo 2 caracteres: evita entradas inválidas desde el teclado.
        it.copy(quantity = text.filter(Char::isDigit).take(2), errors = it.errors.copy(quantityError = null), lastPointsEarned = null)
    }

    /** Botones − y +: mantiene la cantidad entre 1 y el máximo. */
    fun onQuantityStep(delta: Int) {
        val current = _state.value.quantity.trim().toIntOrNull() ?: 1
        onQuantityChange(current.plus(delta).coerceIn(1, ActivityValidator.MAX_QUANTITY).toString())
    }

    fun onNoteChange(text: String) = _state.update {
        it.copy(note = text, errors = it.errors.copy(noteError = null), lastPointsEarned = null)
    }

    // Edición (U de CRUD): se conserva la foto y la fecha originales hasta guardar.
    private var original: RecycleActivity? = null

    /** Carga la actividad [id] en el formulario. Solo actúa una vez por id. */
    fun startEdit(id: Long) {
        if (_state.value.editingId == id) return
        viewModelScope.launch {
            val activity = repository.getById(id)
            if (activity == null) {
                _state.update { it.copy(notFound = true) }
                return@launch
            }
            original = activity
            _state.value = RegisterUiState(
                categoryId = activity.categoryId, quantity = activity.quantity.toString(),
                note = activity.note, photoPath = activity.photoPath, editingId = id
            )
        }
    }

    fun newPhotoFile(): File = photos.newPhotoFile()

    /** La foto original de una actividad guardada solo se borra al guardar los cambios, nunca antes. */
    private fun discard(path: String?) {
        if (path != null && path != original?.photoPath) photos.delete(path)
    }

    /** Guarda la foto recién tomada; si ya había una sin registrar, la reemplaza y borra la anterior. */
    fun onPhotoTaken(path: String) {
        val previous = _state.value.photoPath
        if (previous != path) discard(previous)
        _state.update { it.copy(photoPath = path) }
    }

    fun onPhotoRemoved() {
        discard(_state.value.photoPath)
        _state.update { it.copy(photoPath = null) }
    }

    fun submit(now: Long = System.currentTimeMillis()) {
        val current = _state.value
        val result = ActivityValidator.validate(current.categoryId, current.quantity, current.note)
        if (!result.isValid) {
            _state.update { it.copy(errors = result, lastPointsEarned = null) }
            return
        }
        val category = Catalog.categoryById(current.categoryId!!)!!
        val quantity = current.quantity.trim().toInt()
        val points = ActivityValidator.pointsFor(category, quantity)
        val before = original
        if (current.isEditing && before != null) {
            viewModelScope.launch {
                repository.update(
                    before.copy(
                        categoryId = category.id, quantity = quantity, note = current.note.trim(),
                        points = points, photoPath = current.photoPath
                    )
                )
                // Si la foto cambió o se quitó, la anterior ya no pertenece a nada.
                if (before.photoPath != null && before.photoPath != current.photoPath) photos.delete(before.photoPath)
                original = null
                _state.update { it.copy(photoPath = null, lastPointsEarned = points) }
            }
            return
        }
        viewModelScope.launch {
            repository.add(
                RecycleActivity(
                    categoryId = category.id, quantity = quantity,
                    note = current.note.trim(), points = points, createdAt = now,
                    photoPath = current.photoPath
                )
            )
            // La foto ya pertenece a la actividad guardada: el formulario se reinicia sin borrarla.
            _state.value = RegisterUiState(lastPointsEarned = points)
        }
    }

    override fun onCleared() {
        // Si el usuario salió sin guardar, la foto nueva no quedó asociada a nada: se elimina.
        // (La foto original de una actividad editada se conserva.)
        discard(_state.value.photoPath)
    }
}

// ---------- Historial: listar y eliminar actividades (R y D de CRUD) ----------
data class HistoryUiState(val items: List<RecycleActivity> = emptyList(), val loaded: Boolean = false)

class HistoryViewModel(
    private val repository: ActivityRepository,
    private val photos: PhotoStore
) : ViewModel() {
    val state: StateFlow<HistoryUiState> = repository.observeAll()
        .map { HistoryUiState(it, loaded = true) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HistoryUiState())

    /** Elimina la actividad y su foto. Los puntos y retos se recalculan solos. */
    fun delete(id: Long) {
        viewModelScope.launch {
            val activity = repository.getById(id) ?: return@launch
            repository.delete(id)
            photos.delete(activity.photoPath)
        }
    }
}

// ---------- Retos (HU04) ----------
data class ChallengesUiState(val items: List<ChallengeProgress> = emptyList())

class ChallengesViewModel(repository: ActivityRepository) : ViewModel() {
    val state: StateFlow<ChallengesUiState> = repository.observeAll()
        .map { ChallengesUiState(ProgressCalculator.challengeProgress(Catalog.challenges, it)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ChallengesUiState())
}

// ---------- Recordatorios (HU06) y respaldo en la nube (HU13) ----------
data class SettingsUiState(
    val remindersEnabled: Boolean = false,
    val reminderHour: Int = 18,
    val email: String = "",
    val sync: SyncState = SyncState.Idle
)

class SettingsViewModel(
    private val preferences: PreferencesRepository,
    private val scheduler: ReminderScheduler,
    auth: AuthRepository,
    private val syncCoordinator: SyncCoordinator
) : ViewModel() {
    val state: StateFlow<SettingsUiState> = combine(
        preferences.remindersEnabled, preferences.reminderHour, auth.currentUser, syncCoordinator.state
    ) { enabled, hour, user, sync -> SettingsUiState(enabled, hour, user?.email.orEmpty(), sync) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    fun onRemindersChange(enabled: Boolean) {
        viewModelScope.launch {
            preferences.setRemindersEnabled(enabled)
            if (enabled) scheduler.schedule(preferences.reminderHour.first()) else scheduler.cancel()
        }
    }

    fun onHourChange(hour: Int) {
        viewModelScope.launch {
            preferences.setReminderHour(hour)
            if (preferences.remindersEnabled.first()) scheduler.schedule(hour)
        }
    }

    /** "Sincronizar ahora" (CA13.3). */
    fun syncNow() {
        viewModelScope.launch { syncCoordinator.syncNow() }
    }
}

// ---------- Puntos de reciclaje: lista, filtros, mapa y cercanía (HU10, HU11) ----------
data class PuntosUiState(
    val items: List<PuntoConDistancia> = emptyList(),
    val totalDownloaded: Int = 0,
    val categoryFilter: String? = null,
    val loading: Boolean = true,
    /** Mensaje del último intento de descarga que falló; null si salió bien. */
    val error: String? = null,
    val nearMe: Boolean = false,
    val locating: Boolean = false,
    val locationNotice: String? = null
) {
    /** Hay datos descargados antes: se muestran aunque la red falle (CA10.4). */
    val showingCache: Boolean get() = error != null && totalDownloaded > 0
}

class PuntosViewModel(
    private val repository: PuntosRepository,
    private val locator: LocationProvider
) : ViewModel() {

    private data class Ui(
        val filter: String? = null, val lat: Double? = null, val lng: Double? = null,
        val loading: Boolean = true, val error: String? = null,
        val locating: Boolean = false, val notice: String? = null
    )

    private val ui = MutableStateFlow(Ui())

    val state: StateFlow<PuntosUiState> = combine(repository.observeAprobados(), ui) { all, u ->
        val sorted = PuntoFilter.sorted(PuntoFilter.byCategory(all, u.filter), u.lat, u.lng)
        PuntosUiState(
            items = sorted, totalDownloaded = all.size, categoryFilter = u.filter,
            loading = u.loading, error = u.error, nearMe = u.lat != null && u.lng != null,
            locating = u.locating, locationNotice = u.notice
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PuntosUiState())

    init { refresh() }

    fun refresh() {
        ui.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            val result = repository.refresh()
            ui.update {
                it.copy(loading = false, error = (result as? ApiResult.Failure)?.error?.userMessage)
            }
        }
    }

    fun onFilterChange(categoryId: String?) = ui.update { it.copy(filter = categoryId) }

    /** El permiso ya fue concedido: se pide la ubicación una sola vez y se usa solo en memoria. */
    fun useMyLocation() {
        ui.update { it.copy(locating = true, notice = null) }
        viewModelScope.launch {
            val point = locator.currentLocation()
            ui.update {
                if (point == null) it.copy(locating = false, notice = "No pudimos obtener tu ubicación. Revisa que el GPS esté activo.")
                else it.copy(locating = false, lat = point.latitude, lng = point.longitude, notice = null)
            }
        }
    }

    /** CA11.2: si se rechaza el permiso, la lista sigue funcionando ordenada por nombre. */
    fun onLocationDenied() = ui.update {
        it.copy(lat = null, lng = null, notice = "Sin permiso de ubicación: la lista sigue ordenada por nombre.")
    }

    fun clearNearMe() = ui.update { it.copy(lat = null, lng = null, notice = null) }
}

// ---------- Mis sugerencias: leer, editar, eliminar (HU12) ----------
data class MyPointsUiState(
    val items: List<PuntoReciclaje> = emptyList(),
    val loading: Boolean = true,
    val error: String? = null,
    val message: String? = null
)

class MyPointsViewModel(private val repository: PuntosRepository) : ViewModel() {
    private val _state = MutableStateFlow(MyPointsUiState())
    val state: StateFlow<MyPointsUiState> = _state.asStateFlow()

    init { load() }

    fun load() {
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            when (val r = repository.misSugerencias()) {
                is ApiResult.Success -> _state.update { it.copy(items = r.value, loading = false) }
                is ApiResult.Failure -> _state.update { it.copy(loading = false, error = r.error.userMessage) }
            }
        }
    }

    /** CA12.3: solo se pueden borrar sugerencias pendientes (también lo exige la base con RLS). */
    fun delete(id: Long) {
        viewModelScope.launch {
            when (val r = repository.eliminar(id)) {
                is ApiResult.Success -> {
                    _state.update { s -> s.copy(items = s.items.filterNot { it.id == id }, message = "Sugerencia eliminada.") }
                }
                is ApiResult.Failure -> _state.update {
                    it.copy(message = if (r.error == ApiError.Forbidden) "Solo puedes eliminar sugerencias pendientes." else r.error.userMessage)
                }
            }
        }
    }

    fun messageShown() = _state.update { it.copy(message = null) }
}

// ---------- Sugerir / editar un punto (HU12) ----------
data class SuggestUiState(
    val nombre: String = "",
    val direccion: String = "",
    val descripcion: String = "",
    val horario: String = "",
    val telefono: String = "",
    val categorias: Set<String> = emptySet(),
    val latitud: Double? = null,
    val longitud: Double? = null,
    val errors: PuntoValidator.Result = PuntoValidator.Result(),
    val formError: String? = null,
    val saving: Boolean = false,
    val locating: Boolean = false,
    val saved: Boolean = false,
    val editingId: Long? = null,
    val loadingEdit: Boolean = false
) {
    val isEditing: Boolean get() = editingId != null
}

class SuggestPointViewModel(
    private val repository: PuntosRepository,
    private val locator: LocationProvider
) : ViewModel() {
    private val _state = MutableStateFlow(SuggestUiState())
    val state: StateFlow<SuggestUiState> = _state.asStateFlow()

    fun onNombre(v: String) = _state.update { it.copy(nombre = v.take(PuntoValidator.MAX_NOMBRE), errors = it.errors.copy(nombreError = null), formError = null) }
    fun onDireccion(v: String) = _state.update { it.copy(direccion = v.take(PuntoValidator.MAX_DIRECCION), errors = it.errors.copy(direccionError = null), formError = null) }
    fun onDescripcion(v: String) = _state.update { it.copy(descripcion = v.take(PuntoValidator.MAX_DESCRIPCION), errors = it.errors.copy(descripcionError = null)) }
    fun onHorario(v: String) = _state.update { it.copy(horario = v.take(PuntoValidator.MAX_HORARIO), errors = it.errors.copy(horarioError = null)) }
    fun onTelefono(v: String) = _state.update { it.copy(telefono = v.take(PuntoValidator.MAX_TELEFONO), errors = it.errors.copy(telefonoError = null)) }

    fun onToggleCategoria(id: String) = _state.update {
        val next = if (id in it.categorias) it.categorias - id else it.categorias + id
        it.copy(categorias = next, errors = it.errors.copy(categoriasError = null), formError = null)
    }

    /** El usuario tocó el mapa. */
    fun onLocationPicked(lat: Double, lng: Double) = _state.update {
        it.copy(latitud = lat, longitud = lng, errors = it.errors.copy(ubicacionError = null), formError = null)
    }

    /** Con el permiso ya concedido, coloca el marcador en la ubicación actual. */
    fun useMyLocation() {
        _state.update { it.copy(locating = true) }
        viewModelScope.launch {
            val p = locator.currentLocation()
            _state.update {
                if (p == null) it.copy(locating = false, formError = "No pudimos obtener tu ubicación. Toca el mapa para marcarla.")
                else it.copy(locating = false, latitud = p.latitude, longitud = p.longitude, errors = it.errors.copy(ubicacionError = null), formError = null)
            }
        }
    }

    fun onLocationDenied() = _state.update {
        it.copy(formError = "Sin permiso de ubicación. Puedes marcar el punto tocando el mapa.")
    }

    /** Carga una sugerencia existente en el formulario (U de CRUD). Solo actúa una vez por id. */
    fun startEdit(id: Long) {
        if (_state.value.editingId == id) return
        _state.update { it.copy(editingId = id, loadingEdit = true) }
        viewModelScope.launch {
            when (val r = repository.misSugerencias()) {
                is ApiResult.Failure -> _state.update { it.copy(loadingEdit = false, formError = r.error.userMessage) }
                is ApiResult.Success -> {
                    val p = r.value.firstOrNull { it.id == id }
                    if (p == null || p.estado != PuntoEstado.PENDIENTE) {
                        _state.update { it.copy(loadingEdit = false, formError = "Solo puedes editar sugerencias pendientes.") }
                    } else {
                        _state.value = SuggestUiState(
                            nombre = p.nombre, direccion = p.direccion, descripcion = p.descripcion,
                            horario = p.horario, telefono = p.telefono, categorias = p.categorias.toSet(),
                            latitud = p.latitud, longitud = p.longitud, editingId = id
                        )
                    }
                }
            }
        }
    }

    fun submit() {
        val s = _state.value
        if (s.saving) return
        val result = PuntoValidator.validate(s.nombre, s.direccion, s.categorias, s.latitud, s.longitud, s.descripcion, s.horario, s.telefono)
        if (!result.isValid) {
            _state.update { it.copy(errors = result) }
            return
        }
        val input = PuntoInput(
            nombre = s.nombre, descripcion = s.descripcion, direccion = s.direccion,
            categorias = s.categorias.toList(), latitud = s.latitud!!, longitud = s.longitud!!,
            horario = s.horario, telefono = s.telefono
        )
        _state.update { it.copy(saving = true, formError = null) }
        viewModelScope.launch {
            val r = if (s.editingId != null) repository.editar(s.editingId, input) else repository.sugerir(input)
            when (r) {
                is ApiResult.Success -> _state.update { it.copy(saving = false, saved = true) }
                is ApiResult.Failure -> _state.update {
                    it.copy(
                        saving = false,
                        formError = if (s.isEditing && r.error == ApiError.Forbidden) "Solo puedes editar sugerencias pendientes." else r.error.userMessage
                    )
                }
            }
        }
    }
}

// ---------- Fábrica ----------
@Suppress("UNCHECKED_CAST")
class ViewModelFactory(private val container: AppContainer) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T = when (modelClass) {
        SessionViewModel::class.java -> SessionViewModel(container.authRepository)
        LoginViewModel::class.java -> LoginViewModel(container.authRepository)
        SignupViewModel::class.java -> SignupViewModel(container.authRepository)
        HomeViewModel::class.java -> HomeViewModel(container.authRepository, container.activityRepository)
        GuideViewModel::class.java -> GuideViewModel()
        RegisterViewModel::class.java -> RegisterViewModel(container.activityRepository, container.photoStore)
        HistoryViewModel::class.java -> HistoryViewModel(container.activityRepository, container.photoStore)
        ChallengesViewModel::class.java -> ChallengesViewModel(container.activityRepository)
        SettingsViewModel::class.java -> SettingsViewModel(
            container.preferencesRepository, container.reminderScheduler,
            container.authRepository, container.syncCoordinator
        )
        PuntosViewModel::class.java -> PuntosViewModel(container.puntosRepository, container.locationProvider)
        MyPointsViewModel::class.java -> MyPointsViewModel(container.puntosRepository)
        SuggestPointViewModel::class.java -> SuggestPointViewModel(container.puntosRepository, container.locationProvider)
        else -> throw IllegalArgumentException("ViewModel desconocido: ${modelClass.name}")
    } as T
}

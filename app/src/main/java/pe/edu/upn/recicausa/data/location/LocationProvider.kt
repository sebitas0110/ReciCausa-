package pe.edu.upn.recicausa.data.location

import android.annotation.SuppressLint
import android.content.Context
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await

// Sprint 5 - Ubicación aproximada (semana 11). Se obtiene una sola vez, en el momento en que el
// usuario toca "Cerca de mí", y se usa solo en memoria: no se guarda ni se envía (CA11.3).

data class GeoPoint(val latitude: Double, val longitude: Double)

interface LocationProvider {
    /** Ubicación actual, o null si no hay permiso, GPS apagado o falla. */
    suspend fun currentLocation(): GeoPoint?
}

class FusedLocationProvider(context: Context) : LocationProvider {
    private val client = LocationServices.getFusedLocationProviderClient(context.applicationContext)

    // El permiso lo verifica y solicita la pantalla antes de llamar a esta función.
    @SuppressLint("MissingPermission")
    override suspend fun currentLocation(): GeoPoint? = try {
        client.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, null).await()
            ?.let { GeoPoint(it.latitude, it.longitude) }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) { // SecurityException, ApiException, etc.
        null
    }
}

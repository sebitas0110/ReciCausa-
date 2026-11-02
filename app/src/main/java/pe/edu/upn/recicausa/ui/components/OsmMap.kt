package pe.edu.upn.recicausa.ui.components

import android.annotation.SuppressLint
import android.view.MotionEvent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint as OsmPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.CopyrightOverlay
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import pe.edu.upn.recicausa.domain.PuntoConDistancia

// Sprint 5 - Mapa de OpenStreetMap con osmdroid (sin clave de API).
// Atribución obligatoria de OSM: "© OpenStreetMap contributors" (CopyrightOverlay).

/** Centro del mapa cuando aún no hay ubicación: Plaza de Armas de Trujillo. */
val TRUJILLO_CENTER = OsmPoint(-8.1116, -79.0288)

@SuppressLint("ClickableViewAccessibility")
@Composable
private fun rememberMapView(center: OsmPoint, zoom: Double, insideScroll: Boolean): MapView {
    val context = LocalContext.current
    val mapView = remember {
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            isTilesScaledToDpi = true
            zoomController.setVisibility(CustomZoomButtonsController.Visibility.SHOW_AND_FADEOUT)
            controller.setZoom(zoom)
            controller.setCenter(center)
            overlays.add(CopyrightOverlay(context))
            if (insideScroll) {
                // Dentro de una pantalla con scroll, el mapa debe recibir sus propios gestos.
                setOnTouchListener { v, ev ->
                    when (ev.actionMasked) {
                        MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE ->
                            v.parent?.requestDisallowInterceptTouchEvent(true)
                        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL ->
                            v.parent?.requestDisallowInterceptTouchEvent(false)
                    }
                    false
                }
            }
        }
    }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, mapView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            mapView.onPause()
            mapView.onDetach()
        }
    }
    return mapView
}

private fun MapView.replaceMarkers(markers: List<Marker>) {
    overlays.removeAll { it is Marker }
    overlays.addAll(markers)
    invalidate()
}

/** CA10.3: un marcador por punto; al tocarlo muestra el nombre y la dirección. */
@Composable
fun PuntosOsmMap(items: List<PuntoConDistancia>, modifier: Modifier = Modifier) {
    val mapView = rememberMapView(TRUJILLO_CENTER, 13.0, insideScroll = false)
    AndroidView(
        factory = { mapView },
        modifier = modifier,
        update = { map ->
            map.replaceMarkers(items.map { item ->
                Marker(map).apply {
                    position = OsmPoint(item.punto.latitud, item.punto.longitud)
                    title = item.punto.nombre
                    snippet = item.punto.direccion
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                }
            })
        }
    )
}

/** Mapa para marcar la ubicación de un punto sugerido (toque simple en el mapa). */
@Composable
fun LocationPickerOsm(
    latitud: Double?,
    longitud: Double?,
    onPick: (Double, Double) -> Unit,
    modifier: Modifier = Modifier
) {
    val mapView = rememberMapView(TRUJILLO_CENTER, 14.0, insideScroll = true)
    val currentOnPick by rememberUpdatedState(onPick)

    DisposableEffect(mapView) {
        val tapOverlay = MapEventsOverlay(object : MapEventsReceiver {
            override fun singleTapConfirmedHelper(p: OsmPoint?): Boolean {
                p?.let { currentOnPick(it.latitude, it.longitude) }
                return true
            }
            override fun longPressHelper(p: OsmPoint?): Boolean = false
        })
        mapView.overlays.add(0, tapOverlay)
        onDispose { mapView.overlays.remove(tapOverlay) }
    }
    // Si la ubicación cambia (p. ej. "Usar mi ubicación"), el mapa se centra en ella.
    LaunchedEffect(latitud, longitud) {
        if (latitud != null && longitud != null) {
            mapView.controller.setZoom(17.0)
            mapView.controller.animateTo(OsmPoint(latitud, longitud))
        }
    }
    AndroidView(
        factory = { mapView },
        modifier = modifier,
        update = { map ->
            map.replaceMarkers(
                if (latitud != null && longitud != null) listOf(
                    Marker(map).apply {
                        position = OsmPoint(latitud, longitud)
                        title = "Ubicación del punto"
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                    }
                ) else emptyList()
            )
        }
    )
}

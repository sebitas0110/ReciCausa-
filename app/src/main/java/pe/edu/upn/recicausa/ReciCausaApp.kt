package pe.edu.upn.recicausa

import android.app.Application
import java.io.File
import org.osmdroid.config.Configuration

class ReciCausaApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        // OpenStreetMap (osmdroid): exige identificarse con un User-Agent y guardar las teselas
        // en la carpeta privada de la app, así no hace falta ningún permiso de almacenamiento.
        Configuration.getInstance().apply {
            userAgentValue = packageName
            osmdroidBasePath = File(cacheDir, "osmdroid")
            osmdroidTileCache = File(cacheDir, "osmdroid/tiles")
        }
        container = AppContainer(this)
    }
}

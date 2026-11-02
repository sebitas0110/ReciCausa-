package pe.edu.upn.recicausa.data

import android.content.Context
import java.io.File
import pe.edu.upn.recicausa.data.repository.PhotoStore

// Las fotos viven en la carpeta privada de la app (filesDir/photos): ninguna otra app las ve,
// no se piden permisos de almacenamiento y se borran al desinstalar.

class FilePhotoStore(context: Context) : PhotoStore {

    private val dir = File(context.filesDir, "photos").apply { mkdirs() }

    override fun newPhotoFile(): File = File(dir, "act_${System.currentTimeMillis()}.jpg")

    override fun delete(path: String?) {
        if (path == null) return
        val file = File(path)
        // Solo borra archivos dentro de la carpeta de fotos de la app.
        if (file.canonicalFile.parentFile == dir.canonicalFile && file.exists()) file.delete()
    }
}

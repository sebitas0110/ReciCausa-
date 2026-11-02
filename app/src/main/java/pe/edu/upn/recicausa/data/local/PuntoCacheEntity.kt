package pe.edu.upn.recicausa.data.local

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow
import pe.edu.upn.recicausa.domain.PuntoEstado
import pe.edu.upn.recicausa.domain.PuntoReciclaje

// Sprint 5 - Caché de los últimos puntos descargados (CA10.4: sin conexión se ven los últimos puntos).

@Entity(tableName = "points_cache")
data class PuntoCacheEntity(
    @PrimaryKey val id: Long,
    val nombre: String,
    val descripcion: String,
    val direccion: String,
    val distrito: String,
    val latitud: Double,
    val longitud: Double,
    /** Ids de categoría separados por coma. */
    val categorias: String,
    val horario: String,
    val telefono: String
)

fun PuntoCacheEntity.toDomain() = PuntoReciclaje(
    id = id, nombre = nombre, descripcion = descripcion, direccion = direccion, distrito = distrito,
    latitud = latitud, longitud = longitud,
    categorias = categorias.split(',').filter { it.isNotBlank() },
    horario = horario, telefono = telefono, estado = PuntoEstado.APROBADO
)

fun PuntoReciclaje.toEntity() = PuntoCacheEntity(
    id = id, nombre = nombre, descripcion = descripcion, direccion = direccion, distrito = distrito,
    latitud = latitud, longitud = longitud, categorias = categorias.joinToString(","),
    horario = horario, telefono = telefono
)

@Dao
abstract class PuntoCacheDao {
    @Query("SELECT * FROM points_cache ORDER BY nombre COLLATE NOCASE")
    abstract fun observeAll(): Flow<List<PuntoCacheEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertAll(list: List<PuntoCacheEntity>)

    @Query("DELETE FROM points_cache")
    abstract suspend fun deleteAll()

    /** Reemplaza toda la caché en una sola transacción: nunca queda a medias. */
    @Transaction
    open suspend fun replaceAll(list: List<PuntoCacheEntity>) {
        deleteAll()
        insertAll(list)
    }
}

package pe.edu.upn.recicausa.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

// Acceso a datos. Devuelve Flow: la UI se actualiza sola cuando cambia la tabla.
// user_id es el uuid de la cuenta de Supabase (Sprint 5).

@Dao
interface ActivityDao {
    @Query("SELECT * FROM activities WHERE user_id = :userId ORDER BY created_at DESC")
    fun observeByUser(userId: String): Flow<List<ActivityEntity>>

    @Query("SELECT * FROM activities WHERE user_id = :userId")
    suspend fun getAllByUser(userId: String): List<ActivityEntity>

    @Query("SELECT * FROM activities WHERE id = :id AND user_id = :userId LIMIT 1")
    suspend fun getById(id: Long, userId: String): ActivityEntity?

    @Insert
    suspend fun insert(entity: ActivityEntity): Long

    /** Restauración desde la nube: conserva el id original y no pisa lo que ya existe. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAllIgnore(entities: List<ActivityEntity>)

    @Update
    suspend fun update(entity: ActivityEntity)

    @Query("DELETE FROM activities WHERE id = :id AND user_id = :userId")
    suspend fun deleteById(id: Long, userId: String)
}

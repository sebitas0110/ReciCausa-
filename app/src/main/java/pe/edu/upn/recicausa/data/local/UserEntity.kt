package pe.edu.upn.recicausa.data.local

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import pe.edu.upn.recicausa.domain.User

// Sprint 5: las cuentas pasaron a Supabase Auth. Esta tabla queda solo por compatibilidad con la
// base de datos de versiones anteriores (no se escribe ni se lee desde la app).
@Entity(tableName = "users", indices = [Index(value = ["email"], unique = true)])
data class UserEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val email: String,
    @ColumnInfo(name = "password_hash") val passwordHash: String,
    val salt: String,
    @ColumnInfo(name = "created_at") val createdAt: Long
)

fun UserEntity.toDomain() = User(id = id.toString(), name = name, email = email)

@Dao
interface UserDao {
    @Query("SELECT * FROM users WHERE email = :email LIMIT 1")
    suspend fun findByEmail(email: String): UserEntity?

    @Query("SELECT * FROM users WHERE id = :id")
    fun observeById(id: Long): Flow<UserEntity?>

    @Insert
    suspend fun insert(entity: UserEntity): Long
}

package pe.edu.upn.recicausa.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import pe.edu.upn.recicausa.domain.RecycleActivity

@Entity(tableName = "activities", indices = [Index("user_id")])
data class ActivityEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "user_id") val userId: String,
    @ColumnInfo(name = "category_id") val categoryId: String,
    val quantity: Int,
    val note: String,
    val points: Int,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "photo_path") val photoPath: String?
)

fun ActivityEntity.toDomain() = RecycleActivity(
    id = id, categoryId = categoryId, quantity = quantity,
    note = note, points = points, createdAt = createdAt, photoPath = photoPath
)

fun RecycleActivity.toEntity(userId: String) = ActivityEntity(
    id = id, userId = userId, categoryId = categoryId, quantity = quantity,
    note = note, points = points, createdAt = createdAt, photoPath = photoPath
)

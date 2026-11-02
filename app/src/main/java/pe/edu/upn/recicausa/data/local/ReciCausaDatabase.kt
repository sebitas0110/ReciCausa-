package pe.edu.upn.recicausa.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [ActivityEntity::class, UserEntity::class, PuntoCacheEntity::class],
    version = 3,
    exportSchema = false
)
abstract class ReciCausaDatabase : RoomDatabase() {
    abstract fun activityDao(): ActivityDao
    abstract fun userDao(): UserDao
    abstract fun puntoCacheDao(): PuntoCacheDao

    companion object {
        /**
         * 2 -> 3 (Sprint 5): activities.user_id pasa de INTEGER a TEXT (uuid de Supabase) y se crea
         * points_cache. Conserva usuarios y actividades. Las actividades de cuentas locales antiguas
         * quedan guardadas, pero solo se muestran a una cuenta con ese mismo id.
         */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE activities_new (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, user_id TEXT NOT NULL, " +
                        "category_id TEXT NOT NULL, quantity INTEGER NOT NULL, note TEXT NOT NULL, " +
                        "points INTEGER NOT NULL, created_at INTEGER NOT NULL, photo_path TEXT)"
                )
                db.execSQL(
                    "INSERT INTO activities_new (id, user_id, category_id, quantity, note, points, created_at, photo_path) " +
                        "SELECT id, CAST(user_id AS TEXT), category_id, quantity, note, points, created_at, photo_path FROM activities"
                )
                db.execSQL("DROP TABLE activities")
                db.execSQL("ALTER TABLE activities_new RENAME TO activities")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_activities_user_id ON activities (user_id)")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS points_cache (" +
                        "id INTEGER PRIMARY KEY NOT NULL, nombre TEXT NOT NULL, descripcion TEXT NOT NULL, " +
                        "direccion TEXT NOT NULL, distrito TEXT NOT NULL, latitud REAL NOT NULL, " +
                        "longitud REAL NOT NULL, categorias TEXT NOT NULL, horario TEXT NOT NULL, telefono TEXT NOT NULL)"
                )
            }
        }

        fun create(context: Context): ReciCausaDatabase =
            Room.databaseBuilder(context, ReciCausaDatabase::class.java, "recicausa.db")
                .addMigrations(MIGRATION_2_3)
                .fallbackToDestructiveMigrationFrom(1)
                .build()
    }
}

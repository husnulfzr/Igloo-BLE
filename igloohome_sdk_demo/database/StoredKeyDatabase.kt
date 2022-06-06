package co.igloohome.igloohome_sdk_demo.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [StoredKey::class], version = 1, exportSchema = false)
abstract class StoredKeyDatabase : RoomDatabase() {

    abstract val storedKeyDatabaseDao: StoredKeyDao

    companion object {

        @Volatile
        private var INSTANCE: StoredKeyDatabase? = null

        fun getInstance(context: Context): StoredKeyDatabase {
            synchronized(this) {
                return INSTANCE ?: run {
                    INSTANCE = Room.databaseBuilder(
                        context.applicationContext,
                        StoredKeyDatabase::class.java,
                        "stored_key_database"
                    )
                        .fallbackToDestructiveMigration()
                        .build()
                    INSTANCE!!
                }
            }
        }
    }
}

package co.igloohome.igloohome_sdk_demo.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface StoredKeyDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(key: StoredKey)

    @Query("UPDATE StoredKey SET masterPin = :masterPin WHERE lockName = :lockName")
    suspend fun updateMasterPin(lockName: String, masterPin: String)

    @Query("SELECT * FROM StoredKey WHERE lockName = :lockName")
    suspend fun getStoredKey(lockName: String): StoredKey?

    @Query("SELECT * FROM StoredKey")
    suspend fun getAll(): List<StoredKey>?

    @Query("DELETE FROM StoredKey WHERE lockName = :lockName")
    suspend fun deleteStoredKey(lockName: String)

}
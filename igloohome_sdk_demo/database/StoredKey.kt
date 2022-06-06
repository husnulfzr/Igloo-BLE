package co.igloohome.igloohome_sdk_demo.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity
data class StoredKey(
    @PrimaryKey
    val lockName: String,
    val key: String,
    var masterPin: String
)
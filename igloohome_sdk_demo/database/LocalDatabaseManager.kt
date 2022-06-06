package co.igloohome.igloohome_sdk_demo.database

import android.content.Context
import io.reactivex.Single
import kotlinx.coroutines.*


class LocalDatabaseManager(context: Context) {
    private val database by lazy {
        StoredKeyDatabase.getInstance(context).storedKeyDatabaseDao
    }
    private val job = Job()
    private val databaseScope = CoroutineScope(Dispatchers.Main + job)

    fun getAdminKeyFromLocalDb(lockName: String): Single<StoredKey> {
        return Single.create { emitter ->
            databaseScope.launch {
                withContext(Dispatchers.IO) {
                    database.getStoredKey(lockName)
                }?.also {
                    emitter.onSuccess(it)
                } ?: run {
                    emitter.onError(Throwable("Error locating key in local storage"))
                }
            }
        }
    }

    fun saveKeyToLocalDb(storedKey: StoredKey) {
        databaseScope.launch {
            withContext(Dispatchers.IO) {
                database.insert(storedKey)
            }
        }
    }

    fun deleteKeyFromLocalDb(lockName: String) {
        databaseScope.launch {
            withContext(Dispatchers.IO) {
                database.deleteStoredKey(lockName)
            }
        }
    }

    fun updatePinOnLocalDb(lockName: String, pin: String) {
        databaseScope.launch {
            withContext(Dispatchers.IO) {
                database.updateMasterPin(lockName, pin)
            }
        }
    }

    fun cancelLocalDbJob() {
        job.cancel()
    }
}
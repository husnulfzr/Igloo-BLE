package co.igloohome.igloohome_sdk_demo.lock

import android.bluetooth.le.ScanSettings
import android.content.Context
import co.igloohome.ble.lock.BleManager
import co.igloohome.ble.lock.IglooLock
import io.reactivex.Single
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import java.lang.RuntimeException
import java.util.concurrent.TimeUnit


/**
 * Connection Manager
 *
 * Manages the connection and scanning disposables.
 *
 * Also, the getConnectedLock() method helps ensure that the lock is connected before a Bluetooth
 * command is performed, provided that the key is valid.
 * See the unlock command in LockFragment class for the usage example.
 *
 * Using this class is optional.
 */
class ConnectionManager(ctx: Context) {
    private val context = ctx

    //Scanning disposable
    private var scanDisposable: Disposable? = null

    //Connection disposables for all the locks
    private var connectionDisposables = hashMapOf<String, Disposable?>()

    //Key for connection to each lock. Map<lock name, key string>
    private var keys = hashMapOf<String, String?>()

    /**
     * Set the key of a lock, to be used for future connection
     */
    fun setKey(lockName: String, key: String?) {
        keys.put(lockName, key)
    }

    private fun getKey(lockName: String) : String? {
        return keys.get(lockName)
    }

    /**
     * Basic connect, the start of a pairing process.
     * The key is null
     *
     * @param lock The lock to be connected
     * @return IglooLock as Single
     */
    fun connectForPairing(lock: IglooLock) : Single<IglooLock> {
        return connect(lock, null)
    }

    /**
     * Connect to lock
     * Before a Bluetooth command is performed.
     * The key for the connection must firstly exist.
     *
     * @param lock The lock to be connected
     * @return IglooLock as Single
     */
    fun connect(lock: IglooLock) : Single<IglooLock> {
        val key = keys.get(lock.name)
            ?: return Single.error(RuntimeException("Key of ${lock.name} is null!!"))
        return connect(lock, key)
    }

    private fun connect(lock: IglooLock, key: String?) : Single<IglooLock> {
        return Single.create<IglooLock> { emitter ->
            connectionDisposables[lock.name]?.dispose()
            connectionDisposables[lock.name] = lock.connectByStringKey(context, key)
                .subscribeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    { lockConnected ->
                        emitter.onSuccess(lockConnected)
                    },
                    {
                        connectionDisposables[lock.name]?.dispose()
                        connectionDisposables[lock.name] = null
                        emitter.onError(RuntimeException(it.message))
                    }
                )
        }
    }

    /**
     * Get connected lock.
     *
     * To be called before performing a Bluetooth command.
     * The purpose is to ensure that the lock is connected before performing a Bluetooth command,
     * provided that the key is valid
     *
     * If the lock is already connected, it simply returns the lock. Otherwise, it scans then connects,
     * then returns the connected lock.
     *
     * @param lock The lock to be checked
     * @return IglooLock The connected lock.
     */
    fun getConnectedLock(
        lock: IglooLock)
    : Single<IglooLock> {
        val key = getKey(lock.name)
            ?: return Single.error(RuntimeException("Key of ${lock.name} is null!!"))
        return if (isLockConnected(lock)) {
            Single.just(lock)
        } else {
            scanByLockId(lock.name)
                .flatMap { foundLock ->
                    connect(foundLock, key)
                }
        }
    }

    /**
     * Disconnect this lock
     *
     * @param lock The lock to be disconnected
     */
    fun disconnectLock(lock: IglooLock) {
        connectionDisposables[lock.name]?.dispose()
        connectionDisposables[lock.name] = null
    }

    private fun isLockConnected(lock: IglooLock) : Boolean {
        return connectionDisposables[lock.name] != null
    }

    /**
     * Scan by lock Id
     *
     * Scan and return IglooLock identified by its Id
     *
     * @param lockId The lock Id
     * @return IglooLock as Single
     */
    fun scanByLockId(lockId: String) : Single<IglooLock> {
        return Single.create<IglooLock> { emitter ->
            scanDisposable?.dispose()
            scanDisposable = BleManager(context)
                .scan(
                    ScanSettings.Builder()
                        .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                        .build()
                )
                .observeOn(AndroidSchedulers.mainThread())
                .subscribeOn(AndroidSchedulers.mainThread())
                .filter { it.name == lockId }
                .timeout(20, TimeUnit.SECONDS)
                .subscribe(
                    { lockFound ->
                        emitter.onSuccess(lockFound)
                    },
                    {
                        emitter.onError(RuntimeException(it.message))
                    }
                )

            emitter.setCancellable {
                scanDisposable?.dispose()
                scanDisposable = null
            }
        }
    }

    private fun disposeAll(lock: IglooLock) {
        connectionDisposables[lock.name]?.dispose()
        connectionDisposables[lock.name] = null
        scanDisposable?.dispose()
        scanDisposable = null
    }
}

package co.igloohome.igloohome_sdk_demo

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import co.igloohome.ble.lock.IglooLock

class LockViewModel : ViewModel() {

    private var _selectedLock = MutableLiveData<IglooLock>()

    val lock : LiveData<IglooLock>
        get() = _selectedLock

    fun onLockSelected(lock: IglooLock) {
        _selectedLock.value = lock
    }
}
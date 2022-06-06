package co.igloohome.igloohome_sdk_demo

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import co.igloohome.ble.lock.BleManager
import io.reactivex.disposables.Disposable
import timber.log.Timber
import java.util.*

class MainActivity : AppCompatActivity() {
    val permissionsRequired: Array<String> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        arrayOf(Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT)
    } else {
        arrayOf(Manifest.permission.BLUETOOTH,
            Manifest.permission.ACCESS_FINE_LOCATION)
    }
    private val permissionRequestCode = 1

    companion object {
        //API Key needed. Please contact support@igloohome.co to get the API key.
        //TODO:: Change this
        val SERVER_API_KEY = ""
    }

    private val disposables = ArrayList<Disposable>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        if (SERVER_API_KEY.isBlank()) throw Exception("Please fill in SERVER_API_KEY in MainActivity first.")

        Timber.plant(Timber.DebugTree())

        //Check if permissions are granted on start.
        val permissionsNotGranted = ArrayList<String>()
        for (permission in permissionsRequired) {
            if (checkCallingOrSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
                Timber.e("$permission not yet granted.")
                permissionsNotGranted.add(permission)
            }
        }

        //Request permissions
        if (permissionsNotGranted.size != 0) {
            Timber.e("Requested permissions not yet granted.")
            ActivityCompat.requestPermissions(this, permissionsNotGranted.toTypedArray(), permissionRequestCode)
        } else {
            Timber.d("All permissions required have been granted.")
        }

        //Setup Ble SDK
        BleManager(applicationContext).setDebug(true)

    }

    override fun onPause() {
        super.onPause()
        disposables.forEach {
            it.dispose()
        }
        disposables.clear()
    }
}

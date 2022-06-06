package co.igloohome.igloohome_sdk_demo.server

import androidx.annotation.Keep

@Keep
data class PairingDataRequest(
    /**
     * returned value from IglooLock.pair() function
     */
    val payload: String,
    /**
     * tz database id
     */
    val timezone: String
)

@Keep
data class BluetoothAdminKey(
    val type: String,
    val data: IntArray
)

@Keep
data class PairingDataResponse(
    val bluetoothAdminKey: String, //BluetoothAdminKey,
    val masterPin: String
)
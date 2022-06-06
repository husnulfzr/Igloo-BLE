package co.igloohome.igloohome_sdk_demo.server

import androidx.annotation.Keep
import java.util.*

@Keep
data class EKeyRequest(val startDate: Date, val endDate: Date, val permissions: Collection<Permission>) {
    enum class Permission {
        UNLOCK,
        SET_TIME,
        GET_TIME,
        GET_BATTERY_LEVEL,
        GET_LOCK_STATUS,
        SET_VOLUME,
        RESET_LOCK,
        SET_AUTORELOCK,
        SET_MAX_INCORRECT_PINS,
        GET_LOGS,
        CREATE_PIN,
        EDIT_PIN,
        DELETE_PIN,
        SET_MASTER_PIN,
        LOCK,
        ENABLE_AUTOUNLOCK,
        BLACKLIST_GUEST_KEY,
        UNBLACKLIST_GUEST_KEY,
        ENABLE_DFU,
        SET_DAYLIGHT_SAVINGS,
        ADD_CARD,
        DELETE_CARD,
        SET_BRIGHTNESS
    }
}
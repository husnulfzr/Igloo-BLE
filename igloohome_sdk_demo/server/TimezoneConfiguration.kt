package co.igloohome.igloohome_sdk_demo.server

import androidx.annotation.Keep

@Keep
data class TimezoneConfiguration(
    val gmtOffset: Int,
    val ranges: Collection<DaylightSavings>
)

@Keep
data class DaylightSavings(
    val year: Int,
    val startDate: Int,
    val endDate: Int,
    val offset: Int
)

/**
 * Extension function to get daylight savings needed by BLE SDK.
 */
fun TimezoneConfiguration.getDaylightSavings(): Collection<co.igloohome.ble.lock.DaylightSavings> =
    this.ranges
        .map {
            co.igloohome.ble.lock.DaylightSavings(
                it.year,
                it.startDate,
                it.endDate,
                it.offset
            )
        }
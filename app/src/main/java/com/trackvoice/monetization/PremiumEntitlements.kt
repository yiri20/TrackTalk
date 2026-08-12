package com.trackvoice.monetization

import com.trackvoice.data.UserSettings

fun UserSettings.forPremiumEntitlement(isPremium: Boolean): UserSettings {
    if (isPremium) return this
    return copy(
        autoEnableOnScreenOff = false,
        bluetoothOnlyForAutoEnable = false,
        speechRate = 1f,
        pitch = 1f,
        volume = 1f,
        raiseDeviceVolume = true,
        deviceVolumePercent = 90,
    )
}

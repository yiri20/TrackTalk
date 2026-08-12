package com.trackvoice.announcement

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.annotation.SuppressLint

@SuppressLint("InlinedApi")
class AudioOutputDetector(context: Context) {
    private val audioManager = context.applicationContext.getSystemService(AudioManager::class.java)

    fun hasExternalOutput(): Boolean = outputDevices().any { device ->
        device.type in setOf(
            AudioDeviceInfo.TYPE_WIRED_HEADSET,
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
            AudioDeviceInfo.TYPE_USB_HEADSET,
            AudioDeviceInfo.TYPE_USB_DEVICE,
            AudioDeviceInfo.TYPE_USB_ACCESSORY,
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
            AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
            AudioDeviceInfo.TYPE_HEARING_AID,
            AudioDeviceInfo.TYPE_BLE_HEADSET,
            AudioDeviceInfo.TYPE_HDMI,
            AudioDeviceInfo.TYPE_HDMI_ARC,
            AudioDeviceInfo.TYPE_HDMI_EARC,
            AudioDeviceInfo.TYPE_LINE_ANALOG,
            AudioDeviceInfo.TYPE_LINE_DIGITAL,
        )
    }

    fun hasBluetoothOutput(): Boolean = outputDevices().any { device ->
        device.type in setOf(
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
            AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
            AudioDeviceInfo.TYPE_BLE_HEADSET,
        )
    }

    private fun outputDevices(): Array<AudioDeviceInfo> = runCatching {
        audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
    }.getOrDefault(emptyArray())
}

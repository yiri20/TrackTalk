package com.trackvoice.announcement

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Handler
import android.os.Looper

data class ConnectedAudioDevice(
    val key: String,
    val name: String,
    val typeLabel: String,
)

@SuppressLint("InlinedApi")
class AudioDeviceMonitor(context: Context, private val onChanged: (List<ConnectedAudioDevice>) -> Unit) {
    private val audioManager = context.applicationContext.getSystemService(AudioManager::class.java)
    private val callback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) = publish()
        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) = publish()
    }

    fun start() {
        runCatching {
            audioManager.registerAudioDeviceCallback(callback, Handler(Looper.getMainLooper()))
            publish()
        }
    }

    fun stop() {
        runCatching { audioManager.unregisterAudioDeviceCallback(callback) }
    }

    private fun publish() {
        val devices = runCatching {
            audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
                .filter { it.type in supportedTypes }
                .map { device ->
                    val name = device.productName?.toString()?.ifBlank { null } ?: typeLabel(device.type)
                    val address = device.address.orEmpty()
                    ConnectedAudioDevice(
                        key = if (address.isNotBlank()) "${device.type}:$address" else "${device.type}:$name",
                        name = name,
                        typeLabel = typeLabel(device.type),
                    )
                }
                .distinctBy { it.key }
        }.getOrDefault(emptyList())
        runCatching { onChanged(devices) }
    }

    private fun typeLabel(type: Int): String = when (type) {
        AudioDeviceInfo.TYPE_WIRED_HEADSET, AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> "유선 이어폰"
        AudioDeviceInfo.TYPE_USB_HEADSET, AudioDeviceInfo.TYPE_USB_DEVICE -> "USB 오디오"
        AudioDeviceInfo.TYPE_USB_ACCESSORY -> "USB 오디오"
        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> "Bluetooth"
        AudioDeviceInfo.TYPE_BLE_HEADSET -> "Bluetooth LE"
        AudioDeviceInfo.TYPE_HEARING_AID -> "보청기"
        AudioDeviceInfo.TYPE_HDMI, AudioDeviceInfo.TYPE_HDMI_ARC, AudioDeviceInfo.TYPE_HDMI_EARC -> "HDMI 오디오"
        AudioDeviceInfo.TYPE_LINE_ANALOG, AudioDeviceInfo.TYPE_LINE_DIGITAL -> "라인 오디오"
        else -> "오디오 기기"
    }

    private companion object {
        val supportedTypes = setOf(
            AudioDeviceInfo.TYPE_WIRED_HEADSET,
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
            AudioDeviceInfo.TYPE_USB_HEADSET,
            AudioDeviceInfo.TYPE_USB_DEVICE,
            AudioDeviceInfo.TYPE_USB_ACCESSORY,
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
            AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
            AudioDeviceInfo.TYPE_BLE_HEADSET,
            AudioDeviceInfo.TYPE_HEARING_AID,
            AudioDeviceInfo.TYPE_HDMI,
            AudioDeviceInfo.TYPE_HDMI_ARC,
            AudioDeviceInfo.TYPE_HDMI_EARC,
            AudioDeviceInfo.TYPE_LINE_ANALOG,
            AudioDeviceInfo.TYPE_LINE_DIGITAL,
        )
    }
}

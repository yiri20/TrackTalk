package com.trackvoice.announcement

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Handler
import android.os.Looper

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
            val discovered = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
                .filter { it.type in supportedTypes }
                .map { device ->
                    val productName = device.productName?.toString()?.ifBlank { null }
                    val kind = deviceKind(device.type)
                    val address = device.address.orEmpty()
                    // This reproduces the old profile-specific key exactly so
                    // persisted automation choices can be migrated safely.
                    val stableName = productName ?: legacyKeyLabel(kind)
                    DiscoveredAudioDevice(
                        systemId = device.id,
                        profile = deviceProfile(device.type),
                        productName = productName,
                        stableAddress = address.ifBlank { null },
                        legacyKey = if (address.isNotBlank()) {
                            "${device.type}:$address"
                        } else {
                            "${device.type}:$stableName"
                        },
                        kind = kind,
                    )
                }
            LogicalAudioDeviceNormalizer.normalize(discovered)
        }.getOrDefault(emptyList())
        runCatching { onChanged(devices) }
    }

    private fun deviceKind(type: Int): AudioDeviceKind = when (type) {
        AudioDeviceInfo.TYPE_WIRED_HEADSET,
        AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
        -> AudioDeviceKind.WIRED_HEADPHONES
        AudioDeviceInfo.TYPE_USB_HEADSET,
        AudioDeviceInfo.TYPE_USB_DEVICE,
        AudioDeviceInfo.TYPE_USB_ACCESSORY,
        -> AudioDeviceKind.USB_AUDIO
        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
        AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
        -> AudioDeviceKind.BLUETOOTH
        AudioDeviceInfo.TYPE_BLE_HEADSET,
        AudioDeviceInfo.TYPE_BLE_SPEAKER,
        -> AudioDeviceKind.BLUETOOTH_LE
        AudioDeviceInfo.TYPE_HEARING_AID -> AudioDeviceKind.HEARING_AID
        AudioDeviceInfo.TYPE_HDMI,
        AudioDeviceInfo.TYPE_HDMI_ARC,
        AudioDeviceInfo.TYPE_HDMI_EARC,
        -> AudioDeviceKind.HDMI_AUDIO
        AudioDeviceInfo.TYPE_LINE_ANALOG,
        AudioDeviceInfo.TYPE_LINE_DIGITAL,
        -> AudioDeviceKind.LINE_AUDIO
        else -> AudioDeviceKind.OTHER
    }

    private fun deviceProfile(type: Int): AudioDeviceProfile = when (type) {
        AudioDeviceInfo.TYPE_WIRED_HEADSET -> AudioDeviceProfile.WIRED_HEADSET
        AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> AudioDeviceProfile.WIRED_HEADPHONES
        AudioDeviceInfo.TYPE_USB_HEADSET -> AudioDeviceProfile.USB_HEADSET
        AudioDeviceInfo.TYPE_USB_DEVICE -> AudioDeviceProfile.USB_DEVICE
        AudioDeviceInfo.TYPE_USB_ACCESSORY -> AudioDeviceProfile.USB_ACCESSORY
        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> AudioDeviceProfile.BLUETOOTH_A2DP
        AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> AudioDeviceProfile.BLUETOOTH_SCO
        AudioDeviceInfo.TYPE_BLE_HEADSET -> AudioDeviceProfile.BLUETOOTH_LE_HEADSET
        AudioDeviceInfo.TYPE_BLE_SPEAKER -> AudioDeviceProfile.BLUETOOTH_LE_SPEAKER
        AudioDeviceInfo.TYPE_HEARING_AID -> AudioDeviceProfile.HEARING_AID
        AudioDeviceInfo.TYPE_HDMI -> AudioDeviceProfile.HDMI
        AudioDeviceInfo.TYPE_HDMI_ARC -> AudioDeviceProfile.HDMI_ARC
        AudioDeviceInfo.TYPE_HDMI_EARC -> AudioDeviceProfile.HDMI_EARC
        AudioDeviceInfo.TYPE_LINE_ANALOG -> AudioDeviceProfile.LINE_ANALOG
        AudioDeviceInfo.TYPE_LINE_DIGITAL -> AudioDeviceProfile.LINE_DIGITAL
        else -> AudioDeviceProfile.OTHER
    }

    /** Values used by releases that persisted a localized fallback in the device key. */
    private fun legacyKeyLabel(kind: AudioDeviceKind): String = when (kind) {
        AudioDeviceKind.WIRED_HEADPHONES -> "유선 이어폰"
        AudioDeviceKind.USB_AUDIO -> "USB 오디오"
        AudioDeviceKind.BLUETOOTH -> "Bluetooth"
        AudioDeviceKind.BLUETOOTH_LE -> "Bluetooth LE"
        AudioDeviceKind.HEARING_AID -> "보청기"
        AudioDeviceKind.HDMI_AUDIO -> "HDMI 오디오"
        AudioDeviceKind.LINE_AUDIO -> "라인 오디오"
        AudioDeviceKind.OTHER -> "오디오 기기"
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
            AudioDeviceInfo.TYPE_BLE_SPEAKER,
            AudioDeviceInfo.TYPE_HEARING_AID,
            AudioDeviceInfo.TYPE_HDMI,
            AudioDeviceInfo.TYPE_HDMI_ARC,
            AudioDeviceInfo.TYPE_HDMI_EARC,
            AudioDeviceInfo.TYPE_LINE_ANALOG,
            AudioDeviceInfo.TYPE_LINE_DIGITAL,
        )
    }
}

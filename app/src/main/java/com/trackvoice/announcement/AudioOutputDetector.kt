package com.trackvoice.announcement

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.annotation.SuppressLint
import android.media.AudioAttributes
import android.os.Build
import com.trackvoice.diagnostics.TrackTalkDebugLog

@SuppressLint("InlinedApi")
class AudioOutputDetector(context: Context) {
    private val audioManager = context.applicationContext.getSystemService(AudioManager::class.java)
    private val mediaAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
        .build()
    private var lastLoggedRoute: String? = null

    /**
     * Returns whether the current media route is something other than the
     * phone's built-in speaker/earpiece.
     *
     * `getDevices(GET_DEVICES_OUTPUTS)` is a connected-device inventory, not
     * the route currently selected for media. On some phones a connected
     * Bluetooth SCO device (for example a computer used for wireless ADB)
     * remains in that inventory while music is still playing through the
     * built-in speaker. Using that inventory made speaker suppression appear
     * to be ignored. Android 12+ exposes the actual route for media
     * attributes, so prefer it and only use the conservative legacy fallback
     * on older devices or when the route query is unavailable.
     */
    fun hasExternalOutput(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val routedTypes = runCatching {
                audioManager.getAudioDevicesForAttributes(mediaAttributes)
            }.getOrNull()?.map(AudioDeviceInfo::getType)
            if (routedTypes != null) {
                // Samsung can return an empty attribute route even while an
                // A2DP session is actively playing. In that specific case,
                // use only the legacy *active-route* flags as a fallback;
                // never use the full connected-device inventory here because
                // a connected Bluetooth device may still be idle.
                val external = classifyRoutedOutput(
                    routedTypes = routedTypes,
                    activeLegacyExternalOutput = hasActiveLegacyExternalOutput(),
                )
                logRoute(
                    types = routedTypes,
                    external = external,
                    source = if (routedTypes.isEmpty()) "attributes_empty_legacy_active" else "attributes",
                )
                return external
            }
        }

        val external = hasLegacyExternalOutput()
        logRoute(outputDevices().map(AudioDeviceInfo::getType), external, "legacy_inventory")
        return external
    }

    private fun logRoute(types: List<Int>, external: Boolean, source: String) {
        val signature = "$source|${types.sorted()}|$external"
        if (signature == lastLoggedRoute) return
        lastLoggedRoute = signature
        TrackTalkDebugLog.event(
            "audio_route",
            "source" to source,
            "types" to types.sorted(),
            "external" to external,
        )
    }

    fun hasBluetoothOutput(): Boolean = outputDevices().any { device ->
        device.type in setOf(
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
            AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
            AudioDeviceInfo.TYPE_BLE_HEADSET,
            AudioDeviceInfo.TYPE_BLE_SPEAKER,
            AudioDeviceInfo.TYPE_BLE_BROADCAST,
        )
    }

    @Suppress("DEPRECATION")
    private fun hasLegacyExternalOutput(): Boolean = runCatching {
        audioManager.isWiredHeadsetOn ||
            audioManager.isBluetoothA2dpOn ||
        audioManager.isBluetoothScoOn ||
            outputDevices().any { device ->
                device.type in externalOutputTypes
            }
    }.getOrDefault(false)

    @Suppress("DEPRECATION")
    private fun hasActiveLegacyExternalOutput(): Boolean = runCatching {
        audioManager.isWiredHeadsetOn ||
            audioManager.isBluetoothA2dpOn ||
            audioManager.isBluetoothScoOn
    }.getOrDefault(false)

    private fun outputDevices(): Array<AudioDeviceInfo> = runCatching {
        audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
    }.getOrDefault(emptyArray())

    internal companion object {
        /** Pure route classification kept separate so it can be unit tested. */
        fun hasExternalOutputType(types: Iterable<Int>): Boolean = types.any(::isExternalOutputType)

        /**
         * Some Android/Samsung builds return an empty attribute route while a
         * legacy active-route flag still identifies Bluetooth or wired audio.
         */
        fun classifyRoutedOutput(
            routedTypes: Iterable<Int>,
            activeLegacyExternalOutput: Boolean,
        ): Boolean = routedTypes.toList().let { types ->
            if (types.isEmpty()) activeLegacyExternalOutput else hasExternalOutputType(types)
        }

        private fun isExternalOutputType(type: Int): Boolean = type in externalOutputTypes

        private val externalOutputTypes = setOf(
            AudioDeviceInfo.TYPE_WIRED_HEADSET,
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
            AudioDeviceInfo.TYPE_USB_HEADSET,
            AudioDeviceInfo.TYPE_USB_DEVICE,
            AudioDeviceInfo.TYPE_USB_ACCESSORY,
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
            AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
            AudioDeviceInfo.TYPE_HEARING_AID,
            AudioDeviceInfo.TYPE_BLE_HEADSET,
            AudioDeviceInfo.TYPE_BLE_SPEAKER,
            AudioDeviceInfo.TYPE_BLE_BROADCAST,
            AudioDeviceInfo.TYPE_HDMI,
            AudioDeviceInfo.TYPE_HDMI_ARC,
            AudioDeviceInfo.TYPE_HDMI_EARC,
            AudioDeviceInfo.TYPE_LINE_ANALOG,
            AudioDeviceInfo.TYPE_LINE_DIGITAL,
            AudioDeviceInfo.TYPE_DOCK,
            AudioDeviceInfo.TYPE_AUX_LINE,
            AudioDeviceInfo.TYPE_IP,
            AudioDeviceInfo.TYPE_BUS,
            AudioDeviceInfo.TYPE_REMOTE_SUBMIX,
        )
    }
}

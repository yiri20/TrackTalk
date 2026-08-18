package com.trackvoice.announcement

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import com.trackvoice.diagnostics.TrackTalkDebugLog

/**
 * A media route conclusion, deliberately separate from the inventory of
 * connected devices. A connected Bluetooth headset is not necessarily the
 * route used for music.
 */
internal enum class AudioRouteState {
    EXTERNAL,
    SPEAKER,
    TRANSITIONING,
    UNKNOWN,
}

/** Raw, non-identifying route signals captured for one resolution attempt. */
internal data class AudioRouteEvidence(
    val attributesRoute: List<Int>?,
    val legacyBluetoothActive: Boolean,
    val legacyWiredActive: Boolean,
    val bluetoothOutputPresent: Boolean,
    val availableOutputTypes: List<Int>,
)

internal data class AudioRouteResolution(
    val state: AudioRouteState,
    val reason: String,
) {
    val isExternal: Boolean get() = state == AudioRouteState.EXTERNAL
    val isTransitioning: Boolean get() = state == AudioRouteState.TRANSITIONING
}

@SuppressLint("InlinedApi")
class AudioOutputDetector(context: Context) {
    private val audioManager = context.applicationContext.getSystemService(AudioManager::class.java)
    private val mediaAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
        .build()
    private var lastLoggedRoute: String? = null
    private var lastResolvedRoute: AudioRouteState? = null

    /**
     * Resolves the media output route without treating the connected-device
     * inventory as proof of the selected route. Android 12+ attributes are
     * normally authoritative, but Samsung can briefly report a stale speaker
     * route while both the active A2DP/SCO flag and a compatible Bluetooth
     * output still corroborate Bluetooth media playback.
     */
    internal fun resolveRoute(retryAttempt: Int = 0): AudioRouteResolution {
        val availableOutputTypes = outputDevices().map(AudioDeviceInfo::getType)
        val attributesRoute = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            runCatching {
                audioManager.getAudioDevicesForAttributes(mediaAttributes).map(AudioDeviceInfo::getType)
            }.getOrNull()
        } else {
            null
        }
        val evidence = AudioRouteEvidence(
            attributesRoute = attributesRoute,
            legacyBluetoothActive = isLegacyBluetoothActive(),
            legacyWiredActive = isLegacyWiredActive(),
            bluetoothOutputPresent = availableOutputTypes.any(::isBluetoothOutputType),
            availableOutputTypes = availableOutputTypes,
        )
        val resolution = resolveEvidence(evidence, retryAttempt)
        logRoute(evidence, resolution, retryAttempt)
        return resolution
    }

    fun hasExternalOutput(): Boolean = resolveRoute().isExternal

    fun hasBluetoothOutput(): Boolean = outputDevices().any { device ->
        isBluetoothOutputType(device.type)
    }

    private fun logRoute(
        evidence: AudioRouteEvidence,
        resolution: AudioRouteResolution,
        retryAttempt: Int,
    ) {
        val attributesRoute = evidence.attributesRoute?.sorted()
        val signature = listOf(
            attributesRoute?.joinToString(",") ?: "unavailable",
            evidence.legacyBluetoothActive,
            evidence.legacyWiredActive,
            evidence.bluetoothOutputPresent,
            evidence.availableOutputTypes.sorted().joinToString(","),
            resolution.state,
            resolution.reason,
            retryAttempt,
        ).joinToString("|")
        if (signature == lastLoggedRoute) return

        val previousRoute = lastResolvedRoute
        lastLoggedRoute = signature
        lastResolvedRoute = resolution.state
        TrackTalkDebugLog.event(
            "audio_route",
            "attributesRoute" to attributesRoute,
            "legacyBluetoothActive" to evidence.legacyBluetoothActive,
            "legacyWiredActive" to evidence.legacyWiredActive,
            "bluetoothOutputPresent" to evidence.bluetoothOutputPresent,
            "availableOutputTypes" to evidence.availableOutputTypes.sorted(),
            "previousRoute" to previousRoute,
            "resolution" to resolution.state,
            "resolutionReason" to resolution.reason,
            "retryAttempt" to retryAttempt,
        )
    }

    @Suppress("DEPRECATION")
    private fun isLegacyBluetoothActive(): Boolean = runCatching {
        audioManager.isBluetoothA2dpOn || audioManager.isBluetoothScoOn
    }.getOrDefault(false)

    @Suppress("DEPRECATION")
    private fun isLegacyWiredActive(): Boolean = runCatching {
        audioManager.isWiredHeadsetOn
    }.getOrDefault(false)

    private fun outputDevices(): Array<AudioDeviceInfo> = runCatching {
        audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
    }.getOrDefault(emptyArray())

    internal companion object {
        /** Pure route classification kept separate so it can be unit tested. */
        fun hasExternalOutputType(types: Iterable<Int>): Boolean = types.any(::isExternalOutputType)

        /**
         * Reconciles active-route evidence. The Bluetooth inventory is only a
         * corroborating signal after an active A2DP/SCO signal; it never
         * independently promotes a speaker route to an external one.
         */
        fun resolveEvidence(
            evidence: AudioRouteEvidence,
            retryAttempt: Int = 0,
        ): AudioRouteResolution {
            val attributesRoute = evidence.attributesRoute
            if (attributesRoute == null) {
                return if (
                    evidence.legacyBluetoothActive ||
                    evidence.legacyWiredActive ||
                    hasExternalOutputType(evidence.availableOutputTypes)
                ) {
                    AudioRouteResolution(AudioRouteState.EXTERNAL, "LEGACY_FALLBACK_EXTERNAL")
                } else {
                    AudioRouteResolution(AudioRouteState.UNKNOWN, "LEGACY_FALLBACK_UNKNOWN")
                }
            }

            if (hasExternalOutputType(attributesRoute)) {
                return AudioRouteResolution(AudioRouteState.EXTERNAL, "ATTRIBUTES_EXTERNAL")
            }

            if (attributesRoute.isEmpty()) {
                return if (evidence.legacyBluetoothActive || evidence.legacyWiredActive) {
                    AudioRouteResolution(AudioRouteState.EXTERNAL, "ATTRIBUTES_EMPTY_LEGACY_ACTIVE")
                } else {
                    AudioRouteResolution(AudioRouteState.UNKNOWN, "ATTRIBUTES_EMPTY")
                }
            }

            if (hasBuiltInOutputType(attributesRoute)) {
                val corroboratedBluetoothConflict =
                    evidence.legacyBluetoothActive && evidence.bluetoothOutputPresent
                if (corroboratedBluetoothConflict) {
                    return if (retryAttempt >= PERSISTENT_CONFLICT_RETRY_ATTEMPT) {
                        AudioRouteResolution(
                            AudioRouteState.EXTERNAL,
                            "PERSISTENT_BLUETOOTH_CONFLICT_FALLBACK",
                        )
                    } else {
                        AudioRouteResolution(
                            AudioRouteState.TRANSITIONING,
                            "SPEAKER_ATTRIBUTES_BLUETOOTH_ACTIVE",
                        )
                    }
                }
                return AudioRouteResolution(AudioRouteState.SPEAKER, "ATTRIBUTES_BUILT_IN")
            }

            return AudioRouteResolution(AudioRouteState.UNKNOWN, "ATTRIBUTES_UNKNOWN")
        }

        private fun isExternalOutputType(type: Int): Boolean = type in externalOutputTypes

        private fun hasBuiltInOutputType(types: Iterable<Int>): Boolean = types.any { type ->
            type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER ||
                type == AudioDeviceInfo.TYPE_BUILTIN_EARPIECE
        }

        private fun isBluetoothOutputType(type: Int): Boolean = type in bluetoothOutputTypes

        private const val PERSISTENT_CONFLICT_RETRY_ATTEMPT = 1

        private val bluetoothOutputTypes = setOf(
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
            AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
            AudioDeviceInfo.TYPE_BLE_HEADSET,
            AudioDeviceInfo.TYPE_BLE_SPEAKER,
            AudioDeviceInfo.TYPE_BLE_BROADCAST,
        )

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

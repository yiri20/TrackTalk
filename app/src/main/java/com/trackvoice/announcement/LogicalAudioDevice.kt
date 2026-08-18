package com.trackvoice.announcement

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Locale

/** A user-facing logical output device after Android route/profile normalization. */
data class ConnectedAudioDevice(
    val key: String,
    val productName: String?,
    val kind: AudioDeviceKind,
    /** Previous profile-specific keys that may still own persisted settings. */
    val legacyKeys: Set<String> = emptySet(),
)

enum class AudioDeviceKind {
    WIRED_HEADPHONES,
    USB_AUDIO,
    BLUETOOTH,
    BLUETOOTH_LE,
    HEARING_AID,
    HDMI_AUDIO,
    LINE_AUDIO,
    OTHER,
}

internal enum class AudioDeviceProfile {
    WIRED_HEADSET,
    WIRED_HEADPHONES,
    USB_HEADSET,
    USB_DEVICE,
    USB_ACCESSORY,
    BLUETOOTH_A2DP,
    BLUETOOTH_SCO,
    BLUETOOTH_LE_HEADSET,
    BLUETOOTH_LE_SPEAKER,
    HEARING_AID,
    HDMI,
    HDMI_ARC,
    HDMI_EARC,
    LINE_ANALOG,
    LINE_DIGITAL,
    OTHER,
}

internal data class DiscoveredAudioDevice(
    val systemId: Int,
    val profile: AudioDeviceProfile,
    val productName: String?,
    val stableAddress: String?,
    val legacyKey: String,
    val kind: AudioDeviceKind,
)

/**
 * Collapses profile-specific Android audio routes into physical/logical devices.
 *
 * Bluetooth address/identity is hashed locally before it becomes a persistent
 * key. When Android withholds that identity, entries remain profile/session
 * specific rather than merging unrelated same-name devices.
 */
internal object LogicalAudioDeviceNormalizer {
    fun normalize(devices: List<DiscoveredAudioDevice>): List<ConnectedAudioDevice> {
        val grouped = linkedMapOf<String, MutableList<DiscoveredAudioDevice>>()
        devices.forEach { device ->
            grouped.getOrPut(logicalKey(device)) { mutableListOf() } += device
        }
        return grouped.map { (key, sources) ->
            val orderedSources = sources.sortedWith(
                compareBy<DiscoveredAudioDevice> { profilePriority(it.profile) }
                    .thenBy { it.systemId },
            )
            val representative = orderedSources.first()
            val hasStableBluetoothIdentity = representative.isBluetooth &&
                normalizedStableAddress(representative.stableAddress) != null
            ConnectedAudioDevice(
                key = key,
                productName = orderedSources.firstNotNullOfOrNull { it.productName?.ifBlank { null } },
                kind = representative.kind,
                legacyKeys = if (hasStableBluetoothIdentity) {
                    sources.map(DiscoveredAudioDevice::legacyKey)
                        .filterNot { it == key }
                        .toSet()
                } else {
                    emptySet()
                },
            )
        }
    }

    private fun logicalKey(device: DiscoveredAudioDevice): String {
        if (!device.isBluetooth) return device.legacyKey
        val stableAddress = normalizedStableAddress(device.stableAddress)
        return if (stableAddress != null) {
            "bluetooth:${stableIdentityHash(stableAddress)}"
        } else {
            // AudioDeviceInfo.id is only a connection-session fallback. It
            // keeps two same-name devices separate but is not claimed to be
            // stable across reconnects.
            "bluetooth-session:${device.profile.name.lowercase(Locale.ROOT)}:${device.systemId}"
        }
    }

    private val DiscoveredAudioDevice.isBluetooth: Boolean
        get() = kind == AudioDeviceKind.BLUETOOTH ||
            kind == AudioDeviceKind.BLUETOOTH_LE ||
            kind == AudioDeviceKind.HEARING_AID

    private fun normalizedStableAddress(address: String?): String? {
        val normalized = address?.trim()?.lowercase(Locale.ROOT)?.takeIf(String::isNotEmpty)
            ?: return null
        return normalized.takeUnless { it in INVALID_BLUETOOTH_IDENTITIES }
    }

    private fun stableIdentityHash(value: String): String = MessageDigest
        .getInstance("SHA-256")
        .digest(value.toByteArray(StandardCharsets.UTF_8))
        .take(12)
        .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }

    private fun profilePriority(profile: AudioDeviceProfile): Int = when (profile) {
        AudioDeviceProfile.HEARING_AID -> 0
        AudioDeviceProfile.BLUETOOTH_A2DP -> 1
        AudioDeviceProfile.BLUETOOTH_LE_HEADSET,
        AudioDeviceProfile.BLUETOOTH_LE_SPEAKER,
        -> 2
        AudioDeviceProfile.BLUETOOTH_SCO -> 3
        else -> 4
    }

    private val INVALID_BLUETOOTH_IDENTITIES = setOf(
        "0",
        "unknown",
        "00:00:00:00:00:00",
        "02:00:00:00:00:00",
    )
}

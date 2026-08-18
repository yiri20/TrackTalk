package com.trackvoice

import com.trackvoice.announcement.ConnectedAudioDevice
import com.trackvoice.data.AudioDeviceSettings

internal data class DeviceAutoActivationDecision(
    val active: Boolean,
    val changed: Boolean,
)

/** Pure decision boundary used to keep repeated Android route callbacks idempotent. */
internal object DeviceAutomationPolicy {
    fun decide(
        currentlyActive: Boolean,
        isPremium: Boolean,
        devices: List<ConnectedAudioDevice>,
        settings: Map<String, AudioDeviceSettings>,
    ): DeviceAutoActivationDecision {
        val active = isPremium && devices.any { device ->
            settings[device.key]?.let { it.enabled && it.autoEnable } == true
        }
        return DeviceAutoActivationDecision(
            active = active,
            changed = active != currentlyActive,
        )
    }
}

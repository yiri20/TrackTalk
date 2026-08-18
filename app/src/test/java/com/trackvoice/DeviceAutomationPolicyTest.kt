package com.trackvoice

import com.trackvoice.announcement.AudioDeviceKind
import com.trackvoice.announcement.ConnectedAudioDevice
import com.trackvoice.data.AudioDeviceSettings
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceAutomationPolicyTest {
    @Test
    fun oneEligibleLogicalDeviceCreatesOneActivationTransition() {
        val device = bluetoothDevice()
        val settings = mapOf(
            device.key to AudioDeviceSettings(
                deviceKey = device.key,
                displayName = "Space One Pro",
                enabled = true,
                autoEnable = true,
            ),
        )

        val first = DeviceAutomationPolicy.decide(
            currentlyActive = false,
            isPremium = true,
            devices = listOf(device),
            settings = settings,
        )
        val repeatedCallback = DeviceAutomationPolicy.decide(
            currentlyActive = first.active,
            isPremium = true,
            devices = listOf(device),
            settings = settings,
        )

        assertTrue(first.active)
        assertTrue(first.changed)
        assertTrue(repeatedCallback.active)
        assertFalse(repeatedCallback.changed)
    }

    @Test
    fun disconnectCreatesOneDeactivationTransition() {
        val disconnected = DeviceAutomationPolicy.decide(
            currentlyActive = true,
            isPremium = true,
            devices = emptyList(),
            settings = emptyMap(),
        )
        val repeatedCallback = DeviceAutomationPolicy.decide(
            currentlyActive = disconnected.active,
            isPremium = true,
            devices = emptyList(),
            settings = emptyMap(),
        )

        assertFalse(disconnected.active)
        assertTrue(disconnected.changed)
        assertFalse(repeatedCallback.changed)
    }

    @Test
    fun automationRemainsUnavailableWithoutPremiumOrExplicitEligibility() {
        val device = bluetoothDevice()
        val eligible = AudioDeviceSettings(
            deviceKey = device.key,
            displayName = "Space One Pro",
            enabled = true,
            autoEnable = true,
        )

        assertFalse(
            DeviceAutomationPolicy.decide(false, false, listOf(device), mapOf(device.key to eligible)).active,
        )
        assertFalse(
            DeviceAutomationPolicy.decide(
                currentlyActive = false,
                isPremium = true,
                devices = listOf(device),
                settings = mapOf(device.key to eligible.copy(enabled = false)),
            ).active,
        )
        assertFalse(
            DeviceAutomationPolicy.decide(
                currentlyActive = false,
                isPremium = true,
                devices = listOf(device),
                settings = mapOf(device.key to eligible.copy(autoEnable = false)),
            ).active,
        )
    }

    private fun bluetoothDevice() = ConnectedAudioDevice(
        key = "bluetooth:logical-device",
        productName = "Space One Pro",
        kind = AudioDeviceKind.BLUETOOTH,
    )
}

package com.trackvoice.announcement

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LogicalAudioDeviceNormalizerTest {
    @Test
    fun sameBluetoothAddressFromAudioRouteAndProfileBecomesOneLogicalDevice() {
        val devices = LogicalAudioDeviceNormalizer.normalize(
            listOf(
                bluetooth(AudioDeviceProfile.BLUETOOTH_A2DP, 10, ADDRESS_A, "Space One Pro", "8:$ADDRESS_A"),
                bluetooth(AudioDeviceProfile.BLUETOOTH_SCO, 11, ADDRESS_A, "Space One Pro", "7:$ADDRESS_A"),
            ),
        )

        assertEquals(1, devices.size)
        assertTrue(devices.single().key.startsWith("bluetooth:"))
        assertEquals(34, devices.single().key.length)
        assertEquals(setOf("8:$ADDRESS_A", "7:$ADDRESS_A"), devices.single().legacyKeys)
    }

    @Test
    fun multipleProfilesForTheSamePhysicalHeadsetBecomeOneLogicalDevice() {
        val devices = LogicalAudioDeviceNormalizer.normalize(
            listOf(
                bluetooth(AudioDeviceProfile.BLUETOOTH_A2DP, 20, ADDRESS_A, "Space One Pro", "8:$ADDRESS_A"),
                bluetooth(AudioDeviceProfile.BLUETOOTH_SCO, 21, ADDRESS_A, "Space One Pro", "7:$ADDRESS_A"),
                bluetooth(AudioDeviceProfile.BLUETOOTH_LE_HEADSET, 22, ADDRESS_A, "Space One Pro", "26:$ADDRESS_A", AudioDeviceKind.BLUETOOTH_LE),
            ),
        )

        assertEquals(1, devices.size)
        assertEquals("Space One Pro", devices.single().productName)
    }

    @Test
    fun differentAddressesWithTheSameVisibleNameRemainSeparate() {
        val devices = LogicalAudioDeviceNormalizer.normalize(
            listOf(
                bluetooth(AudioDeviceProfile.BLUETOOTH_A2DP, 30, ADDRESS_A, "Galaxy Buds2 Pro", "8:$ADDRESS_A"),
                bluetooth(AudioDeviceProfile.BLUETOOTH_A2DP, 31, ADDRESS_B, "Galaxy Buds2 Pro", "8:$ADDRESS_B"),
            ),
        )

        assertEquals(2, devices.size)
        assertNotEquals(devices[0].key, devices[1].key)
    }

    @Test
    fun reconnectWithTheSameAddressKeepsTheSameLogicalKey() {
        val first = LogicalAudioDeviceNormalizer.normalize(
            listOf(bluetooth(AudioDeviceProfile.BLUETOOTH_A2DP, 40, ADDRESS_A, "Space One Pro", "8:$ADDRESS_A")),
        ).single()
        val reconnected = LogicalAudioDeviceNormalizer.normalize(
            listOf(bluetooth(AudioDeviceProfile.BLUETOOTH_A2DP, 99, ADDRESS_A.lowercase(), "Space One Pro", "8:${ADDRESS_A.lowercase()}")),
        ).single()

        assertEquals(first.key, reconnected.key)
    }

    @Test
    fun unavailableStableIdentityDoesNotMergeSameNameDevices() {
        val devices = LogicalAudioDeviceNormalizer.normalize(
            listOf(
                bluetooth(AudioDeviceProfile.BLUETOOTH_A2DP, 50, null, "Space One Pro", "8:Space One Pro"),
                bluetooth(AudioDeviceProfile.BLUETOOTH_A2DP, 51, null, "Space One Pro", "8:Space One Pro"),
            ),
        )

        assertEquals(2, devices.size)
        assertTrue(devices.all { it.legacyKeys.isEmpty() })
    }

    @Test
    fun placeholderBluetoothAddressIsNotTreatedAsPhysicalIdentity() {
        val devices = LogicalAudioDeviceNormalizer.normalize(
            listOf(
                bluetooth(
                    AudioDeviceProfile.BLUETOOTH_A2DP,
                    60,
                    "02:00:00:00:00:00",
                    "Space One Pro",
                    "8:02:00:00:00:00",
                ),
                bluetooth(
                    AudioDeviceProfile.BLUETOOTH_SCO,
                    61,
                    "02:00:00:00:00:00",
                    "Space One Pro",
                    "7:02:00:00:00:00",
                ),
            ),
        )

        assertEquals(2, devices.size)
        assertTrue(devices.all { it.key.startsWith("bluetooth-session:") })
    }

    private fun bluetooth(
        profile: AudioDeviceProfile,
        systemId: Int,
        address: String?,
        name: String,
        legacyKey: String,
        kind: AudioDeviceKind = AudioDeviceKind.BLUETOOTH,
    ) = DiscoveredAudioDevice(
        systemId = systemId,
        profile = profile,
        productName = name,
        stableAddress = address,
        legacyKey = legacyKey,
        kind = kind,
    )

    private companion object {
        const val ADDRESS_A = "AA:BB:CC:DD:EE:01"
        const val ADDRESS_B = "AA:BB:CC:DD:EE:02"
    }
}

package com.trackvoice.announcement

import android.media.AudioDeviceInfo
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioOutputDetectorTest {
    @Test
    fun builtInSpeakerAndEarpieceAreNotExternalRoutes() {
        assertFalse(
            AudioOutputDetector.hasExternalOutputType(
                listOf(
                    AudioDeviceInfo.TYPE_BUILTIN_SPEAKER,
                    AudioDeviceInfo.TYPE_BUILTIN_EARPIECE,
                ),
            ),
        )
    }

    @Test
    fun connectedBluetoothDoesNotOverrideAnActiveSpeakerRoute() {
        // The detector receives the media route, not the full connected-device
        // inventory. A phone speaker route must remain a speaker route even if
        // a Bluetooth SCO device is connected for another purpose.
        assertFalse(
            AudioOutputDetector.hasExternalOutputType(
                listOf(AudioDeviceInfo.TYPE_BUILTIN_SPEAKER),
            ),
        )
    }

    @Test
    fun unknownRouteDoesNotCountAsExternal() {
        assertFalse(
            AudioOutputDetector.hasExternalOutputType(
                listOf(AudioDeviceInfo.TYPE_UNKNOWN, AudioDeviceInfo.TYPE_BUILTIN_SPEAKER),
            ),
        )
    }

    @Test
    fun BluetoothAndWiredRoutesAreExternal() {
        assertTrue(
            AudioOutputDetector.hasExternalOutputType(
                listOf(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP),
            ),
        )
        assertTrue(
            AudioOutputDetector.hasExternalOutputType(
                listOf(AudioDeviceInfo.TYPE_WIRED_HEADPHONES),
            ),
        )
    }

    @Test
    fun UsbAndHdmiRoutesAreExternal() {
        assertTrue(
            AudioOutputDetector.hasExternalOutputType(
                listOf(AudioDeviceInfo.TYPE_USB_DEVICE),
            ),
        )
        assertTrue(
            AudioOutputDetector.hasExternalOutputType(
                listOf(AudioDeviceInfo.TYPE_HDMI),
            ),
        )
    }
}

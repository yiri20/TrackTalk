package com.trackvoice.announcement

import android.media.AudioDeviceInfo
import org.junit.Assert.assertEquals
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
    fun attributesBluetoothRouteIsExternal() {
        assertEquals(
            AudioRouteState.EXTERNAL,
            resolve(attributesRoute = listOf(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP)).state,
        )
    }

    @Test
    fun attributesWiredRouteIsExternal() {
        assertEquals(
            AudioRouteState.EXTERNAL,
            resolve(attributesRoute = listOf(AudioDeviceInfo.TYPE_WIRED_HEADPHONES)).state,
        )
    }

    @Test
    fun attributesUsbAndHdmiRoutesAreExternal() {
        listOf(
            AudioDeviceInfo.TYPE_USB_DEVICE,
            AudioDeviceInfo.TYPE_HDMI,
        ).forEach { routeType ->
            assertEquals(
                AudioRouteState.EXTERNAL,
                resolve(attributesRoute = listOf(routeType)).state,
            )
        }
    }

    @Test
    fun cleanSpeakerRouteRemainsSpeaker() {
        assertEquals(
            AudioRouteState.SPEAKER,
            resolve(attributesRoute = listOf(AudioDeviceInfo.TYPE_BUILTIN_SPEAKER)).state,
        )
    }

    @Test
    fun emptyAttributesFallBackToAnActiveLegacyBluetoothRoute() {
        assertEquals(
            AudioRouteState.EXTERNAL,
            resolve(
                attributesRoute = emptyList(),
                legacyBluetoothActive = true,
            ).state,
        )
        assertEquals(
            AudioRouteState.UNKNOWN,
            resolve(attributesRoute = emptyList()).state,
        )
    }

    @Test
    fun samsungSpeakerBluetoothConflictDefersThenUsesPersistentEvidence() {
        val evidence = evidence(
            attributesRoute = listOf(AudioDeviceInfo.TYPE_BUILTIN_SPEAKER),
            legacyBluetoothActive = true,
            bluetoothOutputPresent = true,
        )

        val initial = AudioOutputDetector.resolveEvidence(evidence)
        assertEquals(AudioRouteState.TRANSITIONING, initial.state)
        assertEquals("SPEAKER_ATTRIBUTES_BLUETOOTH_ACTIVE", initial.reason)

        val retried = AudioOutputDetector.resolveEvidence(evidence, retryAttempt = 1)
        assertEquals(AudioRouteState.EXTERNAL, retried.state)
        assertEquals("PERSISTENT_BLUETOOTH_CONFLICT_FALLBACK", retried.reason)
    }

    @Test
    fun connectedBluetoothWithoutActiveBluetoothEvidenceDoesNotOverrideSpeaker() {
        assertEquals(
            AudioRouteState.SPEAKER,
            resolve(
                attributesRoute = listOf(AudioDeviceInfo.TYPE_BUILTIN_SPEAKER),
                bluetoothOutputPresent = true,
            ).state,
        )
    }

    @Test
    fun conflictThatResolvesToBluetoothOnRetryIsExternal() {
        val initial = resolve(
            attributesRoute = listOf(AudioDeviceInfo.TYPE_BUILTIN_SPEAKER),
            legacyBluetoothActive = true,
            bluetoothOutputPresent = true,
        )
        assertEquals(AudioRouteState.TRANSITIONING, initial.state)

        val retried = resolve(
            attributesRoute = listOf(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP),
            retryAttempt = 1,
        )
        assertEquals(AudioRouteState.EXTERNAL, retried.state)
    }

    @Test
    fun conflictThatResolvesToSpeakerOnRetryRemainsSpeaker() {
        val initial = resolve(
            attributesRoute = listOf(AudioDeviceInfo.TYPE_BUILTIN_SPEAKER),
            legacyBluetoothActive = true,
            bluetoothOutputPresent = true,
        )
        assertEquals(AudioRouteState.TRANSITIONING, initial.state)

        val retried = resolve(
            attributesRoute = listOf(AudioDeviceInfo.TYPE_BUILTIN_SPEAKER),
            bluetoothOutputPresent = true,
            retryAttempt = 1,
        )
        assertEquals(AudioRouteState.SPEAKER, retried.state)
    }

    @Test
    fun unknownRouteDoesNotCountAsExternal() {
        assertFalse(
            AudioOutputDetector.hasExternalOutputType(
                listOf(AudioDeviceInfo.TYPE_UNKNOWN, AudioDeviceInfo.TYPE_BUILTIN_SPEAKER),
            ),
        )
    }

    private fun resolve(
        attributesRoute: List<Int>?,
        legacyBluetoothActive: Boolean = false,
        legacyWiredActive: Boolean = false,
        bluetoothOutputPresent: Boolean = false,
        retryAttempt: Int = 0,
    ): AudioRouteResolution = AudioOutputDetector.resolveEvidence(
        evidence(
            attributesRoute = attributesRoute,
            legacyBluetoothActive = legacyBluetoothActive,
            legacyWiredActive = legacyWiredActive,
            bluetoothOutputPresent = bluetoothOutputPresent,
        ),
        retryAttempt = retryAttempt,
    )

    private fun evidence(
        attributesRoute: List<Int>?,
        legacyBluetoothActive: Boolean = false,
        legacyWiredActive: Boolean = false,
        bluetoothOutputPresent: Boolean = false,
    ): AudioRouteEvidence = AudioRouteEvidence(
        attributesRoute = attributesRoute,
        legacyBluetoothActive = legacyBluetoothActive,
        legacyWiredActive = legacyWiredActive,
        bluetoothOutputPresent = bluetoothOutputPresent,
        availableOutputTypes = buildList {
            if (bluetoothOutputPresent) add(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP)
        },
    )
}

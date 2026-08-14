package com.trackvoice.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioOutputPolicyTest {
    @Test
    fun externalOnlyBlocksBuiltInSpeakerAndEarpiece() {
        assertFalse(AnnouncementOutputPolicy.EXTERNAL_ONLY.allows(externalAudioOutput = false))
    }

    @Test
    fun externalOnlyAllowsExternalRoutes() {
        assertTrue(AnnouncementOutputPolicy.EXTERNAL_ONLY.allows(externalAudioOutput = true))
    }

    @Test
    fun allOutputsAllowsBuiltInSpeaker() {
        assertTrue(AnnouncementOutputPolicy.ALL_OUTPUTS.allows(externalAudioOutput = false))
    }

    @Test
    fun legacyMissingValuesKeepThePreviousSafeDefault() {
        assertEquals(
            AnnouncementOutputPolicy.EXTERNAL_ONLY,
            AnnouncementOutputPolicy.fromLegacy(
                headphonesOnly = null,
                suppressDuringSpeakerPlayback = null,
            ),
        )
    }

    @Test
    fun legacyBothFalseMeansAllOutputs() {
        assertEquals(
            AnnouncementOutputPolicy.ALL_OUTPUTS,
            AnnouncementOutputPolicy.fromLegacy(
                headphonesOnly = false,
                suppressDuringSpeakerPlayback = false,
            ),
        )
    }

    @Test
    fun eitherLegacySuppressionSwitchMeansExternalOnly() {
        listOf(
            true to false,
            false to true,
            true to true,
        ).forEach { (headphonesOnly, suppressSpeaker) ->
            assertEquals(
                AnnouncementOutputPolicy.EXTERNAL_ONLY,
                AnnouncementOutputPolicy.fromLegacy(headphonesOnly, suppressSpeaker),
            )
        }
    }
}

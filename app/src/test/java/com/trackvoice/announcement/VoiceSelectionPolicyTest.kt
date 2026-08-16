package com.trackvoice.announcement

import com.trackvoice.data.GenderFilter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceSelectionPolicyTest {
    @Test
    fun requestedFemaleDoesNotAcceptExplicitMaleVoice() {
        val selection = VoiceSelectionPolicy.choose(
            candidates = listOf(
                candidate("male", GenderFilter.MALE),
                candidate("female", GenderFilter.FEMALE),
            ),
            explicitName = "male",
            requestedGender = GenderFilter.FEMALE,
        )

        assertEquals("female", selection.name)
        assertFalse(selection.usedGenderFallback)
    }

    @Test
    fun requestedMaleChoosesTheBestMaleVoiceDeterministically() {
        val selection = VoiceSelectionPolicy.choose(
            candidates = listOf(
                candidate("female-high", GenderFilter.FEMALE, quality = 500),
                candidate("male-device", GenderFilter.MALE, quality = 300),
                candidate("male-network", GenderFilter.MALE, quality = 900, requiresNetwork = true),
            ),
            explicitName = null,
            requestedGender = GenderFilter.MALE,
        )

        assertEquals("male-device", selection.name)
        assertFalse(selection.usedGenderFallback)
    }

    @Test
    fun missingGenderUsesUnknownBeforeKnownOpposite() {
        val selection = VoiceSelectionPolicy.choose(
            candidates = listOf(
                candidate("male", GenderFilter.MALE),
                candidate("unknown", GenderFilter.UNSPECIFIED),
            ),
            explicitName = null,
            requestedGender = GenderFilter.FEMALE,
        )

        assertEquals("unknown", selection.name)
        assertTrue(selection.usedGenderFallback)
    }

    @Test
    fun anyGenderKeepsExplicitVoice() {
        val selection = VoiceSelectionPolicy.choose(
            candidates = listOf(
                candidate("chosen", GenderFilter.MALE),
                candidate("other", GenderFilter.FEMALE),
            ),
            explicitName = "chosen",
            requestedGender = GenderFilter.ANY,
        )

        assertEquals("chosen", selection.name)
        assertFalse(selection.usedGenderFallback)
    }

    @Test
    fun previewSelectionUsesTheExactRequestedVoice() {
        val selection = VoiceSelectionPolicy.choose(
            candidates = listOf(
                candidate("voice-1", GenderFilter.UNSPECIFIED, quality = 500),
                candidate("voice-2", GenderFilter.UNSPECIFIED, quality = 100),
            ),
            explicitName = "voice-2",
            requestedGender = GenderFilter.ANY,
        )

        assertEquals("voice-2", selection.name)
        assertFalse(selection.usedGenderFallback)
    }

    private fun candidate(
        name: String,
        gender: GenderFilter,
        quality: Int = 500,
        requiresNetwork: Boolean = false,
    ) = VoiceCandidate(name, gender, quality, requiresNetwork)
}

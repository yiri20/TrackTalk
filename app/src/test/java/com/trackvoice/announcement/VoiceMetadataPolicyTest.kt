package com.trackvoice.announcement

import com.trackvoice.data.GenderFilter
import com.trackvoice.data.VoiceLanguage
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceMetadataPolicyTest {
    @Test
    fun duplicateFriendlyNumbersAreStableAndDistinct() {
        val voices = listOf(
            voice("voice-z"),
            voice("voice-a"),
            voice("voice-m"),
        )

        val first = VoiceMetadataPolicy.stableDisplayNumbers(voices, VoiceLanguage.KOREAN, Locale.KOREA)
        val second = VoiceMetadataPolicy.stableDisplayNumbers(voices.reversed(), VoiceLanguage.KOREAN, Locale.KOREA)

        assertEquals(first, second)
        assertEquals(setOf(1, 2, 3), first.values.toSet())
        assertEquals(1, first["voice-a"])
        assertEquals(2, first["voice-m"])
        assertEquals(3, first["voice-z"])
        assertTrue(VoiceMetadataPolicy.labels(voices[0], first[voices[0].name] ?: 0, false).primary.contains("음성"))
    }

    @Test
    fun onlineAndOfflineLabelsUseLocalizedUserFacingTerms() {
        val offline = VoiceMetadataPolicy.labels(
            voice("offline", requiresNetwork = false),
            number = 1,
            english = false,
        )
        val online = VoiceMetadataPolicy.labels(
            voice("online", requiresNetwork = true),
            number = 2,
            english = false,
        )

        assertTrue(offline.secondary.contains("오프라인"))
        assertTrue(online.secondary.contains("온라인 필요"))
        assertFalse(offline.secondary.contains("on-device"))
        assertFalse(online.secondary.contains("online"))
    }

    @Test
    fun qualityAndLatencyMetadataBecomeConciseKoreanAndEnglishLabels() {
        assertEquals("매우 높은 품질", VoiceMetadataPolicy.qualityLabel(500, english = false))
        assertEquals("High quality", VoiceMetadataPolicy.qualityLabel(400, english = true))
        assertEquals("보통", VoiceMetadataPolicy.latencyLabel(300, english = false))
        assertEquals("Very fast", VoiceMetadataPolicy.latencyLabel(100, english = true))
    }

    @Test
    fun unknownGenderIsNotInventedInVoiceRows() {
        val labels = VoiceMetadataPolicy.labels(
            voice("engine.voice.1", gender = GenderFilter.UNSPECIFIED),
            number = 1,
            english = false,
        )

        assertFalse(labels.secondary.contains("여성"))
        assertFalse(labels.secondary.contains("남성"))
    }

    @Test
    fun sortingPrefersOfflineQualityLatencyThenStableName() {
        val sorted = VoiceMetadataPolicy.sort(
            listOf(
                voice("online-best", requiresNetwork = true, quality = 500, latency = 100),
                voice("offline-normal-slow", quality = 300, latency = 500),
                voice("offline-high-slow", quality = 500, latency = 500),
                voice("offline-high-fast", quality = 500, latency = 100),
            ),
        )

        assertEquals(
            listOf("offline-high-fast", "offline-high-slow", "offline-normal-slow", "online-best"),
            sorted.map { it.name },
        )
    }

    @Test
    fun previewSampleFollowsFixedLanguage() {
        assertTrue(VoicePreviewPolicy.sampleFor(VoiceLanguage.KOREAN).contains("TrackTalk"))
        assertTrue(VoicePreviewPolicy.sampleFor(VoiceLanguage.ENGLISH).startsWith("Hello"))
    }

    private fun voice(
        name: String,
        localeTag: String = "ko-KR",
        gender: GenderFilter = GenderFilter.UNSPECIFIED,
        quality: Int = VoiceMetadataPolicy.QUALITY_HIGH,
        latency: Int = VoiceMetadataPolicy.LATENCY_LOW,
        requiresNetwork: Boolean = false,
    ) = InstalledVoice(
        providerId = AndroidSystemTtsProvider.PROVIDER_ID,
        name = name,
        label = name,
        localeTag = localeTag,
        quality = quality,
        requiresNetwork = requiresNetwork,
        gender = gender,
        latency = latency,
        features = emptySet(),
    )
}

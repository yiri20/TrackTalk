package com.trackvoice.announcement

import com.trackvoice.data.GenderFilter
import com.trackvoice.data.VoiceLanguage
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceCatalogPolicyTest {
    private val voices = listOf(
        voice("en-female", "en-US", GenderFilter.FEMALE),
        voice("en-male", "en-US", GenderFilter.MALE),
        voice("ko-female", "ko-KR", GenderFilter.FEMALE),
        voice("ja-neutral", "ja-JP", GenderFilter.UNSPECIFIED),
    )

    @Test
    fun automaticLanguageDoesNotExposeTheGlobalInstalledVoiceCatalog() {
        assertTrue(
            VoiceCatalogPolicy.visibleVoices(
                voices = voices + List(470) { voice("extra-$it", "fr-FR", GenderFilter.UNSPECIFIED) },
                language = VoiceLanguage.AUTO,
                gender = GenderFilter.ANY,
            ).isEmpty(),
        )
        assertTrue(VoiceCatalogPolicy.showsManualVoicePicker(VoiceLanguage.AUTO).not())
    }

    @Test
    fun fixedEnglishFemaleModeFiltersByLanguageAndGender() {
        val visible = VoiceCatalogPolicy.visibleVoices(
            voices = voices,
            language = VoiceLanguage.ENGLISH,
            gender = GenderFilter.FEMALE,
        )

        assertEquals(listOf("en-female"), visible.map { it.name })
    }

    @Test
    fun systemModeUsesTheSystemLanguageForTheManualPicker() {
        val visible = VoiceCatalogPolicy.visibleVoices(
            voices = voices,
            language = VoiceLanguage.SYSTEM,
            gender = GenderFilter.ANY,
            systemLocale = Locale.KOREA,
        )

        assertEquals(listOf("ko-female"), visible.map { it.name })
    }

    private fun voice(name: String, localeTag: String, gender: GenderFilter) = InstalledVoice(
        providerId = AndroidSystemTtsProvider.PROVIDER_ID,
        name = name,
        label = name,
        localeTag = localeTag,
        quality = 500,
        requiresNetwork = false,
        gender = gender,
    )
}

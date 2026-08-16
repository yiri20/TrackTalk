package com.trackvoice.announcement

import com.trackvoice.data.GenderFilter
import com.trackvoice.data.VoiceLanguage
import java.util.Locale

/**
 * Keeps the voice picker scoped to a deliberate language choice. In AUTO
 * mode, the engine chooses a compatible voice for each text segment; showing
 * the complete installed-engine catalog would be misleading and unusable.
 */
object VoiceCatalogPolicy {
    fun visibleVoices(
        voices: List<InstalledVoice>,
        language: VoiceLanguage,
        gender: GenderFilter,
        systemLocale: Locale = Locale.getDefault(),
    ): List<InstalledVoice> {
        val languageCode = languageCode(language, systemLocale) ?: return emptyList()
        return voices.filter { voice ->
            Locale.forLanguageTag(voice.localeTag).language == languageCode &&
                (gender == GenderFilter.ANY || voice.gender == gender)
        }
    }

    fun showsManualVoicePicker(language: VoiceLanguage): Boolean = language != VoiceLanguage.AUTO

    private fun languageCode(language: VoiceLanguage, systemLocale: Locale): String? = when (language) {
        VoiceLanguage.AUTO -> null
        VoiceLanguage.SYSTEM -> systemLocale.language
        VoiceLanguage.KOREAN -> Locale.KOREAN.language
        VoiceLanguage.ENGLISH -> Locale.ENGLISH.language
    }
}

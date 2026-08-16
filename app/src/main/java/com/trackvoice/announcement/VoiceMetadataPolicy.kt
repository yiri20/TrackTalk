package com.trackvoice.announcement

import com.trackvoice.data.GenderFilter
import com.trackvoice.data.VoiceLanguage
import java.util.Locale

/** Localized, provider-neutral presentation for Android TTS voice metadata. */
data class VoiceDisplayLabels(
    val primary: String,
    val secondary: String,
)

object VoiceMetadataPolicy {
    // Android Voice quality/latency constants are ordered from 100 to 500.
    const val QUALITY_VERY_LOW = 100
    const val QUALITY_LOW = 200
    const val QUALITY_NORMAL = 300
    const val QUALITY_HIGH = 400
    const val QUALITY_VERY_HIGH = 500

    const val LATENCY_VERY_LOW = 100
    const val LATENCY_LOW = 200
    const val LATENCY_NORMAL = 300
    const val LATENCY_HIGH = 400
    const val LATENCY_VERY_HIGH = 500

    fun sort(voices: List<InstalledVoice>): List<InstalledVoice> = voices.sortedWith(
        compareBy<InstalledVoice> { it.requiresNetwork }
            .thenByDescending { it.quality }
            .thenBy { it.latency }
            .thenBy { it.name.lowercase(Locale.ROOT) }
            .thenBy { it.name },
    )

    /**
     * Numbers are assigned from the complete language catalog, not the
     * currently selected gender filter, so changing that filter does not make
     * a voice change its display number.
     */
    fun stableDisplayNumbers(
        voices: List<InstalledVoice>,
        language: VoiceLanguage,
        systemLocale: Locale = Locale.getDefault(),
    ): Map<String, Int> {
        val languageCode = languageCode(language, systemLocale) ?: return emptyMap()
        return voices.filter { Locale.forLanguageTag(it.localeTag).language == languageCode }
            .distinctBy { it.name }
            .sortedWith(
                compareBy<InstalledVoice> { it.name.lowercase(Locale.ROOT) }
                    .thenBy { it.name },
            )
            .mapIndexed { index, voice -> voice.name to index + 1 }
            .toMap()
    }

    fun labels(
        voice: InstalledVoice,
        number: Int,
        english: Boolean,
    ): VoiceDisplayLabels {
        val locale = Locale.forLanguageTag(voice.localeTag)
        val displayLocale = if (english) Locale.ENGLISH else Locale.KOREAN
        val languageName = locale.getDisplayLanguage(displayLocale)
            .ifBlank { locale.language }
        val country = locale.getDisplayCountry(displayLocale)
            .takeIf { it.isNotBlank() }
            ?.let { " ($it)" }
            .orEmpty()
        val voiceWord = if (english) "Voice" else "음성"
        val secondary = buildList {
            add(if (voice.requiresNetwork) {
                if (english) "Online required" else "온라인 필요"
            } else {
                if (english) "Offline" else "오프라인"
            })
            when (voice.gender) {
                GenderFilter.FEMALE -> add(if (english) "Female" else "여성")
                GenderFilter.MALE -> add(if (english) "Male" else "남성")
                else -> Unit
            }
            qualityLabel(voice.quality, english)?.let(::add)
            latencyLabel(voice.latency, english)?.let(::add)
        }.joinToString(" · ")

        return VoiceDisplayLabels(
            primary = "$languageName$country · $voiceWord $number",
            secondary = secondary,
        )
    }

    fun qualityLabel(quality: Int, english: Boolean): String? = when (quality) {
        QUALITY_VERY_HIGH -> if (english) "Very high quality" else "매우 높은 품질"
        QUALITY_HIGH -> if (english) "High quality" else "고품질"
        QUALITY_NORMAL -> if (english) "Standard quality" else "보통 품질"
        QUALITY_LOW -> if (english) "Low quality" else "낮은 품질"
        QUALITY_VERY_LOW -> if (english) "Very low quality" else "매우 낮은 품질"
        else -> null
    }

    fun latencyLabel(latency: Int, english: Boolean): String? = when (latency) {
        LATENCY_VERY_LOW -> if (english) "Very fast" else "매우 빠름"
        LATENCY_LOW -> if (english) "Fast" else "빠름"
        LATENCY_NORMAL -> if (english) "Normal" else "보통"
        LATENCY_HIGH -> if (english) "Slow" else "느림"
        LATENCY_VERY_HIGH -> if (english) "Very slow" else "매우 느림"
        else -> null
    }

    private fun languageCode(language: VoiceLanguage, systemLocale: Locale): String? = when (language) {
        VoiceLanguage.AUTO -> null
        VoiceLanguage.SYSTEM -> systemLocale.language
        VoiceLanguage.KOREAN -> Locale.KOREAN.language
        VoiceLanguage.ENGLISH -> Locale.ENGLISH.language
    }
}

object VoicePreviewPolicy {
    fun sampleFor(
        language: VoiceLanguage,
        systemLocale: Locale = Locale.getDefault(),
        voiceLocaleTag: String? = null,
    ): String {
        val languageCode = voiceLocaleTag
            ?.let(Locale::forLanguageTag)
            ?.language
            ?.takeIf { it.isNotBlank() }
            ?: when (language) {
                VoiceLanguage.KOREAN -> Locale.KOREAN.language
                VoiceLanguage.ENGLISH -> Locale.ENGLISH.language
                VoiceLanguage.SYSTEM,
                VoiceLanguage.AUTO,
                -> systemLocale.language
            }
        return when (languageCode.lowercase(Locale.ROOT)) {
            "ko" -> "안녕하세요. TrackTalk 음성 미리 듣기입니다."
            "ja" -> "こんにちは。TrackTalkの音声プレビューです。"
            "zh" -> "你好，这是 TrackTalk 语音预览。"
            else -> "Hello. This is a TrackTalk voice preview."
        }
    }
}

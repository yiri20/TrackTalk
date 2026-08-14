package com.trackvoice.announcement

import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.media.AudioAttributes
import com.trackvoice.data.UserSettings
import com.trackvoice.data.VoiceLanguage
import com.trackvoice.data.GenderFilter
import com.trackvoice.diagnostics.TrackTalkDebugLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

enum class TtsStatus {
    INITIALIZING,
    READY,
    ERROR,
    CLOSED,
}

data class TtsState(
    val status: TtsStatus = TtsStatus.INITIALIZING,
    val message: String = "TTS 초기화 중",
    val fallbackUsed: Boolean = false,
)

data class InstalledVoice(
    val name: String,
    val label: String,
    val localeTag: String,
    val quality: Int,
    val requiresNetwork: Boolean,
    val gender: GenderFilter,
)

object TtsLocaleResolver {
    fun choose(requested: Locale, supported: Set<Locale>, systemDefault: Locale): Pair<Locale, Boolean> {
        val exact = supported.firstOrNull { it == requested }
        if (exact != null) return exact to false
        val sameLanguage = supported.firstOrNull { it.language == requested.language }
        if (sameLanguage != null) return sameLanguage to false
        return systemDefault to true
    }
}

data class LanguageSegment(val text: String, val locale: Locale)

object MixedLanguageSegmenter {
    fun segment(text: String, fallbackLocale: Locale = Locale.getDefault()): List<LanguageSegment> {
        if (text.isBlank()) return emptyList()
        val japaneseContext = text.any { it.code in 0x3040..0x30FF }
        val result = mutableListOf<LanguageSegment>()
        val buffer = StringBuilder()
        val leadingNeutral = StringBuilder()
        var currentLocale: Locale? = null

        fun flush() {
            val locale = currentLocale ?: return
            if (buffer.isNotEmpty()) result += LanguageSegment(buffer.toString(), locale)
            buffer.clear()
        }

        text.forEach { character ->
            val locale = localeFor(character, japaneseContext, fallbackLocale)
            if (locale == null) {
                if (currentLocale == null) leadingNeutral.append(character) else buffer.append(character)
            } else if (currentLocale == null) {
                currentLocale = locale
                buffer.append(leadingNeutral).append(character)
                leadingNeutral.clear()
            } else if (currentLocale?.language == locale.language) {
                buffer.append(character)
            } else {
                flush()
                currentLocale = locale
                buffer.append(character)
            }
        }
        if (currentLocale == null) return listOf(LanguageSegment(text, fallbackLocale))
        flush()
        return result
    }

    private fun localeFor(character: Char, japaneseContext: Boolean, fallbackLocale: Locale): Locale? = when {
        character.code in 0xAC00..0xD7A3 || character.code in 0x3131..0x318E -> Locale.KOREAN
        character.code in 0x3040..0x30FF -> Locale.JAPANESE
        character.code in 0x4E00..0x9FFF -> if (japaneseContext) Locale.JAPANESE else Locale.CHINESE
        Character.UnicodeScript.of(character.code) == Character.UnicodeScript.LATIN -> Locale.ENGLISH
        character.isLetter() -> fallbackLocale
        else -> null
    }
}

class TtsEngine(context: Context) : TextToSpeech.OnInitListener {
    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private val _state = MutableStateFlow(TtsState())
    private val _voices = MutableStateFlow<List<InstalledVoice>>(emptyList())
    private var textToSpeech: TextToSpeech? = null

    val state: StateFlow<TtsState> = _state.asStateFlow()
    val voices: StateFlow<List<InstalledVoice>> = _voices.asStateFlow()

    init {
        mainHandler.post { textToSpeech = TextToSpeech(appContext, this) }
    }

    override fun onInit(status: Int) {
        TrackTalkDebugLog.event("tts_init", "status" to status)
        if (status != TextToSpeech.SUCCESS) {
            _state.value = TtsState(TtsStatus.ERROR, "기본 TTS 엔진을 초기화하지 못했습니다.")
            return
        }
        val engine = textToSpeech ?: return
        runCatching { engine.setOnUtteranceProgressListener(progressListener) }
        runCatching { engine.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build(),
        ) }
        runCatching { refreshVoices(engine) }
        _state.value = TtsState(TtsStatus.READY, "사용 가능한 TTS 음성을 준비했습니다.")
    }

    fun speak(
        text: String,
        settings: UserSettings,
        onFinished: (success: Boolean, message: String) -> Unit,
    ) {
        mainHandler.post {
            if (text.isBlank()) {
                onFinished(false, "읽을 내용이 없습니다.")
                return@post
            }
            val engine = textToSpeech
            if (engine == null || _state.value.status != TtsStatus.READY) {
                onFinished(false, "TTS가 아직 준비되지 않았습니다.")
                return@post
            }

            // QUEUE_FLUSH stops the old audio, but old progress callbacks can still
            // arrive. Complete interrupted batches first so the controller can
            // abandon focus and resume a track that it paused for the old batch.
            pendingResults.values.distinct().forEach { batch ->
                if (!batch.completed) {
                    batch.completed = true
                    batch.callback(false, "이전 음성 안내가 중단되었습니다.")
                }
            }
            pendingResults.clear()
            runCatching { engine.stop() }
            val supportedLocales = runCatching { engine.voices.orEmpty().map { it.locale }.toSet() }
                .getOrDefault(emptySet())
            runCatching { engine.setSpeechRate(settings.speechRate.coerceIn(0.5f, 2f)) }
            runCatching { engine.setPitch(settings.pitch.coerceIn(0.5f, 2f)) }
            val params = Bundle().apply {
                putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, settings.volume.coerceIn(0f, 1f))
            }
            val fallbackLocale = settings.voiceLanguage.toLocale(text)
            val segments = MixedLanguageSegmenter.segment(text, fallbackLocale)
            val batch = PendingBatch(segments.size, onFinished)
            TrackTalkDebugLog.event(
                "tts_enqueue",
                "segments" to segments.size,
                "textLength" to text.length,
                "volume" to settings.volume.coerceIn(0f, 1f),
                "voiceLanguage" to settings.voiceLanguage,
                "gender" to settings.genderFilter,
            )
            var localeFallbackUsed = false
            var genderFallbackUsed = false
            segments.forEachIndexed { index, segment ->
                val (resolvedLocale, localeFallback) = TtsLocaleResolver.choose(
                    requested = segment.locale,
                    supported = supportedLocales,
                    systemDefault = fallbackLocale,
                )
                val languageResult = runCatching { engine.setLanguage(resolvedLocale) }
                    .getOrDefault(TextToSpeech.LANG_NOT_SUPPORTED)
                val segmentFallback = localeFallback || languageResult == TextToSpeech.LANG_MISSING_DATA ||
                    languageResult == TextToSpeech.LANG_NOT_SUPPORTED
                localeFallbackUsed = localeFallbackUsed || segmentFallback
                genderFallbackUsed = genderFallbackUsed || runCatching {
                    selectVoice(engine, resolvedLocale, settings)
                }.getOrDefault(true)
                val utteranceId = "trackvoice-${System.nanoTime()}-$index"
                pendingResults[utteranceId] = batch
                val result = runCatching { engine.speak(
                    segment.text,
                    if (index == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD,
                    params,
                    utteranceId,
                ) }.getOrDefault(TextToSpeech.ERROR)
                if (result == TextToSpeech.ERROR) {
                    failBatch(batch, "TTS 음성 합성에 실패했습니다.")
                    return@post
                }
            }
            _state.value = TtsState(
                status = TtsStatus.READY,
                message = when {
                    localeFallbackUsed && genderFallbackUsed -> "일부 언어 또는 성별 음성이 없어 기본 음성으로 안내합니다."
                    localeFallbackUsed -> "일부 언어 음성이 없어 기본 음성으로 안내합니다."
                    genderFallbackUsed -> "일부 언어에 지정한 성별 음성이 없어 가능한 기본 음성으로 안내합니다."
                    else -> "다국어 TTS 준비 완료"
                },
                fallbackUsed = localeFallbackUsed || genderFallbackUsed,
            )
        }
    }

    fun shutdown() {
        mainHandler.post {
            runCatching { textToSpeech?.stop() }
            runCatching { textToSpeech?.shutdown() }
            textToSpeech = null
            pendingResults.clear()
            _state.value = TtsState(TtsStatus.CLOSED, "TTS 종료")
        }
    }

    private data class PendingBatch(
        var remaining: Int,
        val callback: (Boolean, String) -> Unit,
        var completed: Boolean = false,
    )

    private val pendingResults = mutableMapOf<String, PendingBatch>()

    private val progressListener = object : UtteranceProgressListener() {
        override fun onStart(utteranceId: String?) {
            TrackTalkDebugLog.event("tts_start", "utteranceId" to utteranceId)
        }

        override fun onDone(utteranceId: String?) {
            if (utteranceId == null) return
            TrackTalkDebugLog.event("tts_segment_done", "utteranceId" to utteranceId)
            mainHandler.post {
                val batch = pendingResults.remove(utteranceId) ?: return@post
                batch.remaining -= 1
                if (batch.remaining == 0 && !batch.completed) {
                    batch.completed = true
                    batch.callback(true, "다국어 음성 안내 완료")
                }
            }
        }

        @Deprecated("Deprecated in Android API; kept for TTS compatibility")
        override fun onError(utteranceId: String?) {
            if (utteranceId == null) return
            TrackTalkDebugLog.event("tts_error", "utteranceId" to utteranceId)
            mainHandler.post {
                pendingResults[utteranceId]?.let { failBatch(it, "TTS 재생 중 오류가 발생했습니다.") }
            }
        }

        override fun onError(utteranceId: String?, errorCode: Int) {
            if (utteranceId == null) return
            TrackTalkDebugLog.event("tts_error", "utteranceId" to utteranceId, "errorCode" to errorCode)
            mainHandler.post {
                pendingResults[utteranceId]?.let { failBatch(it, "TTS 재생 오류 코드: $errorCode") }
            }
        }
    }

    private fun failBatch(batch: PendingBatch, message: String) {
        if (batch.completed) return
        batch.completed = true
        pendingResults.entries.removeAll { it.value === batch }
        runCatching { textToSpeech?.stop() }
        runCatching { batch.callback(false, message) }
    }

    private fun selectVoice(engine: TextToSpeech, locale: Locale, settings: UserSettings): Boolean {
        val compatibleVoices = engine.voices.orEmpty().filter { it.locale.language == locale.language }
        val candidates = compatibleVoices.map { voice ->
            VoiceCandidate(
                name = voice.name,
                gender = inferGender(voice.name, voice.features),
                quality = voice.quality,
                requiresNetwork = voice.isNetworkConnectionRequired,
            )
        }
        val selection = VoiceSelectionPolicy.choose(candidates, settings.voiceName, settings.genderFilter)
        selection.name
            ?.let { name -> compatibleVoices.firstOrNull { it.name == name } }
            ?.let(engine::setVoice)
        return selection.usedGenderFallback
    }

    private fun refreshVoices(engine: TextToSpeech) {
        val voiceIndexes = mutableMapOf<String, Int>()
        _voices.value = engine.voices.orEmpty()
            .sortedWith(compareBy({ it.locale.toLanguageTag() }, { it.name }))
            .map { voice ->
                val localeTag = voice.locale.toLanguageTag()
                val gender = inferGender(voice.name, voice.features)
                val indexKey = "$localeTag:${gender.name}"
                val index = (voiceIndexes[indexKey] ?: 0) + 1
                voiceIndexes[indexKey] = index
                val region = voice.locale.getDisplayCountry(Locale.KOREAN)
                    .ifBlank { voice.locale.getDisplayLanguage(Locale.KOREAN) }
                val source = if (voice.isNetworkConnectionRequired) "온라인" else "기기 내장"
                val genderLabel = when (gender) {
                    GenderFilter.FEMALE -> "여성"
                    GenderFilter.MALE -> "남성"
                    else -> "기본"
                }
                InstalledVoice(
                    name = voice.name,
                    label = "$region $genderLabel 음성 $index · $source",
                    localeTag = localeTag,
                    quality = voice.quality,
                    requiresNetwork = voice.isNetworkConnectionRequired,
                    gender = gender,
                )
            }
    }

    private fun VoiceLanguage.toLocale(text: String): Locale = when (this) {
        VoiceLanguage.AUTO -> detectLocale(text)
        VoiceLanguage.SYSTEM -> Locale.getDefault()
        VoiceLanguage.KOREAN -> Locale.KOREAN
        VoiceLanguage.ENGLISH -> Locale.ENGLISH
    }

    private fun detectLocale(text: String): Locale {
        val letters = text.filter(Char::isLetter)
        if (letters.isEmpty()) return Locale.getDefault()
        val hangul = letters.count { it.code in 0xAC00..0xD7A3 || it.code in 0x3131..0x318E }
        return if (hangul * 2 >= letters.length) Locale.KOREAN else Locale.ENGLISH
    }

    private fun inferGender(name: String, features: Set<String> = emptySet()): GenderFilter {
        val normalized = (name + " " + features.joinToString(" ")).lowercase(Locale.ROOT)
        return when {
            listOf("female", "woman", "fem", "-f-", "_f_").any(normalized::contains) -> GenderFilter.FEMALE
            listOf("male", "man", "masc", "-m-", "_m_").any(normalized::contains) -> GenderFilter.MALE
            googleFemaleVoiceCodes.any(normalized::contains) -> GenderFilter.FEMALE
            googleMaleVoiceCodes.any(normalized::contains) -> GenderFilter.MALE
            else -> GenderFilter.UNSPECIFIED
        }
    }

    private companion object {
        val googleFemaleVoiceCodes = listOf(
            "en-us-language", "en-us-x-iob", "en-us-x-iog", "en-us-x-sfg", "en-us-x-tpc", "en-us-x-tpf",
            "ko-kr-language", "ko-kr-x-ism", "ko-kr-x-kob",
            "en-au-language", "en-au-x-aua", "en-au-x-auc", "en-au-x-afh",
            "en-gb-language", "en-gb-x-gba", "en-gb-x-gbc", "en-gb-x-gbg", "en-gb-x-fis",
            "en-in-language", "en-in-x-ahp", "en-in-x-cxx", "en-in-x-ena", "en-in-x-enc",
        )
        val googleMaleVoiceCodes = listOf(
            "en-us-x-iol", "en-us-x-iom", "en-us-x-tpd",
            "ko-kr-x-koc", "ko-kr-x-kod",
            "en-au-x-aub", "en-au-x-aud",
            "en-gb-x-gbb", "en-gb-x-gbd", "en-gb-x-rjs",
            "en-in-x-end", "en-in-x-ene",
        )
    }
}

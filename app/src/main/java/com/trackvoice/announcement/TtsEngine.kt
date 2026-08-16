package com.trackvoice.announcement

import android.content.Context
import android.media.AudioManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.trackvoice.data.UserSettings
import com.trackvoice.data.VoiceLanguage
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

class TtsEngine(context: Context) {
    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private val _state = MutableStateFlow(TtsState())
    private val _voices = MutableStateFlow<List<InstalledVoice>>(emptyList())
    private val ttsProvider: TtsProvider = AndroidSystemTtsProvider(appContext)

    val state: StateFlow<TtsState> = _state.asStateFlow()
    val voices: StateFlow<List<InstalledVoice>> = _voices.asStateFlow()

    init {
        mainHandler.post { ttsProvider.initialize(::onProviderInitialized) }
    }

    private fun onProviderInitialized(success: Boolean) {
        TrackTalkDebugLog.event(
            "tts_init",
            "status" to if (success) TextToSpeech.SUCCESS else TextToSpeech.ERROR,
        )
        if (!success) {
            _state.value = TtsState(TtsStatus.ERROR, "기본 TTS 엔진을 초기화하지 못했습니다.")
            return
        }
        runCatching { ttsProvider.setProgressListener(progressListener) }
        val audioAttributes = TrackTalkAudioAttributes.speech()
        val audioAttributesApplied = runCatching { ttsProvider.setAudioAttributes(audioAttributes) }
            .getOrDefault(false)
        TrackTalkDebugLog.event(
            "TTS_AUDIO_ATTRIBUTES",
            "applied" to audioAttributesApplied,
            "ttsUsage" to TrackTalkAudioAttributes.USAGE_LABEL,
            "ttsContentType" to TrackTalkAudioAttributes.CONTENT_TYPE_LABEL,
            "volumeControlStream" to audioAttributes.volumeControlStream,
        )
        runCatching { refreshVoices() }
        _state.value = TtsState(TtsStatus.READY, "TTS 준비 완료")
    }

    fun speak(
        text: String,
        settings: UserSettings,
        transitionAtMs: Long? = null,
        onFinished: (success: Boolean, message: String) -> Unit,
    ) {
        mainHandler.post {
            if (text.isBlank()) {
                onFinished(false, "읽을 내용이 없습니다.")
                return@post
            }
            if (_state.value.status != TtsStatus.READY) {
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
            utteranceTransitionAtMs.clear()
            runCatching { ttsProvider.stop() }
            val supportedLocales = runCatching { ttsProvider.supportedLocales() }
                .getOrDefault(emptySet())
            runCatching { ttsProvider.setSpeechRate(settings.speechRate.coerceIn(0.5f, 2f)) }
            runCatching { ttsProvider.setPitch(settings.pitch.coerceIn(0.5f, 2f)) }
            val ttsParamVolume = TtsVolumeMapping.parameterForUiVolume(settings.volume)
            val params = Bundle().apply {
                putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, ttsParamVolume)
            }
            logVoiceGainDiagnostic(settings, ttsParamVolume)
            val fallbackLocale = settings.voiceLanguage.toLocale(text)
            val segments = MixedLanguageSegmenter.segment(text, fallbackLocale)
            val batch = PendingBatch(segments.size, onFinished)
            TrackTalkDebugLog.event(
                "tts_enqueue",
                "segments" to segments.size,
                "textLength" to text.length,
                "volume" to ttsParamVolume,
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
                val languageResult = runCatching { ttsProvider.setLanguage(resolvedLocale) }
                    .getOrDefault(TextToSpeech.LANG_NOT_SUPPORTED)
                val segmentFallback = localeFallback || languageResult == TextToSpeech.LANG_MISSING_DATA ||
                    languageResult == TextToSpeech.LANG_NOT_SUPPORTED
                localeFallbackUsed = localeFallbackUsed || segmentFallback
                genderFallbackUsed = genderFallbackUsed || runCatching {
                    selectVoice(ttsProvider, resolvedLocale, settings)
                }.getOrDefault(true)
                val utteranceId = "trackvoice-${System.nanoTime()}-$index"
                pendingResults[utteranceId] = batch
                utteranceTransitionAtMs[utteranceId] = transitionAtMs
                val result = runCatching { ttsProvider.speak(
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
                    else -> "TTS 준비 완료"
                },
                fallbackUsed = localeFallbackUsed || genderFallbackUsed,
            )
        }
    }

    private fun logVoiceGainDiagnostic(settings: UserSettings, ttsParamVolume: Float) {
        val audioManager = appContext.getSystemService(AudioManager::class.java)
        val ttsAttributes = TrackTalkAudioAttributes.speech()
        val ttsVolumeControlStream = ttsAttributes.volumeControlStream
        val plan = AnnouncementPlaybackPlanner.plan(settings)
        val focusMode = when {
            !plan.requestAudioFocus -> "NONE"
            plan.shouldDuckMusic -> "AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK"
            else -> "AUDIOFOCUS_GAIN_TRANSIENT"
        }
        val musicAttenuationMethod = when (plan.musicAttenuationStrategy) {
            MusicAttenuationStrategy.NONE -> "NONE"
            MusicAttenuationStrategy.SYSTEM_DUCK -> "AUDIO_FOCUS_AUTO_DUCK"
            MusicAttenuationStrategy.MEDIA_PAUSE -> "MEDIA_PAUSE"
        }
        TrackTalkDebugLog.event(
            "AUDIO_GAIN_STATE",
            "voiceUiPercent" to (settings.volume * 100f).toInt(),
            "ttsParamVolume" to ttsParamVolume,
            "ttsUsage" to TrackTalkAudioAttributes.USAGE_LABEL,
            "ttsContentType" to TrackTalkAudioAttributes.CONTENT_TYPE_LABEL,
            "musicUiSetting" to plan.musicTreatment,
            "musicAttenuationMethod" to musicAttenuationMethod,
            "mediaStreamVolumeBefore" to runCatching {
                audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
            }.getOrNull(),
            "mediaStreamMax" to runCatching {
                audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            }.getOrNull(),
            "ttsVolumeControlStream" to ttsVolumeControlStream,
            "ttsStreamVolume" to runCatching {
                audioManager.getStreamVolume(ttsVolumeControlStream)
            }.getOrNull(),
            "ttsStreamMax" to runCatching {
                audioManager.getStreamMaxVolume(ttsVolumeControlStream)
            }.getOrNull(),
            "audioFocusGain" to focusMode,
            "outputDeviceTypes" to runCatching {
                audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
                    .map { it.type }
                    .distinct()
                    .sorted()
            }.getOrNull(),
        )
        TrackTalkDebugLog.event(
            "VOICE_GAIN_DIAGNOSTIC",
            "uiVoicePercent" to (settings.volume * 100f).toInt(),
            "effectiveVoicePercent" to (ttsParamVolume * 100f).toInt(),
            "ttsParamVolume" to ttsParamVolume,
            "musicTreatment" to plan.musicTreatment,
            "musicAttenuationMethod" to musicAttenuationMethod,
            "volumeControlStream" to ttsVolumeControlStream,
            "streamVolume" to runCatching {
                audioManager.getStreamVolume(ttsVolumeControlStream)
            }.getOrNull(),
            "streamMaxVolume" to runCatching {
                audioManager.getStreamMaxVolume(ttsVolumeControlStream)
            }.getOrNull(),
            "musicStreamVolume" to runCatching {
                audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
            }.getOrNull(),
            "musicStreamMaxVolume" to runCatching {
                audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            }.getOrNull(),
            "ttsUsage" to TrackTalkAudioAttributes.USAGE_LABEL,
            "ttsContentType" to TrackTalkAudioAttributes.CONTENT_TYPE_LABEL,
            "audioFocusMode" to focusMode,
        )
    }

    fun shutdown() {
        mainHandler.post {
            runCatching { ttsProvider.stop() }
            runCatching { ttsProvider.shutdown() }
            pendingResults.clear()
            utteranceTransitionAtMs.clear()
            _state.value = TtsState(TtsStatus.CLOSED, "TTS 종료")
        }
    }

    private data class PendingBatch(
        var remaining: Int,
        val callback: (Boolean, String) -> Unit,
        var completed: Boolean = false,
    )

    private val pendingResults = mutableMapOf<String, PendingBatch>()
    private val utteranceTransitionAtMs = mutableMapOf<String, Long?>()

    private val progressListener = object : UtteranceProgressListener() {
        override fun onStart(utteranceId: String?) {
            TrackTalkDebugLog.event("tts_start", "utteranceId" to utteranceId)
            TrackTalkDebugLog.event(
                "TTS_STARTED",
                "utteranceId" to utteranceId,
                "transitionToTtsStartMs" to utteranceId?.let { id ->
                    utteranceTransitionAtMs[id]?.let { startAt -> System.currentTimeMillis() - startAt }
                },
            )
        }

        override fun onDone(utteranceId: String?) {
            if (utteranceId == null) return
            TrackTalkDebugLog.event("tts_segment_done", "utteranceId" to utteranceId)
            mainHandler.post {
                val batch = pendingResults.remove(utteranceId) ?: return@post
                utteranceTransitionAtMs.remove(utteranceId)
                batch.remaining -= 1
                if (batch.remaining == 0 && !batch.completed) {
                    batch.completed = true
                    TrackTalkDebugLog.event(
                        "TTS_COMPLETED",
                        "utteranceId" to utteranceId,
                    )
                    batch.callback(true, "다국어 음성 안내 완료")
                }
            }
        }

        @Deprecated("Deprecated in Android API; kept for TTS compatibility")
        override fun onError(utteranceId: String?) {
            if (utteranceId == null) return
            TrackTalkDebugLog.event("tts_error", "utteranceId" to utteranceId)
            mainHandler.post {
                utteranceTransitionAtMs.remove(utteranceId)
                pendingResults[utteranceId]?.let { failBatch(it, "TTS 재생 중 오류가 발생했습니다.") }
            }
        }

        override fun onError(utteranceId: String?, errorCode: Int) {
            if (utteranceId == null) return
            TrackTalkDebugLog.event("tts_error", "utteranceId" to utteranceId, "errorCode" to errorCode)
            mainHandler.post {
                utteranceTransitionAtMs.remove(utteranceId)
                pendingResults[utteranceId]?.let { failBatch(it, "TTS 재생 오류 코드: $errorCode") }
            }
        }
    }

    private fun failBatch(batch: PendingBatch, message: String) {
        if (batch.completed) return
        batch.completed = true
        pendingResults.filterValues { it === batch }.keys.forEach(utteranceTransitionAtMs::remove)
        pendingResults.entries.removeAll { it.value === batch }
        runCatching { ttsProvider.stop() }
        runCatching { batch.callback(false, message) }
    }

    private fun selectVoice(provider: TtsProvider, locale: Locale, settings: UserSettings): Boolean {
        val compatibleVoices = provider.availableVoices().filter {
            Locale.forLanguageTag(it.localeTag).language == locale.language
        }
        val candidates = compatibleVoices.map { voice ->
            VoiceCandidate(
                name = voice.name,
                gender = voice.gender,
                quality = voice.quality,
                requiresNetwork = voice.requiresNetwork,
            )
        }
        val selection = VoiceSelectionPolicy.choose(
            candidates = candidates,
            explicitName = settings.voiceName.takeIf { settings.voiceLanguage != VoiceLanguage.AUTO },
            requestedGender = settings.genderFilter,
        )
        selection.name
            ?.let { name -> compatibleVoices.firstOrNull { it.name == name } }
            ?.let { provider.setVoice(it.name) }
        return selection.usedGenderFallback
    }

    private fun refreshVoices() {
        _voices.value = ttsProvider.availableVoices()
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
}

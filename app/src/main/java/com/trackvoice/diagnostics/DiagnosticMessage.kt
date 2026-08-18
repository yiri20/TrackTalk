package com.trackvoice.diagnostics

/** Stable diagnostic states that the UI translates at render time. */
enum class DiagnosticMessage {
    NEVER_ANNOUNCED,
    AUDIO_FOCUS_UNAVAILABLE,
    TTS_INITIALIZING,
    TTS_INITIALIZATION_FAILED,
    TTS_READY,
    TTS_NOTHING_TO_READ,
    TTS_NOT_READY,
    TTS_INTERRUPTED,
    TTS_SYNTHESIS_FAILED,
    TTS_FALLBACK_LANGUAGE_AND_GENDER,
    TTS_FALLBACK_LANGUAGE,
    TTS_FALLBACK_GENDER,
    TTS_CLOSED,
    TTS_COMPLETED,
    TTS_PLAYBACK_ERROR,
}

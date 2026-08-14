package com.trackvoice.diagnostics

import android.util.Log
import com.trackvoice.BuildConfig

/**
 * Concise debug-only event logging for real-device playback validation.
 * It is disabled in release builds and intentionally logs metadata fields,
 * decisions, and timing rather than the full sentence sent to TTS.
 */
object TrackTalkDebugLog {
    private const val TAG = "TrackTalk.Validation"

    fun event(name: String, vararg fields: Pair<String, Any?>) {
        if (!BuildConfig.DEBUG) return
        val details = fields
            .filter { it.second != null }
            .joinToString(separator = " ") { (key, value) -> "$key=$value" }
        Log.d(TAG, if (details.isBlank()) name else "$name $details")
    }
}

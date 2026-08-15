package com.trackvoice.announcement

import android.media.AudioAttributes

/** Semantic audio attributes for TrackTalk's short spoken instructions. */
object TrackTalkAudioAttributes {
    const val USAGE_LABEL = "USAGE_ASSISTANT"
    const val CONTENT_TYPE_LABEL = "CONTENT_TYPE_SPEECH"

    fun speech(): AudioAttributes = AudioAttributes.Builder()
        // Android defines USAGE_ASSISTANT for audio responses, instructions,
        // and help utterances. TrackTalk is not an AccessibilityService.
        .setUsage(AudioAttributes.USAGE_ASSISTANT)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()
}

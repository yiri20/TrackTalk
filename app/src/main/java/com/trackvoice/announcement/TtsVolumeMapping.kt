package com.trackvoice.announcement

/** Maps the user-facing voice slider directly to TextToSpeech's relative gain. */
object TtsVolumeMapping {
    fun parameterForUiVolume(volume: Float): Float = volume.coerceIn(0f, 1f)
}

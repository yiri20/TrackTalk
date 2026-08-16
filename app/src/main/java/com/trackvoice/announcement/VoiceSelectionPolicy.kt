package com.trackvoice.announcement

import com.trackvoice.data.GenderFilter

data class VoiceCandidate(
    val name: String,
    val gender: GenderFilter,
    val quality: Int,
    val requiresNetwork: Boolean,
    val latency: Int = VoiceMetadataPolicy.LATENCY_NORMAL,
)

data class VoiceSelection(
    val name: String?,
    val usedGenderFallback: Boolean,
)

/** Selects a deterministic voice for every language segment. */
object VoiceSelectionPolicy {
    fun choose(
        candidates: List<VoiceCandidate>,
        explicitName: String?,
        requestedGender: GenderFilter,
    ): VoiceSelection {
        val sorted = candidates.sortedWith(
            compareBy<VoiceCandidate>({ it.requiresNetwork }, { -it.quality }, { it.latency }, { it.name }),
        )
        if (sorted.isEmpty()) return VoiceSelection(null, usedGenderFallback = false)

        val explicit = explicitName?.let { name ->
            sorted.firstOrNull { it.name == name && matchesGender(it.gender, requestedGender) }
        }
        if (explicit != null) return VoiceSelection(explicit.name, usedGenderFallback = false)

        val matching = sorted.firstOrNull { matchesGender(it.gender, requestedGender) }
        if (matching != null) return VoiceSelection(matching.name, usedGenderFallback = false)

        // Some TTS engines do not expose gender metadata for every locale.
        // Prefer an unknown voice over a known opposite-gender voice and keep
        // the choice deterministic so segments do not jump around.
        val neutral = sorted.firstOrNull { it.gender == GenderFilter.UNSPECIFIED } ?: sorted.first()
        return VoiceSelection(neutral.name, usedGenderFallback = true)
    }

    private fun matchesGender(actual: GenderFilter, requested: GenderFilter): Boolean =
        requested == GenderFilter.ANY || actual == requested
}

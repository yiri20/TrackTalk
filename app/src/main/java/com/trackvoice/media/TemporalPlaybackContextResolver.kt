package com.trackvoice.media

import java.util.Locale

/**
 * Resolves playback type using evidence from more than one track transition.
 *
 * MediaSession snapshots often cannot identify the user's starting action. A
 * first track therefore remains UNKNOWN unless the provider exposes a strong
 * queue signal. Repeated, likely-natural transitions within one stable session
 * can later confirm an album. This deliberately favors UNKNOWN when the data is
 * insufficient instead of treating every provider-generated queue as an album.
 */
class TemporalPlaybackContextResolver(
    private val albumConfirmationTransitions: Int = 2,
    private val naturalTransitionMinimumGapMs: Long = 5_000L,
    private val rapidTransitionWindowMs: Long = 2_000L,
) {
    private var state: ContextState? = null

    fun reset() {
        state = null
    }

    fun resolve(
        event: PlaybackEvent,
        sessionKey: String? = null,
    ): TemporalPlaybackContextDecision {
        val directDecision = PlaybackCollectionResolver.resolveWithEvidence(event)
        val previousState = state
        val resetReason = previousState?.let { boundaryReason(it, event, sessionKey) }

        if (previousState == null || resetReason != null) {
            val collection = directDecision.collection
            state = ContextState(
                sessionKey = sessionKey ?: previousState?.sessionKey,
                sourcePackageName = event.sourcePackageName,
                lastEvent = event,
                collection = collection,
                sameAlbumNaturalTransitions = 0,
                mixedNaturalTransitions = 0,
            )
            return TemporalPlaybackContextDecision(
                collection = collection,
                reason = resetReason?.let { "RESET_$it" }
                    ?: if (collection == PlaybackCollection.UNKNOWN) "INITIAL_AMBIGUOUS" else directDecision.reason,
                evidence = directDecision.evidence,
                stateReset = resetReason != null,
                transition = false,
                naturalTransition = false,
                sameAlbumNaturalTransitions = 0,
                mixedNaturalTransitions = 0,
            )
        }

        val previousEvent = previousState.lastEvent
        val transition = !sameLogicalTrack(previousEvent, event)
        val naturalTransition = transition && isLikelyNaturalTransition(previousEvent, event)
        var sameAlbumTransitions = previousState.sameAlbumNaturalTransitions
        var mixedTransitions = previousState.mixedNaturalTransitions
        val sameAlbum = sameAlbum(previousEvent, event)

        if (transition && naturalTransition) {
            if (sameAlbum) {
                sameAlbumTransitions += 1
                mixedTransitions = 0
            } else {
                mixedTransitions += 1
                sameAlbumTransitions = 0
            }
        }

        // A strong current snapshot wins. The previous-event resolver is only
        // used for a likely-natural transition, so a rapid manual skip cannot
        // manufacture an album -> recommendation transition.
        val transitionDecision = if (transition && naturalTransition) {
            PlaybackCollectionResolver.resolve(
                event = event,
                previousEvent = previousEvent,
                previousCollection = previousState.collection,
            )
        } else {
            PlaybackCollection.UNKNOWN
        }
        val strongCurrentCollection = when {
            transitionDecision != PlaybackCollection.UNKNOWN -> transitionDecision
            directDecision.collection != PlaybackCollection.UNKNOWN -> directDecision.collection
            else -> PlaybackCollection.UNKNOWN
        }

        val collection = when {
            strongCurrentCollection != PlaybackCollection.UNKNOWN -> strongCurrentCollection
            !transition -> previousState.collection
            previousState.collection == PlaybackCollection.ALBUM && sameAlbum -> PlaybackCollection.ALBUM
            previousState.collection == PlaybackCollection.ALBUM -> PlaybackCollection.UNKNOWN
            sameAlbumTransitions >= albumConfirmationTransitions -> PlaybackCollection.ALBUM
            else -> PlaybackCollection.UNKNOWN
        }

        val reason = when {
            transitionDecision != PlaybackCollection.UNKNOWN -> "NATURAL_TRANSITION_${transitionDecision.name}"
            directDecision.collection != PlaybackCollection.UNKNOWN -> directDecision.reason
            !transition && collection != PlaybackCollection.UNKNOWN -> "SAME_LOGICAL_TRACK"
            collection == PlaybackCollection.ALBUM && sameAlbumTransitions >= albumConfirmationTransitions ->
                "TEMPORAL_SAME_ALBUM_CONTINUITY"
            transition && naturalTransition && !sameAlbum -> "MIXED_ALBUM_TRANSITION"
            transition && !naturalTransition -> "MANUAL_OR_UNCONFIRMED_TRANSITION"
            transition && sameAlbum -> "SAME_ALBUM_TRANSITION_$sameAlbumTransitions"
            else -> "AMBIGUOUS_MEDIA_SESSION_CONTEXT"
        }

        state = previousState.copy(
            sessionKey = sessionKey ?: previousState.sessionKey,
            lastEvent = event,
            collection = collection,
            sameAlbumNaturalTransitions = sameAlbumTransitions,
            mixedNaturalTransitions = mixedTransitions,
        )
        return TemporalPlaybackContextDecision(
            collection = collection,
            reason = reason,
            evidence = directDecision.evidence,
            stateReset = false,
            transition = transition,
            naturalTransition = naturalTransition,
            sameAlbumNaturalTransitions = sameAlbumTransitions,
            mixedNaturalTransitions = mixedTransitions,
        )
    }

    private fun boundaryReason(
        previous: ContextState,
        current: PlaybackEvent,
        sessionKey: String?,
    ): String? {
        if (previous.sourcePackageName != current.sourcePackageName) return "SOURCE_APP_CHANGED"
        if (previous.sessionKey != null && sessionKey != null && previous.sessionKey != sessionKey) {
            return "MEDIA_SESSION_CHANGED"
        }
        if (previous.lastEvent.playbackState == PlaybackStatus.STOPPED ||
            previous.lastEvent.playbackState == PlaybackStatus.NONE
        ) {
            return "PLAYBACK_SESSION_RESTARTED"
        }

        val previousQueue = previous.lastEvent.queueTitle.normalizedOrNull()
        val currentQueue = current.queueTitle.normalizedOrNull()
        if (
            previousQueue != null &&
            currentQueue != null &&
            previousQueue != currentQueue &&
            !PlaybackCollectionResolver.isGenericQueueTitle(previous.lastEvent.queueTitle) &&
            !PlaybackCollectionResolver.isGenericQueueTitle(current.queueTitle) &&
            !sameLogicalTrack(previous.lastEvent, current)
        ) {
            return "QUEUE_REPLACED"
        }
        return null
    }

    private fun isLikelyNaturalTransition(previous: PlaybackEvent, current: PlaybackEvent): Boolean {
        if (!previous.isPlaying || !current.isPlaying) return false
        if (previous.sourcePackageName != current.sourcePackageName) return false
        val gapMs = (current.observedAt - previous.observedAt).coerceAtLeast(0L)
        if (gapMs < rapidTransitionWindowMs) return false
        if (gapMs < naturalTransitionMinimumGapMs) return false

        val previousPosition = previous.playbackPosition
        val previousDuration = previous.duration
        val playedEnough = when {
            previousPosition == null && previousDuration == null -> gapMs >= 30_000L
            previousPosition == null && previousDuration != null -> gapMs >= (previousDuration * 0.7f).toLong()
            previousDuration == null -> previousPosition?.let { it >= 30_000L } == true
            previousPosition == null -> false
            previousPosition <= 1_000L -> gapMs >= (previousDuration * 0.7f).toLong()
            else -> previousPosition >= (previousDuration * 0.5f).toLong()
        }
        if (!playedEnough) return false

        val previousQueuePosition = previous.activeQueuePosition
        val currentQueuePosition = current.activeQueuePosition
        if (previousQueuePosition != null && currentQueuePosition != null) {
            // A natural transition normally advances one queue item. A jump is
            // more consistent with a manual skip or a queue replacement.
            if (currentQueuePosition != previousQueuePosition + 1) return false
        }
        return true
    }

    private fun sameLogicalTrack(previous: PlaybackEvent, current: PlaybackEvent): Boolean {
        if (previous.sourcePackageName != current.sourcePackageName) return false
        val previousMediaId = previous.mediaId.normalizedOrNull()
        val currentMediaId = current.mediaId.normalizedOrNull()
        if (previousMediaId != null && currentMediaId != null) return previousMediaId == currentMediaId

        val previousTitle = previous.title.normalizedOrNull()
        val currentTitle = current.title.normalizedOrNull()
        if (previousTitle != null && currentTitle != null && previousTitle == currentTitle) {
            return compatibleText(previous.artist, current.artist) &&
                compatibleText(previous.album, current.album)
        }
        val previousPosition = previous.activeQueuePosition
        val currentPosition = current.activeQueuePosition
        return previousPosition != null && previousPosition == currentPosition &&
            previous.queueTitle.normalizedOrNull() == current.queueTitle.normalizedOrNull()
    }

    private fun sameAlbum(previous: PlaybackEvent, current: PlaybackEvent): Boolean {
        val previousAlbum = previous.album.normalizedOrNull() ?: return false
        val currentAlbum = current.album.normalizedOrNull() ?: return false
        return previousAlbum == currentAlbum
    }

    private fun compatibleText(previous: String?, current: String?): Boolean =
        previous.normalizedOrNull() == null ||
            current.normalizedOrNull() == null ||
            previous.normalizedOrNull() == current.normalizedOrNull()

    private fun String?.normalizedOrNull(): String? = this
        ?.trim()
        ?.lowercase(Locale.ROOT)
        ?.replace(Regex("\\s+"), " ")
        ?.takeIf { it.isNotEmpty() }

    private data class ContextState(
        val sessionKey: String?,
        val sourcePackageName: String,
        val lastEvent: PlaybackEvent,
        val collection: PlaybackCollection,
        val sameAlbumNaturalTransitions: Int,
        val mixedNaturalTransitions: Int,
    )
}

data class TemporalPlaybackContextDecision(
    val collection: PlaybackCollection,
    val reason: String,
    val evidence: PlaybackContextEvidence,
    val stateReset: Boolean,
    val transition: Boolean,
    val naturalTransition: Boolean,
    val sameAlbumNaturalTransitions: Int,
    val mixedNaturalTransitions: Int,
)

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
    private val transientPlaybackBridgeMs: Long = 5_000L,
    private val longStopResetMs: Long = 15_000L,
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
        val currentQueueGeneration = queueGeneration(event)

        if (previousState == null || resetReason != null) {
            val collection = directDecision.collection
            state = ContextState(
                sessionKey = sessionKey ?: previousState?.sessionKey,
                sourcePackageName = event.sourcePackageName,
                lastEvent = event,
                lastPlayingEvent = event.takeIf { it.isPlaying },
                collection = collection,
                sameAlbumNaturalTransitions = 0,
                mixedNaturalTransitions = 0,
                queueGeneration = currentQueueGeneration,
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
                previousTrackIdentity = previousState?.lastEvent?.trackIdentity(),
                currentTrackIdentity = event.trackIdentity(),
                albumSameAsPrevious = previousState?.lastPlayingEvent?.let { sameAlbum(it, event) },
                artistSameAsPrevious = previousState?.lastPlayingEvent?.let { sameArtist(it, event) },
                transitionKind = resetReason?.let { "RESET_$it" } ?: "INITIAL",
                previousPosition = previousState?.lastPlayingEvent?.playbackPosition,
                previousDuration = previousState?.lastPlayingEvent?.duration,
                sessionContinuous = false,
                queueGeneration = currentQueueGeneration,
                queueChanged = previousState?.queueGeneration != currentQueueGeneration,
                evidenceAdded = 0,
                evidenceRemoved = previousState?.sameAlbumNaturalTransitions ?: 0,
                resetReason = resetReason,
                stateBeforeHypothesis = previousState?.collection,
                stateAfterHypothesis = collection,
                confidence = confidence(collection, directDecision.reason, 0),
                stateBeforeSameAlbumNaturalTransitions = previousState?.sameAlbumNaturalTransitions ?: 0,
                stateBeforeMixedNaturalTransitions = previousState?.mixedNaturalTransitions ?: 0,
            )
        }

        val previousEvent = previousState.lastEvent
        val previousPlayingEvent = previousState.lastPlayingEvent
        val transitionPreviousEvent = if (
            previousPlayingEvent != null &&
            !previousEvent.isPlaying &&
            event.isPlaying &&
            !sameLogicalTrack(previousPlayingEvent, event)
        ) {
            previousPlayingEvent
        } else {
            previousEvent
        }
        val transition = !sameLogicalTrack(transitionPreviousEvent, event)
        val naturalPreviousEvent = if (
            !transitionPreviousEvent.isPlaying &&
            previousPlayingEvent != null &&
            (event.observedAt - transitionPreviousEvent.observedAt).coerceAtLeast(0L) <= transientPlaybackBridgeMs
        ) {
            previousPlayingEvent
        } else {
            transitionPreviousEvent
        }
        val naturalTransition = transition && isLikelyNaturalTransition(naturalPreviousEvent, event)
        var sameAlbumTransitions = previousState.sameAlbumNaturalTransitions
        var mixedTransitions = previousState.mixedNaturalTransitions
        val sameAlbum = sameAlbum(naturalPreviousEvent, event)

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
                previousEvent = naturalPreviousEvent,
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
            lastPlayingEvent = event.takeIf { it.isPlaying } ?: previousState.lastPlayingEvent,
            collection = collection,
            sameAlbumNaturalTransitions = sameAlbumTransitions,
            mixedNaturalTransitions = mixedTransitions,
            queueGeneration = currentQueueGeneration,
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
            previousTrackIdentity = transitionPreviousEvent.trackIdentity(),
            currentTrackIdentity = event.trackIdentity(),
            albumSameAsPrevious = sameAlbum(naturalPreviousEvent, event),
            artistSameAsPrevious = sameArtist(naturalPreviousEvent, event),
            transitionKind = when {
                !transition -> "SAME_LOGICAL_TRACK"
                naturalTransition && naturalPreviousEvent !== transitionPreviousEvent -> "BRIDGED_NATURAL_TRANSITION"
                naturalTransition -> "NATURAL_TRANSITION"
                else -> "MANUAL_OR_UNCONFIRMED_TRANSITION"
            },
            previousPosition = naturalPreviousEvent.playbackPosition,
            previousDuration = naturalPreviousEvent.duration,
            sessionContinuous = true,
            queueGeneration = currentQueueGeneration,
            queueChanged = previousState.queueGeneration != currentQueueGeneration,
            evidenceAdded = if (naturalTransition && sameAlbum) 1 else 0,
            evidenceRemoved = if (naturalTransition && !sameAlbum) previousState.sameAlbumNaturalTransitions else 0,
            resetReason = null,
            stateBeforeHypothesis = previousState.collection,
            stateAfterHypothesis = collection,
            confidence = confidence(collection, directDecision.reason, sameAlbumTransitions),
            stateBeforeSameAlbumNaturalTransitions = previousState.sameAlbumNaturalTransitions,
            stateBeforeMixedNaturalTransitions = previousState.mixedNaturalTransitions,
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
        if (
            (previous.lastEvent.playbackState == PlaybackStatus.STOPPED ||
                previous.lastEvent.playbackState == PlaybackStatus.NONE) &&
            (current.observedAt - previous.lastEvent.observedAt).coerceAtLeast(0L) > longStopResetMs
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

    private fun sameArtist(previous: PlaybackEvent, current: PlaybackEvent): Boolean =
        compatibleText(previous.artist, current.artist) &&
            compatibleText(previous.albumArtist, current.albumArtist)

    private fun confidence(
        collection: PlaybackCollection,
        directReason: String,
        sameAlbumTransitions: Int,
    ): Int = when {
        collection == PlaybackCollection.UNKNOWN -> 0
        directReason.startsWith("EXPLICIT_") ||
            directReason == "ALGORITHMIC_QUEUE_TITLE" ||
            directReason == "CANONICAL_ALBUM_QUEUE_METADATA" -> 100
        collection == PlaybackCollection.ALBUM && sameAlbumTransitions >= albumConfirmationTransitions -> 80
        else -> 70
    }

    private fun queueGeneration(event: PlaybackEvent): String? {
        val queueTitle = event.queueTitle?.trim()?.takeIf { it.isNotEmpty() }
        if (queueTitle == null && event.queue.isEmpty() && !event.queueOrderChanged) return null
        val itemIdsHash = event.queue
            .map { it.mediaId?.trim().orEmpty() }
            .hashCode()
        return listOf(queueTitle.orEmpty(), event.queue.size, itemIdsHash).joinToString(":")
    }

    private fun PlaybackEvent.trackIdentity(): String? =
        mediaId?.trim()?.takeIf { it.isNotEmpty() }
            ?: listOfNotNull(title?.trim(), artist?.trim(), album?.trim())
                .takeIf { it.isNotEmpty() }
                ?.joinToString(" / ")

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
        val lastPlayingEvent: PlaybackEvent?,
        val collection: PlaybackCollection,
        val sameAlbumNaturalTransitions: Int,
        val mixedNaturalTransitions: Int,
        val queueGeneration: String?,
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
    val previousTrackIdentity: String? = null,
    val currentTrackIdentity: String? = null,
    val albumSameAsPrevious: Boolean? = null,
    val artistSameAsPrevious: Boolean? = null,
    val transitionKind: String = "UNKNOWN",
    val previousPosition: Long? = null,
    val previousDuration: Long? = null,
    val sessionContinuous: Boolean = false,
    val queueGeneration: String? = null,
    val queueChanged: Boolean = false,
    val evidenceAdded: Int = 0,
    val evidenceRemoved: Int = 0,
    val resetReason: String? = null,
    val stateBeforeHypothesis: PlaybackCollection? = null,
    val stateAfterHypothesis: PlaybackCollection? = null,
    val confidence: Int = 0,
    val stateBeforeSameAlbumNaturalTransitions: Int = 0,
    val stateBeforeMixedNaturalTransitions: Int = 0,
)

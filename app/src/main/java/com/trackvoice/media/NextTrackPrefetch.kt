package com.trackvoice.media

/**
 * Describes how much of the next queue item is safe to prepare before the
 * player reports that it is actually playing.
 *
 * This is deliberately metadata-only. A prepared prediction is never a
 * PlaybackEvent and never authorizes audio focus, ducking, pausing, or TTS.
 */
enum class NextTrackPrefetchQuality {
    FULL,
    PARTIAL,
    IDENTITY_ONLY,
    NONE,
}

enum class NextTrackMetadataField {
    TITLE,
    ARTIST,
    ALBUM,
    TRACK_NUMBER,
    MEDIA_ID,
}

data class PreparedNextTrack(
    val sourcePackageName: String,
    val sessionKey: String?,
    val queueTitle: String?,
    val queueGeneration: String,
    val anchor: NextTrackIdentity,
    val predicted: NextTrackIdentity,
    val title: String?,
    val artist: String?,
    val album: String?,
    val trackNumber: Int?,
    val quality: NextTrackPrefetchQuality,
    val availableFields: Set<NextTrackMetadataField>,
    val preparedAt: Long,
)

data class NextTrackIdentity(
    val mediaId: String?,
    val queueItemId: Long?,
    val title: String?,
    val artist: String?,
    val album: String?,
)

/** Pure, deterministic helpers for the transient next-track prediction. */
object NextTrackPrefetch {
    fun quality(item: QueueItemSnapshot): NextTrackPrefetchQuality {
        val fields = availableFields(item)
        val hasStableIdentity = NextTrackMetadataField.MEDIA_ID in fields || item.queueItemId != null
        return when {
            NextTrackMetadataField.TITLE in fields &&
                NextTrackMetadataField.ARTIST in fields &&
                NextTrackMetadataField.ALBUM in fields &&
                NextTrackMetadataField.TRACK_NUMBER in fields -> NextTrackPrefetchQuality.FULL
            fields.any { it != NextTrackMetadataField.MEDIA_ID } -> NextTrackPrefetchQuality.PARTIAL
            hasStableIdentity -> NextTrackPrefetchQuality.IDENTITY_ONLY
            else -> NextTrackPrefetchQuality.NONE
        }
    }

    fun availableFields(item: QueueItemSnapshot): Set<NextTrackMetadataField> = buildSet {
        if (!item.title.isNullOrBlank()) add(NextTrackMetadataField.TITLE)
        if (!item.artist.isNullOrBlank()) add(NextTrackMetadataField.ARTIST)
        if (!item.album.isNullOrBlank()) add(NextTrackMetadataField.ALBUM)
        if (item.trackNumber != null) add(NextTrackMetadataField.TRACK_NUMBER)
        if (!item.mediaId.isNullOrBlank()) add(NextTrackMetadataField.MEDIA_ID)
    }

    fun prepare(
        event: PlaybackEvent,
        sessionKey: String?,
        preparedAt: Long,
    ): PreparedNextTrack? {
        val activePosition = event.activeQueuePosition ?: return null
        val current = event.queue.getOrNull(activePosition) ?: return null
        if (!queueItemMatchesEvent(event, current)) return null
        val next = event.queue.getOrNull(activePosition + 1) ?: return null
        val quality = quality(next)
        if (quality == NextTrackPrefetchQuality.NONE) return null
        return PreparedNextTrack(
            sourcePackageName = event.sourcePackageName,
            sessionKey = sessionKey,
            queueTitle = event.queueTitle,
            queueGeneration = queueGeneration(event),
            anchor = identity(event, current),
            predicted = identity(next),
            title = next.title,
            artist = next.artist,
            album = next.album,
            trackNumber = next.trackNumber,
            quality = quality,
            availableFields = availableFields(next),
            preparedAt = preparedAt,
        )
    }

    /** True when the current event is still the track that produced the prediction. */
    fun anchorMatches(
        prepared: PreparedNextTrack,
        event: PlaybackEvent,
        sessionKey: String?,
    ): Boolean = prepared.sourcePackageName == event.sourcePackageName &&
        prepared.sessionKey == sessionKey &&
        sameIdentity(prepared.anchor, identity(event, event.currentQueueItem()))

    /** True only when the actual current queue item is the predicted item. */
    fun matches(
        prepared: PreparedNextTrack,
        event: PlaybackEvent,
        sessionKey: String?,
    ): Boolean = prepared.sourcePackageName == event.sourcePackageName &&
        prepared.sessionKey == sessionKey &&
        sameIdentity(prepared.predicted, identity(event, event.currentQueueItem()))

    fun samePrediction(first: PreparedNextTrack, second: PreparedNextTrack): Boolean =
        first.sourcePackageName == second.sourcePackageName &&
            first.sessionKey == second.sessionKey &&
            first.queueTitle == second.queueTitle &&
            first.queueGeneration == second.queueGeneration &&
            sameIdentity(first.anchor, second.anchor) &&
            sameIdentity(first.predicted, second.predicted) &&
            first.title == second.title &&
            first.artist == second.artist &&
            first.album == second.album &&
            first.trackNumber == second.trackNumber &&
            first.quality == second.quality

    /**
     * Merge only fields the real event does not have. The real MediaSession
     * event remains authoritative and can enrich or correct the prediction.
     */
    fun mergeMissingMetadata(prepared: PreparedNextTrack, event: PlaybackEvent): PlaybackEvent {
        val canUseTrackNumber = event.trackNumber == null && prepared.trackNumber != null
        return event.copy(
            title = event.title ?: prepared.title,
            artist = event.artist ?: prepared.artist,
            album = event.album ?: prepared.album,
            trackNumber = event.trackNumber ?: prepared.trackNumber,
            trackNumberReliable = event.trackNumberReliable || canUseTrackNumber,
            trackNumberSource = when {
                event.trackNumber != null -> event.trackNumberSource
                prepared.trackNumber != null -> TrackNumberSource.QUEUE_ITEM_METADATA
                else -> event.trackNumberSource
            },
        )
    }

    private fun identity(event: PlaybackEvent, item: QueueItemSnapshot?): NextTrackIdentity = NextTrackIdentity(
        mediaId = event.mediaId ?: item?.mediaId,
        queueItemId = item?.queueItemId,
        title = event.title ?: item?.title,
        artist = event.artist ?: item?.artist,
        album = event.album ?: item?.album,
    )

    private fun identity(item: QueueItemSnapshot): NextTrackIdentity = NextTrackIdentity(
        mediaId = item.mediaId,
        queueItemId = item.queueItemId,
        title = item.title,
        artist = item.artist,
        album = item.album,
    )

    private fun queueItemMatchesEvent(event: PlaybackEvent, item: QueueItemSnapshot): Boolean {
        val eventMediaId = event.mediaId.clean()
        val itemMediaId = item.mediaId.clean()
        if (eventMediaId != null && itemMediaId != null) return eventMediaId == itemMediaId

        val eventTitle = event.title.normalized()
        val itemTitle = item.title.normalized()
        if (eventTitle.isNotBlank() && itemTitle.isNotBlank() && eventTitle != itemTitle) return false

        val eventArtist = event.artist.normalized()
        val itemArtist = item.artist.normalized()
        if (eventArtist.isNotBlank() && itemArtist.isNotBlank() && eventArtist != itemArtist) return false

        // A provider may omit one side of the description. The mapper already
        // treats the populated side as usable current metadata.
        return eventTitle.isNotBlank() || eventMediaId != null || itemTitle.isNotBlank()
    }

    private fun sameIdentity(expected: NextTrackIdentity, actual: NextTrackIdentity): Boolean {
        val expectedMediaId = expected.mediaId.clean()
        val actualMediaId = actual.mediaId.clean()
        if (expectedMediaId != null && actualMediaId != null) return expectedMediaId == actualMediaId
        val expectedTitle = expected.title.normalized()
        val actualTitle = actual.title.normalized()
        val expectedArtist = expected.artist.normalized()
        val actualArtist = actual.artist.normalized()
        // YouTube Music can rebuild a generated queue and assign new queue
        // IDs without changing the track. Content identity is therefore the
        // primary match whenever title/artist are available.
        if (expectedTitle.isNotBlank() && actualTitle.isNotBlank()) {
            if (expectedTitle != actualTitle) return false
            if (expectedArtist.isNotBlank() && actualArtist.isNotBlank()) {
                return expectedArtist == actualArtist
            }
        }
        if (expected.queueItemId != null && actual.queueItemId != null) {
            return expected.queueItemId == actual.queueItemId
        }
        return expectedTitle.isNotBlank() && expectedTitle == actualTitle
    }

    private fun PlaybackEvent.currentQueueItem(): QueueItemSnapshot? =
        activeQueuePosition?.let(queue::getOrNull)

    private fun String?.clean(): String? = this?.trim()?.takeIf { it.isNotEmpty() }

    private fun String?.normalized(): String = clean()
        ?.lowercase(java.util.Locale.ROOT)
        ?.replace(Regex("\\s+"), " ")
        .orEmpty()

    private fun queueGeneration(event: PlaybackEvent): String = event.queue
        .joinToString("|") { item ->
            listOf(
                item.mediaId.orEmpty(),
                item.title.orEmpty(),
                item.artist.orEmpty(),
                item.album.orEmpty(),
            ).joinToString("~")
        }
}

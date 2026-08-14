package com.trackvoice.media

import com.trackvoice.data.CollectionFallback
import java.util.Locale

enum class PlaybackStatus {
    PLAYING,
    PAUSED,
    BUFFERING,
    STOPPED,
    NONE,
}

data class QueueItemSnapshot(
    val mediaId: String?,
    val title: String?,
    val artist: String?,
    val album: String? = null,
    val trackNumber: Int? = null,
)

data class PlaybackEvent(
    val sourcePackageName: String,
    val sourceAppName: String,
    val title: String?,
    val artist: String?,
    val album: String?,
    val albumArtist: String?,
    val trackNumber: Int?,
    val totalTracks: Int?,
    val discNumber: Int?,
    val duration: Long?,
    val mediaId: String?,
    val playbackState: PlaybackStatus,
    val playbackPosition: Long?,
    val queue: List<QueueItemSnapshot> = emptyList(),
    val observedAt: Long,
    val queueTitle: String? = null,
    val activeQueuePosition: Int? = null,
    val queueOrderChanged: Boolean = false,
    val shuffleState: ShuffleState = ShuffleState.UNKNOWN,
    val trackNumberReliable: Boolean = true,
) {
    val hasTitle: Boolean get() = !title.isNullOrBlank()
    /**
     * A media session can publish the title after album/queue metadata. Keep
     * that session selectable while there is still enough track evidence to
     * wait for the enrichment, but do not select an entirely empty player.
     */
    val hasTrackMetadata: Boolean
        get() = hasTitle ||
            !mediaId.isNullOrBlank() ||
            !album.isNullOrBlank() ||
            trackNumber != null ||
            activeQueuePosition != null
    val isPlaying: Boolean get() = playbackState == PlaybackStatus.PLAYING
}

enum class ShuffleState {
    UNKNOWN,
    OFF,
    ON,
}

object AlbumTrackNumberResolver {
    fun resolve(event: PlaybackEvent, allowQueuePositionFallback: Boolean): Int? {
        val effectiveTotalTracks = event.totalTracks ?: event.queue.size.takeIf { it > 1 }
        val directTrack = event.trackNumber
            ?.takeIf { event.trackNumberReliable }
            ?.takeIf { it.isValidTrack(effectiveTotalTracks) }
        if (directTrack != null) return directTrack

        // A queue position is not an album track number once the player has
        // shuffled or reordered its queue. It is safer to omit the number than
        // to announce a convincing but incorrect "track 1".
        if (!allowQueuePositionFallback || event.queueOrderChanged || event.shuffleState == ShuffleState.ON) {
            return null
        }
        return event.activeQueuePosition
            ?.plus(1)
            ?.takeIf { it.isValidTrack(effectiveTotalTracks) }
    }

    fun isFirstAlbumTrack(event: PlaybackEvent): Boolean {
        if (event.queueOrderChanged || event.shuffleState == ShuffleState.ON) return false

        val directTrack = event.trackNumber
            ?.takeIf { event.trackNumberReliable }
            ?.takeIf { it.isValidTrack(null) }
        if (directTrack != null) {
            return directTrack == 1 && (event.discNumber == null || event.discNumber <= 1)
        }

        // A queue position is only a safe first-track signal when the player
        // has not shuffled or reordered the album queue.
        return event.activeQueuePosition == 0
    }

    private fun Int.isValidTrack(totalTracks: Int?): Boolean =
        this in 1..999 && (totalTracks == null || totalTracks >= this)
}

enum class PlaybackCollection {
    ALBUM,
    PLAYLIST,
    ALGORITHMIC,
    UNKNOWN,
}

object PlaybackCollectionResolver {
    fun applyFallback(
        detected: PlaybackCollection,
        fallback: CollectionFallback,
    ): PlaybackCollection = if (detected != PlaybackCollection.UNKNOWN) {
        detected
    } else {
        when (fallback) {
            CollectionFallback.AUTO -> PlaybackCollection.UNKNOWN
            CollectionFallback.ALBUM -> PlaybackCollection.ALBUM
            CollectionFallback.PLAYLIST -> PlaybackCollection.PLAYLIST
            CollectionFallback.ALGORITHMIC -> PlaybackCollection.ALGORITHMIC
        }
    }

    /**
     * Resolves a track while considering the previous stable playback
     * context. A recommendation that starts immediately after the last track
     * of an album can still expose the recommended song's own album and track
     * metadata. Those fields describe the song, not the queue that selected
     * it, so the transition must take precedence over the song metadata.
     */
    fun resolve(
        event: PlaybackEvent,
        previousEvent: PlaybackEvent?,
        previousCollection: PlaybackCollection,
    ): PlaybackCollection {
        val detected = resolve(event)
        if (
            previousCollection == PlaybackCollection.ALBUM &&
            previousEvent != null &&
            isAutomaticRecommendationTransition(previousEvent, event)
        ) {
            return PlaybackCollection.ALGORITHMIC
        }
        return detected
    }

    fun resolve(event: PlaybackEvent): PlaybackCollection {
        val queueTitle = event.queueTitle.normalized()
        val album = event.album.normalized().takeIf { it.isNotBlank() }
        when {
            queueTitle.isExplicitPlaylistTitle() -> {
                return PlaybackCollection.PLAYLIST
            }
            queueTitle.isExplicitAlbumTitle() -> {
                return PlaybackCollection.ALBUM
            }
        }

        // A recommendation/radio title identifies the source queue, even
        // when the current song also exposes ordinary album metadata. Album
        // metadata describes the track, not necessarily the queue it came
        // from.
        if (queueTitle.isAlgorithmicTitle()) {
            return PlaybackCollection.ALGORITHMIC
        }

        val queueAlbums = event.queue.mapNotNull { it.album.normalized().takeIf(String::isNotBlank) }.distinct()
        val hasMeaningfulQueueTitle = queueTitle.isNotBlank() && !queueTitle.isGenericTitle()
        if (hasMeaningfulQueueTitle) {
            if (album != null && queueTitle.matchesAlbum(album)) {
                return PlaybackCollection.ALBUM
            }
            if (event.queue.size > 1) {
                // A named, non-generic queue that is not the current album is
                // the strongest playlist signal available from MediaSession.
                return PlaybackCollection.PLAYLIST
            }
        }

        if (queueAlbums.size > 1) return PlaybackCollection.PLAYLIST
        if (event.queue.size > 1 && album != null && queueAlbums.singleOrNull() == album) {
            return PlaybackCollection.ALBUM
        }

        // YouTube Music and several other players expose an album queue as a
        // generic "next track" queue. In that snapshot they keep the current
        // album but omit both track-number metadata and album names from queue
        // items. A multi-item generic queue is the best album signal left;
        // without this fallback album playback is repeatedly reported as an
        // ambiguous or previously selected recommendation queue. Album and
        // track metadata by itself is deliberately not enough: those fields
        // describe the selected song even when the user tapped one song
        // directly from search or a recommendation surface.
        if (album != null && event.queue.size > 1 && queueTitle.isGenericTitle()) {
            return PlaybackCollection.ALBUM
        }

        // A queue position by itself only says that the player has a queue;
        // it does not say that the queue is an album or an algorithmic mix.
        // Leave this ambiguous rather than applying the wrong settings.
        return PlaybackCollection.UNKNOWN
    }

    fun isGenericQueueTitle(queueTitle: String?): Boolean = queueTitle.normalized().isGenericTitle()

    private fun isAutomaticRecommendationTransition(
        previous: PlaybackEvent,
        current: PlaybackEvent,
    ): Boolean {
        if (!previous.hasTitle || !current.isPlaying) return false
        if (previous.sourcePackageName != current.sourcePackageName) return false
        if (sameTrack(previous, current)) return false

        val previousTotal = previous.totalTracks
            ?: previous.queue.size.takeIf { it > 1 }
            ?: return false
        val previousTrack = AlbumTrackNumberResolver.resolve(
            previous,
            allowQueuePositionFallback = true,
        ) ?: return false
        if (previousTrack < previousTotal) return false

        val previousAlbum = previous.album.normalized().takeIf { it.isNotBlank() }
        val currentAlbum = current.album.normalized().takeIf { it.isNotBlank() }
        // A player may briefly emit a new media ID while continuing to play
        // the same album. That is still album playback, not a recommendation
        // transition.
        if (previousAlbum != null && currentAlbum != null && previousAlbum == currentAlbum) {
            return false
        }

        val currentQueueTitle = current.queueTitle.normalized()
        val queueTitleLooksStale = previousAlbum != null &&
            currentAlbum != null &&
            previousAlbum != currentAlbum &&
            currentQueueTitle == previousAlbum
        // An explicitly named album or playlist is a direct user/queue
        // signal and should remain in that category. Generic queue titles,
        // empty titles, and recommendation-like titles are not.
        if (currentQueueTitle.isExplicitPlaylistTitle()) {
            return false
        }
        if (currentQueueTitle.isExplicitAlbumTitle() && !queueTitleLooksStale) return false
        if (
            currentQueueTitle.isNotBlank() &&
            !queueTitleLooksStale &&
            !currentQueueTitle.isGenericTitle() &&
            !currentQueueTitle.isAlgorithmicTitle() &&
            !currentQueueTitle.isRecommendationLikeTitle()
        ) {
            return false
        }

        return current.hasTitle
    }

    private fun sameTrack(previous: PlaybackEvent, current: PlaybackEvent): Boolean {
        val previousMediaId = previous.mediaId.normalized()
        val currentMediaId = current.mediaId.normalized()
        if (previousMediaId.isNotBlank() && currentMediaId.isNotBlank()) {
            return previousMediaId == currentMediaId
        }
        return previous.title.normalized() == current.title.normalized() &&
            previous.artist.normalized() == current.artist.normalized() &&
            previous.album.normalized() == current.album.normalized()
    }

    private fun String?.normalized(): String = this
        ?.trim()
        ?.lowercase(Locale.ROOT)
        ?.replace(Regex("\\s+"), " ")
        .orEmpty()

    private fun String.isAlgorithmicTitle(): Boolean = containsAny(
        "algorithm",
        "autoplay",
        "auto play",
        "radio",
        "station",
        "discover weekly",
        "release radar",
        "daily mix",
        "recommend",
        "알고리즘",
        "자동재생",
        "자동 재생",
        "라디오",
        "스테이션",
        "추천",
    )

    private fun String.isRecommendationLikeTitle(): Boolean = containsAny(
        "mix ",
        " mix",
        "random",
        "shuffle",
        "랜덤",
        "셔플",
        "무작위",
        "믹스 ",
        " 믹스",
    )

    private fun String.isExplicitPlaylistTitle(): Boolean = containsAny(
        "playlist",
        "재생목록",
        "플레이리스트",
    )

    private fun String.isExplicitAlbumTitle(): Boolean = containsAny("album", "앨범")

    private fun String.isGenericTitle(): Boolean = containsAny(
        "다음 트랙",
        "다음 곡",
        "up next",
        "next up",
        "now playing",
        "현재 재생",
        "재생 대기열",
        "대기열",
        "queue",
    ) || this in setOf(
        "mix",
        "shuffle",
        "random",
        "믹스",
        "셔플",
        "랜덤",
        "무작위",
    )

    private fun String.matchesAlbum(album: String): Boolean =
        this == album || (length >= 3 && album.length >= 3 && (contains(album) || album.contains(this)))

    private fun String.containsAny(vararg candidates: String): Boolean =
        candidates.any(::contains)
}

data class SessionSnapshot(
    val sessionKey: String,
    val event: PlaybackEvent,
    val isMediaKeySession: Boolean,
    val lastMetadataChangedAt: Long,
    val lastPlaybackStateChangedAt: Long,
    val lastObservedAt: Long,
)

enum class MediaEventType {
    INITIAL,
    METADATA,
    PLAYBACK_STATE,
    QUEUE,
    ACTIVE_SESSIONS,
}

data class MediaMonitorUpdate(
    val selected: SessionSnapshot?,
    val activeSessionCount: Int,
    val eventType: MediaEventType,
    val observedAt: Long,
)

object TrackFingerprint {
    /**
     * Full event identity used when the individual metadata fields matter.
     * This is intentionally kept separate from [announcement] because media
     * sessions often fill metadata in several callbacks for one track.
     */
    fun stable(event: PlaybackEvent): String = listOf(
        event.sourcePackageName,
        event.mediaId.orEmpty(),
        event.title.orEmpty().trim(),
        event.artist.orEmpty().trim(),
        event.album.orEmpty().trim(),
        event.trackNumber?.takeIf { event.trackNumberReliable }?.toString().orEmpty(),
        event.discNumber?.toString().orEmpty(),
    ).joinToString("|")

    /**
     * Identity for one spoken track announcement.
     *
     * Android media sessions can publish the same track first with only a
     * media ID and then publish title/album/track-number metadata. Including
     * all of those fields in the de-duplication key makes the same song look
     * like a new song in the middle of playback. A non-empty media ID is the
     * most reliable identity; metadata is used only when the player provides
     * no media ID at all.
     */
    fun announcement(event: PlaybackEvent): String {
        return listOf(
            announcementBase(event),
            event.trackNumber?.takeIf { event.trackNumberReliable }?.toString().orEmpty(),
            event.discNumber?.toString().orEmpty(),
        ).joinToString("|")
    }

    /**
     * The part of the announcement identity that remains stable while a
     * player enriches optional track metadata.
     */
    fun announcementBase(event: PlaybackEvent): String {
        val mediaId = event.mediaId?.trim()?.takeIf { it.isNotEmpty() }
        return if (mediaId != null) {
            listOf(event.sourcePackageName, "media-id", mediaId).joinToString("|")
        } else {
            listOf(
                event.sourcePackageName,
                "metadata",
                event.title.orEmpty().trim(),
                event.artist.orEmpty().trim(),
                event.album.orEmpty().trim(),
            ).joinToString("|")
        }
    }

    fun event(event: PlaybackEvent): String = listOf(
        stable(event),
        event.playbackState.name,
        event.playbackPosition?.div(1_000L) ?: -1L,
    ).joinToString("|")
}

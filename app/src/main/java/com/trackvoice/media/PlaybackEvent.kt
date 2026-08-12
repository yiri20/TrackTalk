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

    fun resolve(event: PlaybackEvent): PlaybackCollection {
        val queueTitle = event.queueTitle.normalized()
        when {
            queueTitle.isAlgorithmicTitle() -> {
                return PlaybackCollection.ALGORITHMIC
            }
            queueTitle.isExplicitPlaylistTitle() -> {
                return PlaybackCollection.PLAYLIST
            }
            queueTitle.isExplicitAlbumTitle() -> {
                return PlaybackCollection.ALBUM
            }
        }

        val album = event.album.normalized().takeIf { it.isNotBlank() }
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

        val queueAlbums = event.queue.mapNotNull { it.album.normalized().takeIf(String::isNotBlank) }.distinct()
        if (queueAlbums.size > 1) return PlaybackCollection.PLAYLIST
        if (album != null && queueAlbums.singleOrNull() == album) {
            return PlaybackCollection.ALBUM
        }

        val hasCompleteAlbumTrackMetadata = album != null &&
            event.trackNumber != null &&
            event.totalTracks != null
        if (hasCompleteAlbumTrackMetadata && event.queue.size <= 1) {
            return PlaybackCollection.ALBUM
        }

        // A queue position by itself only says that the player has a queue;
        // it does not say that the queue is an album. Leave this ambiguous
        // rather than applying album settings to a playlist track.
        return PlaybackCollection.UNKNOWN
    }

    fun isGenericQueueTitle(queueTitle: String?): Boolean = queueTitle.normalized().isGenericTitle()

    private fun String?.normalized(): String = this
        ?.trim()
        ?.lowercase(Locale.ROOT)
        ?.replace(Regex("\\s+"), " ")
        .orEmpty()

    private fun String.isAlgorithmicTitle(): Boolean = containsAny(
        "algorithm",
        "random",
        "shuffle",
        "autoplay",
        "auto play",
        "radio",
        "mix",
        "station",
        "discover weekly",
        "release radar",
        "daily mix",
        "recommend",
        "알고리즘",
        "랜덤",
        "무작위",
        "셔플",
        "자동재생",
        "자동 재생",
        "라디오",
        "믹스",
        "스테이션",
        "추천",
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

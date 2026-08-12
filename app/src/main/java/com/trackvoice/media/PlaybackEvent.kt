package com.trackvoice.media

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
) {
    val hasTitle: Boolean get() = !title.isNullOrBlank()
    val isPlaying: Boolean get() = playbackState == PlaybackStatus.PLAYING
}

enum class PlaybackCollection {
    ALBUM,
    PLAYLIST,
    ALGORITHMIC,
    UNKNOWN,
}

object PlaybackCollectionResolver {
    fun resolve(event: PlaybackEvent): PlaybackCollection {
        val queueTitle = event.queueTitle.orEmpty().lowercase(Locale.ROOT)
        when {
            queueTitle.containsAny(
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
            ) -> {
                return PlaybackCollection.ALGORITHMIC
            }
            queueTitle.containsAny("playlist", "재생목록", "queue") -> {
                return PlaybackCollection.PLAYLIST
            }
            queueTitle.containsAny("album", "앨범") -> return PlaybackCollection.ALBUM
        }

        val hasAlbumMetadata = !event.album.isNullOrBlank()
        val hasAlbumTrackContext = event.trackNumber != null ||
            event.totalTracks != null ||
            event.activeQueuePosition != null ||
            event.queue.size > 1
        if (hasAlbumMetadata && hasAlbumTrackContext) {
            return PlaybackCollection.ALBUM
        }

        val queueLooksLikeAlbum = event.totalTracks != null &&
            event.queue.size in 2..(event.totalTracks + 1)
        if (event.queue.size > 1 && !queueLooksLikeAlbum) return PlaybackCollection.PLAYLIST
        return PlaybackCollection.UNKNOWN
    }

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
    fun stable(event: PlaybackEvent): String = listOf(
        event.sourcePackageName,
        event.mediaId.orEmpty(),
        event.title.orEmpty().trim(),
        event.artist.orEmpty().trim(),
        event.album.orEmpty().trim(),
        event.trackNumber?.toString().orEmpty(),
        event.discNumber?.toString().orEmpty(),
    ).joinToString("|")

    fun event(event: PlaybackEvent): String = listOf(
        stable(event),
        event.playbackState.name,
        event.playbackPosition?.div(1_000L) ?: -1L,
    ).joinToString("|")
}

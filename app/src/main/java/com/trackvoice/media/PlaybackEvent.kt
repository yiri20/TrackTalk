package com.trackvoice.media

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
) {
    val hasTitle: Boolean get() = !title.isNullOrBlank()
    val isPlaying: Boolean get() = playbackState == PlaybackStatus.PLAYING
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

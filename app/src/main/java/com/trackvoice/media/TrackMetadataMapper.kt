package com.trackvoice.media

import android.media.MediaDescription
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.PlaybackState

class TrackMetadataMapper(
    private val appNameForPackage: (String) -> String,
) {
    fun map(controller: MediaController, observedAt: Long = System.currentTimeMillis()): PlaybackEvent {
        val metadata = controller.metadata
        val state = controller.playbackState
        val rawQueue = controller.queue.orEmpty()
        val queue = rawQueue.map { it.toSnapshot() }
        val metadataMediaId = metadata?.getString(MediaMetadata.METADATA_KEY_MEDIA_ID).clean()
        val activeQueueDescription = state?.activeQueueItemId
            ?.takeIf { it >= 0L }
            ?.let { queueId -> rawQueue.firstOrNull { it.queueId == queueId }?.description }
        val activeQueuePosition = state?.activeQueueItemId
            ?.takeIf { it >= 0L }
            ?.let { queueId -> rawQueue.indexOfFirst { it.queueId == queueId } }
            ?.takeIf { it >= 0 }
            ?: metadataMediaId?.let { mediaId ->
                rawQueue.indexOfFirst { it.description.mediaId == mediaId }
            }?.takeIf { it >= 0 }
        return PlaybackEvent(
            sourcePackageName = controller.packageName,
            sourceAppName = appNameForPackage(controller.packageName),
            title = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE).clean()
                ?: activeQueueDescription?.title?.toString().clean(),
            artist = metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST).clean()
                ?: activeQueueDescription?.subtitle?.toString().clean(),
            album = metadata?.getString(MediaMetadata.METADATA_KEY_ALBUM).clean(),
            albumArtist = metadata?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST).clean(),
            trackNumber = metadata?.getLong(MediaMetadata.METADATA_KEY_TRACK_NUMBER)?.safeInt(),
            totalTracks = metadata?.getLong(MediaMetadata.METADATA_KEY_NUM_TRACKS)?.safeInt(),
            discNumber = metadata?.getLong(MediaMetadata.METADATA_KEY_DISC_NUMBER)?.safeInt(),
            duration = metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION)?.takeIf { it > 0L },
            mediaId = metadataMediaId
                ?: activeQueueDescription?.mediaId.clean(),
            playbackState = state.toPlaybackStatus(),
            playbackPosition = state?.position?.takeIf { it >= 0L },
            queue = queue,
            observedAt = observedAt,
            queueTitle = controller.queueTitle?.toString().clean(),
            activeQueuePosition = activeQueuePosition,
        )
    }

    private fun MediaSession.QueueItem.toSnapshot(): QueueItemSnapshot = QueueItemSnapshot(
        mediaId = description.mediaId,
        title = description.title?.toString().clean(),
        artist = description.subtitle?.toString().clean(),
        album = description.extras?.getString(MediaMetadata.METADATA_KEY_ALBUM).clean(),
        trackNumber = description.extras?.intMetadata(MediaMetadata.METADATA_KEY_TRACK_NUMBER),
    )

    private fun PlaybackState?.toPlaybackStatus(): PlaybackStatus = when (this?.state) {
        PlaybackState.STATE_PLAYING -> PlaybackStatus.PLAYING
        PlaybackState.STATE_BUFFERING -> PlaybackStatus.BUFFERING
        PlaybackState.STATE_PAUSED -> PlaybackStatus.PAUSED
        PlaybackState.STATE_STOPPED -> PlaybackStatus.STOPPED
        else -> PlaybackStatus.NONE
    }

    private fun String?.clean(): String? = this?.trim()?.takeIf { it.isNotEmpty() }
    private fun Long.safeInt(): Int? = toInt().takeIf { it > 0 && it.toLong() == this }

    private fun android.os.Bundle.intMetadata(key: String): Int? {
        if (!containsKey(key)) return null
        val longValue = getLong(key, Long.MIN_VALUE)
        if (longValue != Long.MIN_VALUE) return longValue.safeInt()
        return getInt(key, Int.MIN_VALUE).takeIf { it > 0 }
    }
}

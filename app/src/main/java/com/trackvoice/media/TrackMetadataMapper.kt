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
        val activeQueueDescription = state?.activeQueueItemId
            ?.takeIf { it >= 0L }
            ?.let { queueId -> rawQueue.firstOrNull { it.queueId == queueId }?.description }
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
            mediaId = metadata?.getString(MediaMetadata.METADATA_KEY_MEDIA_ID).clean()
                ?: activeQueueDescription?.mediaId.clean(),
            playbackState = state.toPlaybackStatus(),
            playbackPosition = state?.position?.takeIf { it >= 0L },
            queue = queue,
            observedAt = observedAt,
        )
    }

    private fun MediaSession.QueueItem.toSnapshot(): QueueItemSnapshot = QueueItemSnapshot(
        mediaId = description.mediaId,
        title = description.title?.toString().clean(),
        artist = description.subtitle?.toString().clean(),
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
}

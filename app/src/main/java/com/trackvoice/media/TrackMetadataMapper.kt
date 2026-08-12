package com.trackvoice.media

import android.media.MediaDescription
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.PlaybackState

class TrackMetadataMapper(
    private val appNameForPackage: (String) -> String,
) {
    private val queueHistories = linkedMapOf<String, QueueHistory>()
    private val knownTrackNumbers = linkedMapOf<String, Int>()

    fun map(controller: MediaController, observedAt: Long = System.currentTimeMillis()): PlaybackEvent {
        val metadata = controller.metadata
        val state = controller.playbackState
        val rawQueue = controller.queue.orEmpty()
        val queue = rawQueue.map { it.toSnapshot() }
        val metadataMediaId = metadata?.getString(MediaMetadata.METADATA_KEY_MEDIA_ID).clean()
        val activeQueueDescription = state?.activeQueueItemId
            ?.takeIf { it >= 0L }
            ?.let { queueId -> rawQueue.firstOrNull { it.queueId == queueId }?.description }
            ?: metadataMediaId?.let { mediaId ->
                rawQueue.firstOrNull { it.description.mediaId.clean() == mediaId }?.description
            }
        val activeQueuePosition = state?.activeQueueItemId
            ?.takeIf { it >= 0L }
            ?.let { queueId -> rawQueue.indexOfFirst { it.queueId == queueId } }
            ?.takeIf { it >= 0 }
            ?: metadataMediaId?.let { mediaId ->
                rawQueue.indexOfFirst { it.description.mediaId == mediaId }
            }?.takeIf { it >= 0 }
        val queueOrderChanged = detectQueueOrderChange(controller, rawQueue)
        val shuffleState = state.toShuffleState()
        rememberQueueTrackNumbers(controller.packageName, queue)
        val metadataTrackNumber = metadata?.getLong(MediaMetadata.METADATA_KEY_TRACK_NUMBER)?.safeInt()
        val activeQueueTrackNumber = activeQueueDescription?.extras?.intMetadata(MediaMetadata.METADATA_KEY_TRACK_NUMBER)
        val mediaId = metadataMediaId ?: activeQueueDescription?.mediaId.clean()
        val trackKey = mediaId?.let { trackKey(controller.packageName, it) }
        val cachedTrackNumber = trackKey?.let(knownTrackNumbers::get)
        val resolvedTrackNumber = when {
            activeQueueTrackNumber != null -> activeQueueTrackNumber
            queueOrderChanged || shuffleState == ShuffleState.ON -> cachedTrackNumber ?: metadataTrackNumber
            else -> metadataTrackNumber
        }
        val trackNumberReliable = activeQueueTrackNumber != null ||
            cachedTrackNumber != null && (queueOrderChanged || shuffleState == ShuffleState.ON) ||
            metadataTrackNumber != null && !queueOrderChanged && shuffleState != ShuffleState.ON
        if (trackKey != null && resolvedTrackNumber != null && trackNumberReliable) {
            knownTrackNumbers[trackKey] = resolvedTrackNumber
        }
        return PlaybackEvent(
            sourcePackageName = controller.packageName,
            sourceAppName = appNameForPackage(controller.packageName),
            title = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE).clean()
                ?: activeQueueDescription?.title?.toString().clean(),
            artist = metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST).clean()
                ?: activeQueueDescription?.subtitle?.toString().clean(),
            album = metadata?.getString(MediaMetadata.METADATA_KEY_ALBUM).clean()
                ?: activeQueueDescription?.extras?.getString(MediaMetadata.METADATA_KEY_ALBUM).clean(),
            albumArtist = metadata?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST).clean(),
            trackNumber = resolvedTrackNumber,
            totalTracks = metadata?.getLong(MediaMetadata.METADATA_KEY_NUM_TRACKS)?.safeInt(),
            discNumber = metadata?.getLong(MediaMetadata.METADATA_KEY_DISC_NUMBER)?.safeInt(),
            duration = metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION)?.takeIf { it > 0L },
            mediaId = mediaId,
            playbackState = state.toPlaybackStatus(),
            playbackPosition = state?.position?.takeIf { it >= 0L },
            queue = queue,
            observedAt = observedAt,
            queueTitle = controller.queueTitle?.toString().clean(),
            activeQueuePosition = activeQueuePosition,
            queueOrderChanged = queueOrderChanged,
            shuffleState = shuffleState,
            trackNumberReliable = trackNumberReliable,
        )
    }

    private fun detectQueueOrderChange(
        controller: MediaController,
        rawQueue: List<MediaSession.QueueItem>,
    ): Boolean {
        val order = rawQueue.map { item ->
            item.description.mediaId.clean() ?: "queue:${item.queueId}"
        }
        if (order.size < 2 || order.distinct().size != order.size) return false

        val contextKey = "${controller.packageName}|${controller.queueTitle?.toString().clean().orEmpty()}"
        val previous = queueHistories[contextKey]
        val sameItems = previous != null && previous.order.toSet() == order.toSet()
        val reordered = if (sameItems) {
            previous!!.reordered || previous.order != order
        } else {
            false
        }
        queueHistories[contextKey] = QueueHistory(order, reordered)
        while (queueHistories.size > 32) queueHistories.remove(queueHistories.keys.first())
        return reordered
    }

    private fun rememberQueueTrackNumbers(packageName: String, queue: List<QueueItemSnapshot>) {
        queue.forEach { item ->
            val mediaId = item.mediaId?.trim()?.takeIf { it.isNotEmpty() } ?: return@forEach
            val trackNumber = item.trackNumber ?: return@forEach
            knownTrackNumbers.putIfAbsent(trackKey(packageName, mediaId), trackNumber)
        }
        while (knownTrackNumbers.size > 256) knownTrackNumbers.remove(knownTrackNumbers.keys.first())
    }

    private fun trackKey(packageName: String, mediaId: String): String = "$packageName|$mediaId"

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

    private fun PlaybackState?.toShuffleState(): ShuffleState {
        val actionName = this?.customActions.orEmpty()
            .mapNotNull { it.name?.toString()?.trim()?.lowercase(java.util.Locale.ROOT) }
            .firstOrNull { it.contains("shuffle") || it.contains("\uC154\uD50C") }
            ?: return ShuffleState.UNKNOWN
        return when {
            actionName.contains("off") || actionName.contains("disable") || actionName.contains("\uC548\uD568") -> ShuffleState.ON
            actionName.contains("on") || actionName.contains("enable") || actionName.contains("\uC0AC\uC6A9") -> ShuffleState.OFF
            else -> ShuffleState.UNKNOWN
        }
    }

    private fun String?.clean(): String? = this?.trim()?.takeIf { it.isNotEmpty() }
    private fun Long.safeInt(): Int? = toInt().takeIf { it > 0 && it.toLong() == this }

    private fun android.os.Bundle.intMetadata(key: String): Int? {
        if (!containsKey(key)) return null
        val longValue = getLong(key, Long.MIN_VALUE)
        if (longValue != Long.MIN_VALUE) return longValue.safeInt()
        return getInt(key, Int.MIN_VALUE).takeIf { it > 0 }
    }

    private data class QueueHistory(
        val order: List<String>,
        val reordered: Boolean,
    )
}

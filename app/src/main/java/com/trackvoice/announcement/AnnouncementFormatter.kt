package com.trackvoice.announcement

import com.trackvoice.data.AnnouncementMode
import com.trackvoice.media.PlaybackEvent
import com.trackvoice.media.PlaybackCollection
import com.trackvoice.media.PlaybackCollectionResolver

data class AnnouncementFormatOptions(
    val readTitle: Boolean = true,
    val readArtist: Boolean = true,
    val readTrackNumber: Boolean = true,
    val readAlbum: Boolean = true,
    val readCollection: Boolean = true,
)

object AnnouncementFormatter {
    fun format(
        event: PlaybackEvent,
        mode: AnnouncementMode,
        options: AnnouncementFormatOptions = AnnouncementFormatOptions(),
        collection: PlaybackCollection = PlaybackCollectionResolver.resolve(event),
    ): String? {
        val title = event.title.cleanIf(options.readTitle)
        val artist = event.artist.cleanIf(options.readArtist)
        val album = event.album.cleanIf(options.readAlbum)
        val collectionTitle = event.queueTitle.cleanIf(options.readCollection)
        val validTrack = event.trackNumber.isValidTrack(event.totalTracks)
        val track = event.trackNumber?.takeIf { options.readTrackNumber && validTrack }

        val resolvedMode = if (mode == AnnouncementMode.SMART) {
            when (collection) {
                PlaybackCollection.ALBUM -> AnnouncementMode.ALBUM
                PlaybackCollection.PLAYLIST -> AnnouncementMode.PLAYLIST
                PlaybackCollection.ALGORITHMIC -> AnnouncementMode.TITLE_AND_ARTIST
                PlaybackCollection.UNKNOWN -> AnnouncementMode.TITLE_AND_ARTIST
            }
        } else {
            mode
        }

        return when (resolvedMode) {
            AnnouncementMode.TITLE_ONLY -> title
            AnnouncementMode.TITLE_AND_ARTIST -> joinTitleAndArtist(title, artist)
            AnnouncementMode.ALBUM -> joinParts(
                album?.let { "앨범 $it" },
                track?.let { "트랙 ${it}번" },
                title,
                artist,
            )
            AnnouncementMode.PLAYLIST -> joinParts(
                collectionTitle?.let { "재생목록 $it" },
                title,
                artist,
            )
            AnnouncementMode.SMART -> joinTitleAndArtist(title, artist)
        }?.withSentenceEnding()
    }

    private fun joinParts(vararg parts: String?): String? = parts
        .filterNot { it.isNullOrBlank() }
        .takeIf { it.isNotEmpty() }
        ?.joinToString(", ")

    private fun joinTitleAndArtist(title: String?, artist: String?): String? = listOfNotNull(title, artist)
        .takeIf { it.isNotEmpty() }
        ?.joinToString(", ")

    private fun String?.cleanIf(enabled: Boolean): String? =
        if (enabled) this?.trim()?.takeIf { it.isNotEmpty() } else null

    private fun Int?.isValidTrack(totalTracks: Int?): Boolean =
        this != null && this in 1..999 && (totalTracks == null || totalTracks >= this)

    private fun String.withSentenceEnding(): String {
        val clean = trim().trimEnd('.', '!', '?', '。')
        return "$clean."
    }
}

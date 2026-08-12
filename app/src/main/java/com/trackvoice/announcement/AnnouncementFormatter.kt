package com.trackvoice.announcement

import com.trackvoice.data.AnnouncementMode
import com.trackvoice.media.PlaybackEvent

data class AnnouncementFormatOptions(
    val readTitle: Boolean = true,
    val readArtist: Boolean = true,
    val readTrackNumber: Boolean = true,
)

object AnnouncementFormatter {
    fun format(
        event: PlaybackEvent,
        mode: AnnouncementMode,
        options: AnnouncementFormatOptions = AnnouncementFormatOptions(),
    ): String? {
        val title = event.title.cleanIf(options.readTitle)
        val artist = event.artist.cleanIf(options.readArtist)
        val validTrack = event.trackNumber.isValidTrack(event.totalTracks)
        val track = event.trackNumber?.takeIf { options.readTrackNumber && validTrack }

        return when (mode) {
            AnnouncementMode.TITLE_ONLY -> title
            AnnouncementMode.TITLE_AND_ARTIST -> joinTitleAndArtist(title, artist)
            AnnouncementMode.ALBUM -> trackTitle(track, title) ?: title ?: artist
            AnnouncementMode.SMART -> if (track != null && !event.album.isNullOrBlank()) {
                trackTitle(track, title) ?: title ?: artist
            } else {
                joinTitleAndArtist(title, artist)
            }
        }?.withSentenceEnding()
    }

    private fun trackTitle(track: Int?, title: String?): String? = when {
        track != null && title != null -> "트랙 ${track}번, $title"
        track != null -> "트랙 ${track}번"
        else -> title
    }

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

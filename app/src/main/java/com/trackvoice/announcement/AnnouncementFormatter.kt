package com.trackvoice.announcement

import com.trackvoice.data.AnnouncementMode
import com.trackvoice.media.PlaybackEvent
import com.trackvoice.media.PlaybackCollection
import com.trackvoice.media.PlaybackCollectionResolver
import com.trackvoice.media.AlbumTrackNumberResolver
import com.trackvoice.data.VoiceLanguage
import java.util.Locale

enum class AnnouncementTextLanguage {
    KOREAN,
    ENGLISH,
}

data class AnnouncementFormatOptions(
    val readTitle: Boolean = true,
    val readArtist: Boolean = true,
    val readTrackNumber: Boolean = true,
    val readAlbum: Boolean = true,
    val readCollection: Boolean = true,
)

object AnnouncementFormatter {
    fun testText(voiceLanguage: VoiceLanguage): String = when (voiceLanguage.toAnnouncementTextLanguage()) {
        AnnouncementTextLanguage.KOREAN -> "트랙 3번, Glass Eyes. Radiohead."
        AnnouncementTextLanguage.ENGLISH -> "Track 3, Glass Eyes. Radiohead."
    }

    fun format(
        event: PlaybackEvent,
        mode: AnnouncementMode,
        options: AnnouncementFormatOptions = AnnouncementFormatOptions(),
        collection: PlaybackCollection = PlaybackCollectionResolver.resolve(event),
        voiceLanguage: VoiceLanguage = VoiceLanguage.KOREAN,
    ): String? {
        val textLanguage = voiceLanguage.toAnnouncementTextLanguage()
        val title = event.title.cleanIf(options.readTitle)
        val artist = event.artist.cleanIf(options.readArtist)
        val album = event.album.cleanIf(options.readAlbum)
        val collectionTitle = event.queueTitle.cleanIf(options.readCollection)
        val track = if (options.readTrackNumber) {
            AlbumTrackNumberResolver.resolve(
                event = event,
                allowQueuePositionFallback = collection == PlaybackCollection.ALBUM || mode == AnnouncementMode.ALBUM,
            )
        } else {
            null
        }

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
                album?.let { textLanguage.albumLabel(it) },
                track?.let { textLanguage.trackLabel(it) },
                title,
                artist,
            )
            AnnouncementMode.PLAYLIST -> joinParts(
                collectionTitle?.let { textLanguage.playlistLabel(it) },
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

    private fun String.withSentenceEnding(): String {
        val clean = trim().trimEnd('.', '!', '?', '。')
        return "$clean."
    }

    private fun AnnouncementTextLanguage.albumLabel(album: String): String = when (this) {
        AnnouncementTextLanguage.KOREAN -> "앨범 $album"
        AnnouncementTextLanguage.ENGLISH -> "Album $album"
    }

    private fun AnnouncementTextLanguage.trackLabel(track: Int): String = when (this) {
        AnnouncementTextLanguage.KOREAN -> "트랙 ${track}번"
        AnnouncementTextLanguage.ENGLISH -> "Track $track"
    }

    private fun AnnouncementTextLanguage.playlistLabel(playlist: String): String = when (this) {
        AnnouncementTextLanguage.KOREAN -> "재생목록 $playlist"
        AnnouncementTextLanguage.ENGLISH -> "Playlist $playlist"
    }

    private fun VoiceLanguage.toAnnouncementTextLanguage(): AnnouncementTextLanguage = when (this) {
        VoiceLanguage.ENGLISH -> AnnouncementTextLanguage.ENGLISH
        VoiceLanguage.SYSTEM -> if (Locale.getDefault().language.equals("ko", ignoreCase = true)) {
            AnnouncementTextLanguage.KOREAN
        } else {
            AnnouncementTextLanguage.ENGLISH
        }
        // AUTO historically used Korean structural labels on Korean devices.
        // Keep that behavior while allowing an explicit English selection to
        // produce a completely English announcement sentence.
        VoiceLanguage.AUTO,
        VoiceLanguage.KOREAN,
        -> AnnouncementTextLanguage.KOREAN
    }
}

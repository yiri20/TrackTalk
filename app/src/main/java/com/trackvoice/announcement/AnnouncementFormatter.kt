package com.trackvoice.announcement

import com.trackvoice.data.AnnouncementMode
import com.trackvoice.data.AnnouncementOrder
import com.trackvoice.data.AnnouncementReadField
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
    val albumNameFirstTrackOnly: Boolean = false,
    val announcementOrder: AnnouncementOrder = AnnouncementOrder.DEFAULT,
    /**
     * Canonical ordered fields from content-specific settings. When present,
     * this list is the exact order sent to the formatter; the legacy boolean
     * flags remain for app-level and old saved settings compatibility.
     */
    val orderedFields: List<AnnouncementReadField>? = null,
)

fun AnnouncementFormatOptions.shouldReadAlbum(
    event: PlaybackEvent,
    collection: PlaybackCollection,
): Boolean = readAlbum && (
    !albumNameFirstTrackOnly ||
        collection != PlaybackCollection.ALBUM ||
        AlbumTrackNumberResolver.isFirstAlbumTrack(event)
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
        val album = event.album.cleanIf(options.shouldReadAlbum(event, collection))
        val collectionTitle = event.queueTitle.cleanIf(options.readCollection)
        val track = if (options.readTrackNumber) {
            AlbumTrackNumberResolver.resolve(
                event = event,
                // A queue position is meaningful as an album track only when
                // the content was actually identified as an album. A user may
                // choose album/track fields for a recommendation or shuffle,
                // but that queue is not album-ordered.
                allowQueuePositionFallback = collection == PlaybackCollection.ALBUM ||
                    (mode == AnnouncementMode.ALBUM && collection == PlaybackCollection.UNKNOWN),
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

        val parts = options.orderedFields?.map { field ->
            field.toAnnouncementPart() to when (field) {
                // The field name is already visible in the settings. Speaking
                // "Album <name>" makes the album value sound duplicated,
                // especially with an English voice. Read the title itself.
                AnnouncementReadField.ALBUM -> album
                AnnouncementReadField.TRACK_NUMBER -> track?.let { textLanguage.trackLabel(it) }
                AnnouncementReadField.TITLE -> title
                AnnouncementReadField.ARTIST -> artist
                AnnouncementReadField.COLLECTION -> collectionTitle?.let { textLanguage.playlistLabel(it) }
            }
        } ?: when (resolvedMode) {
            AnnouncementMode.TITLE_ONLY -> listOf(
                AnnouncementPart.TITLE to title,
            )
            AnnouncementMode.TITLE_AND_ARTIST -> listOf(
                AnnouncementPart.TITLE to title,
                AnnouncementPart.ARTIST to artist,
            )
            AnnouncementMode.ALBUM -> listOf(
                AnnouncementPart.ALBUM to album,
                AnnouncementPart.TRACK_NUMBER to track?.let { textLanguage.trackLabel(it) },
                AnnouncementPart.TITLE to title,
                AnnouncementPart.ARTIST to artist,
            )
            AnnouncementMode.PLAYLIST -> listOf(
                AnnouncementPart.COLLECTION to collectionTitle?.let { textLanguage.playlistLabel(it) },
                AnnouncementPart.ALBUM to album,
                AnnouncementPart.TRACK_NUMBER to track?.let { textLanguage.trackLabel(it) },
                AnnouncementPart.TITLE to title,
                AnnouncementPart.ARTIST to artist,
            )
            AnnouncementMode.SMART -> listOf(
                AnnouncementPart.TITLE to title,
                AnnouncementPart.ARTIST to artist,
            )
        }

        return joinParts(*orderParts(parts, options.announcementOrder).map { it.second }.toTypedArray())
            ?.withSentenceEnding()
    }

    private enum class AnnouncementPart {
        COLLECTION,
        ALBUM,
        TRACK_NUMBER,
        TITLE,
        ARTIST,
    }

    private fun AnnouncementReadField.toAnnouncementPart(): AnnouncementPart = when (this) {
        AnnouncementReadField.COLLECTION -> AnnouncementPart.COLLECTION
        AnnouncementReadField.ALBUM -> AnnouncementPart.ALBUM
        AnnouncementReadField.TRACK_NUMBER -> AnnouncementPart.TRACK_NUMBER
        AnnouncementReadField.TITLE -> AnnouncementPart.TITLE
        AnnouncementReadField.ARTIST -> AnnouncementPart.ARTIST
    }

    private fun orderParts(
        parts: List<Pair<AnnouncementPart, String?>>,
        order: AnnouncementOrder,
    ): List<Pair<AnnouncementPart, String?>> {
        val firstPart = when (order) {
            AnnouncementOrder.DEFAULT -> null
            AnnouncementOrder.TITLE_FIRST -> AnnouncementPart.TITLE
            AnnouncementOrder.ALBUM_FIRST -> AnnouncementPart.ALBUM
            AnnouncementOrder.TRACK_NUMBER_FIRST -> AnnouncementPart.TRACK_NUMBER
            AnnouncementOrder.ARTIST_FIRST -> AnnouncementPart.ARTIST
            AnnouncementOrder.COLLECTION_FIRST -> AnnouncementPart.COLLECTION
        }
        if (firstPart == null || parts.none { it.first == firstPart && !it.second.isNullOrBlank() }) {
            return parts
        }
        val first = parts.first { it.first == firstPart }
        return listOf(first) + parts.filterNot { it.first == firstPart }
    }

    private fun joinParts(vararg parts: String?): String? = parts
        .filterNot { it.isNullOrBlank() }
        .takeIf { it.isNotEmpty() }
        ?.joinToString(", ")

    private fun String?.cleanIf(enabled: Boolean): String? =
        if (enabled) this?.trim()?.takeIf { it.isNotEmpty() } else null

    private fun String.withSentenceEnding(): String {
        val clean = trim().trimEnd('.', '!', '?', '。')
        return "$clean."
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

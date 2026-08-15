package com.trackvoice.metadata

import java.text.Normalizer
import java.util.Locale
import kotlin.math.abs

/** The only public music metadata a resolver is allowed to receive. */
data class ExternalTrackMetadataQuery(
    val title: String,
    val artist: String? = null,
    val album: String? = null,
    val durationMs: Long? = null,
) {
    fun cacheKey(): String = listOf(
        TrackMetadataText.normalize(title),
        TrackMetadataText.normalize(artist),
        TrackMetadataText.normalize(album),
    ).joinToString("|")
}

data class ExternalTrackMetadataCandidate(
    val trackNumber: Int?,
    val trackCount: Int?,
    val discNumber: Int?,
    val discCount: Int?,
    val title: String?,
    val artist: String?,
    val album: String?,
    val durationMs: Long?,
)

data class ExternalTrackMetadata(
    val trackNumber: Int,
    val trackCount: Int?,
    val discNumber: Int?,
    val canonicalTitle: String?,
    val canonicalArtist: String?,
    val canonicalAlbum: String?,
    val durationMs: Long?,
    val provider: String,
    val confidence: Double,
)

enum class ExternalMetadataStatus {
    MATCHED,
    AMBIGUOUS,
    NOT_FOUND,
    RATE_LIMITED,
    FAILED,
}

data class ExternalTrackMetadataResult(
    val status: ExternalMetadataStatus,
    val metadata: ExternalTrackMetadata? = null,
    val provider: String,
    val confidence: Double = metadata?.confidence ?: 0.0,
)

interface ExternalTrackMetadataResolver {
    suspend fun resolve(
        title: String,
        artist: String?,
        album: String?,
        durationMs: Long?,
    ): ExternalTrackMetadataResult
}

/**
 * Conservative provider-independent matching. A missing track number is safe;
 * a wrong track number is not, so a result must have strong title/artist and
 * release evidence and must beat the next candidate by a clear margin.
 */
object ExternalTrackMetadataMatcher {
    const val MIN_CONFIDENCE = 0.86
    const val MIN_MARGIN = 0.08

    fun match(
        query: ExternalTrackMetadataQuery,
        candidates: List<ExternalTrackMetadataCandidate>,
        provider: String,
    ): ExternalTrackMetadataResult {
        if (query.artist.isNullOrBlank() && query.album.isNullOrBlank()) {
            return ExternalTrackMetadataResult(
                status = ExternalMetadataStatus.NOT_FOUND,
                provider = provider,
            )
        }
        val scored = candidates
            .filter { it.trackNumber?.let { number -> number in 1..999 } == true }
            .mapNotNull { candidate -> score(query, candidate)?.let { it to candidate } }
            .sortedByDescending { it.first.total }

        val best = scored.firstOrNull()
            ?: return ExternalTrackMetadataResult(
                status = ExternalMetadataStatus.NOT_FOUND,
                provider = provider,
            )
        val second = scored.getOrNull(1)
        val margin = best.first.total - (second?.first?.total ?: 0.0)
        val strongIdentity = best.first.title >= 0.90 &&
            (query.artist.isNullOrBlank() || best.first.artist >= 0.90) &&
            (query.album.isNullOrBlank() || best.first.album >= 0.78)
        if (best.first.total < MIN_CONFIDENCE || !strongIdentity || margin < MIN_MARGIN) {
            return ExternalTrackMetadataResult(
                status = ExternalMetadataStatus.AMBIGUOUS,
                provider = provider,
                confidence = best.first.total,
            )
        }

        val selected = best.second
        return ExternalTrackMetadataResult(
            status = ExternalMetadataStatus.MATCHED,
            provider = provider,
            confidence = best.first.total,
            metadata = ExternalTrackMetadata(
                trackNumber = selected.trackNumber!!,
                trackCount = selected.trackCount?.takeIf { it in 1..999 },
                discNumber = selected.discNumber?.takeIf { it in 1..99 },
                canonicalTitle = selected.title,
                canonicalArtist = selected.artist,
                canonicalAlbum = selected.album,
                durationMs = selected.durationMs,
                provider = provider,
                confidence = best.first.total,
            ),
        )
    }

    private fun score(
        query: ExternalTrackMetadataQuery,
        candidate: ExternalTrackMetadataCandidate,
    ): CandidateScore? {
        val titleScore = comparableScore(query.title, candidate.title) ?: return null
        if (titleScore < 0.90) return null
        val artistScore = if (query.artist.isNullOrBlank()) {
            0.0
        } else {
            comparableScore(query.artist, candidate.artist) ?: return null
        }
        if (!query.artist.isNullOrBlank() && artistScore < 0.90) return null
        val albumScore = if (query.album.isNullOrBlank()) {
            0.0
        } else {
            comparableScore(query.album, candidate.album) ?: return null
        }
        if (!query.album.isNullOrBlank() && albumScore < 0.78) return null
        val durationScore = durationScore(query.durationMs, candidate.durationMs)
        val weightedParts = buildList {
            add(titleScore to 0.45)
            if (!query.artist.isNullOrBlank()) add(artistScore to 0.25)
            if (!query.album.isNullOrBlank()) add(albumScore to 0.15)
            if (query.durationMs != null && candidate.durationMs != null) add(durationScore to 0.15)
        }
        val totalWeight = weightedParts.sumOf { it.second }
        return CandidateScore(
            total = weightedParts.sumOf { it.first * it.second } / totalWeight,
            title = titleScore,
            artist = artistScore,
            album = albumScore,
        )
    }

    private fun comparableScore(left: String?, right: String?): Double? {
        val leftValue = left?.let(TrackMetadataText::normalize)?.takeIf { it.isNotBlank() } ?: return null
        val rightValue = right?.let(TrackMetadataText::normalize)?.takeIf { it.isNotBlank() } ?: return null
        if (leftValue == rightValue) return 1.0
        val leftEditionNeutral = TrackMetadataText.removeEditionSuffix(leftValue)
        val rightEditionNeutral = TrackMetadataText.removeEditionSuffix(rightValue)
        return if (leftEditionNeutral == rightEditionNeutral) 0.93 else 0.0
    }

    private fun durationScore(queryMs: Long?, candidateMs: Long?): Double {
        if (queryMs == null || candidateMs == null || queryMs <= 0L || candidateMs <= 0L) return 0.0
        return when (abs(queryMs - candidateMs)) {
            in 0L..1_500L -> 1.0
            in 1_501L..5_000L -> 0.8
            in 5_001L..10_000L -> 0.4
            else -> 0.0
        }
    }

    private data class CandidateScore(
        val total: Double,
        val title: Double,
        val artist: Double,
        val album: Double,
    )
}

/** Small shared normalization utility used for matching and cache identity. */
object TrackMetadataText {
    private val punctuation = Regex("[^\\p{L}\\p{N}]+")
    private val editionTokens = setOf(
        "deluxe",
        "expanded",
        "anniversary",
        "remaster",
        "remastered",
        "single",
        "album",
        "version",
        "radio",
        "edit",
        "live",
        "edition",
    )

    fun normalize(value: String?): String = value
        ?.let { Normalizer.normalize(it, Normalizer.Form.NFKC) }
        ?.lowercase(Locale.ROOT)
        ?.replace("&", " and ")
        ?.replace(punctuation, " ")
        ?.trim()
        ?.replace(Regex("\\s+"), " ")
        .orEmpty()

    fun removeEditionSuffix(normalized: String): String {
        val tokens = normalized.split(' ').filter(String::isNotBlank).toMutableList()
        while (tokens.size > 1 && tokens.last() in editionTokens) {
            tokens.removeAt(tokens.lastIndex)
        }
        return tokens.joinToString(" ")
    }
}

data class ExternalMetadataCacheEntry(
    val status: ExternalMetadataStatus,
    val provider: String,
    val confidence: Double,
    val trackNumber: Int?,
    val trackCount: Int?,
    val discNumber: Int?,
    val canonicalTitle: String?,
    val canonicalArtist: String?,
    val canonicalAlbum: String?,
    val durationMs: Long?,
    val resolvedAt: Long,
)

fun ExternalMetadataCacheEntry.isDurationCompatible(durationMs: Long?): Boolean {
    if (durationMs == null || durationMs <= 0L || this.durationMs == null || this.durationMs <= 0L) return true
    return abs(durationMs - this.durationMs) <= 10_000L
}

object ExternalMetadataCachePolicy {
    const val MATCHED_TTL_MS = 30L * 24L * 60L * 60L * 1_000L
    const val NEGATIVE_TTL_MS = 6L * 60L * 60L * 1_000L
    const val FAILURE_TTL_MS = 5L * 60L * 1_000L

    fun isFresh(entry: ExternalMetadataCacheEntry, now: Long): Boolean =
        now - entry.resolvedAt <= ttlMs(entry.status)

    private fun ttlMs(status: ExternalMetadataStatus): Long = when (status) {
        ExternalMetadataStatus.MATCHED -> MATCHED_TTL_MS
        ExternalMetadataStatus.AMBIGUOUS,
        ExternalMetadataStatus.NOT_FOUND,
        -> NEGATIVE_TTL_MS
        ExternalMetadataStatus.RATE_LIMITED,
        ExternalMetadataStatus.FAILED,
        -> FAILURE_TTL_MS
    }
}

fun ExternalTrackMetadataResult.toCacheEntry(now: Long): ExternalMetadataCacheEntry =
    ExternalMetadataCacheEntry(
        status = status,
        provider = provider,
        confidence = confidence,
        trackNumber = metadata?.trackNumber,
        trackCount = metadata?.trackCount,
        discNumber = metadata?.discNumber,
        canonicalTitle = metadata?.canonicalTitle,
        canonicalArtist = metadata?.canonicalArtist,
        canonicalAlbum = metadata?.canonicalAlbum,
        durationMs = metadata?.durationMs,
        resolvedAt = now,
    )

fun ExternalMetadataCacheEntry.toResult(): ExternalTrackMetadataResult {
    val resolved = if (status == ExternalMetadataStatus.MATCHED && trackNumber != null) {
        ExternalTrackMetadata(
            trackNumber = trackNumber,
            trackCount = trackCount,
            discNumber = discNumber,
            canonicalTitle = canonicalTitle,
            canonicalArtist = canonicalArtist,
            canonicalAlbum = canonicalAlbum,
            durationMs = durationMs,
            provider = provider,
            confidence = confidence,
        )
    } else {
        null
    }
    return ExternalTrackMetadataResult(status, resolved, provider, confidence)
}

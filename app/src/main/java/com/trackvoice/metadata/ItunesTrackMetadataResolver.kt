package com.trackvoice.metadata

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.net.URL

/**
 * First external catalog adapter. It requests text metadata only; no artwork,
 * preview audio, account information, or device identifiers are sent or used.
 */
class ItunesTrackMetadataResolver(
    private val connectionFactory: (String) -> HttpURLConnection = ::openItunesConnection,
) : ExternalTrackMetadataResolver {
    private val rateLimitLock = Any()
    private var lastRequestAtMs: Long = 0L

    override suspend fun resolve(
        title: String,
        artist: String?,
        album: String?,
        durationMs: Long?,
    ): ExternalTrackMetadataResult = withContext(Dispatchers.IO) {
        val query = ExternalTrackMetadataQuery(title, artist, album, durationMs)
        if (!tryReserveRequest(System.currentTimeMillis())) {
            return@withContext ExternalTrackMetadataResult(
                status = ExternalMetadataStatus.RATE_LIMITED,
                provider = PROVIDER,
            )
        }
        val url = buildSearchUrl(query)
        val connection = runCatching { connectionFactory(url) }.getOrElse {
            return@withContext ExternalTrackMetadataResult(
                status = ExternalMetadataStatus.FAILED,
                provider = PROVIDER,
            )
        }
        try {
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS
            connection.requestMethod = "GET"
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("User-Agent", "TrackTalk/${BuildInfo.VERSION}")
            val responseCode = connection.responseCode
            if (responseCode == 429) {
                return@withContext ExternalTrackMetadataResult(
                    status = ExternalMetadataStatus.RATE_LIMITED,
                    provider = PROVIDER,
                )
            }
            if (responseCode !in 200..299) {
                return@withContext ExternalTrackMetadataResult(
                    status = ExternalMetadataStatus.FAILED,
                    provider = PROVIDER,
                )
            }
            val body = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            val candidates = parseCandidates(body)
            ExternalTrackMetadataMatcher.match(query, candidates, PROVIDER)
        } catch (_: IOException) {
            ExternalTrackMetadataResult(
                status = ExternalMetadataStatus.FAILED,
                provider = PROVIDER,
            )
        } catch (_: RuntimeException) {
            // A malformed response must never affect playback or TTS.
            ExternalTrackMetadataResult(
                status = ExternalMetadataStatus.FAILED,
                provider = PROVIDER,
            )
        } finally {
            connection.disconnect()
        }
    }

    internal fun parseCandidates(json: String): List<ExternalTrackMetadataCandidate> {
        val results = runCatching { JSONObject(json).optJSONArray("results") }.getOrNull() ?: return emptyList()
        return buildList {
            for (index in 0 until results.length()) {
                val item = results.optJSONObject(index) ?: continue
                if (item.optString("wrapperType") != "track") continue
                if (item.optString("kind").isNotBlank() && item.optString("kind") != "song") continue
                val title = item.optStringOrNull("trackName") ?: continue
                add(
                    ExternalTrackMetadataCandidate(
                        trackNumber = item.optIntOrNull("trackNumber"),
                        trackCount = item.optIntOrNull("trackCount"),
                        discNumber = item.optIntOrNull("discNumber"),
                        discCount = item.optIntOrNull("discCount"),
                        title = title,
                        artist = item.optStringOrNull("artistName"),
                        album = item.optStringOrNull("collectionName"),
                        durationMs = item.optLongOrNull("trackTimeMillis"),
                    ),
                )
            }
        }
    }

    internal companion object {
        const val PROVIDER = "ITUNES_SEARCH"
        const val CONNECT_TIMEOUT_MS = 700
        const val READ_TIMEOUT_MS = 900
        // Apple's Search API documentation describes an approximate limit of
        // 20 requests per minute. Keep this adapter conservative so a long
        // listening session does not turn every transition into a request.
        const val MIN_REQUEST_INTERVAL_MS = 3_000L

        fun buildSearchUrl(query: ExternalTrackMetadataQuery): String {
            val terms = listOfNotNull(query.title, query.artist, query.album)
                .filter { it.isNotBlank() }
                .joinToString(" ")
            return "https://itunes.apple.com/search" +
                "?country=us&media=music&entity=song&limit=20&term=" +
                URLEncoder.encode(terms, Charsets.UTF_8.name())
        }

        private object BuildInfo {
            const val VERSION = "0.1"
        }
    }

    private fun tryReserveRequest(now: Long): Boolean = synchronized(rateLimitLock) {
        if (now - lastRequestAtMs < MIN_REQUEST_INTERVAL_MS) return false
        lastRequestAtMs = now
        true
    }
}

private fun JSONObject.optStringOrNull(key: String): String? =
    optString(key).trim().takeIf { it.isNotEmpty() }

private fun JSONObject.optIntOrNull(key: String): Int? =
    optInt(key, 0).takeIf { it > 0 }

private fun JSONObject.optLongOrNull(key: String): Long? =
    optLong(key, 0L).takeIf { it > 0L }

private fun openItunesConnection(url: String): HttpURLConnection =
    URL(url).openConnection() as HttpURLConnection

package com.trackvoice.media

object ActiveSessionSelector {
    fun select(sessions: List<SessionSnapshot>): SessionSnapshot? {
        if (sessions.isEmpty()) return null

        val playing = sessions
            .filter { it.event.isPlaying && it.event.hasTitle }
        val mediaKeyPlaying = playing
            .filter { it.isMediaKeySession }
            .maxByOrNull { maxOf(it.lastObservedAt, it.lastMetadataChangedAt) }
        if (mediaKeyPlaying != null) return mediaKeyPlaying

        val recentPlaying = playing
            .maxByOrNull { maxOf(it.lastObservedAt, it.lastMetadataChangedAt) }
        if (recentPlaying != null) return recentPlaying

        val mediaKey = sessions
            .filter { it.isMediaKeySession && it.event.hasTitle }
            .maxByOrNull { it.lastObservedAt }
        if (mediaKey != null) return mediaKey

        return sessions
            .filter { it.event.hasTitle }
            .maxByOrNull { it.lastMetadataChangedAt }
    }
}

package com.trackvoice.announcement

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class MixedLanguageSegmenterTest {
    @Test
    fun splitsKoreanAndEnglishWhileKeepingNumbersAndPunctuation() {
        val segments = MixedLanguageSegmenter.segment(
            "트랙 3번, Glass Eyes · Radiohead.",
            Locale.KOREAN,
        )

        assertEquals(2, segments.size)
        assertEquals("트랙 3번, ", segments[0].text)
        assertEquals("ko", segments[0].locale.language)
        assertEquals("Glass Eyes · Radiohead.", segments[1].text)
        assertEquals("en", segments[1].locale.language)
    }

    @Test
    fun groupsJapaneseKanaAndKanjiIntoOneSegment() {
        val segments = MixedLanguageSegmenter.segment("新しいSongを再生", Locale.ENGLISH)

        assertEquals(listOf("ja", "en", "ja"), segments.map { it.locale.language })
        assertEquals(listOf("新しい", "Song", "を再生"), segments.map { it.text })
    }

    @Test
    fun attachesLeadingNumberToFollowingLanguage() {
        val segments = MixedLanguageSegmenter.segment("2026 Love Song", Locale.KOREAN)

        assertEquals(1, segments.size)
        assertEquals("2026 Love Song", segments.single().text)
        assertEquals("en", segments.single().locale.language)
    }
}

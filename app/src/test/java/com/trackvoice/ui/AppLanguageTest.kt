package com.trackvoice.ui

import com.trackvoice.data.AppLanguage
import com.trackvoice.data.AnnouncementTiming
import com.trackvoice.data.AnnouncementMode
import com.trackvoice.data.resolve
import com.trackvoice.monetization.PremiumMessage
import org.junit.Assert.assertEquals
import org.junit.Test

class AppLanguageTest {
    @Test
    fun systemKoreanResolvesToKorean() {
        assertEquals(AppLanguage.KOREAN, AppLanguage.SYSTEM.resolve("ko"))
    }

    @Test
    fun systemNonKoreanResolvesToEnglish() {
        assertEquals(AppLanguage.ENGLISH, AppLanguage.SYSTEM.resolve("en-US"))
    }

    @Test
    fun explicitLanguageIgnoresSystemLanguage() {
        assertEquals(AppLanguage.KOREAN, AppLanguage.KOREAN.resolve("en"))
        assertEquals(AppLanguage.ENGLISH, AppLanguage.ENGLISH.resolve("ko"))
    }

    @Test
    fun stringsFollowSelectedLanguage() {
        assertEquals("홈", TrackTalkStrings.forLanguage(AppLanguage.KOREAN, "en").navLabel(AppSection.HOME))
        assertEquals("Home", TrackTalkStrings.forLanguage(AppLanguage.ENGLISH, "ko").navLabel(AppSection.HOME))
        assertEquals("안내·음성", TrackTalkStrings.forLanguage(AppLanguage.KOREAN, "en").navLabel(AppSection.GENERAL))
        assertEquals("기기", TrackTalkStrings.forLanguage(AppLanguage.KOREAN, "en").navLabel(AppSection.DEVICES))
        assertEquals("Guide & voice", TrackTalkStrings.forLanguage(AppLanguage.ENGLISH, "ko").navLabel(AppSection.GENERAL))
        assertEquals("Devices", TrackTalkStrings.forLanguage(AppLanguage.ENGLISH, "ko").navLabel(AppSection.DEVICES))
    }

    @Test
    fun premiumMessagesFollowSelectedLanguage() {
        assertEquals(
            "Plus 구매 기능을 준비 중입니다. 잠시 후 다시 시도해 주세요.",
            TrackTalkStrings.forLanguage(AppLanguage.KOREAN, "en")
                .premiumMessage(PremiumMessage.PRODUCT_UNAVAILABLE),
        )
        assertEquals(
            "Plus purchases aren't ready yet. Please try again later.",
            TrackTalkStrings.forLanguage(AppLanguage.ENGLISH, "ko")
                .premiumMessage(PremiumMessage.PRODUCT_UNAVAILABLE),
        )
    }

    @Test
    fun announcementTimingExplainsTheActualWait() {
        val korean = TrackTalkStrings.forLanguage(AppLanguage.KOREAN, "en")
        val english = TrackTalkStrings.forLanguage(AppLanguage.ENGLISH, "ko")

        assertEquals(
            "새 곡을 감지한 뒤 2초 후 읽습니다.",
            korean.announcementTimingSummary(AnnouncementTiming.DELAYED, 2),
        )
        assertEquals(
            "It reads immediately at 0s. Set 1–2s to wait before reading.",
            english.announcementTimingSummary(AnnouncementTiming.DELAYED, 0),
        )
        assertEquals(
            "자동 · 바로 안내",
            korean.guideSummary(com.trackvoice.data.AnnouncementMode.SMART, AnnouncementTiming.IMMEDIATE, 2),
        )
    }

    @Test
    fun nowPlayingCardUsesASeparateGuideBasisLabel() {
        val korean = TrackTalkStrings.forLanguage(AppLanguage.KOREAN, "en")
        val english = TrackTalkStrings.forLanguage(AppLanguage.ENGLISH, "ko")

        assertEquals(
            "안내 기준: 앨범",
            korean.readingFormatLabel(AnnouncementMode.ALBUM),
        )
        assertEquals(
            "Guide basis: Playlist",
            english.readingFormatLabel(AnnouncementMode.PLAYLIST),
        )
        assertEquals("기본 설정", korean.announcementBasisValue(appSpecific = false, typeSpecific = false))
        assertEquals("유형별 설정", korean.announcementBasisValue(appSpecific = false, typeSpecific = true))
        assertEquals("앱별 설정", korean.announcementBasisValue(appSpecific = true, typeSpecific = true))
    }
}

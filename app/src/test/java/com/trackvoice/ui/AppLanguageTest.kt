package com.trackvoice.ui

import com.trackvoice.data.AppLanguage
import com.trackvoice.data.AnnouncementOutputPolicy
import com.trackvoice.data.AnnouncementTiming
import com.trackvoice.data.AnnouncementReadField
import com.trackvoice.data.resolve
import com.trackvoice.announcement.AnnouncementConfigurationSource
import com.trackvoice.announcement.EffectiveAnnouncementConfiguration
import com.trackvoice.media.PlaybackCollection
import com.trackvoice.monetization.PremiumMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
            "현재 설치 환경에서는 구매 정보를 확인할 수 없습니다. Google Play에서 설치한 앱에서 다시 확인해 주세요.",
            TrackTalkStrings.forLanguage(AppLanguage.KOREAN, "en")
                .premiumMessage(PremiumMessage.PRODUCT_UNAVAILABLE),
        )
        assertEquals(
            "Purchase details aren't available in this installation. Check the Google Play version of the app.",
            TrackTalkStrings.forLanguage(AppLanguage.ENGLISH, "ko")
                .premiumMessage(PremiumMessage.PRODUCT_UNAVAILABLE),
        )
    }

    @Test
    fun freeTierCopyDescribesBehaviorWithoutTierLabels() {
        val korean = TrackTalkStrings.forLanguage(AppLanguage.KOREAN, "en")
        val english = TrackTalkStrings.forLanguage(AppLanguage.ENGLISH, "ko")

        assertEquals("안내 시점과 음량을 세밀하게 조절합니다.", korean.freeGuideDetailsSummary)
        assertEquals("Fine-tune announcement timing and volume.", english.freeGuideDetailsSummary)
        assertFalse(korean.albumPlaylistSummary.contains("무료"))
        assertFalse(english.albumPlaylistSummary.contains("Free", ignoreCase = true))
        assertEquals("기기·진단", korean.sectionTitle(AppSection.DEVICES))
        assertEquals("Devices & diagnostics", english.sectionTitle(AppSection.DEVICES))
        assertEquals("PLUS", korean.plusBadge)
        assertEquals("자동화", korean.automationPlusTitle)
        assertEquals("Automation", english.automationPlusTitle)
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
    fun nowPlayingCardSummarizesTheEffectiveSourceAndFieldOrder() {
        val korean = TrackTalkStrings.forLanguage(AppLanguage.KOREAN, "en")
        val english = TrackTalkStrings.forLanguage(AppLanguage.ENGLISH, "ko")
        val defaultConfiguration = EffectiveAnnouncementConfiguration(
            source = AnnouncementConfigurationSource.DEFAULT,
            collection = PlaybackCollection.UNKNOWN,
            fields = listOf(AnnouncementReadField.TITLE, AnnouncementReadField.ARTIST),
            typeSpecificSettingsEnabled = true,
        )
        val albumConfiguration = EffectiveAnnouncementConfiguration(
            source = AnnouncementConfigurationSource.CONTENT_SPECIFIC,
            collection = PlaybackCollection.ALBUM,
            fields = listOf(AnnouncementReadField.TRACK_NUMBER, AnnouncementReadField.TITLE),
            typeSpecificSettingsEnabled = true,
        )

        assertEquals("기본 설정", korean.homeAnnouncementBasis(defaultConfiguration))
        assertEquals("Album", english.homeAnnouncementBasis(albumConfiguration))
        assertEquals("트랙 번호 · 곡명", korean.announcementFieldsSummary(albumConfiguration.fields))
        assertEquals("Track number · Track title", english.announcementFieldsSummary(albumConfiguration.fields))
    }

    @Test
    fun outputPolicyLabelsMatchSelectedLanguage() {
        val korean = TrackTalkStrings.forLanguage(AppLanguage.KOREAN, "en")
        val english = TrackTalkStrings.forLanguage(AppLanguage.ENGLISH, "ko")

        assertEquals("모든 오디오 출력", korean.announcementOutputOption(AnnouncementOutputPolicy.ALL_OUTPUTS))
        assertEquals("External audio only", english.announcementOutputOption(AnnouncementOutputPolicy.EXTERNAL_ONLY))
    }
}

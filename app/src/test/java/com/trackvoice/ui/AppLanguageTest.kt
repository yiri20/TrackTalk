package com.trackvoice.ui

import com.trackvoice.data.AppLanguage
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
}

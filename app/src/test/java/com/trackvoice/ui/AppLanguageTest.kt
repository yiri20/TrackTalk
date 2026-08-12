package com.trackvoice.ui

import com.trackvoice.data.AppLanguage
import com.trackvoice.data.resolve
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
}

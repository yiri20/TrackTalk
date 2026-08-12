package com.trackvoice.announcement

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class TtsLocaleResolverTest {
    @Test
    fun unsupportedLanguageFallsBackToSystemLocale() {
        val (locale, fallback) = TtsLocaleResolver.choose(
            requested = Locale.KOREAN,
            supported = setOf(Locale.ENGLISH),
            systemDefault = Locale.US,
        )
        assertEquals(Locale.US, locale)
        assertTrue(fallback)
    }
}

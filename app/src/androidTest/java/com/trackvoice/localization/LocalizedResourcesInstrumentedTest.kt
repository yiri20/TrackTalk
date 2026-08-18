package com.trackvoice.localization

import android.content.Context
import android.content.res.Configuration
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.trackvoice.R
import com.trackvoice.data.AppLanguage
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LocalizedResourcesInstrumentedTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun systemKoreanUsesKoreanResources() {
        val koreanContext = context.withLocale(Locale.KOREAN)
        assertEquals(
            "음악 재생",
            koreanContext.localizedString(AppLanguage.SYSTEM, R.string.playback_tile_play_label),
        )
    }

    @Test
    fun systemEnglishAndUnsupportedLocalesUseEnglishResources() {
        val englishContext = context.withLocale(Locale.US)
        val japaneseContext = context.withLocale(Locale.JAPANESE)

        assertEquals(
            "Play music",
            englishContext.localizedString(AppLanguage.SYSTEM, R.string.playback_tile_play_label),
        )
        assertEquals(
            "Play music",
            japaneseContext.localizedString(AppLanguage.SYSTEM, R.string.playback_tile_play_label),
        )
    }

    @Test
    fun explicitOverridesIgnoreTheSystemResourceLocale() {
        val koreanContext = context.withLocale(Locale.KOREAN)
        val englishContext = context.withLocale(Locale.US)

        assertEquals(
            "Play music",
            koreanContext.localizedString(AppLanguage.ENGLISH, R.string.playback_tile_play_label),
        )
        assertEquals(
            "음악 재생",
            englishContext.localizedString(AppLanguage.KOREAN, R.string.playback_tile_play_label),
        )
    }

    private fun Context.withLocale(locale: Locale): Context = createConfigurationContext(
        Configuration(resources.configuration).apply { setLocale(locale) },
    )
}

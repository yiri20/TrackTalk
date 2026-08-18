package com.trackvoice.localization

import android.content.Context
import android.content.res.Configuration
import androidx.annotation.StringRes
import com.trackvoice.data.AppLanguage
import java.util.Locale

/**
 * Resolves non-Compose copy without changing the process locale. This keeps
 * the interface language independent from TrackTalk's speech language.
 */
fun Context.localizedString(
    appLanguage: AppLanguage,
    @StringRes stringId: Int,
    vararg formatArgs: Any,
): String {
    val localizedContext = when (appLanguage) {
        AppLanguage.SYSTEM -> when (
            resources.configuration.locales.get(0).language.lowercase(Locale.ROOT)
        ) {
            "ko" -> withLocale(Locale.KOREAN)
            else -> withLocale(Locale.ENGLISH)
        }
        AppLanguage.KOREAN -> withLocale(Locale.KOREAN)
        AppLanguage.ENGLISH -> withLocale(Locale.ENGLISH)
    }
    return if (formatArgs.isEmpty()) {
        localizedContext.getString(stringId)
    } else {
        localizedContext.getString(stringId, *formatArgs)
    }
}

private fun Context.withLocale(locale: Locale): Context {
    val configuration = Configuration(resources.configuration).apply {
        setLocale(locale)
    }
    return createConfigurationContext(configuration)
}

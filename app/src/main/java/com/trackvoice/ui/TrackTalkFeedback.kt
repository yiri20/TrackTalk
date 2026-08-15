package com.trackvoice.ui

import android.content.Intent
import android.os.Build
import androidx.core.net.toUri
import com.trackvoice.BuildConfig

/** Builds the intentionally minimal feedback email without including user activity data. */
object TrackTalkFeedback {
    private const val FEEDBACK_ADDRESS = "thegreatinside.web@gmail.com"
    private const val SUBJECT = "TrackTalk Feedback"

    fun createIntent(): Intent = Intent(
        Intent.ACTION_SENDTO,
        "mailto:$FEEDBACK_ADDRESS".toUri(),
    ).apply {
        putExtra(Intent.EXTRA_SUBJECT, SUBJECT)
        putExtra(Intent.EXTRA_TEXT, diagnosticBody())
    }

    private fun diagnosticBody(): String = buildString {
        appendLine("TrackTalk version: ${BuildConfig.VERSION_NAME}")
        appendLine("Build: ${BuildConfig.VERSION_CODE}")
        appendLine("Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
        append("Device: ${Build.MODEL}")
    }
}

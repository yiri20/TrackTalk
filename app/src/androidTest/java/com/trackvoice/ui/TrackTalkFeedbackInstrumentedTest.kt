package com.trackvoice.ui

import android.content.Intent
import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.trackvoice.BuildConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TrackTalkFeedbackInstrumentedTest {
    @Test
    fun createsMailtoIntentWithOnlySafeDiagnosticBasics() {
        val intent = TrackTalkFeedback.createIntent()
        val body = intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString().orEmpty()

        assertEquals(Intent.ACTION_SENDTO, intent.action)
        assertEquals("mailto:thegreatinside.web@gmail.com", intent.dataString)
        assertEquals("TrackTalk Feedback", intent.getStringExtra(Intent.EXTRA_SUBJECT))
        assertTrue(body.contains("TrackTalk version: ${BuildConfig.VERSION_NAME}"))
        assertTrue(body.contains("Build: ${BuildConfig.VERSION_CODE}"))
        assertTrue(body.contains("Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"))
        assertTrue(body.contains("Device: ${Build.MODEL}"))
        assertFalse(body.contains("playback", ignoreCase = true))
        assertFalse(body.contains("settings", ignoreCase = true))
        assertFalse(body.contains("logs", ignoreCase = true))
        assertFalse(body.contains("mediaId", ignoreCase = true))
    }
}

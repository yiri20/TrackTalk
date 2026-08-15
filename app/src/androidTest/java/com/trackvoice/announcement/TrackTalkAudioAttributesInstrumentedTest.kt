package com.trackvoice.announcement

import android.media.AudioAttributes
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TrackTalkAudioAttributesInstrumentedTest {
    @Test
    fun speechUsesAssistantSemanticsNotAccessibilitySemantics() {
        val attributes = TrackTalkAudioAttributes.speech()

        assertEquals(AudioAttributes.USAGE_ASSISTANT, attributes.usage)
        assertEquals(AudioAttributes.CONTENT_TYPE_SPEECH, attributes.contentType)
        assertNotEquals(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY, attributes.usage)
    }
}

package com.trackvoice.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.trackvoice.announcement.TtsState
import com.trackvoice.data.AppLanguage
import com.trackvoice.data.UserSettings
import com.trackvoice.test.TrackTalkComposeTestActivity
import com.trackvoice.ui.theme.TrackVoiceTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GuideSettingsScreenInstrumentedTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<TrackTalkComposeTestActivity>()

    @Test
    fun guideAndVoiceSegmentsHaveEqualWidthAndStayCentered() {
        composeRule.setContent {
            TrackVoiceTheme {
                CompositionLocalProvider(
                    LocalTrackTalkStrings provides TrackTalkStrings.forLanguage(
                        AppLanguage.KOREAN,
                        "en",
                    ),
                ) {
                    GuideSettingsScreen(
                        settings = UserSettings(appLanguage = AppLanguage.KOREAN),
                        voices = emptyList(),
                        ttsStatus = TtsState(),
                        isPremium = false,
                        onUpdate = {},
                        onTest = {},
                        onPreviewVoice = {},
                        onOpenPremium = {},
                        selectedPaneName = GuideSettingsPane.GUIDE.name,
                        onPaneSelected = {},
                    )
                }
            }
        }

        val guide = composeRule.onNodeWithText("안내").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val voice = composeRule.onNodeWithText("음성").assertIsDisplayed().fetchSemanticsNode().boundsInRoot

        assertEquals(guide.width, voice.width, 1f)
        assertTrue(guide.center.x < voice.center.x)
    }
}

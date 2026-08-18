package com.trackvoice.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.trackvoice.data.AppLanguage
import com.trackvoice.data.UserSettings
import com.trackvoice.test.TrackTalkComposeTestActivity
import com.trackvoice.ui.theme.TrackVoiceTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DeviceSettingsScreenInstrumentedTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<TrackTalkComposeTestActivity>()

    @Test
    fun appLanguageLivesInCompactGeneralSectionAndUpdatesTheInterface() {
        var settings by mutableStateOf(UserSettings(appLanguage = AppLanguage.KOREAN))

        composeRule.setContent {
            TrackVoiceTheme {
                CompositionLocalProvider(
                    LocalTrackTalkStrings provides TrackTalkStrings.forLanguage(settings.appLanguage, "ko"),
                ) {
                    DeviceSettingsScreen(
                        settings = settings,
                        connectedDevices = emptyList(),
                        deviceSettings = emptyMap(),
                        isPremium = false,
                        onUpdate = { transform -> settings = transform(settings) },
                        onUpdateDevice = {},
                        onOpenPremium = {},
                        onOpenDiagnostics = {},
                        onFeedback = {},
                    )
                }
            }
        }

        composeRule.onNodeWithText("일반").assertIsDisplayed()
        composeRule.onNodeWithText("앱 언어").assertIsDisplayed()
        composeRule.onNodeWithText("한국어").assertIsDisplayed()
        composeRule.onNodeWithTag(APP_LANGUAGE_SETTING_TAG).performClick()
        composeRule.onNodeWithText("시스템 언어").assertIsDisplayed()
        composeRule.onNodeWithText("English").performClick()

        composeRule.onNodeWithText("General").assertIsDisplayed()
        composeRule.onNodeWithText("App language").assertIsDisplayed()
        composeRule.onNodeWithText("English").assertIsDisplayed()
    }
}

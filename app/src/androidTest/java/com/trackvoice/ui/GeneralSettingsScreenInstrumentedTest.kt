package com.trackvoice.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.trackvoice.data.AnnouncementOutputPolicy
import com.trackvoice.data.AnnouncementTiming
import com.trackvoice.data.AppLanguage
import com.trackvoice.data.MusicTreatment
import com.trackvoice.data.TrackStartBehavior
import com.trackvoice.data.UserSettings
import com.trackvoice.ui.theme.TrackVoiceTheme
import com.trackvoice.test.TrackTalkComposeTestActivity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GeneralSettingsScreenInstrumentedTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<TrackTalkComposeTestActivity>()

    @Test
    fun selectingDelayedTimingShowsAndPersistsAnnouncementDelay() {
        var settings by mutableStateOf(
            UserSettings(
                appLanguage = AppLanguage.KOREAN,
                outputPolicy = AnnouncementOutputPolicy.ALL_OUTPUTS,
                timing = AnnouncementTiming.IMMEDIATE,
                trackStartBehavior = TrackStartBehavior.ANNOUNCE_THEN_PLAY,
                musicTreatment = MusicTreatment.PAUSE,
                useContentTypeSettings = false,
            ),
        )

        fun setScreen() {
            composeRule.setContent {
                TrackVoiceTheme {
                    CompositionLocalProvider(
                        LocalTrackTalkStrings provides TrackTalkStrings.forLanguage(
                            AppLanguage.KOREAN,
                            "en",
                        ),
                    ) {
                        GeneralSettingsScreen(
                            settings = settings,
                            isPremium = true,
                            onUpdate = { transform -> settings = transform(settings) },
                            onOpenPremium = {},
                            target = null,
                            onTargetHandled = {},
                        )
                    }
                }
            }
        }

        setScreen()
        composeRule.onNodeWithText("재생 시작").performScrollTo()
        composeRule.onNodeWithText("바로 읽기").performClick()
        composeRule.onNodeWithText("몇 초 후 읽기").performClick()

        composeRule.onNodeWithText("읽기 전 대기").performScrollTo().assertIsDisplayed()
        val sliderMatcher = SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo)
        composeRule.onAllNodes(sliderMatcher).assertCountEquals(1)
        composeRule.onAllNodes(sliderMatcher)
            .onFirst()
            .performSemanticsAction(SemanticsActions.SetProgress) { setProgress ->
                setProgress(2f)
            }
        composeRule.onNodeWithText("2초").assertIsDisplayed()

        composeRule.runOnIdle {
            check(settings.delaySeconds == 2)
        }
        composeRule.onNodeWithText("읽기 전 대기").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("2초").assertIsDisplayed()
    }

    @Test
    fun immediateTimingHidesAnnouncementDelayControl() {
        composeRule.setContent {
            TrackVoiceTheme {
                CompositionLocalProvider(
                    LocalTrackTalkStrings provides TrackTalkStrings.forLanguage(
                        AppLanguage.KOREAN,
                        "en",
                    ),
                ) {
                    GeneralSettingsScreen(
                        settings = UserSettings(
                            appLanguage = AppLanguage.KOREAN,
                            outputPolicy = AnnouncementOutputPolicy.ALL_OUTPUTS,
                            timing = AnnouncementTiming.IMMEDIATE,
                            trackStartBehavior = TrackStartBehavior.ANNOUNCE_THEN_PLAY,
                            musicTreatment = MusicTreatment.PAUSE,
                            useContentTypeSettings = false,
                        ),
                        isPremium = true,
                        onUpdate = {},
                        onOpenPremium = {},
                        target = null,
                        onTargetHandled = {},
                    )
                }
            }
        }

        composeRule.onNodeWithText("재생 시작").performScrollTo()
        composeRule.onNodeWithText("읽기 전 대기").assertDoesNotExist()
    }
}

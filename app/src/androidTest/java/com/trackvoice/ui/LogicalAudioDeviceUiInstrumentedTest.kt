package com.trackvoice.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.trackvoice.announcement.AudioDeviceKind
import com.trackvoice.announcement.AudioDeviceProfile
import com.trackvoice.announcement.DiscoveredAudioDevice
import com.trackvoice.announcement.LogicalAudioDeviceNormalizer
import com.trackvoice.data.AppLanguage
import com.trackvoice.data.UserSettings
import com.trackvoice.test.TrackTalkComposeTestActivity
import com.trackvoice.ui.theme.TrackVoiceTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LogicalAudioDeviceUiInstrumentedTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<TrackTalkComposeTestActivity>()

    @Test
    fun normalizedBluetoothProfilesRenderAsOneAutomationDevice() {
        val address = "AA:BB:CC:DD:EE:99"
        val connectedDevices = LogicalAudioDeviceNormalizer.normalize(
            listOf(
                DiscoveredAudioDevice(
                    systemId = 1,
                    profile = AudioDeviceProfile.BLUETOOTH_A2DP,
                    productName = "Space One Pro",
                    stableAddress = address,
                    legacyKey = "8:$address",
                    kind = AudioDeviceKind.BLUETOOTH,
                ),
                DiscoveredAudioDevice(
                    systemId = 2,
                    profile = AudioDeviceProfile.BLUETOOTH_SCO,
                    productName = "Space One Pro",
                    stableAddress = address,
                    legacyKey = "7:$address",
                    kind = AudioDeviceKind.BLUETOOTH,
                ),
            ),
        )

        composeRule.setContent {
            TrackVoiceTheme {
                CompositionLocalProvider(
                    LocalTrackTalkStrings provides TrackTalkStrings.forLanguage(AppLanguage.ENGLISH, "en"),
                ) {
                    DeviceSettingsScreen(
                        settings = UserSettings(appLanguage = AppLanguage.ENGLISH),
                        connectedDevices = connectedDevices,
                        deviceSettings = emptyMap(),
                        isPremium = true,
                        onUpdate = {},
                        onUpdateDevice = {},
                        onOpenPremium = {},
                        onOpenDiagnostics = {},
                        onFeedback = {},
                    )
                }
            }
        }

        composeRule.onAllNodesWithText("Space One Pro").assertCountEquals(1)
    }
}

package com.trackvoice.ui

import com.trackvoice.data.AppLanguage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationPermissionBannerTest {
    @Test
    fun bannerIsShownOnlyWhenRuntimePermissionIsNeededAndMissing() {
        assertTrue(
            shouldShowNotificationPermissionBanner(
                requiresRuntimePermission = true,
                permissionGranted = false,
                statusNotificationEnabled = true,
            ),
        )
        assertFalse(
            shouldShowNotificationPermissionBanner(
                requiresRuntimePermission = true,
                permissionGranted = true,
                statusNotificationEnabled = true,
            ),
        )
        assertFalse(
            shouldShowNotificationPermissionBanner(
                requiresRuntimePermission = false,
                permissionGranted = false,
                statusNotificationEnabled = true,
            ),
        )
        assertFalse(
            shouldShowNotificationPermissionBanner(
                requiresRuntimePermission = true,
                permissionGranted = false,
                statusNotificationEnabled = false,
            ),
        )
    }

    @Test
    fun bannerCopyIsConciseAndLocalized() {
        val korean = TrackTalkStrings.forLanguage(AppLanguage.KOREAN, "en")
        assertEquals("알림 권한 필요", korean.notificationPermissionTitle)
        assertEquals("상단바 바로가기 사용에 필요합니다. 음악 감지는 계속됩니다.", korean.notificationPermissionSummary)
        assertEquals("허용", korean.allowNotifications)

        val english = TrackTalkStrings.forLanguage(AppLanguage.ENGLISH, "ko")
        assertEquals("Notifications needed", english.notificationPermissionTitle)
        assertEquals("Needed for the quick status-bar shortcut. Music detection still works.", english.notificationPermissionSummary)
        assertEquals("Allow", english.allowNotifications)
    }
}

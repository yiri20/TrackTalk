package com.trackvoice.ui

import com.trackvoice.data.AnnouncementMode
import com.trackvoice.data.AnnouncementOrder
import com.trackvoice.data.AnnouncementOutputPolicy
import com.trackvoice.data.AnnouncementReadField
import com.trackvoice.data.AnnouncementTiming
import com.trackvoice.data.AnnouncementTimingPolicy
import com.trackvoice.data.AppCategory
import com.trackvoice.data.AppLanguage
import com.trackvoice.data.CollectionFallback
import com.trackvoice.data.GenderFilter
import com.trackvoice.data.MusicTreatment
import com.trackvoice.data.TrackStartBehavior
import com.trackvoice.data.VoiceLanguage
import com.trackvoice.data.DEFAULT_TTS_VOLUME_PERCENT
import com.trackvoice.data.resolve
import com.trackvoice.announcement.AnnouncementConfigurationSource
import com.trackvoice.announcement.EffectiveAnnouncementConfiguration
import com.trackvoice.media.PlaybackCollection
import com.trackvoice.media.PlaybackStatus
import com.trackvoice.monetization.PremiumMessage
import androidx.compose.runtime.staticCompositionLocalOf

enum class ContentReadPreset {
    DEFAULT,
    TITLE_AND_ARTIST,
    TITLE_ONLY,
    CUSTOM,
}

/** Small, local UI dictionary used by the Compose surface. */
class TrackTalkStrings private constructor(private val language: AppLanguage) {
    private val english = language == AppLanguage.ENGLISH

    private fun t(korean: String, englishText: String): String = if (english) englishText else korean

    fun text(korean: String, englishText: String): String = t(korean, englishText)

    fun sectionTitle(section: AppSection): String = when (section) {
        AppSection.HOME -> "TrackTalk"
        AppSection.GENERAL -> t("안내·음성 설정", "Guide & voice settings")
        AppSection.APPS -> t("앱 설정", "App settings")
        AppSection.DEVICES -> t("기기·진단", "Devices & diagnostics")
        AppSection.DIAGNOSTICS -> t("진단", "Diagnostics")
    }

    fun navLabel(section: AppSection): String = when (section) {
        AppSection.HOME -> t("홈", "Home")
        AppSection.GENERAL -> t("안내·음성", "Guide & voice")
        AppSection.APPS -> t("앱", "Apps")
        AppSection.DEVICES -> t("기기", "Devices")
        AppSection.DIAGNOSTICS -> t("진단", "Diagnostics")
    }

    val plus: String get() = "Plus"
    val appLanguageTitle: String get() = t("앱 언어", "App language")
    val appLanguageLabel: String get() = t("표시 언어", "Display language")
    val appLanguageDescription: String
        get() = t("시스템 언어를 따르거나 한국어·영어를 직접 선택합니다.", "Follow the system language or choose Korean or English.")

    fun appLanguageOption(language: AppLanguage): String = when (language) {
        AppLanguage.SYSTEM -> t("시스템 언어", "System language")
        AppLanguage.KOREAN -> "한국어"
        AppLanguage.ENGLISH -> "English"
    }

    fun guideSummary(mode: AnnouncementMode, timing: AnnouncementTiming, delaySeconds: Int): String {
        return guideSummary(announcementMode(mode), timing, delaySeconds)
    }
    fun guideSummary(basis: String, timing: AnnouncementTiming, delaySeconds: Int): String {
        return "$basis · ${guideTimingSummary(timing, delaySeconds)}"
    }
    fun guideTimingSummary(timing: AnnouncementTiming, delaySeconds: Int): String {
        val timingText = when (timing) {
            AnnouncementTiming.IMMEDIATE -> t("바로 안내", "announce now")
            AnnouncementTiming.DELAYED,
            AnnouncementTiming.BETWEEN_TRACKS,
            -> {
                val effectiveDelay = AnnouncementTimingPolicy.normalizeStoredDelaySeconds(timing, delaySeconds)
                t("${effectiveDelay}초 후 안내", "announce after ${effectiveDelay}s")
            }
        }
        return timingText
    }
    fun seconds(value: Int): String = if (english) "${value}s" else "${value}초"
    val currentAnnouncement: String get() = t("현재 안내", "Current announcement")
    fun homeAnnouncementBasis(configuration: EffectiveAnnouncementConfiguration): String = when {
        configuration.source == AnnouncementConfigurationSource.CONTENT_SPECIFIC -> {
            collectionValue(configuration.collection)
        }
        else -> t("기본 설정", "Default")
    }
    fun homeAnnouncementBehavior(timing: AnnouncementTiming, delaySeconds: Int): String =
        guideTimingSummary(timing, delaySeconds)

    fun announcementFieldsSummary(fields: List<AnnouncementReadField>): String =
        fields.joinToString(" · ") { readField(it) }

    val homeVoiceGuide: String get() = t("음성 안내", "Voice guide")
    fun statusSummary(effectiveEnabled: Boolean, enabled: Boolean): String = when {
        !effectiveEnabled -> t("OFF · 음성 안내가 꺼져 있습니다.", "OFF · Voice guide is off.")
        !enabled -> t("자동 활성화 · 조건에 맞을 때 안내합니다.", "Auto-enabled · Guide runs when conditions match.")
        else -> t("ON · 새 곡을 안내합니다.", "ON · New tracks will be announced.")
    }

    val notificationPermissionTitle: String get() = t("상태 알림 권한", "Status notification permission")
    val notificationPermissionSummary: String
        get() = t("상단바 바로가기 알림을 표시하려면 알림 권한이 필요합니다. 음악 감지 권한과는 별개입니다.", "Notification permission is needed for the shortcut notification. It is separate from music detection access.")
    val allowNotifications: String get() = t("알림 허용", "Allow notifications")
    val permissionRequired: String get() = t("권한이 필요합니다", "Permission required")
    val permissionSummary: String get() = t("음악 정보를 읽으려면 알림 접근 권한을 허용해 주세요.", "Allow notification access to read music information.")
    val permissionSettings: String get() = t("권한 설정", "Permission settings")

    val currentTrack: String get() = t("현재 재생", "Now playing")
    val noMusicPlaying: String get() = t("재생 중인 음악이 없습니다.", "No music is playing.")
    val playMusicHint: String get() = t("음악 앱을 재생하면 여기에 표시됩니다.", "Play music in a media app to see it here.")
    val appField: String get() = t("앱", "App")
    val trackField: String get() = t("곡", "Track")
    val artistField: String get() = t("아티스트", "Artist")
    val albumField: String get() = t("앨범", "Album")
    val playlistField: String get() = t("재생목록", "Playlist")
    val trackNumberField: String get() = t("트랙", "Track #")
    val unknownTitle: String get() = t("곡명 없음", "Unknown track title")
    val unknownArtist: String get() = t("아티스트 없음", "Unknown artist")
    val unknownAlbum: String get() = t("앨범 없음", "Unknown album")
    fun trackNumber(number: Int, total: Int?): String = if (english) {
        "#$number${total?.let { " / $it" }.orEmpty()}"
    } else {
        "${number}번${total?.let { " / $it" }.orEmpty()}"
    }
    val playMusic: String get() = t("음악 재생", "Play music")
    val pauseMusic: String get() = t("음악 일시정지", "Pause music")
    fun lastDetected(time: String): String = t("마지막 감지 $time", "Last detected $time")

    val premiumTitle: String get() = "TrackTalk Plus"
    val plusBadge: String get() = "PLUS"
    val premiumEnabledSummary: String get() = t("Plus가 활성화되어 모든 고급 기능을 사용할 수 있습니다.", "Plus is active. All advanced features are available.")
    val premiumLockedSummary: String get() = t("음성 속도·높이·음량과 기기별 자동화 기능을 한 번의 결제로 이용할 수 있습니다.", "Unlock detailed voice controls and per-device automation with a one-time purchase.")
    val view: String get() = t("보기", "View")
    val premiumVoiceBenefit: String get() = t("음성 속도·높이·음량 세부 조절", "Fine-tune voice speed, pitch, and volume")
    val premiumDeviceBenefit: String get() = t("기기별 안내와 자동 활성화", "Per-device announcements and auto-enable")
    val premiumFutureBenefit: String get() = t("추가 예정인 고급 음성·자동화 기능", "Future advanced voice and automation features")
    val premiumActive: String get() = t("Plus가 활성화되어 있습니다.", "Plus is active.")
    fun oneTimePrice(price: String): String = t("일회성 결제 · $price", "One-time purchase · $price")
    val purchaseUnavailable: String get() = t(
        "현재 설치 환경에서는 구매 정보를 확인할 수 없습니다. Google Play에서 설치한 앱에서 다시 확인해 주세요.",
        "Purchase details aren't available in this installation. Check the Google Play version of the app.",
    )
    fun premiumMessage(message: PremiumMessage): String = when (message) {
        PremiumMessage.BILLING_UNAVAILABLE -> t("Google Play 결제를 사용할 수 없습니다.", "Google Play billing is unavailable.")
        PremiumMessage.SERVICE_DISCONNECTED -> t("Google Play 연결이 끊겼습니다. 잠시 후 다시 시도해 주세요.", "The Google Play connection was lost. Please try again later.")
        PremiumMessage.PRODUCT_UNAVAILABLE -> purchaseUnavailable
        PremiumMessage.PRODUCT_LOAD_FAILED -> t("구매 상품을 불러오지 못했습니다. 잠시 후 다시 시도해 주세요.", "We couldn't load the purchase product. Please try again later.")
        PremiumMessage.PURCHASE_UNAVAILABLE -> t("구매 상품을 아직 불러오지 못했습니다. 잠시 후 다시 시도해 주세요.", "The purchase product isn't available yet. Please try again later.")
        PremiumMessage.PURCHASE_FLOW_FAILED -> t("구매 화면을 열지 못했습니다. 잠시 후 다시 시도해 주세요.", "We couldn't open the purchase screen. Please try again later.")
        PremiumMessage.PURCHASE_CANCELED -> t("구매를 취소했습니다.", "Purchase canceled.")
        PremiumMessage.PURCHASE_FAILED -> t("구매를 완료하지 못했습니다. 잠시 후 다시 시도해 주세요.", "The purchase couldn't be completed. Please try again later.")
        PremiumMessage.RESTORE_FAILED -> t("구매 내역을 확인하지 못했습니다. 잠시 후 다시 시도해 주세요.", "We couldn't check your purchases. Please try again later.")
        PremiumMessage.PENDING -> t("결제가 보류 중입니다. 결제가 완료되면 Plus가 활성화됩니다.", "Payment is pending. Plus will activate when payment is complete.")
        PremiumMessage.LOCAL_CODE_APPLIED -> t("지인용 Plus 코드가 적용되었습니다.", "Friend Plus code applied.")
        PremiumMessage.ACKNOWLEDGE_FAILED -> t("구매 확인을 완료하지 못했습니다. 앱을 다시 열어 확인해 주세요.", "We couldn't confirm the purchase. Reopen the app to check again.")
    }
    val promoDescription: String
        get() = t("지인용 코드는 이 기기에서 Plus를 바로 활성화합니다. Google Play 프로모션 코드는 아래 버튼을 눌러 사용하세요.", "A friend code activates Plus on this device. Use a Google Play promo code with the button below.")
    val promoCode: String get() = t("코드 입력", "Enter a code")
    val promoCodeSection: String get() = t("프로모션 코드", "Promo code")
    val closePromoCode: String get() = t("프로모션 코드 닫기", "Hide promo code")
    val promoCodeFormatError: String get() = t("영문, 숫자, 하이픈(-)만 입력할 수 있습니다.", "Use letters, numbers, and hyphens (-) only.")
    val applyFriendCode: String get() = t("지인용 코드 적용", "Apply friend code")
    val useGooglePlayCode: String get() = t("Google Play 프로모션 코드 사용", "Redeem on Google Play")
    val close: String get() = t("닫기", "Close")
    val checking: String get() = t("확인 중...", "Checking...")
    val buyPlus: String get() = t("Plus 구매", "Buy Plus")
    val restorePurchase: String get() = t("구매 내역 복원", "Restore purchase")

    val basicOperation: String get() = t("기본 동작", "Basic behavior")
    val voiceGuide: String get() = t("음성 안내", "Voice guide")
    val voiceGuideSummary: String get() = t("상단바의 안내 타일과 동기화됩니다.", "Syncs with the guide tile in the notification shade.")
    val announcementOutput: String get() = t("안내 출력", "Announcement output")
    fun announcementOutputOption(policy: AnnouncementOutputPolicy): String = when (policy) {
        AnnouncementOutputPolicy.ALL_OUTPUTS -> t("모든 오디오 출력", "All audio outputs")
        AnnouncementOutputPolicy.EXTERNAL_ONLY -> t("외부 오디오에서만", "External audio only")
    }
    fun announcementOutputSummary(policy: AnnouncementOutputPolicy): String = when (policy) {
        AnnouncementOutputPolicy.ALL_OUTPUTS -> t(
            "휴대폰 스피커와 외부 오디오에서 모두 안내합니다.",
            "Announce through the phone speaker and external audio routes.",
        )
        AnnouncementOutputPolicy.EXTERNAL_ONLY -> t(
            "Bluetooth, 유선·USB·HDMI 등 외부 오디오에서만 안내합니다.",
            "Announce only through Bluetooth, wired, USB, HDMI, and other external routes.",
        )
    }
    val statusShortcut: String get() = t("상단바 바로가기", "Notification shortcut")
    val statusShortcutSummary: String get() = t("알림을 눌러 앱으로 바로 이동합니다.", "Tap the notification to open the app.")
    val connectedDevices: String get() = t("연결 기기", "Connected devices")
    val automationPlusTitle: String get() = t("자동화", "Automation")
    val automationPlusSummary: String get() = t(
        "Bluetooth·USB·HDMI 기기별 동작과 화면 꺼짐 자동 활성화를 설정할 수 있습니다.",
        "Configure Bluetooth, USB, and HDMI behavior plus screen-off auto-enable.",
    )
    val deviceAutomationSummary: String get() = t("기기별로 안내 사용과 자동 켜짐을 정할 수 있습니다.", "Choose announcement and auto-enable behavior per device.")
    val noConnectedDevices: String get() = t("연결된 이어폰이나 Bluetooth 기기가 없습니다.", "No headphones or Bluetooth devices are connected.")
    val diagnosticsSummary: String get() = t("알림 접근, 미디어 감지, 음성 엔진 상태를 확인합니다.", "Check notification access, media detection, and the voice engine.")
    val openDiagnostics: String get() = t("진단 열기", "Open diagnostics")
    val backToDevices: String get() = t("기기·진단으로 돌아가기", "Back to devices & diagnostics")
    val useOnThisDevice: String get() = t("이 기기에서 사용", "Use on this device")
    val useOnThisDeviceSummary: String get() = t("연결 중인 이 기기에 안내합니다.", "Announce through this connected device.")
    val autoEnableOnConnect: String get() = t("연결하면 자동 켜기", "Enable when connected")
    val autoEnableOnConnectSummary: String get() = t("이 기기가 연결되면 안내를 켭니다.", "Enable announcements when this device connects.")
    val trackGuide: String get() = t("기본 안내", "Guide defaults")
    val guideDefaultsSummary: String get() = t("모든 앱에 적용되는 기본 안내입니다.", "Defaults for all apps.")
    val trackStart: String get() = t("재생 시작", "Playback start")
    fun trackStartSummary(behavior: TrackStartBehavior): String = when (behavior) {
        TrackStartBehavior.PLAY_IMMEDIATELY -> t(
            "새 곡을 감지하면 음악과 함께 안내합니다.",
            "Guide with music when a new track is detected.",
        )
        TrackStartBehavior.ANNOUNCE_THEN_PLAY -> t(
            "새 곡을 먼저 안내한 뒤 음악을 재생합니다.",
            "Announce the new track, then play music.",
        )
    }
    val announceThenPlaySummary: String get() = t(
        "안내 중 음악은 멈추고, 끝나면 다시 재생합니다.",
        "Music pauses during the guide and resumes when it ends.",
    )
    val musicDuringGuide: String get() = t("안내 중 음악", "Music during guide")
    val musicVolumeSummary: String get() = t("음성 음량은 음성 탭에서, 안내 중 음악 음량은 여기서 조절합니다.", "Adjust voice volume in Voice and music volume here.")
    val musicDuckAmount: String get() = t("안내 중 음악 음량", "Music volume during guide")
    fun musicDuckPercent(percent: Int): String = t("현재 음량의 ${percent}%", "${percent}% of current volume")
    fun freeMusicDuckSummary(percent: Int): String = t(
        "안내 중 음악: 현재 음량의 ${percent}%",
        "Music during guide: ${percent}% of current volume",
    )
    val freeGuideWithMusic: String get() = t("새 곡: 음악과 함께 안내", "New track: guide with music")
    fun defaultVoiceVolumeSummary(percent: Int = DEFAULT_TTS_VOLUME_PERCENT): String = t(
        "음성 기본 음량 ${percent}%",
        "Default voice volume: ${percent}%",
    )
    val separateVoiceVolumeSummary: String get() = t("음성 음량은 음악과 따로 조절합니다.", "Voice volume is separate from music.")
    val freeGuideDetailsSummary: String get() = t(
        "안내 시점과 음량을 세밀하게 조절합니다.",
        "Fine-tune announcement timing and volume.",
    )
    val announcementTiming: String get() = t("언제 읽을까요?", "When to read")
    val globalReadContent: String get() = t("기본 읽기 형식", "Default reading format")
    val announcementOrder: String get() = t("안내 항목 순서 (Plus)", "Announcement order (Plus)")
    val announcementOrderSummary: String get() = t(
        "체크한 항목 중 선택한 항목을 먼저 읽고, 나머지는 기본 순서로 이어서 읽습니다.",
        "The selected checked item is read first, followed by the remaining fields in the default order.",
    )
    fun announcementOrder(order: AnnouncementOrder): String = when (order) {
        AnnouncementOrder.DEFAULT -> t("기본 순서", "Default order")
        AnnouncementOrder.TITLE_FIRST -> t("곡명 먼저", "Title first")
        AnnouncementOrder.ALBUM_FIRST -> t("앨범명 먼저", "Album first")
        AnnouncementOrder.TRACK_NUMBER_FIRST -> t("트랙 번호 먼저", "Track number first")
        AnnouncementOrder.ARTIST_FIRST -> t("아티스트 먼저", "Artist first")
        AnnouncementOrder.COLLECTION_FIRST -> t("재생목록 이름 먼저", "Playlist name first")
    }
    fun globalReadContentSummary(mode: AnnouncementMode, useContentTypeSettings: Boolean): String = if (useContentTypeSettings) {
        t(
            "감지된 유형은 유형별 설정을 우선 사용하고, 유형을 알 수 없을 때만 기본 형식을 사용합니다.",
            "Detected content uses its type-specific settings first; the default format is used only when the type is unknown.",
        )
    } else {
        t(
            "이 형식과 아래 읽기 항목을 모든 콘텐츠에 적용합니다.",
            "This format and the fields below apply to all content.",
        )
    }
    val announcementDelay: String get() = t("읽기 전 대기", "Wait before reading")
    fun announcementTimingSummary(
        timing: AnnouncementTiming,
        delaySeconds: Int,
        trackStartBehavior: TrackStartBehavior? = null,
    ): String {
        if (trackStartBehavior == TrackStartBehavior.ANNOUNCE_THEN_PLAY) {
            return t(
                "곡명 안내 후 재생에서는 바로 안내합니다.",
                "Announces immediately before playback starts.",
            )
        }
        return when (timing) {
        AnnouncementTiming.IMMEDIATE -> t(
            "새 곡을 감지하면 바로 읽습니다. 대기 시간은 적용되지 않습니다.",
            "Reads as soon as a new track is detected. Wait time is ignored.",
        )
        AnnouncementTiming.DELAYED,
        AnnouncementTiming.BETWEEN_TRACKS,
        -> {
            val effectiveDelay = AnnouncementTimingPolicy.normalizeStoredDelaySeconds(timing, delaySeconds)
            t(
                "새 곡을 감지한 뒤 ${effectiveDelay}초 후 읽습니다.",
                "Reads ${effectiveDelay}s after a new track is detected.",
            )
        }
        }
    }
    val minimumPlayback: String get() = t("최소 재생 시간", "Minimum playback time")
    val repeatTrack: String get() = t("같은 곡 다시 안내", "Repeat the same track")
    val repeatTrackSummary: String get() = t("기본값은 같은 곡을 한 번만 안내합니다.", "By default, each track is announced once.")
    val albumPlaylistReading: String get() = t("콘텐츠 유형별 읽기", "Content-type reading")
    val albumPlaylistSummary: String get() = t(
        "앨범·재생목록·추천 재생에 맞게 자동으로 안내합니다.",
        "Automatically adapts announcements for albums, playlists, and recommended or shuffle playback.",
    )
    val contentReadPresetHint: String get() = t(
        "유형별 설정을 켜면 감지된 유형은 아래 설정을 우선 사용합니다. 기본 설정은 유형을 알 수 없을 때 적용합니다.",
        "When type-specific settings are on, detected content uses the sections below first. Defaults apply when the type is unknown.",
    )
    val allContentPresetLabel: String get() = t("기본 읽기 항목", "Default read fields")
    val allContentReadItems: String get() = t("기본 설정에서 읽기", "Fields in default settings")
    val contentTypeSettings: String get() = t("유형별 설정 사용", "Use type-specific settings")
    val contentTypeDetails: String get() = t("유형별 설정", "Type-specific settings")
    fun contentTypeSettingsSummary(enabled: Boolean, mode: AnnouncementMode): String = if (enabled) {
        t(
            "앨범·재생목록·추천/랜덤처럼 유형이 확인되면 각각의 설정을 사용합니다. 유형을 못 찾으면 기본 설정을 씁니다.",
            "Use the matching album, playlist, or recommendation/shuffle settings when the type is known; otherwise use the defaults.",
        )
    } else {
        t(
            "유형별 설정을 끄고 모든 콘텐츠에 기본 설정을 적용합니다.",
            "Turn this off to apply the default settings to all content.",
        )
    }
    val contentTypeSettingsDisabledSummary: String get() = t(
        "현재는 위의 기본 설정을 모든 콘텐츠에 적용합니다. 유형별 설정을 켜면 세부 항목이 다시 표시됩니다.",
        "The default settings above apply to all content. Turn on content-type settings to show the individual sections.",
    )
    val contentTypeSettingsInactiveSummary: String get() = t(
        "유형별 설정은 저장되어 있지만 현재 기본 읽기 형식이 우선 적용됩니다. 유형별 설정을 쓰려면 기본 읽기 형식을 자동으로 바꾸세요.",
        "Type-specific settings are saved but inactive because the default format takes priority. Choose Automatic to use them.",
    )
    fun contentPresetLabel(collection: PlaybackCollection): String = when (collection) {
        PlaybackCollection.ALBUM -> t("앨범 빠른 설정", "Album preset")
        PlaybackCollection.PLAYLIST -> t("재생목록 빠른 설정", "Playlist preset")
        PlaybackCollection.ALGORITHMIC -> t("추천·랜덤 빠른 설정", "Recommended / shuffle preset")
        PlaybackCollection.UNKNOWN -> t("빠른 설정", "Quick preset")
    }
    fun contentTypeSectionTitle(collection: PlaybackCollection): String = when (collection) {
        PlaybackCollection.ALBUM -> t("앨범 재생", "Album playback")
        PlaybackCollection.PLAYLIST -> t("재생목록 재생", "Playlist playback")
        PlaybackCollection.ALGORITHMIC -> t("추천·랜덤 재생", "Recommended / shuffle playback")
        PlaybackCollection.UNKNOWN -> t("콘텐츠 유형", "Content type")
    }
    fun contentReadPreset(preset: ContentReadPreset): String = when (preset) {
        ContentReadPreset.DEFAULT -> t("기본 항목", "Default fields")
        ContentReadPreset.TITLE_AND_ARTIST -> t("곡명·아티스트", "Track title · artist")
        ContentReadPreset.TITLE_ONLY -> t("곡명만", "Track title only")
        ContentReadPreset.CUSTOM -> t("직접 선택", "Custom")
    }
    val albumReadItems: String get() = t("앨범에서 읽기", "Read from albums")
    val albumNameFirstTrackOnly: String get() = t("앨범명은 첫 트랙에서만 안내", "Announce album name on the first track only")
    val albumNameFirstTrackOnlySummary: String get() = t(
        "앨범 재생 중 첫 곡에서만 앨범명을 읽고, 다음 곡부터는 생략합니다.",
        "During album playback, read the album name on the first track and omit it from the rest.",
    )
    val playlistReadItems: String get() = t("재생목록에서 읽기", "Read from playlists")
    val algorithmReadItems: String get() = t("추천·랜덤에서 읽기", "Read from recommended / shuffle")
    val contentReadSelectionHint: String get() = t("선택한 항목만 읽습니다.", "Only selected items are read.")
    val contentReadOrderHint: String get() = t(
        "항목을 눌러 켜고 끄세요. 길게 눌러 좌우로 끌면 읽는 순서를 바꿉니다.",
        "Tap to include or exclude. Touch and hold, then drag left or right to reorder.",
    )
    val contentSpecificPlusSummary: String get() = t(
        "콘텐츠별 읽기 항목과 안내 순서를 따로 설정합니다.",
        "Set reading fields and announcement order for each content type.",
    )
    val detailedGuidePlusTitle: String get() = t("상세 안내 설정", "Detailed guide settings")
    val contentReadingPlusTitle: String get() = t("콘텐츠별 읽기 설정", "Content reading settings")
    val autoEnable: String get() = t("자동 켜기", "Auto enable")
    val screenOffEnable: String get() = t("화면이 꺼지면 켜기", "Enable when screen turns off")
    val screenOffEnableSummary: String get() = t("화면을 끄면 안내를 시작합니다.", "Start announcements when the screen turns off.")
    val screenOnRestore: String get() = t("화면을 켜면 원래대로", "Restore when screen turns on")
    val screenOnRestoreSummary: String get() = t("화면을 켜면 자동 상태를 해제합니다.", "Disable the automatic state when the screen turns on.")
    val bluetoothOnly: String get() = t("화면이 꺼지면 Bluetooth에서만 켜기", "Screen-off enable on Bluetooth only")
    val bluetoothOnlySummary: String get() = t("화면이 꺼질 때 Bluetooth 오디오가 연결된 경우에만 안내를 자동으로 켭니다.", "Enable only when Bluetooth audio is connected as the screen turns off.")

    val appsIntro: String get() = t(
        "앱별로 TrackTalk 사용 여부만 설정합니다. 안내 내용과 시점은 안내 설정을 따릅니다.",
        "Choose which apps use TrackTalk. Announcement content and timing follow Guide settings.",
    )
    val refresh: String get() = t("새로 고침", "Refresh")
    val visibleCategories: String get() = t("표시할 카테고리", "Visible categories")
    val scrollMore: String get() = t("카테고리 더 보기", "Scroll for more categories")
    fun appCountSummary(appCount: Int, categoryCount: Int): String =
        if (english) "$appCount apps shown · $categoryCount categories selected" else "${appCount}개 앱 표시 · ${categoryCount}개 카테고리 선택"
    val noSupportedApps: String get() = t("지원되는 음악 앱을 찾지 못했습니다.", "No supported music apps found.")
    val appAutoAddSummary: String get() = t("앱이 미디어 세션을 만들면 자동으로 추가됩니다.", "Apps are added automatically when they create a media session.")
    val noCategorySelected: String get() = t("선택한 카테고리가 없습니다. 위에서 하나 이상 선택하세요.", "No categories selected. Choose at least one above.")
    fun categoryTitle(category: AppCategory): String = when (category) {
        AppCategory.MUSIC_STREAMING -> t("음악 스트리밍", "Music streaming")
        AppCategory.MUSIC_VIDEO -> t("음악·동영상", "Music · video")
        AppCategory.LEARNING -> t("학습·오디오북", "Learning · audiobook")
        AppCategory.PODCAST -> t("팟캐스트·라디오", "Podcast · radio")
        AppCategory.OTHER -> t("기타 미디어", "Other media")
    }
    fun categoryDescription(category: AppCategory): String = when (category) {
        AppCategory.MUSIC_STREAMING -> t("음악을 중심으로 재생하는 앱", "Apps focused on music playback")
        AppCategory.MUSIC_VIDEO -> t("영상과 뮤직비디오를 재생하는 앱", "Apps for videos and music videos")
        AppCategory.LEARNING -> t("강의, 학습, 오디오북을 듣는 앱", "Apps for classes, study, and audiobooks")
        AppCategory.PODCAST -> t("팟캐스트와 라디오를 듣는 앱", "Apps for podcasts and radio")
        AppCategory.OTHER -> t("자동 분류되지 않은 미디어 앱", "Uncategorized media apps")
    }
    fun appCategoryCount(count: Int): String = if (english) "$count" else "${count}개"
    val appGuideEnabled: String get() = t("안내 사용 중", "Guide on")
    val appGuideDisabled: String get() = t("안내 꺼짐", "Guide off")
    val readTitle: String get() = t("곡명", "Track title")
    val readArtist: String get() = t("아티스트", "Artist")
    val readTrackNumber: String get() = t("트랙 번호", "Track number")
    val readAlbum: String get() = t("앨범", "Album")
    fun readField(field: AnnouncementReadField): String = when (field) {
        AnnouncementReadField.TITLE -> readTitle
        AnnouncementReadField.ARTIST -> readArtist
        AnnouncementReadField.TRACK_NUMBER -> readTrackNumber
        AnnouncementReadField.ALBUM -> readAlbum
        AnnouncementReadField.COLLECTION -> t("재생목록 이름", "Playlist name")
    }

    val voiceSelection: String get() = t("음성 선택", "Voice selection")
    val defaultVoiceLanguage: String get() = t("기본 언어", "Default language")
    val voiceLanguageHint: String get() = t("한글·영문·일문·중문이 섞이면 구간별 음성으로 자동 전환합니다.", "Mixed Korean, English, Japanese, and Chinese text switches voice by segment.")
    val gender: String get() = t("성별", "Gender")
    val voice: String get() = t("목소리", "Voice")
    val autoSelect: String get() = t("자동 선택", "Automatic")
    val noMatchingVoices: String get() = t("선택한 언어와 성별의 설치 음성이 없습니다.", "No installed voice matches the selected language and gender.")
    fun availableVoices(gender: GenderFilter, count: Int): String = if (english) {
        "${genderLabel(gender)} voices available: $count"
    } else {
        "선택 가능한 ${genderLabel(gender)} ${count}개"
    }
    val speechControls: String get() = t("말하기 조절", "Speech controls")
    val speechRate: String get() = t("속도", "Speed")
    val pitch: String get() = t("높이", "Pitch")
    val voiceVolumeSeparate: String get() = t("음성 음량 · 음악과 별도", "Voice volume · separate from music")
    val speechVolumeHint: String get() = t("음악 음량은 안내 탭의 기본 설정에서 정합니다. 줄이기 선택 시 안내가 끝나면 원래 미디어 음량으로 복원합니다.", "Music behavior is set in Guide. When lowering music, the original media volume is restored after the announcement.")
    val testPlayback: String get() = t("테스트 재생", "Test playback")
    val testExample: String get() = t("예: 트랙 3번, Glass Eyes · Radiohead", "Example: track 3, Glass Eyes · Radiohead")
    val voiceControlsPlusTitle: String get() = t("음성 세밀 조절은 Plus 기능입니다.", "Detailed voice controls are a Plus feature.")
    val voiceControlsFreeSummary: String get() = t("말하기 속도·높이·음량을 음악과 분리해 직접 조절할 수 있습니다.", "Adjust speech speed, pitch, and volume separately from music.")

    val connectionStatus: String get() = t("연결 상태", "Connection status")
    val notificationAccess: String get() = t("알림 접근", "Notification access")
    val connected: String get() = t("연결됨", "Connected")
    val notConnected: String get() = t("연결되지 않음", "Not connected")
    val activeMediaSessions: String get() = t("활성 음악 세션", "Active media sessions")
    val selectedApp: String get() = t("선택한 앱", "Selected app")
    val none: String get() = t("없음", "None")
    val voiceEngine: String get() = t("음성 엔진", "Voice engine")
    val recentLog: String get() = t("최근 기록", "Recent log")
    val metadataDetected: String get() = t("곡 정보 감지", "Track metadata detected")
    val playbackDetected: String get() = t("재생 상태 감지", "Playback state detected")
    val lastAnnouncement: String get() = t("마지막 안내", "Last announcement")
    val announcementTime: String get() = t("안내 시각", "Announcement time")
    val appInfoTitle: String get() = t("앱 정보", "App info")
    val appInfoSummary: String get() = t("TrackTalk의 버전과 개발자 정보를 확인할 수 있습니다.", "Check the TrackTalk version and developer information.")
    val versionLabel: String get() = t("버전", "Version")
    val buildNumberLabel: String get() = t("빌드 번호", "Build number")
    val developerLabel: String get() = t("개발자", "Developer")
    val developerName: String get() = "yiri20"
    val openRepository: String get() = t("프로젝트 페이지 열기", "Open project page")
    val privacy: String get() = t("개인정보", "Privacy")
    val privacySummary: String get() = t("곡 정보는 안내에만 사용하며 서버에 저장하지 않습니다.", "Track information is used only for announcements and is not stored on a server.")
    val currentTrackInfo: String get() = t("현재 곡 정보", "Current track information")
    val titleMissing: String get() = t("곡명 없음", "No track title")
    val artistMissing: String get() = t("아티스트 없음", "No artist")
    val permissionNeeded: String get() = t("권한 필요", "Permission required")
    val on: String get() = "ON"
    val off: String get() = "OFF"
    val permissionShort: String get() = t("권한", "Access")

    fun announcementMode(mode: AnnouncementMode): String = when (mode) {
        AnnouncementMode.SMART -> t("자동", "Automatic")
        AnnouncementMode.ALBUM -> t("앨범", "Album")
        AnnouncementMode.PLAYLIST -> t("재생목록", "Playlist")
        AnnouncementMode.TITLE_AND_ARTIST -> t("제목·아티스트", "Title · artist")
        AnnouncementMode.TITLE_ONLY -> t("제목", "Title")
    }

    fun announcementTiming(timing: AnnouncementTiming): String = when (timing) {
        AnnouncementTiming.IMMEDIATE -> t("바로 읽기", "Read now")
        AnnouncementTiming.DELAYED,
        AnnouncementTiming.BETWEEN_TRACKS,
        -> t("몇 초 후 읽기", "Read after a delay")
    }

    fun voiceLanguage(language: VoiceLanguage): String = when (language) {
        VoiceLanguage.AUTO -> t("제목에 맞게 자동", "Automatic by title")
        VoiceLanguage.SYSTEM -> t("시스템 언어", "System language")
        VoiceLanguage.KOREAN -> "한국어"
        VoiceLanguage.ENGLISH -> "English"
    }

    fun musicTreatment(treatment: MusicTreatment): String = when (treatment) {
        MusicTreatment.KEEP -> t("그대로 재생", "Keep playing")
        MusicTreatment.DUCK -> t("음량 줄이기", "Lower volume")
        MusicTreatment.PAUSE -> t("일시정지", "Pause")
    }

    fun trackStartBehavior(behavior: TrackStartBehavior): String = when (behavior) {
        TrackStartBehavior.PLAY_IMMEDIATELY -> t("음악과 함께 안내", "Guide with music")
        TrackStartBehavior.ANNOUNCE_THEN_PLAY -> t("곡명 안내 후 재생", "Announce, then play")
    }

    fun genderLabel(gender: GenderFilter): String = when (gender) {
        GenderFilter.ANY,
        GenderFilter.UNSPECIFIED,
        -> t("자동 선택", "automatic")
        GenderFilter.FEMALE -> t("여성 음성", "female")
        GenderFilter.MALE -> t("남성 음성", "male")
    }

    fun playbackStatus(status: PlaybackStatus): String = when (status) {
        PlaybackStatus.PLAYING -> t("재생 중", "Playing")
        PlaybackStatus.PAUSED -> t("일시정지", "Paused")
        PlaybackStatus.BUFFERING -> t("버퍼링", "Buffering")
        PlaybackStatus.STOPPED -> t("정지", "Stopped")
        PlaybackStatus.NONE -> t("상태 없음", "No status")
    }

    fun collectionLabel(collection: PlaybackCollection): String = when (collection) {
        PlaybackCollection.ALBUM -> t("앨범 재생", "Album playback")
        PlaybackCollection.PLAYLIST -> t("재생목록 재생", "Playlist playback")
        PlaybackCollection.ALGORITHMIC -> t("알고리즘·랜덤 재생", "Algorithmic / shuffle playback")
        PlaybackCollection.UNKNOWN -> t("기본 안내", "Basic guide")
    }

    fun collectionValue(collection: PlaybackCollection): String = when (collection) {
        PlaybackCollection.ALBUM -> t("앨범", "Album")
        PlaybackCollection.PLAYLIST -> t("재생목록", "Playlist")
        PlaybackCollection.ALGORITHMIC -> t("알고리즘·랜덤", "Algorithmic / shuffle")
        PlaybackCollection.UNKNOWN -> t("확인 중", "Checking")
    }

    val collectionUnknownSummary: String get() = t(
        "재생 앱이 앨범·재생목록 정보를 제공하지 않아 곡명과 아티스트 기준으로 안내합니다.",
        "This player did not provide album or playlist context, so the title and artist are used.",
    )

    fun collectionFallback(fallback: CollectionFallback): String = when (fallback) {
        CollectionFallback.AUTO -> t("자동(곡명·아티스트로 안내)", "Automatic (title + artist)")
        CollectionFallback.ALBUM -> t("앨범으로 처리", "Treat as album")
        CollectionFallback.PLAYLIST -> t("재생목록으로 처리", "Treat as playlist")
        CollectionFallback.ALGORITHMIC -> t("알고리즘·랜덤으로 처리", "Treat as algorithmic / shuffle")
    }

    companion object {
        fun forLanguage(appLanguage: AppLanguage, systemLanguage: String): TrackTalkStrings =
            TrackTalkStrings(appLanguage.resolve(systemLanguage))
    }
}

val LocalTrackTalkStrings = staticCompositionLocalOf<TrackTalkStrings> {
    error("TrackTalk strings were not provided")
}

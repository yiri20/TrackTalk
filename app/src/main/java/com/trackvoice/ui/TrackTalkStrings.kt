package com.trackvoice.ui

import com.trackvoice.data.AnnouncementMode
import com.trackvoice.data.AnnouncementTiming
import com.trackvoice.data.AppCategory
import com.trackvoice.data.AppLanguage
import com.trackvoice.data.GenderFilter
import com.trackvoice.data.MusicTreatment
import com.trackvoice.data.TrackStartBehavior
import com.trackvoice.data.VoiceLanguage
import com.trackvoice.data.resolve
import com.trackvoice.media.PlaybackCollection
import com.trackvoice.media.PlaybackStatus
import com.trackvoice.monetization.PremiumMessage
import androidx.compose.runtime.staticCompositionLocalOf

/** Small, local UI dictionary used by the Compose surface. */
class TrackTalkStrings private constructor(private val language: AppLanguage) {
    private val english = language == AppLanguage.ENGLISH

    private fun t(korean: String, englishText: String): String = if (english) englishText else korean

    fun text(korean: String, englishText: String): String = t(korean, englishText)

    fun sectionTitle(section: AppSection): String = when (section) {
        AppSection.HOME -> "TrackTalk"
        AppSection.GENERAL -> t("안내 설정", "Guide settings")
        AppSection.APPS -> t("앱 설정", "App settings")
        AppSection.VOICE -> t("음성 설정", "Voice settings")
        AppSection.DIAGNOSTICS -> t("진단", "Diagnostics")
    }

    fun navLabel(section: AppSection): String = when (section) {
        AppSection.HOME -> t("홈", "Home")
        AppSection.GENERAL -> t("안내", "Guide")
        AppSection.APPS -> t("앱", "Apps")
        AppSection.VOICE -> t("음성", "Voice")
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

    val guideSettings: String get() = t("안내 설정", "Guide settings")
    fun guideSummary(mode: AnnouncementMode, delaySeconds: Int): String =
        if (english) "${announcementMode(mode)} · announce after ${delaySeconds}s" else "${announcementMode(mode)} · ${delaySeconds}초 후 안내"
    fun seconds(value: Int): String = if (english) "${value}s" else "${value}초"
    val openSettings: String get() = t("설정 열기", "Open settings")

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
    val unknownTitle: String get() = t("제목 없음", "Unknown title")
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
    val plusView: String get() = t("Plus 보기", "View Plus")
    val premiumEnabledSummary: String get() = t("Plus가 활성화되어 모든 고급 기능을 사용할 수 있습니다.", "Plus is active. All advanced features are available.")
    val premiumLockedSummary: String get() = t("음성 속도·높이·음량과 기기별 자동화 기능을 한 번의 결제로 이용할 수 있습니다.", "Unlock detailed voice controls and per-device automation with a one-time purchase.")
    val view: String get() = t("보기", "View")
    val basicMusicDetection: String get() = t("곡 감지와 기본 안내 기능은 계속 무료로 제공됩니다.", "Track detection and basic announcements remain free.")
    val premiumVoiceBenefit: String get() = t("음성 속도·높이·음량 세부 조절", "Fine-tune voice speed, pitch, and volume")
    val premiumDeviceBenefit: String get() = t("기기별 안내와 자동 활성화", "Per-device announcements and auto-enable")
    val premiumFutureBenefit: String get() = t("추가 예정인 고급 음성·자동화 기능", "Future advanced voice and automation features")
    val premiumActive: String get() = t("Plus가 활성화되어 있습니다.", "Plus is active.")
    fun oneTimePrice(price: String): String = t("일회성 결제 · $price", "One-time purchase · $price")
    val playProductPreparing: String get() = t("Plus 구매 기능을 준비 중입니다. 잠시 후 다시 시도해 주세요.", "Plus purchases aren't ready yet. Please try again later.")
    fun premiumMessage(message: PremiumMessage): String = when (message) {
        PremiumMessage.BILLING_UNAVAILABLE -> t("Google Play 결제를 사용할 수 없습니다.", "Google Play billing is unavailable.")
        PremiumMessage.SERVICE_DISCONNECTED -> t("Google Play 연결이 끊겼습니다. 잠시 후 다시 시도해 주세요.", "The Google Play connection was lost. Please try again later.")
        PremiumMessage.PRODUCT_UNAVAILABLE -> playProductPreparing
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
    val headphonesOnly: String get() = t("이어폰에서만 안내", "Guide only on headphones")
    val headphonesOnlySummary: String get() = t("외부 오디오가 연결될 때만 안내합니다.", "Guide only when external audio is connected.")
    val suppressSpeaker: String get() = t("스피커에서는 안내하지 않기", "Do not guide on speaker")
    val suppressSpeakerSummary: String get() = t("스피커로 재생할 때 안내를 건너뜁니다.", "Skip announcements when playing through the speaker.")
    val statusShortcut: String get() = t("상단바 바로가기", "Notification shortcut")
    val statusShortcutSummary: String get() = t("알림을 눌러 앱으로 바로 이동합니다.", "Tap the notification to open the app.")
    val connectedDevices: String get() = t("연결 기기", "Connected devices")
    val deviceAutomationSummary: String get() = t("기기별로 안내 사용과 자동 켜짐을 정할 수 있습니다.", "Choose announcement and auto-enable behavior per device.")
    val noConnectedDevices: String get() = t("연결된 이어폰이나 Bluetooth 기기가 없습니다.", "No headphones or Bluetooth devices are connected.")
    val useOnThisDevice: String get() = t("이 기기에서 사용", "Use on this device")
    val useOnThisDeviceSummary: String get() = t("연결 중인 이 기기에 안내합니다.", "Announce through this connected device.")
    val autoEnableOnConnect: String get() = t("연결하면 자동 켜기", "Enable when connected")
    val autoEnableOnConnectSummary: String get() = t("이 기기가 연결되면 안내를 켭니다.", "Enable announcements when this device connects.")
    val trackGuide: String get() = t("곡 안내", "Track announcements")
    val trackStart: String get() = t("재생 시작", "Playback start")
    val musicDuringGuide: String get() = t("안내 중 음악", "Music during guide")
    val musicVolumeSummary: String get() = t("음성 음량은 음성 탭에서 따로 조절하고, 음악은 그대로·줄이기·일시정지로 제어합니다.", "Adjust voice volume separately in Voice; keep, duck, or pause music during announcements.")
    val announcementTiming: String get() = t("안내 시점", "Announcement timing")
    val readContent: String get() = t("읽을 내용", "Content to read")
    val announcementDelay: String get() = t("안내 지연", "Announcement delay")
    val minimumPlayback: String get() = t("최소 재생 시간", "Minimum playback time")
    val repeatTrack: String get() = t("같은 곡 다시 안내", "Repeat the same track")
    val repeatTrackSummary: String get() = t("기본값은 같은 곡을 한 번만 안내합니다.", "By default, each track is announced once.")
    val albumPlaylistReading: String get() = t("앨범·재생목록 읽기", "Album and playlist reading")
    val albumPlaylistSummary: String get() = t("MediaSession의 queue 제목과 트랙 metadata를 함께 분석해 콘텐츠 유형별로 다르게 읽습니다.", "Queue titles and track metadata are analyzed together for contextual announcements.")
    val albumPlayback: String get() = t("앨범 재생", "Album playback")
    val playlistPlayback: String get() = t("재생목록 재생", "Playlist playback")
    val freeAlbumPlaylistDefaults: String get() = t("무료 기본값: 앨범은 앨범·트랙·아티스트, 재생목록은 재생목록·곡·아티스트를 안내합니다.", "Free defaults: albums announce album, track, and artist; playlists announce playlist, track, and artist.")
    val autoEnable: String get() = t("자동 켜기", "Auto enable")
    val screenOffEnable: String get() = t("화면이 꺼지면 켜기", "Enable when screen turns off")
    val screenOffEnableSummary: String get() = t("화면을 끄면 안내를 시작합니다.", "Start announcements when the screen turns off.")
    val screenOnRestore: String get() = t("화면을 켜면 원래대로", "Restore when screen turns on")
    val screenOnRestoreSummary: String get() = t("화면을 켜면 자동 상태를 해제합니다.", "Disable the automatic state when the screen turns on.")
    val bluetoothOnly: String get() = t("화면 꺼짐은 Bluetooth에서만", "Screen-off enable on Bluetooth only")
    val bluetoothOnlySummary: String get() = t("화면이 꺼질 때 Bluetooth 오디오가 연결된 경우에만 자동 켭니다.", "Enable only when Bluetooth audio is connected as the screen turns off.")

    val appsIntro: String get() = t("기기에서 미디어 재생을 지원하는 앱과 감지된 앱입니다.", "Media-capable and detected apps on this device.")
    val refresh: String get() = t("새로 고침", "Refresh")
    val visibleCategories: String get() = t("표시할 카테고리", "Visible categories")
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
    val alwaysExclude: String get() = t("항상 제외", "Always excluded")
    val appGuideEnabled: String get() = t("곡 안내 사용 중", "Track announcements on")
    val appGuideDisabled: String get() = t("곡 안내 꺼짐", "Track announcements off")
    val collapseDetails: String get() = t("세부 설정 접기", "Hide details")
    val expandDetails: String get() = t("세부 설정", "Details")
    val appDetailsPlusTitle: String get() = t("앱별 세부 설정은 Plus 기능입니다.", "Per-app details are a Plus feature.")
    val appDetailsFreeSummary: String get() = t("무료 버전은 기본 Smart 안내를 사용합니다.", "The free version uses Smart announcements.")
    val appReadTitle: String get() = t("제목", "Title")
    val appReadArtist: String get() = t("아티스트", "Artist")
    val appReadTrackNumber: String get() = t("트랙 번호", "Track number")
    val appReadAlbum: String get() = t("앨범", "Album")
    val appReadCollection: String get() = t("앨범·재생목록 이름", "Album · playlist name")
    val appAlwaysExclude: String get() = t("이 앱은 항상 제외", "Always exclude this app")

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
    val speechVolumeHint: String get() = t("음악 음량은 안내 중 음악 옵션에서 줄이기/그대로/일시정지를 선택합니다. Android의 공용 미디어 음량을 임의로 바꾸지 않습니다.", "Music behavior is selected in the guide settings. TrackTalk does not change Android's shared media volume.")
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
    val privacy: String get() = t("개인정보", "Privacy")
    val privacySummary: String get() = t("곡 정보는 안내에만 사용하며 서버에 저장하지 않습니다.", "Track information is used only for announcements and is not stored on a server.")
    val currentTrackInfo: String get() = t("현재 곡 정보", "Current track information")
    val titleMissing: String get() = t("제목 없음", "No title")
    val artistMissing: String get() = t("아티스트 없음", "No artist")
    val permissionNeeded: String get() = t("권한 필요", "Permission required")
    val on: String get() = "ON"
    val off: String get() = "OFF"
    val permissionShort: String get() = t("권한", "Access")

    fun announcementMode(mode: AnnouncementMode): String = when (mode) {
        AnnouncementMode.SMART -> "Smart"
        AnnouncementMode.ALBUM -> t("앨범 정보", "Album details")
        AnnouncementMode.PLAYLIST -> t("재생목록 정보", "Playlist details")
        AnnouncementMode.TITLE_AND_ARTIST -> t("제목 + 아티스트", "Title + artist")
        AnnouncementMode.TITLE_ONLY -> t("제목만", "Title only")
    }

    fun announcementTiming(timing: AnnouncementTiming): String = when (timing) {
        AnnouncementTiming.IMMEDIATE -> t("즉시", "Immediately")
        AnnouncementTiming.DELAYED -> t("잠시 후", "After a moment")
        AnnouncementTiming.BETWEEN_TRACKS -> t("곡 사이", "Between tracks")
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
        PlaybackCollection.UNKNOWN -> t("콘텐츠 유형 확인 중", "Identifying content type")
    }

    companion object {
        fun forLanguage(appLanguage: AppLanguage, systemLanguage: String): TrackTalkStrings =
            TrackTalkStrings(appLanguage.resolve(systemLanguage))
    }
}

val LocalTrackTalkStrings = staticCompositionLocalOf<TrackTalkStrings> {
    error("TrackTalk strings were not provided")
}

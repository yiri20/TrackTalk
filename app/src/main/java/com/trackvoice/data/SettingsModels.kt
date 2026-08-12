package com.trackvoice.data

const val DEFAULT_TTS_VOLUME = 0.65f
const val DEFAULT_MUSIC_DUCK_PERCENT = 35
const val MIN_MUSIC_DUCK_PERCENT = 10
const val MAX_MUSIC_DUCK_PERCENT = 80

enum class AnnouncementMode(val label: String) {
    SMART("Smart"),
    ALBUM("앨범 정보"),
    PLAYLIST("재생목록 정보"),
    TITLE_AND_ARTIST("제목 + 아티스트"),
    TITLE_ONLY("제목만"),
}

enum class AnnouncementTiming(val label: String) {
    IMMEDIATE("즉시"),
    DELAYED("잠시 후"),
    BETWEEN_TRACKS("곡 사이"),
}

enum class VoiceLanguage(val label: String) {
    AUTO("제목에 맞게 자동"),
    SYSTEM("시스템 언어"),
    KOREAN("한국어"),
    ENGLISH("영어"),
}

enum class MusicTreatment(val label: String) {
    KEEP("그대로 재생"),
    DUCK("음량 줄이기"),
    PAUSE("일시정지"),
}

enum class TrackStartBehavior(val label: String) {
    PLAY_IMMEDIATELY("음악과 함께 안내"),
    ANNOUNCE_THEN_PLAY("곡명 안내 후 재생"),
}

enum class GenderFilter(val label: String) {
    ANY("자동 선택"),
    FEMALE("여성 음성"),
    MALE("남성 음성"),
    UNSPECIFIED("자동 선택"),
}

enum class AppLanguage {
    SYSTEM,
    KOREAN,
    ENGLISH,
}

fun AppLanguage.resolve(systemLanguage: String): AppLanguage = when (this) {
    AppLanguage.SYSTEM -> if (systemLanguage.startsWith("ko", ignoreCase = true)) {
        AppLanguage.KOREAN
    } else {
        AppLanguage.ENGLISH
    }
    AppLanguage.KOREAN,
    AppLanguage.ENGLISH,
    -> this
}

data class UserSettings(
    val appLanguage: AppLanguage = AppLanguage.SYSTEM,
    val enabled: Boolean = true,
    val autoEnableOnScreenOff: Boolean = false,
    val restoreEnabledWhenScreenOn: Boolean = true,
    val headphonesOnly: Boolean = false,
    val bluetoothOnlyForAutoEnable: Boolean = false,
    val suppressDuringSpeakerPlayback: Boolean = true,
    val musicTreatment: MusicTreatment = MusicTreatment.DUCK,
    val musicDuckPercent: Int = DEFAULT_MUSIC_DUCK_PERCENT,
    val trackStartBehavior: TrackStartBehavior = TrackStartBehavior.PLAY_IMMEDIATELY,
    val showStatusNotification: Boolean = true,
    val timing: AnnouncementTiming = AnnouncementTiming.IMMEDIATE,
    val delaySeconds: Int = 0,
    val defaultMode: AnnouncementMode = AnnouncementMode.SMART,
    val allowRepeatAnnouncements: Boolean = false,
    val minimumPlaybackSeconds: Int = 0,
    val albumMode: AnnouncementMode = AnnouncementMode.ALBUM,
    val playlistMode: AnnouncementMode = AnnouncementMode.PLAYLIST,
    val algorithmMode: AnnouncementMode = AnnouncementMode.TITLE_AND_ARTIST,
    val voiceLanguage: VoiceLanguage = VoiceLanguage.AUTO,
    val voiceName: String? = null,
    val genderFilter: GenderFilter = GenderFilter.ANY,
    val speechRate: Float = 1f,
    val pitch: Float = 1f,
    val volume: Float = DEFAULT_TTS_VOLUME,
    val raiseDeviceVolume: Boolean = false,
    val deviceVolumePercent: Int = 90,
)

data class AudioDeviceSettings(
    val deviceKey: String,
    val displayName: String,
    val autoEnable: Boolean = false,
    val enabled: Boolean = true,
)

data class AppSettings(
    val packageName: String,
    val appName: String,
    val enabled: Boolean = true,
    val useCustomGuideSettings: Boolean = false,
    val mode: AnnouncementMode = AnnouncementMode.SMART,
    val readTitle: Boolean = true,
    val readArtist: Boolean = true,
    val readTrackNumber: Boolean = true,
    val readAlbum: Boolean = true,
    val readCollection: Boolean = true,
    val timing: AnnouncementTiming? = null,
    val alwaysExclude: Boolean = false,
)

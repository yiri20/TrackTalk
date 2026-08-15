package com.trackvoice.data

const val DEFAULT_TTS_VOLUME_PERCENT = 40
const val DEFAULT_TTS_VOLUME = 0.40f
const val DEFAULT_MUSIC_DUCK_PERCENT = 35
const val MIN_MUSIC_DUCK_PERCENT = 10
const val MAX_MUSIC_DUCK_PERCENT = 80
const val YOUTUBE_PACKAGE_NAME = "com.google.android.youtube"

fun defaultAppGuideEnabled(packageName: String, appName: String = ""): Boolean =
    AppGuideEnablementPolicy.defaultEnabled(packageName, appName)

/**
 * The single source of truth for which media routes may receive announcements.
 * Built-in speaker and earpiece are never considered external routes.
 */
enum class AnnouncementOutputPolicy {
    ALL_OUTPUTS,
    EXTERNAL_ONLY,
    ;

    fun allows(externalAudioOutput: Boolean): Boolean =
        this == ALL_OUTPUTS || externalAudioOutput

    companion object {
        /**
         * Preserve the old behavior while migrating the two legacy switches:
         * either old switch enabled meant speaker suppression, while both false
         * explicitly meant that all routes were allowed. Missing old values use
         * the previous safe default, which suppressed the built-in speaker.
         */
        fun fromLegacy(
            headphonesOnly: Boolean?,
            suppressDuringSpeakerPlayback: Boolean?,
        ): AnnouncementOutputPolicy = if (
            headphonesOnly == true || suppressDuringSpeakerPlayback != false
        ) {
            EXTERNAL_ONLY
        } else {
            ALL_OUTPUTS
        }
    }
}

enum class AnnouncementMode(val label: String) {
    SMART("Smart"),
    ALBUM("앨범 정보"),
    PLAYLIST("재생목록 정보"),
    TITLE_AND_ARTIST("제목 + 아티스트"),
    TITLE_ONLY("제목만"),
}

/**
 * The first item spoken in a multi-field announcement.
 *
 * DEFAULT keeps the content-aware order (for example, album -> track ->
 * title -> artist for albums). The other values move the selected field to
 * the front and keep the remaining checked fields in that stable order.
 */
enum class AnnouncementOrder {
    DEFAULT,
    TITLE_FIRST,
    ALBUM_FIRST,
    TRACK_NUMBER_FIRST,
    ARTIST_FIRST,
    COLLECTION_FIRST,
}

enum class CollectionFallback {
    AUTO,
    ALBUM,
    PLAYLIST,
    ALGORITHMIC,
}

enum class AnnouncementReadField {
    TITLE,
    ARTIST,
    TRACK_NUMBER,
    ALBUM,
    COLLECTION,
}

/** Every field that can be selected in the global reading configuration. */
val ALL_ANNOUNCEMENT_READ_FIELDS = listOf(
    AnnouncementReadField.TITLE,
    AnnouncementReadField.ARTIST,
    AnnouncementReadField.ALBUM,
    AnnouncementReadField.TRACK_NUMBER,
    AnnouncementReadField.COLLECTION,
)

/** The canonical default order for album announcements. */
val DEFAULT_ALBUM_READ_FIELDS = listOf(
    AnnouncementReadField.ALBUM,
    AnnouncementReadField.TRACK_NUMBER,
    AnnouncementReadField.TITLE,
    AnnouncementReadField.ARTIST,
)

/** The canonical default order for playlist announcements. */
val DEFAULT_PLAYLIST_READ_FIELDS = listOf(
    AnnouncementReadField.COLLECTION,
    AnnouncementReadField.ALBUM,
    AnnouncementReadField.TRACK_NUMBER,
    AnnouncementReadField.TITLE,
    AnnouncementReadField.ARTIST,
)

/** The canonical default order for recommendation/shuffle announcements. */
val DEFAULT_ALGORITHMIC_READ_FIELDS = listOf(
    AnnouncementReadField.ALBUM,
    AnnouncementReadField.TRACK_NUMBER,
    AnnouncementReadField.TITLE,
    AnnouncementReadField.ARTIST,
)

// A safe global fallback that works across players even when album or queue
// metadata is missing.
val DEFAULT_GLOBAL_READ_FIELDS = DEFAULT_ALGORITHMIC_READ_FIELDS

/**
 * Removes duplicates/unsupported values while preserving the user's order.
 * A reading configuration must never become empty: old or corrupt storage is
 * normalized to its content-specific default, then to the first supported
 * field as a final safety net.
 */
fun normalizeAnnouncementReadFields(
    fields: Iterable<AnnouncementReadField>,
    allowedFields: List<AnnouncementReadField>,
    fallbackFields: List<AnnouncementReadField>,
): List<AnnouncementReadField> {
    val allowed = allowedFields.toSet()
    val normalized = fields
        .filter { it in allowed }
        .distinct()
    if (normalized.isNotEmpty()) return normalized

    val fallback = fallbackFields
        .filter { it in allowed }
        .distinct()
    return fallback.ifEmpty { allowedFields.distinct().take(1) }
}

/**
 * Toggles a field in the canonical ordered selection. The final active field
 * cannot be removed, preventing a silent no-text configuration in the UI.
 */
fun toggleAnnouncementReadField(
    fields: List<AnnouncementReadField>,
    field: AnnouncementReadField,
    enabled: Boolean,
    allowedFields: List<AnnouncementReadField>,
    fallbackFields: List<AnnouncementReadField> = allowedFields,
): List<AnnouncementReadField> {
    val normalized = normalizeAnnouncementReadFields(fields, allowedFields, fallbackFields)
    return when {
        enabled && field !in normalized && field in allowedFields -> normalized + field
        !enabled && field in normalized && normalized.size > 1 -> normalized - field
        else -> normalized
    }
}

/** Reorders one active field without changing the active/inactive selection. */
fun reorderAnnouncementReadField(
    fields: List<AnnouncementReadField>,
    field: AnnouncementReadField,
    targetIndex: Int,
    allowedFields: List<AnnouncementReadField>,
    fallbackFields: List<AnnouncementReadField> = allowedFields,
): List<AnnouncementReadField> {
    val normalized = normalizeAnnouncementReadFields(fields, allowedFields, fallbackFields)
    val currentIndex = normalized.indexOf(field)
    if (currentIndex < 0 || normalized.size < 2) return normalized
    val mutable = normalized.toMutableList()
    val item = mutable.removeAt(currentIndex)
    mutable.add(targetIndex.coerceIn(0, mutable.size), item)
    return mutable
}

/**
 * Applies the old single-first-field setting while migrating legacy data.
 * New UI changes store the full order and reset this compatibility value.
 */
fun List<AnnouncementReadField>.withLegacyAnnouncementOrder(
    order: AnnouncementOrder,
): List<AnnouncementReadField> {
    val first = when (order) {
        AnnouncementOrder.DEFAULT -> null
        AnnouncementOrder.TITLE_FIRST -> AnnouncementReadField.TITLE
        AnnouncementOrder.ALBUM_FIRST -> AnnouncementReadField.ALBUM
        AnnouncementOrder.TRACK_NUMBER_FIRST -> AnnouncementReadField.TRACK_NUMBER
        AnnouncementOrder.ARTIST_FIRST -> AnnouncementReadField.ARTIST
        AnnouncementOrder.COLLECTION_FIRST -> AnnouncementReadField.COLLECTION
    } ?: return this
    if (first !in this) return this
    return listOf(first) + filterNot { it == first }
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
    val outputPolicy: AnnouncementOutputPolicy = AnnouncementOutputPolicy.EXTERNAL_ONLY,
    val bluetoothOnlyForAutoEnable: Boolean = false,
    val musicTreatment: MusicTreatment = MusicTreatment.DUCK,
    val musicDuckPercent: Int = DEFAULT_MUSIC_DUCK_PERCENT,
    val trackStartBehavior: TrackStartBehavior = TrackStartBehavior.PLAY_IMMEDIATELY,
    val showStatusNotification: Boolean = true,
    val timing: AnnouncementTiming = AnnouncementTiming.IMMEDIATE,
    val delaySeconds: Int = 0,
    val defaultMode: AnnouncementMode = AnnouncementMode.SMART,
    val useContentTypeSettings: Boolean = true,
    val defaultReadFields: List<AnnouncementReadField> = DEFAULT_GLOBAL_READ_FIELDS,
    val announcementOrder: AnnouncementOrder = AnnouncementOrder.DEFAULT,
    val allowRepeatAnnouncements: Boolean = false,
    val minimumPlaybackSeconds: Int = 0,
    val albumMode: AnnouncementMode = AnnouncementMode.ALBUM,
    val playlistMode: AnnouncementMode = AnnouncementMode.PLAYLIST,
    val algorithmMode: AnnouncementMode = AnnouncementMode.TITLE_AND_ARTIST,
    val albumReadFields: List<AnnouncementReadField> = DEFAULT_ALBUM_READ_FIELDS,
    val albumNameFirstTrackOnly: Boolean = false,
    val playlistReadFields: List<AnnouncementReadField> = DEFAULT_PLAYLIST_READ_FIELDS,
    val algorithmReadFields: List<AnnouncementReadField> = DEFAULT_ALGORITHMIC_READ_FIELDS,
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
    val collectionFallback: CollectionFallback = CollectionFallback.AUTO,
    val readTitle: Boolean = true,
    val readArtist: Boolean = true,
    val readTrackNumber: Boolean = true,
    val readAlbum: Boolean = true,
    val readCollection: Boolean = true,
    val timing: AnnouncementTiming? = null,
    /** Null means the app follows its category default; non-null is explicit. */
    val enabledOverride: Boolean? = null,
)

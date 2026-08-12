package com.trackvoice.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.trackVoiceDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "trackvoice_settings",
)

private fun appKey(packageName: String, suffix: String): Preferences.Key<String> =
    stringPreferencesKey("app.$packageName.$suffix")

private const val TTS_VOLUME_DEFAULT_VERSION = 1

class DataStoreRepository(private val context: Context) {
    private val dataStore = context.trackVoiceDataStore

    val userSettings: Flow<UserSettings> = dataStore.data
        .catch { error ->
            if (error is IOException) emit(androidx.datastore.preferences.core.emptyPreferences()) else throw error
        }
        .map { preferences -> preferences.toUserSettings() }

    val appSettings: Flow<Map<String, AppSettings>> = dataStore.data
        .catch { error ->
            if (error is IOException) emit(androidx.datastore.preferences.core.emptyPreferences()) else throw error
        }
        .map { preferences -> preferences.toAppSettings() }

    val audioDeviceSettings: Flow<Map<String, AudioDeviceSettings>> = dataStore.data
        .catch { error ->
            if (error is IOException) emit(androidx.datastore.preferences.core.emptyPreferences()) else throw error
        }
        .map { preferences -> preferences.toAudioDeviceSettings() }

    suspend fun currentUserSettings(): UserSettings = userSettings.first()
    suspend fun currentAppSettings(): Map<String, AppSettings> = appSettings.first()

    suspend fun migrateTtsVolumeDefault() {
        dataStore.edit { preferences ->
            if ((preferences[Keys.ttsVolumeDefaultVersion] ?: 0) >= TTS_VOLUME_DEFAULT_VERSION) {
                return@edit
            }
            val storedVolume = preferences[Keys.volume]
            if (storedVolume == null || storedVolume == 1f || storedVolume == 0.85f) {
                preferences[Keys.volume] = DEFAULT_TTS_VOLUME
            }
            preferences[Keys.ttsVolumeDefaultVersion] = TTS_VOLUME_DEFAULT_VERSION
        }
    }

    suspend fun setEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.enabled] = enabled }
    }

    suspend fun updateUserSettings(transform: (UserSettings) -> UserSettings) {
        dataStore.edit { preferences ->
            val updated = preferences.toUserSettings().let(transform)
            preferences.writeUserSettings(updated)
        }
    }

    suspend fun ensureApp(packageName: String, appName: String) {
        if (packageName.isBlank()) return
        dataStore.edit { preferences ->
            val packages = preferences[Keys.knownPackages].orEmpty().toMutableSet()
            val displayName = appName.ifBlank { packageName }
            if (packageName !in packages) {
                packages += packageName
                preferences[Keys.knownPackages] = packages
            }
            if (preferences[appKey(packageName, "name")] != displayName) {
                preferences[appKey(packageName, "name")] = displayName
            }
        }
    }

    suspend fun updateAppSettings(settings: AppSettings) {
        dataStore.edit { preferences ->
            val packages = preferences[Keys.knownPackages].orEmpty().toMutableSet()
            packages += settings.packageName
            preferences[Keys.knownPackages] = packages
            preferences.writeAppSettings(settings)
        }
    }

    suspend fun updateAudioDeviceSettings(settings: AudioDeviceSettings) {
        dataStore.edit { preferences ->
            val keys = preferences[Keys.knownDeviceKeys].orEmpty().toMutableSet()
            keys += settings.deviceKey
            preferences[Keys.knownDeviceKeys] = keys
            preferences[deviceKey(settings.deviceKey, "name")] = settings.displayName
            preferences[deviceBooleanKey(settings.deviceKey, "auto_enable")] = settings.autoEnable
            preferences[deviceBooleanKey(settings.deviceKey, "enabled")] = settings.enabled
        }
    }

    suspend fun removeApp(packageName: String) {
        dataStore.edit { preferences ->
            val packages = preferences[Keys.knownPackages].orEmpty().toMutableSet()
            packages -= packageName
            preferences[Keys.knownPackages] = packages
            preferences.remove(AppKeys.name(packageName))
            preferences.remove(AppKeys.enabled(packageName))
            preferences.remove(AppKeys.useCustomGuideSettings(packageName))
            preferences.remove(AppKeys.mode(packageName))
            preferences.remove(AppKeys.readTitle(packageName))
            preferences.remove(AppKeys.readArtist(packageName))
            preferences.remove(AppKeys.readTrackNumber(packageName))
            preferences.remove(AppKeys.readAlbum(packageName))
            preferences.remove(AppKeys.readCollection(packageName))
            preferences.remove(AppKeys.timing(packageName))
            preferences.remove(AppKeys.alwaysExclude(packageName))
        }
    }

    private object Keys {
        val enabled = booleanPreferencesKey("enabled")
        val appLanguage = stringPreferencesKey("app_language")
        val autoEnableOnScreenOff = booleanPreferencesKey("auto_enable_screen_off")
        val restoreEnabledWhenScreenOn = booleanPreferencesKey("restore_on_screen_on")
        val headphonesOnly = booleanPreferencesKey("headphones_only")
        val bluetoothOnlyForAutoEnable = booleanPreferencesKey("bluetooth_only_auto")
        val suppressDuringSpeakerPlayback = booleanPreferencesKey("suppress_speaker")
        val pauseMusicDuringAnnouncement = booleanPreferencesKey("pause_music_during_announcement")
        val musicTreatment = stringPreferencesKey("music_treatment")
        val musicDuckPercent = intPreferencesKey("music_duck_percent")
        val trackStartBehavior = stringPreferencesKey("track_start_behavior")
        val showStatusNotification = booleanPreferencesKey("show_status_notification")
        val timing = stringPreferencesKey("timing")
        val delaySeconds = intPreferencesKey("delay_seconds")
        val defaultMode = stringPreferencesKey("default_mode")
        val allowRepeatAnnouncements = booleanPreferencesKey("allow_repeat")
        val minimumPlaybackSeconds = intPreferencesKey("minimum_playback_seconds")
        val albumMode = stringPreferencesKey("album_mode")
        val playlistMode = stringPreferencesKey("playlist_mode")
        val algorithmMode = stringPreferencesKey("algorithm_mode")
        val voiceLanguage = stringPreferencesKey("voice_language")
        val voiceName = stringPreferencesKey("voice_name")
        val genderFilter = stringPreferencesKey("gender_filter")
        val speechRate = floatPreferencesKey("speech_rate")
        val pitch = floatPreferencesKey("pitch")
        val volume = floatPreferencesKey("volume")
        val ttsVolumeDefaultVersion = intPreferencesKey("tts_volume_default_version")
        val raiseDeviceVolume = booleanPreferencesKey("raise_device_volume")
        val deviceVolumePercent = intPreferencesKey("device_volume_percent")
        val knownPackages = stringSetPreferencesKey("known_app_packages")
        val knownDeviceKeys = stringSetPreferencesKey("known_audio_devices")
    }

    private object AppKeys {
        fun name(packageName: String) = appKey(packageName, "name")
        fun enabled(packageName: String) = appBooleanKey(packageName, "enabled")
        fun useCustomGuideSettings(packageName: String) = appBooleanKey(packageName, "custom_guide_settings")
        fun mode(packageName: String) = appKey(packageName, "mode")
        fun readTitle(packageName: String) = appBooleanKey(packageName, "read_title")
        fun readArtist(packageName: String) = appBooleanKey(packageName, "read_artist")
        fun readTrackNumber(packageName: String) = appBooleanKey(packageName, "read_track_number")
        fun readAlbum(packageName: String) = appBooleanKey(packageName, "read_album")
        fun readCollection(packageName: String) = appBooleanKey(packageName, "read_collection")
        fun timing(packageName: String) = appKey(packageName, "timing")
        fun alwaysExclude(packageName: String) = appBooleanKey(packageName, "always_exclude")
    }

    private fun Preferences.toUserSettings(): UserSettings = UserSettings(
        appLanguage = enumOrDefault(this[Keys.appLanguage], AppLanguage.SYSTEM),
        enabled = this[Keys.enabled] ?: true,
        autoEnableOnScreenOff = this[Keys.autoEnableOnScreenOff] ?: false,
        restoreEnabledWhenScreenOn = this[Keys.restoreEnabledWhenScreenOn] ?: true,
        headphonesOnly = this[Keys.headphonesOnly] ?: false,
        bluetoothOnlyForAutoEnable = this[Keys.bluetoothOnlyForAutoEnable] ?: false,
        suppressDuringSpeakerPlayback = this[Keys.suppressDuringSpeakerPlayback] ?: true,
        musicTreatment = this[Keys.musicTreatment]?.let { enumOrDefault(it, MusicTreatment.DUCK) }
            ?: if (this[Keys.pauseMusicDuringAnnouncement] ?: false) MusicTreatment.PAUSE else MusicTreatment.DUCK,
        musicDuckPercent = (this[Keys.musicDuckPercent] ?: DEFAULT_MUSIC_DUCK_PERCENT)
            .coerceIn(MIN_MUSIC_DUCK_PERCENT, MAX_MUSIC_DUCK_PERCENT),
        trackStartBehavior = enumOrDefault(this[Keys.trackStartBehavior], TrackStartBehavior.PLAY_IMMEDIATELY),
        showStatusNotification = this[Keys.showStatusNotification] ?: true,
        timing = enumOrDefault(this[Keys.timing], AnnouncementTiming.IMMEDIATE),
        delaySeconds = (this[Keys.delaySeconds] ?: 0).coerceIn(0, 2),
        defaultMode = enumOrDefault(this[Keys.defaultMode], AnnouncementMode.SMART),
        allowRepeatAnnouncements = this[Keys.allowRepeatAnnouncements] ?: false,
        minimumPlaybackSeconds = (this[Keys.minimumPlaybackSeconds] ?: 0).coerceIn(0, 120),
        albumMode = enumOrDefault(this[Keys.albumMode], AnnouncementMode.ALBUM),
        playlistMode = enumOrDefault(this[Keys.playlistMode], AnnouncementMode.PLAYLIST),
        algorithmMode = enumOrDefault(this[Keys.algorithmMode], AnnouncementMode.TITLE_AND_ARTIST),
        voiceLanguage = enumOrDefault(this[Keys.voiceLanguage], VoiceLanguage.AUTO),
        voiceName = this[Keys.voiceName],
        genderFilter = enumOrDefault(this[Keys.genderFilter], GenderFilter.ANY),
        speechRate = (this[Keys.speechRate] ?: 1f).coerceIn(0.5f, 2f),
        pitch = (this[Keys.pitch] ?: 1f).coerceIn(0.5f, 2f),
        volume = (this[Keys.volume] ?: DEFAULT_TTS_VOLUME).coerceIn(0f, 1f),
        raiseDeviceVolume = this[Keys.raiseDeviceVolume] ?: false,
        deviceVolumePercent = (this[Keys.deviceVolumePercent] ?: 90).coerceIn(10, 100),
    )

    private fun Preferences.toAudioDeviceSettings(): Map<String, AudioDeviceSettings> =
        this[Keys.knownDeviceKeys].orEmpty().associateWith { key ->
            AudioDeviceSettings(
                deviceKey = key,
                displayName = this[deviceKey(key, "name")] ?: key,
                autoEnable = this[deviceBooleanKey(key, "auto_enable")] ?: false,
                enabled = this[deviceBooleanKey(key, "enabled")] ?: true,
            )
        }

    private fun Preferences.toAppSettings(): Map<String, AppSettings> = this[Keys.knownPackages]
        .orEmpty()
        .associateWith { packageName ->
            val mode = enumOrDefault(this[AppKeys.mode(packageName)], AnnouncementMode.SMART)
            val readTitle = this[AppKeys.readTitle(packageName)] ?: true
            val readArtist = this[AppKeys.readArtist(packageName)] ?: true
            val readTrackNumber = this[AppKeys.readTrackNumber(packageName)] ?: true
            val readAlbum = this[AppKeys.readAlbum(packageName)] ?: true
            val readCollection = this[AppKeys.readCollection(packageName)] ?: true
            val timing = nullableEnum<AnnouncementTiming>(this[AppKeys.timing(packageName)])
            AppSettings(
                packageName = packageName,
                appName = this[AppKeys.name(packageName)] ?: packageName,
                enabled = this[AppKeys.enabled(packageName)] ?: true,
                useCustomGuideSettings = this[AppKeys.useCustomGuideSettings(packageName)] ?: (
                    mode != AnnouncementMode.SMART ||
                        !readTitle ||
                        !readArtist ||
                        !readTrackNumber ||
                        !readAlbum ||
                        !readCollection ||
                        timing != null
                    ),
                mode = mode,
                readTitle = readTitle,
                readArtist = readArtist,
                readTrackNumber = readTrackNumber,
                readAlbum = readAlbum,
                readCollection = readCollection,
                timing = timing,
                alwaysExclude = this[AppKeys.alwaysExclude(packageName)] ?: false,
            )
        }

    private fun MutablePreferences.writeUserSettings(settings: UserSettings) {
        this[Keys.appLanguage] = settings.appLanguage.name
        this[Keys.enabled] = settings.enabled
        this[Keys.autoEnableOnScreenOff] = settings.autoEnableOnScreenOff
        this[Keys.restoreEnabledWhenScreenOn] = settings.restoreEnabledWhenScreenOn
        this[Keys.headphonesOnly] = settings.headphonesOnly
        this[Keys.bluetoothOnlyForAutoEnable] = settings.bluetoothOnlyForAutoEnable
        this[Keys.suppressDuringSpeakerPlayback] = settings.suppressDuringSpeakerPlayback
        this[Keys.musicTreatment] = settings.musicTreatment.name
        this[Keys.musicDuckPercent] = settings.musicDuckPercent.coerceIn(MIN_MUSIC_DUCK_PERCENT, MAX_MUSIC_DUCK_PERCENT)
        this[Keys.trackStartBehavior] = settings.trackStartBehavior.name
        this[Keys.showStatusNotification] = settings.showStatusNotification
        this[Keys.timing] = settings.timing.name
        this[Keys.delaySeconds] = settings.delaySeconds.coerceIn(0, 2)
        this[Keys.defaultMode] = settings.defaultMode.name
        this[Keys.allowRepeatAnnouncements] = settings.allowRepeatAnnouncements
        this[Keys.minimumPlaybackSeconds] = settings.minimumPlaybackSeconds.coerceIn(0, 120)
        this[Keys.albumMode] = settings.albumMode.name
        this[Keys.playlistMode] = settings.playlistMode.name
        this[Keys.algorithmMode] = settings.algorithmMode.name
        this[Keys.voiceLanguage] = settings.voiceLanguage.name
        if (settings.voiceName == null) remove(Keys.voiceName) else this[Keys.voiceName] = settings.voiceName
        this[Keys.genderFilter] = settings.genderFilter.name
        this[Keys.speechRate] = settings.speechRate.coerceIn(0.5f, 2f)
        this[Keys.pitch] = settings.pitch.coerceIn(0.5f, 2f)
        this[Keys.volume] = settings.volume.coerceIn(0f, 1f)
        this[Keys.raiseDeviceVolume] = settings.raiseDeviceVolume
        this[Keys.deviceVolumePercent] = settings.deviceVolumePercent.coerceIn(10, 100)
    }

    private fun MutablePreferences.writeAppSettings(settings: AppSettings) {
        this[AppKeys.name(settings.packageName)] = settings.appName
        this[AppKeys.enabled(settings.packageName)] = settings.enabled
        this[AppKeys.useCustomGuideSettings(settings.packageName)] = settings.useCustomGuideSettings
        this[AppKeys.mode(settings.packageName)] = settings.mode.name
        this[AppKeys.readTitle(settings.packageName)] = settings.readTitle
        this[AppKeys.readArtist(settings.packageName)] = settings.readArtist
        this[AppKeys.readTrackNumber(settings.packageName)] = settings.readTrackNumber
        this[AppKeys.readAlbum(settings.packageName)] = settings.readAlbum
        this[AppKeys.readCollection(settings.packageName)] = settings.readCollection
        if (settings.timing == null) remove(AppKeys.timing(settings.packageName))
        else this[AppKeys.timing(settings.packageName)] = settings.timing.name
        this[AppKeys.alwaysExclude(settings.packageName)] = settings.alwaysExclude
    }

    private inline fun <reified T : Enum<T>> enumOrDefault(value: String?, default: T): T =
        value?.let { runCatching { enumValueOf<T>(it) }.getOrNull() } ?: default

    private inline fun <reified T : Enum<T>> nullableEnum(value: String?): T? =
        value?.let { runCatching { enumValueOf<T>(it) }.getOrNull() }
}

private fun appBooleanKey(packageName: String, suffix: String): Preferences.Key<Boolean> =
    booleanPreferencesKey("app.$packageName.$suffix")

private fun deviceKey(key: String, suffix: String): Preferences.Key<String> =
    stringPreferencesKey("device.${key.hashCode()}.$suffix")

private fun deviceBooleanKey(key: String, suffix: String): Preferences.Key<Boolean> =
    booleanPreferencesKey("device.${key.hashCode()}.$suffix")

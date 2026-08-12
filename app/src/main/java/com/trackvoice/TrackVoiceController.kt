package com.trackvoice

import android.content.Context
import com.trackvoice.announcement.AnnouncementPolicy
import com.trackvoice.announcement.AudioFocusManager
import com.trackvoice.announcement.AudioOutputDetector
import com.trackvoice.announcement.DuplicateSuppressor
import com.trackvoice.announcement.AudioDeviceMonitor
import com.trackvoice.announcement.AnnouncementPlaybackPlanner
import com.trackvoice.announcement.ConnectedAudioDevice
import com.trackvoice.announcement.MusicVolumeManager
import com.trackvoice.announcement.InstalledVoice
import com.trackvoice.announcement.TtsEngine
import com.trackvoice.announcement.TtsState
import com.trackvoice.data.AnnouncementMode
import com.trackvoice.data.AppSettings
import com.trackvoice.data.DataStoreRepository
import com.trackvoice.data.UserSettings
import com.trackvoice.data.AudioDeviceSettings
import com.trackvoice.monetization.PremiumState
import com.trackvoice.monetization.forPremiumEntitlement
import android.content.Intent
import android.service.media.MediaBrowserService
import android.content.pm.PackageManager
import android.os.Build
import com.trackvoice.media.MediaEventType
import com.trackvoice.media.MediaMonitorUpdate
import com.trackvoice.media.MediaSessionMonitor
import com.trackvoice.media.PlaybackPauseToken
import com.trackvoice.media.PlaybackEvent
import com.trackvoice.media.TrackFingerprint
import com.trackvoice.media.PlaybackCollection
import com.trackvoice.media.PlaybackCollectionResolver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.launch

data class MediaUiState(
    val currentEvent: PlaybackEvent? = null,
    val effectiveEnabled: Boolean = true,
    val currentMode: AnnouncementMode = AnnouncementMode.SMART,
    val currentCollection: PlaybackCollection = PlaybackCollection.UNKNOWN,
    val lastDetectedAt: Long? = null,
)

data class DiagnosticsState(
    val notificationListenerConnected: Boolean = false,
    val activeSessionCount: Int = 0,
    val selectedSourcePackage: String? = null,
    val lastMetadataEventAt: Long? = null,
    val lastPlaybackStateEventAt: Long? = null,
    val lastAnnouncementAt: Long? = null,
    val lastAnnouncementSucceeded: Boolean? = null,
    val lastAnnouncementMessage: String = "아직 음성 안내가 실행되지 않았습니다.",
    val ttsState: TtsState = TtsState(),
)

class TrackVoiceController(
    context: Context,
    val repository: DataStoreRepository,
    private val premiumState: StateFlow<PremiumState>,
) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val ttsEngine = TtsEngine(appContext)
    private val audioFocusManager = AudioFocusManager(appContext)
    private val musicVolumeManager = MusicVolumeManager(appContext)
    private val outputDetector = AudioOutputDetector(appContext)
    private val audioDeviceMonitor = AudioDeviceMonitor(appContext, ::handleAudioDevices)
    private val duplicateSuppressor = DuplicateSuppressor()
    private val pendingFingerprints = mutableSetOf<String>()
    private var pendingJob: Job? = null
    private var monitor: MediaSessionMonitor? = null
    private var pausedPlayback: PlaybackPauseToken? = null
    private var speechGeneration = 0L
    private var screenAutoActivated = false
    private var deviceAutoActivated = false

    private val _mediaState = MutableStateFlow(MediaUiState())
    private val _diagnostics = MutableStateFlow(DiagnosticsState())
    private val _connectedAudioDevices = MutableStateFlow<List<ConnectedAudioDevice>>(emptyList())
    val mediaState: StateFlow<MediaUiState> = _mediaState.asStateFlow()
    val diagnostics: StateFlow<DiagnosticsState> = _diagnostics.asStateFlow()
    val connectedAudioDevices: StateFlow<List<ConnectedAudioDevice>> = _connectedAudioDevices.asStateFlow()
    val userSettings = repository.userSettings.stateIn(
        scope,
        SharingStarted.Eagerly,
        UserSettings(),
    )
    val appSettings = repository.appSettings.stateIn(
        scope,
        SharingStarted.Eagerly,
        emptyMap(),
    )
    val audioDeviceSettings = repository.audioDeviceSettings.stateIn(
        scope,
        SharingStarted.Eagerly,
        emptyMap(),
    )
    val ttsState: StateFlow<TtsState> = ttsEngine.state
    val installedVoices: StateFlow<List<InstalledVoice>> = ttsEngine.voices

    init {
        scope.launch(Dispatchers.IO) { repository.migrateTtsVolumeDefault() }
        scope.launch {
            userSettings.collectLatest { settings ->
                val effectiveSettings = settings.forPremiumEntitlement(premiumState.value.isPremium)
                _mediaState.value = _mediaState.value.copy(
                    effectiveEnabled = effectiveSettings.enabled || screenAutoActivated || deviceAutoActivated,
                )
            }
        }
        scope.launch {
            premiumState.collectLatest { state ->
                val settings = userSettings.value.forPremiumEntitlement(state.isPremium)
                _mediaState.value = _mediaState.value.copy(
                    effectiveEnabled = settings.enabled || screenAutoActivated || deviceAutoActivated,
                )
                evaluateDeviceAutoActivation(_connectedAudioDevices.value, audioDeviceSettings.value)
            }
        }
        discoverSupportedMediaApps()
        audioDeviceMonitor.start()
        scope.launch {
            audioDeviceSettings.collectLatest { evaluateDeviceAutoActivation(_connectedAudioDevices.value, it) }
        }
        scope.launch {
            ttsEngine.state.collectLatest { state ->
                _diagnostics.value = _diagnostics.value.copy(ttsState = state)
            }
        }
    }

    fun attachNotificationListener() {
        _diagnostics.value = _diagnostics.value.copy(notificationListenerConnected = true)
    }

    fun setNotificationAccessGranted(granted: Boolean) {
        if (granted) attachNotificationListener()
        else detachNotificationListener()
    }

    fun detachNotificationListener() {
        resumePausedPlayback()
        monitor?.stop()
        monitor = null
        _diagnostics.value = _diagnostics.value.copy(notificationListenerConnected = false)
        _mediaState.value = _mediaState.value.copy(
            currentEvent = null,
            currentMode = userSettings.value.defaultMode,
            lastDetectedAt = null,
        )
        _diagnostics.value = _diagnostics.value.copy(
            activeSessionCount = 0,
            selectedSourcePackage = null,
        )
    }

    fun attachMediaSessionMonitor(serviceContext: Context) {
        if (monitor != null) return
        monitor = MediaSessionMonitor(serviceContext, ::handleMediaUpdate).also { it.start() }
    }

    fun setAutoActivated(value: Boolean) {
        screenAutoActivated = value
        _mediaState.value = _mediaState.value.copy(
            effectiveEnabled = effectiveSettings().enabled || screenAutoActivated || deviceAutoActivated,
        )
    }

    fun refreshMediaSessions() = monitor?.refresh()

    fun refreshSupportedMediaApps() = discoverSupportedMediaApps()

    fun togglePlayback(): Boolean? = monitor?.toggleSelectedPlayback()

    fun isPlaybackPlaying(): Boolean? = monitor?.isSelectedPlaybackPlaying()

    fun setEnabled(enabled: Boolean) {
        scope.launch { repository.setEnabled(enabled) }
    }

    fun updateUserSettings(transform: (UserSettings) -> UserSettings) {
        scope.launch { repository.updateUserSettings(transform) }
    }

    fun updateAppSettings(settings: AppSettings) {
        scope.launch { repository.updateAppSettings(settings) }
    }

    fun updateAudioDeviceSettings(settings: AudioDeviceSettings) {
        scope.launch { repository.updateAudioDeviceSettings(settings) }
    }

    fun speakTest() {
        speak("트랙 3번, Glass Eyes. Radiohead.")
    }

    fun speak(text: String) {
        val generation = ++speechGeneration
        val settings = effectiveSettings()
        val plan = AnnouncementPlaybackPlanner.plan(settings)
        musicVolumeManager.restore()
        if (!plan.pauseBeforeAnnouncement) {
            // If a previous "announce then play" batch was interrupted, do not
            // carry its pause token into a new "play immediately" announcement.
            resumePausedPlayback()
        }
        // A new batch owns the focus lifecycle. This also releases an old
        // focus request when TTS replaces speech with QUEUE_FLUSH.
        audioFocusManager.abandon()
        if (pausedPlayback == null && plan.pauseBeforeAnnouncement) {
            pausedPlayback = monitor?.pauseSelectedIfPlaying()
        }
        if (plan.shouldDuckMusic) musicVolumeManager.duckTo(settings.musicDuckPercent)
        if (plan.requestAudioFocus && !audioFocusManager.request(plan.shouldDuckMusic)) {
            musicVolumeManager.restore()
            resumePausedPlayback()
            _diagnostics.value = _diagnostics.value.copy(
                lastAnnouncementAt = System.currentTimeMillis(),
                lastAnnouncementSucceeded = false,
                lastAnnouncementMessage = "오디오 포커스를 얻지 못해 안내를 건너뛰었습니다.",
            )
            return
        }
        ttsEngine.speak(text, settings) { success, message ->
            if (generation == speechGeneration) {
                if (plan.requestAudioFocus) audioFocusManager.abandon()
                musicVolumeManager.restore()
                resumePausedPlayback()
                _diagnostics.value = _diagnostics.value.copy(
                    lastAnnouncementAt = System.currentTimeMillis(),
                    lastAnnouncementSucceeded = success,
                    lastAnnouncementMessage = message,
                )
            }
        }
    }

    fun onScreenOff() {
        val settings = effectiveSettings()
        if (!settings.autoEnableOnScreenOff) return
        if (settings.bluetoothOnlyForAutoEnable && !outputDetector.hasBluetoothOutput()) return
        setAutoActivated(true)
    }

    fun onScreenOn() {
        if (userSettings.value.restoreEnabledWhenScreenOn) setAutoActivated(false)
    }

    fun close() {
        speechGeneration += 1
        pendingJob?.cancel()
        resumePausedPlayback()
        musicVolumeManager.restore()
        monitor?.stop()
        monitor = null
        audioDeviceMonitor.stop()
        ttsEngine.shutdown()
        audioFocusManager.abandon()
        scope.coroutineContext.cancel()
    }

    private fun handleAudioDevices(devices: List<ConnectedAudioDevice>) {
        _connectedAudioDevices.value = devices
        devices.forEach { device ->
            if (audioDeviceSettings.value[device.key] == null) {
                scope.launch {
                    repository.updateAudioDeviceSettings(
                        AudioDeviceSettings(device.key, device.name),
                    )
                }
            }
        }
        evaluateDeviceAutoActivation(devices, audioDeviceSettings.value)
    }

    private fun evaluateDeviceAutoActivation(
        devices: List<ConnectedAudioDevice>,
        settings: Map<String, AudioDeviceSettings>,
    ) {
        val shouldActivate = premiumState.value.isPremium && devices.any { device ->
            settings[device.key]?.let { it.enabled && it.autoEnable } == true
        }
        deviceAutoActivated = shouldActivate
        _mediaState.value = _mediaState.value.copy(
            effectiveEnabled = effectiveSettings().enabled || screenAutoActivated || deviceAutoActivated,
        )
    }

    private fun discoverSupportedMediaApps() {
        scope.launch(Dispatchers.IO) {
            val intent = Intent(MediaBrowserService.SERVICE_INTERFACE)
            val services = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                appContext.packageManager.queryIntentServices(
                    intent,
                    PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_ALL.toLong()),
                )
            } else {
                @Suppress("DEPRECATION")
                appContext.packageManager.queryIntentServices(intent, PackageManager.MATCH_ALL)
            }
            val mediaButtonIntent = Intent(Intent.ACTION_MEDIA_BUTTON)
            val receivers = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                appContext.packageManager.queryBroadcastReceivers(
                    mediaButtonIntent,
                    PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_ALL.toLong()),
                )
            } else {
                @Suppress("DEPRECATION")
                appContext.packageManager.queryBroadcastReceivers(mediaButtonIntent, PackageManager.MATCH_ALL)
            }
            (services.mapNotNull { it.serviceInfo?.applicationInfo } +
                receivers.mapNotNull { it.activityInfo?.applicationInfo })
                .distinctBy { it.packageName }
                .filter { info ->
                    info.packageName != appContext.packageName &&
                        appContext.packageManager.getLaunchIntentForPackage(info.packageName) != null
                }
                .forEach { info ->
                    val label = appContext.packageManager.getApplicationLabel(info).toString()
                    repository.ensureApp(info.packageName, label)
                }
            repository.currentAppSettings().keys.forEach { packageName ->
                val info = runCatching {
                    appContext.packageManager.getApplicationInfo(packageName, 0)
                }.getOrNull() ?: return@forEach
                val isSystemComponent = info.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM != 0 &&
                    appContext.packageManager.getLaunchIntentForPackage(packageName) == null
                if (isSystemComponent) repository.removeApp(packageName)
            }
        }
    }

    private fun resumePausedPlayback() {
        pausedPlayback?.let { monitor?.resumePlayback(it) }
        pausedPlayback = null
    }

    private fun handleMediaUpdate(update: MediaMonitorUpdate) {
        val event = update.selected?.event
        val settings = effectiveSettings()
        val app = event?.let { appSettings.value[it.sourcePackageName]?.forPremiumEntitlement(premiumState.value.isPremium) }
        val appGuideSettings = app?.takeIf { it.useCustomGuideSettings }
        val collection = event?.let {
            PlaybackCollectionResolver.applyFallback(
                detected = PlaybackCollectionResolver.resolve(it),
                fallback = appGuideSettings?.collectionFallback
                    ?: com.trackvoice.data.CollectionFallback.AUTO,
            )
        } ?: PlaybackCollection.UNKNOWN
        val configuredMode = appGuideSettings?.mode ?: settings.defaultMode
        val mode = when {
            configuredMode != AnnouncementMode.SMART -> configuredMode
            collection == PlaybackCollection.ALBUM -> settings.albumMode
            collection == PlaybackCollection.PLAYLIST -> settings.playlistMode
            collection == PlaybackCollection.ALGORITHMIC -> settings.algorithmMode
            else -> AnnouncementMode.TITLE_AND_ARTIST
        }
        _mediaState.value = _mediaState.value.copy(
            currentEvent = event,
            effectiveEnabled = settings.enabled || screenAutoActivated || deviceAutoActivated,
            currentMode = mode,
            currentCollection = collection,
            lastDetectedAt = update.observedAt.takeIf { event != null },
        )
        _diagnostics.value = _diagnostics.value.copy(
            activeSessionCount = update.activeSessionCount,
            selectedSourcePackage = event?.sourcePackageName,
            lastMetadataEventAt = update.observedAt.takeIf {
                event != null && (
                    update.eventType == MediaEventType.METADATA || update.eventType == MediaEventType.INITIAL
                )
            } ?: _diagnostics.value.lastMetadataEventAt,
            lastPlaybackStateEventAt = update.observedAt.takeIf {
                event != null && (
                    update.eventType == MediaEventType.PLAYBACK_STATE || update.eventType == MediaEventType.INITIAL
                )
            } ?: _diagnostics.value.lastPlaybackStateEventAt,
        )
        if (event != null && appSettings.value[event.sourcePackageName] == null) {
            scope.launch { repository.ensureApp(event.sourcePackageName, event.sourceAppName) }
        }

        if (event == null || !event.isPlaying) return
        scheduleAnnouncement(event)
    }

    private fun scheduleAnnouncement(event: PlaybackEvent) {
        val settings = effectiveSettings()
        val app = appSettings.value[event.sourcePackageName]
            ?.forPremiumEntitlement(premiumState.value.isPremium)
        val connectedDevices = _connectedAudioDevices.value
        if (connectedDevices.isNotEmpty() && connectedDevices.none { device ->
                audioDeviceSettings.value[device.key]?.enabled != false
            }
        ) return
        val externalOutput = outputDetector.hasExternalOutput()
        val decision = AnnouncementPolicy.decide(
            event = event,
            userSettings = settings,
            appSettings = app,
            effectiveEnabled = settings.enabled || screenAutoActivated || deviceAutoActivated,
            externalAudioOutput = externalOutput,
        )
        if (!decision.shouldAnnounce || decision.text == null) return

        val fingerprint = TrackFingerprint.announcement(event)
        if (fingerprint in pendingFingerprints) return
        if (!duplicateSuppressor.shouldAnnounce(event, settings.allowRepeatAnnouncements, System.currentTimeMillis())) return

        pendingJob?.cancel()
        pendingFingerprints += fingerprint
        pendingJob = scope.launch {
            try {
                if (decision.delayMs > 0L) delay(decision.delayMs)
                val current = _mediaState.value.currentEvent
                if (current == null || !current.isPlaying || TrackFingerprint.announcement(current) != fingerprint) return@launch

                duplicateSuppressor.markAnnounced(event, System.currentTimeMillis())
                speak(decision.text)
            } finally {
                pendingFingerprints -= fingerprint
            }
        }
    }

    private fun effectiveSettings(): UserSettings =
        userSettings.value.forPremiumEntitlement(premiumState.value.isPremium)
}

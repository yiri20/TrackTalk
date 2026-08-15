package com.trackvoice

import android.content.Context
import com.trackvoice.announcement.AnnouncementPolicy
import com.trackvoice.announcement.AnnouncementFormatter
import com.trackvoice.announcement.AnnouncementTrackMatcher
import com.trackvoice.announcement.AudioFocusManager
import com.trackvoice.announcement.AudioOutputDetector
import com.trackvoice.announcement.DuplicateSuppressor
import com.trackvoice.announcement.AudioDeviceMonitor
import com.trackvoice.announcement.AnnouncementPlaybackPlanner
import com.trackvoice.announcement.AnnouncementAudioTiming
import com.trackvoice.announcement.ConnectedAudioDevice
import com.trackvoice.announcement.MusicVolumeManager
import com.trackvoice.announcement.InstalledVoice
import com.trackvoice.announcement.TtsEngine
import com.trackvoice.announcement.TtsState
import com.trackvoice.announcement.shouldReadAlbum
import com.trackvoice.data.AnnouncementMode
import com.trackvoice.data.AnnouncementReadField
import com.trackvoice.data.AppGuideEnablementPolicy
import com.trackvoice.data.AppSettings
import com.trackvoice.data.DataStoreRepository
import com.trackvoice.data.PersistedAnnouncement
import com.trackvoice.data.UserSettings
import com.trackvoice.diagnostics.TrackTalkDebugLog
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
import com.trackvoice.media.PlaybackStatus
import com.trackvoice.media.TrackFingerprint
import com.trackvoice.media.TrackNumberSource
import com.trackvoice.media.PlaybackCollection
import com.trackvoice.media.PlaybackCollectionResolver
import com.trackvoice.media.AlbumTrackNumberResolver
import com.trackvoice.media.TemporalPlaybackContextResolver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

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

private data class QueuedMediaUpdate(
    val monitorGeneration: Long,
    val update: MediaMonitorUpdate,
)

private fun PlaybackEvent.logicalIdentity(): String = listOf(
    sourcePackageName,
    mediaId.orEmpty(),
    title.orEmpty().trim(),
    artist.orEmpty().trim(),
    album.orEmpty().trim(),
).joinToString("|")

private fun PersistedAnnouncement.logicalIdentity(): String = listOf(
    sourcePackageName,
    mediaId.orEmpty(),
    title.orEmpty().trim(),
    artist.orEmpty().trim(),
    album.orEmpty().trim(),
).joinToString("|")

private fun PlaybackEvent.toPersistedAnnouncement(announcedAt: Long): PersistedAnnouncement =
    PersistedAnnouncement(
        sourcePackageName = sourcePackageName,
        sourceAppName = sourceAppName,
        title = title,
        artist = artist,
        album = album,
        trackNumber = trackNumber,
        discNumber = discNumber,
        duration = duration,
        mediaId = mediaId,
        trackNumberReliable = trackNumberReliable,
        trackNumberSource = trackNumberSource.name,
        announcedAt = announcedAt,
    )

private fun PersistedAnnouncement.toPlaybackEvent(): PlaybackEvent = PlaybackEvent(
    sourcePackageName = sourcePackageName,
    sourceAppName = sourceAppName,
    title = title,
    artist = artist,
    album = album,
    albumArtist = null,
    trackNumber = trackNumber,
    totalTracks = null,
    discNumber = discNumber,
    duration = duration,
    mediaId = mediaId,
    playbackState = PlaybackStatus.PLAYING,
    playbackPosition = null,
    queue = emptyList(),
    observedAt = announcedAt,
    queueTitle = null,
    activeQueuePosition = null,
    queueOrderChanged = false,
    shuffleState = com.trackvoice.media.ShuffleState.UNKNOWN,
    trackNumberReliable = trackNumberReliable,
    trackNumberSource = runCatching { TrackNumberSource.valueOf(trackNumberSource) }
        .getOrDefault(TrackNumberSource.UNSPECIFIED),
)

class TrackVoiceController(
    context: Context,
    val repository: DataStoreRepository,
    private val premiumState: StateFlow<PremiumState>,
) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val persistenceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val persistenceMutex = Mutex()
    private val mediaUpdateQueue = Channel<QueuedMediaUpdate>(Channel.UNLIMITED)
    private val announcementHistoryReady = CompletableDeferred<Unit>()
    private val ttsEngine = TtsEngine(appContext)
    private val audioFocusManager = AudioFocusManager(appContext)
    private val musicVolumeManager = MusicVolumeManager(appContext)
    private val outputDetector = AudioOutputDetector(appContext)
    private val audioDeviceMonitor = AudioDeviceMonitor(appContext, ::handleAudioDevices)
    private val duplicateSuppressor = DuplicateSuppressor()
    private val temporalContextResolver = TemporalPlaybackContextResolver()
    private val pendingFingerprints = mutableSetOf<String>()
    private var pendingJob: Job? = null
    private var pendingAnnouncementEvent: PlaybackEvent? = null
    private var pendingAnnouncementToken = 0L
    private var preparedAnnouncement: PreparedAnnouncement? = null
    private var monitor: MediaSessionMonitor? = null
    private var pausedPlayback: PlaybackPauseToken? = null
    private var activeSpeechTrack: PlaybackEvent? = null
    private var lastAnnouncedTrack: PlaybackEvent? = null
    private var lastAnnouncedAt: Long = Long.MIN_VALUE
    private var lastAnnouncedSessionKey: String? = null
    private var selectedSessionKey: String? = null
    private var lastEventSessionKey: String? = null
    private var monitorGeneration = 0L
    private var logicalSessionGeneration = 0L
    private var speechGeneration = 0L
    private var monitorStartJob: Job? = null
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
        scope.launch {
            for (queuedUpdate in mediaUpdateQueue) {
                if (queuedUpdate.monitorGeneration != monitorGeneration) {
                    TrackTalkDebugLog.event(
                        "STALE_MEDIA_EVENT_IGNORED",
                        "eventSequenceNumber" to queuedUpdate.update.eventSequenceNumber,
                        "eventGeneration" to queuedUpdate.monitorGeneration,
                        "currentGeneration" to monitorGeneration,
                        "selectedSessionKey" to queuedUpdate.update.selectedSessionKey,
                    )
                    continue
                }
                processMediaUpdate(queuedUpdate.update)
            }
        }
        scope.launch(Dispatchers.IO) {
            val persisted = runCatching { repository.currentPersistedAnnouncement() }.getOrNull()
            withContext(Dispatchers.Main.immediate) {
                if (persisted != null) restorePersistedAnnouncement(persisted)
                TrackTalkDebugLog.event(
                    "DUPLICATE_STATE_READ",
                    "stage" to "RESTORE",
                    "historyPresent" to (persisted != null),
                    "logicalTrack" to persisted?.logicalIdentity(),
                    "announcedAt" to persisted?.announcedAt,
                )
                announcementHistoryReady.complete(Unit)
            }
        }
        scope.launch(Dispatchers.IO) { repository.migrateTtsVolumeDefault() }
        scope.launch(Dispatchers.IO) {
            repository.migrateContentReadDefaults()
            repository.migrateContentReadOrder()
            repository.migrateLegacyAppAnnouncementSettings()
        }
        scope.launch(Dispatchers.IO) { repository.migrateAudioOutputPolicy() }
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
        TrackTalkDebugLog.event(
            "SESSION_STATE_PRESERVATION",
            "stage" to "ATTACH",
            "duplicateHistoryPresent" to (lastAnnouncedTrack != null),
            "lastAnnouncedSessionKey" to lastAnnouncedSessionKey,
            "logicalSessionGeneration" to logicalSessionGeneration,
        )
    }

    fun setNotificationAccessGranted(granted: Boolean) {
        if (granted) attachNotificationListener()
        else detachNotificationListener(preservePlaybackHistory = false)
    }

    fun detachNotificationListener(preservePlaybackHistory: Boolean = true) {
        TrackTalkDebugLog.event(
            "SESSION_STATE_PRESERVATION",
            "stage" to "DETACH",
            "duplicateHistoryPreserved" to (preservePlaybackHistory && lastAnnouncedTrack != null),
            "lastAnnouncedSessionKey" to lastAnnouncedSessionKey,
        )
        speechGeneration += 1
        activeSpeechTrack = null
        cancelPendingAnnouncement()
        finishAnnouncementAudio()
        monitorGeneration += 1
        monitorStartJob?.cancel()
        monitorStartJob = null
        monitor?.stop()
        monitor = null
        selectedSessionKey = null
        lastEventSessionKey = null
        if (!preservePlaybackHistory) {
            lastAnnouncedTrack = null
            lastAnnouncedAt = Long.MIN_VALUE
            lastAnnouncedSessionKey = null
            duplicateSuppressor.clear()
            temporalContextResolver.reset()
            persistenceScope.launch {
                persistenceMutex.withLock { repository.clearPersistedAnnouncement() }
            }
        }
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
        if (monitor != null || monitorStartJob?.isActive == true) return
        val generation = ++monitorGeneration
        TrackTalkDebugLog.event(
            "SESSION_GENERATION_CHANGED",
            "stage" to "MONITOR_ATTACH_REQUESTED",
            "monitorGeneration" to generation,
            "logicalSessionGeneration" to logicalSessionGeneration,
        )
        monitorStartJob = scope.launch {
            announcementHistoryReady.await()
            if (monitor != null || generation != monitorGeneration) return@launch
            monitor = MediaSessionMonitor(
                context = serviceContext,
                onUpdate = { update ->
                    mediaUpdateQueue.trySend(QueuedMediaUpdate(generation, update))
                },
            ).also { it.start() }
            TrackTalkDebugLog.event(
                "SESSION_GENERATION_CHANGED",
                "stage" to "MONITOR_ATTACHED",
                "monitorGeneration" to generation,
                "logicalSessionGeneration" to logicalSessionGeneration,
            )
        }
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
        speak(AnnouncementFormatter.testText(effectiveSettings().voiceLanguage))
    }

    fun speak(text: String) {
        // A manual test should take over any delayed automatic announcement.
        cancelPendingAnnouncement()
        activeSpeechTrack = null
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
        // In pause-until-finished mode, a focus request can pause a media app
        // even when its session was too transient to return a resume token.
        // Do not make audio focus the only pause mechanism; without a token we
        // let TTS play over the current state and avoid leaving music stopped.
        val shouldRequestAudioFocus = plan.requestAudioFocus &&
            (!plan.pauseBeforeAnnouncement || pausedPlayback != null)
        if (shouldRequestAudioFocus && !audioFocusManager.request(plan.shouldDuckMusic)) {
            audioFocusManager.abandon()
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
                if (shouldRequestAudioFocus) audioFocusManager.abandon()
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

    private fun speakPrepared(
        text: String,
        track: PlaybackEvent,
        fingerprint: String,
        pendingToken: Long,
    ) {
        if (!isPendingAnnouncement(pendingToken, fingerprint)) return
        if (preparedAnnouncement?.fingerprint != fingerprint || preparedAnnouncement?.token != pendingToken) return
        preparedAnnouncement = null
        activeSpeechTrack = track
        val generation = ++speechGeneration
        val settings = effectiveSettings()
        ttsEngine.speak(text, settings) { success, message ->
            if (generation == speechGeneration) {
                activeSpeechTrack = null
                finishAnnouncementAudio()
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
        activeSpeechTrack = null
        lastAnnouncedTrack = null
        lastAnnouncedAt = Long.MIN_VALUE
        lastAnnouncedSessionKey = null
        selectedSessionKey = null
        duplicateSuppressor.clear()
        temporalContextResolver.reset()
        cancelPendingAnnouncement()
        finishAnnouncementAudio()
        monitorGeneration += 1
        monitorStartJob?.cancel()
        monitorStartJob = null
        mediaUpdateQueue.close()
        monitor?.stop()
        monitor = null
        audioDeviceMonitor.stop()
        ttsEngine.shutdown()
        audioFocusManager.abandon()
        persistenceScope.cancel()
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

    private fun processMediaUpdate(update: MediaMonitorUpdate) {
        val event = update.selected?.event
        val settings = effectiveSettings()
        val incomingSessionKey = update.selected?.sessionKey
        val previousSessionKey = lastEventSessionKey
        if (incomingSessionKey != null && incomingSessionKey != previousSessionKey) {
            logicalSessionGeneration += 1
            lastEventSessionKey = incomingSessionKey
            TrackTalkDebugLog.event(
                "SESSION_GENERATION_CHANGED",
                "stage" to "SELECTED_SESSION",
                "eventSequenceNumber" to update.eventSequenceNumber,
                "oldSessionKey" to previousSessionKey,
                "newSessionKey" to incomingSessionKey,
                "logicalSessionGeneration" to logicalSessionGeneration,
                "thread" to Thread.currentThread().name,
            )
        }
        TrackTalkDebugLog.event(
            "TRACK_CANDIDATE",
            "timestamp" to update.observedAt,
            "eventSequenceNumber" to update.eventSequenceNumber,
            "thread" to Thread.currentThread().name,
            "callbackThread" to update.callbackThread,
            "monitorGeneration" to monitorGeneration,
            "logicalSessionGeneration" to logicalSessionGeneration,
            "controllerSessionKey" to incomingSessionKey,
            "package" to event?.sourcePackageName,
            "logicalTrack" to event?.logicalIdentity(),
            "playbackState" to event?.playbackState,
        )
        val previousEvent = _mediaState.value.currentEvent
        val sameLogicalTrackAsPrevious = previousEvent != null && event != null &&
            AnnouncementTrackMatcher.matchesForDuplicateSuppression(
                expected = previousEvent,
                current = event,
                requireSameSource = true,
            )
        val history = lastAnnouncedTrack
        val sameLogicalTrackAsHistory = history != null && event != null &&
            AnnouncementTrackMatcher.matchesForDuplicateSuppression(
                expected = history,
                current = event,
                requireSameSource = true,
            )
        TrackTalkDebugLog.event(
            "TRACK_IDENTITY_COMPARISON",
            "eventSequenceNumber" to update.eventSequenceNumber,
            "logicalTrack" to event?.logicalIdentity(),
            "previousTrack" to previousEvent?.logicalIdentity(),
            "acceptedTrack" to history?.logicalIdentity(),
            "sameLogicalTrack" to sameLogicalTrackAsHistory,
            "sameLogicalSession" to (lastAnnouncedSessionKey != null && lastAnnouncedSessionKey == incomingSessionKey),
            "samePackage" to (history != null && event != null && history.sourcePackageName == event.sourcePackageName),
            "historyPresent" to (history != null),
            "thread" to Thread.currentThread().name,
        )
        val sameVisibleTrack = previousEvent != null && event != null &&
            AnnouncementTrackMatcher.matchesForDuplicateSuppression(
                expected = previousEvent,
                current = event,
                requireSameSource = true,
            )
        val sameAnnouncedTrack = lastAnnouncedTrack?.let { announced ->
            event?.let {
                AnnouncementTrackMatcher.matchesForDuplicateSuppression(
                    expected = announced,
                    current = it,
                    requireSameSource = true,
                )
            }
        } == true
        val sessionRefresh = event != null && sameAnnouncedTrack && (
            update.eventType == MediaEventType.INITIAL ||
                update.eventType == MediaEventType.ACTIVE_SESSIONS ||
                (selectedSessionKey != null && incomingSessionKey != null && selectedSessionKey != incomingSessionKey)
            )
        if (sessionRefresh) {
            TrackTalkDebugLog.event(
                "ACTIVE_SESSION_REFRESH",
                "oldSessionKey" to selectedSessionKey,
                "newSessionKey" to incomingSessionKey,
                "sameLogicalTrack" to true,
                "samePackage" to true,
                "eventType" to update.eventType,
            )
            TrackTalkDebugLog.event(
                "SESSION_STATE_PRESERVATION",
                "duplicateHistoryPreserved" to true,
                "reason" to "SAME_LOGICAL_TRACK_SESSION_REFRESH",
            )
        } else if (sameVisibleTrack && update.eventType == MediaEventType.ACTIVE_SESSIONS) {
            TrackTalkDebugLog.event(
                "ACTIVE_SESSION_REFRESH",
                "oldSessionKey" to selectedSessionKey,
                "newSessionKey" to incomingSessionKey,
                "sameLogicalTrack" to true,
                "samePackage" to true,
                "eventType" to update.eventType,
            )
        }
        selectedSessionKey = incomingSessionKey ?: selectedSessionKey
        val collection = event?.let { resolveCollection(it, incomingSessionKey) } ?: PlaybackCollection.UNKNOWN
        val mode = AnnouncementPolicy.resolveMode(collection, settings)
        TrackTalkDebugLog.event(
            "controller_media_update",
            "eventType" to update.eventType,
            "source" to event?.sourcePackageName,
            "mediaId" to event?.mediaId,
            "title" to event?.title,
            "artist" to event?.artist,
            "album" to event?.album,
            "collection" to collection,
            "mode" to mode,
            "playing" to event?.isPlaying,
            "observedAt" to update.observedAt,
            "eventSequenceNumber" to update.eventSequenceNumber,
            "logicalSessionGeneration" to logicalSessionGeneration,
        )
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

        if (event == null) {
            // A provider can briefly lose the selected snapshot while active
            // sessions are still present during a controller/queue refresh.
            // Preserve temporal playback evidence across that soft gap. Only
            // an empty active-session set is a hard context boundary.
            val noActiveSessions = update.activeSessionCount == 0
            if (noActiveSessions) {
                temporalContextResolver.reset()
                selectedSessionKey = null
            }
            TrackTalkDebugLog.event(
                "PLAYBACK_CONTEXT_BOUNDARY",
                "reason" to if (noActiveSessions) "NO_ACTIVE_SESSIONS" else "NO_SELECTED_SESSION_SOFT",
                "activeSessions" to update.activeSessionCount,
            )
            TrackTalkDebugLog.event("announcement_cancel", "reason" to "no_selected_event")
            cancelPendingAnnouncement()
            return
        }
        if (!event.isPlaying) {
            // A media session commonly reports PAUSED while audio focus is
            // moving to TTS. Once preparation or speech has been committed,
            // keep that batch alive; otherwise a real user pause still cancels
            // the delayed announcement as expected.
            val sameCommittedAnnouncement =
                (preparedAnnouncement != null || activeSpeechTrack != null) &&
                    (
                        preparedAnnouncement != null &&
                            pendingAnnouncementEvent?.let {
                                AnnouncementTrackMatcher.matches(it, event, requireSameSource = false)
                            } == true ||
                            activeSpeechTrack?.let {
                                AnnouncementTrackMatcher.matches(it, event, requireSameSource = false)
                            } == true
                        )
            if (!sameCommittedAnnouncement) cancelPendingAnnouncement()
            TrackTalkDebugLog.event(
                "announcement_pause_state",
                "committed" to sameCommittedAnnouncement,
                "mediaId" to event.mediaId,
                "title" to event.title,
            )
            return
        }
        scheduleAnnouncement(
            event = event,
            collection = collection,
            sessionKey = incomingSessionKey,
            sessionRefresh = sessionRefresh,
            eventSequenceNumber = update.eventSequenceNumber,
            logicalSessionGeneration = logicalSessionGeneration,
        )
    }

    private fun restorePersistedAnnouncement(persisted: PersistedAnnouncement) {
        val event = persisted.toPlaybackEvent()
        lastAnnouncedTrack = event
        lastAnnouncedAt = persisted.announcedAt
        lastAnnouncedSessionKey = null
        duplicateSuppressor.restoreAnnounced(event, persisted.announcedAt)
    }

    private fun resolveCollection(event: PlaybackEvent, sessionKey: String?): PlaybackCollection {
        val decision = temporalContextResolver.resolve(event, sessionKey)
        val evidence = decision.evidence
        TrackTalkDebugLog.event(
            "TEMPORAL_CONTEXT_INPUT",
            "sessionKey" to sessionKey,
            "trackIdentity" to decision.currentTrackIdentity,
            "previousTrackIdentity" to decision.previousTrackIdentity,
            "albumSameAsPrevious" to decision.albumSameAsPrevious,
            "artistSameAsPrevious" to decision.artistSameAsPrevious,
            "transitionKind" to decision.transitionKind,
            "previousPosition" to decision.previousPosition,
            "previousDuration" to decision.previousDuration,
            "sessionContinuous" to decision.sessionContinuous,
            "queueGeneration" to decision.queueGeneration,
            "queueChanged" to decision.queueChanged,
        )
        TrackTalkDebugLog.event(
            "TEMPORAL_CONTEXT_STATE_BEFORE",
            "sameAlbumNaturalTransitions" to decision.stateBeforeSameAlbumNaturalTransitions,
            "mixedAlbumTransitions" to decision.stateBeforeMixedNaturalTransitions,
            "currentHypothesis" to decision.stateBeforeHypothesis,
            "confidence" to decision.confidence,
        )
        TrackTalkDebugLog.event(
            "TEMPORAL_CONTEXT_UPDATE",
            "evidenceAdded" to decision.evidenceAdded,
            "evidenceRemoved" to decision.evidenceRemoved,
            "resetReason" to decision.resetReason,
        )
        TrackTalkDebugLog.event(
            "TEMPORAL_CONTEXT_STATE_AFTER",
            "hypothesis" to decision.stateAfterHypothesis,
            "confidence" to decision.confidence,
            "effectiveContext" to decision.collection,
        )
        TrackTalkDebugLog.event(
            "PLAYBACK_CONTEXT_EVIDENCE",
            "source" to event.sourcePackageName,
            "mediaId" to event.mediaId,
            "queueTitleSignal" to evidence.queueTitleSignal,
            "queueTitle" to event.queueTitle,
            "queueSize" to evidence.queueSize,
            "activeQueuePosition" to evidence.activeQueuePosition,
            "currentAlbumPresent" to evidence.currentAlbumPresent,
            "queueAlbums" to evidence.queueAlbums.joinToString(",", prefix = "[", postfix = "]"),
            "queueItemsWithAlbums" to evidence.queueItemsWithAlbums,
            "queueItemsWithTrackNumbers" to evidence.queueItemsWithTrackNumbers,
            "canonicalAlbumQueue" to evidence.hasCanonicalAlbumQueue,
            "shuffleState" to evidence.shuffleState,
        )
        TrackTalkDebugLog.event(
            "PLAYBACK_CONTEXT_DECISION",
            "source" to event.sourcePackageName,
            "mediaId" to event.mediaId,
            "detected" to decision.collection,
            "detectedReason" to decision.reason,
            "final" to decision.collection,
            "stateReset" to decision.stateReset,
            "transition" to decision.transition,
            "naturalTransition" to decision.naturalTransition,
            "sameAlbumTransitions" to decision.sameAlbumNaturalTransitions,
            "mixedTransitions" to decision.mixedNaturalTransitions,
        )
        return decision.collection
    }

    private fun scheduleAnnouncement(
        event: PlaybackEvent,
        collection: PlaybackCollection,
        sessionKey: String?,
        sessionRefresh: Boolean,
        eventSequenceNumber: Long = 0L,
        logicalSessionGeneration: Long = 0L,
    ) {
        val settings = effectiveSettings()
        val app = appSettingsFor(event)
        val connectedDevices = _connectedAudioDevices.value
        if (connectedDevices.isNotEmpty() && connectedDevices.none { device ->
                audioDeviceSettings.value[device.key]?.enabled != false
            }
        ) {
            cancelPendingAnnouncement()
            return
        }
        val externalOutput = outputDetector.hasExternalOutput()
        val decision = AnnouncementPolicy.decide(
            event = event,
            userSettings = settings,
            appSettings = app,
            effectiveEnabled = settings.enabled || screenAutoActivated || deviceAutoActivated,
            externalAudioOutput = externalOutput,
            collectionOverride = collection,
        )
        val configuration = AnnouncementPolicy.resolveConfiguration(settings, collection)
        TrackTalkDebugLog.event(
            "CONTENT_TYPE",
            "mediaId" to event.mediaId,
            "resolved" to collection,
            "source" to configuration.source,
        )
        TrackTalkDebugLog.event(
            "ANNOUNCEMENT_CONFIG",
            "source" to configuration.source,
            "selected" to decision.formatOptions.orderedFields?.joinToString(","),
            "legacyOrder" to decision.formatOptions.announcementOrder,
            "mode" to decision.mode,
        )
        TrackTalkDebugLog.event(
            "announcement_policy",
            "mediaId" to event.mediaId,
            "title" to event.title,
            "collection" to decision.collection,
            "mode" to decision.mode,
            "appEnabled" to app.enabled,
            "appOverride" to app.enabledOverride,
            "appDefault" to AppGuideEnablementPolicy.defaultEnabled(
                event.sourcePackageName,
                event.sourceAppName,
            ),
            "shouldAnnounce" to decision.shouldAnnounce,
            "skipReason" to decision.skipReason,
            "delayMs" to decision.delayMs,
        )
        TrackTalkDebugLog.event(
            "ANNOUNCEMENT_DECISION",
            "stage" to "INITIAL",
            "mediaId" to event.mediaId,
            "shouldAnnounce" to decision.shouldAnnounce,
            "skipReason" to decision.skipReason,
            "textAvailable" to (decision.text != null),
        )
        if (decision.shouldAnnounce) {
            logAnnouncementComponents(
                event = event,
                decision = decision,
                action = if (needsMetadataSettlement(event, decision)) "WAIT_FOR_METADATA" else "READY",
            )
        }
        if (!decision.shouldAnnounce || decision.text == null) {
            cancelPendingAnnouncement()
            return
        }

        // Media apps often emit a transient PAUSED/metadata-cleared event
        // while audio focus moves to TTS. Do not schedule the same track again
        // while its announcement is still being spoken.
        if (activeSpeechTrack?.let { AnnouncementTrackMatcher.matches(it, event, requireSameSource = false) } == true) {
            TrackTalkDebugLog.event(
                "duplicate_suppressed",
                "reason" to "active_speech",
                "mediaId" to event.mediaId,
                "sameLogicalTrack" to true,
                "sameLogicalSession" to (lastAnnouncedSessionKey != null && lastAnnouncedSessionKey == sessionKey),
                "samePackage" to (activeSpeechTrack?.sourcePackageName == event.sourcePackageName),
                "historyPresent" to (lastAnnouncedTrack != null),
                "eventSequenceNumber" to eventSequenceNumber,
                "logicalSessionGeneration" to logicalSessionGeneration,
            )
            return
        }

        // MediaSession can emit several callbacks for one playback start:
        // queue description, canonical metadata, focus-induced pause/resume,
        // and a refreshed media ID. The suppressor handles history, while
        // this latch prevents the current playback epoch from being scheduled
        // again after the first speech has already completed.
        val now = System.currentTimeMillis()
        if (
            lastAnnouncedTrack?.let {
                AnnouncementTrackMatcher.matchesForDuplicateSuppression(
                    expected = it,
                    current = event,
                    requireSameSource = true,
                )
            } == true &&
            (!settings.allowRepeatAnnouncements || now - lastAnnouncedAt < REPEAT_ANNOUNCEMENT_COOLDOWN_MS)
        ) {
            TrackTalkDebugLog.event(
                "duplicate_suppressed",
                "reason" to if (sessionRefresh) "SAME_LOGICAL_TRACK_SESSION_REFRESH" else "completed_track",
                "mediaId" to event.mediaId,
                "lastSessionKey" to lastAnnouncedSessionKey,
                "sessionKey" to sessionKey,
                "sameLogicalTrack" to true,
                "sameLogicalSession" to (lastAnnouncedSessionKey != null && lastAnnouncedSessionKey == sessionKey),
                "samePackage" to (lastAnnouncedTrack?.sourcePackageName == event.sourcePackageName),
                "historyPresent" to true,
                "eventSequenceNumber" to eventSequenceNumber,
                "logicalSessionGeneration" to logicalSessionGeneration,
            )
            return
        }

        val fingerprint = TrackFingerprint.announcement(event)
        // Metadata and queue callbacks for one track can arrive while the
        // first announcement is still waiting for its settlement delay. The
        // full fingerprint may change when album/track-number metadata is
        // filled in, so use the track identity as the pending key too. This
        // prevents a metadata update from cancelling and rescheduling the
        // same announcement before the first one has spoken.
        if (pendingAnnouncementEvent?.let { AnnouncementTrackMatcher.matches(it, event, requireSameSource = false) } == true) {
            val previousPending = pendingAnnouncementEvent
            pendingAnnouncementEvent = event
            val added = newlyAvailableComponents(previousPending, event, decision)
            TrackTalkDebugLog.event(
                "metadata_enriched",
                "sameTrack" to true,
                "mediaId" to event.mediaId,
                "added" to added.joinToString(",", prefix = "[", postfix = "]"),
                "eventSequenceNumber" to eventSequenceNumber,
                "logicalSessionGeneration" to logicalSessionGeneration,
            )
            logAnnouncementComponents(
                event = event,
                decision = decision,
                action = if (needsMetadataSettlement(event, decision)) "WAIT_FOR_METADATA" else "READY",
            )
            return
        }
        if (fingerprint in pendingFingerprints) {
            TrackTalkDebugLog.event(
                "duplicate_suppressed",
                "reason" to "pending_fingerprint",
                "mediaId" to event.mediaId,
                "sameLogicalTrack" to true,
                "sameLogicalSession" to (lastAnnouncedSessionKey != null && lastAnnouncedSessionKey == sessionKey),
                "samePackage" to (pendingAnnouncementEvent?.sourcePackageName == event.sourcePackageName),
                "historyPresent" to (lastAnnouncedTrack != null),
                "eventSequenceNumber" to eventSequenceNumber,
                "logicalSessionGeneration" to logicalSessionGeneration,
            )
            return
        }
        TrackTalkDebugLog.event(
            "DUPLICATE_STATE_READ",
            "stage" to "DECISION",
            "historyPresent" to (lastAnnouncedTrack != null),
            "logicalTrack" to event.logicalIdentity(),
            "sameLogicalTrack" to (lastAnnouncedTrack?.let {
                AnnouncementTrackMatcher.matchesForDuplicateSuppression(it, event, requireSameSource = true)
            } == true),
            "sameLogicalSession" to (lastAnnouncedSessionKey != null && lastAnnouncedSessionKey == sessionKey),
            "samePackage" to (lastAnnouncedTrack?.sourcePackageName == event.sourcePackageName),
            "eventSequenceNumber" to eventSequenceNumber,
            "logicalSessionGeneration" to logicalSessionGeneration,
        )
        if (!duplicateSuppressor.shouldAnnounce(
            event = event,
            allowRepeat = settings.allowRepeatAnnouncements,
            now = System.currentTimeMillis(),
            announcementText = decision.text,
        )
        ) {
            TrackTalkDebugLog.event(
                "duplicate_suppressed",
                "reason" to "history",
                "mediaId" to event.mediaId,
                "sameLogicalTrack" to (lastAnnouncedTrack?.let {
                    AnnouncementTrackMatcher.matchesForDuplicateSuppression(it, event, requireSameSource = true)
                } == true),
                "sameLogicalSession" to (lastAnnouncedSessionKey != null && lastAnnouncedSessionKey == sessionKey),
                "samePackage" to (lastAnnouncedTrack?.sourcePackageName == event.sourcePackageName),
                "historyPresent" to (lastAnnouncedTrack != null),
                "eventSequenceNumber" to eventSequenceNumber,
                "logicalSessionGeneration" to logicalSessionGeneration,
            )
            cancelPendingAnnouncement()
            return
        }

        cancelPendingAnnouncement()
        pendingAnnouncementEvent = event
        val pendingToken = ++pendingAnnouncementToken
        val scheduledDelayMs = maxOf(
            decision.delayMs,
            if (needsMetadataSettlement(event, decision)) METADATA_SETTLE_DELAY_MS else 0L,
        )
        val preparationDelayMs = AnnouncementAudioTiming.preparationDelayMs(
            scheduledDelayMs = scheduledDelayMs,
            decisionDelayMs = decision.delayMs,
        )
        TrackTalkDebugLog.event(
            "announcement_scheduled",
            "mediaId" to event.mediaId,
            "title" to event.title,
            "decisionDelayMs" to decision.delayMs,
            "scheduledDelayMs" to scheduledDelayMs,
            "preparationDelayMs" to preparationDelayMs,
            "observedAt" to event.observedAt,
            "eventSequenceNumber" to eventSequenceNumber,
            "logicalSessionGeneration" to logicalSessionGeneration,
        )
        pendingFingerprints += fingerprint
        pendingJob = scope.launch {
            try {
                if (preparationDelayMs > 0L) delay(preparationDelayMs)
                if (!isPendingAnnouncement(pendingToken, fingerprint)) return@launch
                if (preparedAnnouncement == null) {
                    // Mark the batch before requesting audio focus or pausing
                    // the player. Those calls synchronously/asynchronously
                    // produce a PAUSED callback; without this marker that
                    // callback cancels the very job that caused it.
                    preparedAnnouncement = PreparedAnnouncement(fingerprint, pendingToken)
                    if (!prepareAnnouncementAudio(settings, event)) {
                        if (preparedAnnouncement?.fingerprint == fingerprint) {
                            releasePreparedAnnouncement()
                        }
                        return@launch
                    }
                    if (!isPendingAnnouncement(pendingToken, fingerprint)) {
                        finishAnnouncementAudio()
                        return@launch
                    }
                }
                val remainingDelayMs = scheduledDelayMs - preparationDelayMs
                if (remainingDelayMs > 0L) delay(remainingDelayMs)
                if (!isPendingAnnouncement(pendingToken, fingerprint)) return@launch
                val current = _mediaState.value.currentEvent
                val currentMatches = current != null && AnnouncementTrackMatcher.matches(event, current)
                val committedCurrent = currentMatches && (
                    current?.isPlaying == true ||
                        preparedAnnouncement?.fingerprint == fingerprint
                    )
                if (!committedCurrent) return@launch

                // Settings and the audio route can change while the metadata
                // settlement/announcement delay is running. Re-read both at
                // the last possible moment so enabling speaker suppression or
                // switching from Bluetooth to the phone speaker cannot leak
                // a queued announcement.
                val currentSettings = effectiveSettings()
                val currentApp = appSettingsFor(current)
                val currentDecision = AnnouncementPolicy.decide(
                    event = current,
                    userSettings = currentSettings,
                    appSettings = currentApp,
                    effectiveEnabled = currentSettings.enabled || screenAutoActivated || deviceAutoActivated,
                    externalAudioOutput = outputDetector.hasExternalOutput(),
                    collectionOverride = _mediaState.value.currentCollection,
                )
                TrackTalkDebugLog.event(
                    "ANNOUNCEMENT_DECISION",
                    "stage" to "FINAL",
                    "mediaId" to current.mediaId,
                    "shouldAnnounce" to currentDecision.shouldAnnounce,
                    "skipReason" to currentDecision.skipReason,
                    "textAvailable" to (currentDecision.text != null),
                )
                if (!currentDecision.shouldAnnounce || currentDecision.text == null) return@launch

                logAnnouncementComponents(
                    event = current,
                    decision = currentDecision,
                    action = "FINAL",
                )

                val announcedAt = System.currentTimeMillis()
                lastAnnouncedTrack = current
                lastAnnouncedAt = announcedAt
                lastAnnouncedSessionKey = sessionKey
                duplicateSuppressor.markAnnounced(
                    event = current,
                    now = announcedAt,
                    announcementText = currentDecision.text,
                )
                TrackTalkDebugLog.event(
                    "DUPLICATE_STATE_WRITE",
                    "stage" to "ACCEPTED",
                    "historyPresent" to true,
                    "logicalTrack" to current.logicalIdentity(),
                    "announcedAt" to announcedAt,
                    "eventSequenceNumber" to eventSequenceNumber,
                    "logicalSessionGeneration" to logicalSessionGeneration,
                )
                persistenceScope.launch {
                    persistenceMutex.withLock {
                        repository.savePersistedAnnouncement(current.toPersistedAnnouncement(announcedAt))
                    }
                }
                TrackTalkDebugLog.event(
                    "TTS_REQUESTED",
                    "mediaId" to current.mediaId,
                    "title" to current.title,
                    "artist" to current.artist,
                    "album" to current.album,
                    "observedAt" to current.observedAt,
                    "elapsedSinceObservedMs" to (announcedAt - current.observedAt),
                    "collection" to currentDecision.collection,
                    "mode" to currentDecision.mode,
                    "eventSequenceNumber" to eventSequenceNumber,
                    "logicalSessionGeneration" to logicalSessionGeneration,
                )
                speakPrepared(currentDecision.text, current, fingerprint, pendingToken)
            } finally {
                if (pendingAnnouncementToken == pendingToken) {
                    pendingFingerprints -= fingerprint
                    pendingAnnouncementEvent = null
                    if (preparedAnnouncement?.fingerprint == fingerprint) {
                        releasePreparedAnnouncement()
                    }
                }
            }
        }
    }

    /**
     * Resolve app enablement synchronously for the current media event. A new
     * app can emit its first MediaSession callback before ensureApp() finishes;
     * using a category-based fallback here prevents that race from bypassing
     * the default-off policy at runtime.
     */
    private fun appSettingsFor(event: PlaybackEvent): AppSettings =
        appSettings.value[event.sourcePackageName]
            ?: AppSettings(
                packageName = event.sourcePackageName,
                appName = event.sourceAppName,
                enabled = AppGuideEnablementPolicy.defaultEnabled(
                    event.sourcePackageName,
                    event.sourceAppName,
                ),
            )

    private fun needsMetadataSettlement(
        event: PlaybackEvent,
        decision: com.trackvoice.announcement.AnnouncementDecision,
    ): Boolean = missingAnnouncementComponents(event, decision).isNotEmpty()

    private fun logAnnouncementComponents(
        event: PlaybackEvent,
        decision: com.trackvoice.announcement.AnnouncementDecision,
        action: String,
    ) {
        val required = requiredAnnouncementComponents(event, decision)
        val available = required.filter { componentAvailable(event, decision, it) }
        val missing = required.filterNot(available::contains)
        TrackTalkDebugLog.event(
            "METADATA_AVAILABILITY",
            "trackIdentity" to TrackFingerprint.announcementBase(event),
            "mediaId" to event.mediaId,
            "collection" to decision.collection,
            "mode" to decision.mode,
            "selected" to decision.formatOptions.orderedFields?.joinToString(","),
            "required" to required.joinToString(",", prefix = "[", postfix = "]"),
            "available" to available.joinToString(",", prefix = "[", postfix = "]"),
            "missing" to missing.joinToString(",", prefix = "[", postfix = "]"),
            "trackNumber" to event.trackNumber,
            "trackNumberSource" to event.trackNumberSource,
            "action" to action,
        )
    }

    private fun missingAnnouncementComponents(
        event: PlaybackEvent,
        decision: com.trackvoice.announcement.AnnouncementDecision,
    ): List<String> {
        val required = requiredAnnouncementComponents(event, decision)
        return required.filterNot { componentAvailable(event, decision, it) }
    }

    private fun newlyAvailableComponents(
        previous: PlaybackEvent?,
        current: PlaybackEvent,
        decision: com.trackvoice.announcement.AnnouncementDecision,
    ): List<String> {
        if (previous == null) return emptyList()
        return requiredAnnouncementComponents(current, decision).filter { component ->
            !componentAvailable(previous, decision, component) && componentAvailable(current, decision, component)
        }
    }

    private fun requiredAnnouncementComponents(
        event: PlaybackEvent,
        decision: com.trackvoice.announcement.AnnouncementDecision,
    ): List<String> {
        val mode = when (decision.mode) {
            AnnouncementMode.SMART -> when (decision.collection) {
                PlaybackCollection.ALBUM -> AnnouncementMode.ALBUM
                PlaybackCollection.PLAYLIST -> AnnouncementMode.PLAYLIST
                PlaybackCollection.ALGORITHMIC,
                PlaybackCollection.UNKNOWN,
                -> AnnouncementMode.TITLE_AND_ARTIST
            }
            else -> decision.mode
        }
        val options = decision.formatOptions
        options.orderedFields?.let { orderedFields ->
            return orderedFields
                .filter { field ->
                    field != AnnouncementReadField.ALBUM || options.shouldReadAlbum(event, decision.collection)
                }
                .map(AnnouncementReadField::name)
        }
        return when (mode) {
            AnnouncementMode.TITLE_ONLY -> listOf("TITLE")
            AnnouncementMode.TITLE_AND_ARTIST -> buildList {
                if (options.readTitle) add("TITLE")
                if (options.readArtist) add("ARTIST")
            }
            AnnouncementMode.ALBUM -> buildList {
                if (options.shouldReadAlbum(event, decision.collection)) add("ALBUM")
                if (options.readTrackNumber) add("TRACK_NUMBER")
                if (options.readTitle) add("TITLE")
                if (options.readArtist) add("ARTIST")
            }
            AnnouncementMode.PLAYLIST -> buildList {
                if (options.readCollection) add("COLLECTION")
                if (options.shouldReadAlbum(event, decision.collection)) add("ALBUM")
                if (options.readTrackNumber) add("TRACK_NUMBER")
                if (options.readTitle) add("TITLE")
                if (options.readArtist) add("ARTIST")
            }
            AnnouncementMode.SMART -> error("resolved above")
        }
    }

    private fun componentAvailable(
        event: PlaybackEvent,
        decision: com.trackvoice.announcement.AnnouncementDecision,
        component: String,
    ): Boolean = when (component) {
        "TITLE" -> event.hasTitle
        "ARTIST" -> !event.artist.isNullOrBlank()
        "ALBUM" -> !event.album.isNullOrBlank()
        "COLLECTION" -> !event.queueTitle.isNullOrBlank() &&
            !PlaybackCollectionResolver.isGenericQueueTitle(event.queueTitle)
        "TRACK_NUMBER" -> AlbumTrackNumberResolver.resolve(event) != null
        else -> false
    }

    /**
     * Applies music ducking/pausing before the delayed announcement job runs.
     * MediaSession callbacks arrive after playback has started, so doing this
     * here removes the additional gap before TTS takes over.
     */
    private fun prepareAnnouncementAudio(settings: UserSettings, event: PlaybackEvent? = pendingAnnouncementEvent): Boolean {
        val preparationStartedAt = System.currentTimeMillis()
        val plan = AnnouncementPlaybackPlanner.plan(settings)
        musicVolumeManager.restore()
        if (!plan.pauseBeforeAnnouncement) {
            // If a previous "announce then play" batch was interrupted, do not
            // carry its pause token into a new "play immediately" announcement.
            resumePausedPlayback()
        }
        audioFocusManager.abandon()
        if (pausedPlayback == null && plan.pauseBeforeAnnouncement) {
            pausedPlayback = monitor?.pauseSelectedIfPlaying()
        }
        if (plan.shouldDuckMusic) musicVolumeManager.duckTo(settings.musicDuckPercent)

        TrackTalkDebugLog.event(
            "audio_protection",
            "mediaId" to event?.mediaId,
            "title" to event?.title,
            "pauseBeforeAnnouncement" to plan.pauseBeforeAnnouncement,
            "pauseToken" to (pausedPlayback != null),
            "duck" to plan.shouldDuckMusic,
            "duckPercent" to settings.musicDuckPercent.takeIf { plan.shouldDuckMusic },
            "elapsedSinceObservedMs" to event?.let { preparationStartedAt - it.observedAt },
        )

        // In pause-until-finished mode, a focus request can pause a media app
        // even when its session was too transient to return a resume token.
        val shouldRequestAudioFocus = plan.requestAudioFocus &&
            (!plan.pauseBeforeAnnouncement || pausedPlayback != null)
        if (shouldRequestAudioFocus && !audioFocusManager.request(plan.shouldDuckMusic)) {
            finishAnnouncementAudio()
            _diagnostics.value = _diagnostics.value.copy(
                lastAnnouncementAt = System.currentTimeMillis(),
                lastAnnouncementSucceeded = false,
                lastAnnouncementMessage = "?ㅻ뵒???ъ빱?ㅻ? ?살? 紐삵빐 ?덈궡瑜?嫄대꼫?곗뿀?듬땲??",
            )
            return false
        }
        TrackTalkDebugLog.event(
            "audio_protection_ready",
            "mediaId" to event?.mediaId,
            "elapsedSinceObservedMs" to event?.let { System.currentTimeMillis() - it.observedAt },
        )
        return true
    }

    private fun cancelPendingAnnouncement() {
        pendingAnnouncementToken += 1
        pendingJob?.cancel()
        pendingJob = null
        pendingAnnouncementEvent = null
        pendingFingerprints.clear()
        releasePreparedAnnouncement()
    }

    private fun isPendingAnnouncement(token: Long, fingerprint: String): Boolean =
        pendingAnnouncementToken == token && fingerprint in pendingFingerprints

    private fun isSameAnnouncementTrack(
        expected: PlaybackEvent,
        current: PlaybackEvent,
        requireSameSource: Boolean = true,
    ): Boolean = AnnouncementTrackMatcher.matches(expected, current, requireSameSource)

    private fun releasePreparedAnnouncement() {
        if (preparedAnnouncement == null) return
        preparedAnnouncement = null
        finishAnnouncementAudio()
    }

    private fun finishAnnouncementAudio() {
        TrackTalkDebugLog.event("playback_restore")
        audioFocusManager.abandon()
        musicVolumeManager.restore()
        resumePausedPlayback()
    }

    private data class PreparedAnnouncement(
        val fingerprint: String,
        val token: Long,
    )

    private companion object {
        const val METADATA_SETTLE_DELAY_MS = 250L
        const val REPEAT_ANNOUNCEMENT_COOLDOWN_MS = 30_000L
    }

    private fun effectiveSettings(): UserSettings =
        userSettings.value.forPremiumEntitlement(premiumState.value.isPremium)
}

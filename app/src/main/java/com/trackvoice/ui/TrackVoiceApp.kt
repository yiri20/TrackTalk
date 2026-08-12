package com.trackvoice.ui

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Podcasts
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.core.graphics.drawable.toBitmap
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.trackvoice.TrackVoiceViewModel
import com.trackvoice.announcement.InstalledVoice
import com.trackvoice.announcement.TtsStatus
import com.trackvoice.data.AnnouncementMode
import com.trackvoice.data.AnnouncementTiming
import com.trackvoice.data.AppSettings
import com.trackvoice.data.AppCategory
import com.trackvoice.data.AppLanguage
import com.trackvoice.data.categorizeApp
import com.trackvoice.data.GenderFilter
import com.trackvoice.data.UserSettings
import com.trackvoice.data.VoiceLanguage
import com.trackvoice.data.MusicTreatment
import com.trackvoice.data.TrackStartBehavior
import com.trackvoice.data.AudioDeviceSettings
import com.trackvoice.announcement.ConnectedAudioDevice
import com.trackvoice.data.MAX_MUSIC_DUCK_PERCENT
import com.trackvoice.data.MIN_MUSIC_DUCK_PERCENT
import com.trackvoice.media.PlaybackEvent
import com.trackvoice.media.PlaybackStatus
import com.trackvoice.media.PlaybackCollection
import com.trackvoice.monetization.PremiumState
import com.trackvoice.monetization.forPremiumEntitlement
import com.trackvoice.monetization.promoCodeRedeemUrl
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class AppSection {
    HOME,
    GENERAL,
    APPS,
    VOICE,
    DIAGNOSTICS,
}

private val TrackVoiceCardShape = RoundedCornerShape(14.dp)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackVoiceApp(viewModel: TrackVoiceViewModel, activity: Activity) {
    val settings by viewModel.userSettings.collectAsStateWithLifecycle()
    val appSettings by viewModel.appSettings.collectAsStateWithLifecycle()
    val mediaState by viewModel.mediaState.collectAsStateWithLifecycle()
    val diagnostics by viewModel.diagnostics.collectAsStateWithLifecycle()
    val voices by viewModel.installedVoices.collectAsStateWithLifecycle()
    val connectedDevices by viewModel.connectedAudioDevices.collectAsStateWithLifecycle()
    val deviceSettings by viewModel.audioDeviceSettings.collectAsStateWithLifecycle()
    val premiumState by viewModel.premiumState.collectAsStateWithLifecycle()
    val effectiveSettings = settings.forPremiumEntitlement(premiumState.isPremium)
    val strings = TrackTalkStrings.forLanguage(settings.appLanguage, Locale.getDefault().language)
    var selectedSectionName by rememberSaveable { mutableStateOf(AppSection.HOME.name) }
    var showPremiumDialog by rememberSaveable { mutableStateOf(false) }
    val selectedSection = AppSection.valueOf(selectedSectionName)
    val context = LocalContext.current
    var notificationPermissionGranted by remember {
        mutableStateOf(
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS,
                ) == PackageManager.PERMISSION_GRANTED,
        )
    }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> notificationPermissionGranted = granted }

    CompositionLocalProvider(LocalTrackTalkStrings provides strings) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                TopAppBar(
                    title = { Text(strings.sectionTitle(selectedSection)) },
                    actions = {
                        if (!premiumState.isPremium) {
                            TextButton(onClick = { showPremiumDialog = true }) {
                                Icon(Icons.Default.Star, contentDescription = null)
                                Spacer(Modifier.width(4.dp))
                                Text(strings.plus)
                            }
                        }
                        StatusBadge(
                            enabled = mediaState.effectiveEnabled,
                            notificationAccess = diagnostics.notificationListenerConnected,
                        )
                    },
                )
            },
            bottomBar = {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface, tonalElevation = 0.dp) {
                    NavigationItem(AppSection.HOME, Icons.Default.Home, selectedSection) { selectedSectionName = it.name }
                    NavigationItem(AppSection.GENERAL, Icons.Default.Tune, selectedSection) { selectedSectionName = it.name }
                    NavigationItem(AppSection.APPS, Icons.Default.Apps, selectedSection) { selectedSectionName = it.name }
                    NavigationItem(AppSection.VOICE, Icons.Default.RecordVoiceOver, selectedSection) { selectedSectionName = it.name }
                    NavigationItem(AppSection.DIAGNOSTICS, Icons.Default.BugReport, selectedSection) { selectedSectionName = it.name }
                }
            },
        ) { padding ->
            SurfaceContent(padding) {
                when (selectedSection) {
                AppSection.HOME -> HomeScreen(
                    settings = effectiveSettings,
                    mediaEvent = mediaState.currentEvent,
                    currentMode = mediaState.currentMode,
                    currentCollection = mediaState.currentCollection,
                    lastDetectedAt = mediaState.lastDetectedAt,
                    effectiveEnabled = mediaState.effectiveEnabled,
                    notificationAccess = diagnostics.notificationListenerConnected,
                    notificationPermissionGranted = notificationPermissionGranted,
                    premiumState = premiumState,
                    onToggle = viewModel.controller::setEnabled,
                    onTogglePlayback = { viewModel.controller.togglePlayback() },
                    onOpenPermission = {
                        context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                    },
                    onRequestNotificationPermission = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    },
                    onOpenGeneral = { selectedSectionName = AppSection.GENERAL.name },
                    onOpenPremium = { showPremiumDialog = true },
                )

                AppSection.GENERAL -> GeneralSettingsScreen(
                    settings = effectiveSettings,
                    connectedDevices = connectedDevices,
                    deviceSettings = deviceSettings,
                    isPremium = premiumState.isPremium,
                    onUpdate = viewModel.controller::updateUserSettings,
                    onUpdateDevice = viewModel.controller::updateAudioDeviceSettings,
                    onOpenPremium = { showPremiumDialog = true },
                )

                AppSection.APPS -> AppSettingsScreen(
                    apps = appSettings.values.sortedBy { it.appName.lowercase(Locale.getDefault()) },
                    isPremium = premiumState.isPremium,
                    onUpdate = viewModel.controller::updateAppSettings,
                    onRefresh = viewModel.controller::refreshSupportedMediaApps,
                    onOpenPremium = { showPremiumDialog = true },
                )

                AppSection.VOICE -> VoiceSettingsScreen(
                    settings = effectiveSettings,
                    voices = voices,
                    ttsStatus = diagnostics.ttsState,
                    isPremium = premiumState.isPremium,
                    onUpdate = viewModel.controller::updateUserSettings,
                    onTest = viewModel.controller::speakTest,
                    onOpenPremium = { showPremiumDialog = true },
                )

                AppSection.DIAGNOSTICS -> DiagnosticsScreen(diagnostics, mediaState.currentEvent)
                }
            }
        }
        if (showPremiumDialog) {
            PremiumDialog(
                state = premiumState,
                onDismiss = { showPremiumDialog = false },
                onPurchase = { viewModel.purchasePremium(activity) },
                onRestore = viewModel::restorePremium,
                onRedeemLocalCode = viewModel::redeemLocalPromoCode,
                onOpenPromoCode = { url ->
                    context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
                },
            )
        }
    }
}

@Composable
private fun RowScope.NavigationItem(
    section: AppSection,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: AppSection,
    onSelected: (AppSection) -> Unit,
) {
    val strings = LocalTrackTalkStrings.current
    NavigationBarItem(
        selected = selected == section,
        onClick = { onSelected(section) },
        icon = { Icon(icon, contentDescription = strings.sectionTitle(section)) },
        label = { Text(strings.navLabel(section), maxLines = 1) },
    )
}

@Composable
private fun SurfaceContent(padding: PaddingValues, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding),
        content = content,
    )
}

@Composable
private fun HomeScreen(
    settings: UserSettings,
    mediaEvent: PlaybackEvent?,
    currentMode: AnnouncementMode,
    currentCollection: PlaybackCollection,
    lastDetectedAt: Long?,
    effectiveEnabled: Boolean,
    notificationAccess: Boolean,
    notificationPermissionGranted: Boolean,
    premiumState: PremiumState,
    onToggle: (Boolean) -> Unit,
    onTogglePlayback: () -> Unit,
    onOpenPermission: () -> Unit,
    onRequestNotificationPermission: () -> Unit,
    onOpenGeneral: () -> Unit,
    onOpenPremium: () -> Unit,
) {
    val strings = LocalTrackTalkStrings.current
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            StatusCard(
                enabled = settings.enabled,
                effectiveEnabled = effectiveEnabled,
                onToggle = onToggle,
            )
        }
        if (!notificationAccess) {
            item {
                PermissionCard(onOpenPermission)
            }
        }
        if (!notificationPermissionGranted && settings.showStatusNotification) {
            item {
                NotificationPermissionCard(onRequestNotificationPermission)
            }
        }
        item {
            CurrentTrackCard(mediaEvent, currentMode, currentCollection, lastDetectedAt, onTogglePlayback)
        }
        item {
            PremiumCard(premiumState, onOpenPremium)
        }
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = TrackVoiceCardShape,
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(strings.guideSettings, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(strings.guideSummary(currentMode, settings.delaySeconds))
                    TextButton(onClick = onOpenGeneral) { Text(strings.openSettings) }
                }
            }
        }
    }
}

@Composable
private fun PremiumCard(state: PremiumState, onOpenPremium: () -> Unit) {
    val strings = LocalTrackTalkStrings.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = TrackVoiceCardShape,
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (state.isPremium) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.tertiaryContainer
            },
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(Icons.Default.Star, contentDescription = null, modifier = Modifier.size(28.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(strings.premiumTitle, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    if (state.isPremium) {
                        strings.premiumEnabledSummary
                    } else {
                        strings.premiumLockedSummary
                    },
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            if (!state.isPremium) {
                TextButton(onClick = onOpenPremium) { Text(strings.view) }
            }
        }
    }
}

@Composable
private fun PremiumDialog(
    state: PremiumState,
    onDismiss: () -> Unit,
    onPurchase: () -> Unit,
    onRestore: () -> Unit,
    onRedeemLocalCode: (String) -> Boolean,
    onOpenPromoCode: (String) -> Unit,
) {
    val strings = LocalTrackTalkStrings.current
    var promoCode by rememberSaveable { mutableStateOf("") }
    var promoCodeError by rememberSaveable { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Star, contentDescription = null) },
        title = { Text(strings.premiumTitle) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(strings.basicMusicDetection)
                PremiumBenefit(strings.premiumVoiceBenefit)
                PremiumBenefit(strings.premiumDeviceBenefit)
                PremiumBenefit(strings.premiumFutureBenefit)
                when {
                    state.isPremium -> Text(
                        strings.premiumActive,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                    )
                    state.price != null -> Text(strings.oneTimePrice(state.price.orEmpty()))
                    else -> Text(
                        state.message?.let(strings::premiumMessage) ?: strings.playProductPreparing,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (!state.isPremium && state.message != null && state.price != null) {
                    Text(
                        strings.premiumMessage(state.message),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (!state.isPremium) {
                    HorizontalDivider()
                    Text(
                        strings.promoDescription,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    OutlinedTextField(
                        value = promoCode,
                        onValueChange = {
                            promoCode = it.take(64)
                            promoCodeError = false
                        },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(strings.promoCode) },
                        singleLine = true,
                        isError = promoCodeError,
                        supportingText = if (promoCodeError) {
                            { Text(strings.promoCodeFormatError) }
                        } else {
                            null
                        },
                    )
                    OutlinedButton(
                        onClick = {
                            if (onRedeemLocalCode(promoCode)) {
                                promoCode = ""
                                promoCodeError = false
                            } else {
                                promoCodeError = true
                            }
                        },
                        enabled = promoCode.isNotBlank(),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(strings.applyFriendCode)
                    }
                    TextButton(
                        onClick = {
                            val url = promoCodeRedeemUrl(promoCode)
                            if (url == null) {
                                promoCodeError = true
                            } else {
                                onOpenPromoCode(url)
                            }
                        },
                        enabled = promoCode.isNotBlank(),
                    ) {
                        Text(strings.useGooglePlayCode)
                    }
                }
            }
        },
        confirmButton = {
            if (state.isPremium) {
                TextButton(onClick = onDismiss) { Text(strings.close) }
            } else {
                Button(
                    onClick = onPurchase,
                    enabled = state.productAvailable && !state.isLoading,
                ) {
                    Text(if (state.isLoading) strings.checking else strings.buyPlus)
                }
            }
        },
        dismissButton = {
            if (!state.isPremium) {
                TextButton(onClick = onRestore, enabled = !state.isLoading) { Text(strings.restorePurchase) }
            }
        },
    )
}

@Composable
private fun PremiumBenefit(text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(
            Icons.Default.CheckCircle,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp),
        )
        Text(text, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun NotificationPermissionCard(onRequestPermission: () -> Unit) {
    val strings = LocalTrackTalkStrings.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = TrackVoiceCardShape,
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(strings.notificationPermissionTitle, fontWeight = FontWeight.Bold)
            Text(strings.notificationPermissionSummary, style = MaterialTheme.typography.bodyMedium)
            OutlinedButton(onClick = onRequestPermission) { Text(strings.allowNotifications) }
        }
    }
}

@Composable
private fun StatusCard(enabled: Boolean, effectiveEnabled: Boolean, onToggle: (Boolean) -> Unit) {
    val strings = LocalTrackTalkStrings.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = TrackVoiceCardShape,
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (effectiveEnabled) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(strings.homeVoiceGuide, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(strings.statusSummary(effectiveEnabled, enabled))
            }
            Switch(checked = enabled, onCheckedChange = onToggle)
        }
    }
}

@Composable
private fun PermissionCard(onOpenPermission: () -> Unit) {
    val strings = LocalTrackTalkStrings.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = TrackVoiceCardShape,
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Warning, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(strings.permissionRequired, fontWeight = FontWeight.Bold)
            }
            Text(strings.permissionSummary, style = MaterialTheme.typography.bodyMedium)
            Button(onClick = onOpenPermission) { Text(strings.permissionSettings) }
        }
    }
}

@Composable
private fun CurrentTrackCard(
    event: PlaybackEvent?,
    mode: AnnouncementMode,
    collection: PlaybackCollection,
    lastDetectedAt: Long?,
    onTogglePlayback: () -> Unit,
) {
    val strings = LocalTrackTalkStrings.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = TrackVoiceCardShape,
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(strings.currentTrack, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            if (event == null) {
                Text(strings.noMusicPlaying)
                Text(strings.playMusicHint, style = MaterialTheme.typography.bodySmall)
            } else {
                TrackField(strings.appField, event.sourceAppName)
                TrackField(strings.trackField, event.title ?: strings.unknownTitle)
                TrackField(strings.artistField, event.artist ?: strings.unknownArtist)
                TrackField(strings.albumField, event.album ?: strings.unknownAlbum)
                if (!event.queueTitle.isNullOrBlank()) TrackField(strings.playlistField, event.queueTitle.orEmpty())
                if (event.trackNumber != null) TrackField(strings.trackNumberField, strings.trackNumber(event.trackNumber, event.totalTracks))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AssistChip(onClick = {}, label = { Text(strings.collectionLabel(collection)) })
                    AssistChip(onClick = {}, label = { Text("${strings.announcementMode(mode)} · ${strings.playbackStatus(event.playbackState)}") })
                }
                OutlinedButton(onClick = onTogglePlayback, modifier = Modifier.fillMaxWidth()) {
                    Box(Modifier.fillMaxWidth()) {
                        Icon(
                            if (event.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.align(Alignment.CenterStart),
                        )
                        Text(
                            if (event.isPlaying) strings.pauseMusic else strings.playMusic,
                            modifier = Modifier.align(Alignment.Center),
                        )
                    }
                }
                if (lastDetectedAt != null) Text(strings.lastDetected(formatTime(lastDetectedAt)), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun TrackField(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(label, modifier = Modifier.width(64.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun GeneralSettingsScreen(
    settings: UserSettings,
    connectedDevices: List<ConnectedAudioDevice>,
    deviceSettings: Map<String, AudioDeviceSettings>,
    isPremium: Boolean,
    onUpdate: ((UserSettings) -> UserSettings) -> Unit,
    onUpdateDevice: (AudioDeviceSettings) -> Unit,
    onOpenPremium: () -> Unit,
) {
    val strings = LocalTrackTalkStrings.current
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            SettingCard(strings.appLanguageTitle) {
                OptionDropdown(
                    strings.appLanguageLabel,
                    settings.appLanguage,
                    AppLanguage.values().toList(),
                    strings::appLanguageOption,
                ) { language -> onUpdate { it.copy(appLanguage = language) } }
                Text(
                    strings.appLanguageDescription,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item {
            SettingCard(strings.basicOperation) {
                SettingSwitchRow(strings.voiceGuide, strings.voiceGuideSummary, settings.enabled) { enabled ->
                    onUpdate { current -> current.copy(enabled = enabled) }
                }
                SettingSwitchRow(strings.headphonesOnly, strings.headphonesOnlySummary, settings.headphonesOnly) { enabled ->
                    onUpdate { current -> current.copy(headphonesOnly = enabled) }
                }
                SettingSwitchRow(strings.suppressSpeaker, strings.suppressSpeakerSummary, settings.suppressDuringSpeakerPlayback) { enabled ->
                    onUpdate { current -> current.copy(suppressDuringSpeakerPlayback = enabled) }
                }
                SettingSwitchRow(strings.statusShortcut, strings.statusShortcutSummary, settings.showStatusNotification) { enabled ->
                    onUpdate { current -> current.copy(showStatusNotification = enabled) }
                }
            }
        }
        item {
            SettingCard(strings.connectedDevices) {
                if (isPremium) {
                    Text(strings.deviceAutomationSummary, style = MaterialTheme.typography.bodySmall)
                    if (connectedDevices.isEmpty()) {
                        Text(strings.noConnectedDevices, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    connectedDevices.forEach { device ->
                        val saved = deviceSettings[device.key] ?: AudioDeviceSettings(device.key, device.name)
                        Text(device.name, fontWeight = FontWeight.SemiBold)
                        Text(device.typeLabel, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        SettingSwitchRow(strings.useOnThisDevice, strings.useOnThisDeviceSummary, saved.enabled) {
                            onUpdateDevice(saved.copy(enabled = it))
                        }
                        SettingSwitchRow(strings.autoEnableOnConnect, strings.autoEnableOnConnectSummary, saved.autoEnable) {
                            onUpdateDevice(saved.copy(autoEnable = it))
                        }
                        HorizontalDivider()
                    }
                } else {
                    PremiumLockedContent(
                        title = strings.text("기기별 자동화는 Plus 기능입니다.", "Per-device automation is a Plus feature."),
                        summary = strings.text("Bluetooth·USB·HDMI 기기별로 안내 여부와 자동 켜짐을 설정할 수 있습니다.", "Configure announcements and auto-enable for Bluetooth, USB, and HDMI devices."),
                        onOpenPremium = onOpenPremium,
                    )
                }
            }
        }
        item {
            SettingCard(strings.trackGuide) {
                Text(
                    strings.text("모든 앱에 적용되는 안내 기본값입니다.", "Defaults applied to all apps."),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (isPremium) {
                    OptionDropdown(strings.trackStart, settings.trackStartBehavior, TrackStartBehavior.values().toList(), strings::trackStartBehavior) { value ->
                        onUpdate { current ->
                            current.copy(
                                trackStartBehavior = value,
                                musicTreatment = if (value == TrackStartBehavior.PLAY_IMMEDIATELY && current.musicTreatment == MusicTreatment.PAUSE) {
                                    MusicTreatment.DUCK
                                } else current.musicTreatment,
                            )
                        }
                    }
                    val treatmentOptions = if (settings.trackStartBehavior == TrackStartBehavior.PLAY_IMMEDIATELY) {
                        listOf(MusicTreatment.KEEP, MusicTreatment.DUCK)
                    } else {
                        MusicTreatment.values().toList()
                    }
                    OptionDropdown(strings.musicDuringGuide, settings.musicTreatment, treatmentOptions, strings::musicTreatment) { value ->
                        onUpdate { it.copy(musicTreatment = value) }
                    }
                    if (settings.musicTreatment == MusicTreatment.DUCK) {
                        SliderSetting(
                            strings.musicDuckAmount,
                            settings.musicDuckPercent.toFloat(),
                            MIN_MUSIC_DUCK_PERCENT.toFloat()..MAX_MUSIC_DUCK_PERCENT.toFloat(),
                            strings.musicDuckPercent(settings.musicDuckPercent),
                        ) { value ->
                            onUpdate {
                                it.copy(
                                    musicDuckPercent = value.toInt().coerceIn(
                                        MIN_MUSIC_DUCK_PERCENT,
                                        MAX_MUSIC_DUCK_PERCENT,
                                    ),
                                )
                            }
                        }
                    }
                    Text(
                        strings.musicVolumeSummary,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OptionDropdown(strings.announcementTiming, settings.timing, AnnouncementTiming.values().toList(), strings::announcementTiming) { selectedTiming ->
                        onUpdate { current -> current.copy(timing = selectedTiming) }
                    }
                    OptionDropdown(strings.readContent, settings.defaultMode, AnnouncementMode.values().toList(), strings::announcementMode) { selectedMode ->
                        onUpdate { current -> current.copy(defaultMode = selectedMode) }
                    }
                    SliderSetting(strings.announcementDelay, settings.delaySeconds.toFloat(), 0f..2f, strings.seconds(settings.delaySeconds)) { value ->
                        onUpdate { it.copy(delaySeconds = value.toInt()) }
                    }
                    SliderSetting(
                        strings.minimumPlayback,
                        settings.minimumPlaybackSeconds.toFloat(),
                        0f..60f,
                        strings.seconds(settings.minimumPlaybackSeconds),
                    ) { value -> onUpdate { it.copy(minimumPlaybackSeconds = value.toInt()) } }
                    SettingSwitchRow(strings.repeatTrack, strings.repeatTrackSummary, settings.allowRepeatAnnouncements) { enabled ->
                        onUpdate { current -> current.copy(allowRepeatAnnouncements = enabled) }
                    }
                } else {
                    BasicPlaybackDefaults(settings.musicDuckPercent)
                    PremiumLockedContent(
                        title = strings.text("안내 방식 세부 설정은 Plus 기능입니다.", "Detailed announcement modes are a Plus feature."),
                        summary = strings.text("무료 버전은 새 트랙을 바로 안내하고 음악을 기본 감쇠량으로 줄입니다.", "The free version announces new tracks immediately and uses the default music ducking."),
                        onOpenPremium = onOpenPremium,
                    )
                }
            }
        }
        item {
            SettingCard(strings.albumPlaylistReading) {
                Text(
                    strings.albumPlaylistSummary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (isPremium) {
                    OptionDropdown(strings.albumPlayback, settings.albumMode, AnnouncementMode.values().toList(), strings::announcementMode) { mode ->
                        onUpdate { it.copy(albumMode = mode) }
                    }
                    OptionDropdown(strings.playlistPlayback, settings.playlistMode, AnnouncementMode.values().toList(), strings::announcementMode) { mode ->
                        onUpdate { it.copy(playlistMode = mode) }
                    }
                    OptionDropdown(strings.algorithmPlayback, settings.algorithmMode, AnnouncementMode.values().toList(), strings::announcementMode) { mode ->
                        onUpdate { it.copy(algorithmMode = mode) }
                    }
                } else {
                    Text(strings.freeAlbumPlaylistDefaults)
                    PremiumLockedContent(
                        title = strings.text("콘텐츠별 읽기 방식 변경은 Plus 기능입니다.", "Content-specific reading modes are a Plus feature."),
                        summary = strings.text("앨범, 재생목록, 알고리즘·랜덤 재생의 안내 문장을 각각 선택할 수 있습니다.", "Choose separate announcement formats for albums, playlists, and algorithmic or shuffled playback."),
                        onOpenPremium = onOpenPremium,
                    )
                }
            }
        }
        item {
            SettingCard(strings.autoEnable) {
                if (isPremium) {
                    SettingSwitchRow(strings.screenOffEnable, strings.screenOffEnableSummary, settings.autoEnableOnScreenOff) { enabled ->
                        onUpdate { it.copy(autoEnableOnScreenOff = enabled) }
                    }
                    SettingSwitchRow(strings.screenOnRestore, strings.screenOnRestoreSummary, settings.restoreEnabledWhenScreenOn) { enabled ->
                        onUpdate { it.copy(restoreEnabledWhenScreenOn = enabled) }
                    }
                    SettingSwitchRow(
                        strings.bluetoothOnly,
                        strings.bluetoothOnlySummary,
                        settings.bluetoothOnlyForAutoEnable,
                    ) { enabled ->
                        onUpdate { it.copy(bluetoothOnlyForAutoEnable = enabled) }
                    }
                } else {
                    PremiumLockedContent(
                        title = strings.text("화면 꺼짐 자동 활성화는 Plus 기능입니다.", "Screen-off auto enable is a Plus feature."),
                        summary = strings.text("화면을 끈 뒤에도 음악 안내를 계속 유지할 수 있습니다.", "Keep music announcements active after turning off the screen."),
                        onOpenPremium = onOpenPremium,
                    )
                }
            }
        }
    }
}

@Composable
private fun AppSettingsScreen(
    apps: List<AppSettings>,
    isPremium: Boolean,
    onUpdate: (AppSettings) -> Unit,
    onRefresh: () -> Unit,
    onOpenPremium: () -> Unit,
) {
    val strings = LocalTrackTalkStrings.current
    var selectedCategoryNames by rememberSaveable {
        mutableStateOf(AppCategory.values().map { it.name })
    }
    val appsByCategory = remember(apps) {
        apps.groupBy { app -> categorizeApp(app.packageName, app.appName) }
    }
    val visibleAppCount = selectedCategoryNames.sumOf { name ->
        appsByCategory[AppCategory.valueOf(name)].orEmpty().size
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = TrackVoiceCardShape,
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Default.MusicNote, contentDescription = null)
                    Spacer(Modifier.width(12.dp))
                    Text(
                        strings.appsIntro,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = onRefresh) { Text(strings.refresh) }
                }
            }
        }
        if (apps.isNotEmpty()) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        strings.visibleCategories,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        AppCategory.values().forEach { category ->
                            val selected = category.name in selectedCategoryNames
                            FilterChip(
                                selected = selected,
                                onClick = {
                                    selectedCategoryNames = if (selected) {
                                        selectedCategoryNames - category.name
                                    } else {
                                        selectedCategoryNames + category.name
                                    }
                                },
                                label = { Text(strings.categoryTitle(category)) },
                                leadingIcon = if (selected) {
                                    { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                                } else null,
                            )
                        }
                    }
                    Text(
                        strings.appCountSummary(visibleAppCount, selectedCategoryNames.size),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        if (apps.isEmpty()) {
            item {
                Column(Modifier.padding(horizontal = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(strings.noSupportedApps)
                    Text(strings.appAutoAddSummary, style = MaterialTheme.typography.bodySmall)
                }
            }
        } else if (visibleAppCount == 0) {
            item {
                Text(
                    strings.noCategorySelected,
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
            }
        } else {
            AppCategory.values().forEach { category ->
                val categoryApps = appsByCategory[category].orEmpty()
                if (category.name in selectedCategoryNames && categoryApps.isNotEmpty()) {
                    item(key = "category-${category.name}") {
                        AppCategoryHeader(category, categoryApps.size)
                    }
                    items(
                        items = categoryApps,
                        key = { app -> "app-${app.packageName}" },
                    ) { app ->
                        AppSettingsCard(app, isPremium, onUpdate, onOpenPremium)
                    }
                }
            }
        }
    }
}

@Composable
private fun AppCategoryHeader(category: AppCategory, appCount: Int) {
    val strings = LocalTrackTalkStrings.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(
            imageVector = category.icon(),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp),
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(
                strings.categoryTitle(category),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                strings.categoryDescription(category),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            strings.appCategoryCount(appCount),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

private fun AppCategory.icon() = when (this) {
    AppCategory.MUSIC_STREAMING -> Icons.Default.MusicNote
    AppCategory.MUSIC_VIDEO -> Icons.Default.VideoLibrary
    AppCategory.LEARNING -> Icons.Default.School
    AppCategory.PODCAST -> Icons.Default.Podcasts
    AppCategory.OTHER -> Icons.Default.Apps
}

@Composable
private fun AppSettingsCard(
    app: AppSettings,
    isPremium: Boolean,
    onUpdate: (AppSettings) -> Unit,
    onOpenPremium: () -> Unit,
) {
    val strings = LocalTrackTalkStrings.current
    var expanded by rememberSaveable(app.packageName) { mutableStateOf(false) }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = TrackVoiceCardShape,
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                InstalledAppIcon(app.packageName)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(app.appName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        when {
                            app.alwaysExclude -> strings.alwaysExclude
                            app.enabled -> strings.appGuideEnabled
                            else -> strings.appGuideDisabled
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = app.enabled,
                    onCheckedChange = { onUpdate(app.copy(enabled = it, alwaysExclude = false)) },
                )
            }
            if (isPremium) {
                SettingSwitchRow(
                    strings.appCustomGuideSettings,
                    strings.appCustomGuideSummary(app.useCustomGuideSettings),
                    app.useCustomGuideSettings,
                ) { custom ->
                    expanded = custom
                    onUpdate(app.copy(useCustomGuideSettings = custom))
                }
                CheckRow(strings.appAlwaysExclude, app.alwaysExclude) {
                    onUpdate(
                        app.copy(
                            alwaysExclude = it,
                            enabled = if (it) false else app.enabled,
                        ),
                    )
                }
                if (app.useCustomGuideSettings) {
                    TextButton(onClick = { expanded = !expanded }) {
                        Text(if (expanded) strings.collapseDetails else strings.expandDetails)
                    }
                }
            } else {
                PremiumLockedContent(
                    title = strings.appDetailsPlusTitle,
                    summary = strings.appDetailsFreeSummary,
                    onOpenPremium = onOpenPremium,
                )
            }
            if (expanded && isPremium && app.useCustomGuideSettings) {
                Text(
                    strings.appOverrideDetails,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                OptionDropdown(strings.appReadMode, app.mode, AnnouncementMode.values().toList(), strings::announcementMode) {
                    onUpdate(app.copy(mode = it))
                }
                OptionDropdown(
                    strings.appAnnouncementTiming,
                    app.timing,
                    listOf(null) + AnnouncementTiming.values().toList(),
                    { it?.let(strings::announcementTiming) ?: strings.text("기본 설정 사용", "Use default") },
                ) { onUpdate(app.copy(timing = it)) }
                HorizontalDivider()
                CheckRow(strings.appReadTitle, app.readTitle) { onUpdate(app.copy(readTitle = it)) }
                CheckRow(strings.appReadArtist, app.readArtist) { onUpdate(app.copy(readArtist = it)) }
                CheckRow(strings.appReadTrackNumber, app.readTrackNumber) { onUpdate(app.copy(readTrackNumber = it)) }
                CheckRow(strings.appReadAlbum, app.readAlbum) { onUpdate(app.copy(readAlbum = it)) }
                CheckRow(strings.appReadCollection, app.readCollection) { onUpdate(app.copy(readCollection = it)) }
            }
        }
    }
}

@Composable
private fun InstalledAppIcon(packageName: String) {
    val context = LocalContext.current
    val bitmap = androidx.compose.runtime.remember(packageName) {
        runCatching {
            context.packageManager.getApplicationIcon(packageName)
                .toBitmap(width = 96, height = 96)
                .asImageBitmap()
        }.getOrNull()
    }
    if (bitmap != null) {
        Image(
            bitmap = bitmap,
            contentDescription = null,
            modifier = Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)),
        )
    } else {
        Box(
            modifier = Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Default.Apps, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun VoiceSettingsScreen(
    settings: UserSettings,
    voices: List<InstalledVoice>,
    ttsStatus: com.trackvoice.announcement.TtsState,
    isPremium: Boolean,
    onUpdate: ((UserSettings) -> UserSettings) -> Unit,
    onTest: () -> Unit,
    onOpenPremium: () -> Unit,
) {
    val strings = LocalTrackTalkStrings.current
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            SettingCard(strings.voiceSelection) {
                OptionDropdown(strings.defaultVoiceLanguage, settings.voiceLanguage, VoiceLanguage.values().toList(), strings::voiceLanguage) { selectedLanguage ->
                    onUpdate { current -> current.copy(voiceLanguage = selectedLanguage, voiceName = null) }
                }
                Text(
                    strings.voiceLanguageHint,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val genderOptions = listOf(GenderFilter.ANY, GenderFilter.FEMALE, GenderFilter.MALE)
                val visibleGender = settings.genderFilter.takeIf { it in genderOptions } ?: GenderFilter.ANY
                OptionDropdown(strings.gender, visibleGender, genderOptions, strings::genderLabel) { selectedGender ->
                    onUpdate { current -> current.copy(genderFilter = selectedGender, voiceName = null) }
                }
                val languageCode = when (settings.voiceLanguage) {
                    VoiceLanguage.AUTO -> null
                    VoiceLanguage.SYSTEM -> Locale.getDefault().language
                    VoiceLanguage.KOREAN -> "ko"
                    VoiceLanguage.ENGLISH -> "en"
                }
                val languageVoices = voices.filter { voice ->
                    languageCode == null || Locale.forLanguageTag(voice.localeTag).language == languageCode
                }
                val filteredVoices = languageVoices.filter { voice ->
                    visibleGender == GenderFilter.ANY || voice.gender == visibleGender
                }
                val selectedVoice = filteredVoices.firstOrNull { it.name == settings.voiceName }
                OptionDropdown(
                    strings.voice,
                    selectedVoice,
                    listOf(null) + filteredVoices,
                    { voice -> voice?.label ?: strings.autoSelect },
                ) { voice -> onUpdate { it.copy(voiceName = voice?.name) } }
                Text(
                    if (filteredVoices.isEmpty()) strings.noMatchingVoices
                    else strings.availableVoices(visibleGender, filteredVoices.size),
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    if (ttsStatus.status == TtsStatus.READY) ttsStatus.message else "${ttsStatus.message}",
                    color = if (ttsStatus.status == TtsStatus.ERROR) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item {
            SettingCard(strings.speechControls) {
                if (isPremium) {
                    SliderSetting(strings.speechRate, settings.speechRate, 0.5f..2f, "${"%.1f".format(Locale.getDefault(), settings.speechRate)}x") { value ->
                        onUpdate { current -> current.copy(speechRate = value) }
                    }
                    SliderSetting(strings.pitch, settings.pitch, 0.5f..2f, "${"%.1f".format(Locale.getDefault(), settings.pitch)}x") { value ->
                        onUpdate { current -> current.copy(pitch = value) }
                    }
                    SliderSetting(strings.voiceVolumeSeparate, settings.volume, 0f..1f, "${(settings.volume * 100).toInt()}%") { value ->
                        onUpdate { current -> current.copy(volume = value) }
                    }
                    Text(
                        strings.speechVolumeHint,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    PremiumLockedContent(
                        title = strings.voiceControlsPlusTitle,
                        summary = strings.voiceControlsFreeSummary,
                        onOpenPremium = onOpenPremium,
                    )
                }
                Button(onClick = onTest) {
                    Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(strings.testPlayback)
                }
                Text(strings.testExample, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun DiagnosticsScreen(
    diagnostics: com.trackvoice.DiagnosticsState,
    event: PlaybackEvent?,
) {
    val strings = LocalTrackTalkStrings.current
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            SettingCard(strings.connectionStatus) {
                DiagnosticRow(strings.notificationAccess, if (diagnostics.notificationListenerConnected) strings.connected else strings.notConnected, diagnostics.notificationListenerConnected)
                DiagnosticRow(strings.activeMediaSessions, diagnostics.activeSessionCount.toString(), diagnostics.activeSessionCount > 0)
                DiagnosticRow(strings.selectedApp, diagnostics.selectedSourcePackage ?: strings.none, diagnostics.selectedSourcePackage != null)
                DiagnosticRow(strings.voiceEngine, diagnostics.ttsState.message, diagnostics.ttsState.status == TtsStatus.READY)
            }
        }
        item {
            SettingCard(strings.recentLog) {
                DiagnosticRow(strings.metadataDetected, formatTimeOrDash(diagnostics.lastMetadataEventAt, strings.none), diagnostics.lastMetadataEventAt != null)
                DiagnosticRow(strings.playbackDetected, formatTimeOrDash(diagnostics.lastPlaybackStateEventAt, strings.none), diagnostics.lastPlaybackStateEventAt != null)
                DiagnosticRow(strings.lastAnnouncement, diagnostics.lastAnnouncementMessage, diagnostics.lastAnnouncementSucceeded == true)
                DiagnosticRow(strings.announcementTime, formatTimeOrDash(diagnostics.lastAnnouncementAt, strings.none), diagnostics.lastAnnouncementAt != null)
            }
        }
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = TrackVoiceCardShape,
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(strings.privacy, fontWeight = FontWeight.Bold)
                    Text(strings.privacySummary)
                }
            }
        }
        if (event != null) {
            item {
                Text(strings.currentTrackInfo, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("${event.sourcePackageName} · ${event.title ?: strings.titleMissing} · ${event.artist ?: strings.artistMissing}", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun SettingCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = TrackVoiceCardShape,
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            content = {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                content()
            },
        )
    }
}

@Composable
private fun PremiumLockedContent(
    title: String,
    summary: String,
    onOpenPremium: () -> Unit,
) {
    val strings = LocalTrackTalkStrings.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(Icons.Default.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(
                summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        TextButton(onClick = onOpenPremium) { Text(strings.plusView) }
    }
}

@Composable
private fun BasicPlaybackDefaults(musicDuckPercent: Int) {
    val strings = LocalTrackTalkStrings.current
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(strings.text("무료 기본값", "Free defaults"), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        Text(strings.text("새 트랙이 시작되면 음악과 함께 음성 안내", "Guide with music when a new track starts"))
        Text(strings.freeMusicDuckSummary(musicDuckPercent))
        Text(strings.text("음성 기본 음량 65%", "Default voice volume 65%"))
        Text(strings.text("음성 음량은 음악과 분리된 기본 음량으로 출력", "Voice volume is output separately from music"))
    }
}

@Composable
private fun SettingSwitchRow(title: String, summary: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title)
            Text(summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun CheckRow(title: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = checked, onCheckedChange = onCheckedChange)
        Text(title)
    }
}

@Composable
private fun <T> OptionDropdown(
    label: String,
    selected: T,
    options: List<T>,
    optionLabel: (T) -> String,
    onSelected: (T) -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Box {
            OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
                Text(optionLabel(selected), modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("▾")
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.widthIn(max = 320.dp).heightIn(max = 360.dp),
            ) {
                options.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(optionLabel(option)) },
                        onClick = {
                            onSelected(option)
                            expanded = false
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun SliderSetting(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    valueLabel: String,
    onValueChange: (Float) -> Unit,
) {
    var sliderValue by remember(value) { mutableFloatStateOf(value) }
    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
        Text(label)
        Text(valueLabel, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    Slider(
        value = sliderValue,
        onValueChange = { sliderValue = it },
        onValueChangeFinished = { onValueChange(sliderValue) },
        valueRange = range,
    )
}

@Composable
private fun DiagnosticRow(label: String, value: String, ok: Boolean) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(
            if (ok) Icons.Default.CheckCircle else Icons.Default.Warning,
            contentDescription = null,
            tint = if (ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            modifier = Modifier.size(18.dp),
        )
        Column(Modifier.weight(1f)) {
            Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            Text(value, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun StatusBadge(enabled: Boolean, notificationAccess: Boolean) {
    val strings = LocalTrackTalkStrings.current
    val color = when {
        !notificationAccess -> MaterialTheme.colorScheme.error
        enabled -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(
        modifier = Modifier.padding(end = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            when {
                !notificationAccess -> Icons.Default.Warning
                enabled -> Icons.Default.CheckCircle
                else -> Icons.Default.Settings
            },
            contentDescription = when {
                !notificationAccess -> strings.permissionNeeded
                enabled -> strings.on
                else -> strings.off
            },
            tint = color,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(4.dp))
        Text(
            when {
                !notificationAccess -> strings.permissionShort
                enabled -> strings.on
                else -> strings.off
            },
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = color,
        )
    }
}

private fun PlaybackStatus.label(): String = when (this) {
    PlaybackStatus.PLAYING -> "재생 중"
    PlaybackStatus.PAUSED -> "일시정지"
    PlaybackStatus.BUFFERING -> "버퍼링"
    PlaybackStatus.STOPPED -> "정지"
    PlaybackStatus.NONE -> "상태 없음"
}

private fun PlaybackCollection.label(): String = when (this) {
    PlaybackCollection.ALBUM -> "앨범 재생"
    PlaybackCollection.PLAYLIST -> "재생목록 재생"
    PlaybackCollection.ALGORITHMIC -> "알고리즘·랜덤 재생"
    PlaybackCollection.UNKNOWN -> "콘텐츠 유형 확인 중"
}

private fun formatTime(timestamp: Long): String = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(timestamp))
private fun formatTimeOrDash(timestamp: Long?, emptyLabel: String = "없음"): String = timestamp?.let(::formatTime) ?: emptyLabel

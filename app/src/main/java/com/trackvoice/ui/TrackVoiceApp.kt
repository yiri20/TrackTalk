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
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Podcasts
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.automirrored.filled.VolumeUp
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
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.core.graphics.drawable.toBitmap
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.trackvoice.BuildConfig
import com.trackvoice.TrackVoiceViewModel
import com.trackvoice.announcement.InstalledVoice
import com.trackvoice.announcement.TtsStatus
import com.trackvoice.data.AnnouncementMode
import com.trackvoice.data.AnnouncementOrder
import com.trackvoice.data.AnnouncementOutputPolicy
import com.trackvoice.data.AnnouncementReadField
import com.trackvoice.data.AnnouncementTiming
import com.trackvoice.data.AppSettings
import com.trackvoice.data.AppCategory
import com.trackvoice.data.AppLanguage
import com.trackvoice.data.CollectionFallback
import com.trackvoice.data.categorizeApp
import com.trackvoice.data.GenderFilter
import com.trackvoice.data.UserSettings
import com.trackvoice.data.VoiceLanguage
import com.trackvoice.data.MusicTreatment
import com.trackvoice.data.TrackStartBehavior
import com.trackvoice.data.AudioDeviceSettings
import com.trackvoice.data.DEFAULT_ALBUM_READ_FIELDS
import com.trackvoice.data.DEFAULT_ALGORITHMIC_READ_FIELDS
import com.trackvoice.data.DEFAULT_GLOBAL_READ_FIELDS
import com.trackvoice.data.DEFAULT_PLAYLIST_READ_FIELDS
import com.trackvoice.data.DEFAULT_TTS_VOLUME_PERCENT
import com.trackvoice.announcement.ConnectedAudioDevice
import com.trackvoice.data.MAX_MUSIC_DUCK_PERCENT
import com.trackvoice.data.MIN_MUSIC_DUCK_PERCENT
import com.trackvoice.media.PlaybackEvent
import com.trackvoice.media.PlaybackStatus
import com.trackvoice.media.PlaybackCollection
import com.trackvoice.media.PlaybackCollectionResolver
import com.trackvoice.media.AlbumTrackNumberResolver
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
    DEVICES,
    DIAGNOSTICS,
}

private enum class GuideSettingsPane {
    GUIDE,
    VOICE,
}

private const val LEGACY_VOICE_SECTION_NAME = "VOICE"

enum class GeneralSettingsTarget {
    ALBUM,
    PLAYLIST,
    ALGORITHMIC,
}

private const val CONTENT_DEFAULTS_ITEM_INDEX = 3
private const val CONTENT_ALBUM_ITEM_INDEX = 4
private const val CONTENT_PLAYLIST_ITEM_INDEX = 5
private const val CONTENT_ALGORITHMIC_ITEM_INDEX = 6

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
    val currentAppUsesCustomGuide = mediaState.currentEvent?.let { event ->
        appSettings[event.sourcePackageName]
            ?.forPremiumEntitlement(premiumState.isPremium)
            ?.useCustomGuideSettings == true
    } == true
    val currentGuideUsesTypeSpecificSettings = effectiveSettings.useContentTypeSettings &&
        mediaState.currentCollection != PlaybackCollection.UNKNOWN
    val currentGuideUsesAppSpecificSettings = currentAppUsesCustomGuide &&
        !currentGuideUsesTypeSpecificSettings
    val currentGuideBasis = strings.announcementBasisValue(
        appSpecific = currentGuideUsesAppSpecificSettings,
        typeSpecific = currentGuideUsesTypeSpecificSettings,
    )
    var selectedSectionName by rememberSaveable { mutableStateOf(AppSection.HOME.name) }
    var guidePaneName by rememberSaveable { mutableStateOf(GuideSettingsPane.GUIDE.name) }
    var generalSettingsTargetName by rememberSaveable { mutableStateOf<String?>(null) }
    var showPremiumDialog by rememberSaveable { mutableStateOf(false) }
    val selectedSection = when (selectedSectionName) {
        LEGACY_VOICE_SECTION_NAME -> AppSection.GENERAL
        else -> runCatching { AppSection.valueOf(selectedSectionName) }.getOrDefault(AppSection.HOME)
    }
    val generalSettingsTarget = generalSettingsTargetName?.let { name ->
        GeneralSettingsTarget.values().firstOrNull { it.name == name }
    }
    LaunchedEffect(selectedSectionName) {
        if (selectedSectionName == LEGACY_VOICE_SECTION_NAME) {
            selectedSectionName = AppSection.GENERAL.name
            guidePaneName = GuideSettingsPane.VOICE.name
        }
    }
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
                        StatusBadge(
                            enabled = mediaState.effectiveEnabled,
                            notificationAccess = diagnostics.notificationListenerConnected,
                        )
                    },
                )
            },
            bottomBar = {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface, tonalElevation = 0.dp) {
                    NavigationItem(AppSection.HOME, Icons.Default.Home, selectedSection) {
                        selectedSectionName = it.name
                        generalSettingsTargetName = null
                    }
                    NavigationItem(AppSection.GENERAL, Icons.Default.Tune, selectedSection) {
                        selectedSectionName = it.name
                        generalSettingsTargetName = null
                    }
                    NavigationItem(AppSection.APPS, Icons.Default.Apps, selectedSection) {
                        selectedSectionName = it.name
                        generalSettingsTargetName = null
                    }
                    NavigationItem(AppSection.DEVICES, Icons.Default.Settings, selectedSection) {
                        selectedSectionName = it.name
                        generalSettingsTargetName = null
                    }
                }
            },
        ) { padding ->
            SurfaceContent(padding) {
                when (selectedSection) {
                AppSection.HOME -> HomeScreen(
                    settings = effectiveSettings,
                    mediaEvent = mediaState.currentEvent,
                    currentCollection = mediaState.currentCollection,
                    guideBasis = currentGuideBasis,
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
                    onOpenGeneral = {
                        generalSettingsTargetName = null
                        guidePaneName = GuideSettingsPane.GUIDE.name
                        selectedSectionName = AppSection.GENERAL.name
                    },
                    onOpenCollectionSettings = { collection ->
                        generalSettingsTargetName = collection.toGeneralSettingsTarget()?.name
                        guidePaneName = GuideSettingsPane.GUIDE.name
                        selectedSectionName = AppSection.GENERAL.name
                    },
                    onOpenPremium = { showPremiumDialog = true },
                )

                AppSection.GENERAL -> GuideSettingsScreen(
                    settings = effectiveSettings,
                    voices = voices,
                    ttsStatus = diagnostics.ttsState,
                    isPremium = premiumState.isPremium,
                    onUpdate = viewModel.controller::updateUserSettings,
                    onTest = viewModel.controller::speakTest,
                    onOpenPremium = { showPremiumDialog = true },
                    target = generalSettingsTarget,
                    onTargetHandled = { generalSettingsTargetName = null },
                    selectedPaneName = guidePaneName,
                    onPaneSelected = { guidePaneName = it.name },
                )

                AppSection.APPS -> AppSettingsScreen(
                    apps = appSettings.values.sortedBy { it.appName.lowercase(Locale.getDefault()) },
                    onUpdate = viewModel.controller::updateAppSettings,
                    onRefresh = viewModel.controller::refreshSupportedMediaApps,
                )

                AppSection.DEVICES -> DeviceSettingsScreen(
                    settings = effectiveSettings,
                    connectedDevices = connectedDevices,
                    deviceSettings = deviceSettings,
                    isPremium = premiumState.isPremium,
                    onUpdate = viewModel.controller::updateUserSettings,
                    onUpdateDevice = viewModel.controller::updateAudioDeviceSettings,
                    onOpenPremium = { showPremiumDialog = true },
                    onOpenDiagnostics = { selectedSectionName = AppSection.DIAGNOSTICS.name },
                    onOpenRepository = {
                        context.startActivity(Intent(Intent.ACTION_VIEW, "https://github.com/yiri20/TrackTalk".toUri()))
                    },
                )

                AppSection.DIAGNOSTICS -> DiagnosticsScreen(
                    diagnostics,
                    mediaState.currentEvent,
                    onBack = { selectedSectionName = AppSection.DEVICES.name },
                )
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
    currentCollection: PlaybackCollection,
    guideBasis: String,
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
    onOpenCollectionSettings: (PlaybackCollection) -> Unit,
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
            CurrentTrackCard(
                event = mediaEvent,
                collection = currentCollection,
                guideBasis = guideBasis,
                lastDetectedAt = lastDetectedAt,
                onTogglePlayback = onTogglePlayback,
                onOpenCollectionSettings = onOpenCollectionSettings,
            )
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
                    Text(strings.guideTimingSummary(settings.timing, settings.delaySeconds))
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
    var showPromoCode by rememberSaveable { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Star, contentDescription = null) },
        title = { Text(strings.premiumTitle) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
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
                        state.message?.let(strings::premiumMessage) ?: strings.purchaseUnavailable,
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
                    TextButton(
                        onClick = { showPromoCode = !showPromoCode },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(if (showPromoCode) strings.closePromoCode else strings.promoCodeSection)
                    }
                    if (showPromoCode) {
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
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(strings.useGooglePlayCode)
                        }
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
    collection: PlaybackCollection,
    guideBasis: String,
    lastDetectedAt: Long?,
    onTogglePlayback: () -> Unit,
    onOpenCollectionSettings: (PlaybackCollection) -> Unit,
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
                if (
                    collection == PlaybackCollection.PLAYLIST &&
                    !PlaybackCollectionResolver.isGenericQueueTitle(event.queueTitle) &&
                    !event.queueTitle.isNullOrBlank()
                ) {
                    TrackField(strings.playlistField, event.queueTitle.orEmpty())
                }
                val visibleTrackNumber = AlbumTrackNumberResolver.resolve(
                    event = event,
                    allowQueuePositionFallback = collection == PlaybackCollection.ALBUM,
                )
                if (visibleTrackNumber != null) {
                    TrackField(
                        strings.trackNumberField,
                        strings.trackNumber(visibleTrackNumber, event.totalTracks ?: event.queue.size.takeIf { it > 1 }),
                    )
                }
                CurrentTrackSummary(
                    collection = collection,
                    guideBasis = guideBasis,
                    onClick = { onOpenCollectionSettings(collection) },
                )
                if (collection == PlaybackCollection.UNKNOWN) {
                    Text(
                        strings.collectionUnknownSummary,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
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
private fun CurrentTrackSummary(
    collection: PlaybackCollection,
    guideBasis: String,
    onClick: () -> Unit,
) {
    val strings = LocalTrackTalkStrings.current
    val summaryShape = RoundedCornerShape(12.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(summaryShape)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.32f))
            .border(BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), summaryShape)
            .padding(horizontal = 10.dp, vertical = 9.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SummaryInfo(
            modifier = Modifier
                .weight(0.9f)
                .clickable(enabled = collection != PlaybackCollection.UNKNOWN, onClick = onClick)
                .padding(horizontal = 3.dp, vertical = 1.dp),
            label = strings.playbackTypeLabel,
            value = strings.collectionValue(collection),
        )
        Box(
            modifier = Modifier
                .width(1.dp)
                .height(30.dp)
                .background(MaterialTheme.colorScheme.outlineVariant),
        )
        SummaryInfo(
            modifier = Modifier
                .weight(1.1f)
                .padding(horizontal = 3.dp, vertical = 1.dp),
            label = strings.announcementBasisLabel,
            value = guideBasis,
        )
    }
}

@Composable
private fun SummaryInfo(
    modifier: Modifier,
    label: String,
    value: String,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
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
private fun GuideSettingsScreen(
    settings: UserSettings,
    voices: List<InstalledVoice>,
    ttsStatus: com.trackvoice.announcement.TtsState,
    isPremium: Boolean,
    onUpdate: ((UserSettings) -> UserSettings) -> Unit,
    onTest: () -> Unit,
    onOpenPremium: () -> Unit,
    target: GeneralSettingsTarget?,
    onTargetHandled: () -> Unit,
    selectedPaneName: String,
    onPaneSelected: (GuideSettingsPane) -> Unit,
) {
    val selectedPane = runCatching { GuideSettingsPane.valueOf(selectedPaneName) }
        .getOrDefault(GuideSettingsPane.GUIDE)
    val strings = LocalTrackTalkStrings.current
    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(
                selected = selectedPane == GuideSettingsPane.GUIDE,
                onClick = { onPaneSelected(GuideSettingsPane.GUIDE) },
                label = { Text(strings.text("안내", "Guide")) },
                modifier = Modifier.weight(1f),
            )
            FilterChip(
                selected = selectedPane == GuideSettingsPane.VOICE,
                onClick = { onPaneSelected(GuideSettingsPane.VOICE) },
                label = { Text(strings.text("음성", "Voice")) },
                modifier = Modifier.weight(1f),
            )
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (selectedPane == GuideSettingsPane.GUIDE) {
                GeneralSettingsScreen(
                    settings = settings,
                    isPremium = isPremium,
                    onUpdate = onUpdate,
                    onOpenPremium = onOpenPremium,
                    target = target,
                    onTargetHandled = onTargetHandled,
                )
            } else {
                VoiceSettingsScreen(
                    settings = settings,
                    voices = voices,
                    ttsStatus = ttsStatus,
                    isPremium = isPremium,
                    onUpdate = onUpdate,
                    onTest = onTest,
                    onOpenPremium = onOpenPremium,
                )
            }
        }
    }
}

@Composable
private fun GeneralSettingsScreen(
    settings: UserSettings,
    isPremium: Boolean,
    onUpdate: ((UserSettings) -> UserSettings) -> Unit,
    onOpenPremium: () -> Unit,
    target: GeneralSettingsTarget?,
    onTargetHandled: () -> Unit,
) {
    val strings = LocalTrackTalkStrings.current
    val listState = rememberLazyListState()
    val targetItemIndex = when {
        !isPremium || !settings.useContentTypeSettings -> CONTENT_DEFAULTS_ITEM_INDEX
        target == GeneralSettingsTarget.ALBUM -> CONTENT_ALBUM_ITEM_INDEX
        target == GeneralSettingsTarget.PLAYLIST -> CONTENT_PLAYLIST_ITEM_INDEX
        target == GeneralSettingsTarget.ALGORITHMIC -> CONTENT_ALGORITHMIC_ITEM_INDEX
        else -> CONTENT_DEFAULTS_ITEM_INDEX
    }
    LaunchedEffect(target, isPremium, settings.useContentTypeSettings) {
        if (target != null) {
            listState.scrollToItem(targetItemIndex)
            onTargetHandled()
        }
    }
    LazyColumn(
        state = listState,
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
                OptionDropdown(
                    strings.announcementOutput,
                    settings.outputPolicy,
                    AnnouncementOutputPolicy.values().toList(),
                    strings::announcementOutputOption,
                ) { policy ->
                    onUpdate { current -> current.copy(outputPolicy = policy) }
                }
                Text(
                    strings.announcementOutputSummary(settings.outputPolicy),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                SettingSwitchRow(strings.statusShortcut, strings.statusShortcutSummary, settings.showStatusNotification) { enabled ->
                    onUpdate { current -> current.copy(showStatusNotification = enabled) }
                }
            }
        }
        item {
            SettingCard(strings.trackGuide) {
                Text(
                    strings.guideDefaultsSummary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (isPremium) {
                    OptionDropdown(strings.trackStart, settings.trackStartBehavior, TrackStartBehavior.values().toList(), strings::trackStartBehavior) { value ->
                        onUpdate { current ->
                            current.copy(
                                trackStartBehavior = value,
                                musicTreatment = when (value) {
                                    TrackStartBehavior.ANNOUNCE_THEN_PLAY -> MusicTreatment.PAUSE
                                    TrackStartBehavior.PLAY_IMMEDIATELY -> current.musicTreatment.takeUnless {
                                        it == MusicTreatment.PAUSE
                                    } ?: MusicTreatment.DUCK
                                },
                            )
                        }
                    }
                    Text(
                        strings.trackStartSummary(settings.trackStartBehavior),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (settings.trackStartBehavior == TrackStartBehavior.ANNOUNCE_THEN_PLAY) {
                        Text(
                            strings.announceThenPlaySummary,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    } else {
                        val visibleTreatment = settings.musicTreatment.takeUnless { it == MusicTreatment.PAUSE }
                            ?: MusicTreatment.DUCK
                        OptionDropdown(
                            strings.musicDuringGuide,
                            visibleTreatment,
                            listOf(MusicTreatment.KEEP, MusicTreatment.DUCK),
                            strings::musicTreatment,
                        ) { value -> onUpdate { it.copy(musicTreatment = value) } }
                        if (visibleTreatment == MusicTreatment.DUCK) {
                            SliderSetting(
                                strings.musicDuckAmount,
                                settings.musicDuckPercent.toFloat(),
                                MIN_MUSIC_DUCK_PERCENT.toFloat()..MAX_MUSIC_DUCK_PERCENT.toFloat(),
                                { value -> strings.musicDuckPercent(value.toInt()) },
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
                    }
                    val timingOptions = listOf(
                        AnnouncementTiming.IMMEDIATE,
                        AnnouncementTiming.DELAYED,
                    )
                    OptionDropdown(strings.announcementTiming, settings.timing, timingOptions, strings::announcementTiming) { selectedTiming ->
                        onUpdate { current -> current.copy(timing = selectedTiming) }
                    }
                    Text(
                        strings.announcementTimingSummary(settings.timing, settings.delaySeconds),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    val playbackThresholdsActive = settings.timing != AnnouncementTiming.IMMEDIATE &&
                        settings.trackStartBehavior != TrackStartBehavior.ANNOUNCE_THEN_PLAY
                    if (playbackThresholdsActive) {
                        SliderSetting(strings.announcementDelay, settings.delaySeconds.toFloat(), 0f..2f, { value -> strings.seconds(value.toInt()) }) { value ->
                            onUpdate { it.copy(delaySeconds = value.toInt()) }
                        }
                        SliderSetting(
                            strings.minimumPlayback,
                            settings.minimumPlaybackSeconds.toFloat(),
                            0f..60f,
                            { value -> strings.seconds(value.toInt()) },
                        ) { value -> onUpdate { it.copy(minimumPlaybackSeconds = value.toInt()) } }
                    }
                    SettingSwitchRow(strings.repeatTrack, strings.repeatTrackSummary, settings.allowRepeatAnnouncements) { enabled ->
                        onUpdate { current -> current.copy(allowRepeatAnnouncements = enabled) }
                    }
                } else {
                    BasicPlaybackDefaults(settings.musicDuckPercent)
                    PremiumLockedContent(
                        title = strings.detailedGuidePlusTitle,
                        summary = strings.freeGuideDetailsSummary,
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
                    Text(
                        strings.contentReadPresetHint,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    SettingSwitchRow(
                        strings.contentTypeSettings,
                        strings.contentTypeSettingsSummary(settings.useContentTypeSettings, settings.defaultMode),
                        settings.useContentTypeSettings,
                    ) { enabled ->
                        onUpdate { it.copy(useContentTypeSettings = enabled) }
                    }
                    OptionDropdown(
                        strings.globalReadContent,
                        settings.defaultMode,
                        AnnouncementMode.values().toList(),
                        strings::announcementMode,
                    ) { selectedMode ->
                        onUpdate { current -> current.copy(defaultMode = selectedMode) }
                    }
                    Text(
                        strings.globalReadContentSummary(settings.defaultMode, settings.useContentTypeSettings),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OptionDropdown(
                        strings.announcementOrder,
                        settings.announcementOrder,
                        AnnouncementOrder.values().toList(),
                        strings::announcementOrder,
                    ) { selectedOrder ->
                        onUpdate { current -> current.copy(announcementOrder = selectedOrder) }
                    }
                    Text(
                        strings.announcementOrderSummary,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OptionDropdown(
                        strings.allContentPresetLabel,
                        settings.defaultReadFields.toContentReadPreset(DEFAULT_GLOBAL_READ_FIELDS),
                        CONTENT_READ_PRESET_OPTIONS,
                        strings::contentReadPreset,
                    ) { preset ->
                        onUpdate { it.withGlobalReadPreset(preset) }
                    }
                    ContentReadChecklist(
                        title = strings.allContentReadItems,
                        availableFields = listOf(
                            AnnouncementReadField.TITLE,
                            AnnouncementReadField.ARTIST,
                            AnnouncementReadField.ALBUM,
                            AnnouncementReadField.TRACK_NUMBER,
                            AnnouncementReadField.COLLECTION,
                        ),
                        selectedFields = settings.defaultReadFields,
                    ) { fields ->
                        onUpdate { it.copy(defaultMode = AnnouncementMode.SMART, defaultReadFields = fields) }
                    }
                    if (!settings.useContentTypeSettings) {
                        Text(
                            strings.contentTypeSettingsDisabledSummary,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    PremiumLockedContent(
                        title = strings.contentReadingPlusTitle,
                        summary = strings.contentSpecificPlusSummary,
                        onOpenPremium = onOpenPremium,
                    )
                }
            }
        }
        if (isPremium && settings.useContentTypeSettings) {
            item {
                SettingCard(strings.contentTypeSectionTitle(PlaybackCollection.ALBUM)) {
                    OptionDropdown(
                        strings.contentPresetLabel(PlaybackCollection.ALBUM),
                        settings.albumReadFields.toContentReadPreset(DEFAULT_ALBUM_READ_FIELDS),
                        CONTENT_READ_PRESET_OPTIONS,
                        strings::contentReadPreset,
                    ) { preset -> onUpdate { it.withAlbumReadPreset(preset) } }
                    ContentReadChecklist(
                        title = strings.albumReadItems,
                        availableFields = listOf(
                            AnnouncementReadField.ALBUM,
                            AnnouncementReadField.TRACK_NUMBER,
                            AnnouncementReadField.TITLE,
                            AnnouncementReadField.ARTIST,
                        ),
                        selectedFields = settings.albumReadFields,
                    ) { fields -> onUpdate { it.copy(albumMode = AnnouncementMode.ALBUM, albumReadFields = fields) } }
                    SettingSwitchRow(
                        strings.albumNameFirstTrackOnly,
                        strings.albumNameFirstTrackOnlySummary,
                        settings.albumNameFirstTrackOnly,
                    ) { enabled -> onUpdate { it.copy(albumNameFirstTrackOnly = enabled) } }
                }
            }
            item {
                SettingCard(strings.contentTypeSectionTitle(PlaybackCollection.PLAYLIST)) {
                    OptionDropdown(
                        strings.contentPresetLabel(PlaybackCollection.PLAYLIST),
                        settings.playlistReadFields.toContentReadPreset(DEFAULT_PLAYLIST_READ_FIELDS),
                        CONTENT_READ_PRESET_OPTIONS,
                        strings::contentReadPreset,
                    ) { preset -> onUpdate { it.withPlaylistReadPreset(preset) } }
                    ContentReadChecklist(
                        title = strings.playlistReadItems,
                        availableFields = listOf(
                            AnnouncementReadField.COLLECTION,
                            AnnouncementReadField.ALBUM,
                            AnnouncementReadField.TRACK_NUMBER,
                            AnnouncementReadField.TITLE,
                            AnnouncementReadField.ARTIST,
                        ),
                        selectedFields = settings.playlistReadFields,
                    ) { fields -> onUpdate { it.copy(playlistMode = AnnouncementMode.PLAYLIST, playlistReadFields = fields) } }
                }
            }
            item {
                SettingCard(strings.contentTypeSectionTitle(PlaybackCollection.ALGORITHMIC)) {
                    OptionDropdown(
                        strings.contentPresetLabel(PlaybackCollection.ALGORITHMIC),
                        settings.algorithmReadFields.toContentReadPreset(DEFAULT_ALGORITHMIC_READ_FIELDS),
                        CONTENT_READ_PRESET_OPTIONS,
                        strings::contentReadPreset,
                    ) { preset -> onUpdate { it.withAlgorithmReadPreset(preset) } }
                    ContentReadChecklist(
                        title = strings.algorithmReadItems,
                        availableFields = listOf(
                            AnnouncementReadField.TITLE,
                            AnnouncementReadField.ARTIST,
                            AnnouncementReadField.ALBUM,
                            AnnouncementReadField.TRACK_NUMBER,
                        ),
                        selectedFields = settings.algorithmReadFields,
                    ) { fields ->
                        onUpdate {
                            it.copy(
                                algorithmMode = fields.toAlgorithmAnnouncementMode(),
                                algorithmReadFields = fields,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DeviceSettingsScreen(
    settings: UserSettings,
    connectedDevices: List<ConnectedAudioDevice>,
    deviceSettings: Map<String, AudioDeviceSettings>,
    isPremium: Boolean,
    onUpdate: ((UserSettings) -> UserSettings) -> Unit,
    onUpdateDevice: (AudioDeviceSettings) -> Unit,
    onOpenPremium: () -> Unit,
    onOpenDiagnostics: () -> Unit,
    onOpenRepository: () -> Unit,
) {
    val strings = LocalTrackTalkStrings.current
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (isPremium) {
            item {
                SettingCard(strings.connectedDevices) {
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
                }
            }
            item {
                SettingCard(strings.autoEnable) {
                    SettingSwitchRow(strings.screenOffEnable, strings.screenOffEnableSummary, settings.autoEnableOnScreenOff) { enabled ->
                        onUpdate { it.copy(autoEnableOnScreenOff = enabled) }
                    }
                    SettingSwitchRow(strings.screenOnRestore, strings.screenOnRestoreSummary, settings.restoreEnabledWhenScreenOn) { enabled ->
                        onUpdate { it.copy(restoreEnabledWhenScreenOn = enabled) }
                    }
                    SettingSwitchRow(strings.bluetoothOnly, strings.bluetoothOnlySummary, settings.bluetoothOnlyForAutoEnable) { enabled ->
                        onUpdate { it.copy(bluetoothOnlyForAutoEnable = enabled) }
                    }
                }
            }
        } else {
            item {
                SettingCard(strings.automationPlusTitle) {
                    PremiumLockedContent(
                        title = strings.automationPlusDetailsTitle,
                        summary = strings.automationPlusSummary,
                        onOpenPremium = onOpenPremium,
                    )
                }
            }
        }
        item {
            SettingCard(strings.sectionTitle(AppSection.DIAGNOSTICS)) {
                Text(
                    strings.diagnosticsSummary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedButton(onClick = onOpenDiagnostics, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.BugReport, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(strings.openDiagnostics)
                }
            }
        }
        item {
            AppInfoCard(onOpenRepository = onOpenRepository)
        }
    }
}

@Composable
private fun AppInfoCard(onOpenRepository: () -> Unit) {
    val strings = LocalTrackTalkStrings.current
    SettingCard(strings.appInfoTitle) {
        Text(
            strings.appInfoSummary,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        InfoRow(strings.versionLabel, "v${BuildConfig.VERSION_NAME}")
        InfoRow(strings.buildNumberLabel, BuildConfig.VERSION_CODE.toString())
        InfoRow(strings.developerLabel, strings.developerName)
        OutlinedButton(onClick = onOpenRepository, modifier = Modifier.fillMaxWidth()) {
            Text(strings.openRepository)
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            modifier = Modifier.width(88.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(value, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun AppSettingsScreen(
    apps: List<AppSettings>,
    onUpdate: (AppSettings) -> Unit,
    onRefresh: () -> Unit,
) {
    val strings = LocalTrackTalkStrings.current
    var selectedCategoryNames by rememberSaveable {
        mutableStateOf(
            listOf(
                AppCategory.MUSIC_STREAMING.name,
                AppCategory.MUSIC_VIDEO.name,
            ),
        )
    }
    val appsByCategory = remember(apps) {
        apps.groupBy { app -> categorizeApp(app.packageName, app.appName) }
    }
    val visibleAppCount = selectedCategoryNames.sumOf { name ->
        appsByCategory[AppCategory.valueOf(name)].orEmpty().size
    }
    val categoryScrollState = rememberScrollState()
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
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Default.MusicNote, contentDescription = null)
                        Spacer(Modifier.width(12.dp))
                        Text(strings.appsIntro, modifier = Modifier.weight(1f))
                    }
                    TextButton(
                        onClick = onRefresh,
                        modifier = Modifier.align(Alignment.End),
                    ) {
                        Text(strings.refresh)
                    }
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
                    Box(Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(categoryScrollState)
                                .padding(end = 32.dp),
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
                        if (categoryScrollState.maxValue > categoryScrollState.value) {
                            Icon(
                                Icons.Default.ChevronRight,
                                contentDescription = strings.scrollMore,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .align(Alignment.CenterEnd)
                                    .size(32.dp)
                                    .background(MaterialTheme.colorScheme.background.copy(alpha = 0.94f))
                                    .padding(4.dp),
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
                        AppSettingsCard(app, onUpdate)
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
    onUpdate: (AppSettings) -> Unit,
) {
    val strings = LocalTrackTalkStrings.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = TrackVoiceCardShape,
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                InstalledAppIcon(app.packageName)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(app.appName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        if (app.enabled) strings.appGuideEnabled else strings.appGuideDisabled,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = app.enabled,
                    onCheckedChange = { enabled ->
                        onUpdate(app.copy(enabled = enabled, enabledOverride = enabled))
                    },
                )
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
                    SliderSetting(strings.speechRate, settings.speechRate, 0.5f..2f, { value -> "${"%.1f".format(Locale.getDefault(), value)}x" }) { value ->
                        onUpdate { current -> current.copy(speechRate = value) }
                    }
                    SliderSetting(strings.pitch, settings.pitch, 0.5f..2f, { value -> "${"%.1f".format(Locale.getDefault(), value)}x" }) { value ->
                        onUpdate { current -> current.copy(pitch = value) }
                    }
                    SliderSetting(strings.voiceVolumeSeparate, settings.volume, 0f..1f, { value -> "${(value * 100).toInt()}%" }) { value ->
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
    onBack: () -> Unit,
) {
    val strings = LocalTrackTalkStrings.current
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            TextButton(onClick = onBack) {
                Text(strings.backToDevices)
            }
        }
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
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(
                summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        TextButton(onClick = onOpenPremium) { Text(strings.plus) }
    }
}

@Composable
private fun BasicPlaybackDefaults(musicDuckPercent: Int) {
    val strings = LocalTrackTalkStrings.current
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(strings.freeGuideWithMusic)
        Text(strings.freeMusicDuckSummary(musicDuckPercent))
        Text(strings.defaultVoiceVolumeSummary(DEFAULT_TTS_VOLUME_PERCENT))
        Text(strings.separateVoiceVolumeSummary)
    }
}

@Composable
private fun ContentReadChecklist(
    title: String,
    hint: String? = null,
    availableFields: List<AnnouncementReadField>,
    selectedFields: Set<AnnouncementReadField>,
    onUpdate: (Set<AnnouncementReadField>) -> Unit,
) {
    val strings = LocalTrackTalkStrings.current
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        Text(
            hint ?: strings.contentReadChecklistHint,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        availableFields.forEach { field ->
            CheckRow(strings.readField(field), field in selectedFields) { checked ->
                onUpdate(if (checked) selectedFields + field else selectedFields - field)
            }
        }
    }
}

private fun AnnouncementMode.toReadFields(defaultFields: Set<AnnouncementReadField>): Set<AnnouncementReadField> = when (this) {
    AnnouncementMode.ALBUM -> setOf(
        AnnouncementReadField.ALBUM,
        AnnouncementReadField.TRACK_NUMBER,
        AnnouncementReadField.TITLE,
        AnnouncementReadField.ARTIST,
    )
    AnnouncementMode.PLAYLIST -> setOf(
        AnnouncementReadField.COLLECTION,
        AnnouncementReadField.TITLE,
        AnnouncementReadField.ARTIST,
    )
    AnnouncementMode.TITLE_ONLY -> setOf(AnnouncementReadField.TITLE)
    AnnouncementMode.TITLE_AND_ARTIST -> setOf(
        AnnouncementReadField.TITLE,
        AnnouncementReadField.ARTIST,
    )
    AnnouncementMode.SMART -> defaultFields
}

private val CONTENT_READ_PRESET_OPTIONS = listOf(
    ContentReadPreset.DEFAULT,
    ContentReadPreset.TITLE_AND_ARTIST,
    ContentReadPreset.TITLE_ONLY,
)

private fun Set<AnnouncementReadField>.toContentReadPreset(defaultFields: Set<AnnouncementReadField>): ContentReadPreset = when {
    this == defaultFields -> ContentReadPreset.DEFAULT
    this == setOf(AnnouncementReadField.TITLE, AnnouncementReadField.ARTIST) -> ContentReadPreset.TITLE_AND_ARTIST
    this == setOf(AnnouncementReadField.TITLE) -> ContentReadPreset.TITLE_ONLY
    else -> ContentReadPreset.CUSTOM
}

private fun ContentReadPreset.toAnnouncementMode(defaultMode: AnnouncementMode): AnnouncementMode = when (this) {
    ContentReadPreset.DEFAULT -> defaultMode
    ContentReadPreset.TITLE_AND_ARTIST -> AnnouncementMode.TITLE_AND_ARTIST
    ContentReadPreset.TITLE_ONLY -> AnnouncementMode.TITLE_ONLY
    ContentReadPreset.CUSTOM -> defaultMode
}

private fun UserSettings.withAlbumReadPreset(preset: ContentReadPreset): UserSettings = copy(
    albumMode = preset.toAnnouncementMode(AnnouncementMode.ALBUM),
    albumReadFields = preset.toReadFields(DEFAULT_ALBUM_READ_FIELDS),
)

private fun UserSettings.withPlaylistReadPreset(preset: ContentReadPreset): UserSettings = copy(
    playlistMode = preset.toAnnouncementMode(AnnouncementMode.PLAYLIST),
    playlistReadFields = preset.toReadFields(DEFAULT_PLAYLIST_READ_FIELDS),
)

private fun UserSettings.withAlgorithmReadPreset(preset: ContentReadPreset): UserSettings = copy(
    algorithmMode = preset.toAnnouncementMode(AnnouncementMode.TITLE_AND_ARTIST),
    algorithmReadFields = preset.toReadFields(DEFAULT_ALGORITHMIC_READ_FIELDS),
)

private fun Set<AnnouncementReadField>.toAlgorithmAnnouncementMode(): AnnouncementMode = when {
    AnnouncementReadField.ALBUM in this || AnnouncementReadField.TRACK_NUMBER in this -> AnnouncementMode.ALBUM
    AnnouncementReadField.TITLE in this && AnnouncementReadField.ARTIST in this -> AnnouncementMode.TITLE_AND_ARTIST
    AnnouncementReadField.TITLE in this -> AnnouncementMode.TITLE_ONLY
    else -> AnnouncementMode.TITLE_AND_ARTIST
}

private fun UserSettings.withGlobalReadPreset(preset: ContentReadPreset): UserSettings = copy(
    defaultMode = preset.toAnnouncementMode(AnnouncementMode.SMART),
    defaultReadFields = preset.toReadFields(DEFAULT_GLOBAL_READ_FIELDS),
)

private fun ContentReadPreset.toReadFields(defaultFields: Set<AnnouncementReadField>): Set<AnnouncementReadField> = when (this) {
    ContentReadPreset.DEFAULT -> defaultFields
    ContentReadPreset.TITLE_AND_ARTIST -> setOf(AnnouncementReadField.TITLE, AnnouncementReadField.ARTIST)
    ContentReadPreset.TITLE_ONLY -> setOf(AnnouncementReadField.TITLE)
    ContentReadPreset.CUSTOM -> defaultFields
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
    modifier: Modifier = Modifier,
    onSelected: (T) -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Column(
        modifier = Modifier.fillMaxWidth().then(modifier),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
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
    valueLabel: (Float) -> String,
    onValueChange: (Float) -> Unit,
) {
    var sliderValue by remember(value) { mutableFloatStateOf(value) }
    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
        Text(label)
        Text(valueLabel(sliderValue), color = MaterialTheme.colorScheme.onSurfaceVariant)
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

private fun PlaybackCollection.toGeneralSettingsTarget(): GeneralSettingsTarget? = when (this) {
    PlaybackCollection.ALBUM -> GeneralSettingsTarget.ALBUM
    PlaybackCollection.PLAYLIST -> GeneralSettingsTarget.PLAYLIST
    PlaybackCollection.ALGORITHMIC -> GeneralSettingsTarget.ALGORITHMIC
    PlaybackCollection.UNKNOWN -> null
}

private fun formatTime(timestamp: Long): String = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(timestamp))
private fun formatTimeOrDash(timestamp: Long?, emptyLabel: String = "없음"): String = timestamp?.let(::formatTime) ?: emptyLabel

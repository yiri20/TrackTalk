package com.trackvoice.ui

import android.Manifest
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Podcasts
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
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
import androidx.core.graphics.drawable.toBitmap
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.trackvoice.TrackVoiceViewModel
import com.trackvoice.announcement.InstalledVoice
import com.trackvoice.announcement.TtsStatus
import com.trackvoice.data.AnnouncementMode
import com.trackvoice.data.AnnouncementTiming
import com.trackvoice.data.AppSettings
import com.trackvoice.data.AppCategory
import com.trackvoice.data.categorizeApp
import com.trackvoice.data.GenderFilter
import com.trackvoice.data.UserSettings
import com.trackvoice.data.VoiceLanguage
import com.trackvoice.data.MusicTreatment
import com.trackvoice.data.TrackStartBehavior
import com.trackvoice.data.AudioDeviceSettings
import com.trackvoice.announcement.ConnectedAudioDevice
import com.trackvoice.media.PlaybackEvent
import com.trackvoice.media.PlaybackStatus
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class AppSection(val title: String, val navLabel: String) {
    HOME("TrackTalk", "홈"),
    GENERAL("안내 설정", "안내"),
    APPS("앱 설정", "앱"),
    VOICE("음성 설정", "음성"),
    DIAGNOSTICS("진단", "진단"),
}

private val TrackVoiceCardShape = RoundedCornerShape(14.dp)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackVoiceApp(viewModel: TrackVoiceViewModel) {
    val settings by viewModel.userSettings.collectAsStateWithLifecycle()
    val appSettings by viewModel.appSettings.collectAsStateWithLifecycle()
    val mediaState by viewModel.mediaState.collectAsStateWithLifecycle()
    val diagnostics by viewModel.diagnostics.collectAsStateWithLifecycle()
    val voices by viewModel.installedVoices.collectAsStateWithLifecycle()
    val connectedDevices by viewModel.connectedAudioDevices.collectAsStateWithLifecycle()
    val deviceSettings by viewModel.audioDeviceSettings.collectAsStateWithLifecycle()
    var selectedSectionName by rememberSaveable { mutableStateOf(AppSection.HOME.name) }
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

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(selectedSection.title) },
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
                    settings = settings,
                    mediaEvent = mediaState.currentEvent,
                    currentMode = mediaState.currentMode,
                    lastDetectedAt = mediaState.lastDetectedAt,
                    effectiveEnabled = mediaState.effectiveEnabled,
                    notificationAccess = diagnostics.notificationListenerConnected,
                    notificationPermissionGranted = notificationPermissionGranted,
                    onToggle = viewModel.controller::setEnabled,
                    onTest = viewModel.controller::speakTest,
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
                )

                AppSection.GENERAL -> GeneralSettingsScreen(
                    settings = settings,
                    connectedDevices = connectedDevices,
                    deviceSettings = deviceSettings,
                    onUpdate = viewModel.controller::updateUserSettings,
                    onUpdateDevice = viewModel.controller::updateAudioDeviceSettings,
                )

                AppSection.APPS -> AppSettingsScreen(
                    apps = appSettings.values.sortedBy { it.appName.lowercase(Locale.getDefault()) },
                    onUpdate = viewModel.controller::updateAppSettings,
                    onRefresh = viewModel.controller::refreshSupportedMediaApps,
                )

                AppSection.VOICE -> VoiceSettingsScreen(
                    settings = settings,
                    voices = voices,
                    ttsStatus = diagnostics.ttsState,
                    onUpdate = viewModel.controller::updateUserSettings,
                    onTest = viewModel.controller::speakTest,
                )

                AppSection.DIAGNOSTICS -> DiagnosticsScreen(diagnostics, mediaState.currentEvent)
            }
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
    NavigationBarItem(
        selected = selected == section,
        onClick = { onSelected(section) },
        icon = { Icon(icon, contentDescription = section.title) },
        label = { Text(section.navLabel, maxLines = 1) },
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
    lastDetectedAt: Long?,
    effectiveEnabled: Boolean,
    notificationAccess: Boolean,
    notificationPermissionGranted: Boolean,
    onToggle: (Boolean) -> Unit,
    onTest: () -> Unit,
    onTogglePlayback: () -> Unit,
    onOpenPermission: () -> Unit,
    onRequestNotificationPermission: () -> Unit,
    onOpenGeneral: () -> Unit,
) {
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
            CurrentTrackCard(mediaEvent, currentMode, lastDetectedAt, onTogglePlayback)
        }
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = TrackVoiceCardShape,
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("음성 테스트", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        "현재 설정으로 안내 음성을 확인합니다.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Button(onClick = onTest) {
                        Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("테스트 재생")
                    }
                }
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
                    Text("안내 설정", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("${currentMode.label} · ${settings.delaySeconds}초 후 안내")
                    TextButton(onClick = onOpenGeneral) { Text("설정 열기") }
                }
            }
        }
    }
}

@Composable
private fun NotificationPermissionCard(onRequestPermission: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = TrackVoiceCardShape,
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("상태 알림 권한", fontWeight = FontWeight.Bold)
            Text(
                "상단바 바로가기 알림을 표시하려면 알림 권한이 필요합니다. 음악 감지 권한과는 별개입니다.",
                style = MaterialTheme.typography.bodyMedium,
            )
            OutlinedButton(onClick = onRequestPermission) { Text("알림 허용") }
        }
    }
}

@Composable
private fun StatusCard(enabled: Boolean, effectiveEnabled: Boolean, onToggle: (Boolean) -> Unit) {
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
                Text("음성 안내", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    when {
                        !effectiveEnabled -> "OFF · 음성 안내가 꺼져 있습니다."
                        !enabled -> "자동 활성화 · 조건에 맞을 때 안내합니다."
                        else -> "ON · 새 곡을 안내합니다."
                    },
                )
            }
            Switch(checked = enabled, onCheckedChange = onToggle)
        }
    }
}

@Composable
private fun PermissionCard(onOpenPermission: () -> Unit) {
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
                Text("권한이 필요합니다", fontWeight = FontWeight.Bold)
            }
            Text(
                "음악 정보를 읽으려면 알림 접근 권한을 허용해 주세요.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Button(onClick = onOpenPermission) { Text("권한 설정") }
        }
    }
}

@Composable
private fun CurrentTrackCard(
    event: PlaybackEvent?,
    mode: AnnouncementMode,
    lastDetectedAt: Long?,
    onTogglePlayback: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = TrackVoiceCardShape,
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("현재 재생", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            if (event == null) {
                Text("재생 중인 음악이 없습니다.")
                Text("음악 앱을 재생하면 여기에 표시됩니다.", style = MaterialTheme.typography.bodySmall)
            } else {
                TrackField("앱", event.sourceAppName)
                TrackField("곡", event.title ?: "제목 없음")
                TrackField("아티스트", event.artist ?: "아티스트 없음")
                TrackField("앨범", event.album ?: "앨범 없음")
                AssistChip(onClick = {}, label = { Text("${mode.label} · ${event.playbackState.label()}") })
                OutlinedButton(onClick = onTogglePlayback, modifier = Modifier.fillMaxWidth()) {
                    Icon(
                        if (event.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = null,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(if (event.isPlaying) "음악 일시정지" else "음악 재생")
                }
                if (lastDetectedAt != null) Text("마지막 감지 ${formatTime(lastDetectedAt)}", style = MaterialTheme.typography.bodySmall)
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
    onUpdate: ((UserSettings) -> UserSettings) -> Unit,
    onUpdateDevice: (AudioDeviceSettings) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            SettingCard("기본 동작") {
                SettingSwitchRow("음성 안내", "상단바의 안내 타일과 동기화됩니다.", settings.enabled) { enabled ->
                    onUpdate { current -> current.copy(enabled = enabled) }
                }
                SettingSwitchRow("이어폰에서만 안내", "외부 오디오가 연결될 때만 안내합니다.", settings.headphonesOnly) { enabled ->
                    onUpdate { current -> current.copy(headphonesOnly = enabled) }
                }
                SettingSwitchRow("스피커에서는 안내하지 않기", "스피커로 재생할 때 안내를 건너뜁니다.", settings.suppressDuringSpeakerPlayback) { enabled ->
                    onUpdate { current -> current.copy(suppressDuringSpeakerPlayback = enabled) }
                }
                SettingSwitchRow("상단바 바로가기", "알림을 눌러 앱으로 바로 이동합니다.", settings.showStatusNotification) { enabled ->
                    onUpdate { current -> current.copy(showStatusNotification = enabled) }
                }
            }
        }
        item {
            SettingCard("연결 기기") {
                Text("기기별로 안내 사용과 자동 켜짐을 정할 수 있습니다.", style = MaterialTheme.typography.bodySmall)
                if (connectedDevices.isEmpty()) {
                    Text("연결된 이어폰이나 Bluetooth 기기가 없습니다.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                connectedDevices.forEach { device ->
                    val saved = deviceSettings[device.key] ?: AudioDeviceSettings(device.key, device.name)
                    Text(device.name, fontWeight = FontWeight.SemiBold)
                    Text(device.typeLabel, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    SettingSwitchRow("이 기기에서 사용", "연결 중인 이 기기에 안내합니다.", saved.enabled) {
                        onUpdateDevice(saved.copy(enabled = it))
                    }
                    SettingSwitchRow("연결하면 자동 켜기", "이 기기가 연결되면 안내를 켭니다.", saved.autoEnable) {
                        onUpdateDevice(saved.copy(autoEnable = it))
                    }
                    HorizontalDivider()
                }
            }
        }
        item {
            SettingCard("곡 안내") {
                OptionDropdown("재생 시작", settings.trackStartBehavior, TrackStartBehavior.values().toList(), { it.label }) { value ->
                    onUpdate { it.copy(trackStartBehavior = value) }
                }
                OptionDropdown("안내 중 음악", settings.musicTreatment, MusicTreatment.values().toList(), { it.label }) { value ->
                    onUpdate { it.copy(musicTreatment = value) }
                }
                OptionDropdown("안내 시점", settings.timing, AnnouncementTiming.values().toList(), { it.label }) { selectedTiming ->
                    onUpdate { current -> current.copy(timing = selectedTiming) }
                }
                OptionDropdown("읽을 내용", settings.defaultMode, AnnouncementMode.values().toList(), { it.label }) { selectedMode ->
                    onUpdate { current -> current.copy(defaultMode = selectedMode) }
                }
                SliderSetting("안내 지연", settings.delaySeconds.toFloat(), 0f..2f, "${settings.delaySeconds}초") { value ->
                    onUpdate { it.copy(delaySeconds = value.toInt()) }
                }
                SliderSetting(
                    "최소 재생 시간",
                    settings.minimumPlaybackSeconds.toFloat(),
                    0f..60f,
                    "${settings.minimumPlaybackSeconds}초",
                ) { value -> onUpdate { it.copy(minimumPlaybackSeconds = value.toInt()) } }
                SettingSwitchRow("같은 곡 다시 안내", "기본값은 같은 곡을 한 번만 안내합니다.", settings.allowRepeatAnnouncements) { enabled ->
                    onUpdate { current -> current.copy(allowRepeatAnnouncements = enabled) }
                }
            }
        }
        item {
            SettingCard("자동 켜기") {
                SettingSwitchRow("화면이 꺼지면 켜기", "화면을 끄면 안내를 시작합니다.", settings.autoEnableOnScreenOff) { enabled ->
                    onUpdate { it.copy(autoEnableOnScreenOff = enabled) }
                }
                SettingSwitchRow("화면을 켜면 원래대로", "화면을 켜면 자동 상태를 해제합니다.", settings.restoreEnabledWhenScreenOn) { enabled ->
                    onUpdate { it.copy(restoreEnabledWhenScreenOn = enabled) }
                }
                SettingSwitchRow(
                    "화면 꺼짐은 Bluetooth에서만",
                    "화면이 꺼질 때 Bluetooth 오디오가 연결된 경우에만 자동 켭니다.",
                    settings.bluetoothOnlyForAutoEnable,
                ) { enabled ->
                    onUpdate { it.copy(bluetoothOnlyForAutoEnable = enabled) }
                }
            }
        }
    }
}

@Composable
private fun AppSettingsScreen(
    apps: List<AppSettings>,
    onUpdate: (AppSettings) -> Unit,
    onRefresh: () -> Unit,
) {
    val appsByCategory = remember(apps) {
        apps.groupBy { app -> categorizeApp(app.packageName, app.appName) }
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
                        "기기에서 미디어 재생을 지원하는 앱과 감지된 앱입니다.",
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = onRefresh) { Text("새로 고침") }
                }
            }
        }
        if (apps.isNotEmpty()) {
            item {
                Text(
                    "${apps.size}개 앱이 ${appsByCategory.size}개 카테고리로 정리되어 있습니다.",
                    modifier = Modifier.padding(horizontal = 4.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (apps.isEmpty()) {
            item {
                Column(Modifier.padding(horizontal = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("지원되는 음악 앱을 찾지 못했습니다.")
                    Text("앱이 미디어 세션을 만들면 자동으로 추가됩니다.", style = MaterialTheme.typography.bodySmall)
                }
            }
        } else {
            AppCategory.values().forEach { category ->
                val categoryApps = appsByCategory[category].orEmpty()
                if (categoryApps.isNotEmpty()) {
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
                category.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                category.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            "${appCount}개",
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
private fun AppSettingsCard(app: AppSettings, onUpdate: (AppSettings) -> Unit) {
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
                            app.alwaysExclude -> "항상 제외"
                            app.enabled -> "곡 안내 사용 중"
                            else -> "곡 안내 꺼짐"
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
            TextButton(onClick = { expanded = !expanded }) {
                Text(if (expanded) "세부 설정 접기" else "세부 설정")
            }
                if (expanded) {
                OptionDropdown("읽을 내용", app.mode, AnnouncementMode.values().toList(), { it.label }) {
                    onUpdate(app.copy(mode = it))
                }
                OptionDropdown(
                    "안내 시점",
                    app.timing,
                    listOf(null) + AnnouncementTiming.values().toList(),
                    { it?.label ?: "기본 설정 사용" },
                ) { onUpdate(app.copy(timing = it)) }
                HorizontalDivider()
                CheckRow("제목", app.readTitle) { onUpdate(app.copy(readTitle = it)) }
                CheckRow("아티스트", app.readArtist) { onUpdate(app.copy(readArtist = it)) }
                CheckRow("트랙 번호", app.readTrackNumber) { onUpdate(app.copy(readTrackNumber = it)) }
                CheckRow("이 앱은 항상 제외", app.alwaysExclude) {
                    onUpdate(
                        app.copy(
                            alwaysExclude = it,
                            enabled = if (it) false else app.enabled,
                        ),
                    )
                }
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
    onUpdate: ((UserSettings) -> UserSettings) -> Unit,
    onTest: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            SettingCard("음성 선택") {
                OptionDropdown("기본 언어", settings.voiceLanguage, VoiceLanguage.values().toList(), { it.label }) { selectedLanguage ->
                    onUpdate { current -> current.copy(voiceLanguage = selectedLanguage, voiceName = null) }
                }
                Text(
                    "한글·영문·일문·중문이 섞이면 구간별 음성으로 자동 전환합니다.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val genderOptions = listOf(GenderFilter.ANY, GenderFilter.FEMALE, GenderFilter.MALE)
                val visibleGender = settings.genderFilter.takeIf { it in genderOptions } ?: GenderFilter.ANY
                OptionDropdown("성별", visibleGender, genderOptions, { it.label }) { selectedGender ->
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
                    "목소리",
                    selectedVoice,
                    listOf(null) + filteredVoices,
                    { voice -> voice?.label ?: "자동 선택" },
                ) { voice -> onUpdate { it.copy(voiceName = voice?.name) } }
                Text(
                    if (filteredVoices.isEmpty()) "선택한 언어와 성별의 설치 음성이 없습니다."
                    else "선택 가능한 ${visibleGender.label} ${filteredVoices.size}개",
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    if (ttsStatus.status == TtsStatus.READY) ttsStatus.message else "${ttsStatus.message}",
                    color = if (ttsStatus.status == TtsStatus.ERROR) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item {
            SettingCard("말하기 조절") {
                SliderSetting("속도", settings.speechRate, 0.5f..2f, "${"%.1f".format(Locale.getDefault(), settings.speechRate)}x") { value ->
                    onUpdate { current -> current.copy(speechRate = value) }
                }
                SliderSetting("높이", settings.pitch, 0.5f..2f, "${"%.1f".format(Locale.getDefault(), settings.pitch)}x") { value ->
                    onUpdate { current -> current.copy(pitch = value) }
                }
                SliderSetting("음량", settings.volume, 0f..1f, "${(settings.volume * 100).toInt()}%") { value ->
                    onUpdate { current -> current.copy(volume = value) }
                }
                SettingSwitchRow("기기 음량 올리기", "안내 중에만 미디어 음량을 올리고 복원합니다.", settings.raiseDeviceVolume) { enabled ->
                    onUpdate { it.copy(raiseDeviceVolume = enabled) }
                }
                if (settings.raiseDeviceVolume) {
                    SliderSetting(
                        "안내 시 기기 음량",
                        settings.deviceVolumePercent.toFloat(),
                        50f..100f,
                        "${settings.deviceVolumePercent}%",
                    ) { value -> onUpdate { it.copy(deviceVolumePercent = value.toInt()) } }
                }
                Button(onClick = onTest) {
                    Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("테스트 재생")
                }
                Text("예: 트랙 3번, Glass Eyes · Radiohead", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun DiagnosticsScreen(
    diagnostics: com.trackvoice.DiagnosticsState,
    event: PlaybackEvent?,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            SettingCard("연결 상태") {
                DiagnosticRow("알림 접근", if (diagnostics.notificationListenerConnected) "연결됨" else "연결되지 않음", diagnostics.notificationListenerConnected)
                DiagnosticRow("활성 음악 세션", diagnostics.activeSessionCount.toString(), diagnostics.activeSessionCount > 0)
                DiagnosticRow("선택한 앱", diagnostics.selectedSourcePackage ?: "없음", diagnostics.selectedSourcePackage != null)
                DiagnosticRow("음성 엔진", diagnostics.ttsState.message, diagnostics.ttsState.status == TtsStatus.READY)
            }
        }
        item {
            SettingCard("최근 기록") {
                DiagnosticRow("곡 정보 감지", formatTimeOrDash(diagnostics.lastMetadataEventAt), diagnostics.lastMetadataEventAt != null)
                DiagnosticRow("재생 상태 감지", formatTimeOrDash(diagnostics.lastPlaybackStateEventAt), diagnostics.lastPlaybackStateEventAt != null)
                DiagnosticRow("마지막 안내", diagnostics.lastAnnouncementMessage, diagnostics.lastAnnouncementSucceeded == true)
                DiagnosticRow("안내 시각", formatTimeOrDash(diagnostics.lastAnnouncementAt), diagnostics.lastAnnouncementAt != null)
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
                    Text("개인정보", fontWeight = FontWeight.Bold)
                    Text("곡 정보는 안내에만 사용하며 서버에 저장하지 않습니다.")
                }
            }
        }
        if (event != null) {
            item {
                Text("현재 곡 정보", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("${event.sourcePackageName} · ${event.title ?: "제목 없음"} · ${event.artist ?: "아티스트 없음"}", style = MaterialTheme.typography.bodySmall)
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
                !notificationAccess -> "권한 필요"
                enabled -> "ON"
                else -> "OFF"
            },
            tint = color,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(4.dp))
        Text(
            when {
                !notificationAccess -> "권한"
                enabled -> "ON"
                else -> "OFF"
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

private fun formatTime(timestamp: Long): String = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(timestamp))
private fun formatTimeOrDash(timestamp: Long?): String = timestamp?.let(::formatTime) ?: "없음"

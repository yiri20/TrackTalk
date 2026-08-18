package com.trackvoice.quicksettings

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.trackvoice.R
import com.trackvoice.TrackVoiceApplication
import com.trackvoice.data.UserSettings
import com.trackvoice.localization.localizedString
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class TrackVoicePlaybackTileService : TileService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var settingsJob: Job? = null
    private var latestSettings = UserSettings()

    private val application: TrackVoiceApplication
        get() = getApplication() as TrackVoiceApplication

    override fun onStartListening() {
        super.onStartListening()
        application.controller.refreshMediaSessions()
        settingsJob?.cancel()
        settingsJob = scope.launch {
            application.repository.userSettings.collectLatest { settings ->
                latestSettings = settings
                renderTile(application.controller.isPlaybackPlaying(), settings)
            }
        }
        renderTile(application.controller.isPlaybackPlaying(), latestSettings)
    }

    override fun onStopListening() {
        settingsJob?.cancel()
        settingsJob = null
        super.onStopListening()
    }

    override fun onClick() {
        super.onClick()
        renderTile(application.controller.togglePlayback(), latestSettings)
    }

    override fun onTileRemoved() {
        settingsJob?.cancel()
        settingsJob = null
        super.onTileRemoved()
    }

    override fun onDestroy() {
        settingsJob?.cancel()
        scope.cancel()
        super.onDestroy()
    }

    private fun renderTile(isPlaying: Boolean?, settings: UserSettings) {
        qsTile?.let { tile ->
            tile.state = when (isPlaying) {
                true -> Tile.STATE_ACTIVE
                false -> Tile.STATE_INACTIVE
                null -> Tile.STATE_UNAVAILABLE
            }
            tile.label = localizedString(
                settings.appLanguage,
                if (isPlaying == true) R.string.playback_tile_pause_label else R.string.playback_tile_play_label,
            )
            tile.contentDescription = localizedString(
                settings.appLanguage,
                when (isPlaying) {
                    true -> R.string.playback_tile_pause_description
                    false -> R.string.playback_tile_play_description
                    null -> R.string.playback_tile_unavailable_description
                },
            )
            tile.updateTile()
        }
    }
}

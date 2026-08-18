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

class TrackVoiceTileService : TileService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var settingsJob: Job? = null
    private var latestSettings = UserSettings()

    private val application: TrackVoiceApplication
        get() = getApplication() as TrackVoiceApplication

    override fun onStartListening() {
        super.onStartListening()
        settingsJob?.cancel()
        settingsJob = scope.launch {
            application.repository.userSettings.collectLatest { settings ->
                latestSettings = settings
                renderTile(settings)
            }
        }
        renderTile(latestSettings)
    }

    override fun onStopListening() {
        settingsJob?.cancel()
        settingsJob = null
        super.onStopListening()
    }

    override fun onClick() {
        super.onClick()
        val next = !latestSettings.enabled
        application.controller.setEnabled(next)
        latestSettings = latestSettings.copy(enabled = next)
        renderTile(latestSettings)
    }

    override fun onTileAdded() {
        super.onTileAdded()
        renderTile(latestSettings)
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

    private fun renderTile(settings: UserSettings) {
        qsTile?.let { tile ->
            tile.state = if (settings.enabled) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            tile.label = "TrackTalk"
            tile.contentDescription = localizedString(
                settings.appLanguage,
                if (settings.enabled) {
                    R.string.announcement_tile_on_description
                } else {
                    R.string.announcement_tile_off_description
                },
            )
            tile.updateTile()
        }
    }
}

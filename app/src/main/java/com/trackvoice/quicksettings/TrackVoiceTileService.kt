package com.trackvoice.quicksettings

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.trackvoice.TrackVoiceApplication
import com.trackvoice.data.UserSettings
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
                renderTile(settings.enabled)
            }
        }
        renderTile(latestSettings.enabled)
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
        renderTile(next)
    }

    override fun onTileAdded() {
        super.onTileAdded()
        renderTile(latestSettings.enabled)
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

    private fun renderTile(enabled: Boolean) {
        qsTile?.let { tile ->
            tile.state = if (enabled) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            tile.label = "TrackTalk"
            tile.contentDescription = if (enabled) "TrackTalk 음성 안내 켜짐" else "TrackTalk 음성 안내 꺼짐"
            tile.updateTile()
        }
    }
}

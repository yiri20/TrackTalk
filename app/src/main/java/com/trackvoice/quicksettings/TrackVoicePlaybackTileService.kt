package com.trackvoice.quicksettings

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.trackvoice.TrackVoiceApplication

class TrackVoicePlaybackTileService : TileService() {
    private val application: TrackVoiceApplication
        get() = getApplication() as TrackVoiceApplication

    override fun onStartListening() {
        super.onStartListening()
        application.controller.refreshMediaSessions()
        renderTile(application.controller.isPlaybackPlaying())
    }

    override fun onClick() {
        super.onClick()
        renderTile(application.controller.togglePlayback())
    }

    private fun renderTile(isPlaying: Boolean?) {
        qsTile?.let { tile ->
            tile.state = when (isPlaying) {
                true -> Tile.STATE_ACTIVE
                false -> Tile.STATE_INACTIVE
                null -> Tile.STATE_UNAVAILABLE
            }
            tile.label = if (isPlaying == true) "음악 일시정지" else "음악 재생"
            tile.contentDescription = when (isPlaying) {
                true -> "현재 음악을 일시정지합니다"
                false -> "현재 음악을 재생합니다"
                null -> "재생 중인 음악이 없습니다"
            }
            tile.updateTile()
        }
    }
}

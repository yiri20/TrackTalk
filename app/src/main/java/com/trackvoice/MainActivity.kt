package com.trackvoice

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import com.trackvoice.ui.TrackVoiceApp
import com.trackvoice.ui.theme.TrackVoiceTheme

class MainActivity : ComponentActivity() {
    private val viewModel by viewModels<TrackVoiceViewModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            TrackVoiceTheme {
                TrackVoiceApp(viewModel, this@MainActivity)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshNotificationAccess(this)
        viewModel.refreshBilling()
    }
}

package com.trackvoice

import android.app.Application
import com.trackvoice.data.DataStoreRepository

class TrackVoiceApplication : Application() {
    lateinit var repository: DataStoreRepository
        private set
    lateinit var controller: TrackVoiceController
        private set

    override fun onCreate() {
        super.onCreate()
        repository = DataStoreRepository(this)
        controller = TrackVoiceController(this, repository)
    }
}

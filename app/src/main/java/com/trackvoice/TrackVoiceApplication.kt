package com.trackvoice

import android.app.Application
import com.trackvoice.data.DataStoreRepository
import com.trackvoice.monetization.PlayBillingManager

class TrackVoiceApplication : Application() {
    lateinit var repository: DataStoreRepository
        private set
    lateinit var controller: TrackVoiceController
        private set
    lateinit var billingManager: PlayBillingManager
        private set

    override fun onCreate() {
        super.onCreate()
        repository = DataStoreRepository(this)
        controller = TrackVoiceController(this, repository)
        billingManager = PlayBillingManager(this)
        billingManager.connect()
    }
}

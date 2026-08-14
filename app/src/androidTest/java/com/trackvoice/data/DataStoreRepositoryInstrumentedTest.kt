package com.trackvoice.data

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DataStoreRepositoryInstrumentedTest {
    @Test
    fun outputPolicySurvivesRepositoryRecreation() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repository = DataStoreRepository(context)
        val original = repository.currentUserSettings()

        try {
            repository.updateUserSettings { current ->
                current.copy(outputPolicy = AnnouncementOutputPolicy.ALL_OUTPUTS)
            }

            val recreatedRepository = DataStoreRepository(context)
            assertEquals(
                AnnouncementOutputPolicy.ALL_OUTPUTS,
                recreatedRepository.currentUserSettings().outputPolicy,
            )
        } finally {
            repository.updateUserSettings { original }
        }
    }
}

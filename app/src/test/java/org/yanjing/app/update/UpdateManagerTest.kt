package org.yanjing.app.update

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.WorkManager
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36, 37])
class UpdateManagerTest {
    @Test fun onDemandSchedulerOpensItsDatabaseAndEnqueuesPeriodicChecks() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val store = UpdateStore(app)
        store.preferences.edit().clear().putBoolean("autoCheck", true)
            .putLong("lastChecked", System.currentTimeMillis()).commit()
        UpdateManager(app).use { manager ->
            manager.start()
            assertNotEquals(UpdatePhase.ERROR, store.read().phase)
            val scheduler = WorkManager.getInstance(app)
            val checks = scheduler.getWorkInfosForUniqueWork("update-periodic").get(10, TimeUnit.SECONDS)
            assertEquals(1, checks.size)
            assertFalse(checks.single().state.isFinished)
            manager.option("autoCheck", false)
            scheduler.cancelUniqueWork("update-periodic").result.get(10, TimeUnit.SECONDS)
        }
    }

    @Test fun checkingNeverHidesAnOngoingDownloadOrVerifiedInstaller() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val store = UpdateStore(app)
        store.preferences.edit().clear().commit()
        store.release(ReleaseInfo(99, "99.0.0",
            "https://github.com/KiRenk0/yanjing-bible/releases/download/v99.0.0/yanjing-99.0.0.apk",
            1_000_000, "a".repeat(64), "test", 26))
        for (phase in listOf(UpdatePhase.DOWNLOADING, UpdatePhase.READY)) {
            store.status(phase, "preserve", 73)
            UpdateManager(app) { error("Checking must not touch the scheduler in phase $phase") }.use { manager ->
                manager.check()
                assertEquals(phase, store.read().phase)
                assertEquals("preserve", store.read().message)
                assertEquals(73, store.read().progress)
            }
        }
    }
}

package com.aif31.pocket.settings

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.NetworkType
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.WorkManagerTestInitHelper
import com.aif31.pocket.notifications.WorkFxRateAlertScheduler
import java.time.LocalTime
import java.util.UUID
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class ReminderSchedulerTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @After
    fun tearDown() {
        WorkManagerTestInitHelper.closeWorkDatabase()
    }

    @Test
    fun changing_an_active_reminder_reanchors_the_periodic_schedule() {
        WorkManagerTestInitHelper.initializeTestWorkManager(context)
        val scheduler: ReminderScheduler = WorkReminderScheduler(context)
        val manager = WorkManager.getInstance(context)

        scheduler.apply(enabled = true, time = LocalTime.of(20, 0))
        val originalId = manager.singleReminderId()

        scheduler.apply(enabled = true, time = LocalTime.of(22, 30))

        assertNotEquals(originalId, manager.singleReminderId())
    }

    @Test
    fun `the rate alert waits for a network connection and turning it off cancels it`() {
        WorkManagerTestInitHelper.initializeTestWorkManager(context)
        val scheduler: ReminderScheduler = WorkFxRateAlertScheduler(context)
        val manager = WorkManager.getInstance(context)

        scheduler.apply(enabled = true, time = LocalTime.of(9, 0))
        val work = manager.getWorkInfosForUniqueWork("daily-fx-rate").get().single()
        assertEquals(WorkInfo.State.ENQUEUED, work.state)
        assertEquals(NetworkType.CONNECTED, work.constraints.requiredNetworkType)

        scheduler.apply(enabled = false, time = LocalTime.of(9, 0))
        assertEquals(WorkInfo.State.CANCELLED, manager.getWorkInfosForUniqueWork("daily-fx-rate").get().single().state)
    }

    private fun WorkManager.singleReminderId(): UUID =
        getWorkInfosForUniqueWork("daily-spending-review").get().single().id
}

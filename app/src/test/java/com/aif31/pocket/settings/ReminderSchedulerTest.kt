package com.aif31.pocket.settings

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.WorkManager
import androidx.work.testing.WorkManagerTestInitHelper
import java.time.LocalTime
import java.util.UUID
import org.junit.After
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

    private fun WorkManager.singleReminderId(): UUID =
        getWorkInfosForUniqueWork("daily-spending-review").get().single().id
}

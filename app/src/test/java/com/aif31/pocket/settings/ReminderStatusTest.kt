package com.aif31.pocket.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class ReminderStatusTest {
    @Test fun status_requires_intent_permission_channel_and_enqueued_work() {
        assertEquals(ReminderStatus.Off, resolveReminderStatus(false, false, false, false))
        assertEquals(ReminderStatus.PermissionRequired, resolveReminderStatus(true, false, true, true))
        assertEquals(ReminderStatus.Failed, resolveReminderStatus(true, true, false, true))
        assertEquals(ReminderStatus.Failed, resolveReminderStatus(true, true, true, false))
        assertEquals(ReminderStatus.Scheduled, resolveReminderStatus(true, true, true, true))
    }
}

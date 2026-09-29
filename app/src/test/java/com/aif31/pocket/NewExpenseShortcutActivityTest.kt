package com.aif31.pocket

import android.content.ComponentName
import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class NewExpenseShortcutActivityTest {

    @Test
    fun shortcut_launch_hands_a_new_expense_request_to_the_running_app_and_closes_itself() {
        // The launcher starts static shortcuts with NEW_TASK | CLEAR_TASK and may attach anything it likes.
        val launch = Intent(MainActivity.ACTION_NEW_EXPENSE)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            .putExtra("unexpected", "ignored")
        val controller = Robolectric.buildActivity(NewExpenseShortcutActivity::class.java, launch).create()

        val forwarded = shadowOf(controller.get()).nextStartedActivity
        assertEquals(ComponentName("com.aif31.pocket", "com.aif31.pocket.MainActivity"), forwarded.component)
        assertEquals(MainActivity.ACTION_NEW_EXPENSE, forwarded.action)
        assertNull(forwarded.extras)
        // An already running MainActivity must receive onNewIntent, keeping its open forms underneath.
        val expectedFlags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        assertEquals(expectedFlags, forwarded.flags)
        assertTrue(controller.get().isFinishing)
    }
}

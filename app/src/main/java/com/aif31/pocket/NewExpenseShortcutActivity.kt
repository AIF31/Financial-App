package com.aif31.pocket

import android.app.Activity
import android.content.Intent
import android.os.Bundle

/**
 * Invisible target of the static "Nuevo gasto" shortcut.
 *
 * Launchers start static shortcuts with NEW_TASK | CLEAR_TASK, which would destroy a running MainActivity and any
 * unsaved form in it. This activity lives in its own task, so only it is cleared; it then hands the request to
 * MainActivity, which receives it through onNewIntent. Nothing from the incoming intent is forwarded.
 */
class NewExpenseShortcutActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startActivity(
            Intent(this, MainActivity::class.java)
                .setAction(MainActivity.ACTION_NEW_EXPENSE)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        )
        finish()
    }
}

package com.aif31.pocket

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import com.aif31.pocket.ui.PocketTheme
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    private var restoreCandidate by mutableStateOf<ByteArray?>(null)

    private val createBackup = registerForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        uri?.let { writeExport(it, backup = true) }
    }
    private val createCsv = registerForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        uri?.let { writeExport(it, backup = false) }
    }
    private val openBackup = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { target ->
            lifecycleScope.launch {
                restoreCandidate = withContext(Dispatchers.IO) {
                    contentResolver.openInputStream(target)?.use { input ->
                        val output = ByteArrayOutputStream()
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        var total = 0
                        while (total <= MAX_BACKUP_BYTES) {
                            val count = input.read(buffer, 0, minOf(buffer.size, MAX_BACKUP_BYTES + 1 - total))
                            if (count < 0) break
                            output.write(buffer, 0, count)
                            total += count
                        }
                        output.toByteArray()
                    }
                }
            }
        }
    }
    private val requestNotifications = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val openExpense = intent?.action == ACTION_NEW_EXPENSE
        setContent {
            PocketTheme {
                PocketApp(
                    ledger = (application as PocketApplication).ledger,
                    preferences = (application as PocketApplication).preferences,
                    reminderScheduler = (application as PocketApplication).reminderScheduler,
                    openNewExpense = openExpense,
                    restoreCandidate = restoreCandidate,
                    onRestoreCandidateHandled = { restoreCandidate = null },
                    onCreateBackup = { createBackup.launch("pocket-${java.time.LocalDate.now()}.pocketbackup") },
                    onCreateCsv = { createCsv.launch("pocket-movimientos-${java.time.LocalDate.now()}.csv") },
                    onPickBackup = { openBackup.launch(arrayOf("application/octet-stream", "application/json", "*/*")) },
                    onRequestNotificationPermission = {
                        if (android.os.Build.VERSION.SDK_INT >= 33) requestNotifications.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                    },
                )
            }
        }
    }

    private fun writeExport(uri: Uri, backup: Boolean) {
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                val ledger = (application as PocketApplication).ledger
                val bytes = if (backup) ledger.exportBackup() else ledger.exportCsv()
                contentResolver.openOutputStream(uri, "wt")?.use { it.write(bytes) }
            }
        }
    }

    companion object {
        const val ACTION_NEW_EXPENSE = "com.aif31.pocket.NEW_EXPENSE"
        private const val MAX_BACKUP_BYTES = 10 * 1024 * 1024
    }
}

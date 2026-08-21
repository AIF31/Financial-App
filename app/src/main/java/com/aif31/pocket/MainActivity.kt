package com.aif31.pocket

import android.content.Intent
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.aif31.pocket.ui.PocketTheme

class MainActivity : ComponentActivity() {
    private var pendingBackup: ByteArray? = null
    private var pendingCsv: ByteArray? = null
    private var restoreCandidate by mutableStateOf<ByteArray?>(null)

    private val createBackup = registerForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        uri?.let { target -> pendingBackup?.let { contentResolver.openOutputStream(target)?.use { stream -> stream.write(it) } } }
        pendingBackup = null
    }
    private val createCsv = registerForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        uri?.let { target -> pendingCsv?.let { contentResolver.openOutputStream(target)?.use { stream -> stream.write(it) } } }
        pendingCsv = null
    }
    private val openBackup = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        restoreCandidate = uri?.let { contentResolver.openInputStream(it)?.use { stream -> stream.readBytes() } }
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
                    onCreateBackup = { bytes ->
                        pendingBackup = bytes
                        createBackup.launch("pocket-${java.time.LocalDate.now()}.pocketbackup")
                    },
                    onCreateCsv = { bytes ->
                        pendingCsv = bytes
                        createCsv.launch("pocket-movimientos-${java.time.LocalDate.now()}.csv")
                    },
                    onPickBackup = { openBackup.launch(arrayOf("application/octet-stream", "application/json", "*/*")) },
                    onRequestNotificationPermission = {
                        if (android.os.Build.VERSION.SDK_INT >= 33) requestNotifications.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                    },
                )
            }
        }
    }

    companion object {
        const val ACTION_NEW_EXPENSE = "com.aif31.pocket.NEW_EXPENSE"
    }
}

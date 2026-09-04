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
import com.aif31.pocket.data.LedgerCommand
import com.aif31.pocket.ui.PocketTheme
import java.io.ByteArrayOutputStream
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    private var restoreCandidate by mutableStateOf<ByteArray?>(null)
    private var operationMessage by mutableStateOf<String?>(null)

    private val createBackup = registerForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        uri?.let { writeExport(it, backup = true) }
    }
    private val createCsv = registerForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        uri?.let { writeExport(it, backup = false) }
    }
    private val openBackup = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { target ->
            lifecycleScope.launch {
                try {
                    restoreCandidate = withContext(Dispatchers.IO) {
                        val input = contentResolver.openInputStream(target)
                            ?: throw IOException("The selected backup could not be opened")
                        input.use {
                            val output = ByteArrayOutputStream()
                            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                            var total = 0
                            while (total <= MAX_BACKUP_BYTES) {
                                val count = it.read(buffer, 0, minOf(buffer.size, MAX_BACKUP_BYTES + 1 - total))
                                if (count < 0) break
                                output.write(buffer, 0, count)
                                total += count
                            }
                            output.toByteArray()
                        }
                    }
                } catch (error: Exception) {
                    if (error is CancellationException) throw error
                    operationMessage = "No se pudo leer el backup. Comprueba el archivo y vuelve a intentarlo."
                }
            }
        }
    }
    private val requestNotifications = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        catchUpPeriods()
        val openExpense = intent?.action == ACTION_NEW_EXPENSE
        setContent {
            PocketTheme {
                PocketApp(
                    ledger = (application as PocketApplication).ledger,
                    preferences = (application as PocketApplication).preferences,
                    exchangeRates = (application as PocketApplication).exchangeRates,
                    reminderScheduler = (application as PocketApplication).reminderScheduler,
                    openNewExpense = openExpense,
                    restoreCandidate = restoreCandidate,
                    onRestoreCandidateHandled = { restoreCandidate = null },
                    operationMessage = operationMessage,
                    onOperationMessageHandled = { operationMessage = null },
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

    override fun onResume() {
        super.onResume()
        catchUpPeriods()
    }

    private fun catchUpPeriods() {
        lifecycleScope.launch {
            val application = application as PocketApplication
            val preferredStartDay = application.preferences.state.first().futurePeriodStartDay
            application.ledger.execute(LedgerCommand.CatchUpPeriods(preferredStartDay))
        }
    }

    private fun writeExport(uri: Uri, backup: Boolean) {
        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val ledger = (application as PocketApplication).ledger
                    val bytes = if (backup) ledger.exportBackup() else ledger.exportCsv()
                    val output = contentResolver.openOutputStream(uri, "wt")
                        ?: throw IOException("The selected document could not be opened")
                    output.use { it.write(bytes) }
                }
                operationMessage = if (backup) "Backup creado." else "CSV exportado."
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                operationMessage = if (backup) {
                    "No se pudo crear el backup. Comprueba el destino y vuelve a intentarlo."
                } else {
                    "No se pudo exportar el CSV. Comprueba el destino y vuelve a intentarlo."
                }
            }
        }
    }

    companion object {
        const val ACTION_NEW_EXPENSE = "com.aif31.pocket.NEW_EXPENSE"
        private const val MAX_BACKUP_BYTES = 10 * 1024 * 1024
    }
}

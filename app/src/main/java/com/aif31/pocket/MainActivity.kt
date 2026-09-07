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
    private var retryOperation by mutableStateOf<DocumentOperation?>(null)

    private val createBackup = registerForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        if (uri == null) showOperationMessage("Creación de backup cancelada.")
        else writeExport(uri, DocumentOperation.BACKUP)
    }
    private val createCsv = registerForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri == null) showOperationMessage("Exportación CSV cancelada.")
        else writeExport(uri, DocumentOperation.CSV)
    }
    private val openBackup = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) {
            showOperationMessage("Selección de backup cancelada.")
        } else {
            val target = uri
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
                    retryOperation = null
                } catch (error: Exception) {
                    if (error is CancellationException) throw error
                    showOperationMessage(
                        "No se pudo leer el backup. Comprueba el archivo y vuelve a intentarlo.",
                        DocumentOperation.RESTORE,
                    )
                }
            }
        }
    }
    private val requestNotifications = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        operationMessage = savedInstanceState?.getString(STATE_OPERATION_MESSAGE)
        retryOperation = savedInstanceState?.getString(STATE_RETRY_OPERATION)?.let(DocumentOperation::valueOf)
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
                    operationRetryLabel = retryOperation?.let { "Reintentar" },
                    onOperationMessageHandled = { showOperationMessage(null) },
                    onRetryOperation = ::retryDocumentOperation,
                    onCreateBackup = { launchDocumentOperation(DocumentOperation.BACKUP) },
                    onCreateCsv = { launchDocumentOperation(DocumentOperation.CSV) },
                    onPickBackup = { launchDocumentOperation(DocumentOperation.RESTORE) },
                    onSuccessfulRestore = {
                        runCatching { (application as PocketApplication).notificationBetaMetrics.reset() }
                    },
                    onRestoreCompleted = { showOperationMessage(it) },
                    onRequestNotificationPermission = {
                        if (android.os.Build.VERSION.SDK_INT >= 33) requestNotifications.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                    },
                )
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        operationMessage?.let { outState.putString(STATE_OPERATION_MESSAGE, it) }
        retryOperation?.let { outState.putString(STATE_RETRY_OPERATION, it.name) }
        super.onSaveInstanceState(outState)
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

    private fun launchDocumentOperation(operation: DocumentOperation) {
        showOperationMessage(null)
        when (operation) {
            DocumentOperation.BACKUP -> createBackup.launch("pocket-${java.time.LocalDate.now()}.pocketbackup")
            DocumentOperation.CSV -> createCsv.launch("pocket-movimientos-${java.time.LocalDate.now()}.csv")
            DocumentOperation.RESTORE -> openBackup.launch(arrayOf("application/octet-stream", "application/json", "*/*"))
        }
    }

    private fun retryDocumentOperation() {
        retryOperation?.let(::launchDocumentOperation)
    }

    private fun showOperationMessage(message: String?, retry: DocumentOperation? = null) {
        operationMessage = message
        retryOperation = retry
    }

    private fun writeExport(uri: Uri, operation: DocumentOperation) {
        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val ledger = (application as PocketApplication).ledger
                    val bytes = if (operation == DocumentOperation.BACKUP) ledger.exportBackup() else ledger.exportCsv()
                    val output = contentResolver.openOutputStream(uri, "wt")
                        ?: throw IOException("The selected document could not be opened")
                    output.use { it.write(bytes) }
                }
                showOperationMessage(if (operation == DocumentOperation.BACKUP) "Backup creado." else "CSV exportado.")
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                val message = if (operation == DocumentOperation.BACKUP) {
                    "No se pudo crear el backup. Comprueba el destino y vuelve a intentarlo."
                } else {
                    "No se pudo exportar el CSV. Comprueba el destino y vuelve a intentarlo."
                }
                showOperationMessage(message, operation)
            }
        }
    }

    companion object {
        const val ACTION_NEW_EXPENSE = "com.aif31.pocket.NEW_EXPENSE"
        private const val MAX_BACKUP_BYTES = 10 * 1024 * 1024
        private const val STATE_OPERATION_MESSAGE = "operation_message"
        private const val STATE_RETRY_OPERATION = "retry_operation"
    }
}

private enum class DocumentOperation { BACKUP, CSV, RESTORE }

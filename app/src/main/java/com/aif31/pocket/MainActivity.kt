package com.aif31.pocket

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.AtomicFile
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.aif31.pocket.data.LedgerCommand
import com.aif31.pocket.data.LedgerResult
import com.aif31.pocket.ui.PocketTheme
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    private val recovery by viewModels<RecoveryViewModel>()
    private lateinit var dateCoordinator: ForegroundDateCoordinator

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
                    val bytes = withContext(Dispatchers.IO) {
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
                    recovery.setRestoreCandidate(bytes)
                    recovery.showOperationMessage(null)
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
        enableEdgeToEdge()
        val pocketApplication = application as PocketApplication
        dateCoordinator = ForegroundDateCoordinator(
            initialDate = LocalDate.MIN,
            clock = pocketApplication.clock,
            zoneId = pocketApplication.budgetZone,
        ) { catchUpPeriods() }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (true) {
                    delay(dateCoordinator.refresh())
                }
            }
        }
        val openExpense = intent?.action == ACTION_NEW_EXPENSE
        setContent {
            PocketTheme {
                PocketApp(
                    ledger = (application as PocketApplication).ledger,
                    preferences = (application as PocketApplication).preferences,
                    exchangeRates = (application as PocketApplication).exchangeRates,
                    reminderScheduler = (application as PocketApplication).reminderScheduler,
                    openNewExpense = openExpense,
                    restoreCandidate = recovery.restoreCandidate,
                    onRestoreCandidateHandled = recovery::clearRestoreCandidate,
                    operationMessage = recovery.operationMessage,
                    operationRetryLabel = recovery.retryOperation?.let { "Reintentar" },
                    onOperationMessageHandled = { showOperationMessage(null) },
                    onRetryOperation = ::retryDocumentOperation,
                    onCreateBackup = { launchDocumentOperation(DocumentOperation.BACKUP) },
                    onShareBackup = { launchDocumentOperation(DocumentOperation.SHARE) },
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

    private suspend fun catchUpPeriods(): Boolean {
        val application = application as PocketApplication
        val preferredStartDay = application.preferences.state.first().futurePeriodStartDay
        return application.ledger.execute(LedgerCommand.CatchUpPeriods(preferredStartDay)) == LedgerResult.Success
    }

    internal fun launchDocumentOperation(operation: DocumentOperation) {
        showOperationMessage(null)
        when (operation) {
            DocumentOperation.BACKUP -> createBackup.launch("pocket-${java.time.LocalDate.now()}.pocketbackup")
            DocumentOperation.SHARE -> shareBackup()
            DocumentOperation.CSV -> createCsv.launch("pocket-movimientos-${java.time.LocalDate.now()}.csv")
            DocumentOperation.RESTORE -> openBackup.launch(arrayOf("application/octet-stream", "application/json", "*/*"))
        }
    }

    private fun retryDocumentOperation() {
        recovery.retryOperation?.let { operation ->
            if (operation == DocumentOperation.SHARE) shareExistingBackup() else launchDocumentOperation(operation)
        }
    }

    private fun showOperationMessage(message: String?, retry: DocumentOperation? = null) {
        recovery.showOperationMessage(message, retry)
    }

    private fun shareBackup() {
        recovery.clearPreparedShare()
        lifecycleScope.launch {
            try {
                val file = withContext(Dispatchers.IO) {
                    val bytes = (application as PocketApplication).ledger.exportBackup()
                    val directory = File(cacheDir, SHARED_BACKUP_DIRECTORY).apply { mkdirs() }
                    val target = AtomicFile(File(directory, "pocket-${LocalDate.now()}-${UUID.randomUUID()}.pocketbackup"))
                    val output = target.startWrite()
                    try {
                        output.write(bytes)
                        target.finishWrite(output)
                    } catch (error: Exception) {
                        target.failWrite(output)
                        throw error
                    }
                    target.baseFile
                }
                recovery.rememberPreparedShare(file)
                BackupShareLauncher.share(this@MainActivity, file)
                recovery.clearPreparedShare()
                showOperationMessage("Selector para compartir abierto.")
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                showOperationMessage(
                    "No se pudo compartir el backup. Comprueba las aplicaciones disponibles y vuelve a intentarlo.",
                    DocumentOperation.SHARE,
                )
            }
        }
    }

    private fun shareExistingBackup() {
        val file = recovery.preparedShareFile()
        if (file == null) {
            shareBackup()
            return
        }
        showOperationMessage(null)
        try {
            BackupShareLauncher.share(this, file)
            recovery.clearPreparedShare()
            showOperationMessage("Selector para compartir abierto.")
        } catch (_: Exception) {
            showOperationMessage(
                "No se pudo compartir el backup. Comprueba las aplicaciones disponibles y vuelve a intentarlo.",
                DocumentOperation.SHARE,
            )
        }
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
    }
}

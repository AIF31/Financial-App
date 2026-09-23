package com.aif31.pocket

import android.app.Application
import android.util.AtomicFile
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal enum class DocumentOperation { BACKUP, SHARE, CSV, RESTORE }

internal class RecoveryViewModel(
    application: Application,
    private val savedState: SavedStateHandle,
) : AndroidViewModel(application) {
    private val candidateStore = RestoreCandidateStore(application.cacheDir)
    private var candidateRevision = 0

    var restoreCandidate by mutableStateOf<ByteArray?>(null)
        private set
    var operationMessage by mutableStateOf(savedState.get<String>(STATE_OPERATION_MESSAGE))
        private set
    var retryOperation by mutableStateOf(
        savedState.get<String>(STATE_RETRY_OPERATION)?.let(DocumentOperation::valueOf),
    )
        private set

    fun rememberPreparedShare(file: File) {
        savedState[STATE_PREPARED_SHARE_FILE] = file.name
    }

    fun preparedShareFile(): File? = savedState.get<String>(STATE_PREPARED_SHARE_FILE)?.let { name ->
        File(File(getApplication<Application>().cacheDir, "shared_backups"), name).takeIf { it.isFile }
    }

    fun clearPreparedShare() {
        savedState[STATE_PREPARED_SHARE_FILE] = null
    }

    init {
        val revision = candidateRevision
        viewModelScope.launch {
            val storedCandidate = withContext(Dispatchers.IO) {
                candidateStore.read()
            }
            if (candidateRevision == revision) restoreCandidate = storedCandidate
        }
    }

    suspend fun setRestoreCandidate(bytes: ByteArray) {
        candidateRevision += 1
        withContext(Dispatchers.IO) {
            candidateStore.write(bytes)
        }
        restoreCandidate = bytes
    }

    fun clearRestoreCandidate() {
        candidateRevision += 1
        restoreCandidate = null
        candidateStore.clear()
    }

    fun showOperationMessage(message: String?, retry: DocumentOperation? = null) {
        operationMessage = message
        retryOperation = retry
        savedState[STATE_OPERATION_MESSAGE] = message
        savedState[STATE_RETRY_OPERATION] = retry?.name
    }

    private companion object {
        const val STATE_OPERATION_MESSAGE = "operation_message"
        const val STATE_RETRY_OPERATION = "retry_operation"
        const val STATE_PREPARED_SHARE_FILE = "prepared_share_file"
    }
}

internal class RestoreCandidateStore(cacheDirectory: File) {
    private val file = AtomicFile(File(cacheDirectory, "restore-candidate.pocketbackup"))

    fun read(): ByteArray? = runCatching { file.openRead().use { it.readBytes() } }.getOrNull()

    fun write(bytes: ByteArray) {
        val output = file.startWrite()
        try {
            output.write(bytes)
            file.finishWrite(output)
        } catch (error: Exception) {
            file.failWrite(output)
            throw error
        }
    }

    fun clear() = file.delete()
}

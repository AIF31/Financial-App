package com.aif31.pocket

import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import java.io.File
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RecoveryInfrastructureTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun clearFiles() {
        RestoreCandidateStore(context.cacheDir).clear()
        File(context.cacheDir, "shared_backups").deleteRecursively()
    }

    @Test
    fun restore_candidate_survives_a_new_state_holder_without_entering_saved_instance_state() {
        val bytes = "portable backup".encodeToByteArray()
        RestoreCandidateStore(context.cacheDir).write(bytes)

        val restored = RestoreCandidateStore(context.cacheDir).read()

        assertArrayEquals(bytes, restored)
        RestoreCandidateStore(context.cacheDir).clear()
        assertEquals(null, RestoreCandidateStore(context.cacheDir).read())
    }

    @Test
    fun backup_share_grants_read_only_access_and_reports_launcher_failure_to_the_caller() {
        val backupUri = Uri.parse("content://com.aif31.pocket.fileprovider/shared_backups/test.pocketbackup")
        var launched: Intent? = null
        val recordingContext = object : ContextWrapper(context) {
            override fun startActivity(intent: Intent) {
                launched = intent
            }
        }

        BackupShareLauncher.share(recordingContext, backupUri)

        val send = launched!!.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)!!
        assertEquals(Intent.ACTION_SEND, send.action)
        assertEquals(Intent.FLAG_GRANT_READ_URI_PERMISSION, send.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION)
        assertEquals(0, send.flags and Intent.FLAG_GRANT_WRITE_URI_PERMISSION)

        val failingContext = object : ContextWrapper(context) {
            override fun startActivity(intent: Intent) = throw SecurityException("provider denied launch")
        }
        assertThrows(SecurityException::class.java) { BackupShareLauncher.share(failingContext, backupUri) }
    }

    @Test
    fun share_retry_uses_only_the_prepared_artifact_and_survives_recreation() {
        val directory = File(context.cacheDir, "shared_backups").apply { mkdirs() }
        File(directory, "older.pocketbackup").writeText("older")
        val state = SavedStateHandle()
        val application = ApplicationProvider.getApplicationContext<Application>()
        val recovery = RecoveryViewModel(application, state)

        assertEquals(null, recovery.preparedShareFile())
        val prepared = File(directory, "current.pocketbackup").apply { writeText("current") }
        recovery.rememberPreparedShare(prepared)
        assertEquals(prepared, RecoveryViewModel(application, state).preparedShareFile())
        prepared.delete()
        assertEquals(null, recovery.preparedShareFile())
    }
}

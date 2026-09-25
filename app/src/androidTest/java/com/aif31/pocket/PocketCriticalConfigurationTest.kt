package com.aif31.pocket

import android.content.Intent
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.waitUntilExactlyOneExists
import androidx.compose.ui.test.waitUntilDoesNotExist
import androidx.test.espresso.Espresso.closeSoftKeyboard
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.MediumTest
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import com.aif31.pocket.data.LedgerCommand
import com.aif31.pocket.domain.SupportedCurrency
import com.aif31.pocket.settings.ReminderScheduler
import com.aif31.pocket.settings.ReminderStatus
import com.aif31.pocket.settings.AppPreferences
import com.aif31.pocket.ui.SettingsSection
import java.io.FileInputStream
import java.time.LocalTime
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@MediumTest
@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 35)
@OptIn(ExperimentalTestApi::class)
class PocketCriticalConfigurationTest {
    @get:Rule val compose = createComposeRule()
    private val app get() = ApplicationProvider.getApplicationContext<PocketApplication>()

    @Before fun clearLedger() = runBlocking {
        app.database.clearAllTables()
        RestoreCandidateStore(app.cacheDir).clear()
        app.preferences.setReminder(false, LocalTime.of(21, 0))
        app.preferences.setDefaultExpenseCurrency(SupportedCurrency.SAR)
        app.preferences.setOnlineFxEnabled(false)
    }

    @After fun resetManagedDevice() {
        shell("settings put system font_scale 1.0")
        shell("wm size reset")
        runBlocking { app.database.clearAllTables() }
        RestoreCandidateStore(app.cacheDir).clear()
    }

    private fun shell(command: String) {
        InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command).use { descriptor ->
            FileInputStream(descriptor.fileDescriptor).use { it.readBytes() }
        }
    }

    private fun launch() = ActivityScenario.launch<MainActivity>(Intent(app, MainActivity::class.java))

    private fun seedLedger() = runBlocking {
        app.ledger.execute(LedgerCommand.Initialize(100_000))
        app.ledger.state.first { !it.needsOnboarding }
    }

    @Test fun large_font_compact_window_keeps_onboarding_validation_and_recovery_reachable() {
        shell("settings put system font_scale 1.5")
        shell("wm size 720x1280")
        launch().use {
            compose.waitUntilExactlyOneExists(hasTestTag("new_funds"), 10_000)
            compose.onNodeWithTag("new_funds").performTextInput("12..50")
            compose.onNodeWithText("Comenzar").performScrollTo().performClick()
            compose.onNodeWithText("Revisa los fondos y el día de inicio").performScrollTo().assertIsDisplayed()
            compose.onNodeWithText("Restaurar backup").performScrollTo().assertIsDisplayed()
        }
    }

    @Test fun large_font_compact_window_keeps_populated_period_pocket_movement_and_recovery_actions_reachable() {
        seedLedger()
        shell("settings put system font_scale 1.5")
        shell("wm size 720x1280")
        launch().use {
            compose.waitUntilExactlyOneExists(hasText("Ajustes"), 10_000)
            compose.onNodeWithText("Ajustes").performClick()
            compose.onNodeWithText("Periodo y fondos").performScrollTo().performClick()
            compose.onNodeWithTag("period_funds").performTextReplacement("1.2.3")
            closeSoftKeyboard()
            compose.onNodeWithText("Guardar fondos").performScrollTo().performClick()
            compose.onNodeWithText("Escribe fondos válidos").performScrollTo().assertIsDisplayed()
            compose.onNodeWithTag("period_funds").performTextReplacement("1200.00")
            closeSoftKeyboard()
            compose.onNodeWithText("Guardar fondos").performScrollTo().performClick()
            compose.waitUntil(10_000) { runBlocking { app.ledger.state.first().newFundsMinor == 120_000L } }
            compose.onNodeWithText("Crear periodo siguiente").performScrollTo().assertIsDisplayed()
            compose.onNodeWithTag("settings_list").performScrollToNode(hasText("Atrás"))
            compose.onNodeWithText("Atrás").performClick()

            compose.onNodeWithText("Pockets").performClick()
            compose.onNodeWithTag("pockets_list").performScrollToNode(hasText("Crear Pocket"))
            compose.onNodeWithText("Crear Pocket").performClick()
            compose.onNodeWithText("Guardar Pocket").assertIsDisplayed().performClick()
            compose.onNodeWithText("Escribe un nombre para el Pocket").performScrollTo().assertIsDisplayed()
            compose.onNodeWithTag("pocket_name").performTextInput("Vacaciones")
            closeSoftKeyboard()
            compose.onNodeWithText("Aplicar rollover").performScrollTo().assertIsDisplayed()
            compose.onNodeWithText("Guardar Pocket").assertIsDisplayed().performClick()
            compose.waitUntil(10_000) { runBlocking { app.ledger.state.first().pockets.any { it.pocket.name == "Vacaciones" } } }

            compose.onNodeWithTag("pockets_list").performScrollToNode(hasTestTag("pocket_Supermercado"))
            compose.onNodeWithTag("pocket_Supermercado").performClick()
            compose.onNodeWithTag("allocation_amount").performTextReplacement("50.00")
            closeSoftKeyboard()
            compose.onNodeWithText("Guardar presupuesto").assertIsDisplayed().performClick()
            compose.waitUntil(10_000) { runBlocking { app.ledger.state.first().pockets.first { it.pocket.name == "Supermercado" }.budgetMinor == 5_000L } }

            compose.onNodeWithText("Inicio").performClick()
            compose.onNodeWithTag("contextual_add").performClick()
            compose.onNodeWithTag("movement_amount").performTextInput("8.25")
            closeSoftKeyboard()
            compose.onNodeWithTag("movement_pocket_Supermercado").performScrollTo().performClick()
            compose.onNodeWithTag("movement_save").assertIsDisplayed().performClick()
            compose.waitUntil(10_000) { runBlocking { app.ledger.state.first().movements.size == 1 } }
            compose.onNodeWithTag("dashboard_list").performScrollToNode(hasText("Gastado"))
            compose.onNodeWithText("Gastado").assertIsDisplayed()
            compose.onNodeWithTag("dashboard_list").performScrollToNode(hasText("SAR 8.25"))
            compose.onNodeWithText("SAR 8.25").assertIsDisplayed()
            compose.onNodeWithText("Pockets").performClick()
            compose.onNodeWithTag("pockets_list").performScrollToNode(hasTestTag("pocket_Supermercado"))
            compose.onNodeWithText("SAR 41.75 disponibles").assertIsDisplayed()
        }

        val backup = runBlocking { app.ledger.exportBackup() }
        launch().use { scenario ->
            scenario.onActivity { activity ->
                runBlocking { ViewModelProvider(activity)[RecoveryViewModel::class.java].setRestoreCandidate(backup) }
            }
            compose.waitUntilExactlyOneExists(hasText("Confirmar restauración"), 10_000)
            compose.onNodeWithText("Esta acción reemplazará los datos actuales y puede eliminar información anterior. No se puede deshacer.")
                .performScrollTo().assertIsDisplayed()
            compose.onNodeWithText("Continuar sin backup").performScrollTo().performClick()
            compose.onNodeWithText("Restaurar y reemplazar").assertIsDisplayed().assertIsEnabled().performClick()
            compose.waitUntilDoesNotExist(hasText("Confirmar restauración"), 10_000)
        }
        launch().use { scenario ->
            scenario.onActivity { activity ->
                runBlocking { ViewModelProvider(activity)[RecoveryViewModel::class.java].setRestoreCandidate(byteArrayOf(1, 2, 3)) }
            }
            compose.waitUntilExactlyOneExists(hasText("Backup inválido"), 10_000)
            compose.onNodeWithText("Cancelar").assertIsDisplayed().performClick()
        }
    }

    @Test fun wide_window_keeps_pocket_edit_and_availability_reachable() {
        seedLedger()
        shell("wm size 1440x1080")
        launch().use { scenario ->
            compose.waitUntilExactlyOneExists(hasText("Pockets"), 10_000)
            compose.onNodeWithText("Pockets").performClick()
            compose.onNodeWithText("Disponible").assertIsDisplayed()
            compose.onNodeWithTag("pockets_list").performScrollToNode(hasTestTag("pocket_Supermercado"))
            compose.onNodeWithTag("pocket_Supermercado").assertIsDisplayed().performClick()
            compose.onNodeWithText("Guardar presupuesto").assertIsDisplayed()
            compose.onNodeWithText("Cerrar").performClick()
            shell("wm size 1280x720")
            scenario.recreate()
            compose.waitUntilExactlyOneExists(hasText("Pockets"), 10_000)
            compose.onNodeWithText("Pockets").performClick()
            compose.onNodeWithTag("pockets_list").performScrollToNode(hasTestTag("pocket_Supermercado"))
            compose.onNodeWithTag("pocket_Supermercado").performClick()
            compose.onNodeWithText("Guardar presupuesto").assertIsDisplayed()
        }
    }

    @Test fun keyboard_draft_and_restore_confirmation_survive_recreation() {
        seedLedger()
        shell("wm size 720x1280")
        launch().use { scenario ->
            compose.waitUntilExactlyOneExists(hasTestTag("contextual_add"), 10_000)
            compose.onNodeWithTag("contextual_add").performClick()
            compose.onNodeWithTag("movement_amount").performTextInput("12.50")
            closeSoftKeyboard()
            compose.onNodeWithTag("movement_pocket_Supermercado").performClick()
            compose.onNodeWithTag("movement_pocket_Supermercado").assertTextContains("✓ Supermercado")
            compose.waitUntil(10_000) {
                compose.onNodeWithTag("movement_save").fetchSemanticsNode().config.contains(androidx.compose.ui.semantics.SemanticsProperties.Disabled).not()
            }
            compose.onNodeWithTag("movement_save").assertIsEnabled()
            scenario.recreate()
            compose.waitUntilExactlyOneExists(hasTestTag("movement_amount"), 10_000)
            compose.onNodeWithTag("movement_amount").assertTextContains("12.50")
            compose.onNodeWithTag("movement_save").assertIsEnabled()
        }
        val backup = runBlocking { app.ledger.exportBackup() }
        launch().use { scenario ->
            scenario.onActivity { activity ->
                runBlocking { ViewModelProvider(activity)[RecoveryViewModel::class.java].setRestoreCandidate(backup) }
            }
            compose.waitUntilExactlyOneExists(hasText("Confirmar restauración"), 10_000)
            compose.onNodeWithText("Continuar sin backup").performScrollTo().performClick()
            compose.onNodeWithText("Restaurar y reemplazar").assertIsEnabled()
            scenario.recreate()
            compose.waitUntilExactlyOneExists(hasText("Confirmar restauración"), 10_000)
            compose.onNodeWithText("Restaurar y reemplazar").assertIsEnabled()
        }
    }

    @Test fun compact_keyboard_visible_save_persists_once_after_recreation() {
        seedLedger()
        shell("wm size 720x1280")
        launch().use { scenario ->
            compose.waitUntilExactlyOneExists(hasTestTag("contextual_add"), 10_000)
            compose.onNodeWithTag("contextual_add").performClick()
            closeSoftKeyboard()
            compose.onNodeWithTag("movement_pocket_Supermercado").performClick()
            compose.onNodeWithTag("movement_amount").performTextInput("8.25")
            compose.waitUntil(10_000) {
                !compose.onNodeWithTag("movement_save").fetchSemanticsNode().config.contains(androidx.compose.ui.semantics.SemanticsProperties.Disabled)
            }
            compose.onNodeWithTag("movement_save").assertIsDisplayed().performClick()
            compose.waitUntil(10_000) { runBlocking { app.ledger.state.first().movements.size == 1 } }
            scenario.moveToState(Lifecycle.State.CREATED)
            scenario.moveToState(Lifecycle.State.RESUMED)
            compose.waitUntil(10_000) { runBlocking { app.ledger.state.first().movements.size == 1 } }
            scenario.recreate()
            compose.waitUntil(10_000) { runBlocking { app.ledger.state.first().movements.size == 1 } }
        }
    }

    @Test fun denied_reminder_permission_stays_truthful_after_recreation() {
        seedLedger()
        shell("pm revoke com.aif31.pocket android.permission.POST_NOTIFICATIONS")
        launch().use { scenario ->
            compose.waitUntilExactlyOneExists(hasText("Ajustes"), 10_000)
            compose.onNodeWithText("Ajustes").performClick()
            compose.onNodeWithText("Recordatorio diario").performScrollTo().performClick()
            compose.onNodeWithTag("settings_list").performScrollToNode(hasTestTag("reminder_switch"))
            compose.onNodeWithTag("reminder_switch").performClick()
            compose.waitUntilExactlyOneExists(hasText("Permiso necesario"), 10_000)
            scenario.recreate()
            compose.waitUntilExactlyOneExists(hasText("Permiso necesario"), 10_000)
        }
    }

    @Test fun scheduler_failure_shows_retry_and_recovers() {
        seedLedger()
        val scheduler = object : ReminderScheduler {
            var result: ReminderStatus = ReminderStatus.Failed
            override fun apply(enabled: Boolean, time: LocalTime) = Unit
            override suspend fun status(enabled: Boolean) = if (enabled) result else ReminderStatus.Off
        }
        val state = runBlocking { app.ledger.state.first { !it.needsOnboarding } }
        compose.setContent {
            SettingsScreen(
                state = state, ledger = app.ledger,
                preferences = app.preferences.state.collectAsState(initial = AppPreferences()).value,
                preferencesStore = app.preferences, reminderScheduler = scheduler,
                onCreateBackup = {}, onCreateCsv = {}, onPickBackup = {},
                onRequestNotificationPermission = {}, padding = PaddingValues(),
                section = SettingsSection.REMINDERS, onSectionChange = {},
            )
        }
        compose.onNodeWithTag("settings_list").performScrollToNode(hasTestTag("reminder_switch"))
        compose.onNodeWithTag("reminder_switch").performClick()
        compose.waitUntilExactlyOneExists(hasText("No se pudo programar"), 10_000)
        scheduler.result = ReminderStatus.Scheduled
        compose.onNodeWithTag("settings_list").performScrollToNode(hasText("Reintentar"))
        compose.onNodeWithText("Reintentar").performClick()
        compose.waitUntilExactlyOneExists(hasText("Programado"), 10_000)
    }

    @Test fun document_feedback_remains_readable_after_compact_recreation() {
        seedLedger()
        shell("wm size 720x1280")
        launch().use { scenario ->
            scenario.onActivity { activity ->
                ViewModelProvider(activity)[RecoveryViewModel::class.java]
                    .showOperationMessage("No se pudo crear el backup. Comprueba el destino y vuelve a intentarlo.")
            }
            compose.waitUntilExactlyOneExists(hasText("No se pudo crear el backup. Comprueba el destino y vuelve a intentarlo."), 10_000)
            compose.onNodeWithText("Aceptar").assertIsDisplayed()
            scenario.recreate()
            compose.waitUntilExactlyOneExists(hasText("No se pudo crear el backup. Comprueba el destino y vuelve a intentarlo."), 10_000)
            compose.onNodeWithText("Aceptar").assertIsDisplayed().performClick()
        }
    }
}

package com.aif31.pocket.notifications

import androidx.datastore.core.okio.OkioStorage
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.PreferencesSerializer
import com.aif31.pocket.fx.BanxicoHistorySource
import com.aif31.pocket.fx.FxObservation
import com.aif31.pocket.fx.QuoteFailure
import com.aif31.pocket.fx.FxRateDigest
import com.aif31.pocket.domain.SupportedCurrency
import com.aif31.pocket.settings.DataStorePreferences
import com.aif31.pocket.settings.FxRateAlertFrequency
import com.aif31.pocket.settings.FxRateAlertSettings
import java.io.File
import java.io.IOException
import java.math.BigDecimal
import java.nio.file.FileSystems
import java.time.DayOfWeek
import java.time.LocalDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import okio.FileSystem.Companion.asOkioFileSystem
import okio.Path.Companion.toOkioPath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class FxRateAlertsTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val today = LocalDate.of(2026, 10, 21)
    private val week = listOf(
        observation(14, "18.7500"),
        observation(15, "18.7500"),
        observation(16, "18.7500"),
        observation(19, "18.7500"),
        observation(20, "19.1250"),
    )

    @Test
    fun `alert shows the SAR rate, the change from the previous FIX and the seven-day average`() {
        val alert = fxRateAlert(sarToMxn(week))

        assertEquals("1 SAR = 5.1000 MXN", alert.title)
        assertEquals("+2.00% vs. 19 oct · Promedio 7 días: 5.0200 MXN", alert.text)
        assertEquals(
            "FIX de Banxico del 20 oct.\n+2.00% vs. 19 oct.\nPromedio 7 días: 5.0200 MXN (1.59% por encima del promedio).",
            alert.details,
        )
    }

    @Test
    fun `alert without a previous FIX omits the change`() {
        val alert = fxRateAlert(sarToMxn(listOf(observation(20, "18.7500"))))

        assertEquals("Promedio 7 días: 5.0000 MXN", alert.text)
        assertEquals("FIX de Banxico del 20 oct.\nPromedio 7 días: 5.0000 MXN (igual al promedio).", alert.details)
    }

    @Test
    fun `posts the latest FIX once and requests the lookback through today`() = runTest {
        val store = store(backgroundScope)
        store.setOnlineFxEnabled(true)
        store.setFxRateAlert(FxRateAlertSettings(enabled = true))
        val posted = mutableListOf<FxRateAlert>()
        var range: Pair<LocalDate, LocalDate>? = null
        val coordinator = FxRateAlertCoordinator(
            preferences = store,
            history = BanxicoHistorySource { start, end -> range = start to end; week },
            today = { today },
            post = { posted += it },
        )

        assertEquals(FxRateAlertOutcome.POSTED, coordinator.run())
        assertEquals(FxRateAlertOutcome.SKIPPED, coordinator.run())

        assertEquals(today.minusDays(20) to today, range)
        assertEquals(listOf("1 SAR = 5.1000 MXN"), posted.map { it.title })
        assertEquals("day/2026-10-21", store.state.first().fxRateAlertLastSlot)
    }

    @Test
    fun `a daily alert repeats the latest FIX on the next day`() = runTest {
        val store = store(backgroundScope)
        store.setOnlineFxEnabled(true)
        store.setFxRateAlert(FxRateAlertSettings(enabled = true))
        var day = today
        val posted = mutableListOf<FxRateAlert>()
        val coordinator = FxRateAlertCoordinator(store, { _, _ -> week }, { day }) { posted += it }

        coordinator.run()
        day = today.plusDays(1)
        assertEquals(FxRateAlertOutcome.POSTED, coordinator.run())

        assertEquals(listOf("1 SAR = 5.1000 MXN", "1 SAR = 5.1000 MXN"), posted.map { it.title })
    }

    @Test
    fun `a new-FIX alert waits for a FIX it has not sent`() = runTest {
        val store = store(backgroundScope)
        store.setOnlineFxEnabled(true)
        store.setFxRateAlert(FxRateAlertSettings(enabled = true, frequency = FxRateAlertFrequency.NEW_FIX))
        var day = today
        var observations = week.dropLast(1)
        val posted = mutableListOf<FxRateAlert>()
        val coordinator = FxRateAlertCoordinator(store, { _, _ -> observations }, { day }) { posted += it }

        assertEquals(FxRateAlertOutcome.POSTED, coordinator.run())
        day = today.plusDays(1)
        assertEquals(FxRateAlertOutcome.SKIPPED, coordinator.run())
        observations = week
        assertEquals(FxRateAlertOutcome.POSTED, coordinator.run())

        assertEquals(listOf("1 SAR = 5.0000 MXN", "1 SAR = 5.1000 MXN"), posted.map { it.title })
    }

    @Test
    fun `a weekly alert posts only on its weekday`() = runTest {
        val store = store(backgroundScope)
        store.setOnlineFxEnabled(true)
        store.setFxRateAlert(
            FxRateAlertSettings(enabled = true, frequency = FxRateAlertFrequency.WEEKLY, weekday = DayOfWeek.THURSDAY),
        )
        var calls = 0
        var day = today // A Wednesday.
        val posted = mutableListOf<FxRateAlert>()
        val coordinator = FxRateAlertCoordinator(store, { _, _ -> calls += 1; week }, { day }) { posted += it }

        assertEquals(FxRateAlertOutcome.SKIPPED, coordinator.run())
        assertEquals(0, calls)
        day = today.plusDays(1)
        assertEquals(FxRateAlertOutcome.POSTED, coordinator.run())
        assertEquals(1, posted.size)
    }

    @Test
    fun `a minimum rate holds the alert back without recording it`() = runTest {
        val store = store(backgroundScope)
        store.setOnlineFxEnabled(true)
        store.setFxRateAlert(
            FxRateAlertSettings(enabled = true, frequency = FxRateAlertFrequency.NEW_FIX, minimumRate = BigDecimal("5.05")),
        )
        var observations = week.dropLast(1)
        val posted = mutableListOf<FxRateAlert>()
        val coordinator = FxRateAlertCoordinator(store, { _, _ -> observations }, { today }) { posted += it }

        assertEquals(FxRateAlertOutcome.SKIPPED, coordinator.run())
        assertEquals(null, store.state.first().fxRateAlertLastSlot)
        observations = week
        assertEquals(FxRateAlertOutcome.POSTED, coordinator.run())

        assertEquals(listOf("1 SAR = 5.1000 MXN"), posted.map { it.title })
    }

    @Test
    fun `the chosen pair sets the alert rate`() = runTest {
        val store = store(backgroundScope)
        store.setOnlineFxEnabled(true)
        store.setFxRateAlert(FxRateAlertSettings(enabled = true, base = SupportedCurrency.MXN, quote = SupportedCurrency.USD))
        val posted = mutableListOf<FxRateAlert>()

        FxRateAlertCoordinator(store, { _, _ -> week }, { today }) { posted += it }.run()

        assertEquals("1 MXN = 0.052288 USD", posted.single().title)
        assertTrue(posted.single().text.startsWith("-1.96% vs. 19 oct · Promedio 7 días: 0.05"))
    }

    @Test
    fun `nothing is fetched while the alert or online conversion is off`() = runTest {
        var calls = 0
        val history = BanxicoHistorySource { _, _ -> calls += 1; week }

        val alertOff = store(backgroundScope, "off.preferences_pb").apply { setOnlineFxEnabled(true) }
        val consentOff = store(backgroundScope, "consent.preferences_pb").apply { setFxRateAlert(FxRateAlertSettings(enabled = true)) }

        assertEquals(FxRateAlertOutcome.SKIPPED, FxRateAlertCoordinator(alertOff, history, { today }) {}.run())
        assertEquals(FxRateAlertOutcome.SKIPPED, FxRateAlertCoordinator(consentOff, history, { today }) {}.run())
        assertEquals(0, calls)
    }

    @Test
    fun `provider failures retry, missing configuration does not, and neither records a FIX`() = runTest {
        val store = store(backgroundScope)
        store.setOnlineFxEnabled(true)
        store.setFxRateAlert(FxRateAlertSettings(enabled = true))
        val posted = mutableListOf<FxRateAlert>()

        assertEquals(
            FxRateAlertOutcome.RETRY,
            FxRateAlertCoordinator(store, { _, _ -> throw QuoteFailure.Unavailable() }, { today }) { posted += it }.run(),
        )
        assertEquals(
            FxRateAlertOutcome.RETRY,
            FxRateAlertCoordinator(store, { _, _ -> throw IOException("down") }, { today }) { posted += it }.run(),
        )
        assertEquals(
            FxRateAlertOutcome.RETRY,
            FxRateAlertCoordinator(store, { _, _ -> emptyList() }, { today }) { posted += it }.run(),
        )
        assertEquals(
            FxRateAlertOutcome.SKIPPED,
            FxRateAlertCoordinator(store, { _, _ -> throw QuoteFailure.ConfigurationUnavailable() }, { today }) { posted += it }.run(),
        )

        assertTrue(posted.isEmpty())
        assertEquals(null, store.state.first().fxRateAlertLastSlot)
    }

    private fun sarToMxn(observations: List<FxObservation>) =
        FxRateDigest.of(observations, SupportedCurrency.SAR, SupportedCurrency.MXN)!!

    private fun observation(day: Int, usdToMxn: String) = FxObservation(LocalDate.of(2026, 10, day), BigDecimal(usdToMxn))

    private fun store(scope: CoroutineScope, name: String = "fx.preferences_pb") = DataStorePreferences(
        PreferenceDataStoreFactory.create(
            storage = OkioStorage(
                fileSystem = FileSystems.getDefault().asOkioFileSystem(),
                serializer = PreferencesSerializer,
                producePath = { File(temporaryFolder.root, name).toOkioPath() },
            ),
            scope = scope,
        ),
    )
}

package com.aif31.pocket.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.aif31.pocket.domain.SupportedCurrency
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.Rule

class AppPreferencesTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `missing upgrade keys default to disabled online FX and SAR input`() = runTest {
        val store = DataStorePreferences(newDataStore("upgrade.preferences_pb", backgroundScope))

        val preferences = store.state.first()

        assertFalse(preferences.onlineFxEnabled)
        assertEquals(SupportedCurrency.SAR, preferences.defaultExpenseCurrency)
    }

    @Test
    fun `online FX consent and supported input currency round trip`() = runTest {
        val consentStore = DataStorePreferences(newDataStore("consent.preferences_pb", backgroundScope))
        val currencyStore = DataStorePreferences(newDataStore("currency.preferences_pb", backgroundScope))

        consentStore.setOnlineFxEnabled(true)
        currencyStore.setDefaultExpenseCurrency(SupportedCurrency.MXN)

        assertTrue(consentStore.state.first().onlineFxEnabled)
        assertEquals(SupportedCurrency.MXN, currencyStore.state.first().defaultExpenseCurrency)
    }

    @Test
    fun `unsupported stored input currency safely resolves to SAR`() = runTest {
        val dataStore = newDataStore("unsupported.preferences_pb", backgroundScope)
        dataStore.edit { values -> values[stringPreferencesKey("default_expense_currency")] = "EUR" }

        val preferences = DataStorePreferences(dataStore).state.first()

        assertEquals(SupportedCurrency.SAR, preferences.defaultExpenseCurrency)
    }

    private fun newDataStore(name: String, scope: CoroutineScope): DataStore<Preferences> = PreferenceDataStoreFactory.create(
        scope = scope,
        produceFile = { File(temporaryFolder.root, name) },
    )
}

package com.aif31.pocket.data

import android.content.Context
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FinanceDatabaseMigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        FinanceDatabase::class.java,
    )

    private fun openMigratedDatabase(context: Context, name: String): FinanceDatabase =
        Room.databaseBuilder(context.applicationContext, FinanceDatabase::class.java, name)
            .addMigrations(*FinanceDatabase.MIGRATIONS)
            .build()

    @Test
    fun populated_version_1_upgrades_to_current_and_remains_usable() = runBlocking {
        val name = "migration-1-current-test"
        helper.createDatabase(name, 1).apply {
            execSQL("INSERT INTO periods (id, start_epoch_day, end_exclusive_epoch_day, new_funds_minor, configured_start_day) VALUES " +
                "('period-1', 20478, 20506, 100000, 25), ('period-2', 20506, 20537, 110000, 25)")
            execSQL("INSERT INTO pockets (id, name, sort_order, archived, rollover_enabled) VALUES " +
                "('pocket-1', 'Viajes', 0, 0, 1), ('pocket-2', 'Comida', 1, 0, 0)")
            execSQL("INSERT INTO payment_methods (id, name, archived) VALUES ('card-1', 'Tarjeta', 0)")
            execSQL("INSERT INTO allocations (period_id, pocket_id, budget_minor, rollover_minor) VALUES " +
                "('period-1', 'pocket-1', 25000, 5000), ('period-2', 'pocket-1', 30000, 7000)")
            execSQL("INSERT INTO movements (id, period_id, pocket_id, type, sar_amount_minor, occurred_at_utc_millis, " +
                "local_epoch_day, zone_id, merchant, note, payment_method_id, original_amount_minor, " +
                "original_currency_code, conversion_status, rate) VALUES " +
                "('expense-1', 'period-1', 'pocket-1', 'EXPENSE', 1000, 1, 20479, 'Asia/Riyadh', " +
                "'Merchant', NULL, 'card-1', NULL, 'SAR', 'CONFIRMED', NULL), " +
                "('refund-1', 'period-2', 'pocket-1', 'REFUND', 250, 2, 20507, 'Asia/Riyadh', " +
                "NULL, 'Legacy refund', NULL, NULL, 'SAR', 'CONFIRMED', NULL)")
            execSQL("INSERT INTO recurring_templates (id, name, amount_minor, pocket_id, payment_method_id, archived) " +
                "VALUES ('template-1', 'Viaje', 5000, 'pocket-1', 'card-1', 0)")
            close()
        }
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val zone = ZoneId.of("Asia/Riyadh")
        val clock = Clock.fixed(Instant.parse("2026-02-26T09:00:00Z"), zone)
        val database = openMigratedDatabase(context, name)
        try {
            val ledger = RoomPocketLedger(database, clock, zone)
            val state = ledger.state.first { it.periods.isNotEmpty() }
            assertEquals(setOf("period-1", "period-2"), state.periods.map { it.id }.toSet())
            assertEquals(setOf("expense-1", "refund-1"), state.movements.map { it.id }.toSet())
            assertEquals("period-2", state.currentPeriod?.id)
            assertEquals("card-1", state.defaultPaymentMethodId)
            assertEquals(PocketIconKey.TRAVEL, state.pocketCatalog.single { it.id == "pocket-1" }.iconKey)
            assertEquals(5_000L, state.pocketSummariesByPeriod.getValue("period-1").single { it.pocket.id == "pocket-1" }.rolloverMinor)
            assertEquals(1_000L, state.movements.single { it.id == "expense-1" }.accountingAmountMinor)
            assertEquals(LedgerResult.Success, ledger.execute(LedgerCommand.AddMovement(
                id = "post-migration", pocketId = "pocket-1", type = MovementType.EXPENSE,
                accountingAmountMinor = 100, occurredAtUtcMillis = clock.millis(),
                localDate = LocalDate.of(2026, 2, 26),
            )))
            val backup = ledger.exportBackup()
            assertTrue(ledger.exportCsv().isNotEmpty())
            assertEquals(LedgerResult.Success, ledger.restoreBackup(backup))
            assertEquals(3, ledger.state.first().movements.size)
        } finally {
            database.close()
            context.deleteDatabase(name)
        }
    }

    @Test
    fun overflowing_legacy_rows_remain_intact_after_upgrade_and_are_rejected() = runBlocking {
        val name = "migration-1-overflow-test"
        helper.createDatabase(name, 1).apply {
            execSQL("INSERT INTO periods (id, start_epoch_day, end_exclusive_epoch_day, new_funds_minor, configured_start_day) " +
                "VALUES ('period-1', 20478, 20506, 100000, 25)")
            execSQL("INSERT INTO pockets (id, name, sort_order, archived, rollover_enabled) VALUES ('pocket-1', 'Viajes', 0, 0, 0)")
            execSQL("INSERT INTO movements (id, period_id, pocket_id, type, sar_amount_minor, occurred_at_utc_millis, " +
                "local_epoch_day, zone_id, merchant, note, payment_method_id, original_amount_minor, " +
                "original_currency_code, conversion_status, rate) VALUES " +
                "('max', 'period-1', 'pocket-1', 'EXPENSE', 9223372036854775807, 1, 20479, 'Asia/Riyadh', " +
                "NULL, NULL, NULL, NULL, 'SAR', 'CONFIRMED', NULL), " +
                "('one', 'period-1', 'pocket-1', 'EXPENSE', 1, 2, 20479, 'Asia/Riyadh', " +
                "NULL, NULL, NULL, NULL, 'SAR', 'CONFIRMED', NULL)")
            close()
        }
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val zone = ZoneId.of("Asia/Riyadh")
        val database = openMigratedDatabase(context, name)
        try {
            val ledger = RoomPocketLedger(database, Clock.fixed(Instant.parse("2026-01-26T09:00:00Z"), zone), zone)
            try {
                ledger.state.first()
                fail("The overflowing legacy expense aggregate must be rejected")
            } catch (_: ArithmeticException) {
                assertEquals(2, database.financeDao().movements().size)
            }
        } finally {
            database.close()
            context.deleteDatabase(name)
        }
    }

    @Test
    fun migration_2_to_3_backfills_known_pocket_artwork_and_preserves_custom_pockets() {
        helper.createDatabase(TEST_DATABASE, 2).apply {
            execSQL("INSERT INTO periods (id, start_epoch_day, end_exclusive_epoch_day, new_funds_minor, configured_start_day) " +
                "VALUES ('period-1', 20478, 20506, 100000, 25)")
            execSQL(
                "INSERT INTO pockets (id, name, icon_key, sort_order, archived, rollover_enabled) " +
                    "VALUES ('known', 'Supermercado', 'OTHER', 0, 0, 0)"
            )
            execSQL(
                "INSERT INTO pockets (id, name, icon_key, sort_order, archived, rollover_enabled) " +
                    "VALUES ('custom', 'Mascotas', 'OTHER', 1, 0, 0)"
            )
            execSQL("INSERT INTO payment_methods (id, name, archived) VALUES ('card-1', 'Tarjeta', 0)")
            execSQL("INSERT INTO allocations (period_id, pocket_id, budget_minor, rollover_minor) " +
                "VALUES ('period-1', 'known', 25000, 5000)")
            execSQL("INSERT INTO movements (id, period_id, pocket_id, type, sar_amount_minor, occurred_at_utc_millis, " +
                "local_epoch_day, zone_id, merchant, note, payment_method_id, original_amount_minor, " +
                "original_currency_code, conversion_status, rate) VALUES " +
                "('movement-1', 'period-1', 'known', 'EXPENSE', 1000, 1, 20479, 'Asia/Riyadh', " +
                "NULL, NULL, 'card-1', NULL, 'SAR', 'CONFIRMED', NULL)")
            close()
        }

        helper.runMigrationsAndValidate(
            TEST_DATABASE,
            3,
            true,
            FinanceDatabase.MIGRATION_2_3,
        ).use { database ->
            database.query("SELECT id, icon_key FROM pockets ORDER BY id").use { cursor ->
                val icons = buildMap {
                    while (cursor.moveToNext()) put(cursor.getString(0), cursor.getString(1))
                }
                assertEquals("OTHER", icons.getValue("custom"))
                assertEquals("SUPERMARKET", icons.getValue("known"))
            }
            database.query("SELECT budget_minor, rollover_minor FROM allocations WHERE pocket_id = 'known'").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(25_000L, cursor.getLong(0))
                assertEquals(5_000L, cursor.getLong(1))
            }
            database.query("SELECT sar_amount_minor, payment_method_id FROM movements WHERE id = 'movement-1'").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(1_000L, cursor.getLong(0))
                assertEquals("card-1", cursor.getString(1))
            }
        }
    }

    @Test
    fun migration_3_to_4_snapshots_period_rollover_preferences_and_preserves_financial_rows() {
        helper.createDatabase(TEST_DATABASE_3_4, 3).apply {
            execSQL(
                "INSERT INTO periods (id, start_epoch_day, end_exclusive_epoch_day, new_funds_minor, configured_start_day) " +
                    "VALUES ('period-1', 20478, 20506, 100000, 25), ('period-2', 20506, 20537, 110000, 25)"
            )
            execSQL(
                "INSERT INTO pockets (id, name, icon_key, sort_order, archived, rollover_enabled) " +
                    "VALUES ('enabled', 'Viajes', 'TRAVEL', 0, 0, 1), ('disabled', 'Comida', 'RESTAURANT', 1, 0, 0)"
            )
            execSQL("INSERT INTO payment_methods (id, name, archived) VALUES ('card-1', 'Tarjeta', 0)")
            execSQL(
                "INSERT INTO allocations (period_id, pocket_id, budget_minor, rollover_minor) " +
                    "VALUES ('period-1', 'enabled', 25000, 5000), ('period-2', 'enabled', 30000, 7000)"
            )
            execSQL(
                "INSERT INTO movements (id, period_id, pocket_id, type, sar_amount_minor, occurred_at_utc_millis, " +
                    "local_epoch_day, zone_id, merchant, note, payment_method_id, original_amount_minor, " +
                    "original_currency_code, conversion_status, rate) VALUES " +
                    "('movement-1', 'period-1', 'enabled', 'EXPENSE', 1000, 1, 20479, 'Asia/Riyadh', NULL, NULL, 'card-1', NULL, 'SAR', 'CONFIRMED', NULL)"
            )
            close()
        }

        helper.runMigrationsAndValidate(
            TEST_DATABASE_3_4,
            4,
            true,
            FinanceDatabase.MIGRATION_3_4,
        ).use { database ->
            database.query(
                "SELECT period_id, pocket_id, rollover_eligible, retired FROM period_pockets " +
                    "ORDER BY period_id, pocket_id"
            ).use { cursor ->
                val snapshots = buildList {
                    while (cursor.moveToNext()) {
                        add("${cursor.getString(0)}:${cursor.getString(1)}:${cursor.getInt(2)}:${cursor.getInt(3)}")
                    }
                }
                assertEquals(
                    listOf(
                        "period-1:disabled:0:0",
                        "period-1:enabled:1:0",
                        "period-2:disabled:0:0",
                        "period-2:enabled:1:0",
                    ),
                    snapshots,
                )
            }
            database.query("SELECT is_transition, needs_review FROM periods ORDER BY start_epoch_day").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(0, cursor.getInt(0))
                assertEquals(0, cursor.getInt(1))
            }
            database.query("SELECT COUNT(*) FROM rollover_releases").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(0, cursor.getInt(0))
            }
            database.query("SELECT new_funds_minor FROM periods ORDER BY start_epoch_day").use { cursor ->
                val funds = buildList {
                    while (cursor.moveToNext()) add(cursor.getLong(0))
                }
                assertEquals(listOf(100_000L, 110_000L), funds)
            }
            database.query("SELECT budget_minor, rollover_minor FROM allocations ORDER BY period_id").use { cursor ->
                val allocations = buildList {
                    while (cursor.moveToNext()) add(cursor.getLong(0) to cursor.getLong(1))
                }
                assertEquals(listOf(25_000L to 5_000L, 30_000L to 7_000L), allocations)
            }
            database.query("SELECT id, sar_amount_minor, payment_method_id FROM movements").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("movement-1", cursor.getString(0))
                assertEquals(1_000L, cursor.getLong(1))
                assertEquals("card-1", cursor.getString(2))
            }
        }
    }

    @Test
    fun migration_4_to_5_preserves_manual_fx_and_adds_currency_and_payment_defaults() {
        helper.createDatabase(TEST_DATABASE_4_5, 4).apply {
            execSQL(
                "INSERT INTO periods (id, start_epoch_day, end_exclusive_epoch_day, new_funds_minor, " +
                    "configured_start_day, is_transition, needs_review) " +
                    "VALUES ('period-1', 20478, 20506, 100000, 25, 0, 0)"
            )
            execSQL(
                "INSERT INTO pockets (id, name, icon_key, sort_order, archived, rollover_enabled) " +
                    "VALUES ('pocket-1', 'Viajes', 'TRAVEL', 0, 0, 1)"
            )
            execSQL("INSERT INTO period_pockets (period_id, pocket_id, rollover_eligible, retired) " +
                "VALUES ('period-1', 'pocket-1', 1, 0)")
            execSQL("INSERT INTO allocations (period_id, pocket_id, budget_minor, rollover_minor) " +
                "VALUES ('period-1', 'pocket-1', 25000, 5000)")
            execSQL(
                "INSERT INTO payment_methods (id, name, archived) " +
                    "VALUES ('card-1', 'Tarjeta', 0)"
            )
            execSQL(
                "INSERT INTO movements (id, period_id, pocket_id, type, sar_amount_minor, " +
                    "occurred_at_utc_millis, local_epoch_day, zone_id, merchant, note, " +
                    "payment_method_id, original_amount_minor, original_currency_code, conversion_status, rate) " +
                    "VALUES ('movement-1', 'period-1', 'pocket-1', 'EXPENSE', 12345, 1, 20479, " +
                    "'Asia/Riyadh', 'Merchant', 'Legacy manual FX', 'card-1', 10000, 'USD', " +
                    "'CONFIRMED', '1.2345')"
            )
            execSQL(
                "INSERT INTO recurring_templates (id, name, amount_minor, pocket_id, payment_method_id, archived) " +
                    "VALUES ('template-1', 'Viaje', 5000, 'pocket-1', 'card-1', 0)"
            )
            close()
        }

        helper.runMigrationsAndValidate(
            TEST_DATABASE_4_5,
            5,
            true,
            FinanceDatabase.MIGRATION_4_5,
        ).use { database ->
            database.query("SELECT budget_minor, rollover_minor FROM allocations WHERE pocket_id = 'pocket-1'").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(25_000L, cursor.getLong(0))
                assertEquals(5_000L, cursor.getLong(1))
            }
            database.query(
                "SELECT accounting_currency_code, prior_boundary_from_currency_code, " +
                    "prior_boundary_rate, prior_boundary_effective_epoch_day, prior_boundary_source " +
                    "FROM periods WHERE id = 'period-1'"
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("SAR", cursor.getString(0))
                assertTrue(cursor.isNull(1))
                assertTrue(cursor.isNull(2))
                assertTrue(cursor.isNull(3))
                assertTrue(cursor.isNull(4))
            }
            database.query(
                "SELECT accounting_amount_minor, original_amount_minor, original_currency_code, " +
                    "conversion_status, rate FROM movements WHERE id = 'movement-1'"
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(12_345L, cursor.getLong(0))
                assertEquals(10_000L, cursor.getLong(1))
                assertEquals("USD", cursor.getString(2))
                assertEquals("CONFIRMED", cursor.getString(3))
                assertEquals("1.2345", cursor.getString(4))
            }
            database.query("SELECT input_currency_code FROM recurring_templates WHERE id = 'template-1'").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("SAR", cursor.getString(0))
            }
            database.query("SELECT default_payment_method_id FROM ledger_preferences WHERE id = 1").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("card-1", cursor.getString(0))
            }
            database.query("SELECT COUNT(*) FROM pending_currency_change").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(0, cursor.getInt(0))
            }
        }
    }

    @Test
    fun migration_5_to_6_preserves_ledger_rows_and_starts_with_an_empty_fx_cache() {
        helper.createDatabase(TEST_DATABASE_5_6, 5).apply {
            execSQL(
                "INSERT INTO periods (id, start_epoch_day, end_exclusive_epoch_day, new_funds_minor, " +
                    "configured_start_day, is_transition, needs_review, accounting_currency_code) " +
                    "VALUES ('period-1', 20478, 20506, 100000, 25, 0, 0, 'SAR')"
            )
            execSQL(
                "INSERT INTO pockets (id, name, icon_key, sort_order, archived, rollover_enabled) " +
                    "VALUES ('pocket-1', 'Viajes', 'TRAVEL', 0, 0, 1)"
            )
            execSQL("INSERT INTO period_pockets (period_id, pocket_id, rollover_eligible, retired) " +
                "VALUES ('period-1', 'pocket-1', 1, 0)")
            execSQL("INSERT INTO allocations (period_id, pocket_id, budget_minor, rollover_minor) " +
                "VALUES ('period-1', 'pocket-1', 25000, 5000)")
            execSQL(
                "INSERT INTO payment_methods (id, name, archived) VALUES ('card-1', 'Tarjeta', 0)"
            )
            execSQL(
                "INSERT INTO movements (id, period_id, pocket_id, type, accounting_amount_minor, " +
                    "occurred_at_utc_millis, local_epoch_day, zone_id, merchant, note, payment_method_id, " +
                    "original_amount_minor, original_currency_code, conversion_status, rate) " +
                    "VALUES ('movement-1', 'period-1', 'pocket-1', 'EXPENSE', 12345, 1, 20479, " +
                    "'Asia/Riyadh', NULL, NULL, 'card-1', 10000, 'USD', 'CONFIRMED', '1.2345')"
            )
            execSQL(
                "INSERT INTO recurring_templates (id, name, amount_minor, pocket_id, payment_method_id, archived, input_currency_code) " +
                    "VALUES ('template-1', 'Viaje', 5000, 'pocket-1', 'card-1', 0, 'USD')"
            )
            execSQL(
                "INSERT INTO pending_currency_change (id, from_currency_code, target_currency_code, rate, effective_epoch_day, source) " +
                    "VALUES (1, 'SAR', 'MXN', '4.5', 20506, 'TEST')"
            )
            close()
        }

        helper.runMigrationsAndValidate(
            TEST_DATABASE_5_6,
            6,
            true,
            FinanceDatabase.MIGRATION_5_6,
        ).use { database ->
            database.query("SELECT budget_minor, rollover_minor FROM allocations WHERE pocket_id = 'pocket-1'").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(25_000L, cursor.getLong(0))
                assertEquals(5_000L, cursor.getLong(1))
            }
            database.query("SELECT accounting_amount_minor, original_currency_code, rate, conversion_effective_epoch_day, conversion_source FROM movements").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(12_345L, cursor.getLong(0))
                assertEquals("USD", cursor.getString(1))
                assertEquals("1.2345", cursor.getString(2))
                assertTrue(cursor.isNull(3))
                assertTrue(cursor.isNull(4))
            }
            database.query("SELECT input_currency_code FROM recurring_templates").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("USD", cursor.getString(0))
            }
            database.query("SELECT target_currency_code, rate, quote_effective_epoch_day FROM pending_currency_change").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("MXN", cursor.getString(0))
                assertEquals("4.5", cursor.getString(1))
                assertTrue(cursor.isNull(2))
            }
            database.query("SELECT prior_boundary_quote_effective_epoch_day FROM periods").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertTrue(cursor.isNull(0))
            }
            database.query("SELECT COUNT(*) FROM fx_rate_cache").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(0, cursor.getInt(0))
            }
        }
    }

    @Test
    fun migration_6_to_7_adds_an_empty_private_suggestion_inbox() {
        helper.createDatabase(TEST_DATABASE_6_7, 6).apply {
            execSQL("INSERT INTO periods (id, start_epoch_day, end_exclusive_epoch_day, new_funds_minor, " +
                "configured_start_day, is_transition, needs_review, accounting_currency_code, " +
                "prior_boundary_from_currency_code, prior_boundary_rate, prior_boundary_effective_epoch_day, prior_boundary_source) " +
                "VALUES ('period-1', 20478, 20506, 100000, 25, 0, 0, 'USD', NULL, NULL, NULL, NULL), " +
                "('period-2', 20506, 20537, 110000, 25, 0, 0, 'SAR', 'USD', '3.75', 20506, 'TEST')")
            execSQL("INSERT INTO pockets (id, name, icon_key, sort_order, archived, rollover_enabled) " +
                "VALUES ('pocket-1', 'Viajes', 'TRAVEL', 0, 0, 1)")
            execSQL("INSERT INTO payment_methods (id, name, archived) VALUES ('card-1', 'Tarjeta', 0)")
            execSQL("INSERT INTO period_pockets (period_id, pocket_id, rollover_eligible, retired) " +
                "VALUES ('period-1', 'pocket-1', 1, 0), ('period-2', 'pocket-1', 1, 0)")
            execSQL("INSERT INTO allocations (period_id, pocket_id, budget_minor, rollover_minor) " +
                "VALUES ('period-1', 'pocket-1', 25000, 0), ('period-2', 'pocket-1', 30000, 5000)")
            execSQL("INSERT INTO movements (id, period_id, pocket_id, type, accounting_amount_minor, " +
                "occurred_at_utc_millis, local_epoch_day, zone_id, merchant, note, payment_method_id, " +
                "original_amount_minor, original_currency_code, conversion_status, rate) " +
                "VALUES ('movement-1', 'period-1', 'pocket-1', 'EXPENSE', 1000, 1, 20479, " +
                "'Asia/Riyadh', NULL, NULL, 'card-1', NULL, 'USD', 'CONFIRMED', NULL)")
            execSQL("INSERT INTO ledger_preferences (id, default_payment_method_id) VALUES (1, 'card-1')")
            close()
        }

        helper.runMigrationsAndValidate(
            TEST_DATABASE_6_7,
            7,
            true,
            FinanceDatabase.MIGRATION_6_7,
        ).use { database ->
            database.query("SELECT COUNT(*) FROM movement_suggestions").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(0, cursor.getInt(0))
            }
            database.query("SELECT prior_boundary_rate FROM periods WHERE id = 'period-2'").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("3.75", cursor.getString(0))
            }
            database.query("SELECT COUNT(*) FROM movements").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(1, cursor.getInt(0))
            }
        }
    }

    private companion object {
        const val TEST_DATABASE = "migration-2-3-test"
        const val TEST_DATABASE_3_4 = "migration-3-4-test"
        const val TEST_DATABASE_4_5 = "migration-4-5-test"
        const val TEST_DATABASE_5_6 = "migration-5-6-test"
        const val TEST_DATABASE_6_7 = "migration-6-7-test"
    }
}

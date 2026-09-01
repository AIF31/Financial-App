package com.aif31.pocket.data

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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

    @Test
    fun migration_2_to_3_backfills_known_pocket_artwork_and_preserves_custom_pockets() {
        helper.createDatabase(TEST_DATABASE, 2).apply {
            execSQL(
                "INSERT INTO pockets (id, name, icon_key, sort_order, archived, rollover_enabled) " +
                    "VALUES ('known', 'Supermercado', 'OTHER', 0, 0, 0)"
            )
            execSQL(
                "INSERT INTO pockets (id, name, icon_key, sort_order, archived, rollover_enabled) " +
                    "VALUES ('custom', 'Mascotas', 'OTHER', 1, 0, 0)"
            )
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
            execSQL(
                "INSERT INTO allocations (period_id, pocket_id, budget_minor, rollover_minor) " +
                    "VALUES ('period-1', 'enabled', 25000, 5000), ('period-2', 'enabled', 30000, 7000)"
            )
            execSQL(
                "INSERT INTO movements (id, period_id, pocket_id, type, sar_amount_minor, occurred_at_utc_millis, " +
                    "local_epoch_day, zone_id, merchant, note, payment_method_id, original_amount_minor, " +
                    "original_currency_code, conversion_status, rate) VALUES " +
                    "('movement-1', 'period-1', 'enabled', 'EXPENSE', 1000, 1, 20479, 'Asia/Riyadh', NULL, NULL, NULL, NULL, 'SAR', 'CONFIRMED', NULL)"
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
            database.query("SELECT id, sar_amount_minor FROM movements").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("movement-1", cursor.getString(0))
                assertEquals(1_000L, cursor.getLong(1))
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

    private companion object {
        const val TEST_DATABASE = "migration-2-3-test"
        const val TEST_DATABASE_3_4 = "migration-3-4-test"
        const val TEST_DATABASE_4_5 = "migration-4-5-test"
    }
}

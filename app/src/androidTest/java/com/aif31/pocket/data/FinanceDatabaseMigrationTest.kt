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

    private companion object {
        const val TEST_DATABASE = "migration-2-3-test"
        const val TEST_DATABASE_3_4 = "migration-3-4-test"
    }
}

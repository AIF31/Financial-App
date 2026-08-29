package com.aif31.pocket.data

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
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

    private companion object {
        const val TEST_DATABASE = "migration-2-3-test"
    }
}

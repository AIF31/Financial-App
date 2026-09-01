package com.aif31.pocket.data

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import androidx.room.Upsert
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.aif31.pocket.domain.SupportedCurrency
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "periods", indices = [Index(value = ["start_epoch_day"], unique = true)])
data class PeriodEntity(
    @androidx.room.PrimaryKey val id: String,
    @ColumnInfo(name = "start_epoch_day") val startEpochDay: Long,
    @ColumnInfo(name = "end_exclusive_epoch_day") val endExclusiveEpochDay: Long,
    @ColumnInfo(name = "new_funds_minor") val newFundsMinor: Long,
    @ColumnInfo(name = "configured_start_day") val configuredStartDay: Int,
    @ColumnInfo(name = "is_transition", defaultValue = "0") val isTransition: Boolean = false,
    @ColumnInfo(name = "needs_review", defaultValue = "0") val needsReview: Boolean = false,
    @ColumnInfo(name = "accounting_currency_code", defaultValue = "'SAR'")
    val accountingCurrencyCode: String = SupportedCurrency.SAR.name,
    @ColumnInfo(name = "prior_boundary_from_currency_code") val priorBoundaryFromCurrencyCode: String? = null,
    @ColumnInfo(name = "prior_boundary_rate") val priorBoundaryRate: String? = null,
    @ColumnInfo(name = "prior_boundary_effective_epoch_day") val priorBoundaryEffectiveEpochDay: Long? = null,
    @ColumnInfo(name = "prior_boundary_source") val priorBoundarySource: String? = null,
)

@Entity(tableName = "pockets", indices = [Index(value = ["name"], unique = true)])
data class PocketEntity(
    @androidx.room.PrimaryKey val id: String,
    val name: String,
    @ColumnInfo(name = "icon_key", defaultValue = "'OTHER'") val iconKey: String = PocketIconKey.OTHER.name,
    @ColumnInfo(name = "sort_order") val sortOrder: Int,
    val archived: Boolean,
    @ColumnInfo(name = "rollover_enabled") val rolloverEnabled: Boolean,
)

@Entity(
    tableName = "allocations",
    primaryKeys = ["period_id", "pocket_id"],
    foreignKeys = [
        ForeignKey(entity = PeriodEntity::class, parentColumns = ["id"], childColumns = ["period_id"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = PocketEntity::class, parentColumns = ["id"], childColumns = ["pocket_id"], onDelete = ForeignKey.RESTRICT),
    ],
    indices = [Index("period_id"), Index("pocket_id")],
)
data class AllocationEntity(
    @ColumnInfo(name = "period_id") val periodId: String,
    @ColumnInfo(name = "pocket_id") val pocketId: String,
    @ColumnInfo(name = "budget_minor") val budgetMinor: Long,
    @ColumnInfo(name = "rollover_minor") val rolloverMinor: Long,
)

@Entity(
    tableName = "period_pockets",
    primaryKeys = ["period_id", "pocket_id"],
    foreignKeys = [
        ForeignKey(entity = PeriodEntity::class, parentColumns = ["id"], childColumns = ["period_id"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = PocketEntity::class, parentColumns = ["id"], childColumns = ["pocket_id"], onDelete = ForeignKey.RESTRICT),
    ],
    indices = [Index("period_id"), Index("pocket_id")],
)
data class PeriodPocketEntity(
    @ColumnInfo(name = "period_id") val periodId: String,
    @ColumnInfo(name = "pocket_id") val pocketId: String,
    @ColumnInfo(name = "rollover_eligible") val rolloverEligible: Boolean,
    val retired: Boolean,
)

@Entity(
    tableName = "rollover_releases",
    primaryKeys = ["period_id", "pocket_id"],
    foreignKeys = [
        ForeignKey(entity = PeriodEntity::class, parentColumns = ["id"], childColumns = ["period_id"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = PocketEntity::class, parentColumns = ["id"], childColumns = ["pocket_id"], onDelete = ForeignKey.RESTRICT),
    ],
    indices = [Index("period_id"), Index("pocket_id")],
)
data class RolloverReleaseEntity(
    @ColumnInfo(name = "period_id") val periodId: String,
    @ColumnInfo(name = "pocket_id") val pocketId: String,
    @ColumnInfo(name = "amount_minor") val amountMinor: Long,
)

@Entity(tableName = "payment_methods", indices = [Index(value = ["name"], unique = true)])
data class PaymentMethodEntity(
    @androidx.room.PrimaryKey val id: String,
    val name: String,
    val archived: Boolean,
)

@Entity(
    tableName = "movements",
    foreignKeys = [
        ForeignKey(entity = PeriodEntity::class, parentColumns = ["id"], childColumns = ["period_id"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = PocketEntity::class, parentColumns = ["id"], childColumns = ["pocket_id"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = PaymentMethodEntity::class, parentColumns = ["id"], childColumns = ["payment_method_id"], onDelete = ForeignKey.SET_NULL),
    ],
    indices = [Index("period_id"), Index("pocket_id"), Index("payment_method_id"), Index("local_epoch_day")],
)
data class MovementEntity(
    @androidx.room.PrimaryKey val id: String,
    @ColumnInfo(name = "period_id") val periodId: String,
    @ColumnInfo(name = "pocket_id") val pocketId: String,
    val type: String,
    @ColumnInfo(name = "accounting_amount_minor") val accountingAmountMinor: Long,
    @ColumnInfo(name = "occurred_at_utc_millis") val occurredAtUtcMillis: Long,
    @ColumnInfo(name = "local_epoch_day") val localEpochDay: Long,
    @ColumnInfo(name = "zone_id") val zoneId: String,
    val merchant: String?,
    val note: String?,
    @ColumnInfo(name = "payment_method_id") val paymentMethodId: String?,
    @ColumnInfo(name = "original_amount_minor") val originalAmountMinor: Long?,
    @ColumnInfo(name = "original_currency_code") val originalCurrencyCode: String,
    @ColumnInfo(name = "conversion_status") val conversionStatus: String,
    val rate: String?,
)

@Entity(
    tableName = "recurring_templates",
    foreignKeys = [
        ForeignKey(entity = PocketEntity::class, parentColumns = ["id"], childColumns = ["pocket_id"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = PaymentMethodEntity::class, parentColumns = ["id"], childColumns = ["payment_method_id"], onDelete = ForeignKey.SET_NULL),
    ],
    indices = [Index("pocket_id"), Index("payment_method_id")],
)
data class RecurringTemplateEntity(
    @androidx.room.PrimaryKey val id: String,
    val name: String,
    @ColumnInfo(name = "amount_minor") val amountMinor: Long,
    @ColumnInfo(name = "pocket_id") val pocketId: String,
    @ColumnInfo(name = "payment_method_id") val paymentMethodId: String?,
    val archived: Boolean,
    @ColumnInfo(name = "input_currency_code", defaultValue = "'SAR'")
    val inputCurrencyCode: String = SupportedCurrency.SAR.name,
)

@Entity(tableName = "pending_currency_change")
data class PendingCurrencyChangeEntity(
    @androidx.room.PrimaryKey val id: Int = SINGLETON_ID,
    @ColumnInfo(name = "from_currency_code") val fromCurrencyCode: String,
    @ColumnInfo(name = "target_currency_code") val targetCurrencyCode: String,
    val rate: String,
    @ColumnInfo(name = "effective_epoch_day") val effectiveEpochDay: Long,
    val source: String,
) {
    companion object { const val SINGLETON_ID = 1 }
}

@Entity(
    tableName = "ledger_preferences",
    foreignKeys = [
        ForeignKey(
            entity = PaymentMethodEntity::class,
            parentColumns = ["id"],
            childColumns = ["default_payment_method_id"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index("default_payment_method_id")],
)
data class LedgerPreferencesEntity(
    @androidx.room.PrimaryKey val id: Int = SINGLETON_ID,
    @ColumnInfo(name = "default_payment_method_id") val defaultPaymentMethodId: String?,
) {
    companion object { const val SINGLETON_ID = 1 }
}

@Dao
interface FinanceDao {
    @Query("SELECT * FROM periods ORDER BY start_epoch_day") fun observePeriods(): Flow<List<PeriodEntity>>
    @Query("SELECT * FROM pockets ORDER BY sort_order, name") fun observePockets(): Flow<List<PocketEntity>>
    @Query("SELECT * FROM allocations") fun observeAllocations(): Flow<List<AllocationEntity>>
    @Query("SELECT * FROM period_pockets") fun observePeriodPockets(): Flow<List<PeriodPocketEntity>>
    @Query("SELECT * FROM rollover_releases") fun observeRolloverReleases(): Flow<List<RolloverReleaseEntity>>
    @Query("SELECT * FROM payment_methods ORDER BY name") fun observePaymentMethods(): Flow<List<PaymentMethodEntity>>
    @Query("SELECT * FROM movements ORDER BY occurred_at_utc_millis DESC") fun observeMovements(): Flow<List<MovementEntity>>
    @Query("SELECT * FROM recurring_templates ORDER BY name") fun observeTemplates(): Flow<List<RecurringTemplateEntity>>
    @Query("SELECT * FROM pending_currency_change WHERE id = 1") fun observePendingCurrencyChange(): Flow<PendingCurrencyChangeEntity?>
    @Query("SELECT * FROM ledger_preferences WHERE id = 1") fun observeLedgerPreferences(): Flow<LedgerPreferencesEntity?>

    @Query("SELECT * FROM periods ORDER BY start_epoch_day") suspend fun periods(): List<PeriodEntity>
    @Query("SELECT * FROM pockets ORDER BY sort_order, name") suspend fun pockets(): List<PocketEntity>
    @Query("SELECT * FROM allocations") suspend fun allocations(): List<AllocationEntity>
    @Query("SELECT * FROM period_pockets") suspend fun periodPockets(): List<PeriodPocketEntity>
    @Query("SELECT * FROM rollover_releases") suspend fun rolloverReleases(): List<RolloverReleaseEntity>
    @Query("SELECT * FROM payment_methods ORDER BY name") suspend fun paymentMethods(): List<PaymentMethodEntity>
    @Query("SELECT * FROM movements ORDER BY occurred_at_utc_millis DESC") suspend fun movements(): List<MovementEntity>
    @Query("SELECT * FROM recurring_templates ORDER BY name") suspend fun templates(): List<RecurringTemplateEntity>
    @Query("SELECT * FROM pending_currency_change WHERE id = 1") suspend fun pendingCurrencyChange(): PendingCurrencyChangeEntity?
    @Query("SELECT * FROM ledger_preferences WHERE id = 1") suspend fun ledgerPreferences(): LedgerPreferencesEntity?
    @Query("SELECT COUNT(*) FROM periods") suspend fun periodCount(): Int
    @Query("SELECT * FROM periods WHERE id = :id") suspend fun period(id: String): PeriodEntity?
    @Query("SELECT * FROM movements WHERE id = :id") suspend fun movement(id: String): MovementEntity?
    @Query("SELECT * FROM allocations WHERE period_id = :periodId AND pocket_id = :pocketId") suspend fun allocation(periodId: String, pocketId: String): AllocationEntity?
    @Query("SELECT COALESCE(SUM(budget_minor), 0) FROM allocations WHERE period_id = :periodId") suspend fun allocated(periodId: String): Long

    @Upsert suspend fun putPeriod(value: PeriodEntity)
    @Upsert suspend fun putPockets(values: List<PocketEntity>)
    @Upsert suspend fun putPocket(value: PocketEntity)
    @Upsert suspend fun putAllocation(value: AllocationEntity)
    @Upsert suspend fun putAllocations(values: List<AllocationEntity>)
    @Upsert suspend fun putPeriodPockets(values: List<PeriodPocketEntity>)
    @Upsert suspend fun putPeriodPocket(value: PeriodPocketEntity)
    @Upsert suspend fun putRolloverReleases(values: List<RolloverReleaseEntity>)
    @Upsert suspend fun putRolloverRelease(value: RolloverReleaseEntity)
    @Upsert suspend fun putPaymentMethods(values: List<PaymentMethodEntity>)
    @Upsert suspend fun putPaymentMethod(value: PaymentMethodEntity)
    @Upsert suspend fun putMovement(value: MovementEntity)
    @Upsert suspend fun putMovements(values: List<MovementEntity>)
    @Upsert suspend fun putTemplates(values: List<RecurringTemplateEntity>)
    @Upsert suspend fun putTemplate(value: RecurringTemplateEntity)
    @Upsert suspend fun putPendingCurrencyChange(value: PendingCurrencyChangeEntity)
    @Upsert suspend fun putLedgerPreferences(value: LedgerPreferencesEntity)

    @Update suspend fun updatePeriod(value: PeriodEntity)
    @Query("DELETE FROM movements WHERE id = :id") suspend fun deleteMovement(id: String)
    @Query("DELETE FROM allocations WHERE pocket_id = :pocketId AND period_id IN (:periodIds)")
    suspend fun deleteAllocations(pocketId: String, periodIds: List<String>)
    @Query("DELETE FROM period_pockets WHERE pocket_id = :pocketId AND period_id IN (:periodIds)")
    suspend fun deletePeriodPockets(pocketId: String, periodIds: List<String>)
    @Query("DELETE FROM rollover_releases WHERE period_id = :periodId AND pocket_id = :pocketId")
    suspend fun deleteRolloverRelease(periodId: String, pocketId: String)
    @Query("DELETE FROM recurring_templates") suspend fun clearTemplates()
    @Query("DELETE FROM pending_currency_change") suspend fun clearPendingCurrencyChange()
    @Query("DELETE FROM ledger_preferences") suspend fun clearLedgerPreferences()
    @Query("DELETE FROM movements") suspend fun clearMovements()
    @Query("DELETE FROM rollover_releases") suspend fun clearRolloverReleases()
    @Query("DELETE FROM allocations") suspend fun clearAllocations()
    @Query("DELETE FROM period_pockets") suspend fun clearPeriodPockets()
    @Query("DELETE FROM payment_methods") suspend fun clearPaymentMethods()
    @Query("DELETE FROM pockets") suspend fun clearPockets()
    @Query("DELETE FROM periods") suspend fun clearPeriods()
}

@Database(
    entities = [
        PeriodEntity::class,
        PocketEntity::class,
        AllocationEntity::class,
        PeriodPocketEntity::class,
        RolloverReleaseEntity::class,
        PaymentMethodEntity::class,
        MovementEntity::class,
        RecurringTemplateEntity::class,
        PendingCurrencyChangeEntity::class,
        LedgerPreferencesEntity::class,
    ],
    version = 5,
    autoMigrations = [AutoMigration(from = 1, to = 2)],
    exportSchema = true,
)
abstract class FinanceDatabase : RoomDatabase() {
    abstract fun financeDao(): FinanceDao

    companion object {
        fun open(context: Context): FinanceDatabase =
            Room.databaseBuilder(context.applicationContext, FinanceDatabase::class.java, "pocket.db")
                .addMigrations(MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                .build()

        fun inMemory(context: Context): FinanceDatabase =
            Room.inMemoryDatabaseBuilder(context.applicationContext, FinanceDatabase::class.java)
                .allowMainThreadQueries()
                .build()

        internal val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                val legacyPockets = buildList {
                    db.query("SELECT id, name FROM pockets WHERE icon_key = 'OTHER'").use { cursor ->
                        while (cursor.moveToNext()) {
                            add(cursor.getString(0) to cursor.getString(1))
                        }
                    }
                }
                legacyPockets.forEach { (id, name) ->
                    val iconKey = PocketIconKey.forName(name)
                    if (iconKey != PocketIconKey.OTHER) {
                        db.execSQL(
                            "UPDATE pockets SET icon_key = ? WHERE id = ?",
                            arrayOf(iconKey.name, id),
                        )
                    }
                }
            }
        }

        internal val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE periods ADD COLUMN is_transition INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE periods ADD COLUMN needs_review INTEGER NOT NULL DEFAULT 0")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS period_pockets (" +
                        "period_id TEXT NOT NULL, pocket_id TEXT NOT NULL, rollover_eligible INTEGER NOT NULL, " +
                        "retired INTEGER NOT NULL, PRIMARY KEY(period_id, pocket_id), " +
                        "FOREIGN KEY(period_id) REFERENCES periods(id) ON UPDATE NO ACTION ON DELETE CASCADE, " +
                        "FOREIGN KEY(pocket_id) REFERENCES pockets(id) ON UPDATE NO ACTION ON DELETE RESTRICT)"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_period_pockets_period_id ON period_pockets(period_id)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_period_pockets_pocket_id ON period_pockets(pocket_id)")
                db.execSQL(
                    "INSERT INTO period_pockets (period_id, pocket_id, rollover_eligible, retired) " +
                        "SELECT periods.id, pockets.id, pockets.rollover_enabled, 0 FROM periods CROSS JOIN pockets"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS rollover_releases (" +
                        "period_id TEXT NOT NULL, pocket_id TEXT NOT NULL, amount_minor INTEGER NOT NULL, " +
                        "PRIMARY KEY(period_id, pocket_id), " +
                        "FOREIGN KEY(period_id) REFERENCES periods(id) ON UPDATE NO ACTION ON DELETE CASCADE, " +
                        "FOREIGN KEY(pocket_id) REFERENCES pockets(id) ON UPDATE NO ACTION ON DELETE RESTRICT)"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_rollover_releases_period_id ON rollover_releases(period_id)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_rollover_releases_pocket_id ON rollover_releases(pocket_id)")
            }
        }

        internal val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE periods ADD COLUMN accounting_currency_code TEXT NOT NULL DEFAULT 'SAR'")
                db.execSQL("ALTER TABLE periods ADD COLUMN prior_boundary_from_currency_code TEXT")
                db.execSQL("ALTER TABLE periods ADD COLUMN prior_boundary_rate TEXT")
                db.execSQL("ALTER TABLE periods ADD COLUMN prior_boundary_effective_epoch_day INTEGER")
                db.execSQL("ALTER TABLE periods ADD COLUMN prior_boundary_source TEXT")

                db.execSQL(
                    "CREATE TABLE movements_new (" +
                        "id TEXT NOT NULL, period_id TEXT NOT NULL, pocket_id TEXT NOT NULL, type TEXT NOT NULL, " +
                        "accounting_amount_minor INTEGER NOT NULL, occurred_at_utc_millis INTEGER NOT NULL, " +
                        "local_epoch_day INTEGER NOT NULL, zone_id TEXT NOT NULL, merchant TEXT, note TEXT, " +
                        "payment_method_id TEXT, original_amount_minor INTEGER, original_currency_code TEXT NOT NULL, " +
                        "conversion_status TEXT NOT NULL, rate TEXT, PRIMARY KEY(id), " +
                        "FOREIGN KEY(period_id) REFERENCES periods(id) ON UPDATE NO ACTION ON DELETE RESTRICT, " +
                        "FOREIGN KEY(pocket_id) REFERENCES pockets(id) ON UPDATE NO ACTION ON DELETE RESTRICT, " +
                        "FOREIGN KEY(payment_method_id) REFERENCES payment_methods(id) ON UPDATE NO ACTION ON DELETE SET NULL)"
                )
                db.execSQL(
                    "INSERT INTO movements_new (id, period_id, pocket_id, type, accounting_amount_minor, " +
                        "occurred_at_utc_millis, local_epoch_day, zone_id, merchant, note, payment_method_id, " +
                        "original_amount_minor, original_currency_code, conversion_status, rate) " +
                        "SELECT id, period_id, pocket_id, type, sar_amount_minor, occurred_at_utc_millis, " +
                        "local_epoch_day, zone_id, merchant, note, payment_method_id, original_amount_minor, " +
                        "original_currency_code, conversion_status, rate FROM movements"
                )
                db.execSQL("DROP TABLE movements")
                db.execSQL("ALTER TABLE movements_new RENAME TO movements")
                db.execSQL("CREATE INDEX index_movements_period_id ON movements(period_id)")
                db.execSQL("CREATE INDEX index_movements_pocket_id ON movements(pocket_id)")
                db.execSQL("CREATE INDEX index_movements_payment_method_id ON movements(payment_method_id)")
                db.execSQL("CREATE INDEX index_movements_local_epoch_day ON movements(local_epoch_day)")

                db.execSQL("ALTER TABLE recurring_templates ADD COLUMN input_currency_code TEXT NOT NULL DEFAULT 'SAR'")
                db.execSQL(
                    "CREATE TABLE pending_currency_change (" +
                        "id INTEGER NOT NULL, from_currency_code TEXT NOT NULL, target_currency_code TEXT NOT NULL, " +
                        "rate TEXT NOT NULL, effective_epoch_day INTEGER NOT NULL, source TEXT NOT NULL, PRIMARY KEY(id))"
                )
                db.execSQL(
                    "CREATE TABLE ledger_preferences (" +
                        "id INTEGER NOT NULL, default_payment_method_id TEXT, PRIMARY KEY(id), " +
                        "FOREIGN KEY(default_payment_method_id) REFERENCES payment_methods(id) " +
                        "ON UPDATE NO ACTION ON DELETE SET NULL)"
                )
                db.execSQL(
                    "CREATE INDEX index_ledger_preferences_default_payment_method_id " +
                        "ON ledger_preferences(default_payment_method_id)"
                )
                db.execSQL(
                    "INSERT INTO ledger_preferences (id, default_payment_method_id) " +
                        "SELECT 1, (SELECT id FROM payment_methods WHERE archived = 0 " +
                        "AND name = 'Tarjeta' COLLATE NOCASE ORDER BY id LIMIT 1)"
                )
            }
        }
    }
}

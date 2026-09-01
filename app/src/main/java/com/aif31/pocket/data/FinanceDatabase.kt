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
    @ColumnInfo(name = "sar_amount_minor") val sarAmountMinor: Long,
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
)

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

    @Query("SELECT * FROM periods ORDER BY start_epoch_day") suspend fun periods(): List<PeriodEntity>
    @Query("SELECT * FROM pockets ORDER BY sort_order, name") suspend fun pockets(): List<PocketEntity>
    @Query("SELECT * FROM allocations") suspend fun allocations(): List<AllocationEntity>
    @Query("SELECT * FROM period_pockets") suspend fun periodPockets(): List<PeriodPocketEntity>
    @Query("SELECT * FROM rollover_releases") suspend fun rolloverReleases(): List<RolloverReleaseEntity>
    @Query("SELECT * FROM payment_methods ORDER BY name") suspend fun paymentMethods(): List<PaymentMethodEntity>
    @Query("SELECT * FROM movements ORDER BY occurred_at_utc_millis DESC") suspend fun movements(): List<MovementEntity>
    @Query("SELECT * FROM recurring_templates ORDER BY name") suspend fun templates(): List<RecurringTemplateEntity>
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

    @Update suspend fun updatePeriod(value: PeriodEntity)
    @Query("DELETE FROM movements WHERE id = :id") suspend fun deleteMovement(id: String)
    @Query("DELETE FROM allocations WHERE pocket_id = :pocketId AND period_id IN (:periodIds)")
    suspend fun deleteAllocations(pocketId: String, periodIds: List<String>)
    @Query("DELETE FROM period_pockets WHERE pocket_id = :pocketId AND period_id IN (:periodIds)")
    suspend fun deletePeriodPockets(pocketId: String, periodIds: List<String>)
    @Query("DELETE FROM rollover_releases WHERE period_id = :periodId AND pocket_id = :pocketId")
    suspend fun deleteRolloverRelease(periodId: String, pocketId: String)
    @Query("DELETE FROM recurring_templates") suspend fun clearTemplates()
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
    ],
    version = 4,
    autoMigrations = [AutoMigration(from = 1, to = 2)],
    exportSchema = true,
)
abstract class FinanceDatabase : RoomDatabase() {
    abstract fun financeDao(): FinanceDao

    companion object {
        fun open(context: Context): FinanceDatabase =
            Room.databaseBuilder(context.applicationContext, FinanceDatabase::class.java, "pocket.db")
                .addMigrations(MIGRATION_2_3, MIGRATION_3_4)
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
    }
}

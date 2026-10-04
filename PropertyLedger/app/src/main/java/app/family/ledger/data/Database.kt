package app.family.ledger.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

const val DB_VERSION = 1

/** Tables in dependency order (parents first). Used by backup / restore / CSV. */
val TABLES = listOf(
    "app_settings", "options", "people", "buildings", "floors", "units", "tenants", "residents",
    "rent_obligations", "payments", "debts", "payment_allocations", "handovers", "expenses",
    "maintenance", "photos"
)

@Dao
interface AppDao {
    @Query("SELECT * FROM buildings ORDER BY sortOrder, number") fun buildings(): Flow<List<Building>>
    @Query("SELECT * FROM floors ORDER BY sortOrder, number") fun floors(): Flow<List<Floor>>
    @Query("SELECT * FROM units ORDER BY number, name") fun units(): Flow<List<PropertyUnit>>
    @Query("SELECT * FROM people ORDER BY name") fun people(): Flow<List<Person>>
    @Query("SELECT * FROM tenants ORDER BY name") fun tenants(): Flow<List<Tenant>>
    @Query("SELECT * FROM residents ORDER BY name") fun residents(): Flow<List<Resident>>
    @Query("SELECT * FROM rent_obligations ORDER BY year, month") fun obligations(): Flow<List<RentObligation>>
    @Query("SELECT * FROM payments ORDER BY date DESC") fun payments(): Flow<List<Payment>>
    @Query("SELECT * FROM payment_allocations") fun allocations(): Flow<List<PaymentAllocation>>
    @Query("SELECT * FROM debts ORDER BY date") fun debts(): Flow<List<Debt>>
    @Query("SELECT * FROM handovers ORDER BY date DESC") fun handovers(): Flow<List<Handover>>
    @Query("SELECT * FROM expenses ORDER BY date DESC") fun expenses(): Flow<List<Expense>>
    @Query("SELECT * FROM maintenance ORDER BY date DESC") fun maintenance(): Flow<List<Maintenance>>
    @Query("SELECT * FROM photos ORDER BY date") fun photos(): Flow<List<Photo>>
    @Query("SELECT * FROM options ORDER BY value") fun options(): Flow<List<OptionItem>>
    @Query("SELECT * FROM app_settings") fun settings(): Flow<List<Setting>>

    @Query("SELECT * FROM tenants") suspend fun tenantsOnce(): List<Tenant>
    @Query("SELECT * FROM units") suspend fun unitsOnce(): List<PropertyUnit>
    @Query("SELECT * FROM rent_obligations") suspend fun obligationsOnce(): List<RentObligation>
    @Query("SELECT * FROM debts") suspend fun debtsOnce(): List<Debt>
    @Query("SELECT * FROM payment_allocations") suspend fun allocationsOnce(): List<PaymentAllocation>
    @Query("SELECT COUNT(*) FROM buildings") suspend fun buildingCount(): Int
    @Query("SELECT value FROM app_settings WHERE `key` = :key") suspend fun setting(key: String): String?
    @Query("DELETE FROM payment_allocations WHERE paymentId = :paymentId") suspend fun deleteAllocationsOf(paymentId: String)
    @Query("SELECT * FROM photos WHERE path = :p") suspend fun photoByPath(p: String): Photo?

    @Upsert suspend fun upSetting(x: Setting)
    @Upsert suspend fun upOption(x: OptionItem)
    @Upsert suspend fun upPerson(x: Person)
    @Upsert suspend fun upBuilding(x: Building)
    @Upsert suspend fun upFloor(x: Floor)
    @Upsert suspend fun upUnit(x: PropertyUnit)
    @Upsert suspend fun upTenant(x: Tenant)
    @Upsert suspend fun upResident(x: Resident)
    @Upsert suspend fun upObligation(x: RentObligation)
    @Upsert suspend fun upPayment(x: Payment)
    @Upsert suspend fun upAllocation(x: PaymentAllocation)
    @Upsert suspend fun upDebt(x: Debt)
    @Upsert suspend fun upHandover(x: Handover)
    @Upsert suspend fun upExpense(x: Expense)
    @Upsert suspend fun upMaintenance(x: Maintenance)
    @Upsert suspend fun upPhoto(x: Photo)

    @Delete suspend fun delResident(x: Resident)
    @Delete suspend fun delPhoto(x: Photo)
}

// Schema changes must ship a Migration here. The database is never deleted to "fix" a schema change.
@Database(
    entities = [
        Setting::class, OptionItem::class, Person::class, Building::class, Floor::class, PropertyUnit::class,
        Tenant::class, Resident::class, RentObligation::class, Payment::class, PaymentAllocation::class,
        Debt::class, Handover::class, Expense::class, Maintenance::class, Photo::class
    ],
    version = DB_VERSION,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dao(): AppDao

    companion object {
        @Volatile private var inst: AppDatabase? = null
        fun get(ctx: Context): AppDatabase = inst ?: synchronized(this) {
            inst ?: Room.databaseBuilder(ctx.applicationContext, AppDatabase::class.java, "ledger.db")
                .addMigrations(*ALL_MIGRATIONS)
                .build().also { inst = it }
        }
    }
}

val ALL_MIGRATIONS: Array<androidx.room.migration.Migration> = arrayOf()

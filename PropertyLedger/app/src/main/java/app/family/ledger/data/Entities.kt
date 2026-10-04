package app.family.ledger.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

fun newId(): String = UUID.randomUUID().toString()

// Stable UUID ids everywhere. Visible numbers/names are plain editable fields.
// Option-like fields (type, status, ownership, category...) store English keys that the UI translates.

@Entity(tableName = "app_settings")
data class Setting(@PrimaryKey val key: String, val value: String)

@Entity(tableName = "options")
data class OptionItem(
    @PrimaryKey val id: String = newId(),
    val kind: String, // UNIT_TYPE, EXPENSE_CATEGORY, DEBT_TYPE
    val value: String,
    val archived: Boolean = false
)

@Entity(tableName = "people")
data class Person(
    @PrimaryKey val id: String = newId(),
    val name: String,
    val phone: String = "",
    val notes: String = "",
    val archived: Boolean = false
)

@Entity(tableName = "buildings")
data class Building(
    @PrimaryKey val id: String = newId(),
    val number: String = "",
    val name: String = "",
    val address: String = "",
    val notes: String = "",
    val archived: Boolean = false,
    val sortOrder: Int = 0,
    // reserved for a future 3D / map view
    val posX: Double = 0.0,
    val posY: Double = 0.0,
    val posZ: Double = 0.0
)

@Entity(
    tableName = "floors",
    foreignKeys = [ForeignKey(Building::class, ["id"], ["buildingId"])],
    indices = [Index("buildingId")]
)
data class Floor(
    @PrimaryKey val id: String = newId(),
    val buildingId: String,
    val number: Int = 0,
    val name: String = "",
    val ownership: String = "Ours",
    val notes: String = "",
    val archived: Boolean = false,
    val sortOrder: Int = 0
)

@Entity(
    tableName = "units",
    foreignKeys = [
        ForeignKey(Building::class, ["id"], ["buildingId"]),
        ForeignKey(Floor::class, ["id"], ["floorId"])
    ],
    indices = [Index("buildingId"), Index("floorId")]
)
data class PropertyUnit(
    @PrimaryKey val id: String = newId(),
    val buildingId: String,
    val floorId: String,
    val number: String = "",
    val name: String = "",
    val type: String = "House",
    val ownership: String = "Ours",
    val ownerPersonId: String? = null,
    val status: String = "Rented", // Rented, Vacant, Family, Not ours, Maintenance
    val rentAmount: Double = 0.0,
    val currency: String = "USD",
    val contactName: String = "",
    val contactPhone: String = "",
    val defaultCollectedForId: String? = null,
    val notes: String = "",
    val archived: Boolean = false,
    // reserved for future floor-plan / 3D layout
    val planX: Double = 0.0,
    val planY: Double = 0.0,
    val planW: Double = 0.0,
    val planH: Double = 0.0,
    val planRotation: Double = 0.0,
    val planShape: String = "rect",
    val planLabel: String = ""
)

/** One tenancy of a unit. A tenant who leaves keeps the record (endDate / active=false) so history survives. */
@Entity(
    tableName = "tenants",
    foreignKeys = [ForeignKey(PropertyUnit::class, ["id"], ["unitId"])],
    indices = [Index("unitId")]
)
data class Tenant(
    @PrimaryKey val id: String = newId(),
    val unitId: String,
    val name: String,
    val phone: String = "",
    val phone2: String = "",
    val email: String = "",
    val contactName: String = "",
    val contactPhone: String = "",
    val contactRelation: String = "",
    val photoPath: String = "",
    val householdType: String = "Family", // Family, Men, Women, Individuals, Other
    val father: Int = 0,
    val mother: Int = 0,
    val sons: Int = 0,
    val daughters: Int = 0,
    val otherCount: Int = 0,
    val monthlyRent: Double = 0.0,
    val currency: String = "USD",
    val dueDay: Int = 1, // 1 = beginning, 31 = end of month, anything else = custom day
    val startDate: String = "",
    val endDate: String = "",
    val notes: String = "",
    val active: Boolean = true,
    val archived: Boolean = false
) {
    val residentsTotal: Int
        get() = if (householdType == "Family") father + mother + sons + daughters else otherCount
}

@Entity(
    tableName = "residents",
    foreignKeys = [ForeignKey(Tenant::class, ["id"], ["tenantId"])],
    indices = [Index("tenantId")]
)
data class Resident(
    @PrimaryKey val id: String = newId(),
    val tenantId: String,
    val name: String,
    val relationship: String = "",
    val age: Int? = null,
    val gender: String = "",
    val phone: String = "",
    val notes: String = ""
)

@Entity(
    tableName = "rent_obligations",
    foreignKeys = [ForeignKey(Tenant::class, ["id"], ["tenantId"])],
    indices = [Index("tenantId"), Index("unitId"), Index(value = ["tenantId", "year", "month"], unique = true)]
)
data class RentObligation(
    @PrimaryKey val id: String = newId(),
    val tenantId: String,
    val unitId: String,
    val year: Int,
    val month: Int,
    val expected: Double,
    val currency: String,
    val dueDate: String,
    val collectedForId: String? = null,
    val notes: String = ""
)

@Entity(
    tableName = "payments",
    foreignKeys = [ForeignKey(Tenant::class, ["id"], ["tenantId"])],
    indices = [Index("tenantId"), Index("collectedForId"), Index("date")]
)
data class Payment(
    @PrimaryKey val id: String = newId(),
    val tenantId: String,
    val unitId: String,
    val amount: Double,
    val currency: String,
    val date: String,
    val notes: String = "",
    val collectedForId: String? = null, // stored on the payment itself: history never changes
    val voided: Boolean = false
)

@Entity(
    tableName = "debts",
    foreignKeys = [ForeignKey(Tenant::class, ["id"], ["tenantId"])],
    indices = [Index("tenantId")]
)
data class Debt(
    @PrimaryKey val id: String = newId(),
    val tenantId: String,
    val unitId: String,
    val type: String = "Other", // Old Rent Debt, Current Rent Arrears, Repair Charge, Utility Charge, Other
    val amount: Double,
    val currency: String,
    val date: String,
    val description: String = "",
    val notes: String = ""
)

@Entity(
    tableName = "payment_allocations",
    foreignKeys = [ForeignKey(Payment::class, ["id"], ["paymentId"])],
    indices = [Index("paymentId"), Index("obligationId"), Index("debtId")]
)
data class PaymentAllocation(
    @PrimaryKey val id: String = newId(),
    val paymentId: String,
    val obligationId: String? = null,
    val debtId: String? = null,
    val amount: Double
)

@Entity(
    tableName = "handovers",
    foreignKeys = [ForeignKey(Person::class, ["id"], ["personId"])],
    indices = [Index("personId")]
)
data class Handover(
    @PrimaryKey val id: String = newId(),
    val personId: String,
    val amount: Double,
    val currency: String,
    val date: String,
    val notes: String = ""
)

@Entity(tableName = "expenses", indices = [Index("date")])
data class Expense(
    @PrimaryKey val id: String = newId(),
    val buildingId: String? = null,
    val floorId: String? = null,
    val unitId: String? = null,
    val tenantId: String? = null,
    val category: String = "Other",
    val amount: Double,
    val currency: String,
    val date: String,
    val description: String = "",
    val notes: String = ""
)

@Entity(tableName = "maintenance")
data class Maintenance(
    @PrimaryKey val id: String = newId(),
    val buildingId: String? = null,
    val floorId: String? = null,
    val unitId: String? = null,
    val title: String,
    val description: String = "",
    val date: String,
    val status: String = "Open", // Open, In Progress, Completed, Cancelled
    val estimatedCost: Double = 0.0,
    val actualCost: Double = 0.0,
    val currency: String = "USD",
    val notes: String = ""
)

@Entity(tableName = "photos", indices = [Index("ownerId")])
data class Photo(
    @PrimaryKey val id: String = newId(),
    val ownerType: String, // buildings, units, tenants, maintenance, expenses
    val ownerId: String,
    val path: String, // relative to app files dir, e.g. photos/units/<id>.jpg
    val date: String,
    val description: String = ""
)

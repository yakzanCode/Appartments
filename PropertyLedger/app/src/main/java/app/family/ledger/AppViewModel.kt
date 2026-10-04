package app.family.ledger

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import app.family.ledger.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import java.time.LocalDate
import java.time.YearMonth

@Suppress("UNCHECKED_CAST")
class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val ctx: Context get() = getApplication<Application>()
    val db = AppDatabase.get(app)
    private val dao = db.dao()

    val snap: StateFlow<Snapshot?> = combine(
        listOf<Flow<Any>>(
            dao.buildings(), dao.floors(), dao.units(), dao.people(), dao.tenants(), dao.residents(),
            dao.obligations(), dao.payments(), dao.allocations(), dao.debts(), dao.handovers(),
            dao.expenses(), dao.maintenance(), dao.photos(), dao.options(),
            dao.settings().map { l -> l.associate { it.key to it.value } }
        )
    ) { a ->
        Snapshot(
            a[0] as List<Building>, a[1] as List<Floor>, a[2] as List<PropertyUnit>, a[3] as List<Person>,
            a[4] as List<Tenant>, a[5] as List<Resident>, a[6] as List<RentObligation>, a[7] as List<Payment>,
            a[8] as List<PaymentAllocation>, a[9] as List<Debt>, a[10] as List<Handover>, a[11] as List<Expense>,
            a[12] as List<Maintenance>, a[13] as List<Photo>, a[14] as List<OptionItem>, a[15] as Map<String, String>
        )
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.Eagerly, null)

    // ---- optional local PIN (kept in private preferences, never in the backup, never sent anywhere) ----
    private val prefs = ctx.getSharedPreferences("lock", Context.MODE_PRIVATE)
    val pinEnabled = MutableStateFlow(prefs.contains("hash"))
    val unlocked = MutableStateFlow(!prefs.contains("hash"))

    private fun hash(pin: String, salt: String): String =
        MessageDigest.getInstance("SHA-256").digest((salt + pin).toByteArray()).joinToString("") { "%02x".format(it) }

    fun setPin(pin: String?) {
        if (pin.isNullOrEmpty()) {
            prefs.edit().remove("hash").remove("salt").apply()
            pinEnabled.value = false
        } else {
            val salt = newId()
            prefs.edit().putString("salt", salt).putString("hash", hash(pin, salt)).apply()
            pinEnabled.value = true
        }
    }

    fun tryUnlock(pin: String): Boolean {
        val ok = hash(pin, prefs.getString("salt", "") ?: "") == prefs.getString("hash", "")
        if (ok) unlocked.value = true
        return ok
    }

    fun lock() { if (pinEnabled.value) unlocked.value = false }

    init {
        viewModelScope.launch(Dispatchers.IO) {
            Seed.runIfNeeded(dao)
            ensureObligations()
        }
    }

    private fun run(block: suspend () -> Unit) { viewModelScope.launch(Dispatchers.IO) { block() } }

    // ---- simple saves ----
    fun setSetting(k: String, v: String) = run { dao.upSetting(Setting(k, v)) }
    fun addOption(kind: String, value: String) = run { dao.upOption(OptionItem(kind = kind, value = value.trim())) }
    fun save(x: Person) = run { dao.upPerson(x) }
    fun save(x: Building) = run { dao.upBuilding(x) }
    fun save(x: Floor) = run { dao.upFloor(x) }
    fun save(x: PropertyUnit) = run { dao.upUnit(x) }
    /** Saves a unit and (in order) its first tenant, so the foreign key is always satisfied. */
    fun saveUnit(u: PropertyUnit, firstTenant: Tenant?) = run { dao.upUnit(u); if (firstTenant != null) { dao.upTenant(firstTenant); ensureObligations() } }
    fun save(x: Tenant) = run { dao.upTenant(x); ensureObligations() }
    fun save(x: Resident) = run { dao.upResident(x) }
    fun delete(x: Resident) = run { dao.delResident(x) }
    fun save(x: Debt) = run { dao.upDebt(x) }
    fun save(x: Handover) = run { dao.upHandover(x) }
    fun save(x: Expense) = run { dao.upExpense(x) }
    fun save(x: Maintenance) = run { dao.upMaintenance(x) }

    fun refreshObligations() = run { ensureObligations() }

    /** Creates any missing monthly rent obligations from each active tenancy's start date up to the current month. */
    private suspend fun ensureObligations() {
        val today = LocalDate.now()
        val units = dao.unitsOnce().associateBy { it.id }
        val existing = dao.obligationsOnce().map { Triple(it.tenantId, it.year, it.month) }.toHashSet()
        for (t in dao.tenantsOnce()) {
            if (!t.active || t.archived || t.monthlyRent <= 0.0) continue
            val start = runCatching { LocalDate.parse(t.startDate) }.getOrNull() ?: today
            val end = runCatching { LocalDate.parse(t.endDate) }.getOrNull()
            val floor = today.minusYears(10)
            var ym = YearMonth.from(if (start.isAfter(floor)) start else floor)
            val last = YearMonth.from(if (end != null && end.isBefore(today)) end else today)
            while (!ym.isAfter(last)) {
                if (existing.add(Triple(t.id, ym.year, ym.monthValue))) {
                    dao.upObligation(
                        RentObligation(
                            tenantId = t.id, unitId = t.unitId, year = ym.year, month = ym.monthValue,
                            expected = t.monthlyRent, currency = t.currency,
                            dueDate = Dates.due(ym.year, ym.monthValue, t.dueDay),
                            collectedForId = units[t.unitId]?.defaultCollectedForId
                        )
                    )
                }
                ym = ym.plusMonths(1)
            }
        }
    }

    /**
     * Stores one payment (never edited afterwards) and allocates it to open items.
     * A chosen rent month / debt is paid first; the rest goes to the oldest outstanding balance.
     */
    fun recordPayment(
        tenantId: String, amount: Double, currency: String, date: String, notes: String,
        collectedForId: String?, targetObligationId: String?, targetDebtId: String?
    ) = run {
        db.withTransaction {
            val tenant = dao.tenantsOnce().first { it.id == tenantId }
            val p = Payment(
                tenantId = tenantId, unitId = tenant.unitId, amount = amount, currency = currency,
                date = date, notes = notes, collectedForId = collectedForId
            )
            dao.upPayment(p)
            val allocs = dao.allocationsOnce()
            val paidO = HashMap<String, Double>(); val paidD = HashMap<String, Double>()
            allocs.forEach { a ->
                a.obligationId?.let { paidO[it] = (paidO[it] ?: 0.0) + a.amount }
                a.debtId?.let { paidD[it] = (paidD[it] ?: 0.0) + a.amount }
            }
            val open = ArrayList<OpenItem>()
            dao.obligationsOnce().filter { it.tenantId == tenantId && it.currency == currency }.forEach {
                open.add(OpenItem(true, it.id, it.dueDate, it.expected - (paidO[it.id] ?: 0.0)))
            }
            dao.debtsOnce().filter { it.tenantId == tenantId && it.currency == currency }.forEach {
                open.add(OpenItem(false, it.id, it.date, it.amount - (paidD[it.id] ?: 0.0)))
            }
            for (a in planAllocation(open, amount, targetObligationId ?: targetDebtId)) {
                dao.upAllocation(
                    PaymentAllocation(
                        paymentId = p.id, obligationId = if (a.isObligation) a.id else null,
                        debtId = if (!a.isObligation) a.id else null, amount = a.amount
                    )
                )
            }
        }
    }

    /** Reversal instead of deletion: the payment stays in history marked as voided. */
    fun voidPayment(p: Payment) = run {
        db.withTransaction { dao.upPayment(p.copy(voided = true)); dao.deleteAllocationsOf(p.id) }
    }

    // ---- photos ----
    fun addPhoto(uri: Uri, ownerType: String, ownerId: String, asTenantAvatar: Boolean = false) = run {
        val rel = PhotoStore.save(ctx, uri, ownerType) ?: return@run
        dao.upPhoto(Photo(ownerType = ownerType, ownerId = ownerId, path = rel, date = Dates.today()))
        if (asTenantAvatar) {
            val t = dao.tenantsOnce().firstOrNull { it.id == ownerId } ?: return@run
            if (t.photoPath.isNotBlank()) {
                dao.photoByPath(t.photoPath)?.let { dao.delPhoto(it) }
                PhotoStore.delete(ctx, t.photoPath)
            }
            dao.upTenant(t.copy(photoPath = rel))
        }
    }

    fun deletePhoto(p: Photo) = run {
        dao.delPhoto(p)
        PhotoStore.delete(ctx, p.path)
        if (p.ownerType == "tenants") {
            dao.tenantsOnce().firstOrNull { it.id == p.ownerId && it.photoPath == p.path }?.let { dao.upTenant(it.copy(photoPath = "")) }
        }
    }

    // ---- backup / restore ----
    suspend fun makeExport(kind: String): File = withContext(Dispatchers.IO) {
        when (kind) {
            "data" -> Backup.exportZip(ctx, db, false)
            "full" -> Backup.exportZip(ctx, db, true)
            else -> Backup.exportCsvZip(ctx, db)
        }
    }

    suspend fun inspectBackup(uri: Uri): BackupInfo = withContext(Dispatchers.IO) { Backup.inspect(ctx, uri) }

    suspend fun restore(uri: Uri) = withContext(Dispatchers.IO) { Backup.restoreReplace(ctx, db, uri) }

    fun photoCount(): Int = snap.value?.photos?.size ?: 0
}

package app.family.ledger.data

import java.time.LocalDate
import java.time.YearMonth
import java.util.Locale

typealias Money = Map<String, Double>

val CURRENCIES = listOf("USD", "EUR", "LBP")

object Dates {
    fun today(): String = LocalDate.now().toString()
    fun ym(date: String): String = date.take(7)
    fun monthStart(y: Int, m: Int): String = LocalDate.of(y, m, 1).toString()
    fun monthEnd(y: Int, m: Int): String = YearMonth.of(y, m).atEndOfMonth().toString()
    fun due(y: Int, m: Int, dueDay: Int): String {
        val last = YearMonth.of(y, m).lengthOfMonth()
        return LocalDate.of(y, m, dueDay.coerceIn(1, last)).toString()
    }
}

/** Inclusive date range as ISO strings; null bounds mean open-ended. */
data class Period(val from: String?, val to: String?) {
    fun contains(d: String) = (from == null || d >= from) && (to == null || d <= to)
}

fun periodOf(key: String): Period {
    val now = LocalDate.now()
    return when (key) {
        "This month" -> Period(Dates.monthStart(now.year, now.monthValue), Dates.monthEnd(now.year, now.monthValue))
        "Last month" -> now.minusMonths(1).let { Period(Dates.monthStart(it.year, it.monthValue), Dates.monthEnd(it.year, it.monthValue)) }
        "This year" -> Period("${now.year}-01-01", "${now.year}-12-31")
        "Last year" -> Period("${now.year - 1}-01-01", "${now.year - 1}-12-31")
        else -> Period(null, null)
    }
}

fun fmtNum(a: Double): String {
    val s = if (a % 1.0 == 0.0) String.format(Locale.US, "%,.0f", a) else String.format(Locale.US, "%,.2f", a)
    return s
}

fun fmtMoney(a: Double, cur: String): String = when (cur) {
    "USD" -> "$" + fmtNum(a)
    "EUR" -> "€" + fmtNum(a)
    else -> fmtNum(a) + " " + cur
}

fun <T> Iterable<T>.sumMoney(cur: (T) -> String, amt: (T) -> Double): Money {
    val m = HashMap<String, Double>()
    for (x in this) m[cur(x)] = (m[cur(x)] ?: 0.0) + amt(x)
    return m
}

operator fun Money.plus(o: Money): Money {
    val m = HashMap(this)
    for ((k, v) in o) m[k] = (m[k] ?: 0.0) + v
    return m
}

operator fun Money.minus(o: Money): Money {
    val m = HashMap(this)
    for ((k, v) in o) m[k] = (m[k] ?: 0.0) - v
    return m
}

fun Money.nonZero(): Money = filterValues { kotlin.math.abs(it) > 0.004 }

class Snapshot(
    val buildings: List<Building>,
    val floors: List<Floor>,
    val units: List<PropertyUnit>,
    val people: List<Person>,
    val tenants: List<Tenant>,
    val residents: List<Resident>,
    val obligations: List<RentObligation>,
    val payments: List<Payment>,
    val allocations: List<PaymentAllocation>,
    val debts: List<Debt>,
    val handovers: List<Handover>,
    val expenses: List<Expense>,
    val maintenance: List<Maintenance>,
    val photos: List<Photo>,
    val options: List<OptionItem>,
    val settings: Map<String, String>
) {
    val today: String = Dates.today()

    val buildingById by lazy { buildings.associateBy { it.id } }
    val floorById by lazy { floors.associateBy { it.id } }
    val unitById by lazy { units.associateBy { it.id } }
    val personById by lazy { people.associateBy { it.id } }
    val tenantById by lazy { tenants.associateBy { it.id } }
    val tenantsByUnit by lazy { tenants.groupBy { it.unitId } }
    val activeTenants by lazy { tenants.filter { it.active && !it.archived } }
    fun currentTenant(unitId: String): Tenant? =
        tenantsByUnit[unitId]?.filter { it.active && !it.archived }?.maxByOrNull { it.startDate }

    val livePayments by lazy { payments.filter { !it.voided } }

    private val paidByObligation by lazy {
        allocations.filter { it.obligationId != null }.groupBy { it.obligationId!! }.mapValues { e -> e.value.sumOf { it.amount } }
    }
    private val paidByDebt by lazy {
        allocations.filter { it.debtId != null }.groupBy { it.debtId!! }.mapValues { e -> e.value.sumOf { it.amount } }
    }
    private val allocatedByPayment by lazy { allocations.groupBy { it.paymentId }.mapValues { e -> e.value.sumOf { it.amount } } }

    fun paid(o: RentObligation) = paidByObligation[o.id] ?: 0.0
    fun remaining(o: RentObligation) = (o.expected - paid(o)).coerceAtLeast(0.0)
    fun paid(d: Debt) = paidByDebt[d.id] ?: 0.0
    fun remaining(d: Debt) = (d.amount - paid(d)).coerceAtLeast(0.0)
    fun unallocated(p: Payment) = if (p.voided) 0.0 else (p.amount - (allocatedByPayment[p.id] ?: 0.0)).coerceAtLeast(0.0)

    fun status(o: RentObligation): String = when {
        remaining(o) <= 0.004 -> "Paid"
        paid(o) > 0.004 -> "Partially paid"
        o.dueDate < today -> "Overdue"
        else -> "Unpaid"
    }

    fun debtStatus(d: Debt): String = when {
        remaining(d) <= 0.004 -> "Paid"
        paid(d) > 0.004 -> "Partially paid"
        else -> "Unpaid"
    }

    // ---- balances ----
    fun rentOwed(tenantId: String): Money =
        obligations.filter { it.tenantId == tenantId }.filter { remaining(it) > 0.004 }
            .sumMoney({ it.currency }, { remaining(it) })

    fun debtOwed(tenantId: String): Money =
        debts.filter { it.tenantId == tenantId }.filter { remaining(it) > 0.004 }
            .sumMoney({ it.currency }, { remaining(it) })

    fun balance(tenantId: String): Money = (rentOwed(tenantId) + debtOwed(tenantId)).nonZero()

    fun unitLabel(u: PropertyUnit): String {
        val b = buildingById[u.buildingId]?.name ?: ""
        val f = floorById[u.floorId]?.name ?: ""
        return listOf(b, f, u.name).filter { it.isNotBlank() }.joinToString(" · ")
    }

    fun tenantLabel(t: Tenant): String {
        val u = unitById[t.unitId]
        return if (u == null) t.name else "${t.name} — ${u.name}"
    }

    fun personName(id: String?): String? = id?.let { personById[it]?.name }

    fun setting(key: String, def: String) = settings[key] ?: def

    // ---- reports ----
    inner class Report(val period: Period) {
        private fun inP(d: String) = period.contains(d)
        val rentDueOwn: Money = obligations.filter { it.collectedForId == null && inP(it.dueDate) }.sumMoney({ it.currency }, { it.expected })
        val rentPaidAgainstOwn: Money = obligations.filter { it.collectedForId == null && inP(it.dueDate) }.sumMoney({ it.currency }, { paid(it) })
        val rentUnpaidOwn: Money = obligations.filter { it.collectedForId == null && inP(it.dueDate) }.sumMoney({ it.currency }, { remaining(it) })
        val countPaid = obligations.count { inP(it.dueDate) && status(it) == "Paid" }
        val countPartial = obligations.count { inP(it.dueDate) && status(it) == "Partially paid" }
        val countUnpaid = obligations.count { inP(it.dueDate) && status(it) == "Unpaid" }
        val countOverdue = obligations.count { inP(it.dueDate) && status(it) == "Overdue" }
        val collectedOwn: Money = livePayments.filter { it.collectedForId == null && inP(it.date) }.sumMoney({ it.currency }, { it.amount })
        val collectedForOthers: Money = livePayments.filter { it.collectedForId != null && inP(it.date) }.sumMoney({ it.currency }, { it.amount })
        val expensesTotal: Money = expenses.filter { inP(it.date) }.sumMoney({ it.currency }, { it.amount })
        val expensesByCategory: Map<String, Money> =
            expenses.filter { inP(it.date) }.groupBy { it.category }.mapValues { e -> e.value.sumMoney({ it.currency }, { it.amount }) }
        val maintenanceExpenses: Money = expenses.filter { inP(it.date) && it.category in setOf("Repair", "Maintenance", "Materials", "Contractor") }
            .sumMoney({ it.currency }, { it.amount })
        // Own income minus own expenses. Money collected for other people is NOT family income.
        val net: Money = (collectedOwn - expensesTotal).nonZero()
        val debtsCreated: Money = debts.filter { inP(it.date) }.sumMoney({ it.currency }, { it.amount })
        val debtPayments: Money = allocations.filter { a -> a.debtId != null && payments.firstOrNull { it.id == a.paymentId }?.let { !it.voided && inP(it.date) } == true }
            .sumMoney({ a -> debtById[a.debtId!!]?.currency ?: "USD" }, { it.amount })
    }

    val debtById by lazy { debts.associateBy { it.id } }

    // ---- totals not tied to a period ----
    val oldDebtsRemaining: Money by lazy { debts.filter { remaining(it) > 0.004 && it.type == "Old Rent Debt" }.sumMoney({ it.currency }, { remaining(it) }) }
    val otherChargesRemaining: Money by lazy { debts.filter { remaining(it) > 0.004 && it.type != "Old Rent Debt" }.sumMoney({ it.currency }, { remaining(it) }) }
    val rentArrearsRemaining: Money by lazy { obligations.filter { remaining(it) > 0.004 && it.dueDate < today }.sumMoney({ it.currency }, { remaining(it) }) }

    fun collectedFor(personId: String): Money = livePayments.filter { it.collectedForId == personId }.sumMoney({ it.currency }, { it.amount })
    fun handedOver(personId: String): Money = handovers.filter { it.personId == personId }.sumMoney({ it.currency }, { it.amount })
    fun toHandOver(personId: String): Money = (collectedFor(personId) - handedOver(personId)).nonZero()
}

// ---- payment allocation (pure, unit-tested) ----
data class OpenItem(val isObligation: Boolean, val id: String, val date: String, val remaining: Double)
data class Alloc(val isObligation: Boolean, val id: String, val amount: Double)

/** The chosen item is paid first; the rest goes to the oldest outstanding balance. Any leftover stays as credit. */
fun planAllocation(open: List<OpenItem>, amount: Double, targetId: String?): List<Alloc> {
    val sorted = open.filter { it.remaining > 0.004 }.sortedBy { it.date }
    val ordered = sorted.filter { it.id == targetId } + sorted.filter { it.id != targetId }
    var left = amount
    val out = ArrayList<Alloc>()
    for (item in ordered) {
        if (left <= 0.004) break
        val a = minOf(left, item.remaining)
        out.add(Alloc(item.isObligation, item.id, a))
        left -= a
    }
    return out
}

package app.family.ledger.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.family.ledger.AppViewModel
import app.family.ledger.data.*

// =============================================================== RENT

@Composable
fun RentScreen(vm: AppViewModel, s: Snapshot, nav: Nav) {
    val now = remember { java.time.LocalDate.now() }
    var y by remember { mutableIntStateOf(now.year) }
    var m by remember { mutableIntStateOf(now.monthValue) }
    var pay by remember { mutableStateOf<RentObligation?>(null) }
    val lang = LocalLang.current
    val list = s.obligations.filter { it.year == y && it.month == m }
        .sortedBy { s.unitById[it.unitId]?.let { u -> (s.buildingById[u.buildingId]?.sortOrder ?: 0) * 1000 + (u.number.toIntOrNull() ?: 0) } ?: 0 }
    val due = list.sumMoney({ it.currency }, { it.expected })
    val paid = list.sumMoney({ it.currency }, { s.paid(it) })
    val noRent = s.activeTenants.count { it.monthlyRent <= 0.0 && s.unitById[it.unitId]?.status == "Rented" }
    fun shift(d: Int) { val ym = java.time.YearMonth.of(y, m).plusMonths(d.toLong()); y = ym.year; m = ym.monthValue }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton({ shift(-1) }, Modifier.size(56.dp), contentPadding = PaddingValues(0.dp)) { Text(if (lang == "ar") "▶" else "◀", fontSize = 20.sp) }
                Text("${monthName(m)} $y", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                OutlinedButton({ shift(1) }, Modifier.size(56.dp), contentPadding = PaddingValues(0.dp)) { Text(if (lang == "ar") "◀" else "▶", fontSize = 20.sp) }
            }
        }
        item {
            CardBox {
                RowKV(t("Rent this month")) { MoneyText(due, size = 18) }
                RowKV(t("Collected")) { MoneyText(paid, GreenC(), 18) }
                RowKV(t("Not paid yet")) { MoneyText(due - paid, RedC(), 18) }
            }
            if (noRent > 0) Sub("⚠️ " + t("Some tenants have no rent amount yet. Open the tenant and set the monthly rent."))
            BigButton("➕ " + t("Add Rent Payment"), { pay = RentObligation(tenantId = "", unitId = "", year = y, month = m, expected = 0.0, currency = "", dueDate = "") })
        }
        if (list.isEmpty()) item { Sub(t("No rent entries for this month.")) }
        items(list, key = { it.id }) { o ->
            val tn = s.tenantById[o.tenantId]; val u = s.unitById[o.unitId]
            val left = s.remaining(o)
            CardBox({ tn?.let { nav.push(Route.TenantR(it.id)) } }) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("${u?.let { s.buildingById[it.buildingId]?.name } ?: ""} · ${u?.name ?: ""}", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Tag(s.status(o))
                }
                Sub(tn?.name ?: "")
                o.collectedForId?.let { Text("↗ " + t("for") + " " + (s.personName(it) ?: ""), color = AmberC(), fontWeight = FontWeight.Bold) }
                LinearProgressIndicator(progress = { (s.paid(o) / o.expected).toFloat().coerceIn(0f, 1f) }, Modifier.fillMaxWidth().height(10.dp), color = GreenC())
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("${t("Paid")} ${fmtMoney(s.paid(o), o.currency)} ${t("of")} ${fmtMoney(o.expected, o.currency)}")
                    if (left > 0.004) Text(t("Left") + " " + fmtMoney(left, o.currency), color = RedC(), fontWeight = FontWeight.Bold)
                }
                if (left > 0.004) BigButton(t("Add payment"), { pay = o })
            }
        }
    }
    pay?.let { o -> PaymentDialog(vm, s, { pay = null }, tenantId0 = o.tenantId.ifBlank { null }, obligationId0 = if (o.tenantId.isBlank()) null else o.id) }
}

// =============================================================== DEBTS

@Composable
fun DebtsScreen(vm: AppViewModel, s: Snapshot, nav: Nav) {
    var add by remember { mutableStateOf(false) }
    val rows = s.tenants.map { it to s.balance(it.id) }.filter { it.second.isNotEmpty() }.sortedByDescending { it.second.values.sum() }
    Page {
        CardBox {
            RowKV(t("Old debts")) { MoneyText(s.oldDebtsRemaining, RedC(), 18) }
            RowKV(t("Overdue rent")) { MoneyText(s.rentArrearsRemaining, RedC(), 18) }
            RowKV(t("Other charges")) { MoneyText(s.otherChargesRemaining, RedC(), 18) }
        }
        BigButton("➕ " + t("Add Debt"), { add = true })
        if (rows.isEmpty()) Sub(t("No debts. Everything is paid."))
        rows.forEach { (tn, bal) ->
            CardBox({ nav.push(Route.TenantR(tn.id)) }) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Avatar(tn.photoPath, tn.name)
                        Column { Text(tn.name, fontWeight = FontWeight.Bold); Sub(s.unitById[tn.unitId]?.let { s.unitLabel(it) } ?: "") }
                    }
                    MoneyText(bal, RedC(), 18)
                }
                if (!tn.active) Sub(t("Former tenant"))
            }
        }
    }
    if (add) DebtDialog(vm, s, { add = false })
}

// =============================================================== COLLECTED FOR OTHERS

@Composable
fun OthersScreen(vm: AppViewModel, s: Snapshot, nav: Nav) {
    var hand by remember { mutableStateOf<String?>(null) }
    var editP by remember { mutableStateOf<Person?>(null) }
    var addP by remember { mutableStateOf(false) }
    Page {
        Sub(t("Money collected for other people is not counted as family income."))
        s.people.filter { !it.archived }.forEach { p ->
            val col = s.collectedFor(p.id); val hd = s.handedOver(p.id); val rem = s.toHandOver(p.id)
            CardBox {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(p.name, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    TextButton({ editP = p }) { Text("✏️") }
                }
                RowKV(t("Collected")) { MoneyText(col, size = 18) }
                RowKV(t("Handed over")) { MoneyText(hd, size = 18) }
                RowKV(t("Remaining")) { MoneyText(rem, AmberC(), 20) }
                BigButton("↗ " + t("Record Handover"), { hand = p.id })
                var open by remember { mutableStateOf(false) }
                TextButton({ open = !open }) { Text(t(if (open) "Hide history" else "Show history"), fontSize = 17.sp) }
                if (open) {
                    val items = s.livePayments.filter { it.collectedForId == p.id }.map { Triple(it.date, "+ " + fmtMoney(it.amount, it.currency), (s.tenantById[it.tenantId]?.let { tn -> s.unitById[tn.unitId]?.name } ?: "")) } +
                        s.handovers.filter { it.personId == p.id }.map { Triple(it.date, "− " + fmtMoney(it.amount, it.currency), t("Handed over") + " " + it.notes) }
                    items.sortedByDescending { it.first }.take(40).forEach { (d, a, n) ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Sub("$d  $n", Modifier.weight(1f)); Text(a, fontWeight = FontWeight.Bold) }
                    }
                }
            }
        }
        BigButton("➕ " + t("Add person"), { addP = true }, outlined = true)
    }
    hand?.let { HandoverDialog(vm, s, { hand = null }, personId0 = it) }
    editP?.let { PersonDialog(vm, it, { editP = null }) }
    if (addP) PersonDialog(vm, null, { addP = false })
}

// =============================================================== MAINTENANCE

@Composable
fun MaintenanceScreen(vm: AppViewModel, s: Snapshot, nav: Nav) {
    var filter by remember { mutableStateOf("Open") }
    var add by remember { mutableStateOf(false) }
    val list = s.maintenance.filter { filter == "All" || it.status == filter }
    Page {
        BigButton("➕ " + t("Add issue"), { add = true })
        ChipRow(listOf("All", "Open", "In Progress", "Completed", "Cancelled").map { it to t(it) }, filter) { filter = it }
        if (list.isEmpty()) Sub(t("Nothing here."))
        list.forEach { mm ->
            CardBox({ nav.push(Route.IssueR(mm.id)) }) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(mm.title, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f)); Tag(mm.status)
                }
                Sub(s.unitById[mm.unitId]?.let { s.unitLabel(it) } ?: "")
                Sub(mm.date + (if (mm.estimatedCost > 0) "  ·  " + t("Estimated cost") + " " + fmtMoney(mm.estimatedCost, mm.currency) else ""))
                val n = s.photos.count { it.ownerType == "maintenance" && it.ownerId == mm.id }
                if (n > 0) Sub("📷 $n")
            }
        }
    }
    if (add) MaintenanceDialog(vm, s, null, { add = false }) { nav.push(Route.IssueR(it.id)) }
}

@Composable
fun IssueScreen(vm: AppViewModel, s: Snapshot, nav: Nav, id: String) {
    val mm = s.maintenance.firstOrNull { it.id == id } ?: return
    var edit by remember { mutableStateOf(false) }
    Page {
        CardBox {
            Text(mm.title, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Sub(s.unitById[mm.unitId]?.let { s.unitLabel(it) } ?: "")
            Sub(mm.date)
            if (mm.description.isNotBlank()) Text(mm.description)
            ChipRow(MAINT_STATUSES.map { it to t(it) }, mm.status) { vm.save(mm.copy(status = it)) }
            RowKV(t("Estimated cost"), fmtMoney(mm.estimatedCost, mm.currency))
            RowKV(t("Actual cost"), fmtMoney(mm.actualCost, mm.currency))
            if (mm.notes.isNotBlank()) Sub(mm.notes)
            BigButton("✏️ " + t("Edit"), { edit = true }, outlined = true)
        }
        H2(t("Photos of the problem"))
        PhotoSection(vm, s, "maintenance", id)
    }
    if (edit) MaintenanceDialog(vm, s, mm, { edit = false })
}

// =============================================================== EXPENSES

@Composable
fun ExpensesScreen(vm: AppViewModel, s: Snapshot, nav: Nav) {
    var add by remember { mutableStateOf(false) }
    var edit by remember { mutableStateOf<Expense?>(null) }
    var key by remember { mutableStateOf("This month") }
    val p = periodOf(key)
    val list = s.expenses.filter { p.contains(it.date) }
    Page {
        BigButton("➕ " + t("Add expense"), { add = true })
        ChipRow(PERIODS.map { it to t(it) }, key) { key = it }
        CardBox { RowKV(t("Total")) { MoneyText(list.sumMoney({ it.currency }, { it.amount }), size = 20) } }
        list.forEach { e ->
            CardBox({ edit = e }) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(t(e.category), fontWeight = FontWeight.Bold); Text(fmtMoney(e.amount, e.currency), fontWeight = FontWeight.Bold)
                }
                Sub(e.date + "  " + e.description)
                Sub(listOfNotNull(s.buildingById[e.buildingId]?.name, s.unitById[e.unitId]?.name).joinToString(" · "))
            }
        }
    }
    if (add) ExpenseDialog(vm, s, null, { add = false })
    edit?.let { ExpenseDialog(vm, s, it, { edit = null }) }
}

val PERIODS = listOf("This month", "Last month", "This year", "Last year", "All time")

// =============================================================== REPORTS

@Composable
fun ReportsScreen(vm: AppViewModel, s: Snapshot, nav: Nav) {
    var key by remember { mutableStateOf("This month") }
    var from by remember { mutableStateOf(Dates.today().take(4) + "-01-01") }
    var to by remember { mutableStateOf(Dates.today()) }
    val period = if (key == "Custom range") Period(from, to) else periodOf(key)
    val r = remember(s, period) { s.Report(period) }
    Page {
        ChipRow((PERIODS + "Custom range").map { it to t(it) }, key) { key = it }
        if (key == "Custom range") { DateField(t("From"), from) { from = it }; DateField(t("To"), to) { to = it } }
        CardBox {
            Text(t("Rent"), fontWeight = FontWeight.Bold, fontSize = 20.sp)
            RowKV(t("Rent due")) { MoneyText(r.rentDueOwn, size = 18) }
            RowKV(t("Collected")) { MoneyText(r.collectedOwn, GreenC(), 18) }
            RowKV(t("Not paid yet")) { MoneyText(r.rentUnpaidOwn, RedC(), 18) }
            RowKV(t("Paid"), "${r.countPaid}"); RowKV(t("Partially paid"), "${r.countPartial}")
            RowKV(t("Unpaid"), "${r.countUnpaid}"); RowKV(t("Overdue"), "${r.countOverdue}")
        }
        CardBox {
            Text(t("Debts and charges"), fontWeight = FontWeight.Bold, fontSize = 20.sp)
            RowKV(t("Added in this period")) { MoneyText(r.debtsCreated, size = 18) }
            RowKV(t("Paid against debts")) { MoneyText(r.debtPayments, GreenC(), 18) }
            RowKV(t("Old debts")) { MoneyText(s.oldDebtsRemaining, RedC(), 18) }
            RowKV(t("Overdue rent")) { MoneyText(s.rentArrearsRemaining, RedC(), 18) }
            RowKV(t("Other charges")) { MoneyText(s.otherChargesRemaining, RedC(), 18) }
        }
        CardBox {
            Text(t("Expenses"), fontWeight = FontWeight.Bold, fontSize = 20.sp)
            r.expensesByCategory.entries.sortedBy { it.key }.forEach { (c, m) -> RowKV(t(c)) { MoneyText(m, size = 17) } }
            RowKV(t("Total")) { MoneyText(r.expensesTotal, size = 19) }
        }
        CardBox {
            Text(t("Net income"), fontWeight = FontWeight.Bold, fontSize = 20.sp)
            MoneyText(r.net, GreenC(), 24)
            Sub(t("Own rent collected minus own expenses."))
            RowKV(t("Collected for others (not income)")) { MoneyText(r.collectedForOthers, AmberC(), 17) }
        }
    }
}

// =============================================================== BUILDINGS / FLOORS

@Composable
fun BuildingsScreen(vm: AppViewModel, s: Snapshot, nav: Nav) {
    var add by remember { mutableStateOf(false) }
    Page {
        Sub(t("Buildings, floors and all units, including family units. Everything can be edited."))
        s.buildings.filter { !it.archived }.forEach { b ->
            val us = s.units.filter { it.buildingId == b.id && !it.archived }
            CardBox({ nav.push(Route.BuildingR(b.id)) }) {
                Text("${b.number} · ${b.name}", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Sub("${s.floors.count { it.buildingId == b.id && !it.archived }} ${t("Floors")} · ${us.size} ${t("Units")}")
                if (b.address.isNotBlank()) Sub(b.address)
            }
        }
        BigButton("➕ " + t("Add Building"), { add = true })
        BigButton("🗺️ " + t("Floor Plan"), { nav.push(Route.FloorPlan) }, outlined = true)
    }
    if (add) BuildingDialog(vm, null) { add = false }
}

@Composable
fun BuildingScreen(vm: AppViewModel, s: Snapshot, nav: Nav, id: String) {
    val b = s.buildingById[id] ?: return
    var edit by remember { mutableStateOf(false) }
    var addFloor by remember { mutableStateOf(false) }
    var editFloor by remember { mutableStateOf<Floor?>(null) }
    var addUnit by remember { mutableStateOf<Floor?>(null) }
    val us = s.units.filter { it.buildingId == id && !it.archived }
    val ts = us.mapNotNull { s.currentTenant(it.id) }
    val rentMonthly = ts.filter { us.any { u -> u.id == it.unitId && u.status == "Rented" } }.sumMoney({ it.currency }, { it.monthlyRent })
    val owed = ts.fold(emptyMap<String, Double>()) { a, t2 -> a + s.balance(t2.id) }.nonZero()
    val exp = s.expenses.filter { it.buildingId == id }.sumMoney({ it.currency }, { it.amount })
    val income = s.livePayments.filter { p -> p.collectedForId == null && s.unitById[p.unitId]?.buildingId == id }.sumMoney({ it.currency }, { it.amount })
    Page {
        CardBox {
            Text("${b.number} · ${b.name}", fontSize = 22.sp, fontWeight = FontWeight.Bold)
            if (b.address.isNotBlank()) Sub(b.address)
            if (b.notes.isNotBlank()) Sub(b.notes)
            RowKV(t("Floors"), "${s.floors.count { it.buildingId == id && !it.archived }}")
            RowKV(t("Units"), "${us.size}")
            RowKV(t("Rented"), "${us.count { it.status == "Rented" }}"); RowKV(t("Vacant"), "${us.count { it.status == "Vacant" }}")
            RowKV(t("Family"), "${us.count { it.status == "Family" }}"); RowKV(t("Not ours"), "${us.count { it.status == "Not ours" }}")
            RowKV(t("Monthly rent")) { MoneyText(rentMonthly, size = 18) }
            RowKV(t("Balance")) { MoneyText(owed, RedC(), 18) }
            RowKV(t("Expenses")) { MoneyText(exp, size = 18) }
            RowKV(t("Net income")) { MoneyText(income - exp, GreenC(), 18) }
            BigButton("✏️ " + t("Edit"), { edit = true }, outlined = true)
        }
        s.floors.filter { it.buildingId == id && !it.archived }.sortedBy { it.sortOrder }.forEach { f ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                H2("${f.name}  (${t(f.ownership)})")
                TextButton({ editFloor = f }) { Text("✏️") }
            }
            us.filter { it.floorId == f.id }.forEach { u ->
                CardBox({ nav.push(Route.UnitR(u.id)) }) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(u.name, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f)); Tag(u.status)
                    }
                    Sub(t(u.type) + " · " + t(u.ownership))
                }
            }
            OutlinedButton({ addUnit = f }, Modifier.fillMaxWidth()) { Text("➕ " + t("Add unit"), fontSize = 17.sp) }
        }
        BigButton("➕ " + t("Add floor"), { addFloor = true }, outlined = true)
        H2(t("Photos"))
        PhotoSection(vm, s, "buildings", id)
    }
    if (edit) BuildingDialog(vm, b) { edit = false }
    if (addFloor) FloorDialog(vm, id, null) { addFloor = false }
    editFloor?.let { FloorDialog(vm, id, it) { editFloor = null } }
    addUnit?.let { UnitDialog(vm, s, null, { addUnit = null }, buildingId0 = id, floorId0 = it.id) }
}

// =============================================================== 2D FLOOR PLAN

@Composable
fun FloorPlanScreen(vm: AppViewModel, s: Snapshot, nav: Nav) {
    val blds = s.buildings.filter { !it.archived }
    var bId by remember { mutableStateOf(blds.firstOrNull()?.id) }
    val floors = s.floors.filter { it.buildingId == bId && !it.archived }.sortedBy { it.sortOrder }
    var fId by remember(bId) { mutableStateOf(floors.firstOrNull()?.id) }
    val us = s.units.filter { it.floorId == fId && !it.archived }
    Page {
        Picker(t("Building"), blds.map { it.id as String? to it.name }, bId, { bId = it })
        ChipRow(floors.map { it.id to it.name }, fId) { fId = it }
        if (us.isEmpty()) Sub(t("No units on this floor."))
        us.chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { u ->
                    val tn = s.currentTenant(u.id)
                    Card(
                        onClick = { nav.push(Route.UnitR(u.id)) },
                        modifier = Modifier.weight(1f).heightIn(min = 110.dp),
                        colors = CardDefaults.cardColors(containerColor = statusColor(u.status))
                    ) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(u.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            Text("${statusIcon(u.status)} ${t(u.status)}", color = Color.White, fontSize = 15.sp)
                            if (tn != null) Text(tn.name, color = Color.White, fontSize = 15.sp)
                        }
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
        Sub(t("Colors always come with a word and an icon."))
    }
}

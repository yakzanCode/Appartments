package app.family.ledger.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.family.ledger.AppViewModel
import app.family.ledger.data.*

sealed interface Route {
    data object Home : Route
    data object Rentals : Route
    data object Rent : Route
    data object Others : Route
    data object More : Route
    data object Debts : Route
    data object Maintenance : Route
    data object Expenses : Route
    data object Reports : Route
    data object Buildings : Route
    data object FloorPlan : Route
    data object Settings : Route
    data object Search : Route
    data object Tenants : Route
    data class UnitR(val id: String) : Route
    data class TenantR(val id: String) : Route
    data class BuildingR(val id: String) : Route
    data class IssueR(val id: String) : Route
}

class Nav(val stack: SnapshotStateList<Route>) {
    fun push(r: Route) { stack.add(r) }
    fun pop() { if (stack.size > 1) stack.removeAt(stack.lastIndex) }
}

val MONTHS = listOf("January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December")

@Composable fun monthName(m: Int) = t(MONTHS[(m - 1).coerceIn(0, 11)])

fun dial(ctx: android.content.Context, number: String) {
    if (number.isBlank()) return
    ctx.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + number.filter { it.isDigit() || it == '+' })))
}

@Composable
fun moneyLine(m: Money): String = m.nonZero().entries.sortedBy { it.key }.joinToString(" + ") { fmtMoney(it.value, it.key) }.ifBlank { "0" }

// =============================================================== HOME

@Composable
fun HomeScreen(vm: AppViewModel, s: Snapshot, nav: Nav) {
    val rep = remember(s) { s.Report(periodOf("This month")) }
    var dlg by remember { mutableStateOf("") }
    val units = s.units.filter { !it.archived && !(s.buildingById[it.buildingId]?.archived ?: false) }
    val rented = units.filter { it.status == "Rented" }
    val people = rented.sumOf { u -> s.currentTenant(u.id)?.residentsTotal ?: 0 }
    val openIssues = s.maintenance.count { it.status == "Open" || it.status == "In Progress" }
    val toHand: Money = s.people.fold(emptyMap<String, Double>()) { acc, p -> acc + s.toHandOver(p.id) }.nonZero()
    val olds = s.oldDebtsRemaining

    Page {
        StatGrid(listOf(
            { m -> StatCard(t("Rented units"), m) { Text("${rented.size}", fontSize = 28.sp, fontWeight = FontWeight.ExtraBold) } },
            { m -> StatCard(t("People living in them"), m) { Text("$people", fontSize = 28.sp, fontWeight = FontWeight.ExtraBold) } },
            { m -> StatCard(t("Vacant"), m) { Text("${units.count { it.status == "Vacant" }}", fontSize = 28.sp, fontWeight = FontWeight.ExtraBold) } },
            { m -> StatCard(t("Buildings"), m, { nav.push(Route.Buildings) }) { Text("${s.buildings.count { !it.archived }}", fontSize = 28.sp, fontWeight = FontWeight.ExtraBold) } },
            { m -> StatCard(t("Rent this month"), m) { MoneyText(rep.rentDueOwn) } },
            { m -> StatCard(t("Collected"), m) { MoneyText(rep.collectedOwn, GreenC()) } },
            { m -> StatCard(t("Not paid yet"), m) { MoneyText(rep.rentUnpaidOwn, RedC()) } },
            { m -> StatCard(t("Overdue rent"), m, { nav.push(Route.Debts) }) { MoneyText(s.rentArrearsRemaining, RedC()) } },
            { m -> StatCard(t("Old debts"), m, { nav.push(Route.Debts) }) { MoneyText(olds, RedC()) } },
            { m -> StatCard(t("Other charges"), m, { nav.push(Route.Debts) }) { MoneyText(s.otherChargesRemaining, RedC()) } },
            { m -> StatCard(t("Expenses this month"), m, { nav.push(Route.Expenses) }) { MoneyText(rep.expensesTotal) } },
            { m -> StatCard(t("Net income this month"), m) { MoneyText(rep.net, GreenC()) } },
            { m -> StatCard(t("To hand over"), m, { nav.push(Route.Others) }) { MoneyText(toHand, AmberC()) } },
            { m -> StatCard(t("Open maintenance"), m, { nav.push(Route.Maintenance) }) { Text("$openIssues", fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, color = AmberC()) } },
        ))
        H2(t("Quick Actions"))
        BigButton("➕ " + t("Add Rent Payment"), { dlg = "pay" })
        BigButton("➕ " + t("Add Debt"), { dlg = "debt" }, outlined = true)
        BigButton("➕ " + t("Add expense"), { dlg = "exp" }, outlined = true)
        BigButton("➕ " + t("Add issue"), { dlg = "maint" }, outlined = true)
        BigButton("↗ " + t("Record Handover"), { dlg = "hand" }, outlined = true)
        BigButton("➕ " + t("Add unit"), { dlg = "unit" }, outlined = true)
        Sub(t("Money collected for other people is not counted as family income."))
    }
    when (dlg) {
        "pay" -> PaymentDialog(vm, s, { dlg = "" })
        "debt" -> DebtDialog(vm, s, { dlg = "" })
        "exp" -> ExpenseDialog(vm, s, null, { dlg = "" })
        "maint" -> MaintenanceDialog(vm, s, null, { dlg = "" })
        "hand" -> HandoverDialog(vm, s, { dlg = "" })
        "unit" -> UnitDialog(vm, s, null, { dlg = "" })
    }
}

// =============================================================== RENTALS

@Composable
fun RentalsScreen(vm: AppViewModel, s: Snapshot, nav: Nav) {
    var showAll by remember { mutableStateOf(false) }
    var add by remember { mutableStateOf(false) }
    val today = remember { java.time.LocalDate.now() }
    val shown = s.units.filter { !it.archived && (showAll || it.status in setOf("Rented", "Vacant", "Maintenance")) }
    val byBuilding = s.buildings.filter { !it.archived }.map { b -> b to shown.filter { it.buildingId == b.id }.sortedWith(compareBy({ s.floorById[it.floorId]?.number ?: 0 }, { it.number.toIntOrNull() ?: 0 })) }
        .filter { it.second.isNotEmpty() }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            BigButton("➕ " + t("Add rental unit"), { add = true })
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(showAll, { showAll = it }); Spacer(Modifier.width(8.dp)); Text(t("Show family and other units"))
            }
        }
        byBuilding.forEach { (b, us) ->
            item(key = "h" + b.id) { H2(b.name) }
            items(us, key = { it.id }) { u ->
                val tn = s.currentTenant(u.id)
                val ob = tn?.let { tt -> s.obligations.firstOrNull { it.tenantId == tt.id && it.year == today.year && it.month == today.monthValue } }
                CardBox({ nav.push(Route.UnitR(u.id)) }) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("${s.floorById[u.floorId]?.name ?: ""} · ${u.name}", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        Tag(u.status)
                    }
                    if (tn != null) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Avatar(tn.photoPath, tn.name)
                            Column {
                                Text(tn.name, fontSize = 18.sp)
                                Sub(householdLine(tn))
                            }
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Sub(if (tn.monthlyRent > 0) t("Monthly") + " " + fmtMoney(tn.monthlyRent, tn.currency) else t("Rent not set"))
                            if (ob != null) Tag(s.status(ob))
                        }
                        val bal = s.balance(tn.id)
                        if (bal.isNotEmpty()) Text(t("Balance") + ": " + moneyLine(bal), color = RedC(), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
    if (add) UnitDialog(vm, s, null, { add = false })
}

@Composable
fun householdLine(tn: Tenant): String =
    if (tn.householdType == "Family")
        "${t("Family")} · ${tn.residentsTotal}"
    else "${t(tn.householdType)} · ${tn.residentsTotal}"

// =============================================================== UNIT DETAIL

@Composable
fun UnitScreen(vm: AppViewModel, s: Snapshot, nav: Nav, id: String) {
    val u = s.unitById[id] ?: return
    val tn = s.currentTenant(id)
    var edit by remember { mutableStateOf(false) }
    var addTenant by remember { mutableStateOf(false) }
    var endConfirm by remember { mutableStateOf(false) }
    var issue by remember { mutableStateOf(false) }
    var pay by remember { mutableStateOf(false) }
    val history = (s.tenantsByUnit[id] ?: emptyList()).filter { it.id != tn?.id }.sortedByDescending { it.startDate }
    Page {
        CardBox {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(u.name, fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f)); Tag(u.status)
            }
            Sub(s.unitLabel(u).substringBeforeLast(" · "))
            RowKV(t("Type"), t(u.type)); RowKV(t("Ownership"), t(u.ownership))
            if (u.number.isNotBlank()) RowKV(t("Unit number"), u.number)
            if (u.notes.isNotBlank()) Sub(u.notes)
            BigButton("✏️ " + t("Edit"), { edit = true }, outlined = true)
        }
        if (tn != null) {
            CardBox({ nav.push(Route.TenantR(tn.id)) }) {
                Text(t("Tenant"), fontWeight = FontWeight.Bold)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Avatar(tn.photoPath, tn.name, 56)
                    Column(Modifier.weight(1f)) {
                        Text(tn.name, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        Sub("📞 " + tn.phone.ifBlank { "—" })
                        Sub(householdLine(tn))
                    }
                    Text("›", fontSize = 24.sp)
                }
                val bal = s.balance(tn.id)
                RowKV(t("Monthly"), if (tn.monthlyRent > 0) fmtMoney(tn.monthlyRent, tn.currency) else "—")
                RowKV(t("Balance")) { MoneyText(bal, if (bal.isEmpty()) androidx.compose.ui.graphics.Color.Unspecified else RedC(), 18) }
            }
            BigButton("💰 " + t("Add Rent Payment"), { pay = true })
            BigButton(t("End tenancy / replace tenant"), { endConfirm = true }, outlined = true)
        } else BigButton("➕ " + t("Add tenant"), { addTenant = true })

        if (history.isNotEmpty()) {
            H2(t("Tenancy history"))
            history.forEach { h ->
                CardBox({ nav.push(Route.TenantR(h.id)) }) {
                    Text(h.name, fontWeight = FontWeight.Bold)
                    Sub("${h.startDate.ifBlank { "—" }} → ${h.endDate.ifBlank { "—" }}")
                    val bal = s.balance(h.id); if (bal.isNotEmpty()) Text(t("Balance") + ": " + moneyLine(bal), color = RedC())
                }
            }
        }
        H2(t("Maintenance"))
        val issues = s.maintenance.filter { it.unitId == id }
        issues.forEach { m ->
            CardBox({ nav.push(Route.IssueR(m.id)) }) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(m.title, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f)); Tag(m.status) }
                Sub(m.date)
            }
        }
        BigButton("➕ " + t("Add issue"), { issue = true }, outlined = true)
        H2(t("Room photos"))
        PhotoSection(vm, s, "units", id)
    }
    if (edit) UnitDialog(vm, s, u, { edit = false })
    if (addTenant) TenantDialog(vm, s, null, id) { addTenant = false }
    if (issue) MaintenanceDialog(vm, s, null, { issue = false }, unitId0 = id)
    if (pay) PaymentDialog(vm, s, { pay = false }, tenantId0 = tn?.id)
    if (endConfirm && tn != null) ConfirmDialog(
        t("End tenancy / replace tenant"), t("The tenant stays in the history with all payments. You can then add a new tenant."), t("End tenancy"),
        { endConfirm = false }
    ) { vm.save(tn.copy(active = false, endDate = Dates.today())) }
}

// =============================================================== TENANT DETAIL

@Composable
fun TenantScreen(vm: AppViewModel, s: Snapshot, nav: Nav, id: String) {
    val tn = s.tenantById[id] ?: return
    val ctx = LocalContext.current
    var edit by remember { mutableStateOf(false) }
    var pay by remember { mutableStateOf(false) }
    var debt by remember { mutableStateOf(false) }
    var resDlg by remember { mutableStateOf<Resident?>(null) }
    var addRes by remember { mutableStateOf(false) }
    var voidP by remember { mutableStateOf<Payment?>(null) }
    var allMonths by remember { mutableStateOf(false) }
    val unit = s.unitById[tn.unitId]
    val obs = s.obligations.filter { it.tenantId == id }.sortedByDescending { it.dueDate }
    val debts = s.debts.filter { it.tenantId == id }.sortedByDescending { it.date }
    val pays = s.payments.filter { it.tenantId == id }
    val residents = s.residents.filter { it.tenantId == id }
    Page {
        CardBox {
            AvatarEditor(vm, tn)
            Text(tn.name, fontSize = 24.sp, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.CenterHorizontally))
            if (unit != null) Sub(s.unitLabel(unit), Modifier.align(Alignment.CenterHorizontally))
            if (!tn.active) Text(t("Former tenant"), color = RedC(), fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.CenterHorizontally))
            if (tn.phone.isNotBlank()) BigButton("📞 ${tn.phone}", { dial(ctx, tn.phone) }, outlined = true)
            if (tn.phone2.isNotBlank()) BigButton("📞 ${tn.phone2}", { dial(ctx, tn.phone2) }, outlined = true)
            if (tn.email.isNotBlank()) RowKV(t("Email"), tn.email)
            if (tn.contactName.isNotBlank()) RowKV(t("Responsible person"), "${tn.contactName} ${tn.contactPhone}".trim())
            BigButton("✏️ " + t("Edit"), { edit = true }, outlined = true)
        }
        CardBox {
            Text(t("Who lives here"), fontWeight = FontWeight.Bold)
            RowKV(t("Type"), t(tn.householdType))
            RowKV(t("Total people"), "${tn.residentsTotal}")
            if (tn.householdType == "Family") {
                if (tn.father > 0) RowKV(t("Father"), "${tn.father}")
                if (tn.mother > 0) RowKV(t("Mother"), "${tn.mother}")
                if (tn.sons > 0) RowKV(t("Sons"), "${tn.sons}")
                if (tn.daughters > 0) RowKV(t("Daughters"), "${tn.daughters}")
            }
            residents.forEach { r ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) { Text(r.name); Sub(listOfNotNull(r.relationship.ifBlank { null }, r.age?.toString()).joinToString(" · ")) }
                    TextButton({ resDlg = r }) { Text("✏️") }
                }
            }
            BigButton("➕ " + t("Add resident"), { addRes = true }, outlined = true)
        }
        CardBox {
            Text(t("Rent"), fontWeight = FontWeight.Bold)
            RowKV(t("Monthly"), if (tn.monthlyRent > 0) fmtMoney(tn.monthlyRent, tn.currency) else "—")
            RowKV(t("Due day"), when (tn.dueDay) { 1 -> t("Beginning of month"); 31 -> t("End of month"); else -> "${tn.dueDay}" })
            RowKV(t("Rental start date"), tn.startDate.ifBlank { "—" })
            if (tn.endDate.isNotBlank()) RowKV(t("End date"), tn.endDate)
            H2(t("Balance"))
            RowKV(t("Rent unpaid")) { MoneyText(s.rentOwed(id), RedC(), 18) }
            RowKV(t("Debts and charges")) { MoneyText(s.debtOwed(id), RedC(), 18) }
            RowKV(t("Total")) { MoneyText(s.balance(id), RedC(), 20) }
            if (tn.active) { BigButton("💰 " + t("Add Rent Payment"), { pay = true }); BigButton("➕ " + t("Add Debt"), { debt = true }, outlined = true) }
        }
        H2(t("Rent months"))
        (if (allMonths) obs else obs.take(12)).forEach { o ->
            CardBox {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("${monthName(o.month)} ${o.year}", fontWeight = FontWeight.Bold); Tag(s.status(o))
                }
                Sub("${t("Paid")} ${fmtMoney(s.paid(o), o.currency)} ${t("of")} ${fmtMoney(o.expected, o.currency)}")
                o.collectedForId?.let { Sub(t("for") + " " + (s.personName(it) ?: "")) }
            }
        }
        if (obs.size > 12) OutlinedButton({ allMonths = !allMonths }, Modifier.fillMaxWidth()) { Text(t(if (allMonths) "Show less" else "Show all"), fontSize = 17.sp) }
        if (debts.isNotEmpty()) {
            H2(t("Debts and charges"))
            debts.forEach { d ->
                CardBox {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("${t(d.type)} · ${fmtMoney(d.amount, d.currency)}", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f)); Tag(s.debtStatus(d))
                    }
                    Sub("${d.date} ${d.description}".trim())
                    if (s.remaining(d) > 0.004) Text(t("Left") + " " + fmtMoney(s.remaining(d), d.currency), color = RedC())
                }
            }
        }
        H2(t("Payments"))
        if (pays.isEmpty()) Sub(t("No payments yet"))
        pays.forEach { p ->
            CardBox {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(fmtMoney(p.amount, p.currency), fontWeight = FontWeight.Bold, fontSize = 19.sp,
                        textDecoration = if (p.voided) androidx.compose.ui.text.style.TextDecoration.LineThrough else null)
                    Sub(p.date)
                }
                Sub(t("Collected For") + ": " + (s.personName(p.collectedForId) ?: t("No one")))
                if (p.notes.isNotBlank()) Sub(p.notes)
                if (p.voided) Text(t("Voided"), color = RedC(), fontWeight = FontWeight.Bold)
                else {
                    if (s.unallocated(p) > 0.004) Sub(t("Credit not applied") + ": " + fmtMoney(s.unallocated(p), p.currency))
                    TextButton({ voidP = p }) { Text(t("Void this payment"), color = RedC()) }
                }
            }
        }
    }
    if (edit) TenantDialog(vm, s, tn, tn.unitId) { edit = false }
    if (pay) PaymentDialog(vm, s, { pay = false }, tenantId0 = id)
    if (debt) DebtDialog(vm, s, { debt = false }, tenantId0 = id)
    if (addRes) ResidentDialog(vm, id, null) { addRes = false }
    resDlg?.let { r -> ResidentDialog(vm, id, r) { resDlg = null } }
    voidP?.let { p ->
        ConfirmDialog(t("Void this payment"), t("The payment stays in the history marked as voided, and the balance is restored."), t("Void"), { voidP = null }) { vm.voidPayment(p) }
    }
}

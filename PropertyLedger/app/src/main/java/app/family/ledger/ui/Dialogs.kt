package app.family.ledger.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.family.ledger.AppViewModel
import app.family.ledger.data.*

fun parseNum(s: String): Double? =
    s.trim().map { c -> if (c in '٠'..'٩') ('0' + (c - '٠')) else if (c == '٫' || c == ',') '.' else c }
        .joinToString("").toDoubleOrNull()

fun defaultCurrency(snap: Snapshot) = snap.setting("default_currency", "USD")

@Composable
fun optionList(snap: Snapshot, kind: String, builtin: List<String>): List<Pair<String?, String>> {
    val fromDb = snap.options.filter { it.kind == kind && !it.archived }.map { it.value }
    return (builtin + fromDb).distinct().map { it to t(it) }
}

@Composable
fun CurrencyChips(value: String, onChange: (String) -> Unit) {
    ChipRow(CURRENCIES.map { it to it }, value, onChange)
}

@Composable
fun personOptions(snap: Snapshot, none: String? = "No one"): List<Pair<String?, String>> =
    (if (none != null) listOf<Pair<String?, String>>(null to t(none)) else emptyList()) +
        snap.people.filter { !it.archived }.map { it.id to it.name }

@Composable
fun Stepper(label: String, value: Int, onChange: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 18.sp)
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton({ if (value > 0) onChange(value - 1) }, Modifier.size(48.dp), contentPadding = PaddingValues(0.dp)) { Text("−", fontSize = 22.sp) }
            Text("$value", fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.widthIn(min = 36.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            OutlinedButton({ onChange(value + 1) }, Modifier.size(48.dp), contentPadding = PaddingValues(0.dp)) { Text("+", fontSize = 22.sp) }
        }
    }
}

// ------------------------------------------------------------------ people

@Composable
fun PersonDialog(vm: AppViewModel, existing: Person?, onDismiss: () -> Unit, onSaved: (Person) -> Unit = {}) {
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var phone by remember { mutableStateOf(existing?.phone ?: "") }
    var notes by remember { mutableStateOf(existing?.notes ?: "") }
    var archived by remember { mutableStateOf(existing?.archived ?: false) }
    FormDialog(t(if (existing == null) "Add person" else "Edit person"), onDismiss, name.isNotBlank(), {
        val p = (existing ?: Person(name = name)).copy(name = name.trim(), phone = phone.trim(), notes = notes, archived = archived)
        vm.save(p); onSaved(p)
    }) {
        TField(t("Name"), name, { name = it })
        TField(t("Phone"), phone, { phone = it }, phone = true)
        TField(t("Notes"), notes, { notes = it })
        if (existing != null) Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(archived, { archived = it }); Spacer(Modifier.width(8.dp)); Text(t("Archived"))
        }
    }
}

// ------------------------------------------------------------------ payments

@Composable
fun PaymentDialog(
    vm: AppViewModel, snap: Snapshot, onDismiss: () -> Unit,
    tenantId0: String? = null, obligationId0: String? = null, debtId0: String? = null
) {
    val tenants = snap.activeTenants.sortedBy { snap.unitById[it.unitId]?.name ?: "" }
    val ob0 = obligationId0?.let { id -> snap.obligations.firstOrNull { it.id == id } }
    var tenantId by remember { mutableStateOf(tenantId0 ?: ob0?.tenantId) }
    var target by remember { mutableStateOf(obligationId0 ?: debtId0) }
    var amount by remember { mutableStateOf(ob0?.let { fmtPlain(snap.remaining(it)) } ?: "") }
    var currency by remember { mutableStateOf(ob0?.currency ?: tenantId?.let { snap.tenantById[it]?.currency } ?: defaultCurrency(snap)) }
    var date by remember { mutableStateOf(Dates.today()) }
    var notes by remember { mutableStateOf("") }
    var collectedFor by remember {
        mutableStateOf(ob0?.collectedForId ?: tenantId?.let { snap.unitById[snap.tenantById[it]?.unitId]?.defaultCollectedForId })
    }
    var addPerson by remember { mutableStateOf(false) }

    val tenant = tenantId?.let { snap.tenantById[it] }
    val lang = LocalLang.current
    val openItems = remember(tenantId, snap, lang) {
        if (tenantId == null) emptyList() else
            snap.obligations.filter { it.tenantId == tenantId && snap.remaining(it) > 0.004 }.sortedBy { it.dueDate }
                .map { it.id to ("${tr(lang, "Rent")} ${it.year}-${"%02d".format(it.month)} · ${tr(lang, "Left")} ${fmtMoney(snap.remaining(it), it.currency)}") } +
                snap.debts.filter { it.tenantId == tenantId && snap.remaining(it) > 0.004 }.sortedBy { it.date }
                    .map { it.id to ("${tr(lang, it.type)} ${it.date} · ${tr(lang, "Left")} ${fmtMoney(snap.remaining(it), it.currency)}") }
    }
    val amt = parseNum(amount)

    FormDialog(t("Add Rent Payment"), onDismiss, tenantId != null && amt != null && amt > 0, {
        vm.recordPayment(tenantId!!, amt!!, currency, date, notes, collectedFor, target, target)
    }) {
        Picker(t("Tenant"), tenants.map { it.id as String? to snap.tenantLabel(it) }, tenantId, {
            tenantId = it
            snap.tenantById[it]?.let { tt ->
                currency = tt.currency
                collectedFor = snap.unitById[tt.unitId]?.defaultCollectedForId
            }
            target = null
        })
        if (tenant != null) {
            val bal = snap.balance(tenant.id)
            if (bal.isNotEmpty()) Sub(t("Balance") + ": " + bal.entries.joinToString(" + ") { fmtMoney(it.value, it.key) })
        }
        TField(t("Amount"), amount, { amount = it }, number = true)
        CurrencyChips(currency) { currency = it }
        DateField(t("Date"), date) { date = it }
        Picker(t("Apply to"), listOf<Pair<String?, String>>(null to t("Oldest balance first (automatic)")) + openItems, target, { target = it })
        Picker(t("Collected For"), personOptions(snap), collectedFor, { collectedFor = it }, onAdd = { addPerson = true })
        Sub(t("Collected for someone else is saved on this payment only. It never changes past or future payments."))
        TField(t("Notes"), notes, { notes = it })
    }
    if (addPerson) PersonDialog(vm, null, { addPerson = false }) { collectedFor = it.id }
}

fun fmtPlain(d: Double): String = if (d % 1.0 == 0.0) d.toLong().toString() else "%.2f".format(java.util.Locale.US, d)

@Composable
fun DebtDialog(vm: AppViewModel, snap: Snapshot, onDismiss: () -> Unit, tenantId0: String? = null) {
    val tenants = snap.activeTenants.sortedBy { snap.unitById[it.unitId]?.name ?: "" }
    var tenantId by remember { mutableStateOf(tenantId0) }
    var type by remember { mutableStateOf("Old Rent Debt") }
    var amount by remember { mutableStateOf("") }
    var currency by remember { mutableStateOf(tenantId?.let { snap.tenantById[it]?.currency } ?: defaultCurrency(snap)) }
    var date by remember { mutableStateOf(Dates.today()) }
    var desc by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var addType by remember { mutableStateOf(false) }
    val amt = parseNum(amount)
    FormDialog(t("Add Debt"), onDismiss, tenantId != null && amt != null && amt > 0, {
        val tn = snap.tenantById[tenantId]!!
        vm.save(Debt(tenantId = tn.id, unitId = tn.unitId, type = type, amount = amt!!, currency = currency, date = date, description = desc, notes = notes))
    }) {
        Picker(t("Tenant"), tenants.map { it.id as String? to snap.tenantLabel(it) }, tenantId, { tenantId = it; snap.tenantById[it]?.let { c -> currency = c.currency } })
        Picker(t("Type"), optionList(snap, "DEBT_TYPE", Seed.DEBT_TYPES), type, { type = it ?: type }, onAdd = { addType = true })
        TField(t("Amount"), amount, { amount = it }, number = true)
        CurrencyChips(currency) { currency = it }
        DateField(t("Date"), date) { date = it }
        TField(t("Description"), desc, { desc = it })
        TField(t("Notes"), notes, { notes = it })
    }
    if (addType) AddOptionDialog(t("Add new"), { addType = false }) { vm.addOption("DEBT_TYPE", it); type = it }
}

@Composable
fun HandoverDialog(vm: AppViewModel, snap: Snapshot, onDismiss: () -> Unit, personId0: String? = null) {
    var personId by remember { mutableStateOf(personId0) }
    var amount by remember { mutableStateOf("") }
    var currency by remember { mutableStateOf(defaultCurrency(snap)) }
    var date by remember { mutableStateOf(Dates.today()) }
    var notes by remember { mutableStateOf("") }
    var addPerson by remember { mutableStateOf(false) }
    val amt = parseNum(amount)
    FormDialog(t("Record Handover"), onDismiss, personId != null && amt != null && amt > 0, {
        vm.save(Handover(personId = personId!!, amount = amt!!, currency = currency, date = date, notes = notes))
    }) {
        Picker(t("Person"), personOptions(snap, null), personId, { personId = it }, onAdd = { addPerson = true })
        personId?.let { Sub(t("Remaining") + ": " + (snap.toHandOver(it).entries.joinToString(" + ") { e -> fmtMoney(e.value, e.key) }.ifBlank { "0" })) }
        TField(t("Amount"), amount, { amount = it }, number = true)
        CurrencyChips(currency) { currency = it }
        DateField(t("Date"), date) { date = it }
        TField(t("Notes"), notes, { notes = it })
    }
    if (addPerson) PersonDialog(vm, null, { addPerson = false }) { personId = it.id }
}

// ------------------------------------------------------------------ expenses / maintenance

@Composable
fun ExpenseDialog(vm: AppViewModel, snap: Snapshot, existing: Expense?, onDismiss: () -> Unit, unitId0: String? = null) {
    var category by remember { mutableStateOf(existing?.category ?: "Repair") }
    var amount by remember { mutableStateOf(existing?.let { fmtPlain(it.amount) } ?: "") }
    var currency by remember { mutableStateOf(existing?.currency ?: defaultCurrency(snap)) }
    var date by remember { mutableStateOf(existing?.date ?: Dates.today()) }
    var buildingId by remember { mutableStateOf(existing?.buildingId ?: unitId0?.let { snap.unitById[it]?.buildingId }) }
    var unitId by remember { mutableStateOf(existing?.unitId ?: unitId0) }
    var desc by remember { mutableStateOf(existing?.description ?: "") }
    var notes by remember { mutableStateOf(existing?.notes ?: "") }
    var addCat by remember { mutableStateOf(false) }
    val amt = parseNum(amount)
    FormDialog(t(if (existing == null) "Add expense" else "Edit expense"), onDismiss, amt != null && amt > 0, {
        vm.save((existing ?: Expense(amount = amt!!, currency = currency, date = date)).copy(
            category = category, amount = amt!!, currency = currency, date = date, buildingId = buildingId,
            unitId = unitId, description = desc, notes = notes
        ))
    }) {
        Picker(t("Category"), optionList(snap, "EXPENSE_CATEGORY", Seed.EXPENSE_CATEGORIES), category, { category = it ?: category }, onAdd = { addCat = true })
        TField(t("Amount"), amount, { amount = it }, number = true)
        CurrencyChips(currency) { currency = it }
        DateField(t("Date"), date) { date = it }
        Picker(t("Building"), listOf<Pair<String?, String>>(null to "—") + snap.buildings.filter { !it.archived }.map { it.id to it.name }, buildingId, { buildingId = it; unitId = null })
        Picker(t("Unit"), listOf<Pair<String?, String>>(null to "—") + snap.units.filter { !it.archived && (buildingId == null || it.buildingId == buildingId) }.map { it.id to snap.unitLabel(it) }, unitId, {
            unitId = it; snap.unitById[it]?.let { u -> buildingId = u.buildingId }
        })
        TField(t("Description"), desc, { desc = it })
        TField(t("Notes"), notes, { notes = it })
        if (existing != null) { Text(t("Receipt photo"), fontWeight = FontWeight.Bold); PhotoSection(vm, snap, "expenses", existing.id) }
        else Sub(t("Save the expense first, then open it to add a receipt photo."))
    }
    if (addCat)AddOptionDialog(t("Add new"), { addCat = false }) { vm.addOption("EXPENSE_CATEGORY", it); category = it }
}

val MAINT_STATUSES = listOf("Open", "In Progress", "Completed", "Cancelled")

@Composable
fun MaintenanceDialog(vm: AppViewModel, snap: Snapshot, existing: Maintenance?, onDismiss: () -> Unit, unitId0: String? = null, onSaved: (Maintenance) -> Unit = {}) {
    var title by remember { mutableStateOf(existing?.title ?: "") }
    var desc by remember { mutableStateOf(existing?.description ?: "") }
    var unitId by remember { mutableStateOf(existing?.unitId ?: unitId0) }
    var date by remember { mutableStateOf(existing?.date ?: Dates.today()) }
    var status by remember { mutableStateOf(existing?.status ?: "Open") }
    var est by remember { mutableStateOf(existing?.let { fmtPlain(it.estimatedCost) } ?: "") }
    var act by remember { mutableStateOf(existing?.let { fmtPlain(it.actualCost) } ?: "") }
    var currency by remember { mutableStateOf(existing?.currency ?: defaultCurrency(snap)) }
    var notes by remember { mutableStateOf(existing?.notes ?: "") }
    FormDialog(t(if (existing == null) "Add issue" else "Edit"), onDismiss, title.isNotBlank(), {
        val u = snap.unitById[unitId]
        val m = (existing ?: Maintenance(title = title, date = date)).copy(
            title = title.trim(), description = desc, unitId = unitId, buildingId = u?.buildingId, floorId = u?.floorId, date = date,
            status = status, estimatedCost = parseNum(est) ?: 0.0, actualCost = parseNum(act) ?: 0.0, currency = currency, notes = notes
        )
        vm.save(m); onSaved(m)
    }) {
        TField(t("Title"), title, { title = it })
        Picker(t("Unit"), listOf<Pair<String?, String>>(null to "—") + snap.units.filter { !it.archived }.map { it.id to snap.unitLabel(it) }, unitId, { unitId = it })
        TField(t("Description"), desc, { desc = it }, lines = 2)
        DateField(t("Date"), date) { date = it }
        ChipRow(MAINT_STATUSES.map { it to t(it) }, status) { status = it }
        TField(t("Estimated cost"), est, { est = it }, number = true)
        TField(t("Actual cost"), act, { act = it }, number = true)
        CurrencyChips(currency) { currency = it }
        TField(t("Notes"), notes, { notes = it })
    }
}

// ------------------------------------------------------------------ structure

@Composable
fun BuildingDialog(vm: AppViewModel, existing: Building?, onDismiss: () -> Unit) {
    var number by remember { mutableStateOf(existing?.number ?: "") }
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var address by remember { mutableStateOf(existing?.address ?: "") }
    var notes by remember { mutableStateOf(existing?.notes ?: "") }
    var archived by remember { mutableStateOf(existing?.archived ?: false) }
    FormDialog(t(if (existing == null) "Add Building" else "Edit building"), onDismiss, name.isNotBlank(), {
        vm.save((existing ?: Building()).copy(number = number.trim(), name = name.trim(), address = address, notes = notes, archived = archived))
    }) {
        TField(t("Building number"), number, { number = it })
        TField(t("Name"), name, { name = it })
        TField(t("Address / location"), address, { address = it })
        TField(t("Notes"), notes, { notes = it })
        if (existing != null) Row(verticalAlignment = Alignment.CenterVertically) { Switch(archived, { archived = it }); Spacer(Modifier.width(8.dp)); Text(t("Archived")) }
    }
}

@Composable
fun FloorDialog(vm: AppViewModel, buildingId: String, existing: Floor?, onDismiss: () -> Unit) {
    var number by remember { mutableStateOf(existing?.number?.toString() ?: "0") }
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var ownership by remember { mutableStateOf(existing?.ownership ?: "Ours") }
    var notes by remember { mutableStateOf(existing?.notes ?: "") }
    var archived by remember { mutableStateOf(existing?.archived ?: false) }
    FormDialog(t(if (existing == null) "Add floor" else "Edit floor"), onDismiss, name.isNotBlank(), {
        val n = number.toIntOrNull() ?: 0
        vm.save((existing ?: Floor(buildingId = buildingId)).copy(number = n, sortOrder = n, name = name.trim(), ownership = ownership, notes = notes, archived = archived))
    }) {
        TField(t("Floor number"), number, { number = it }, number = true)
        TField(t("Name"), name, { name = it })
        Picker(t("Ownership"), OWNERSHIP.map { it to t(it) }, ownership, { ownership = it ?: ownership })
        TField(t("Notes"), notes, { notes = it })
        if (existing != null) Row(verticalAlignment = Alignment.CenterVertically) { Switch(archived, { archived = it }); Spacer(Modifier.width(8.dp)); Text(t("Archived")) }
    }
}

val OWNERSHIP = listOf("Ours", "Family", "Managed for another", "Not ours", "Shared", "Other")
val UNIT_STATUSES = listOf("Rented", "Vacant", "Family", "Not ours", "Maintenance")

@Composable
fun UnitDialog(vm: AppViewModel, snap: Snapshot, existing: PropertyUnit?, onDismiss: () -> Unit, buildingId0: String? = null, floorId0: String? = null) {
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var number by remember { mutableStateOf(existing?.number ?: "") }
    var type by remember { mutableStateOf(existing?.type ?: "House") }
    var buildingId by remember { mutableStateOf(existing?.buildingId ?: buildingId0 ?: snap.buildings.firstOrNull()?.id) }
    var floorId by remember { mutableStateOf(existing?.floorId ?: floorId0) }
    var ownership by remember { mutableStateOf(existing?.ownership ?: "Ours") }
    var status by remember { mutableStateOf(existing?.status ?: "Rented") }
    var rent by remember { mutableStateOf(existing?.let { if (it.rentAmount > 0) fmtPlain(it.rentAmount) else "" } ?: "") }
    var contactName by remember { mutableStateOf(existing?.contactName ?: "") }
    var contactPhone by remember { mutableStateOf(existing?.contactPhone ?: "") }
    var collectedFor by remember { mutableStateOf(existing?.defaultCollectedForId) }
    var notes by remember { mutableStateOf(existing?.notes ?: "") }
    var archived by remember { mutableStateOf(existing?.archived ?: false) }
    var addType by remember { mutableStateOf(false) }
    var addPerson by remember { mutableStateOf(false) }
    val floors = snap.floors.filter { it.buildingId == buildingId && !it.archived }
    FormDialog(t(if (existing == null) "Add unit" else "Edit unit"), onDismiss, name.isNotBlank() && buildingId != null && floors.any { it.id == floorId }, {
        val base = existing ?: PropertyUnit(buildingId = buildingId!!, floorId = floorId!!)
        val u = base.copy(
            name = name.trim(), number = number.trim(), type = type, buildingId = buildingId!!, floorId = floorId!!,
            ownership = ownership, status = status, rentAmount = parseNum(rent) ?: 0.0, currency = defaultCurrency(snap),
            contactName = contactName, contactPhone = contactPhone, defaultCollectedForId = collectedFor, notes = notes, archived = archived
        )
        val firstTenant = if (existing == null && status == "Rented")
            Tenant(unitId = u.id, name = contactName.ifBlank { "—" }, phone = contactPhone, monthlyRent = parseNum(rent) ?: 0.0, currency = defaultCurrency(snap), startDate = Dates.today())
        else null
        vm.saveUnit(u, firstTenant)
    }) {
        TField(t("Unit name"), name, { name = it })
        TField(t("Unit number"), number, { number = it })
        Picker(t("Type"), optionList(snap, "UNIT_TYPE", Seed.UNIT_TYPES), type, { type = it ?: type }, onAdd = { addType = true })
        Picker(t("Building"), snap.buildings.filter { !it.archived }.map { it.id as String? to it.name }, buildingId, {
            buildingId = it; floorId = snap.floors.firstOrNull { f -> f.buildingId == it && !f.archived }?.id
        })
        Picker(t("Floor"), floors.map { it.id as String? to it.name }, floorId, { floorId = it })
        Picker(t("Ownership"), OWNERSHIP.map { it to t(it) }, ownership, { ownership = it ?: ownership })
        Picker(t("Status"), UNIT_STATUSES.map { it to t(it) }, status, { status = it ?: status })
        TField(t("Monthly rent"), rent, { rent = it }, number = true)
        TField(t("Responsible person"), contactName, { contactName = it })
        TField(t("Phone"), contactPhone, { contactPhone = it }, phone = true)
        Picker(t("Rent is usually collected for"), personOptions(snap), collectedFor, { collectedFor = it }, onAdd = { addPerson = true })
        TField(t("Notes"), notes, { notes = it })
        if (existing != null) Row(verticalAlignment = Alignment.CenterVertically) { Switch(archived, { archived = it }); Spacer(Modifier.width(8.dp)); Text(t("Archived")) }
    }
    if (addType) AddOptionDialog(t("Add new"), { addType = false }) { vm.addOption("UNIT_TYPE", it); type = it }
    if (addPerson) PersonDialog(vm, null, { addPerson = false }) { collectedFor = it.id }
}

// ------------------------------------------------------------------ tenants

val HOUSEHOLDS = listOf("Family", "Men", "Women", "Individuals", "Other")

@Composable
fun TenantDialog(vm: AppViewModel, snap: Snapshot, existing: Tenant?, unitId: String, onDismiss: () -> Unit) {
    val unit = snap.unitById[unitId]
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var phone by remember { mutableStateOf(existing?.phone ?: "") }
    var phone2 by remember { mutableStateOf(existing?.phone2 ?: "") }
    var email by remember { mutableStateOf(existing?.email ?: "") }
    var cName by remember { mutableStateOf(existing?.contactName ?: "") }
    var cPhone by remember { mutableStateOf(existing?.contactPhone ?: "") }
    var cRel by remember { mutableStateOf(existing?.contactRelation ?: "") }
    var hh by remember { mutableStateOf(existing?.householdType ?: "Family") }
    var father by remember { mutableStateOf(existing?.father ?: 0) }
    var mother by remember { mutableStateOf(existing?.mother ?: 0) }
    var sons by remember { mutableStateOf(existing?.sons ?: 0) }
    var daughters by remember { mutableStateOf(existing?.daughters ?: 0) }
    var other by remember { mutableStateOf(existing?.otherCount ?: 0) }
    var rent by remember { mutableStateOf(existing?.let { if (it.monthlyRent > 0) fmtPlain(it.monthlyRent) else "" } ?: unit?.rentAmount?.takeIf { it > 0 }?.let { fmtPlain(it) } ?: "") }
    var currency by remember { mutableStateOf(existing?.currency ?: defaultCurrency(snap)) }
    var schedule by remember { mutableStateOf(when (existing?.dueDay ?: snap.setting("default_due_day", "1").toIntOrNull() ?: 1) { 1 -> "Beginning of month"; 31 -> "End of month"; else -> "Custom day" }) }
    var customDay by remember { mutableStateOf((existing?.dueDay ?: 15).takeIf { it in 2..30 }?.toString() ?: "15") }
    var start by remember { mutableStateOf(existing?.startDate ?: Dates.today()) }
    var notes by remember { mutableStateOf(existing?.notes ?: "") }
    FormDialog(t(if (existing == null) "Add tenant" else "Edit tenant"), onDismiss, name.isNotBlank(), {
        val day = when (schedule) { "Beginning of month" -> 1; "End of month" -> 31; else -> customDay.toIntOrNull()?.coerceIn(1, 31) ?: 1 }
        vm.save((existing ?: Tenant(unitId = unitId, name = name)).copy(
            name = name.trim(), phone = phone.trim(), phone2 = phone2.trim(), email = email.trim(),
            contactName = cName, contactPhone = cPhone, contactRelation = cRel, householdType = hh,
            father = father, mother = mother, sons = sons, daughters = daughters, otherCount = other,
            monthlyRent = parseNum(rent) ?: 0.0, currency = currency, dueDay = day, startDate = start, notes = notes
        ))
    }) {
        TField(t("Full name"), name, { name = it })
        TField(t("Phone"), phone, { phone = it }, phone = true)
        TField(t("Phone 2"), phone2, { phone2 = it }, phone = true)
        TField(t("Email"), email, { email = it })
        H2(t("Who lives here"))
        ChipRow(HOUSEHOLDS.map { it to t(it) }, hh) { hh = it }
        if (hh == "Family") {
            Stepper(t("Father"), father) { father = it }
            Stepper(t("Mother"), mother) { mother = it }
            Stepper(t("Sons"), sons) { sons = it }
            Stepper(t("Daughters"), daughters) { daughters = it }
            Text(t("Total people") + ": " + (father + mother + sons + daughters), fontWeight = FontWeight.Bold)
        } else Stepper(t("Total people"), other) { other = it }
        H2(t("Rent"))
        TField(t("Monthly rent"), rent, { rent = it }, number = true)
        CurrencyChips(currency) { currency = it }
        ChipRow(listOf("Beginning of month", "End of month", "Custom day").map { it to t(it) }, schedule) { schedule = it }
        if (schedule == "Custom day") TField(t("Due day"), customDay, { customDay = it }, number = true)
        DateField(t("Rental start date"), start) { start = it }
        H2(t("Responsible person"))
        TField(t("Name"), cName, { cName = it })
        TField(t("Phone"), cPhone, { cPhone = it }, phone = true)
        TField(t("Relationship"), cRel, { cRel = it })
        TField(t("Notes"), notes, { notes = it })
    }
}

@Composable
fun ResidentDialog(vm: AppViewModel, tenantId: String, existing: Resident?, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var rel by remember { mutableStateOf(existing?.relationship ?: "") }
    var age by remember { mutableStateOf(existing?.age?.toString() ?: "") }
    var gender by remember { mutableStateOf(existing?.gender ?: "") }
    var phone by remember { mutableStateOf(existing?.phone ?: "") }
    var notes by remember { mutableStateOf(existing?.notes ?: "") }
    FormDialog(t(if (existing == null) "Add resident" else "Edit"), onDismiss, name.isNotBlank(), {
        vm.save((existing ?: Resident(tenantId = tenantId, name = name)).copy(name = name.trim(), relationship = rel, age = age.toIntOrNull(), gender = gender, phone = phone, notes = notes))
    }) {
        TField(t("Name"), name, { name = it })
        TField(t("Relationship"), rel, { rel = it })
        TField(t("Age"), age, { age = it }, number = true)
        ChipRow(listOf("" to "—", "Male" to t("Male"), "Female" to t("Female")), gender) { gender = it }
        TField(t("Phone"), phone, { phone = it }, phone = true)
        TField(t("Notes"), notes, { notes = it })
    }
}

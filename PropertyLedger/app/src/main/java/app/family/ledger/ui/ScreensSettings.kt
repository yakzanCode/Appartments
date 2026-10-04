package app.family.ledger.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.family.ledger.AppViewModel
import app.family.ledger.data.*
import kotlinx.coroutines.launch
import java.io.File

// =============================================================== MORE / TENANTS / SEARCH

@Composable
fun MoreScreen(vm: AppViewModel, s: Snapshot, nav: Nav) {
    val items = listOf(
        Triple("👥", "Tenants", Route.Tenants), Triple("📕", "Debts", Route.Debts), Triple("🔧", "Maintenance", Route.Maintenance),
        Triple("🧾", "Expenses", Route.Expenses), Triple("📊", "Reports", Route.Reports), Triple("🏢", "Buildings", Route.Buildings),
        Triple("🗺️", "Floor Plan", Route.FloorPlan), Triple("🔍", "Search", Route.Search), Triple("⚙️", "Settings", Route.Settings)
    )
    Page {
        items.forEach { (icon, label, route) ->
            CardBox({ nav.push(route) }) {
                Row(Modifier.fillMaxWidth().heightIn(min = 40.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("$icon  ${t(label)}", fontSize = 20.sp); Text("›", fontSize = 24.sp)
                }
            }
        }
    }
}

@Composable
fun TenantsScreen(vm: AppViewModel, s: Snapshot, nav: Nav) {
    var q by remember { mutableStateOf("") }
    var former by remember { mutableStateOf(false) }
    val list = s.tenants.filter { (former || it.active) && !it.archived && (q.isBlank() || it.name.contains(q, true) || it.phone.contains(q) || it.phone2.contains(q)) }
        .sortedBy { it.name }
    Page {
        TField(t("Search"), q, { q = it })
        Row(verticalAlignment = Alignment.CenterVertically) { Switch(former, { former = it }); Spacer(Modifier.width(8.dp)); Text(t("Include former tenants")) }
        list.forEach { tn ->
            CardBox({ nav.push(Route.TenantR(tn.id)) }) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Avatar(tn.photoPath, tn.name, 52)
                    Column(Modifier.weight(1f)) {
                        Text(tn.name, fontWeight = FontWeight.Bold, fontSize = 19.sp)
                        Sub(s.unitById[tn.unitId]?.let { s.unitLabel(it) } ?: "")
                        Sub("📞 " + tn.phone.ifBlank { "—" })
                    }
                    Text("›", fontSize = 24.sp)
                }
            }
        }
    }
}

@Composable
fun SearchScreen(vm: AppViewModel, s: Snapshot, nav: Nav) {
    var q by remember { mutableStateOf("") }
    fun m(vararg f: String) = q.isNotBlank() && f.any { it.contains(q.trim(), true) }
    val b = s.buildings.filter { m(it.name, it.number, it.address) }
    val u = s.units.filter { m(it.name, it.number, it.contactName, it.contactPhone) || m(s.floorById[it.floorId]?.name ?: "") }
    val tn = s.tenants.filter { m(it.name, it.phone, it.phone2, it.contactName, it.contactPhone, it.email) }
    val rs = s.residents.filter { m(it.name, it.phone) }
    val ds = s.debts.filter { m(it.description, it.notes) }
    val ms = s.maintenance.filter { m(it.title, it.description) }
    val ps = s.people.filter { m(it.name, it.phone) }
    Page {
        TField(t("Search tenant, phone, unit…"), q, { q = it })
        @Composable fun group(title: String, rows: List<Pair<String, Route>>) {
            if (rows.isNotEmpty()) {
                H2(t(title))
                rows.take(30).forEach { (label, r) -> CardBox({ nav.push(r) }) { Text(label, fontSize = 18.sp) } }
            }
        }
        group("Buildings", b.map { "${it.number} · ${it.name}" to Route.BuildingR(it.id) })
        group("Units", u.map { s.unitLabel(it) to Route.UnitR(it.id) })
        group("Tenants", tn.map { "${it.name}  📞 ${it.phone}" to Route.TenantR(it.id) })
        group("Residents", rs.mapNotNull { r -> s.tenantById[r.tenantId]?.let { "${r.name} (${it.name})" to Route.TenantR(it.id) } })
        group("Debts", ds.map { "${it.description} — ${s.tenantById[it.tenantId]?.name ?: ""}" to Route.TenantR(it.tenantId) })
        group("Maintenance", ms.map { it.title to Route.IssueR(it.id) })
        group("People", ps.map { it.name to Route.Others })
    }
}

// =============================================================== SETTINGS

fun shareFile(ctx: Context, file: File, subject: String) {
    val uri = Backup.shareUri(ctx, file)
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "application/zip"
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_SUBJECT, subject)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    // Normal Android share sheet. The user picks WhatsApp, Bluetooth, Files, e-mail... himself.
    ctx.startActivity(Intent.createChooser(send, subject).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

@Composable
fun SettingsScreen(vm: AppViewModel, s: Snapshot, nav: Nav) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val lang = LocalLang.current
    var busy by remember { mutableStateOf(false) }
    var restoreUri by remember { mutableStateOf<Uri?>(null) }
    var restoreInfo by remember { mutableStateOf<BackupInfo?>(null) }
    var pinDlg by remember { mutableStateOf(false) }
    val pinOn by vm.pinEnabled.collectAsState()
    var dueDay by remember(s.settings) { mutableStateOf(s.setting("default_due_day", "1")) }

    fun toast(msg: String) = Toast.makeText(ctx, msg, Toast.LENGTH_LONG).show()
    fun export(kind: String, subject: String) {
        scope.launch {
            busy = true
            try { shareFile(ctx, vm.makeExport(kind), subject) } catch (e: Exception) { toast(tr(lang, "Something went wrong") + ": " + e.message) }
            busy = false
        }
    }
    val pickBackup = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            try { restoreInfo = vm.inspectBackup(uri); restoreUri = uri } catch (e: Exception) { toast(tr(lang, "This is not a valid backup file")) }
        }
    }

    Page {
        CardBox {
            Text(t("Language"), fontWeight = FontWeight.Bold)
            ChipRow(listOf("ar" to "العربية", "en" to "English"), lang) { vm.setSetting("lang", it) }
            Text(t("Night mode"), fontWeight = FontWeight.Bold)
            ChipRow(listOf("light" to t("Light"), "dark" to t("Dark")), s.setting("theme", "light")) { vm.setSetting("theme", it) }
            Text(t("Default currency"), fontWeight = FontWeight.Bold)
            CurrencyChips(s.setting("default_currency", "USD")) { vm.setSetting("default_currency", it) }
            TField(t("Default rent due day"), dueDay, { dueDay = it; it.toIntOrNull()?.takeIf { d -> d in 1..31 }?.let { d -> vm.setSetting("default_due_day", d.toString()) } }, number = true)
        }

        H2(t("Backup"))
        Sub(t("Your data lives only on this phone. Nothing is sent anywhere unless you share a backup yourself."))
        BigButton("📤 " + t("Share Data Only"), { export("data", "property_data_backup") })
        BigButton("📤 " + t("Share Data + Photos"), { export("full", "property_full_backup") })
        BigButton("📥 " + t("Restore Data"), { pickBackup.launch(arrayOf("application/zip", "application/octet-stream", "*/*")) }, outlined = true)
        BigButton("📄 " + t("Export CSV (Excel)"), { export("csv", "property_csv") }, outlined = true)
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())

        H2(t("Security"))
        CardBox {
            RowKV(t("PIN lock"), t(if (pinOn) "On" else "Off"))
            BigButton(t(if (pinOn) "Change or remove PIN" else "Set a PIN"), { pinDlg = true }, outlined = true)
        }

        H2(t("Archive"))
        ArchiveSection(vm, s)

        H2(t("Data statistics"))
        CardBox {
            RowKV(t("Buildings"), "${s.buildings.size}"); RowKV(t("Units"), "${s.units.size}")
            RowKV(t("Tenants"), "${s.tenants.size}"); RowKV(t("Payments"), "${s.payments.size}")
            RowKV(t("Debts"), "${s.debts.size}"); RowKV(t("Expenses"), "${s.expenses.size}")
            RowKV(t("Photos"), "${s.photos.size}")
        }
        CardBox {
            RowKV(t("Database version"), "$DB_VERSION")
            RowKV(t("Internet permission"), t("None"))
            Sub(t("About") + ": " + t("Private family property ledger. Works fully offline."))
        }
    }

    val info = restoreInfo
    if (info != null && restoreUri != null) {
        AlertDialog(
            onDismissRequest = { restoreInfo = null; restoreUri = null },
            title = { Text(t("Restore Data"), fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(t("This will replace the current local data with the backup. A safety copy of the current data is saved first. Do you want to continue?"))
                    Spacer(Modifier.height(6.dp))
                    RowKV(t("Backup date"), info.created.take(10))
                    RowKV(t("Buildings"), "${info.buildings}"); RowKV(t("Units"), "${info.units}"); RowKV(t("Tenants"), "${info.tenants}")
                    RowKV(t("Financial records"), "${info.financialRecords}")
                    RowKV(t("Photos"), "${info.photoRows} (" + t("files in backup") + ": ${info.photoFiles})")
                }
            },
            confirmButton = {
                Button({
                    val u = restoreUri!!; restoreInfo = null; restoreUri = null
                    scope.launch {
                        busy = true
                        try { vm.restore(u); toast(tr(lang, "Restore finished")) } catch (e: Exception) { toast(tr(lang, "Something went wrong") + ": " + e.message) }
                        busy = false
                    }
                }) { Text(t("Replace data"), fontSize = 18.sp) }
            },
            dismissButton = { TextButton({ restoreInfo = null; restoreUri = null }) { Text(t("Cancel"), fontSize = 18.sp) } }
        )
    }
    if (pinDlg) PinSetupDialog(vm) { pinDlg = false }
}

@Composable
fun PinSetupDialog(vm: AppViewModel, onDismiss: () -> Unit) {
    var pin by remember { mutableStateOf("") }
    val on by vm.pinEnabled.collectAsState()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(t("PIN lock"), fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Sub(t("The PIN is kept on this phone only."))
                OutlinedTextField(
                    pin, { pin = it.filter(Char::isDigit).take(8) }, label = { Text(t("New PIN (4-8 digits)")) },
                    visualTransformation = PasswordVisualTransformation(), singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword)
                )
            }
        },
        confirmButton = { Button({ vm.setPin(pin); onDismiss() }, enabled = pin.length >= 4) { Text(t("Save"), fontSize = 18.sp) } },
        dismissButton = {
            Row {
                if (on) TextButton({ vm.setPin(null); onDismiss() }) { Text(t("Remove PIN"), color = Red, fontSize = 17.sp) }
                TextButton(onDismiss) { Text(t("Cancel"), fontSize = 18.sp) }
            }
        }
    )
}

@Composable
fun LockScreen(vm: AppViewModel) {
    var pin by remember { mutableStateOf("") }
    var wrong by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text("🔒", fontSize = 56.sp)
        Spacer(Modifier.height(12.dp))
        Text(t("Enter PIN"), fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            pin, { pin = it.filter(Char::isDigit).take(8); wrong = false }, singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 28.sp), isError = wrong
        )
        if (wrong) Text(t("Wrong PIN"), color = Red)
        Spacer(Modifier.height(16.dp))
        BigButton(t("Unlock"), { if (!vm.tryUnlock(pin)) { wrong = true; pin = "" } })
    }
}

@Composable
fun ArchiveSection(vm: AppViewModel, s: Snapshot) {
    val rows = mutableListOf<Pair<String, () -> Unit>>()
    s.buildings.filter { it.archived }.forEach { b -> rows.add("🏢 ${b.name}" to { vm.save(b.copy(archived = false)) }) }
    s.floors.filter { it.archived }.forEach { f -> rows.add("▤ ${f.name} (${s.buildingById[f.buildingId]?.name ?: ""})" to { vm.save(f.copy(archived = false)) }) }
    s.units.filter { it.archived }.forEach { u -> rows.add("🏠 ${u.name}" to { vm.save(u.copy(archived = false)) }) }
    s.people.filter { it.archived }.forEach { p -> rows.add("👤 ${p.name}" to { vm.save(p.copy(archived = false)) }) }
    s.tenants.filter { it.archived }.forEach { tn -> rows.add("👥 ${tn.name}" to { vm.save(tn.copy(archived = false)) }) }
    CardBox {
        if (rows.isEmpty()) Sub(t("Nothing archived."))
        rows.forEach { (label, restore) ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(label, Modifier.weight(1f)); TextButton(restore) { Text(t("Restore"), fontSize = 17.sp) }
            }
        }
        Sub(t("Archived items keep all their history. Archive an item from its Edit screen."))
    }
}

package app.family.ledger

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import app.family.ledger.data.Snapshot
import app.family.ledger.ui.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { App() }
    }
}

@Composable
fun App(vm: AppViewModel = viewModel()) {
    val s by vm.snap.collectAsState()
    val unlocked by vm.unlocked.collectAsState()
    val lang = s?.setting("lang", "ar") ?: "ar"
    val dark = s?.setting("theme", "light") == "dark"
    AppTheme(dark, lang) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            val snap = s
            when {
                !unlocked -> LockScreen(vm)
                snap == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                else -> Shell(vm, snap)
            }
        }
    }
}

private val TABS = listOf(
    Triple("🏠", "Home", Route.Home), Triple("🔑", "Rentals", Route.Rentals), Triple("💰", "Rent", Route.Rent),
    Triple("💵", "For Others", Route.Others), Triple("☰", "More", Route.More)
)

@Composable
fun titleOf(r: Route, s: Snapshot): String = when (r) {
    Route.Home -> t("Home"); Route.Rentals -> t("My Rentals"); Route.Rent -> t("Rent"); Route.Others -> t("Collected for Others")
    Route.More -> t("More"); Route.Debts -> t("Debts"); Route.Maintenance -> t("Maintenance"); Route.Expenses -> t("Expenses")
    Route.Reports -> t("Reports"); Route.Buildings -> t("Buildings"); Route.FloorPlan -> t("Floor Plan"); Route.Settings -> t("Settings")
    Route.Search -> t("Search"); Route.Tenants -> t("Tenants")
    is Route.UnitR -> s.unitById[r.id]?.name ?: ""
    is Route.TenantR -> s.tenantById[r.id]?.name ?: ""
    is Route.BuildingR -> s.buildingById[r.id]?.name ?: ""
    is Route.IssueR -> t("Maintenance")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Shell(vm: AppViewModel, s: Snapshot) {
    val stack = remember { mutableStateListOf<Route>(Route.Home) }
    val nav = remember { Nav(stack) }
    val current = stack.last()
    val lang = LocalLang.current
    BackHandler(enabled = stack.size > 1) { nav.pop() }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.refreshObligations() }

    Scaffold(
        topBar = {
            Row(
                Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.primary).windowInsetsPadding(WindowInsets.statusBars)
                    .padding(horizontal = 8.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (stack.size > 1) TextButton({ nav.pop() }) { Text(if (lang == "ar") "→" else "←", color = Color.White, fontSize = 28.sp) }
                else Spacer(Modifier.width(8.dp))
                Text(titleOf(current, s), color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), maxLines = 1)
                TextButton({ nav.push(Route.Search) }) { Text("🔍", fontSize = 22.sp) }
            }
        },
        bottomBar = {
            NavigationBar {
                TABS.forEach { (icon, label, route) ->
                    NavigationBarItem(
                        selected = stack.first() == route,
                        onClick = { stack.clear(); stack.add(route) },
                        icon = { Text(icon, fontSize = 24.sp) },
                        label = { Text(t(label), fontSize = 13.sp, maxLines = 1) }
                    )
                }
            }
        }
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            when (val r = current) {
                Route.Home -> HomeScreen(vm, s, nav)
                Route.Rentals -> RentalsScreen(vm, s, nav)
                Route.Rent -> RentScreen(vm, s, nav)
                Route.Others -> OthersScreen(vm, s, nav)
                Route.More -> MoreScreen(vm, s, nav)
                Route.Debts -> DebtsScreen(vm, s, nav)
                Route.Maintenance -> MaintenanceScreen(vm, s, nav)
                Route.Expenses -> ExpensesScreen(vm, s, nav)
                Route.Reports -> ReportsScreen(vm, s, nav)
                Route.Buildings -> BuildingsScreen(vm, s, nav)
                Route.FloorPlan -> FloorPlanScreen(vm, s, nav)
                Route.Settings -> SettingsScreen(vm, s, nav)
                Route.Search -> SearchScreen(vm, s, nav)
                Route.Tenants -> TenantsScreen(vm, s, nav)
                is Route.UnitR -> UnitScreen(vm, s, nav, r.id)
                is Route.TenantR -> TenantScreen(vm, s, nav, r.id)
                is Route.BuildingR -> BuildingScreen(vm, s, nav, r.id)
                is Route.IssueR -> IssueScreen(vm, s, nav, r.id)
            }
        }
    }
}

package app.family.ledger.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.family.ledger.AppViewModel
import app.family.ledger.data.*
import java.io.File

val LocalLang = staticCompositionLocalOf { "ar" }

/** Translate an English UI key. Text typed by the user (names, notes...) is never translated. */
@Composable
fun t(s: String): String = tr(LocalLang.current, s)

fun tr(lang: String, s: String): String = if (lang == "ar") ARABIC[s] ?: s else s

private val LightColors = lightColorScheme(
    primary = Color(0xFF1F5F5B), onPrimary = Color.White,
    background = Color(0xFFF6F1E7), onBackground = Color(0xFF2B2418),
    surface = Color.White, onSurface = Color(0xFF2B2418),
    surfaceVariant = Color(0xFFEEF3F2), onSurfaceVariant = Color(0xFF6B6150),
    outlineVariant = Color(0xFFE4DCCB), secondaryContainer = Color(0xFFCDE6E3)
)
private val DarkColors = darkColorScheme(
    primary = Color(0xFF2A8A84), onPrimary = Color.White,
    background = Color(0xFF121618), onBackground = Color(0xFFECEBE6),
    surface = Color(0xFF1D2427), onSurface = Color(0xFFECEBE6),
    surfaceVariant = Color(0xFF26323A), onSurfaceVariant = Color(0xFFA1ABAE),
    outlineVariant = Color(0xFF2E383C), secondaryContainer = Color(0xFF1F4B48)
)

@Composable
fun AppTheme(dark: Boolean, lang: String, content: @Composable () -> Unit) {
    val base = Typography()
    val big = base.copy(
        bodyLarge = TextStyle(fontSize = 18.sp), bodyMedium = TextStyle(fontSize = 17.sp),
        titleMedium = TextStyle(fontSize = 19.sp), labelLarge = TextStyle(fontSize = 17.sp)
    )
    CompositionLocalProvider(
        LocalLang provides lang,
        LocalDark provides dark,
        LocalLayoutDirection provides if (lang == "ar") LayoutDirection.Rtl else LayoutDirection.Ltr
    ) {
        MaterialTheme(colorScheme = if (dark) DarkColors else LightColors, typography = big, content = content)
    }
}

/** Photo strip with "add from gallery" and "take photo". Everything is stored on the phone only. */
@Composable
fun PhotoSection(vm: AppViewModel, snap: Snapshot, ownerType: String, ownerId: String) {
    val ctx = LocalContext.current
    val photos = snap.photos.filter { it.ownerType == ownerType && it.ownerId == ownerId }
    var viewing by remember { mutableStateOf<Photo?>(null) }
    var pending by remember { mutableStateOf<Uri?>(null) }

    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(10)) { uris ->
        uris.forEach { vm.addPhoto(it, ownerType, ownerId) }
    }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        if (ok) pending?.let { vm.addPhoto(it, ownerType, ownerId) }
    }

    if (photos.isEmpty()) Sub("📷 " + t("No photos yet"))
    else LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(photos, key = { it.id }) { p -> Thumb(p.path, 96) { viewing = p } }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        OutlinedButton(
            { pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
            Modifier.weight(1f).heightIn(min = 52.dp)
        ) { Text("🖼 " + t("Gallery"), fontSize = 17.sp) }
        OutlinedButton({
            val f = File(File(ctx.cacheDir, "camera").apply { mkdirs() }, "${newId()}.jpg")
            val uri = Backup.shareUri(ctx, f)
            pending = uri
            camera.launch(uri)
        }, Modifier.weight(1f).heightIn(min = 52.dp)) { Text("📷 " + t("Camera"), fontSize = 17.sp) }
    }
    viewing?.let { p -> FullPhoto(p.path, { viewing = null }, { vm.deletePhoto(p) }) }
}

@Composable
fun AvatarEditor(vm: AppViewModel, tenant: Tenant) {
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) vm.addPhoto(uri, "tenants", tenant.id, asTenantAvatar = true)
    }
    val ctx = LocalContext.current
    var pending by remember { mutableStateOf<Uri?>(null) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        if (ok) pending?.let { vm.addPhoto(it, "tenants", tenant.id, asTenantAvatar = true) }
    }
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Avatar(tenant.photoPath, tenant.name, 96)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton({ pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) { Text("🖼 " + t("Gallery"), fontSize = 16.sp) }
            OutlinedButton({
                val f = File(File(ctx.cacheDir, "camera").apply { mkdirs() }, "${newId()}.jpg")
                pending = Backup.shareUri(ctx, f); camera.launch(pending!!)
            }) { Text("📷 " + t("Camera"), fontSize = 16.sp) }
        }
    }
}

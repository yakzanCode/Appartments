package app.family.ledger.ui

import android.graphics.Bitmap
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.family.ledger.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

val Green = Color(0xFF2E7D4F)
val Red = Color(0xFFC0392B)
val Amber = Color(0xFFD98E04)
val Blue = Color(0xFF2F6690)
val Gray = Color(0xFF8D8677)

@Composable fun GreenC() = if (LocalDark.current) Color(0xFF4FC07F) else Green
@Composable fun RedC() = if (LocalDark.current) Color(0xFFEF6F60) else Red
@Composable fun AmberC() = if (LocalDark.current) Color(0xFFF0B73C) else Amber

val LocalDark = staticCompositionLocalOf { false }

@Composable
fun statusColor(key: String): Color = when (key) {
    "Paid", "Completed", "Family" -> GreenC()
    "Partially paid", "Vacant", "In Progress" -> AmberC()
    "Unpaid", "Overdue", "Open", "Maintenance" -> RedC()
    "Rented" -> Blue
    else -> Gray
}

@Composable
fun statusIcon(key: String): String = when (key) {
    "Paid" -> "✅"; "Partially paid" -> "◑"; "Unpaid" -> "⚠️"; "Overdue" -> "⏰"
    "Rented" -> "🔑"; "Vacant" -> "⬜"; "Family" -> "👨‍👩‍👧"; "Not ours" -> "🚫"; "Maintenance" -> "🔧"
    "Open" -> "🔴"; "In Progress" -> "🟠"; "Completed" -> "🟢"; "Cancelled" -> "⚪"
    else -> ""
}

@Composable
fun Tag(key: String, modifier: Modifier = Modifier) {
    Text(
        "${statusIcon(key)} ${t(key)}".trim(),
        color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold,
        modifier = modifier.clip(RoundedCornerShape(20.dp)).background(statusColor(key)).padding(horizontal = 10.dp, vertical = 4.dp)
    )
}

@Composable
fun Page(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        content = content
    )
}

@Composable
fun CardBox(onClick: (() -> Unit)? = null, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val m = modifier.fillMaxWidth()
    Card(
        modifier = if (onClick != null) m.clickable { onClick() } else m,
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) { Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp), content = content) }
}

@Composable
fun Sub(text: String, modifier: Modifier = Modifier) =
    Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 15.sp, modifier = modifier)

@Composable
fun H2(text: String) = Text(text, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))

@Composable
fun BigButton(text: String, onClick: () -> Unit, outlined: Boolean = false, modifier: Modifier = Modifier) {
    if (outlined) OutlinedButton(onClick, modifier.fillMaxWidth().heightIn(min = 52.dp), shape = RoundedCornerShape(12.dp)) {
        Text(text, fontSize = 18.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
    } else Button(onClick, modifier.fillMaxWidth().heightIn(min = 52.dp), shape = RoundedCornerShape(12.dp)) {
        Text(text, fontSize = 18.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
    }
}

@Composable
fun RowKV(k: String, v: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(k, color = MaterialTheme.colorScheme.onSurfaceVariant)
        v()
    }
}

@Composable fun RowKV(k: String, v: String) = RowKV(k) { Text(v, fontWeight = FontWeight.Bold) }

/** A money map shown on one or more lines, one per currency. */
@Composable
fun MoneyText(m: Money, color: Color = Color.Unspecified, size: Int = 20, zero: String = "0") {
    val nz = m.nonZero()
    if (nz.isEmpty()) Text(zero, fontSize = size.sp, fontWeight = FontWeight.ExtraBold, color = color)
    else Column(horizontalAlignment = Alignment.Start) {
        nz.entries.sortedBy { it.key }.forEach { Text(fmtMoney(it.value, it.key), fontSize = size.sp, fontWeight = FontWeight.ExtraBold, color = color) }
    }
}

@Composable
fun StatCard(label: String, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, value: @Composable () -> Unit) {
    CardBox(onClick, modifier) { value(); Sub(label) }
}

@Composable
fun StatGrid(items: List<@Composable (Modifier) -> Unit>) {
    items.chunked(2).forEach { pair ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            pair.forEach { it(Modifier.weight(1f)) }
            if (pair.size == 1) Spacer(Modifier.weight(1f))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChipRow(options: List<Pair<String, String>>, selected: String?, onSelect: (String) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        options.forEach { (key, label) ->
            FilterChip(selected = key == selected, onClick = { onSelect(key) }, label = { Text(label, fontSize = 16.sp) })
        }
    }
}

@Composable
fun TField(
    label: String, value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier,
    number: Boolean = false, phone: Boolean = false, lines: Int = 1
) {
    OutlinedTextField(
        value = value, onValueChange = onChange, label = { Text(label) },
        modifier = modifier.fillMaxWidth(), singleLine = lines == 1, minLines = lines,
        keyboardOptions = KeyboardOptions(keyboardType = if (number) KeyboardType.Decimal else if (phone) KeyboardType.Phone else KeyboardType.Text),
        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 18.sp)
    )
}

/** Dropdown picker. [onAdd] shows a "+ Add new" entry. */
@Composable
fun Picker(
    label: String, options: List<Pair<String?, String>>, value: String?, onChange: (String?) -> Unit,
    onAdd: (() -> Unit)? = null
) {
    var open by remember { mutableStateOf(false) }
    val shown = options.firstOrNull { it.first == value }?.second ?: ""
    Box(Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = shown, onValueChange = {}, readOnly = true, label = { Text(label) },
            modifier = Modifier.fillMaxWidth(), singleLine = true,
            trailingIcon = { Text("▾", fontSize = 20.sp) },
            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 18.sp)
        )
        Box(Modifier.matchParentSize().clickable { open = true })
        DropdownMenu(open, { open = false }) {
            options.forEach { (k, l) -> DropdownMenuItem(text = { Text(l, fontSize = 18.sp) }, onClick = { onChange(k); open = false }) }
            if (onAdd != null) DropdownMenuItem(
                text = { Text("＋ " + t("Add new"), fontSize = 18.sp, fontWeight = FontWeight.Bold) },
                onClick = { open = false; onAdd() })
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateField(label: String, value: String, onChange: (String) -> Unit) {
    var show by remember { mutableStateOf(false) }
    OutlinedButton({ show = true }, Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
        Text("$label: ${value.ifBlank { "—" }}", fontSize = 17.sp)
    }
    if (show) {
        val initial = runCatching { java.time.LocalDate.parse(value).atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli() }.getOrNull()
        val st = rememberDatePickerState(initialSelectedDateMillis = initial)
        DatePickerDialog(
            onDismissRequest = { show = false },
            confirmButton = {
                TextButton({
                    st.selectedDateMillis?.let { onChange(java.time.Instant.ofEpochMilli(it).atZone(java.time.ZoneOffset.UTC).toLocalDate().toString()) }
                    show = false
                }) { Text(t("OK")) }
            },
            dismissButton = { TextButton({ show = false }) { Text(t("Cancel")) } }
        ) { DatePicker(st) }
    }
}

@Composable
fun FormDialog(title: String, onDismiss: () -> Unit, saveEnabled: Boolean = true, onSave: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp), content = content) },
        confirmButton = { Button(onClick = { onSave(); onDismiss() }, enabled = saveEnabled) { Text(t("Save"), fontSize = 18.sp) } },
        dismissButton = { TextButton(onDismiss) { Text(t("Cancel"), fontSize = 18.sp) } }
    )
}

@Composable
fun ConfirmDialog(title: String, text: String, confirm: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = { Text(text, fontSize = 17.sp) },
        confirmButton = { Button({ onConfirm(); onDismiss() }) { Text(confirm, fontSize = 18.sp) } },
        dismissButton = { TextButton(onDismiss) { Text(t("Cancel"), fontSize = 18.sp) } }
    )
}

@Composable
fun AddOptionDialog(title: String, onDismiss: () -> Unit, onAdd: (String) -> Unit) {
    var v by remember { mutableStateOf("") }
    FormDialog(title, onDismiss, saveEnabled = v.isNotBlank(), onSave = { onAdd(v.trim()) }) { TField(t("Name"), v, { v = it }) }
}

@Composable
fun Avatar(path: String, name: String, size: Int = 46) {
    val ctx = LocalContext.current
    val bmp by produceState<Bitmap?>(null, path) {
        value = if (path.isBlank()) null else withContext(Dispatchers.IO) { PhotoStore.thumb(ctx, path, size * 3) }
    }
    Box(
        Modifier.size(size.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary),
        contentAlignment = Alignment.Center
    ) {
        if (bmp != null) Image(bmp!!.asImageBitmap(), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        else Text(name.trim().take(1).ifBlank { "?" }, color = Color.White, fontSize = (size / 2).sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun Thumb(path: String, size: Int, onClick: () -> Unit) {
    val ctx = LocalContext.current
    val bmp by produceState<Bitmap?>(null, path) { value = withContext(Dispatchers.IO) { PhotoStore.thumb(ctx, path, size * 2) } }
    Box(Modifier.size(size.dp).clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surfaceVariant).clickable { onClick() }, contentAlignment = Alignment.Center) {
        if (bmp != null) Image(bmp!!.asImageBitmap(), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) else Text("📷")
    }
}

@Composable
fun FullPhoto(path: String, onDismiss: () -> Unit, onDelete: (() -> Unit)?) {
    val ctx = LocalContext.current
    val bmp by produceState<Bitmap?>(null, path) { value = withContext(Dispatchers.IO) { PhotoStore.thumb(ctx, path, 1400) } }
    AlertDialog(
        onDismissRequest = onDismiss,
        text = { if (bmp != null) Image(bmp!!.asImageBitmap(), null, modifier = Modifier.fillMaxWidth(), contentScale = ContentScale.Fit) },
        confirmButton = { TextButton(onDismiss) { Text(t("Close"), fontSize = 18.sp) } },
        dismissButton = { if (onDelete != null) TextButton({ onDelete(); onDismiss() }) { Text(t("Delete photo"), color = Red, fontSize = 18.sp) } }
    )
}

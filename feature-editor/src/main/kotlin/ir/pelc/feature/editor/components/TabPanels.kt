package ir.pelc.feature.editor.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ir.pelc.core.model.Layer
import ir.pelc.core.model.MarkSpec
import ir.pelc.core.model.MarkType
import ir.pelc.core.model.PelcFont
import ir.pelc.core.model.canKashidaBefore
import ir.pelc.feature.editor.LetterUi

// ---------------------------------------------------------------------------
// Layer panel
// ---------------------------------------------------------------------------

@Composable
fun LayerPanel(
    layers: List<Layer>,
    selectedId: String?,
    onSelect: (String) -> Unit,
    onVisibilityToggle: () -> Unit,
    onOpacity: (Float) -> Unit,
    onDelete: () -> Unit,
    onAddText: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val selected = layers.firstOrNull { it.id == selectedId }
    Column(modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("لایه‌ها", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            Button(onClick = onAddText) { Text("افزودن متن") }
        }
        Spacer(Modifier.height(8.dp))

        LazyColumn(modifier = Modifier.fillMaxWidth().height(120.dp)) {
            items(layers, key = { it.id }) { layer ->
                val isSelected = layer.id == selectedId
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            if (isSelected) MaterialTheme.colorScheme.primaryContainer
                            else Color.Transparent,
                            RoundedCornerShape(8.dp),
                        )
                        .clickable { onSelect(layer.id) }
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                ) {
                    Text(layer.name, modifier = Modifier.weight(1f))
                    Checkbox(checked = layer.visible, onCheckedChange = { onVisibilityToggle() })
                }
            }
        }

        selected?.let {
            Text("شفافیت: ${(it.opacity * 100).toInt()}%", style = MaterialTheme.typography.bodySmall)
            Slider(
                value = it.opacity,
                onValueChange = onOpacity,
                valueRange = 0f..1f,
            )
            OutlinedButton(onClick = onDelete, enabled = selectedId != null) {
                Text("حذف لایه", color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Word panel
// ---------------------------------------------------------------------------

@Composable
fun WordPanel(
    layer: Layer?,
    fonts: List<PelcFont>,
    onText: (String) -> Unit,
    onFont: (String) -> Unit,
    onSize: (Float) -> Unit,
    onColor: (Long) -> Unit,
    onImportFont: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val content = layer?.text
    if (content == null) {
        PanelHint("برای ویرایش واژه، اول یک لایهٔ متن بساز یا یک لایه را انتخاب کن.", modifier)
        return
    }
    Column(modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        OutlinedTextField(
            value = content.text,
            onValueChange = onText,
            label = { Text("متن واژه") },
            modifier = Modifier.fillMaxWidth(),
            maxLines = 3,
        )
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            FontDropdown(fonts, content.fontId, onFont, Modifier.weight(1f))
            Spacer(Modifier.width(8.dp))
            OutlinedButton(onClick = onImportFont) { Text("قلم تازه") }
        }
        Text("اندازه قلم: ${content.fontSize.toInt()}", style = MaterialTheme.typography.bodySmall)
        Slider(
            value = content.fontSize,
            onValueChange = onSize,
            valueRange = 40f..800f,
        )
        ColorRow(content.color, onColor)
    }
}

// ---------------------------------------------------------------------------
// Letter panel (per-letter variants, fonts, kashida and marks)
// ---------------------------------------------------------------------------

private const val NUDGE_STEP = 4f
private const val SCALE_UP = 1.1f
private const val SCALE_DOWN = 0.9f

@Composable
fun LetterPanel(
    layer: Layer?,
    letters: List<LetterUi>,
    selectedLetter: Int?,
    variants: List<String>,
    fonts: List<PelcFont>,
    onSelectLetter: (Int) -> Unit,
    onVariant: (String?) -> Unit,
    onLetterFont: (String?) -> Unit,
    onResetLetter: () -> Unit,
    onAddKashida: (Int) -> Unit,
    onRemoveKashida: (Int) -> Unit,
    onAddMark: (MarkType) -> Unit,
    onRemoveMark: (Int) -> Unit,
    onNudgeMark: (Int, Float, Float) -> Unit,
    onScaleMark: (Int, Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val content = layer?.text
    if (content == null) {
        PanelHint("برای ویرایش حروف، اول یک لایهٔ متن انتخاب کن.", modifier)
        return
    }
    val cluster = selectedLetter?.let { s -> letters.firstOrNull { it.start == s } }
    val override = selectedLetter?.let { content.perLetter[it] }

    Column(modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text("حرف موردنظر را انتخاب کن:", style = MaterialTheme.typography.titleSmall)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(letters, key = { it.start }) { letter ->
                val isSelected = letter.start == selectedLetter
                FilterChip(
                    selected = isSelected,
                    onClick = { onSelectLetter(letter.start) },
                    label = { Text(letter.label) },
                )
            }
        }
        Spacer(Modifier.height(8.dp))

        if (cluster == null) {
            Text(
                "برای کشیده و علامت‌ها، اول یک حرف انتخاب کن.",
                style = MaterialTheme.typography.bodySmall,
            )
        } else {
            KashidaSection(
                count = cluster.kashidaCount,
                canAdd = canKashidaBefore(content.text, cluster.start),
                onAdd = { onAddKashida(cluster.start) },
                onRemove = { onRemoveKashida(cluster.start) },
            )
            Spacer(Modifier.height(8.dp))
            MarksSection(
                marks = override?.marks.orEmpty(),
                onAddMark = onAddMark,
                onRemoveMark = onRemoveMark,
                onNudgeMark = onNudgeMark,
                onScaleMark = onScaleMark,
            )
            Spacer(Modifier.height(8.dp))
        }

        Text("گونه‌های حرف", style = MaterialTheme.typography.titleSmall)
        if (variants.isEmpty()) {
            Text("برای این قلم گونه‌ای یافت نشد.", style = MaterialTheme.typography.bodySmall)
        } else {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(variants) { variant ->
                    FilterChip(
                        selected = override?.variantTag == variant,
                        onClick = { onVariant(if (override?.variantTag == variant) null else variant) },
                        label = { Text(variant) },
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            FontDropdown(
                fonts = fonts,
                selectedId = override?.fontId ?: content.fontId,
                onSelect = { onLetterFont(it) },
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            OutlinedButton(onClick = { onLetterFont(null) }, enabled = override?.fontId != null) {
                Text("قلم واژه")
            }
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onResetLetter, enabled = override != null) {
            Text("بازنشانی این حرف")
        }
    }
}

// ---------------------------------------------------------------------------
// کشیده (تطویل)
// ---------------------------------------------------------------------------

@Composable
private fun KashidaSection(
    count: Int,
    canAdd: Boolean,
    onAdd: () -> Unit,
    onRemove: () -> Unit,
) {
    Text("کشیده (تطویل)", style = MaterialTheme.typography.titleSmall)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            "تعداد پیش از این حرف: $count",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onRemove, enabled = count > 0) {
            Icon(Icons.Filled.Remove, contentDescription = "حذف یک کشیده")
        }
        IconButton(onClick = onAdd, enabled = canAdd) {
            Icon(Icons.Filled.Add, contentDescription = "افزودن کشیده")
        }
    }
    if (!canAdd && count == 0) {
        Text(
            "در این نقطه اتصالی نیست؛ کشیده فقط میان دو حرفِ متصل معنا دارد (لام‌الف و نیم‌فاصله اجازه نمی‌دهند).",
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

// ---------------------------------------------------------------------------
// علامت‌ها (فتحه، ضمه، کسره، تشدید، سکون، تنوین‌ها)
// ---------------------------------------------------------------------------

@Composable
private fun MarksSection(
    marks: List<MarkSpec>,
    onAddMark: (MarkType) -> Unit,
    onRemoveMark: (Int) -> Unit,
    onNudgeMark: (Int, Float, Float) -> Unit,
    onScaleMark: (Int, Float) -> Unit,
) {
    Text("علامت‌ها", style = MaterialTheme.typography.titleSmall)
    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        items(MarkType.entries) { type ->
            FilterChip(
                selected = false,
                onClick = { onAddMark(type) },
                label = { Text("+ ${type.title}") },
            )
        }
    }
    if (marks.isEmpty()) {
        Text(
            "برای این حرف علامتی نیست؛ از چیپ‌های بالا اضافه کن.",
            style = MaterialTheme.typography.bodySmall,
        )
    } else {
        marks.forEachIndexed { index, mark ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${index + 1}. ${mark.type.title}",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { onNudgeMark(index, -NUDGE_STEP, 0f) }) {
                    Icon(Icons.Filled.KeyboardArrowLeft, contentDescription = "علامت چپ‌تر")
                }
                IconButton(onClick = { onNudgeMark(index, NUDGE_STEP, 0f) }) {
                    Icon(Icons.Filled.KeyboardArrowRight, contentDescription = "علامت راست‌تر")
                }
                IconButton(onClick = { onNudgeMark(index, 0f, -NUDGE_STEP) }) {
                    Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "علامت بالاتر")
                }
                IconButton(onClick = { onNudgeMark(index, 0f, NUDGE_STEP) }) {
                    Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "علامت پایین‌تر")
                }
                IconButton(onClick = { onScaleMark(index, SCALE_DOWN) }) {
                    Icon(Icons.Filled.Remove, contentDescription = "علامت کوچک‌تر")
                }
                IconButton(onClick = { onScaleMark(index, SCALE_UP) }) {
                    Icon(Icons.Filled.Add, contentDescription = "علامت بزرگ‌تر")
                }
                IconButton(onClick = { onRemoveMark(index) }) {
                    Icon(Icons.Filled.Close, contentDescription = "حذف علامت")
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Shared components
// ---------------------------------------------------------------------------

@Composable
fun FontDropdown(
    fonts: List<PelcFont>,
    selectedId: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val selected = fonts.firstOrNull { it.id == selectedId }

    Box(modifier = modifier) {
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
            Text(selected?.displayName ?: "انتخاب قلم")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            if (fonts.isEmpty()) {
                DropdownMenuItem(text = { Text("قلمی موجود نیست — از «قلم تازه» وارد کنید") }, onClick = {})
            }
            fonts.forEach { font ->
                DropdownMenuItem(
                    text = { Text("${font.displayName} — ${font.style}") },
                    onClick = {
                        expanded = false
                        onSelect(font.id)
                    },
                )
            }
        }
    }
}

private val PRESET_COLORS = listOf(
    0xFF1A1A1AL to "مشکی",
    0xFFD4AF37L to "طلایی",
    0xFF101418L to "مرکبی",
    0xFFFFFFFFL to "سفید",
    0xFF8E2A2AL to "شنگرف",
    0xFF1E5A8EL to "لاجوردی",
    0xFF2E6B4FL to "زنگاری",
)

@Composable
fun ColorRow(selected: Long, onSelect: (Long) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text("رنگ", style = MaterialTheme.typography.titleSmall)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(PRESET_COLORS) { (color, name) ->
                val isSelected = color == selected
                Box(
                    modifier = Modifier
                        .padding(vertical = 4.dp)
                        .background(Color(color), RoundedCornerShape(10.dp))
                        .border(
                            width = if (isSelected) 2.dp else 1.dp,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Gray,
                            shape = RoundedCornerShape(10.dp),
                        )
                        .clickable { onSelect(color) }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    Text(name, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
private fun PanelHint(message: String, modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
        Text(message, style = MaterialTheme.typography.bodyMedium)
    }
}

// ---------------------------------------------------------------------------
// Add-text dialog
// ---------------------------------------------------------------------------

@Composable
fun AddTextDialog(
    onAdd: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("افزودن متن") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text("واژه یا عبارت را بنویسید…") },
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onAdd(text)
                    onDismiss()
                },
                enabled = text.isNotBlank(),
            ) { Text("افزودن") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("انصراف") }
        },
    )
}

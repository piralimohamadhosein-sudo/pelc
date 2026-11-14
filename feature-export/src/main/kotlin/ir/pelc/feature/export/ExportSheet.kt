package ir.pelc.feature.export

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ir.pelc.core.export.ExportFormat
import ir.pelc.core.export.ExportOptions
import ir.pelc.core.model.PelcDocument

/**
 * The product's save flow: when the user finishes, the app asks which format
 * they want; the editable .pelc project is always written alongside.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportSheet(
    document: PelcDocument,
    onDismiss: () -> Unit,
    viewModel: ExportViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    var selectedFormat by remember { mutableStateOf(ExportFormat.PNG) }
    var scale by remember { mutableStateOf(2f) }
    var quality by remember { mutableStateOf(92) }
    var transparent by remember { mutableStateOf(false) }

    val formats = listOf(
        ExportFormat.PNG to stringResource(R.string.format_png),
        ExportFormat.JPG to stringResource(R.string.format_jpg),
        ExportFormat.SVG to stringResource(R.string.format_svg),
        ExportFormat.PDF to stringResource(R.string.format_pdf),
        ExportFormat.DXF to stringResource(R.string.format_dxf),
        ExportFormat.PSD to stringResource(R.string.format_psd),
        ExportFormat.PELC to stringResource(R.string.format_pelc),
    )

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text(stringResource(R.string.export_sheet_title), style = MaterialTheme.typography.titleLarge)

            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth().height(240.dp).padding(vertical = 12.dp),
            ) {
                items(formats) { (format, label) ->
                    Card(
                        onClick = { selectedFormat = format },
                        colors = CardDefaults.cardColors(
                            containerColor = if (format == selectedFormat) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            },
                        ),
                    ) {
                        Text(label, Modifier.padding(12.dp), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            Text(stringResource(R.string.export_scale) + ": ${scale.toInt()}x")
            Slider(value = scale, onValueChange = { scale = it }, valueRange = 1f..4f, steps = 2)

            if (selectedFormat == ExportFormat.JPG) {
                Text(stringResource(R.string.export_quality) + ": $quality")
                Slider(value = quality.toFloat(), onValueChange = { quality = it.toInt() }, valueRange = 60f..100f)
            }

            if (selectedFormat == ExportFormat.PNG) {
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    FilterChip(
                        selected = transparent,
                        onClick = { transparent = !transparent },
                        label = { Text("پس‌زمینهٔ شفاف") },
                    )
                }
            }

            Text(
                stringResource(R.string.export_hint_pelc),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(vertical = 8.dp),
            )

            OutlinedButton(
                onClick = {
                    viewModel.export(
                        document = document,
                        format = selectedFormat,
                        options = ExportOptions(
                            scale = scale,
                            jpgQuality = quality,
                            transparentPng = transparent,
                        ),
                    )
                },
                enabled = state !is ExportState.Running,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.export_now))
            }
        }
    }

    when (val current = state) {
        is ExportState.Running -> {
            AlertDialog(
                onDismissRequest = {},
                title = { Text(stringResource(R.string.export_running)) },
                text = { CircularProgressIndicator(Modifier.width(48.dp)) },
                confirmButton = {},
            )
        }
        is ExportState.Done -> {
            ResultDialog(message = current.files, onDismiss = { viewModel.reset(); onDismiss() })
        }
        is ExportState.Error -> {
            val context = LocalContext.current
            ResultDialog(
                message = context.getString(R.string.export_failed, current.message),
                onDismiss = { viewModel.reset(); onDismiss() },
            )
        }
        ExportState.Idle -> Unit
    }
}

@Composable
private fun ResultDialog(message: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(message, style = MaterialTheme.typography.bodyLarge) },
        text = {
            TextButton(
                onClick = {
                    val first = message.split("\n").firstOrNull() ?: return@TextButton
                    val uri = androidx.core.content.FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        java.io.File(first),
                    )
                    val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                        type = "*/*"
                        putExtra(android.content.Intent.EXTRA_STREAM, uri)
                        addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(android.content.Intent.createChooser(intent, null))
                },
            ) { Text(stringResource(R.string.export_share)) }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.export_close)) }
        },
    )
}

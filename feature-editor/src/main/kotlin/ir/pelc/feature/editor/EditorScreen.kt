package ir.pelc.feature.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Redo
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.navigation.compose.hiltViewModel
import ir.pelc.core.model.PelcDocument
import ir.pelc.feature.editor.components.AddTextDialog
import ir.pelc.feature.editor.components.EditorCanvas
import ir.pelc.feature.editor.components.LetterPanel
import ir.pelc.feature.editor.components.LayerPanel
import ir.pelc.feature.editor.components.WordPanel

private const val TAB_LAYER = 0
private const val TAB_WORD = 1
private const val TAB_LETTER = 2

/**
 * Main editor screen: canvas on top, three-level editing bar at the bottom
 * (لایه / واژه / حرف), matching the product's editing model.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    onExport: (PelcDocument) -> Unit,
    viewModel: EditorViewModel = hiltViewModel(),
) {
    val document by viewModel.document.collectAsStateWithLifecycle()
    val preview by viewModel.preview.collectAsStateWithLifecycle()
    val selectedId by viewModel.selectedLayerId.collectAsStateWithLifecycle()
    val fonts by viewModel.availableFonts.collectAsStateWithLifecycle()
    val variants by viewModel.variants.collectAsStateWithLifecycle()
    val letters by viewModel.letters.collectAsStateWithLifecycle()
    val selectedLetter by viewModel.selectedLetter.collectAsStateWithLifecycle()
    val canUndo by viewModel.canUndo.collectAsStateWithLifecycle()
    val canRedo by viewModel.canRedo.collectAsStateWithLifecycle()

    var tab by rememberSaveable { mutableIntStateOf(TAB_LAYER) }
    var showAddDialog by remember { mutableStateOf(false) }

    val fontImportLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocument(),
    ) { uri ->
        uri?.let { viewModel.importFont(it) }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(ir.pelc.feature.editor.R.string.editor_title), fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = viewModel::undo, enabled = canUndo) {
                        Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = stringResource(ir.pelc.feature.editor.R.string.undo))
                    }
                    IconButton(onClick = viewModel::redo, enabled = canRedo) {
                        Icon(Icons.Filled.Redo, contentDescription = stringResource(ir.pelc.feature.editor.R.string.redo))
                    }
                    IconButton(onClick = { onExport(document) }) {
                        Icon(Icons.Filled.Save, contentDescription = stringResource(ir.pelc.feature.editor.R.string.export_save))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            EditorCanvas(
                preview = preview,
                docWidth = document.width.toFloat(),
                emptyHint = stringResource(ir.pelc.feature.editor.R.string.empty_canvas_hint),
                onSelect = viewModel::selectAt,
                onDragStart = viewModel::dragStart,
                onDragMove = viewModel::dragMove,
                onDragEnd = viewModel::dragEnd,
                modifier = Modifier.fillMaxWidth().weight(1f),
            )

            TabRow(selectedTabIndex = tab) {
                Tab(tab == TAB_LAYER, onClick = { tab = TAB_LAYER }, text = { Text(stringResource(ir.pelc.feature.editor.R.string.tab_layer)) })
                Tab(tab == TAB_WORD, onClick = { tab = TAB_WORD }, text = { Text(stringResource(ir.pelc.feature.editor.R.string.tab_word)) })
                Tab(tab == TAB_LETTER, onClick = { tab = TAB_LETTER }, text = { Text(stringResource(ir.pelc.feature.editor.R.string.tab_letter)) })
            }

            val selectedLayer = document.findLayer(selectedId)
            when (tab) {
                TAB_LAYER -> LayerPanel(
                    layers = document.layers,
                    selectedId = selectedId,
                    onSelect = viewModel::selectLayer,
                    onVisibilityToggle = viewModel::toggleVisibility,
                    onOpacity = viewModel::updateOpacity,
                    onDelete = viewModel::deleteSelected,
                    onAddText = { showAddDialog = true },
                )
                TAB_WORD -> WordPanel(
                    layer = selectedLayer,
                    fonts = fonts,
                    onText = viewModel::updateText,
                    onFont = viewModel::updateFont,
                    onSize = viewModel::updateFontSize,
                    onColor = viewModel::updateColor,
                    onImportFont = { fontImportLauncher.launch(arrayOf("font/*", "application/octet-stream")) },
                )
                TAB_LETTER -> LetterPanel(
                    layer = selectedLayer,
                    letters = letters,
                    selectedLetter = selectedLetter,
                    variants = variants,
                    fonts = fonts,
                    onSelectLetter = viewModel::selectLetter,
                    onVariant = viewModel::applyVariantToSelectedLetter,
                    onLetterFont = viewModel::setLetterFont,
                    onResetLetter = viewModel::resetSelectedLetter,
                )
            }
        }
    }

    if (showAddDialog) {
        AddTextDialog(
            onAdd = viewModel::addText,
            onDismiss = { showAddDialog = false },
        )
    }
}

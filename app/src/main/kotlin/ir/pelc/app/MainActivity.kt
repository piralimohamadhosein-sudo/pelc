package ir.pelc.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.unit.LayoutDirection
import androidx.hilt.navigation.compose.hiltViewModel
import dagger.hilt.android.AndroidEntryPoint
import ir.pelc.app.ui.theme.PelcTheme
import ir.pelc.feature.editor.EditorScreen
import ir.pelc.feature.editor.EditorViewModel
import ir.pelc.feature.export.ExportSheet

/**
 * Single-activity, Persian-first (RTL) app. The editor is the home screen;
 * the export sheet (format chooser) opens on save.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PelcTheme {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    PelcRoot()
                }
            }
        }
    }
}

@Composable
private fun PelcRoot(editorViewModel: EditorViewModel = hiltViewModel()) {
    var exportOpen by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        EditorScreen(
            onExport = { exportOpen = true },
            viewModel = editorViewModel,
        )
        if (exportOpen) {
            ExportSheet(
                document = editorViewModel.document.value,
                onDismiss = { exportOpen = false },
            )
        }
    }
}

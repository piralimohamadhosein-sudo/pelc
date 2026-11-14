package ir.pelc.feature.editor.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp
import kotlin.math.max

/**
 * Canvas showing the rendered document preview.
 * - Tap: hit-test layer selection (document coordinates).
 * - Drag: move the selected layer (history-aware in the ViewModel).
 */
@Composable
fun EditorCanvas(
    preview: ImageBitmap?,
    docWidth: Float,
    emptyHint: String,
    onSelect: (docX: Float, docY: Float) -> Unit,
    onDragStart: () -> Unit,
    onDragMove: (dxDoc: Float, dyDoc: Float) -> Unit,
    onDragEnd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var canvasWidthPx by remember { mutableIntStateOf(1) }
    var canvasHeightPx by remember { mutableIntStateOf(1) }
    val scaleToDoc = rememberUpdatedState(docWidth / max(canvasWidthPx, 1))

    Box(
        modifier = modifier
            .onSizeChanged { size ->
                canvasWidthPx = max(size.width, 1)
                canvasHeightPx = max(size.height, 1)
            }
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    onSelect(offset.x * scaleToDoc.value, offset.y * scaleToDoc.value)
                }
            }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { onDragStart() },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        onDragMove(dragAmount.x * scaleToDoc.value, dragAmount.y * scaleToDoc.value)
                    },
                    onDragEnd = { onDragEnd() },
                    onDragCancel = { onDragEnd() },
                )
            },
    ) {
        if (preview != null) {
            Image(
                bitmap = preview,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Text(
                text = emptyHint,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(24.dp),
            )
        }
    }
}

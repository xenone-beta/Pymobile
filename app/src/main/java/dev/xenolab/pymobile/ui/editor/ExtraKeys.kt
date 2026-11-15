package dev.xenolab.pymobile.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.KeyboardTab
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.xenolab.pymobile.editor.CompletionResult
import dev.xenolab.pymobile.project.EditorTab

private const val SYMBOLS = "():[]{}\"'=#_,.+-*/\\<>!&|%@"

@Composable
private fun Key(modifier: Modifier = Modifier, onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        modifier
            .defaultMinSize(minWidth = 38.dp)
            .height(38.dp)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center,
    ) { content() }
}

@Composable
private fun IconKey(icon: ImageVector, description: String, onClick: () -> Unit) {
    Key(onClick = onClick) { Icon(icon, description, Modifier.padding(0.dp)) }
}

@Composable
fun ExtraKeys(tab: EditorTab, autoPair: Boolean, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 6.dp, vertical = 5.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        IconKey(Icons.Filled.KeyboardTab, "Tab") { EditorOps.indent(tab) }
        IconKey(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "←") { EditorOps.left(tab) }
        IconKey(Icons.AutoMirrored.Filled.KeyboardArrowRight, "→") { EditorOps.right(tab) }
        IconKey(Icons.Filled.KeyboardArrowUp, "↑") { EditorOps.up(tab) }
        IconKey(Icons.Filled.KeyboardArrowDown, "↓") { EditorOps.down(tab) }
        IconKey(Icons.AutoMirrored.Filled.Undo, "Undo") { EditorOps.undo(tab) }
        IconKey(Icons.AutoMirrored.Filled.Redo, "Redo") { EditorOps.redo(tab) }
        for (ch in SYMBOLS) {
            Key(onClick = { EditorOps.typeChar(tab, ch, autoPair) }) {
                Text(ch.toString(), fontFamily = FontFamily.Monospace, fontSize = 18.sp)
            }
        }
    }
}

/** A strip of suggestions for the word at the caret. */
@Composable
fun CompletionStrip(result: CompletionResult, onPick: (String) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 6.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        for (item in result.items) {
            Box(
                Modifier
                    .height(34.dp)
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f), RoundedCornerShape(17.dp))
                    .clickable { onPick(item) }
                    .padding(horizontal = 14.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(item, fontFamily = FontFamily.Monospace, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSecondaryContainer)
            }
        }
    }
}

package dev.xenolab.pymobile.ui.console

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.xenolab.pymobile.R
import dev.xenolab.pymobile.editor.ConsoleLine
import dev.xenolab.pymobile.editor.Tracebacks
import dev.xenolab.pymobile.ui.theme.LocalCodeColors

@Composable
fun ConsolePanel(
    lines: List<ConsoleLine>,
    running: Boolean,
    fontSize: Int,
    full: Boolean,
    onToggleFull: () -> Unit,
    onClose: () -> Unit,
    onClear: () -> Unit,
    onStop: () -> Unit,
    onInput: (String) -> Unit,
    onEof: () -> Unit,
    onOpenTrace: (file: String, line: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalCodeColors.current
    val listState = rememberLazyListState()
    var input by remember { mutableStateOf("") }
    val errorColor = colors.error
    val inputColor = MaterialTheme.colorScheme.primary

    // Follow the output while the user is at the bottom.
    LaunchedEffect(lines.size) {
        if (lines.isEmpty()) return@LaunchedEffect
        val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
        if (last >= lines.size - 3 || last < 0) listState.scrollToItem(lines.size - 1)
    }

    Column(modifier.background(MaterialTheme.colorScheme.surfaceContainerLow)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                stringResource(R.string.console),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f).padding(start = 4.dp),
            )
            if (running) {
                AssistChip(
                    onClick = onStop,
                    label = { Text(stringResource(R.string.stop)) },
                    leadingIcon = { Icon(Icons.Filled.Stop, null) },
                )
            }
            IconButton(onClick = onClear) { Icon(Icons.Filled.DeleteSweep, stringResource(R.string.clear)) }
            IconButton(onClick = onToggleFull) {
                Icon(if (full) Icons.Filled.FullscreenExit else Icons.Filled.Fullscreen, stringResource(R.string.fullscreen))
            }
            IconButton(onClick = onClose) { Icon(Icons.Filled.Close, stringResource(R.string.close)) }
        }

        Box(Modifier.weight(1f).fillMaxWidth()) {
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp)) {
                items(lines.size) { index ->
                    val line = lines[index]
                    val trace = Tracebacks.find(line.text)
                    val text = remember(line) {
                        buildAnnotatedString {
                            for (seg in line.segments) {
                                val base = when {
                                    seg.input -> inputColor
                                    seg.color in 1 until colors.ansi.size -> colors.ansi[seg.color]
                                    seg.error -> errorColor
                                    else -> androidx.compose.ui.graphics.Color.Unspecified
                                }
                                withStyle(
                                    SpanStyle(
                                        color = base,
                                        fontWeight = if (seg.bold) FontWeight.Bold else null,
                                        textDecoration = if (trace != null) TextDecoration.Underline else null,
                                    ),
                                ) { append(seg.text) }
                            }
                            if (line.segments.isEmpty()) append(" ")
                        }
                    }
                    Text(
                        text = text,
                        style = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = fontSize.sp,
                            lineHeight = (fontSize * 1.35f).sp,
                            color = MaterialTheme.colorScheme.onSurface,
                        ),
                        modifier = if (trace != null) {
                            Modifier.fillMaxWidth().clickable { onOpenTrace(trace.file, trace.line) }
                        } else Modifier.fillMaxWidth(),
                    )
                }
            }
        }

        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val send = {
                if (running) {
                    onInput(input)
                    input = ""
                }
            }
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                enabled = running,
                singleLine = true,
                placeholder = { Text(stringResource(R.string.console_input_hint)) },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send, autoCorrectEnabled = false),
                keyboardActions = KeyboardActions(onSend = { send() }),
                textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 14.sp),
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { send() }, enabled = running) {
                Icon(Icons.AutoMirrored.Filled.Send, stringResource(R.string.send))
            }
            TextButton(onClick = onEof, enabled = running) { Text(stringResource(R.string.eof)) }
        }
    }
}

package dev.xenolab.pymobile.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.xenolab.pymobile.editor.PyLexer
import dev.xenolab.pymobile.editor.Severity
import dev.xenolab.pymobile.project.EditorTab
import dev.xenolab.pymobile.ui.theme.CodeColors
import dev.xenolab.pymobile.ui.theme.LocalCodeColors

private class PythonTransformation(
    private val tab: EditorTab,
    private val colors: CodeColors,
    private val highlightOn: Boolean,
) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val source = text.text
        val builder = AnnotatedString.Builder(source)
        if (highlightOn) {
            for (span in tab.highlight.spansFor(source, PyLexer::highlight)) {
                val s = span.start.coerceIn(0, source.length)
                val e = span.end.coerceIn(s, source.length)
                if (e > s) builder.addStyle(SpanStyle(color = colors.of(span.type)), s, e)
            }
        }
        for (issue in tab.issues) {
            val s = issue.start.coerceIn(0, source.length)
            val e = issue.end.coerceIn(s, source.length)
            if (e <= s) continue
            val c = if (issue.severity == Severity.ERROR) colors.error else colors.warning
            builder.addStyle(SpanStyle(textDecoration = TextDecoration.Underline, background = c.copy(alpha = 0.18f)), s, e)
        }
        return TransformedText(builder.toAnnotatedString(), OffsetMapping.Identity)
    }
}

@Composable
fun CodeEditor(
    tab: EditorTab,
    fontSize: Int,
    lineNumbers: Boolean,
    autoPair: Boolean,
    onSave: () -> Unit,
    onRun: () -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester = remember { FocusRequester() },
) {
    val colors = LocalCodeColors.current
    val density = LocalDensity.current
    val value = tab.value
    val vScroll = rememberScrollState()
    val hScroll = rememberScrollState()
    var layout by remember(tab) { mutableStateOf<TextLayoutResult?>(null) }
    val lineHeight = (fontSize * 1.45f).sp
    val style = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontSize = fontSize.sp,
        lineHeight = lineHeight,
        color = MaterialTheme.colorScheme.onSurface,
    )
    val lineCount = remember(value.text.length, value.text.count { it == '\n' }) { value.text.count { it == '\n' } + 1 }
    val transformation = remember(tab, colors) { PythonTransformation(tab, colors, tab.file.extension == "py") }

    // Lint a moment after the user stops typing.
    LaunchedEffect(tab) {
        snapshotFlow { tab.value.text }.collect { text ->
            kotlinx.coroutines.delay(400)
            tab.issues = if (tab.file.extension == "py") dev.xenolab.pymobile.editor.Lint.check(text) else emptyList()
        }
    }

    BoxWithConstraints(modifier.background(MaterialTheme.colorScheme.surface)) {
        val viewportH = with(density) { maxHeight.toPx() }
        val viewportW = with(density) { maxWidth.toPx() }
        val digits = lineCount.toString().length.coerceAtLeast(2)
        val gutterWidth = with(density) { (fontSize * 0.62f * digits).sp.toDp() } + 16.dp

        // Keep the caret on the screen.
        LaunchedEffect(layout, value.selection.start) {
            val l = layout ?: return@LaunchedEffect
            val caret = value.selection.start.coerceIn(0, l.layoutInput.text.length)
            val rect = l.getCursorRect(caret)
            val topPad = 8f
            if (rect.top - topPad < vScroll.value) vScroll.scrollTo((rect.top - topPad).toInt().coerceAtLeast(0))
            else if (rect.bottom + topPad > vScroll.value + viewportH) vScroll.scrollTo((rect.bottom + topPad - viewportH).toInt())
            val gutterPx = if (lineNumbers) with(density) { gutterWidth.toPx() } else 0f
            val visibleW = viewportW - gutterPx
            if (rect.left < hScroll.value) hScroll.scrollTo((rect.left - 24f).toInt().coerceAtLeast(0))
            else if (rect.right + 24f > hScroll.value + visibleW) hScroll.scrollTo((rect.right + 24f - visibleW).toInt())
        }

        Row(Modifier.fillMaxSize().verticalScroll(vScroll)) {
            if (lineNumbers) {
                Text(
                    text = remember(lineCount) { (1..lineCount).joinToString("\n") },
                    style = style.copy(color = MaterialTheme.colorScheme.outline, textAlign = TextAlign.End),
                    modifier = Modifier
                        .heightIn(min = maxHeight)
                        .background(MaterialTheme.colorScheme.surfaceContainer)
                        .padding(start = 4.dp, end = 8.dp, top = 8.dp, bottom = 8.dp)
                        .widthIn(min = gutterWidth - 12.dp),
                )
            }
            Box(Modifier.weight(1f).horizontalScroll(hScroll)) {
                BasicTextField(
                    value = value,
                    onValueChange = { EditorOps.userChange(tab, it, autoPair) },
                    textStyle = style,
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    visualTransformation = transformation,
                    onTextLayout = { layout = it },
                    softWrap = false,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.None,
                        autoCorrectEnabled = false,
                        imeAction = ImeAction.None,
                    ),
                    modifier = Modifier
                        .heightIn(min = maxHeight)
                        .widthIn(min = with(density) { (viewportW - (if (lineNumbers) gutterWidth.toPx() else 0f)).toDp() })
                        .padding(horizontal = 8.dp, vertical = 8.dp)
                        .focusRequester(focusRequester)
                        .onPreviewKeyEvent { e ->
                            if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                            when {
                                e.key == Key.Tab && e.isShiftPressed -> { EditorOps.outdent(tab); true }
                                e.key == Key.Tab -> { EditorOps.indent(tab); true }
                                e.isCtrlPressed && e.key == Key.S -> { onSave(); true }
                                e.isCtrlPressed && e.key == Key.Enter -> { onRun(); true }
                                e.isCtrlPressed && e.key == Key.Z && e.isShiftPressed -> { EditorOps.redo(tab); true }
                                e.isCtrlPressed && e.key == Key.Z -> { EditorOps.undo(tab); true }
                                e.isCtrlPressed && e.key == Key.Y -> { EditorOps.redo(tab); true }
                                e.isCtrlPressed && e.key == Key.Slash -> { EditorOps.toggleComment(tab); true }
                                else -> false
                            }
                        },
                )
            }
        }
    }
}

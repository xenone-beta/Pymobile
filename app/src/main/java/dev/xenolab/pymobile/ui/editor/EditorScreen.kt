package dev.xenolab.pymobile.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import dev.xenolab.pymobile.MainViewModel
import dev.xenolab.pymobile.R
import dev.xenolab.pymobile.editor.Completion
import dev.xenolab.pymobile.editor.EditActions
import dev.xenolab.pymobile.editor.PyLexer
import dev.xenolab.pymobile.editor.Severity
import dev.xenolab.pymobile.project.EditorTab
import dev.xenolab.pymobile.ui.ConfirmDialog
import dev.xenolab.pymobile.ui.console.ConsolePanel
import dev.xenolab.pymobile.ui.theme.LocalCodeColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    vm: MainViewModel,
    onOpenDrawer: () -> Unit,
) {
    val snackbar = remember { SnackbarHostState() }
    val tab = vm.activeTab
    val running by vm.running.collectAsState()
    val background by vm.background.collectAsState()
    val lines by vm.consoleLines.collectAsState()
    var closing by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(vm.toast) {
        vm.toast?.let {
            snackbar.showSnackbar(it)
            vm.toast = null
        }
    }
    // Move the caret to the line a traceback pointed at.
    LaunchedEffect(vm.jump, tab) {
        val jump = vm.jump ?: return@LaunchedEffect
        if (tab != null && tab.file.absolutePath == jump.path) {
            EditorOps.moveCaretToLine(tab, jump.line)
            vm.consumeJump()
        }
    }

    Scaffold(
        modifier = Modifier.imePadding(),
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(tab?.title ?: stringResource(R.string.app_name), maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = onOpenDrawer) { Icon(Icons.Filled.Menu, stringResource(R.string.files)) }
                },
                actions = {
                    if (running) {
                        IconButton(onClick = { vm.stop() }) { Icon(Icons.Filled.Stop, stringResource(R.string.stop), tint = MaterialTheme.colorScheme.error) }
                    } else {
                        IconButton(onClick = { vm.run() }, enabled = tab != null && tab.file.extension == "py") {
                            Icon(Icons.Filled.PlayArrow, stringResource(R.string.run))
                        }
                    }
                    IconButton(onClick = { vm.saveActive() }, enabled = tab != null) {
                        Icon(Icons.Filled.Save, stringResource(R.string.save))
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            if (!(vm.consoleVisible && vm.consoleFull)) {
                TabBar(vm, onClose = { index -> if (!vm.closeTab(index)) closing = index })
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    if (tab == null) {
                        EmptyState(Modifier.align(Alignment.Center))
                    } else {
                        key(tab) {
                            CodeEditor(
                                tab = tab,
                                fontSize = vm.fontSize,
                                lineNumbers = vm.lineNumbers,
                                autoPair = vm.autoPair,
                                onSave = { vm.saveActive() },
                                onRun = { vm.run() },
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }
                }
                if (tab != null) {
                    StatusLine(tab)
                    val suggestion = remember(tab.value.text, tab.value.selection) {
                        val v = tab.value
                        if (tab.file.extension == "py" && v.selection.collapsed) {
                            Completion.suggest(v.text, v.selection.start, tab.highlight.spansFor(v.text, PyLexer::highlight))
                        } else null
                    }
                    if (suggestion != null) {
                        CompletionStrip(suggestion, onPick = { choice ->
                            val v = tab.value
                            EditorOps.apply(tab, Completion.apply(v.text, v.selection.start, suggestion, choice))
                        })
                    }
                    ExtraKeys(tab, vm.autoPair)
                }
            }
            if (vm.consoleVisible) {
                ConsolePanel(
                    lines = lines,
                    running = running,
                    canInput = running && !background,
                    fontSize = (vm.fontSize - 1).coerceAtLeast(10),
                    full = vm.consoleFull,
                    onToggleFull = { vm.consoleFull = !vm.consoleFull },
                    onClose = { vm.consoleVisible = false; vm.consoleFull = false },
                    onClear = { vm.clearConsole() },
                    onStop = { vm.stop() },
                    onInput = { vm.sendInput(it) },
                    onEof = { vm.closeInput() },
                    onOpenTrace = { f, l -> vm.consoleFull = false; vm.openTraceback(f, l) },
                    modifier = Modifier.weight(if (vm.consoleFull) 1f else 0.75f).fillMaxWidth(),
                )
            }
        }
    }

    if (vm.showRunSettings && tab != null) {
        RunSettingsDialog(vm, tab.file, onDismiss = { vm.showRunSettings = false })
    }

    closing?.let { index ->
        val t = vm.tabs.getOrNull(index)
        if (t == null) closing = null else ConfirmDialog(
            title = stringResource(R.string.unsaved_title),
            message = stringResource(R.string.unsaved_message, t.title),
            confirmText = stringResource(R.string.save),
            onConfirm = { if (vm.save(t)) vm.closeTab(index, force = true) },
            neutralText = stringResource(R.string.discard),
            onNeutral = { vm.closeTab(index, force = true) },
            onDismiss = { closing = null },
        )
    }
}

@Composable
private fun TabBar(vm: MainViewModel, onClose: (Int) -> Unit) {
    if (vm.tabs.isEmpty()) return
    Row(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 6.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        vm.tabs.forEachIndexed { index, t ->
            val active = index == vm.activeIndex
            Row(
                Modifier
                    .background(
                        if (active) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
                        RoundedCornerShape(10.dp),
                    )
                    .clickable { vm.select(index) }
                    .padding(start = 12.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    (if (t.dirty) "● " else "") + t.title,
                    maxLines = 1,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (active) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                IconButton(onClick = { onClose(index) }, modifier = Modifier.padding(0.dp)) {
                    Icon(Icons.Filled.Close, stringResource(R.string.close), Modifier.padding(6.dp))
                }
            }
        }
    }
}

@Composable
private fun EmptyState(modifier: Modifier) {
    Column(modifier.padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(stringResource(R.string.no_file_title), style = MaterialTheme.typography.titleMedium)
        Text(
            stringResource(R.string.no_file_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

@Composable
private fun StatusLine(tab: EditorTab) {
    val colors = LocalCodeColors.current
    val v = tab.value
    val line = EditActions.lineOf(v.text, v.selection.start)
    val col = EditActions.columnOf(v.text, v.selection.start)
    val here = tab.issues.firstOrNull { EditActions.lineOf(v.text, it.start.coerceIn(0, v.text.length)) == line }
    val errors = tab.issues.count { it.severity == Severity.ERROR }
    Row(
        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(horizontal = 12.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "${line + 1}:${col + 1}",
            style = MaterialTheme.typography.labelMedium,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = when {
                here != null -> here.message
                errors > 0 -> pluralStringResource(R.plurals.problems, errors, errors)
                else -> ""
            },
            style = MaterialTheme.typography.labelMedium,
            color = if (here?.severity == Severity.WARNING) colors.warning else colors.error,
            maxLines = 1,
            modifier = Modifier.padding(start = 12.dp),
        )
    }
}

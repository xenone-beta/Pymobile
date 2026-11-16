package dev.xenolab.pymobile.ui.files

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.NoteAdd
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Comment
import androidx.compose.material.icons.filled.FormatAlignLeft
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.xenolab.pymobile.MainViewModel
import dev.xenolab.pymobile.R
import dev.xenolab.pymobile.project.ProjectFiles
import dev.xenolab.pymobile.ui.ConfirmDialog
import dev.xenolab.pymobile.ui.TextPromptDialog
import dev.xenolab.pymobile.ui.editor.EditorOps
import java.io.File

private enum class Prompt { NEW_FILE, NEW_FOLDER, NEW_PROJECT }

/** What the entries at the bottom of the drawer do; the activity owns the screens and file pickers. */
class DrawerActions(
    val onPickFile: () -> Unit,
    val onExport: () -> Unit,
    val onPip: () -> Unit,
    val onGit: () -> Unit,
    val onSettings: () -> Unit,
)

@Composable
private fun DrawerEntry(icon: ImageVector, label: String, enabled: Boolean = true, onClick: () -> Unit) {
    val color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
    Row(
        Modifier.fillMaxWidth().clickable(enabled = enabled, onClick = onClick).padding(horizontal = 20.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = color)
        Text(label, color = color, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = 16.dp))
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FilesDrawer(vm: MainViewModel, actions: DrawerActions, onOpened: () -> Unit, onRequestAllFiles: () -> Unit) {
    var prompt by remember { mutableStateOf<Prompt?>(null) }
    var menuFor by remember { mutableStateOf<File?>(null) }
    var renameFor by remember { mutableStateOf<File?>(null) }
    var deleteFor by remember { mutableStateOf<File?>(null) }

    Column(Modifier.fillMaxWidth()) {
        Text(
            stringResource(R.string.files),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(start = 20.dp, top = 20.dp, end = 16.dp),
        )
        Text(
            if (vm.currentDir.absolutePath == vm.root.absolutePath) "/" else ProjectFiles.relative(vm.root, vm.currentDir),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp),
        )
        if (!vm.hasAllFiles) {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 6.dp)) {
                Text(
                    stringResource(R.string.storage_private_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(onClick = onRequestAllFiles) { Text(stringResource(R.string.grant_storage)) }
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            IconButton(onClick = { vm.goUp() }, enabled = vm.currentDir.absolutePath != vm.root.absolutePath) {
                Icon(Icons.Filled.ArrowUpward, stringResource(R.string.up))
            }
            IconButton(onClick = { prompt = Prompt.NEW_FILE }) { Icon(Icons.Filled.NoteAdd, stringResource(R.string.new_file)) }
            IconButton(onClick = { prompt = Prompt.NEW_FOLDER }) { Icon(Icons.Filled.CreateNewFolder, stringResource(R.string.new_folder)) }
            IconButton(onClick = { vm.refreshEntries() }) { Icon(Icons.Filled.Refresh, stringResource(R.string.refresh)) }
        }
        if (vm.currentDir.absolutePath == vm.root.absolutePath) {
            FilledTonalButton(
                onClick = { prompt = Prompt.NEW_PROJECT },
                modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
            ) { Text(stringResource(R.string.new_project)) }
        }
        HorizontalDivider(Modifier.padding(top = 8.dp))

        if (vm.entries.isEmpty()) {
            Text(
                stringResource(R.string.folder_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(20.dp),
            )
        }
        LazyColumn(Modifier.weight(1f)) {
            items(vm.entries, key = { it.absolutePath }) { file ->
                val isPy = file.extension == "py"
                Box {
                    ListItem(
                        headlineContent = { Text(file.name, maxLines = 1) },
                        leadingContent = {
                            Icon(
                                when {
                                    file.isDirectory -> Icons.Filled.Folder
                                    isPy -> Icons.Filled.Code
                                    else -> Icons.AutoMirrored.Filled.InsertDriveFile
                                },
                                null,
                                tint = if (file.isDirectory) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                        modifier = Modifier.combinedClickable(
                            onClick = {
                                if (file.isDirectory) vm.enter(file) else {
                                    vm.openFile(file)
                                    onOpened()
                                }
                            },
                            onLongClick = { menuFor = file },
                        ),
                    )
                    DropdownMenu(expanded = menuFor == file, onDismissRequest = { menuFor = null }) {
                        DropdownMenuItem(text = { Text(stringResource(R.string.rename)) }, onClick = { renameFor = file; menuFor = null })
                        DropdownMenuItem(text = { Text(stringResource(R.string.delete)) }, onClick = { deleteFor = file; menuFor = null })
                    }
                }
            }
        }
        HorizontalDivider()
        val running by vm.running.collectAsState()
        val tab = vm.activeTab
        val isPython = tab?.file?.extension == "py"
        Column(Modifier.heightIn(max = 330.dp).verticalScroll(rememberScrollState())) {
            DrawerEntry(Icons.Filled.FileOpen, stringResource(R.string.open_from_device)) { actions.onPickFile(); onOpened() }
            DrawerEntry(Icons.Filled.SaveAlt, stringResource(R.string.save_copy), enabled = tab != null) { actions.onExport(); onOpened() }
            DrawerEntry(Icons.Filled.Schedule, stringResource(R.string.run_background), enabled = isPython && !running) { vm.run(inBackground = true); onOpened() }
            DrawerEntry(Icons.Filled.Tune, stringResource(R.string.run_settings), enabled = isPython) { vm.showRunSettings = true; onOpened() }
            DrawerEntry(Icons.Filled.FormatAlignLeft, stringResource(R.string.format_code), enabled = isPython) { vm.formatActive(); onOpened() }
            DrawerEntry(Icons.Filled.Comment, stringResource(R.string.toggle_comment), enabled = tab != null) { tab?.let { EditorOps.toggleComment(it) }; onOpened() }
            DrawerEntry(Icons.Filled.Terminal, stringResource(R.string.console)) { vm.consoleVisible = !vm.consoleVisible; onOpened() }
            DrawerEntry(Icons.Filled.Extension, stringResource(R.string.packages)) { actions.onPip(); onOpened() }
            DrawerEntry(Icons.Filled.AccountTree, stringResource(R.string.git)) { actions.onGit(); onOpened() }
            DrawerEntry(Icons.Filled.Settings, stringResource(R.string.settings)) { actions.onSettings(); onOpened() }
        }
    }

    when (prompt) {
        Prompt.NEW_FILE -> TextPromptDialog(stringResource(R.string.new_file), "", stringResource(R.string.name_hint_file), onConfirm = { vm.newFile(it) }, onDismiss = { prompt = null })
        Prompt.NEW_FOLDER -> TextPromptDialog(stringResource(R.string.new_folder), "", stringResource(R.string.name_hint_folder), onConfirm = { vm.newFolder(it) }, onDismiss = { prompt = null })
        Prompt.NEW_PROJECT -> TextPromptDialog(stringResource(R.string.new_project), "", stringResource(R.string.name_hint_project), onConfirm = { vm.newProject(it) }, onDismiss = { prompt = null })
        null -> Unit
    }
    renameFor?.let { f ->
        TextPromptDialog(stringResource(R.string.rename), f.name, onConfirm = { vm.rename(f, it) }, onDismiss = { renameFor = null })
    }
    deleteFor?.let { f ->
        ConfirmDialog(
            title = stringResource(R.string.delete),
            message = stringResource(if (f.isDirectory) R.string.delete_folder_msg else R.string.delete_file_msg, f.name),
            confirmText = stringResource(R.string.delete),
            onConfirm = { vm.delete(f) },
            onDismiss = { deleteFor = null },
        )
    }
}

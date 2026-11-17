package dev.xenolab.pymobile.ui.editor

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.xenolab.pymobile.MainViewModel
import dev.xenolab.pymobile.R
import dev.xenolab.pymobile.editor.Snippet
import dev.xenolab.pymobile.editor.Snippets
import dev.xenolab.pymobile.project.EditorTab

/** Pick a snippet to insert at the caret, or make a new one. */
@Composable
fun SnippetsDialog(vm: MainViewModel, tab: EditorTab, onDismiss: () -> Unit) {
    var filter by remember { mutableStateOf("") }
    var creating by remember { mutableStateOf(false) }
    val all = vm.snippets + Snippets.builtin
    val shown = if (filter.isBlank()) all else all.filter {
        it.title.contains(filter, ignoreCase = true) || it.trigger.contains(filter, ignoreCase = true)
    }

    if (creating) {
        NewSnippetDialog(onSave = { vm.addSnippet(it); creating = false }, onDismiss = { creating = false })
        return
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.snippets)) },
        text = {
            Column {
                OutlinedTextField(
                    value = filter,
                    onValueChange = { filter = it },
                    label = { Text(stringResource(R.string.snippet_search)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(autoCorrectEnabled = false),
                    modifier = Modifier.fillMaxWidth(),
                )
                LazyColumn(Modifier.heightIn(max = 380.dp).padding(top = 8.dp)) {
                    items(shown, key = { it.builtin.toString() + it.trigger + it.title }) { s ->
                        ListItem(
                            headlineContent = { Text(s.title) },
                            supportingContent = {
                                Text(
                                    s.body.replace(Snippets.CURSOR, "").trim().take(120),
                                    style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 11.sp),
                                    maxLines = 2,
                                )
                            },
                            overlineContent = if (s.trigger.isNotEmpty()) ({ Text(s.trigger) }) else null,
                            trailingContent = if (!s.builtin) ({
                                IconButton(onClick = { vm.removeSnippet(s) }) { Icon(Icons.Filled.Delete, stringResource(R.string.delete)) }
                            }) else null,
                            modifier = Modifier.clickable {
                                EditorOps.insertSnippet(tab, s.body)
                                onDismiss()
                            },
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { creating = true }) { Text(stringResource(R.string.snippet_new)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } },
    )
}

@Composable
private fun NewSnippetDialog(onSave: (Snippet) -> Unit, onDismiss: () -> Unit) {
    var title by remember { mutableStateOf("") }
    var trigger by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.snippet_new)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(title, { title = it }, label = { Text(stringResource(R.string.snippet_title)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(
                    trigger, { trigger = it.trim() }, label = { Text(stringResource(R.string.snippet_trigger)) }, singleLine = true,
                    keyboardOptions = KeyboardOptions(autoCorrectEnabled = false), modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
                OutlinedTextField(
                    body, { body = it }, label = { Text(stringResource(R.string.snippet_body)) }, minLines = 4,
                    textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 14.sp),
                    keyboardOptions = KeyboardOptions(autoCorrectEnabled = false), modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
                Text(
                    "Python",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(Snippet(trigger, title.ifBlank { trigger.ifBlank { body.lineSequence().first().take(30) } }, body)) },
                enabled = body.isNotBlank(),
            ) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

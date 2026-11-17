package dev.xenolab.pymobile.ui.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FindReplace
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.xenolab.pymobile.MainViewModel
import dev.xenolab.pymobile.R
import dev.xenolab.pymobile.project.ProjectFiles
import dev.xenolab.pymobile.project.SearchMatch
import dev.xenolab.pymobile.project.SearchOptions
import dev.xenolab.pymobile.ui.ConfirmDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(vm: MainViewModel, onBack: () -> Unit, onOpened: () -> Unit) {
    val result by vm.searchResult.collectAsState()
    val busy by vm.searchBusy.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    var query by remember { mutableStateOf("") }
    var replacement by remember { mutableStateOf("") }
    var showReplace by remember { mutableStateOf(false) }
    var caseSensitive by remember { mutableStateOf(false) }
    var wholeWord by remember { mutableStateOf(false) }
    var regex by remember { mutableStateOf(false) }
    var defs by remember { mutableStateOf(false) }
    var confirming by remember { mutableStateOf(false) }

    val options = SearchOptions(query, caseSensitive, wholeWord, regex, defs)
    // Searches a moment after typing stops and again whenever an option changes.
    LaunchedEffect(query, caseSensitive, wholeWord, regex, defs) {
        if (query.isNotEmpty()) kotlinx.coroutines.delay(250)
        vm.search(options)
    }
    LaunchedEffect(vm.toast) {
        vm.toast?.let { snackbar.showSnackbar(it); vm.toast = null }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.search_project)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } },
                actions = {
                    IconButton(onClick = { showReplace = !showReplace }) {
                        Icon(Icons.Filled.FindReplace, stringResource(R.string.search_replace_toggle))
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text(stringResource(R.string.search_hint)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search, autoCorrectEnabled = false),
                keyboardActions = KeyboardActions(onSearch = { vm.search(options) }),
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp),
            )
            if (showReplace) {
                OutlinedTextField(
                    value = replacement,
                    onValueChange = { replacement = it },
                    label = { Text(stringResource(R.string.replace_hint)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(autoCorrectEnabled = false),
                    modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 6.dp),
                )
            }
            Row(
                Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(caseSensitive, { caseSensitive = !caseSensitive }, { Text(stringResource(R.string.search_case)) })
                FilterChip(wholeWord, { wholeWord = !wholeWord }, { Text(stringResource(R.string.search_word)) })
                FilterChip(regex, { regex = !regex }, { Text(stringResource(R.string.search_regex)) })
                FilterChip(defs, { defs = !defs }, { Text(stringResource(R.string.search_defs)) })
            }
            Text(
                stringResource(R.string.search_in, ProjectFiles.relative(vm.root, vm.currentDir).ifEmpty { "/" }),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp),
            )

            val r = result
            if (r != null) {
                val summary = when {
                    r.error != null -> stringResource(R.string.search_bad_regex, r.error)
                    r.matches.isEmpty() -> stringResource(R.string.search_none)
                    else -> stringResource(R.string.search_summary, r.matches.size, r.files)
                }
                Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        summary,
                        style = MaterialTheme.typography.titleSmall,
                        color = if (r.error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                    if (showReplace && r.matches.isNotEmpty()) {
                        Button(onClick = { confirming = true }) { Text(stringResource(R.string.replace_all)) }
                    }
                }
                if (r.truncated) {
                    Text(
                        stringResource(R.string.search_truncated, r.matches.size),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
                HorizontalDivider(Modifier.padding(top = 6.dp))
                val grouped = r.matches.groupBy { it.file }.entries.toList()
                LazyColumn(Modifier.weight(1f)) {
                    for ((file, hits) in grouped) {
                        item(key = "f-" + file.absolutePath) {
                            Text(
                                ProjectFiles.relative(vm.root, file) + "  (" + hits.size + ")",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 2.dp),
                            )
                        }
                        items(hits, key = { "m-" + it.file.absolutePath + ":" + it.line }) { hit ->
                            MatchRow(hit) {
                                vm.openFile(hit.file, hit.line)
                                onOpened()
                            }
                        }
                    }
                }
            }
        }
    }

    if (confirming) {
        val r = result
        ConfirmDialog(
            title = stringResource(R.string.replace_all),
            message = stringResource(R.string.replace_confirm, r?.matches?.size ?: 0, r?.files ?: 0),
            confirmText = stringResource(R.string.replace_all),
            onConfirm = { vm.replaceAll(options, replacement) },
            onDismiss = { confirming = false },
        )
    }
}

@Composable
private fun MatchRow(hit: SearchMatch, onClick: () -> Unit) {
    val highlight = MaterialTheme.colorScheme.tertiaryContainer
    val onHighlight = MaterialTheme.colorScheme.onTertiaryContainer
    val lead = hit.text.length - hit.text.trimStart().length
    val shown = hit.text.trim()
    val from = (hit.column - lead).coerceIn(0, shown.length)
    val to = (from + hit.length).coerceIn(from, shown.length)
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 5.dp)) {
        Text(
            hit.line.toString(),
            style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant),
            modifier = Modifier.padding(end = 10.dp),
        )
        Text(
            buildAnnotatedString {
                append(shown.substring(0, from))
                withStyle(SpanStyle(background = highlight, color = onHighlight, fontWeight = FontWeight.Bold)) { append(shown.substring(from, to)) }
                append(shown.substring(to))
            },
            style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface),
            maxLines = 2,
        )
    }
}

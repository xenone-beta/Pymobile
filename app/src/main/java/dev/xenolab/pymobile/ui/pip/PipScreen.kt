package dev.xenolab.pymobile.ui.pip

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SystemUpdateAlt
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.xenolab.pymobile.MainViewModel
import dev.xenolab.pymobile.R
import dev.xenolab.pymobile.ui.ConfirmDialog
import dev.xenolab.pymobile.ui.theme.LocalCodeColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PipScreen(vm: MainViewModel, onBack: () -> Unit) {
    val packages by vm.pipPackages.collectAsState()
    val busy by vm.pipBusy.collectAsState()
    val log by vm.pipLines.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf("") }
    var removing by remember { mutableStateOf<String?>(null) }
    val colors = LocalCodeColors.current
    val logState = rememberLazyListState()

    LaunchedEffect(Unit) { vm.pipRefresh() }
    LaunchedEffect(vm.toast) {
        vm.toast?.let { snackbar.showSnackbar(it); vm.toast = null }
    }
    LaunchedEffect(log.size) { if (log.isNotEmpty()) logState.scrollToItem(log.size - 1) }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.packages)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } },
                actions = { IconButton(onClick = { vm.pipRefresh() }, enabled = !busy) { Icon(Icons.Filled.Refresh, stringResource(R.string.refresh)) } },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text(stringResource(R.string.pip_hint)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done, autoCorrectEnabled = false),
                    keyboardActions = KeyboardActions(onDone = { if (query.isNotBlank()) { vm.pipInstall(query); query = "" } }),
                    modifier = Modifier.weight(1f),
                )
                Button(
                    onClick = { vm.pipInstall(query); query = "" },
                    enabled = !busy && query.isNotBlank(),
                    modifier = Modifier.padding(start = 8.dp),
                ) { Text(stringResource(R.string.install)) }
            }
            Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { vm.pipInstallRequirements() }, enabled = !busy) { Text(stringResource(R.string.install_requirements)) }
                if (busy) OutlinedButton(onClick = { vm.pipCancel() }) { Text(stringResource(R.string.stop)) }
            }
            Text(
                stringResource(R.string.pip_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            HorizontalDivider()

            if (log.isNotEmpty()) {
                LazyColumn(
                    state = logState,
                    modifier = Modifier.fillMaxWidth().height(150.dp).padding(horizontal = 12.dp, vertical = 4.dp),
                ) {
                    items(log.size) { i ->
                        val line = log[i]
                        Text(
                            buildAnnotatedString {
                                for (seg in line.segments) {
                                    val c = when {
                                        seg.color in 1 until colors.ansi.size -> colors.ansi[seg.color]
                                        seg.error -> colors.error
                                        else -> androidx.compose.ui.graphics.Color.Unspecified
                                    }
                                    withStyle(SpanStyle(color = c)) { append(seg.text) }
                                }
                                if (line.segments.isEmpty()) append(" ")
                            },
                            style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp, lineHeight = 16.sp, color = MaterialTheme.colorScheme.onSurface),
                        )
                    }
                }
                HorizontalDivider()
            }

            val all = packages
            if (all == null) {
                Text(stringResource(R.string.loading), modifier = Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                val list = if (filter.isBlank()) all else all.filter { it.name.contains(filter.trim(), ignoreCase = true) }
                Text(
                    stringResource(R.string.installed_packages, all.size),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(16.dp, 12.dp, 16.dp, 0.dp),
                )
                if (all.size > 6) {
                    OutlinedTextField(
                        value = filter,
                        onValueChange = { filter = it },
                        label = { Text(stringResource(R.string.filter_packages)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(autoCorrectEnabled = false),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
                LazyColumn(Modifier.weight(1f)) {
                    items(list, key = { it.name }) { p ->
                        ListItem(
                            headlineContent = { Text(p.name) },
                            supportingContent = { Text(p.version) },
                            trailingContent = {
                                Row {
                                    IconButton(onClick = { vm.pipInstall(p.name, upgrade = true) }, enabled = !busy) {
                                        Icon(Icons.Filled.SystemUpdateAlt, stringResource(R.string.upgrade))
                                    }
                                    IconButton(onClick = { removing = p.name }, enabled = !busy && p.name.lowercase() != "pip") {
                                        Icon(Icons.Filled.Delete, stringResource(R.string.uninstall))
                                    }
                                }
                            },
                        )
                    }
                }
            }
        }
    }

    removing?.let { name ->
        ConfirmDialog(
            title = stringResource(R.string.uninstall),
            message = stringResource(R.string.uninstall_msg, name),
            confirmText = stringResource(R.string.uninstall),
            onConfirm = { vm.pipUninstall(name) },
            onDismiss = { removing = null },
        )
    }
}

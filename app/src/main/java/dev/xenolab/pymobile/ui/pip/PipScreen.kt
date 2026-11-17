package dev.xenolab.pymobile.ui.pip

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.TextButton
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
import dev.xenolab.pymobile.SetupState
import dev.xenolab.pymobile.ui.ConfirmDialog
import dev.xenolab.pymobile.ui.theme.LocalCodeColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PipScreen(vm: MainViewModel, onBack: () -> Unit) {
    val packages by vm.pipPackages.collectAsState()
    val busy by vm.pipBusy.collectAsState()
    val error by vm.pipError.collectAsState()
    val log by vm.pipLines.collectAsState()
    val results by vm.pypiResults.collectAsState()
    val searching by vm.pypiBusy.collectAsState()
    val pypiProblem by vm.pypiError.collectAsState()
    var detailFor by remember { mutableStateOf<String?>(null) }
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
        val pyVersion = (vm.setup.collectAsState().value as? SetupState.Ready)?.pythonVersion
        val logScroll = rememberScrollState()
        LaunchedEffect(log.size) { logScroll.scrollTo(logScroll.maxValue) }
        val problem = error
        val pypiIssue = pypiProblem
        val all = packages
        val context = androidx.compose.ui.platform.LocalContext.current

        // One scrolling list for the whole screen: a fixed layout above the package list used to squeeze it to nothing.
        LazyColumn(Modifier.padding(padding).fillMaxSize()) {
            item(key = "progress") {
                if (busy || searching) LinearProgressIndicator(Modifier.fillMaxWidth())
            }
            item(key = "install") {
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
            }
            item(key = "buttons") {
                Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { vm.pypiSearch(query) }, enabled = !searching && query.isNotBlank()) {
                        Text(stringResource(R.string.pypi_search))
                    }
                    OutlinedButton(onClick = { vm.pipInstallRequirements() }, enabled = !busy) { Text(stringResource(R.string.install_requirements)) }
                    if (busy) OutlinedButton(onClick = { vm.pipCancel() }) { Text(stringResource(R.string.stop)) }
                }
            }
            if (pypiIssue != null && detailFor == null) {
                item(key = "pypi-problem") {
                    Text(
                        pypiIssue,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                }
            }
            item(key = "note") {
                Column {
                    Text(
                        stringResource(R.string.pip_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                    if (!pyVersion.isNullOrBlank()) {
                        Text(
                            "Python $pyVersion",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
                        )
                    }
                    HorizontalDivider(Modifier.padding(top = 8.dp))
                }
            }
            if (log.isNotEmpty()) {
                item(key = "log") {
                    Column {
                        Column(
                            Modifier.fillMaxWidth().height(150.dp).verticalScroll(logScroll).padding(horizontal = 12.dp, vertical = 4.dp),
                        ) {
                            for (line in log) {
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
                }
            }
            if (problem != null && !busy) {
                item(key = "problem") {
                    Column(Modifier.fillMaxWidth().padding(16.dp, 8.dp)) {
                        Text(stringResource(R.string.pip_error_title), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.error)
                        Text(
                            problem.take(1500),
                            style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 11.sp, lineHeight = 15.sp, color = MaterialTheme.colorScheme.onSurface),
                            modifier = Modifier.padding(vertical = 6.dp),
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { vm.pipRefresh() }) { Text(stringResource(R.string.retry)) }
                            OutlinedButton(onClick = {
                                val cm = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                cm.setPrimaryClip(android.content.ClipData.newPlainText("Pymobile", problem))
                            }) { Text(stringResource(R.string.copy_log)) }
                        }
                        HorizontalDivider(Modifier.padding(top = 8.dp))
                    }
                }
            }
            if (all == null) {
                item(key = "loading") {
                    Text(stringResource(R.string.loading), modifier = Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                val list = if (filter.isBlank()) all else all.filter { it.name.contains(filter.trim(), ignoreCase = true) }
                item(key = "installed-title") {
                    Text(
                        stringResource(R.string.installed_packages, all.size),
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(16.dp, 12.dp, 16.dp, 0.dp),
                    )
                }
                if (all.size > 6) {
                    item(key = "filter") {
                        OutlinedTextField(
                            value = filter,
                            onValueChange = { filter = it },
                            label = { Text(stringResource(R.string.filter_packages)) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(autoCorrectEnabled = false),
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                    }
                }
                val hits = results
                if (hits != null) {
                    item(key = "pypi-header") {
                        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                stringResource(R.string.pypi_results, hits.size),
                                style = MaterialTheme.typography.titleSmall,
                                modifier = Modifier.weight(1f),
                            )
                            TextButton(onClick = { vm.pypiClear() }) { Text(stringResource(R.string.close)) }
                        }
                    }
                    if (hits.isEmpty()) {
                        item(key = "pypi-empty") {
                            Text(
                                stringResource(R.string.pypi_nothing),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                            )
                        }
                    }
                    items(hits, key = { "pypi-" + it.name }) { hit ->
                        ListItem(
                            headlineContent = { Text(hit.name) },
                            supportingContent = {
                                Text(
                                    listOf(hit.version, hit.summary).filter { it.isNotEmpty() }.joinToString(" · "),
                                    maxLines = 2,
                                )
                            },
                            modifier = Modifier.clickable {
                                detailFor = hit.name
                                vm.pypiOpen(hit.name)
                            },
                        )
                    }
                    item(key = "pypi-divider") { HorizontalDivider(Modifier.padding(vertical = 8.dp)) }
                }
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

    detailFor?.let { name ->
        PackageDialog(vm, name, onDismiss = { detailFor = null })
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

@Composable
private fun PackageDialog(vm: MainViewModel, name: String, onDismiss: () -> Unit) {
    val detail by vm.pypiDetail.collectAsState()
    val problem by vm.pypiError.collectAsState()
    var chosen by remember(detail) { mutableStateOf(detail?.versions?.firstOrNull()?.version) }
    var menu by remember { mutableStateOf(false) }
    val close = { vm.pypiCloseDetail(); onDismiss() }

    AlertDialog(
        onDismissRequest = close,
        title = { Text(detail?.name ?: name) },
        text = {
            val d = detail
            if (d == null) {
                Text(
                    problem ?: stringResource(R.string.loading),
                    color = if (problem != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Column {
                    if (d.summary.isNotEmpty()) Text(d.summary, style = MaterialTheme.typography.bodyMedium)
                    if (d.requiresPython.isNotEmpty()) {
                        Text(
                            stringResource(R.string.pypi_requires_python, d.requiresPython),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                    }
                    if (d.versions.isEmpty()) {
                        Text(stringResource(R.string.pypi_no_files), color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp))
                    } else {
                        Text(stringResource(R.string.pypi_version), style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 12.dp))
                        androidx.compose.foundation.layout.Box {
                            OutlinedButton(onClick = { menu = true }) {
                                Text((chosen ?: "") + if (chosen == d.versions.first().version) "  " + stringResource(R.string.pypi_latest) else "")
                            }
                            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                for (v in d.versions) {
                                    DropdownMenuItem(
                                        text = { Text(v.version + if (v.pure) "" else "  ⚠") },
                                        onClick = { chosen = v.version; menu = false },
                                    )
                                }
                            }
                        }
                        val pure = d.versions.firstOrNull { it.version == chosen }?.pure == true
                        Text(
                            stringResource(if (pure) R.string.pypi_pure else R.string.pypi_not_pure),
                            style = MaterialTheme.typography.bodySmall,
                            color = if (pure) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            }
        },
        confirmButton = {
            val d = detail
            TextButton(
                onClick = { if (d != null && chosen != null) vm.pipInstall(d.name + "==" + chosen); close() },
                enabled = d != null && chosen != null,
            ) { Text(stringResource(R.string.install)) }
        },
        dismissButton = { TextButton(onClick = close) { Text(stringResource(R.string.cancel)) } },
    )
}

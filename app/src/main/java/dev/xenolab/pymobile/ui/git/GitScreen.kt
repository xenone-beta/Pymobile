package dev.xenolab.pymobile.ui.git

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.xenolab.pymobile.MainViewModel
import dev.xenolab.pymobile.R
import dev.xenolab.pymobile.git.ChangeKind
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GitScreen(vm: MainViewModel, onBack: () -> Unit, onSettings: () -> Unit) {
    val status by vm.gitStatus.collectAsState()
    val log by vm.gitLog.collectAsState()
    val busy by vm.gitBusy.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    var message by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }

    LaunchedEffect(Unit) { vm.gitRefresh() }
    LaunchedEffect(vm.toast) {
        vm.toast?.let { snackbar.showSnackbar(it); vm.toast = null }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.git)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } },
                actions = { IconButton(onClick = { vm.gitRefresh() }, enabled = !busy) { Icon(Icons.Filled.Refresh, stringResource(R.string.refresh)) } },
            )
        },
    ) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize()) {
            item {
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                Text(
                    vm.gitDir?.name ?: "",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(start = 16.dp, top = 12.dp, end = 16.dp),
                )
                if (vm.gitToken.isBlank()) {
                    Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Text(
                            stringResource(R.string.git_token_missing),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                        OutlinedButton(onClick = onSettings) { Text(stringResource(R.string.settings)) }
                    }
                }
            }

            val st = status
            if (st != null && !st.isRepo) {
                item {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.git_not_repo), style = MaterialTheme.typography.bodyMedium)
                        Button(onClick = { vm.gitInit() }, enabled = !busy) { Text(stringResource(R.string.git_init)) }
                    }
                }
            } else if (st != null) {
                item {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.git_branch, st.branch ?: "?"), style = MaterialTheme.typography.labelLarge)
                        OutlinedTextField(
                            value = message,
                            onValueChange = { message = it },
                            label = { Text(stringResource(R.string.git_message)) },
                            modifier = Modifier.fillMaxWidth(),
                            keyboardOptions = KeyboardOptions(autoCorrectEnabled = true),
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { vm.gitCommit(message.trim()); message = "" },
                                enabled = !busy && message.isNotBlank() && st.changes.isNotEmpty(),
                            ) { Text(stringResource(R.string.git_commit)) }
                            OutlinedButton(onClick = { vm.gitPull() }, enabled = !busy) { Text(stringResource(R.string.git_pull)) }
                            OutlinedButton(onClick = { vm.gitPush() }, enabled = !busy) { Text(stringResource(R.string.git_push)) }
                        }
                    }
                    HorizontalDivider(Modifier.padding(top = 8.dp))
                    Text(
                        stringResource(R.string.git_changes, st.changes.size),
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(16.dp, 12.dp, 16.dp, 4.dp),
                    )
                }
                items(st.changes) { c ->
                    ListItem(
                        headlineContent = { Text(c.path) },
                        supportingContent = {
                            Text(
                                stringResource(
                                    when (c.kind) {
                                        ChangeKind.ADDED -> R.string.git_added
                                        ChangeKind.MODIFIED -> R.string.git_modified
                                        ChangeKind.REMOVED -> R.string.git_removed
                                        ChangeKind.UNTRACKED -> R.string.git_untracked
                                        ChangeKind.CONFLICT -> R.string.git_conflict
                                    },
                                ),
                            )
                        },
                    )
                }
                item {
                    Text(
                        stringResource(R.string.git_history),
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(16.dp, 16.dp, 16.dp, 4.dp),
                    )
                }
                items(log) { c ->
                    ListItem(
                        headlineContent = { Text(c.message.lineSequence().firstOrNull().orEmpty(), maxLines = 2) },
                        supportingContent = {
                            Text("${c.id} · ${c.author} · ${DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(c.timeMillis))}")
                        },
                    )
                }
            }

            item {
                HorizontalDivider(Modifier.padding(top = 12.dp))
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.git_clone), style = MaterialTheme.typography.titleSmall)
                    OutlinedTextField(
                        value = url,
                        onValueChange = { url = it },
                        label = { Text(stringResource(R.string.git_clone_hint)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, keyboardType = androidx.compose.ui.text.input.KeyboardType.Uri),
                    )
                    Button(onClick = { vm.gitClone(url); url = "" }, enabled = !busy && url.isNotBlank()) {
                        Text(stringResource(R.string.git_clone_action))
                    }
                }
            }
        }
    }
}

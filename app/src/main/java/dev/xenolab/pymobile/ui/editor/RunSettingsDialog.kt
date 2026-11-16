package dev.xenolab.pymobile.ui.editor

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
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
import java.io.File

/** Arguments and environment variables used whenever this file is run. */
@Composable
fun RunSettingsDialog(vm: MainViewModel, file: File, onDismiss: () -> Unit) {
    val saved = remember(file) { vm.runConfig(file) }
    var args by remember(file) { mutableStateOf(saved.args) }
    var env by remember(file) { mutableStateOf(saved.env) }
    val mono = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 14.sp)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.run_settings)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(file.name, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                OutlinedTextField(
                    value = args,
                    onValueChange = { args = it },
                    label = { Text(stringResource(R.string.run_args)) },
                    placeholder = { Text(stringResource(R.string.run_args_hint)) },
                    singleLine = true,
                    textStyle = mono,
                    keyboardOptions = KeyboardOptions(autoCorrectEnabled = false),
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
                OutlinedTextField(
                    value = env,
                    onValueChange = { env = it },
                    label = { Text(stringResource(R.string.run_env)) },
                    placeholder = { Text(stringResource(R.string.run_env_hint)) },
                    minLines = 3,
                    textStyle = mono,
                    keyboardOptions = KeyboardOptions(autoCorrectEnabled = false),
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
                Text(
                    stringResource(R.string.run_settings_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { if (vm.saveRunConfig(file, args, env)) onDismiss() }) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

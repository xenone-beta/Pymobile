package dev.xenolab.pymobile.ui.settings

import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import dev.xenolab.pymobile.BuildConfig
import dev.xenolab.pymobile.MainViewModel
import dev.xenolab.pymobile.R
import dev.xenolab.pymobile.data.AppSettings
import dev.xenolab.pymobile.SetupState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue

@Composable
private fun Header(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(16.dp, 20.dp, 16.dp, 4.dp))
}

@Composable
private fun SwitchRow(title: String, checked: Boolean, onChange: (Boolean) -> Unit, enabled: Boolean = true) {
    Row(
        Modifier.fillMaxWidth().clickable(enabled = enabled) { onChange(!checked) }.padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Switch(checked = checked, onCheckedChange = onChange, enabled = enabled)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: MainViewModel, onBack: () -> Unit, onRequestAllFiles: () -> Unit) {
    val setup by vm.setup.collectAsState()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState())) {
            Header(stringResource(R.string.appearance))
            listOf(0 to R.string.theme_system, 1 to R.string.theme_light, 2 to R.string.theme_dark).forEach { (mode, label) ->
                Row(
                    Modifier.fillMaxWidth().clickable { vm.updateThemeMode(mode) }.padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = vm.themeMode == mode, onClick = { vm.updateThemeMode(mode) })
                    Text(stringResource(label))
                }
            }
            SwitchRow(stringResource(R.string.dynamic_color), vm.dynamicColor, vm::updateDynamicColor, enabled = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)

            Header(stringResource(R.string.editor))
            Text(stringResource(R.string.font_size, vm.fontSize), Modifier.padding(horizontal = 16.dp))
            Slider(
                value = vm.fontSize.toFloat(),
                onValueChange = { vm.updateFontSize(it.toInt()) },
                valueRange = AppSettings.MIN_FONT.toFloat()..AppSettings.MAX_FONT.toFloat(),
                steps = AppSettings.MAX_FONT - AppSettings.MIN_FONT - 1,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            SwitchRow(stringResource(R.string.line_numbers), vm.lineNumbers, vm::updateLineNumbers)
            SwitchRow(stringResource(R.string.auto_pair), vm.autoPair, vm::updateAutoPair)
            SwitchRow(stringResource(R.string.format_on_save), vm.formatOnSave, vm::updateFormatOnSave)

            Header(stringResource(R.string.projects_folder))
            Text(vm.root.absolutePath, Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.bodyMedium)
            if (!vm.hasAllFiles) {
                Text(
                    stringResource(R.string.storage_private_note),
                    Modifier.padding(16.dp, 4.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(onClick = onRequestAllFiles, modifier = Modifier.padding(horizontal = 8.dp)) { Text(stringResource(R.string.grant_storage)) }
            }

            Header(stringResource(R.string.git))
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(vm.gitName, vm::updateGitName, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.git_name)) }, singleLine = true)
                OutlinedTextField(
                    vm.gitEmail, vm::updateGitEmail, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.git_email)) }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                )
                OutlinedTextField(
                    vm.gitToken, vm::updateGitToken, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.git_token)) }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
                    supportingText = { Text(stringResource(R.string.git_token_note)) },
                )
            }

            Header(stringResource(R.string.about))
            Text(
                stringResource(R.string.about_text, BuildConfig.VERSION_NAME, (setup as? SetupState.Ready)?.pythonVersion ?: BuildConfig.PYTHON_VERSION),
                Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                style = MaterialTheme.typography.bodyMedium,
            )
            HorizontalDivider(Modifier.padding(top = 24.dp))
        }
    }
}

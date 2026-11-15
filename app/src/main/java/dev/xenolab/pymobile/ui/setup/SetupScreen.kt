package dev.xenolab.pymobile.ui.setup

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.xenolab.pymobile.R
import dev.xenolab.pymobile.SetupState

@Composable
fun SetupScreen(state: SetupState, onRetry: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(28.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        when (state) {
            is SetupState.Checking, is SetupState.Ready -> CircularProgressIndicator()
            is SetupState.Installing -> {
                Text(stringResource(R.string.setup_title), style = MaterialTheme.typography.headlineSmall)
                Text(
                    stringResource(R.string.setup_message),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 12.dp),
                )
                LinearProgressIndicator(progress = { state.progress }, modifier = Modifier.fillMaxWidth())
            }
            is SetupState.Failed -> {
                val context = LocalContext.current
                Text(stringResource(R.string.setup_failed), style = MaterialTheme.typography.headlineSmall)
                Text(
                    state.log,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(vertical = 12.dp).fillMaxWidth(),
                )
                Button(onClick = onRetry) { Text(stringResource(R.string.retry)) }
                OutlinedButton(
                    onClick = {
                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        cm.setPrimaryClip(ClipData.newPlainText("Pymobile", state.log))
                    },
                    modifier = Modifier.padding(top = 8.dp),
                ) { Text(stringResource(R.string.copy_log)) }
            }
        }
    }
}

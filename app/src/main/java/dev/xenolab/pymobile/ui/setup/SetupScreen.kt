package dev.xenolab.pymobile.ui.setup

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
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
            is SetupState.Checking, is SetupState.Ready -> Brand { CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 3.dp) }
            is SetupState.Installing -> Brand {
                Text(stringResource(R.string.setup_title), style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(R.string.setup_message),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(vertical = 12.dp),
                )
                LinearProgressIndicator(progress = { state.progress }, modifier = Modifier.fillMaxWidth().clip(CircleShape))
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

/** The app icon, its name and whatever the loading step puts below. Looks like the system splash, then carries on. */
@Composable
private fun Brand(content: @Composable () -> Unit) {
    Box(
        Modifier.size(112.dp).clip(RoundedCornerShape(30.dp)).background(colorResource(R.color.ic_launcher_background)),
        contentAlignment = Alignment.Center,
    ) {
        Image(painterResource(R.drawable.ic_launcher_foreground), null, Modifier.size(112.dp))
    }
    Spacer(Modifier.height(18.dp))
    Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium)
    Text(
        stringResource(R.string.app_tagline),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(28.dp))
    content()
}

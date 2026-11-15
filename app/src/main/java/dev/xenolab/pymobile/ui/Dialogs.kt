package dev.xenolab.pymobile.ui

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import dev.xenolab.pymobile.R

@Composable
fun TextPromptDialog(
    title: String,
    initial: String = "",
    label: String? = null,
    confirmText: String = stringResource(R.string.ok),
    singleLine: Boolean = true,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = label?.let { { Text(it) } },
                singleLine = singleLine,
                keyboardOptions = KeyboardOptions(autoCorrectEnabled = false),
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text); onDismiss() }, enabled = text.isNotBlank()) { Text(confirmText) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    neutralText: String? = null,
    onNeutral: () -> Unit = {},
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = { TextButton(onClick = { onConfirm(); onDismiss() }) { Text(confirmText) } },
        dismissButton = {
            Row2(neutralText, onNeutral, onDismiss)
        },
    )
}

@Composable
private fun Row2(neutralText: String?, onNeutral: () -> Unit, onDismiss: () -> Unit) {
    androidx.compose.foundation.layout.Row {
        if (neutralText != null) TextButton(onClick = { onNeutral(); onDismiss() }) { Text(neutralText) }
        TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
    }
}

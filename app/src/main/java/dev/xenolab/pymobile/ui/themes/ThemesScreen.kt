package dev.xenolab.pymobile.ui.themes

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.xenolab.pymobile.MainViewModel
import dev.xenolab.pymobile.R
import dev.xenolab.pymobile.editor.PyLexer
import dev.xenolab.pymobile.editor.TokenType
import dev.xenolab.pymobile.theme.Palette
import dev.xenolab.pymobile.theme.PaletteKey
import dev.xenolab.pymobile.theme.Palettes
import dev.xenolab.pymobile.theme.Pmt
import dev.xenolab.pymobile.ui.ConfirmDialog

private class Editing(val replaceId: String?, val initial: Palette)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThemesScreen(vm: MainViewModel, onBack: () -> Unit, onImport: () -> Unit, onExport: (Palette) -> Unit) {
    var editing by remember { mutableStateOf<Editing?>(null) }
    var deleting by remember { mutableStateOf<Pair<String, String>?>(null) }
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(vm.toast) {
        vm.toast?.let { snackbar.showSnackbar(it); vm.toast = null }
    }
    BackHandler(enabled = editing != null) { editing = null }

    val current = editing
    if (current != null) {
        ThemeEditor(
            initial = current.initial,
            isNew = current.replaceId == null,
            onSave = { p -> if (vm.saveTheme(p, current.replaceId)) editing = null },
            onCancel = { editing = null },
        )
        return
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.editor_theme)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } },
                actions = {
                    IconButton(onClick = onImport) { Icon(Icons.Filled.FileOpen, stringResource(R.string.theme_import)) }
                    IconButton(onClick = { editing = Editing(null, (vm.palette ?: Palettes.base(true)).copy(name = "")) }) {
                        Icon(Icons.Filled.Add, stringResource(R.string.theme_new))
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState())) {
            ThemeRow(
                name = stringResource(R.string.theme_follow_app),
                palette = null,
                selected = vm.editorTheme == "app",
                onSelect = { vm.selectTheme("app") },
            )
            Text(
                stringResource(R.string.theme_builtin),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(16.dp, 16.dp, 16.dp, 4.dp),
            )
            for ((id, p) in Palettes.builtIn) {
                ThemeRow(
                    name = p.name,
                    palette = p,
                    selected = vm.editorTheme == id,
                    onSelect = { vm.selectTheme(id) },
                    actions = {
                        IconButton(onClick = { editing = Editing(null, p.copy(name = p.name + " 2")) }) {
                            Icon(Icons.Filled.ContentCopy, stringResource(R.string.theme_duplicate))
                        }
                    },
                )
            }
            Text(
                stringResource(R.string.theme_mine),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(16.dp, 16.dp, 16.dp, 4.dp),
            )
            if (vm.userThemes.isEmpty()) {
                Text(
                    stringResource(R.string.theme_none),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
            for (t in vm.userThemes) {
                ThemeRow(
                    name = t.palette.name,
                    palette = t.palette,
                    selected = vm.editorTheme == t.id,
                    onSelect = { vm.selectTheme(t.id) },
                    actions = {
                        IconButton(onClick = { editing = Editing(t.id, t.palette) }) { Icon(Icons.Filled.Edit, stringResource(R.string.theme_edit)) }
                        IconButton(onClick = { onExport(t.palette) }) { Icon(Icons.Filled.SaveAlt, stringResource(R.string.theme_export)) }
                        IconButton(onClick = { deleting = t.id to t.palette.name }) { Icon(Icons.Filled.Delete, stringResource(R.string.delete)) }
                    },
                )
            }
            HorizontalDivider(Modifier.padding(top = 16.dp))
            Text(
                stringResource(R.string.theme_file_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp),
            )
        }
    }

    deleting?.let { (id, name) ->
        ConfirmDialog(
            title = stringResource(R.string.delete),
            message = stringResource(R.string.theme_delete_msg, name),
            confirmText = stringResource(R.string.delete),
            onConfirm = { vm.deleteTheme(id) },
            onDismiss = { deleting = null },
        )
    }
}

@Composable
private fun ThemeRow(
    name: String,
    palette: Palette?,
    selected: Boolean,
    onSelect: () -> Unit,
    actions: @Composable () -> Unit = {},
) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onSelect).padding(start = 8.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onSelect)
        Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
            Text(name, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
            if (palette != null) {
                Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Swatch(palette[PaletteKey.BACKGROUND], 28)
                    for (key in listOf(PaletteKey.KEYWORD, PaletteKey.STRING, PaletteKey.FUNCTION, PaletteKey.NUMBER, PaletteKey.COMMENT)) {
                        Swatch(palette[key], 18)
                    }
                }
            }
        }
        actions()
    }
}

@Composable
private fun Swatch(argb: Int, size: Int) {
    Box(
        Modifier
            .size(size.dp)
            .background(Color(argb), RoundedCornerShape(5.dp))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(5.dp)),
    )
}

private const val SAMPLE = """@app.route("/")
def hello(name="world"):
    # greet someone
    count = 42
    if count > 3:
        return f"Hello, {name}!"
    print(len(name))

class Bot:
    pass"""

private fun tokenKey(type: TokenType): PaletteKey = when (type) {
    TokenType.KEYWORD -> PaletteKey.KEYWORD
    TokenType.BUILTIN -> PaletteKey.BUILTIN
    TokenType.STRING -> PaletteKey.STRING
    TokenType.COMMENT -> PaletteKey.COMMENT
    TokenType.NUMBER -> PaletteKey.NUMBER
    TokenType.DECORATOR -> PaletteKey.DECORATOR
    TokenType.FUNCTION -> PaletteKey.FUNCTION
    TokenType.CLASS -> PaletteKey.CLASS
}

@Composable
private fun Preview(p: Palette) {
    val style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp, lineHeight = 17.sp)
    val lines = SAMPLE.lines().size
    val code = remember(p) {
        val spans = PyLexer.highlight(SAMPLE)
        buildAnnotatedString {
            var at = 0
            for (s in spans.sortedBy { it.start }) {
                if (s.start < at) continue
                withStyle(SpanStyle(color = Color(p[PaletteKey.TEXT]))) { append(SAMPLE.substring(at, s.start)) }
                withStyle(SpanStyle(color = Color(p[tokenKey(s.type)]))) { append(SAMPLE.substring(s.start, s.end)) }
                at = s.end
            }
            withStyle(SpanStyle(color = Color(p[PaletteKey.TEXT]))) { append(SAMPLE.substring(at)) }
        }
    }
    Column(Modifier.fillMaxWidth().background(Color(p[PaletteKey.BACKGROUND]), RoundedCornerShape(12.dp))) {
        Row {
            Text(
                (1..lines).joinToString("\n"),
                style = style.copy(color = Color(p[PaletteKey.GUTTER_TEXT])),
                modifier = Modifier
                    .background(Color(p[PaletteKey.GUTTER]), RoundedCornerShape(topStart = 12.dp))
                    .padding(horizontal = 8.dp, vertical = 8.dp),
            )
            Column(Modifier.padding(8.dp)) {
                Text(code, style = style)
                Text(
                    buildAnnotatedString {
                        withStyle(SpanStyle(background = Color(p[PaletteKey.SELECTION]), color = Color(p[PaletteKey.TEXT]))) { append("selected text") }
                        withStyle(SpanStyle(color = Color(p[PaletteKey.ERROR]))) { append("  error") }
                        withStyle(SpanStyle(color = Color(p[PaletteKey.WARNING]))) { append("  warning") }
                    },
                    style = style,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
        Text(
            "$ python main.py\nHello, world!",
            style = style.copy(color = Color(p[PaletteKey.CONSOLE_TEXT])),
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(p[PaletteKey.CONSOLE_BACKGROUND]), RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp))
                .padding(8.dp),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ThemeEditor(initial: Palette, isNew: Boolean, onSave: (Palette) -> Unit, onCancel: () -> Unit) {
    var name by remember { mutableStateOf(initial.name) }
    var author by remember { mutableStateOf(initial.author) }
    var dark by remember { mutableStateOf(initial.dark) }
    val colors = remember { mutableStateMapOf<PaletteKey, Int>().apply { putAll(initial.colors) } }
    var picking by remember { mutableStateOf<PaletteKey?>(null) }

    val palette = Palette(name.trim(), dark, PaletteKey.entries.associateWith { colors.getValue(it) }, author.trim())

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(if (isNew) R.string.theme_new else R.string.theme_edit)) },
                navigationIcon = { IconButton(onClick = onCancel) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.cancel)) } },
                actions = {
                    TextButton(onClick = { onSave(palette) }, enabled = name.isNotBlank()) { Text(stringResource(R.string.save)) }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
            Preview(palette)
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(Pmt.MAX_NAME) },
                label = { Text(stringResource(R.string.theme_name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            )
            OutlinedTextField(
                value = author,
                onValueChange = { author = it.take(Pmt.MAX_NAME) },
                label = { Text(stringResource(R.string.theme_author)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.theme_is_dark), Modifier.weight(1f))
                Switch(checked = dark, onCheckedChange = { dark = it })
            }
            Text(
                stringResource(R.string.theme_colors),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
            )
            for (key in PaletteKey.entries) {
                Row(
                    Modifier.fillMaxWidth().clickable { picking = key }.padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Swatch(colors.getValue(key), 32)
                    Text(key.label, Modifier.weight(1f).padding(start = 16.dp), style = MaterialTheme.typography.bodyLarge)
                    Text(
                        Pmt.formatColor(colors.getValue(key)),
                        style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }

    picking?.let { key ->
        ColorDialog(
            title = key.label,
            initial = colors.getValue(key),
            onPick = { colors[key] = it },
            onDismiss = { picking = null },
        )
    }
}

@Composable
private fun ColorDialog(title: String, initial: Int, onPick: (Int) -> Unit, onDismiss: () -> Unit) {
    var argb by remember { mutableIntStateOf(initial) }
    var hex by remember { mutableStateOf(Pmt.formatColor(initial)) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .background(Color(argb), RoundedCornerShape(8.dp))
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp)),
                )
                OutlinedTextField(
                    value = hex,
                    onValueChange = {
                        hex = it
                        Pmt.parseColor(it)?.let { c -> argb = c }
                    },
                    isError = Pmt.parseColor(hex) == null,
                    singleLine = true,
                    label = { Text("#RRGGBB") },
                    textStyle = TextStyle(fontFamily = FontFamily.Monospace),
                    keyboardOptions = KeyboardOptions(autoCorrectEnabled = false),
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                )
                for ((label, shift) in listOf("R" to 16, "G" to 8, "B" to 0)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(label, Modifier.width(20.dp))
                        Slider(
                            value = ((argb shr shift) and 0xFF).toFloat(),
                            onValueChange = { v ->
                                argb = (argb and (0xFF shl shift).inv()) or (v.toInt() shl shift)
                                hex = Pmt.formatColor(argb)
                            },
                            valueRange = 0f..255f,
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onPick(argb); onDismiss() }, enabled = Pmt.parseColor(hex) != null) { Text(stringResource(R.string.ok)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

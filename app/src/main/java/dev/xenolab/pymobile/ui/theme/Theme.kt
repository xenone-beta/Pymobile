package dev.xenolab.pymobile.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import dev.xenolab.pymobile.editor.TokenType
import dev.xenolab.pymobile.theme.Palette
import dev.xenolab.pymobile.theme.PaletteKey

/** Colors for source code and the console, tuned separately for light and dark. */
data class CodeColors(
    val keyword: Color,
    val builtin: Color,
    val string: Color,
    val comment: Color,
    val number: Color,
    val decorator: Color,
    val function: Color,
    val klass: Color,
    val error: Color,
    val warning: Color,
    val ansi: List<Color>,
    // Surfaces from a custom editor theme. Null means "use the app's Material colors".
    val background: Color? = null,
    val text: Color? = null,
    val gutter: Color? = null,
    val gutterText: Color? = null,
    val caret: Color? = null,
    val selection: Color? = null,
    val consoleBackground: Color? = null,
    val consoleText: Color? = null,
) {
    fun of(type: TokenType): Color = when (type) {
        TokenType.KEYWORD -> keyword
        TokenType.BUILTIN -> builtin
        TokenType.STRING -> string
        TokenType.COMMENT -> comment
        TokenType.NUMBER -> number
        TokenType.DECORATOR -> decorator
        TokenType.FUNCTION -> function
        TokenType.CLASS -> klass
    }
}

private val LightCode = CodeColors(
    keyword = Color(0xFF7B3FA0), builtin = Color(0xFF0B6E8E), string = Color(0xFF2E7D32),
    comment = Color(0xFF7A7F87), number = Color(0xFFB45309), decorator = Color(0xFF9A6700),
    function = Color(0xFF1D4ED8), klass = Color(0xFF0F766E),
    error = Color(0xFFB3261E), warning = Color(0xFFB26A00),
    ansi = ansiPalette(dark = false),
)

private val DarkCode = CodeColors(
    keyword = Color(0xFFD0A6F5), builtin = Color(0xFF7FD4EC), string = Color(0xFF9BD59F),
    comment = Color(0xFF8D939C), number = Color(0xFFFFB86B), decorator = Color(0xFFE5C06A),
    function = Color(0xFF9DB8FF), klass = Color(0xFF6FD6C8),
    error = Color(0xFFFFB4AB), warning = Color(0xFFFFC46B),
    ansi = ansiPalette(dark = true),
)

/** Index 0 is "default"; 1–8 are the normal colors, 9–16 the bright ones (matches ConsoleSegment.color). */
private fun ansiPalette(dark: Boolean): List<Color> = if (dark) listOf(
    Color.Unspecified,
    Color(0xFF6B7280), Color(0xFFFF7B72), Color(0xFF7EE787), Color(0xFFE3B341),
    Color(0xFF79C0FF), Color(0xFFD2A8FF), Color(0xFF56D4DD), Color(0xFFD0D7DE),
    Color(0xFF8B949E), Color(0xFFFFA198), Color(0xFFA5F3AE), Color(0xFFF2CC60),
    Color(0xFFA5D6FF), Color(0xFFE2C5FF), Color(0xFF8CE3EB), Color(0xFFFFFFFF),
) else listOf(
    Color.Unspecified,
    Color(0xFF24292F), Color(0xFFCF222E), Color(0xFF1A7F37), Color(0xFF9A6700),
    Color(0xFF0969DA), Color(0xFF8250DF), Color(0xFF1B7C83), Color(0xFF6E7781),
    Color(0xFF57606A), Color(0xFFA40E26), Color(0xFF2DA44E), Color(0xFFBF8700),
    Color(0xFF218BFF), Color(0xFFA475F9), Color(0xFF3192AA), Color(0xFF8C959F),
)

val LocalCodeColors = staticCompositionLocalOf { LightCode }

/** Turns an editor theme into the colors the editor and console use. */
fun codeColorsOf(p: Palette): CodeColors {
    fun c(key: PaletteKey) = Color(p[key])
    return CodeColors(
        keyword = c(PaletteKey.KEYWORD), builtin = c(PaletteKey.BUILTIN), string = c(PaletteKey.STRING),
        comment = c(PaletteKey.COMMENT), number = c(PaletteKey.NUMBER), decorator = c(PaletteKey.DECORATOR),
        function = c(PaletteKey.FUNCTION), klass = c(PaletteKey.CLASS),
        error = c(PaletteKey.ERROR), warning = c(PaletteKey.WARNING),
        ansi = ansiPalette(dark = p.dark),
        background = c(PaletteKey.BACKGROUND), text = c(PaletteKey.TEXT),
        gutter = c(PaletteKey.GUTTER), gutterText = c(PaletteKey.GUTTER_TEXT),
        caret = c(PaletteKey.CARET), selection = c(PaletteKey.SELECTION),
        consoleBackground = c(PaletteKey.CONSOLE_BACKGROUND), consoleText = c(PaletteKey.CONSOLE_TEXT),
    )
}

@Composable
fun PymobileTheme(themeMode: Int, dynamicColor: Boolean, palette: Palette? = null, content: @Composable () -> Unit) {
    // A chosen editor theme decides light or dark for the whole app so the screen matches the editor.
    val dark = palette?.dark ?: when (themeMode) {
        1 -> false
        2 -> true
        else -> isSystemInDarkTheme()
    }
    val context = LocalContext.current
    val scheme: ColorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> darkColorScheme(primary = Color(0xFFB9C3FF), secondary = Color(0xFFC4C5DD), tertiary = Color(0xFFE6BAD7))
        else -> lightColorScheme(primary = Color(0xFF4F5BD5), secondary = Color(0xFF5B5D72), tertiary = Color(0xFF77536D))
    }
    val code = remember(palette, dark) { if (palette != null) codeColorsOf(palette) else if (dark) DarkCode else LightCode }
    CompositionLocalProvider(LocalCodeColors provides code) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}

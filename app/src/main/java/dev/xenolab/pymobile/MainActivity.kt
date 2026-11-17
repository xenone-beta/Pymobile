package dev.xenolab.pymobile

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Surface
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.app.ActivityCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import dev.xenolab.pymobile.ui.editor.EditorScreen
import dev.xenolab.pymobile.ui.files.DrawerActions
import dev.xenolab.pymobile.ui.files.FilesDrawer
import dev.xenolab.pymobile.ui.git.GitScreen
import dev.xenolab.pymobile.ui.pip.PipScreen
import dev.xenolab.pymobile.ui.search.SearchScreen
import dev.xenolab.pymobile.ui.settings.SettingsScreen
import dev.xenolab.pymobile.ui.setup.SetupScreen
import dev.xenolab.pymobile.ui.themes.ThemesScreen
import dev.xenolab.pymobile.theme.Palette
import dev.xenolab.pymobile.theme.Pmt
import dev.xenolab.pymobile.theme.ThemeStore
import dev.xenolab.pymobile.ui.theme.PymobileTheme
import kotlinx.coroutines.launch

private enum class Screen { EDITOR, PIP, GIT, SETTINGS, THEMES, SEARCH }

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()

    private val pickFile = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.importFromUri(contentResolver, uri)
    }

    private val saveCopy = registerForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        if (uri != null) vm.exportTo(contentResolver, uri)
    }

    private val importTheme = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.importTheme(uri)
    }

    private var exportingPalette: Palette? = null

    private val exportTheme = registerForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        val p = exportingPalette
        if (uri != null && p != null) vm.exportTheme(p, uri)
        exportingPalette = null
    }

    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        val started = android.os.SystemClock.elapsedRealtime()
        // Hold the system splash while the interpreter is checked, but never for long: the app has its own loading screen.
        splash.setKeepOnScreenCondition {
            vm.setup.value is SetupState.Checking && android.os.SystemClock.elapsedRealtime() - started < 1200
        }
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) handleIntent(intent)
        setContent {
            PymobileTheme(vm.themeMode, vm.dynamicColor, vm.palette) {
                Surface(Modifier.fillMaxSize()) { Root() }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    /** "Open with" and "Share" from other apps. */
    @Suppress("DEPRECATION")
    private fun handleIntent(intent: Intent?) {
        if (intent == null) return
        val uri: Uri? = when (intent.action) {
            Intent.ACTION_VIEW, Intent.ACTION_EDIT -> intent.data
            Intent.ACTION_SEND ->
                if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                else intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
            else -> null
        }
        if (uri != null) vm.openExternal(uri)
    }

    override fun onResume() {
        super.onResume()
        vm.refreshStorage()
    }

    override fun onPause() {
        vm.flushPending()
        super.onPause()
    }

    private fun requestAllFiles() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:$packageName"))
            try {
                startActivity(intent)
            } catch (_: Exception) {
                startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
            }
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(android.Manifest.permission.WRITE_EXTERNAL_STORAGE, android.Manifest.permission.READ_EXTERNAL_STORAGE),
                1,
            )
        }
    }


    @Composable
    private fun Root() {
        val setup by vm.setup.collectAsState()
        if (setup !is SetupState.Ready) {
            SetupScreen(setup, onRetry = vm::retrySetup)
            return
        }
        var screen by remember { mutableStateOf(Screen.EDITOR) }
        val drawer = rememberDrawerState(DrawerValue.Closed)
        val scope = rememberCoroutineScope()
        val actions = remember {
            DrawerActions(
                onPickFile = { pickFile.launch(arrayOf("*/*")) },
                onExport = { saveCopy.launch(vm.activeTab?.file?.name ?: "script.py") },
                onPip = { screen = Screen.PIP },
                onGit = { screen = Screen.GIT },
                onSettings = { screen = Screen.SETTINGS },
                onSearch = { screen = Screen.SEARCH },
            )
        }

        LaunchedEffect(vm.askNotifications) {
            if (vm.askNotifications) {
                vm.askNotifications = false
                if (Build.VERSION.SDK_INT >= 33) notificationPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        BackHandler(enabled = screen != Screen.EDITOR) { screen = if (screen == Screen.THEMES) Screen.SETTINGS else Screen.EDITOR }
        BackHandler(enabled = screen == Screen.EDITOR && drawer.isOpen) { scope.launch { drawer.close() } }
        BackHandler(enabled = screen == Screen.EDITOR && !drawer.isOpen && vm.consoleVisible) {
            if (vm.consoleFull) vm.consoleFull = false else vm.consoleVisible = false
        }

        when (screen) {
            Screen.EDITOR -> ModalNavigationDrawer(
                drawerState = drawer,
                drawerContent = {
                    ModalDrawerSheet {
                        FilesDrawer(vm, actions, onOpened = { scope.launch { drawer.close() } }, onRequestAllFiles = ::requestAllFiles)
                    }
                },
            ) {
                EditorScreen(vm, onOpenDrawer = { scope.launch { drawer.open() } })
            }
            Screen.SEARCH -> SearchScreen(vm, onBack = { screen = Screen.EDITOR }, onOpened = { screen = Screen.EDITOR })
            Screen.PIP -> PipScreen(vm, onBack = { screen = Screen.EDITOR })
            Screen.GIT -> GitScreen(vm, onBack = { screen = Screen.EDITOR }, onSettings = { screen = Screen.SETTINGS })
            Screen.SETTINGS -> SettingsScreen(
                vm,
                onBack = { screen = Screen.EDITOR },
                onRequestAllFiles = ::requestAllFiles,
                onThemes = { screen = Screen.THEMES },
            )
            Screen.THEMES -> ThemesScreen(
                vm,
                onBack = { screen = Screen.SETTINGS },
                onImport = { importTheme.launch(arrayOf("*/*")) },
                onExport = { p ->
                    exportingPalette = p
                    exportTheme.launch(ThemeStore.slug(p.name) + "." + Pmt.EXTENSION)
                },
            )
        }
    }
}

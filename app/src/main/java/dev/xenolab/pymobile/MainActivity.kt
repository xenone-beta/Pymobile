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
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Surface
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.app.ActivityCompat
import dev.xenolab.pymobile.ui.editor.EditorScreen
import dev.xenolab.pymobile.ui.files.FilesDrawer
import dev.xenolab.pymobile.ui.git.GitScreen
import dev.xenolab.pymobile.ui.pip.PipScreen
import dev.xenolab.pymobile.ui.settings.SettingsScreen
import dev.xenolab.pymobile.ui.setup.SetupScreen
import dev.xenolab.pymobile.ui.theme.PymobileTheme
import kotlinx.coroutines.launch

private enum class Screen { EDITOR, PIP, GIT, SETTINGS }

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PymobileTheme(vm.themeMode, vm.dynamicColor) {
                Surface(Modifier.fillMaxSize()) { Root() }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        vm.refreshStorage()
    }

    override fun onPause() {
        vm.saveAll()
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

        BackHandler(enabled = screen != Screen.EDITOR) { screen = Screen.EDITOR }
        BackHandler(enabled = screen == Screen.EDITOR && drawer.isOpen) { scope.launch { drawer.close() } }
        BackHandler(enabled = screen == Screen.EDITOR && !drawer.isOpen && vm.consoleVisible) {
            if (vm.consoleFull) vm.consoleFull = false else vm.consoleVisible = false
        }

        when (screen) {
            Screen.EDITOR -> ModalNavigationDrawer(
                drawerState = drawer,
                drawerContent = {
                    ModalDrawerSheet {
                        FilesDrawer(vm, onOpened = { scope.launch { drawer.close() } }, onRequestAllFiles = ::requestAllFiles)
                    }
                },
            ) {
                EditorScreen(
                    vm,
                    onOpenDrawer = { scope.launch { drawer.open() } },
                    onPip = { screen = Screen.PIP },
                    onGit = { screen = Screen.GIT },
                    onSettings = { screen = Screen.SETTINGS },
                )
            }
            Screen.PIP -> PipScreen(vm, onBack = { screen = Screen.EDITOR })
            Screen.GIT -> GitScreen(vm, onBack = { screen = Screen.EDITOR }, onSettings = { screen = Screen.SETTINGS })
            Screen.SETTINGS -> SettingsScreen(vm, onBack = { screen = Screen.EDITOR }, onRequestAllFiles = ::requestAllFiles)
        }
    }
}

package dev.xenolab.pymobile

import android.app.Application
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.xenolab.pymobile.data.AppSettings
import dev.xenolab.pymobile.editor.ConsoleBuffer
import dev.xenolab.pymobile.editor.ConsoleLine
import dev.xenolab.pymobile.editor.Formatter
import dev.xenolab.pymobile.editor.UndoStack
import dev.xenolab.pymobile.git.GitCommitInfo
import dev.xenolab.pymobile.git.GitCredentials
import dev.xenolab.pymobile.git.GitService
import dev.xenolab.pymobile.git.GitStatusInfo
import dev.xenolab.pymobile.project.EditorTab
import dev.xenolab.pymobile.project.FileError
import dev.xenolab.pymobile.project.FileOp
import dev.xenolab.pymobile.project.ProjectFiles
import dev.xenolab.pymobile.python.Pip
import dev.xenolab.pymobile.python.PipPackage
import dev.xenolab.pymobile.python.PythonPaths
import dev.xenolab.pymobile.python.PythonPayload
import dev.xenolab.pymobile.python.PythonRequest
import dev.xenolab.pymobile.python.PythonRunner
import dev.xenolab.pymobile.python.PythonSession
import dev.xenolab.pymobile.python.RunListener
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface SetupState {
    data object Checking : SetupState
    data class Installing(val progress: Float) : SetupState
    data class Ready(val pythonVersion: String) : SetupState
    data class Failed(val log: String) : SetupState
}

/** A request to put the caret on a line of a file, for example from a traceback. */
data class Jump(val path: String, val line: Int)

class MainViewModel(private val app: Application) : AndroidViewModel(app) {

    private val settings = AppSettings(app)
    private val paths = PythonPaths(app)
    private val runner = PythonRunner(app, viewModelScope)
    private val git = GitService()

    // ---- setup ----
    private val _setup = MutableStateFlow<SetupState>(SetupState.Checking)
    val setup: StateFlow<SetupState> = _setup.asStateFlow()

    // ---- settings that the UI reads ----
    var fontSize by mutableIntStateOf(settings.fontSize); private set
    var themeMode by mutableIntStateOf(settings.themeMode); private set
    var dynamicColor by mutableStateOf(settings.dynamicColor); private set
    var lineNumbers by mutableStateOf(settings.lineNumbers); private set
    var autoPair by mutableStateOf(settings.autoPair); private set
    var formatOnSave by mutableStateOf(settings.formatOnSave); private set
    var gitName by mutableStateOf(settings.gitName); private set
    var gitEmail by mutableStateOf(settings.gitEmail); private set
    var gitToken by mutableStateOf(settings.gitToken); private set

    // ---- files and tabs ----
    var hasAllFiles by mutableStateOf(false); private set
    var root by mutableStateOf(File("/")); private set
    var currentDir by mutableStateOf(File("/")); private set
    var entries by mutableStateOf<List<File>>(emptyList()); private set
    val tabs = mutableStateListOf<EditorTab>()
    var activeIndex by mutableIntStateOf(0); private set
    var jump by mutableStateOf<Jump?>(null); private set
    var toast by mutableStateOf<String?>(null)

    val activeTab: EditorTab? get() = tabs.getOrNull(activeIndex)

    // ---- running ----
    private val console = ConsoleBuffer()
    private val _consoleLines = MutableStateFlow<List<ConsoleLine>>(emptyList())
    val consoleLines: StateFlow<List<ConsoleLine>> = _consoleLines.asStateFlow()
    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running.asStateFlow()
    private var session: PythonSession? = null
    private var consoleUpdatePending = false
    var consoleVisible by mutableStateOf(false)
    var consoleFull by mutableStateOf(false)

    // ---- pip ----
    private val pipConsole = ConsoleBuffer()
    private val _pipLines = MutableStateFlow<List<ConsoleLine>>(emptyList())
    val pipLines: StateFlow<List<ConsoleLine>> = _pipLines.asStateFlow()
    private val _pipPackages = MutableStateFlow<List<PipPackage>?>(null)
    val pipPackages: StateFlow<List<PipPackage>?> = _pipPackages.asStateFlow()
    private val _pipBusy = MutableStateFlow(false)
    val pipBusy: StateFlow<Boolean> = _pipBusy.asStateFlow()
    private var pipSession: PythonSession? = null

    // ---- git ----
    private val _gitStatus = MutableStateFlow<GitStatusInfo?>(null)
    val gitStatus: StateFlow<GitStatusInfo?> = _gitStatus.asStateFlow()
    private val _gitLog = MutableStateFlow<List<GitCommitInfo>>(emptyList())
    val gitLog: StateFlow<List<GitCommitInfo>> = _gitLog.asStateFlow()
    private val _gitBusy = MutableStateFlow(false)
    val gitBusy: StateFlow<Boolean> = _gitBusy.asStateFlow()
    var gitDir by mutableStateOf<File?>(null); private set

    init {
        // JGit looks for ~/.gitconfig; give it a harmless home.
        System.setProperty("user.home", paths.homeDir.absolutePath)
        refreshStorage()
        viewModelScope.launch { prepare() }
    }

    // =====================================================================================
    // Setup
    // =====================================================================================

    fun retrySetup() {
        _setup.value = SetupState.Checking
        viewModelScope.launch { prepare() }
    }

    private suspend fun prepare() {
        val state = withContext(Dispatchers.IO) {
            try {
                if (!PythonPayload.isInstalled(app)) {
                    _setup.value = SetupState.Installing(0f)
                    PythonPayload.install(app) { _setup.value = SetupState.Installing(it) }
                } else {
                    paths.ensureDirs()
                }
                val check = runner.capture(
                    PythonRequest(listOf("-c", "import sys, ssl, sqlite3, zlib; print(sys.version.split()[0])"), app.filesDir),
                    timeoutMs = 90_000,
                )
                when {
                    check == null -> SetupState.Failed(diagnostics("The Python self-test took longer than 90 seconds."))
                    check.exitCode != 0 -> SetupState.Failed(diagnostics(check.output.ifBlank { "Exit code ${check.exitCode}" }))
                    else -> SetupState.Ready(check.output.trim())
                }
            } catch (t: Throwable) {
                SetupState.Failed(diagnostics(t.stackTraceToString().take(3000)))
            }
        }
        _setup.value = state
        if (state is SetupState.Ready) restoreTabs()
    }

    private fun diagnostics(detail: String): String = buildString {
        appendLine(detail.trim())
        appendLine()
        appendLine("App ${BuildConfig.VERSION_NAME}, Python ${BuildConfig.PYTHON_VERSION}")
        appendLine("Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}), ABIs: ${Build.SUPPORTED_ABIS.joinToString()}")
        if ("arm64-v8a" !in Build.SUPPORTED_ABIS) appendLine("This build contains Python for arm64-v8a only.")
    }

    // =====================================================================================
    // Settings
    // =====================================================================================

    fun updateFontSize(v: Int) { settings.fontSize = v; fontSize = settings.fontSize }
    fun updateThemeMode(v: Int) { settings.themeMode = v; themeMode = v }
    fun updateDynamicColor(v: Boolean) { settings.dynamicColor = v; dynamicColor = v }
    fun updateLineNumbers(v: Boolean) { settings.lineNumbers = v; lineNumbers = v }
    fun updateAutoPair(v: Boolean) { settings.autoPair = v; autoPair = v }
    fun updateFormatOnSave(v: Boolean) { settings.formatOnSave = v; formatOnSave = v }
    fun updateGitName(v: String) { settings.gitName = v.trim(); gitName = settings.gitName }
    fun updateGitEmail(v: String) { settings.gitEmail = v.trim(); gitEmail = settings.gitEmail }
    fun updateGitToken(v: String) { settings.gitToken = v.trim(); gitToken = settings.gitToken }

    // =====================================================================================
    // Storage and the file browser
    // =====================================================================================

    private fun allFilesGranted(): Boolean = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> Environment.isExternalStorageManager()
        Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ->
            app.checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        else -> false
    }

    /** Re-reads the storage permission; call again when the app comes back to the screen. */
    fun refreshStorage() {
        hasAllFiles = allFilesGranted()
        val shared = File(Environment.getExternalStorageDirectory(), "Pymobile")
        val fallback = app.getExternalFilesDir("projects") ?: File(app.filesDir, "projects")
        val wanted = if (hasAllFiles) File(settings.projectsRoot.ifBlank { shared.path }) else fallback
        val folder = if (wanted.isDirectory || wanted.mkdirs()) wanted else fallback.also { it.mkdirs() }
        val changed = folder.absolutePath != root.absolutePath
        root = folder
        if (changed || !currentDir.absolutePath.startsWith(root.absolutePath)) currentDir = root
        refreshEntries()
    }

    fun refreshEntries() {
        entries = ProjectFiles.list(currentDir)
    }

    fun enter(dir: File) {
        currentDir = dir
        refreshEntries()
    }

    fun goUp() {
        val parent = currentDir.parentFile ?: return
        if (currentDir.absolutePath == root.absolutePath) return
        enter(parent)
    }

    private fun report(op: FileOp, ok: () -> Unit) {
        when {
            op.ok -> ok()
            op.error == FileError.BAD_NAME -> toast = app.getString(R.string.msg_bad_name)
            op.error == FileError.EXISTS -> toast = app.getString(R.string.msg_exists)
            else -> toast = app.getString(R.string.msg_failed)
        }
    }

    fun newFile(name: String) {
        val fileName = if ('.' in name.trim()) name.trim() else name.trim() + ".py"
        report(ProjectFiles.create(currentDir, fileName, folder = false)) {
            refreshEntries()
            openFile(File(currentDir, fileName))
        }
    }

    fun newFolder(name: String) {
        report(ProjectFiles.create(currentDir, name, folder = true)) { refreshEntries() }
    }

    fun newProject(name: String) {
        val op = ProjectFiles.createProject(currentDir, name)
        report(op) {
            refreshEntries()
            op.file?.let { openFile(File(it, "main.py")) }
        }
    }

    fun rename(file: File, newName: String) {
        tabs.filter { it.file.absolutePath.startsWith(file.absolutePath) }.forEach { save(it) }
        val wasOpen = tabs.any { it.file == file }
        val op = ProjectFiles.rename(file, newName)
        report(op) {
            if (wasOpen) closeFileTabs(file)
            else closeTabsUnder(file)
            refreshEntries()
            op.file?.takeIf { wasOpen }?.let { openFile(it) }
        }
    }

    fun delete(file: File) {
        if (ProjectFiles.delete(file)) {
            closeTabsUnder(file)
            refreshEntries()
        } else {
            toast = app.getString(R.string.msg_failed)
        }
    }

    private fun closeFileTabs(file: File) {
        tabs.indexOfFirst { it.file == file }.takeIf { it >= 0 }?.let { forceClose(it) }
    }

    private fun closeTabsUnder(path: File) {
        val prefix = path.absolutePath
        for (i in tabs.indices.reversed()) {
            val p = tabs[i].file.absolutePath
            if (p == prefix || p.startsWith(prefix + File.separator)) forceClose(i)
        }
    }

    // =====================================================================================
    // Tabs and editing
    // =====================================================================================

    private fun restoreTabs() {
        if (tabs.isNotEmpty()) return
        viewModelScope.launch {
            val opened = withContext(Dispatchers.IO) {
                settings.openTabs.map(::File).filter { it.isFile && ProjectFiles.isEditable(it) }
                    .map { it to ProjectFiles.read(it) }
            }
            if (tabs.isEmpty()) opened.forEach { (f, text) -> tabs += EditorTab(f, text) }
            val active = settings.activeTab
            activeIndex = tabs.indexOfFirst { it.file.absolutePath == active }.takeIf { it >= 0 } ?: 0
            if (tabs.isEmpty() && ProjectFiles.list(root).isEmpty()) {
                val made = ProjectFiles.createProject(root, "HelloPython")
                made.file?.let {
                    refreshEntries()
                    openFile(File(it, "main.py"))
                }
            }
        }
    }

    private fun persistTabs() {
        settings.openTabs = tabs.map { it.file.absolutePath }
        settings.activeTab = activeTab?.file?.absolutePath ?: ""
    }

    fun openFile(file: File, line: Int? = null) {
        val existing = tabs.indexOfFirst { it.file.absolutePath == file.absolutePath }
        if (existing >= 0) {
            select(existing)
            if (line != null) jump = Jump(file.absolutePath, line)
            return
        }
        if (!ProjectFiles.isEditable(file)) {
            toast = app.getString(R.string.msg_not_editable)
            return
        }
        viewModelScope.launch {
            val text = withContext(Dispatchers.IO) { runCatching { ProjectFiles.read(file) }.getOrNull() }
            if (text == null) {
                toast = app.getString(R.string.msg_failed)
                return@launch
            }
            tabs += EditorTab(file, text)
            activeIndex = tabs.lastIndex
            if (line != null) jump = Jump(file.absolutePath, line)
            persistTabs()
        }
    }

    fun select(index: Int) {
        if (index in tabs.indices) {
            activeIndex = index
            persistTabs()
        }
    }

    fun consumeJump() {
        jump = null
    }

    /** Closes a tab. Returns false if it has unsaved changes (the caller should ask first). */
    fun closeTab(index: Int, force: Boolean = false): Boolean {
        val tab = tabs.getOrNull(index) ?: return true
        if (tab.dirty && !force) return false
        forceClose(index)
        return true
    }

    private fun forceClose(index: Int) {
        if (index !in tabs.indices) return
        tabs.removeAt(index)
        activeIndex = when {
            tabs.isEmpty() -> 0
            index < activeIndex -> activeIndex - 1
            activeIndex >= tabs.size -> tabs.lastIndex
            else -> activeIndex
        }
        persistTabs()
    }

    fun saveActive() {
        activeTab?.let { save(it) }
    }

    fun save(tab: EditorTab): Boolean {
        var text = tab.value.text
        if (formatOnSave && tab.file.extension == "py") {
            val formatted = Formatter.format(text)
            if (formatted != text) {
                replaceText(tab, formatted)
                text = formatted
            }
        }
        val ok = ProjectFiles.write(tab.file, text)
        if (ok) tab.saved = text else toast = app.getString(R.string.msg_failed)
        return ok
    }

    fun saveAll() {
        tabs.filter { it.dirty }.forEach { save(it) }
    }

    /** Puts [text] into the tab as one undoable step, keeping the caret near where it was. */
    fun replaceText(tab: EditorTab, text: String) {
        val old = tab.value
        val caret = old.selection.start.coerceAtMost(text.length)
        tab.undo.record(
            UndoStack.Snapshot(old.text, old.selection.start, old.selection.end),
            UndoStack.Snapshot(text, caret, caret),
            System.currentTimeMillis(),
        )
        tab.value = TextFieldValue(text, TextRange(caret))
    }

    fun formatActive() {
        val tab = activeTab ?: return
        val formatted = Formatter.format(tab.value.text)
        if (formatted != tab.value.text) replaceText(tab, formatted)
        toast = app.getString(R.string.msg_formatted)
    }

    /** Re-reads files that changed on disk (after a pull). Tabs with unsaved edits are left alone. */
    private fun reloadTabs() {
        for (tab in tabs.toList()) {
            if (tab.dirty) continue
            if (!tab.file.isFile) {
                closeFileTabs(tab.file)
                continue
            }
            val text = runCatching { ProjectFiles.read(tab.file) }.getOrNull() ?: continue
            if (text != tab.value.text) {
                tab.value = TextFieldValue(text, TextRange(0))
                tab.saved = text
                tab.undo.clear()
            }
        }
    }

    // =====================================================================================
    // Running
    // =====================================================================================

    private fun scheduleConsoleUpdate() {
        if (consoleUpdatePending) return
        consoleUpdatePending = true
        viewModelScope.launch {
            delay(50)
            consoleUpdatePending = false
            _consoleLines.value = console.snapshot()
        }
    }

    private fun note(text: String) {
        console.append("\u001B[90m$text\u001B[0m\n")
    }

    fun run() {
        val tab = activeTab ?: return
        if (_running.value) return
        if (_setup.value !is SetupState.Ready) {
            toast = app.getString(R.string.msg_python_not_ready)
            return
        }
        saveAll()
        console.clear()
        note("▶ ${tab.file.name}")
        _consoleLines.value = console.snapshot()
        consoleVisible = true
        _running.value = true
        session = runner.start(
            PythonRequest(listOf(tab.file.absolutePath), tab.file.parentFile ?: root),
            object : RunListener {
                override fun onOutput(text: String, isError: Boolean) {
                    console.append(text, isError)
                    scheduleConsoleUpdate()
                }

                override fun onExit(code: Int) {
                    note(
                        when (code) {
                            PythonSession.EXIT_STOPPED -> app.getString(R.string.run_stopped)
                            PythonSession.EXIT_CRASHED -> app.getString(R.string.run_crashed)
                            else -> app.getString(R.string.run_finished, code)
                        },
                    )
                    _consoleLines.value = console.snapshot()
                    _running.value = false
                    session = null
                }
            },
        )
    }

    fun stop() {
        session?.stop()
    }

    fun sendInput(text: String) {
        val s = session ?: return
        console.appendInput(text + "\n")
        s.writeInput(text + "\n")
        scheduleConsoleUpdate()
    }

    fun closeInput() {
        session?.closeInput()
    }

    fun clearConsole() {
        console.clear()
        _consoleLines.value = emptyList()
    }

    /** Opens the file named in a traceback line. */
    fun openTraceback(file: String, line: Int) {
        val f = File(file)
        if (f.isFile) openFile(f, line)
    }

    // =====================================================================================
    // pip
    // =====================================================================================

    val requirementsFile: File?
        get() {
            val dir = activeTab?.file?.parentFile ?: currentDir
            return File(dir, "requirements.txt").takeIf { it.isFile }
        }

    fun pipRefresh() {
        if (_setup.value !is SetupState.Ready || _pipBusy.value) return
        viewModelScope.launch {
            _pipBusy.value = true
            val result = runner.capture(PythonRequest(Pip.listArgs, app.filesDir), 60_000)
            _pipPackages.value = if (result != null && result.exitCode == 0) Pip.parseList(result.output) else _pipPackages.value
            _pipBusy.value = false
        }
    }

    private fun pipRun(args: List<String>, label: String) {
        if (_pipBusy.value || _setup.value !is SetupState.Ready) return
        _pipBusy.value = true
        pipConsole.clear()
        pipConsole.append("\u001B[90m$ pip $label\u001B[0m\n")
        _pipLines.value = pipConsole.snapshot()
        pipSession = runner.start(
            PythonRequest(args, currentDir.takeIf { it.isDirectory } ?: app.filesDir),
            object : RunListener {
                override fun onOutput(text: String, isError: Boolean) {
                    pipConsole.append(text, isError)
                    _pipLines.value = pipConsole.snapshot()
                }

                override fun onExit(code: Int) {
                    pipConsole.append(
                        "\u001B[90m" + (if (code == 0) app.getString(R.string.pip_done) else app.getString(R.string.pip_failed, code)) + "\u001B[0m\n",
                    )
                    _pipLines.value = pipConsole.snapshot()
                    pipSession = null
                    _pipBusy.value = false
                    pipRefresh()
                }
            },
        )
    }

    fun pipInstall(input: String, upgrade: Boolean = false) {
        val requirements = Pip.parseRequirements(input)
        if (requirements == null) {
            toast = app.getString(R.string.msg_pip_bad)
            return
        }
        pipRun(Pip.installArgs(requirements, upgrade), "install ${requirements.joinToString(" ")}")
    }

    fun pipUninstall(name: String) = pipRun(Pip.uninstallArgs(name), "uninstall $name")

    fun pipInstallRequirements() {
        val file = requirementsFile
        if (file == null) {
            toast = app.getString(R.string.msg_no_requirements)
            return
        }
        pipRun(Pip.requirementsFileArgs(file.absolutePath), "install -r requirements.txt")
    }

    fun pipCancel() {
        pipSession?.stop()
    }

    // =====================================================================================
    // Git
    // =====================================================================================

    /** The project folder of the open file (the first folder below the projects root), else the browser folder. */
    private fun projectDir(): File {
        val start = activeTab?.file?.parentFile ?: currentDir
        var dir: File? = start
        while (dir != null) {
            if (dir.parentFile?.absolutePath == root.absolutePath) return dir
            dir = dir.parentFile
        }
        return start
    }

    fun gitRefresh() {
        val dir = projectDir()
        gitDir = dir
        viewModelScope.launch {
            val (status, log) = withContext(Dispatchers.IO) {
                runCatching { git.status(dir) to git.log(dir) }.getOrElse { GitStatusInfo(false) to emptyList() }
            }
            _gitStatus.value = status
            _gitLog.value = log
        }
    }

    private fun credentials() = GitCredentials("token", gitToken)

    private fun gitTask(done: String?, block: suspend (File) -> Unit) {
        val dir = gitDir ?: projectDir()
        if (_gitBusy.value) return
        viewModelScope.launch {
            _gitBusy.value = true
            try {
                withContext(Dispatchers.IO) { block(dir) }
                if (done != null) toast = done
            } catch (e: Exception) {
                toast = app.getString(R.string.msg_git_error, e.message ?: e.javaClass.simpleName)
            } finally {
                _gitBusy.value = false
                reloadTabs()
                refreshEntries()
                gitRefresh()
            }
        }
    }

    fun gitInit() = gitTask(app.getString(R.string.msg_git_inited)) { git.init(it) }

    fun gitCommit(message: String) {
        if (gitName.isBlank() || gitEmail.isBlank()) {
            toast = app.getString(R.string.msg_git_need_identity)
            return
        }
        saveAll()
        gitTask(null) {
            val id = git.commitAll(it, message, gitName, gitEmail)
            toast = app.getString(R.string.msg_git_committed, id)
        }
    }

    fun gitPush() = gitTask(app.getString(R.string.msg_git_pushed)) { git.push(it, credentials()) }

    fun gitPull() = gitTask(app.getString(R.string.msg_git_pulled)) { git.pull(it, credentials()) }

    fun gitClone(url: String) {
        if (!GitService.isSupportedUrl(url)) {
            toast = app.getString(R.string.msg_git_bad_url)
            return
        }
        val dest = File(root, GitService.repoNameFromUrl(url))
        if (dest.exists()) {
            toast = app.getString(R.string.msg_exists)
            return
        }
        gitTask(app.getString(R.string.msg_git_cloned)) { git.clone(url, dest, credentials()) }
    }

    override fun onCleared() {
        session?.stop()
        pipSession?.stop()
        super.onCleared()
    }
}

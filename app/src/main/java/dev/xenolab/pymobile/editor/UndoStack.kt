package dev.xenolab.pymobile.editor

/** Undo and redo for one open file. Quick typing in a row is undone in one step. */
class UndoStack(private val limit: Int = 200) {

    data class Snapshot(val text: String, val start: Int, val end: Int)

    private val undoList = ArrayList<Snapshot>()
    private val redoList = ArrayList<Snapshot>()
    private var lastTime = 0L
    private var lastWasTyping = false

    val canUndo: Boolean get() = undoList.isNotEmpty()
    val canRedo: Boolean get() = redoList.isNotEmpty()

    /** Remembers that the text went from [before] to [after]. */
    fun record(before: Snapshot, after: Snapshot, now: Long) {
        if (before.text == after.text) return
        val typing = kotlin.math.abs(after.text.length - before.text.length) == 1 && after.start == after.end
        val join = typing && lastWasTyping && undoList.isNotEmpty() && now - lastTime < JOIN_MS
        if (!join) {
            undoList += before
            if (undoList.size > limit) undoList.removeAt(0)
        }
        redoList.clear()
        lastWasTyping = typing
        lastTime = now
    }

    fun undo(current: Snapshot): Snapshot? {
        if (undoList.isEmpty()) return null
        redoList += current
        lastWasTyping = false
        return undoList.removeAt(undoList.size - 1)
    }

    fun redo(current: Snapshot): Snapshot? {
        if (redoList.isEmpty()) return null
        undoList += current
        lastWasTyping = false
        return redoList.removeAt(redoList.size - 1)
    }

    fun clear() {
        undoList.clear()
        redoList.clear()
        lastWasTyping = false
    }

    private companion object {
        const val JOIN_MS = 1000L
    }
}

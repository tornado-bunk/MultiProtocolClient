package it.tornado.multiprotocolclient.protocol.terminal

import com.termux.terminal.TerminalEmulator
import com.termux.terminal.TerminalOutput
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionClient

/**
 * A lightweight, process-less terminal session that wraps a [TerminalEmulator] and
 * bridges input/output between the emulator and a remote byte stream.
 *
 * Termux's [TerminalSession] spawns a local subprocess via JNI, which is not what we
 * want here: our bytes come from a network protocol. This class mimics what we need
 * from a session without touching JNI.
 */
class RemoteTerminalSession {

    interface Listener {
        fun onTextChanged()
        fun onTitleChanged(newTitle: String?)
        fun onSessionFinished()
        fun onBell()
    }

    /** Sink for bytes typed by the user: forwarded to the remote side. */
    @Volatile
    var remoteSink: ((ByteArray, Int, Int) -> Unit)? = null

    var title: String? = null
        private set

    val output: TerminalOutput = object : TerminalOutput() {
        override fun write(data: ByteArray, offset: Int, count: Int) {
            remoteSink?.invoke(data, offset, count)
        }

        override fun titleChanged(oldTitle: String?, newTitle: String?) {
            title = newTitle
            listeners.toList().forEach { it.onTitleChanged(newTitle) }
        }

        override fun onCopyTextToClipboard(text: String?) {}

        override fun onPasteTextFromClipboard() {}

        override fun onBell() {
            listeners.toList().forEach { it.onBell() }
        }

        override fun onColorsChanged() {}
    }

    val emulatorClient: TerminalSessionClient = object : TerminalSessionClient {
        override fun onTextChanged(changedSession: TerminalSession) {}
        override fun onTitleChanged(changedSession: TerminalSession) {}
        override fun onSessionFinished(finishedSession: TerminalSession) {}
        override fun onCopyTextToClipboard(session: TerminalSession, text: String?) {}
        override fun onPasteTextFromClipboard(session: TerminalSession?) {}
        override fun onBell(session: TerminalSession) {}
        override fun onColorsChanged(session: TerminalSession) {}
        override fun onTerminalCursorStateChange(state: Boolean) {}
        override fun getTerminalCursorStyle(): Int? = null
        override fun logError(tag: String?, message: String?) {}
        override fun logWarn(tag: String?, message: String?) {}
        override fun logInfo(tag: String?, message: String?) {}
        override fun logDebug(tag: String?, message: String?) {}
        override fun logVerbose(tag: String?, message: String?) {}
        override fun logStackTraceWithMessage(tag: String?, message: String?, e: Exception?) {}
        override fun logStackTrace(tag: String?, e: Exception?) {}
    }

    @Volatile
    var emulator: TerminalEmulator? = null
        private set

    private val listeners = mutableListOf<Listener>()

    /**
     * Bytes received from the remote peer before the emulator was constructed (e.g. SSH
     * banner / shell prompt arriving while the user is still navigating to the console
     * screen). They are replayed into the emulator as soon as it exists so that the
     * first frame shown already contains the accumulated output.
     */
    private val pendingBuffer = java.io.ByteArrayOutputStream()

    @Volatile
    private var finished: Boolean = false

    /** Lazily builds the emulator on the first size update from the view. */
    @Synchronized
    fun ensureEmulator(cols: Int, rows: Int) {
        val safeCols = cols.coerceAtLeast(4)
        val safeRows = rows.coerceAtLeast(4)
        val current = emulator
        if (current == null) {
            val emu = TerminalEmulator(
                output,
                safeCols,
                safeRows,
                TRANSCRIPT_ROWS,
                emulatorClient
            )
            emulator = emu
            if (pendingBuffer.size() > 0) {
                val buffered = pendingBuffer.toByteArray()
                pendingBuffer.reset()
                emu.append(buffered, buffered.size)
            }
            notifyTextChanged()
        } else if (safeCols != current.mColumns || safeRows != current.mRows) {
            current.resize(safeCols, safeRows)
            notifyTextChanged()
        }
    }

    /** Feed bytes coming from the remote peer into the emulator. */
    @Synchronized
    fun feed(data: ByteArray, length: Int) {
        if (length <= 0) return
        val emu = emulator
        if (emu == null) {
            pendingBuffer.write(data, 0, length)
            return
        }
        emu.append(data, length)
        notifyTextChanged()
    }

    /** Send raw bytes to the remote peer (used by the console view for key input). */
    fun sendToRemote(data: ByteArray, offset: Int = 0, length: Int = data.size) {
        if (finished || length <= 0) return
        remoteSink?.invoke(data, offset, length)
    }

    fun sendToRemote(text: String) {
        if (text.isEmpty()) return
        val bytes = text.toByteArray(Charsets.UTF_8)
        sendToRemote(bytes, 0, bytes.size)
    }

    fun markFinished() {
        if (finished) return
        finished = true
        listeners.toList().forEach { it.onSessionFinished() }
    }

    fun addListener(listener: Listener) {
        listeners.add(listener)
    }

    fun removeListener(listener: Listener) {
        listeners.remove(listener)
    }

    private fun notifyTextChanged() {
        listeners.toList().forEach { it.onTextChanged() }
    }

    companion object {
        private const val TRANSCRIPT_ROWS = 5000
    }
}

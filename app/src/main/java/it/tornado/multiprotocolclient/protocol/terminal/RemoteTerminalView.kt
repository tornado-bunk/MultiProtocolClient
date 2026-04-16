package it.tornado.multiprotocolclient.protocol.terminal

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Typeface
import android.text.InputType
import android.util.AttributeSet
import android.util.TypedValue
import android.view.GestureDetector
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.BaseInputConnection
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import com.termux.terminal.KeyHandler
import com.termux.terminal.TerminalEmulator
import com.termux.view.TerminalRenderer
import kotlin.math.max

/**
 * A minimal, self-contained Android [View] that renders a [TerminalEmulator] from
 * Termux without relying on Termux's own `TerminalView` (which is `final` and tied
 * to a JNI-backed `TerminalSession`).
 */
class RemoteTerminalView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private var session: RemoteTerminalSession? = null
    private var renderer: TerminalRenderer = buildRenderer(DEFAULT_TEXT_SIZE_SP)

    private var textSizeSp: Float = DEFAULT_TEXT_SIZE_SP
    private var topRow: Int = 0
    private var scrollRemainderY: Float = 0f

    private val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onSingleTapUp(e: MotionEvent): Boolean {
            requestFocus()
            val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? android.view.inputmethod.InputMethodManager
            imm?.showSoftInput(this@RemoteTerminalView, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
            return true
        }

        override fun onScroll(
            e1: MotionEvent?,
            e2: MotionEvent,
            distanceX: Float,
            distanceY: Float
        ): Boolean {
            val emu = session?.emulator ?: return false
            val lineSpacing = renderer.fontLineSpacing.coerceAtLeast(1)
            scrollRemainderY += distanceY
            val rowsDelta = (scrollRemainderY / lineSpacing).toInt()
            if (rowsDelta == 0) return true
            scrollRemainderY -= rowsDelta * lineSpacing

            val activeRows = emu.screen.activeRows
            val minTop = -(activeRows - emu.mRows).coerceAtLeast(0)
            topRow = (topRow + rowsDelta).coerceIn(minTop, 0)
            invalidate()
            return true
        }
    })

    private val sessionListener = object : RemoteTerminalSession.Listener {
        override fun onTextChanged() {
            post {
                topRow = 0
                invalidate()
            }
        }

        override fun onTitleChanged(newTitle: String?) {}

        override fun onSessionFinished() {
            post { invalidate() }
        }

        override fun onBell() {
            post { performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP) }
        }
    }

    init {
        isFocusable = true
        isFocusableInTouchMode = true
        setBackgroundColor(Color.BLACK)
    }

    fun attachSession(newSession: RemoteTerminalSession?) {
        session?.removeListener(sessionListener)
        session = newSession
        newSession?.addListener(sessionListener)
        topRow = 0
        updateSize()
        invalidate()
    }

    fun setTextSizeSp(sp: Float) {
        if (sp == textSizeSp) return
        textSizeSp = sp
        renderer = buildRenderer(sp)
        topRow = 0
        updateSize()
        invalidate()
    }

    private fun buildRenderer(sp: Float): TerminalRenderer {
        val px = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_SP,
            sp,
            resources.displayMetrics
        ).toInt().coerceAtLeast(8)
        return TerminalRenderer(px, Typeface.MONOSPACE)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        updateSize()
    }

    private fun updateSize() {
        val session = this.session ?: return
        val viewW = width
        val viewH = height
        if (viewW == 0 || viewH == 0) return
        val fontW = renderer.fontWidth.coerceAtLeast(1f)
        val fontH = renderer.fontLineSpacing.coerceAtLeast(1)
        val cols = max(4, (viewW / fontW).toInt())
        val rows = max(4, viewH / fontH)
        session.ensureEmulator(cols, rows)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val emu = session?.emulator ?: run {
            canvas.drawColor(Color.BLACK)
            return
        }
        renderer.render(emu, canvas, topRow, -1, -1, -1, -1)
    }

    // ---------- Input handling ----------

    override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection {
        outAttrs.inputType = InputType.TYPE_NULL
        outAttrs.imeOptions = EditorInfo.IME_FLAG_NO_FULLSCREEN or
            EditorInfo.IME_FLAG_NO_EXTRACT_UI or
            EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING
        return object : BaseInputConnection(this, true) {
            override fun commitText(text: CharSequence?, newCursorPosition: Int): Boolean {
                if (text == null) return true
                writeString(text.toString())
                return true
            }

            override fun sendKeyEvent(event: KeyEvent?): Boolean {
                if (event == null) return false
                if (event.action == KeyEvent.ACTION_DOWN) {
                    return this@RemoteTerminalView.onKeyDown(event.keyCode, event)
                }
                return super.sendKeyEvent(event)
            }

            override fun deleteSurroundingText(beforeLength: Int, afterLength: Int): Boolean {
                if (beforeLength > 0) {
                    val del = ByteArray(beforeLength) { 0x7F.toByte() }
                    session?.sendToRemote(del, 0, del.size)
                }
                return true
            }

            override fun finishComposingText(): Boolean = true
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        val session = this.session ?: return super.onKeyDown(keyCode, event)
        val emu = session.emulator ?: return super.onKeyDown(keyCode, event)

        if (keyCode == KeyEvent.KEYCODE_BACK) {
            return super.onKeyDown(keyCode, event)
        }

        val mod = modifierMaskFrom(event)

        val escapeCode = KeyHandler.getCode(
            keyCode,
            mod,
            emu.isCursorKeysApplicationMode,
            emu.isKeypadApplicationMode
        )
        if (escapeCode != null) {
            session.sendToRemote(escapeCode)
            return true
        }

        val unicodeChar = event.getUnicodeChar(event.metaState)
        if (unicodeChar != 0) {
            val ctrl = (mod and KeyHandler.KEYMOD_CTRL) != 0
            val alt = (mod and KeyHandler.KEYMOD_ALT) != 0
            val effective = if (ctrl) applyCtrl(unicodeChar) else unicodeChar
            if (alt) session.sendToRemote("\u001B")
            writeCodePoint(effective)
            return true
        }

        return super.onKeyDown(keyCode, event)
    }

    override fun onCheckIsTextEditor(): Boolean = true

    override fun onTouchEvent(event: MotionEvent): Boolean {
        gestureDetector.onTouchEvent(event)
        return true
    }

    // ---------- Public helpers used by the UI (special keys bar) ----------

    fun sendKey(keyCode: Int, keyMod: Int = 0) {
        val emu = session?.emulator ?: return
        val code = KeyHandler.getCode(
            keyCode,
            keyMod,
            emu.isCursorKeysApplicationMode,
            emu.isKeypadApplicationMode
        )
        if (code != null) {
            session?.sendToRemote(code)
        }
    }

    fun sendControlChar(ch: Char) {
        val upper = ch.uppercaseChar()
        val code = (upper.code - 'A'.code + 1).coerceIn(0, 31)
        session?.sendToRemote(byteArrayOf(code.toByte()))
    }

    fun writeString(text: String) {
        session?.sendToRemote(text)
    }

    private fun writeCodePoint(codePoint: Int) {
        if (codePoint < 0x80) {
            session?.sendToRemote(byteArrayOf(codePoint.toByte()))
        } else {
            val s = String(Character.toChars(codePoint))
            session?.sendToRemote(s)
        }
    }

    private fun applyCtrl(codePoint: Int): Int {
        val ch = codePoint.toChar()
        return when (ch.uppercaseChar()) {
            in 'A'..'Z' -> ch.uppercaseChar().code - 'A'.code + 1
            ' ' -> 0
            else -> codePoint
        }
    }

    private fun modifierMaskFrom(event: KeyEvent): Int {
        var mod = 0
        if (event.isShiftPressed) mod = mod or KeyHandler.KEYMOD_SHIFT
        if (event.isAltPressed) mod = mod or KeyHandler.KEYMOD_ALT
        if (event.isCtrlPressed) mod = mod or KeyHandler.KEYMOD_CTRL
        return mod
    }

    companion object {
        private const val DEFAULT_TEXT_SIZE_SP = 14f
    }
}

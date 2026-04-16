package it.tornado.multiprotocolclient.screens

import android.content.Context
import android.view.KeyEvent
import android.view.inputmethod.InputMethodManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import it.tornado.multiprotocolclient.protocol.terminal.RemoteTerminalSession
import it.tornado.multiprotocolclient.protocol.terminal.RemoteTerminalView
import it.tornado.multiprotocolclient.viewmodel.ClientViewModel

@Composable
fun ConsoleTerminalScreen(
    modifier: Modifier = Modifier,
    viewModel: ClientViewModel,
    onBack: () -> Unit
) {
    val session: RemoteTerminalSession? by viewModel.terminalSession.collectAsState()
    val activeProtocol by viewModel.activeProtocolFlow.collectAsState()
    val context = LocalContext.current

    var terminalRef by remember { mutableStateOf<RemoteTerminalView?>(null) }

    DisposableEffect(session, terminalRef) {
        terminalRef?.attachSession(session)
        onDispose { terminalRef?.attachSession(null) }
    }

    LaunchedEffect(session) {
        if (session == null) {
            onBack()
        }
    }

    BackHandler {
        viewModel.disconnectInteractiveSession(activeProtocol)
        onBack()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    RemoteTerminalView(ctx).also { view ->
                        terminalRef = view
                        view.attachSession(session)
                        view.requestFocus()
                    }
                },
                update = { view ->
                    if (terminalRef !== view) {
                        terminalRef = view
                    }
                    view.attachSession(session)
                }
            )
        }

        SpecialKeysBar(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding(),
            onKey = { keyCode, keyMod -> terminalRef?.sendKey(keyCode, keyMod) },
            onControlChar = { ch -> terminalRef?.sendControlChar(ch) },
            onShowKeyboard = {
                val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                terminalRef?.let { view ->
                    view.requestFocus()
                    imm?.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT)
                }
            }
        )
    }
}

@Composable
private fun SpecialKeysBar(
    modifier: Modifier = Modifier,
    onKey: (keyCode: Int, keyMod: Int) -> Unit,
    onControlChar: (Char) -> Unit,
    onShowKeyboard: () -> Unit
) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            KeyChip(label = "Esc", onClick = { onKey(KeyEvent.KEYCODE_ESCAPE, 0) })
            KeyChip(label = "Tab", onClick = { onKey(KeyEvent.KEYCODE_TAB, 0) })
            KeyChip(label = "^C", onClick = { onControlChar('C') })
            KeyChip(label = "^D", onClick = { onControlChar('D') })
            KeyChip(label = "^Z", onClick = { onControlChar('Z') })
            KeyChip(label = "^L", onClick = { onControlChar('L') })
            Spacer(modifier = Modifier.widthIn(min = 8.dp))
            ArrowChip(Icons.Filled.KeyboardArrowUp, "Up") {
                onKey(KeyEvent.KEYCODE_DPAD_UP, 0)
            }
            ArrowChip(Icons.Filled.KeyboardArrowDown, "Down") {
                onKey(KeyEvent.KEYCODE_DPAD_DOWN, 0)
            }
            ArrowChip(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Left") {
                onKey(KeyEvent.KEYCODE_DPAD_LEFT, 0)
            }
            ArrowChip(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Right") {
                onKey(KeyEvent.KEYCODE_DPAD_RIGHT, 0)
            }
            Spacer(modifier = Modifier.widthIn(min = 8.dp))
            KeyChip(label = "↵", onClick = { onKey(KeyEvent.KEYCODE_ENTER, 0) })
            FilledTonalIconButton(
                onClick = onShowKeyboard,
                modifier = Modifier
                    .height(40.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Keyboard,
                    contentDescription = "Show keyboard"
                )
            }
        }
    }
}

@Composable
private fun KeyChip(
    label: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.heightIn(min = 40.dp),
        shape = MaterialTheme.shapes.medium,
        tonalElevation = 4.dp,
        color = MaterialTheme.colorScheme.surfaceContainerHighest
    ) {
        Box(
            modifier = Modifier
                .heightIn(min = 40.dp)
                .widthIn(min = 44.dp)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun ArrowChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.heightIn(min = 40.dp),
        shape = MaterialTheme.shapes.medium,
        tonalElevation = 4.dp,
        color = MaterialTheme.colorScheme.surfaceContainerHighest
    ) {
        Box(
            modifier = Modifier
                .heightIn(min = 40.dp)
                .widthIn(min = 44.dp)
                .padding(horizontal = 10.dp, vertical = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = description,
                tint = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

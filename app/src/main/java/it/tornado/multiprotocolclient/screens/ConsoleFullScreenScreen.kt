package it.tornado.multiprotocolclient.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import it.tornado.multiprotocolclient.settings.ConsoleFontSize
import it.tornado.multiprotocolclient.settings.UiSettings
import it.tornado.multiprotocolclient.settings.renderConsoleLines
import it.tornado.multiprotocolclient.viewmodel.ClientViewModel

@Composable
fun ConsoleFullScreenScreen(
    modifier: Modifier = Modifier,
    uiSettings: UiSettings = UiSettings(),
    viewModel: ClientViewModel,
    onBackToRequest: () -> Unit
) {
    val response by viewModel.response.collectAsState()
    val renderedResponse = remember(response, uiSettings.consoleShowTimestamps, uiSettings.maskSensitiveOutput, uiSettings.consoleBufferLimit) {
        renderConsoleLines(response, uiSettings)
    }
    val consoleTextStyle = when (uiSettings.consoleFontSize) {
        ConsoleFontSize.SMALL -> MaterialTheme.typography.bodySmall
        ConsoleFontSize.MEDIUM -> MaterialTheme.typography.bodyMedium
        ConsoleFontSize.LARGE -> MaterialTheme.typography.bodyLarge
    }
    val listState = rememberLazyListState()
    val clipboardManager = LocalClipboardManager.current
    var showContent by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        showContent = true
    }
    LaunchedEffect(renderedResponse.size, uiSettings.consoleAutoScroll) {
        if (uiSettings.consoleAutoScroll && renderedResponse.isNotEmpty()) {
            listState.animateScrollToItem(renderedResponse.lastIndex)
        }
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val isTabletScreen = maxWidth >= 840.dp
        val horizontalPadding = if (isTabletScreen) 24.dp else 16.dp
        val maxContentWidth = if (isTabletScreen) 980.dp else maxWidth

        AnimatedVisibility(
            visible = showContent,
            enter = fadeIn(animationSpec = tween(280)) + slideInVertically(
                initialOffsetY = { it / 9 },
                animationSpec = tween(320)
            )
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.TopCenter
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = maxContentWidth)
                        .padding(start = horizontalPadding, top = 6.dp, end = horizontalPadding)
                        .fillMaxSize()
                ) {
                    CenterAlignedTopAppBar(
                        title = { Text("Console", style = MaterialTheme.typography.titleLarge) },
                        navigationIcon = {
                            IconButton(onClick = onBackToRequest) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = Color.Transparent
                        ),
                        windowInsets = WindowInsets(0, 0, 0, 0)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilledTonalButton(
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 52.dp),
                            shape = MaterialTheme.shapes.extraLarge,
                            onClick = { clipboardManager.setText(AnnotatedString(renderedResponse.joinToString("\n"))) },
                        ) {
                            Text("Copy")
                        }
                        OutlinedButton(
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 52.dp),
                            shape = MaterialTheme.shapes.extraLarge,
                            onClick = { viewModel.resetResponse() },
                            enabled = renderedResponse.isNotEmpty()
                        ) {
                            Text("Clear")
                        }
                    }

                    Surface(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(top = 12.dp, bottom = 8.dp)
                            .navigationBarsPadding(),
                        shape = MaterialTheme.shapes.large,
                        tonalElevation = 2.dp,
                        color = MaterialTheme.colorScheme.surfaceContainerLowest
                    ) {
                        SelectionContainer(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(10.dp)
                        ) {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                state = listState
                            ) {
                                items(renderedResponse) { line ->
                                    when {
                                        line.startsWith("=== ") -> {
                                            Text(
                                                text = line.removePrefix("=== ").removeSuffix(" ==="),
                                                style = MaterialTheme.typography.headlineSmall,
                                                color = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(top = 2.dp, bottom = 6.dp)
                                            )
                                        }
                                        line.startsWith("SECTION: ") -> {
                                            val sectionLabel = line.removePrefix("SECTION: ").trim()
                                            Surface(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(top = 8.dp, bottom = 4.dp),
                                                shape = MaterialTheme.shapes.medium,
                                                color = MaterialTheme.colorScheme.surfaceContainerHigh
                                            ) {
                                                Text(
                                                    text = sectionLabel,
                                                    style = MaterialTheme.typography.labelLarge,
                                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                                )
                                            }
                                        }
                                        else -> {
                                            Text(
                                                text = line,
                                                style = consoleTextStyle,
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(vertical = 1.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

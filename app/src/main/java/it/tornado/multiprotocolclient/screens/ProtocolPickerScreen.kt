package it.tornado.multiprotocolclient.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProtocolPickerScreen(
    modifier: Modifier = Modifier,
    onOpenRequestBuilder: (String) -> Unit
) {
    val defaultProtocol = "HTTP"
    var selectedProtocol by rememberSaveable { mutableStateOf(defaultProtocol) }
    var hasExplicitSelection by rememberSaveable { mutableStateOf(true) }
    var expandedSection by rememberSaveable { mutableStateOf(protocolSections.first().title) }
    var showContent by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        showContent = true
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val isTabletScreen = maxWidth >= 840.dp
        val horizontalPadding = if (isTabletScreen) 24.dp else 16.dp
        val maxContentWidth = if (isTabletScreen) 860.dp else maxWidth

        AnimatedVisibility(
            visible = showContent,
            enter = fadeIn(animationSpec = tween(280)) + slideInVertically(
                initialOffsetY = { it / 7 },
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
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Choose a protocol",
                            style = MaterialTheme.typography.headlineSmall,
                            modifier = Modifier.weight(1f)
                        )
                        FilledIconButton(
                            onClick = { onOpenRequestBuilder(selectedProtocol) },
                            enabled = hasExplicitSelection
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = "Continue to request builder"
                            )
                        }
                    }
                    Text(
                        text = "Pick a category first, then select a protocol to build your request.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp, bottom = 8.dp)
                    )
                    Text(
                        text = "Selected: $selectedProtocol",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(top = 15.dp, bottom = 80.dp)
                    ) {
                        items(protocolSections) { section ->
                            val singleProtocol = section.protocols.size == 1
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                                ),
                                onClick = {
                                    if (singleProtocol) {
                                        selectedProtocol = section.protocols.first()
                                        hasExplicitSelection = true
                                    } else {
                                        expandedSection = if (expandedSection == section.title) "" else section.title
                                    }
                                }
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 4.dp, vertical = 2.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = section.title,
                                            style = MaterialTheme.typography.titleMedium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f)
                                        )
                                        if (!singleProtocol) {
                                            Icon(
                                                imageVector = if (expandedSection == section.title) {
                                                    Icons.Filled.ExpandLess
                                                } else {
                                                    Icons.Filled.ExpandMore
                                                },
                                                contentDescription = null,
                                                modifier = Modifier.padding(start = 8.dp)
                                            )
                                        }
                                    }

                                    if (singleProtocol) {
                                        val onlyProtocol = section.protocols.first()
                                        if (selectedProtocol == onlyProtocol) {
                                            Button(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .heightIn(min = 48.dp)
                                                    .padding(top = 8.dp),
                                                onClick = {
                                                    selectedProtocol = onlyProtocol
                                                    hasExplicitSelection = true
                                                },
                                                colors = ButtonDefaults.buttonColors(
                                                    containerColor = MaterialTheme.colorScheme.primary,
                                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                                )
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Filled.Check,
                                                    contentDescription = null,
                                                    modifier = Modifier.padding(end = 8.dp)
                                                )
                                                Text(onlyProtocol)
                                            }
                                        } else {
                                            OutlinedButton(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .heightIn(min = 48.dp)
                                                    .padding(top = 8.dp),
                                                onClick = {
                                                    selectedProtocol = onlyProtocol
                                                    hasExplicitSelection = true
                                                },
                                                shape = MaterialTheme.shapes.large
                                            ) {
                                                Text(onlyProtocol)
                                            }
                                        }
                                    }

                                    AnimatedVisibility(visible = !singleProtocol && expandedSection == section.title) {
                                        FlowRow(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(top = 10.dp),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            section.protocols.forEach { protocol ->
                                                FilterChip(
                                                    selected = selectedProtocol == protocol,
                                                    onClick = {
                                                        selectedProtocol = protocol
                                                        hasExplicitSelection = true
                                                    },
                                                    label = { Text(protocol) },
                                                    leadingIcon = if (selectedProtocol == protocol) {
                                                        {
                                                            Icon(
                                                                imageVector = Icons.Filled.Check,
                                                                contentDescription = null
                                                            )
                                                        }
                                                    } else {
                                                        null
                                                    }
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
}

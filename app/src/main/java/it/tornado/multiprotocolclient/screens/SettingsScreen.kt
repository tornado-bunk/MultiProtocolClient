package it.tornado.multiprotocolclient.screens

import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import it.tornado.multiprotocolclient.R
import it.tornado.multiprotocolclient.settings.ConsoleFontSize
import it.tornado.multiprotocolclient.settings.ThemeMode
import it.tornado.multiprotocolclient.settings.UiSettingsViewModel
import it.tornado.multiprotocolclient.settings.UiTextSize

@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    settingsViewModel: UiSettingsViewModel
) {
    val settings by settingsViewModel.settings.collectAsState()
    val context = LocalContext.current
    val appVersion = remember {
        runCatching {
            val info = context.packageManager.getPackageInfo(context.packageName, 0)
            "${info.versionName}"
        }.getOrElse { "Unknown" }
    }
    var bufferLimitText by remember(settings.consoleBufferLimit) { mutableStateOf(settings.consoleBufferLimit.toString()) }

    fun openLink(url: String) {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val isTabletScreen = maxWidth >= 840.dp
        val horizontalPadding = if (isTabletScreen) 24.dp else 16.dp
        val maxContentWidth = if (isTabletScreen) 860.dp else maxWidth

        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.TopCenter
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = maxContentWidth)
                    .padding(horizontal = horizontalPadding),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(top = 8.dp, bottom = 96.dp)
            ) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Settings,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Settings",
                            style = MaterialTheme.typography.headlineSmall,
                            modifier = Modifier.padding(start = 10.dp)
                        )
                    }
                }

                item {
                    SettingsSectionCard(title = "Look & Feel") {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Theme",
                                style = MaterialTheme.typography.bodyLarge
                            )
                            EnumFilterRow(
                                values = ThemeMode.entries,
                                current = settings.themeMode,
                                label = { it.name.lowercase().replaceFirstChar(Char::uppercase) },
                                onSelect = settingsViewModel::updateThemeMode
                            )
                        }
                        SettingSwitchRow(
                            title = "Dynamic Color",
                            subtitle = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                                "Not available on this Android version."
                            } else null,
                            checked = settings.dynamicColor,
                            enabled = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S,
                            onCheckedChange = settingsViewModel::updateDynamicColor
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "UI Text Size",
                                style = MaterialTheme.typography.bodyLarge
                            )
                            EnumFilterRow(
                                values = UiTextSize.entries,
                                current = settings.uiTextSize,
                                label = {
                                    when (it) {
                                        UiTextSize.SMALL -> "S"
                                        UiTextSize.MEDIUM -> "M"
                                        UiTextSize.LARGE -> "L"
                                    }
                                },
                                onSelect = settingsViewModel::updateUiTextSize
                            )
                        }
                    }
                }

                item {
                    SettingsSectionCard(title = "Console") {
                        SettingSwitchRow(
                            title = "Auto-scroll output",
                            checked = settings.consoleAutoScroll,
                            onCheckedChange = settingsViewModel::updateConsoleAutoScroll
                        )
                        SettingSwitchRow(
                            title = "Timestamp lines",
                            checked = settings.consoleShowTimestamps,
                            onCheckedChange = settingsViewModel::updateConsoleShowTimestamps
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Text Size",
                                style = MaterialTheme.typography.bodyLarge
                            )
                            EnumFilterRow(
                                values = ConsoleFontSize.entries,
                                current = settings.consoleFontSize,
                                label = {
                                    when (it) {
                                        ConsoleFontSize.SMALL -> "S"
                                        ConsoleFontSize.MEDIUM -> "M"
                                        ConsoleFontSize.LARGE -> "L"
                                    }
                                },
                                onSelect = settingsViewModel::updateConsoleFontSize
                            )
                        }
                        OutlinedTextField(
                            value = bufferLimitText,
                            onValueChange = {
                                bufferLimitText = it.filter(Char::isDigit)
                                bufferLimitText.toIntOrNull()?.let(settingsViewModel::updateConsoleBufferLimit)
                            },
                            label = { Text("Buffer line limit") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                    }
                }

                item {
                    SettingsSectionCard(title = "About") {
                        val githubLogoRes = if (MaterialTheme.colorScheme.surface.luminance() < 0.5f) {
                            R.drawable.github_light
                        } else {
                            R.drawable.github_dark
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.ic_mpc),
                                contentDescription = null,
                                modifier = Modifier.size(20.dp).clip(CircleShape)
                            )
                            Text("MultiProtocolClient $appVersion")
                        }
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                        AboutLinkButton(
                            label = "GitHub",
                            leadingIcon = {
                                Icon(
                                    painter = painterResource(id = githubLogoRes),
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                            },
                            onClick = { openLink("https://github.com/tornado-bunk/MultiProtocolClient") }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsSectionCard(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            content = {
                Text(text = title, style = MaterialTheme.typography.titleMedium)
                content()
            }
        )
    }
}

@Composable
private fun SettingSwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    subtitle: String? = null,
    enabled: Boolean = true
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}

@Composable
private fun <T> EnumFilterRow(
    values: List<T>,
    current: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        values.forEach { value ->
            FilterChip(
                selected = value == current,
                onClick = { onSelect(value) },
                label = { Text(label(value)) }
            )
        }
    }
}

@Composable
private fun AboutLinkButton(
    label: String,
    leadingIcon: @Composable () -> Unit,
    onClick: () -> Unit
) {
    OutlinedButton(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick
    ) {
        leadingIcon()
        Spacer(modifier = Modifier.width(8.dp))
        Text(label)
    }
}

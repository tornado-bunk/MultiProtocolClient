package it.tornado.multiprotocolclient.ui.screens

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import it.tornado.multiprotocolclient.viewmodel.ClientViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalFocusManager
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClientScreen(modifier: Modifier = Modifier) {
    val viewModel: ClientViewModel = viewModel()
    val response by viewModel.response.collectAsState()
    val protocols = listOf("HTTP", "FTP", "NTP", "Custom")
    var selectedProtocol by remember { mutableStateOf(protocols[0]) }
    var ipAddress by remember { mutableStateOf("") }
    var port by remember { mutableStateOf("") }
    var expanded by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    var use_ssl by remember { mutableStateOf(true) }
    var see_only_status_code by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    Column(
        modifier = modifier
            .padding(16.dp)
            .padding(top = 48.dp)
            .fillMaxSize()
    ) {
        SnackbarHost(hostState = snackbarHostState)

        Spacer(modifier = Modifier.height(8.dp))
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = !expanded }
        ) {
            OutlinedTextField(
                value = selectedProtocol,
                onValueChange = { },
                readOnly = true,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor()
            )

            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                protocols.forEach { protocol ->
                    DropdownMenuItem(
                        text = { Text(text = protocol) },
                        onClick = {
                            selectedProtocol = protocol
                            expanded = false
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        OutlinedTextField(
            value = ipAddress,
            onValueChange = { ipAddress = it },
            label = { Text("Host") },
            keyboardOptions = KeyboardOptions.Default.copy(keyboardType = KeyboardType.Text),
            modifier = Modifier.fillMaxWidth()
        )

        if (selectedProtocol == "Custom") {
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedTextField(
                value = port,
                onValueChange = { port = it },
                label = { Text("Port") },
                keyboardOptions = KeyboardOptions.Default.copy(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )
        }
        if (selectedProtocol == "HTTP") {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 8.dp)
            ) {
                Checkbox(
                    checked = use_ssl,
                    onCheckedChange = {
                        use_ssl = it
                        if (!it) {
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar(
                                    message = "HTTP connections are only allowed on the local network",
                                    duration = SnackbarDuration.Short
                                )
                            }
                        }
                    }
                )
                Text(text = "Use SSL")
                Spacer(modifier = Modifier.width(8.dp))
                Checkbox(
                    checked = see_only_status_code,
                    onCheckedChange = { see_only_status_code = it }
                )
                Text(text = "Only status code")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            FilledTonalButton(
                onClick = {
                    if (selectedProtocol == "Custom" && port.isEmpty()) {
                        coroutineScope.launch {
                            Toast.makeText(context, "Port is required for Custom protocol", Toast.LENGTH_SHORT).show()
                        }
                        return@FilledTonalButton
                    }
                    viewModel.resetResponse()
                    focusManager.clearFocus()
                    viewModel.sendHttpRequest(selectedProtocol, ipAddress, port, use_ssl, see_only_status_code)
                    coroutineScope.launch {
                        Toast.makeText(context, "Request Sent", Toast.LENGTH_SHORT).show()
                    }
                }
            ) {
                Text(text = "Send")
            }
            Spacer(modifier = Modifier.width(8.dp))
            OutlinedButton(
                onClick = {
                    focusManager.clearFocus()
                    selectedProtocol = protocols[0]
                    ipAddress = ""
                    port = ""
                    use_ssl = false
                    see_only_status_code = false
                    viewModel.resetResponse()
                }
            ) {
                Text(text = "Reset")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp)
            ) {
                items(response) { line ->
                    Text(text = line, modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }
}
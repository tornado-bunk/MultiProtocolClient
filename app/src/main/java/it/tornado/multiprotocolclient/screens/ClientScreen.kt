package it.tornado.multiprotocolclient.screens

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
import java.time.ZoneId

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClientScreen(modifier: Modifier = Modifier) {
    val viewModel: ClientViewModel = viewModel()
    val response by viewModel.response.collectAsState()
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current

    var useSSL by remember { mutableStateOf(true) }
    var seeOnlyStatusCode by remember { mutableStateOf(false) }
    var trustSelfSigned by remember { mutableStateOf(false) }

    val protocols = listOf("HTTP", "NTP", "Custom")
    var selectedProtocol by remember { mutableStateOf(protocols[0]) }
    var ipAddress by remember { mutableStateOf("") }
    var port by remember { mutableStateOf("") }
    var expanded by remember { mutableStateOf(false) }

    val coroutineScope = rememberCoroutineScope()
    var expandedTimezone by remember { mutableStateOf(false) }
    var selectedTimezone by remember { mutableStateOf("") }
    val timezones = remember { ZoneId.getAvailableZoneIds().sorted() }
    var useTcp by remember { mutableStateOf(true) }
    var useSystemTimezone by remember { mutableStateOf(true) }

    //At launch, set the default port based on the selected protocol
    //Set the default timezone to the system timezone
// Modify the LaunchedEffect
    LaunchedEffect(selectedProtocol) {
        // Reset common fields
        ipAddress = ""
        port = ""

        // Set protocol-specific defaults
        when (selectedProtocol) {
            "HTTP" -> {
                port = if (useSSL) "443" else "80"
                seeOnlyStatusCode = false
                trustSelfSigned = false
            }
            "NTP" -> {
                selectedTimezone = ZoneId.systemDefault().id
                useSystemTimezone = true
            }
            "Custom" -> {
                useTcp = true
            }
        }

        // Reset response
        viewModel.resetResponse()
    }


    // Main content
    Column(
        modifier = modifier
            .padding(16.dp)
            .padding(top = 48.dp)
            .fillMaxSize()
    ) {
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
                    .menuAnchor(MenuAnchorType.PrimaryEditable)
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

        // Show additional fields based on the selected protocol
        if (selectedProtocol == "HTTP") {
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedTextField(
                value = port,
                onValueChange = { port = it },
                label = { Text("Port") },
                keyboardOptions = KeyboardOptions.Default.copy(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )

            Column(
                modifier = Modifier.padding(top = 8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(
                        checked = useSSL,
                        onCheckedChange = {
                            useSSL = it
                            port = if (it) "443" else "80"
                        }
                    )
                    Text(text = "Use SSL")
                    Spacer(modifier = Modifier.width(8.dp))
                    Checkbox(
                        checked = seeOnlyStatusCode,
                        onCheckedChange = { seeOnlyStatusCode = it }
                    )
                    Text(text = "Only status code")
                }

                // Show the option to trust self-signed certificates only if SSL is enabled
                if (useSSL) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 4.dp)
                    ) {
                        Checkbox(
                            checked = trustSelfSigned,
                            onCheckedChange = { trustSelfSigned = it }
                        )
                        Text(text = "Trust self-signed certificates")
                    }
                }
            }
        }

        // Show additional fields based on the selected protocol
        if (selectedProtocol == "NTP") {

            Spacer(modifier = Modifier.height(16.dp))
            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = 8.dp)
                ) {
                    Checkbox(
                        checked = useSystemTimezone,
                        onCheckedChange = { checked ->
                            useSystemTimezone = checked
                            selectedTimezone = if (checked) {
                                ZoneId.systemDefault().id
                            } else {
                                "GMT"
                            }
                        }
                    )
                    Text(text = "Use System Timezone")
                }

                ExposedDropdownMenuBox(
                    expanded = expandedTimezone,
                    onExpandedChange = { expandedTimezone = !expandedTimezone }
                ) {
                    OutlinedTextField(
                        value = selectedTimezone,
                        onValueChange = { },
                        readOnly = true,
                        label = { Text("Timezone") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedTimezone) },
                        enabled = !useSystemTimezone,  //Disable the field if the system timezone is used
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(MenuAnchorType.PrimaryEditable)
                    )

                    ExposedDropdownMenu(
                        expanded = expandedTimezone && !useSystemTimezone,
                        onDismissRequest = { expandedTimezone = false }
                    ) {
                        timezones.forEach { timezone ->
                            DropdownMenuItem(
                                text = { Text(timezone) },
                                onClick = {
                                    selectedTimezone = timezone
                                    expandedTimezone = false
                                }
                            )
                        }
                    }
                }
            }
        }

        // Show additional fields based on the selected protocol
        if (selectedProtocol == "Custom") {

            Spacer(modifier = Modifier.height(16.dp))
            OutlinedTextField(
                value = port,
                onValueChange = { port = it },
                label = { Text("Port") },
                keyboardOptions = KeyboardOptions.Default.copy(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 8.dp)
            ) {
                Checkbox(
                    checked = useTcp,
                    onCheckedChange = { useTcp = it }
                )
                Text(text = if (useTcp) "TCP" else "UDP")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            FilledTonalButton(
                onClick = {
                    // When the user clicks on the button, validate the input fields
                    when (selectedProtocol) {
                        // Case "HTTP" and "Custom"
                        "Custom", "HTTP" -> {
                            if (port.isEmpty()) {
                                coroutineScope.launch {
                                    Toast.makeText(context, "Port is required", Toast.LENGTH_SHORT).show()
                                }
                                return@FilledTonalButton
                            }
                            try {
                                val portNumber = port.toInt()
                                if (portNumber !in 1..65535) {
                                    coroutineScope.launch {
                                        Toast.makeText(context, "Port must be between 1 and 65535", Toast.LENGTH_SHORT).show()
                                    }
                                    return@FilledTonalButton
                                }
                            } catch (e: NumberFormatException) {
                                coroutineScope.launch {
                                    Toast.makeText(context, "Invalid port number", Toast.LENGTH_SHORT).show()
                                }
                                return@FilledTonalButton
                            }
                        }
                    }

                    viewModel.resetResponse()
                    focusManager.clearFocus()
                    when (selectedProtocol) {
                        "HTTP" -> viewModel.sendHttpRequest(selectedProtocol, ipAddress, port, useSSL, seeOnlyStatusCode, trustSelfSigned)
                        "NTP" -> viewModel.sendNtpRequest(ipAddress, selectedTimezone)
                        "Custom" -> viewModel.sendCustomRequest(ipAddress, port, useTcp)
                    }
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
                    port = "443"
                    useSSL = true
                    seeOnlyStatusCode = false
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
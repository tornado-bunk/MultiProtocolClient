package it.tornado.multiprotocolclient.screens
import it.tornado.multiprotocolclient.viewmodel.ClientViewModel

import android.widget.Toast
import androidx.compose.foundation.clickable
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

    val protocols = listOf("HTTP", "DNS", "NTP", "Custom")
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

    var dnsQueryType by remember { mutableStateOf("A") }
    val dnsTypes = listOf("A", "MX", "CNAME", "NS", "PTR", "ANY")
    var useDnsOverHttps by remember { mutableStateOf(false) }
    var useDnsOverTls by remember { mutableStateOf(false) }
    var expandedDnsType by remember { mutableStateOf(false) }
    var expandedResolver by remember { mutableStateOf(false) }
    var useRecursion by remember { mutableStateOf(true) }
    var useTcp4Dns by remember { mutableStateOf(false) }
    var selectedResolver by remember { mutableStateOf("Google DNS (8.8.8.8)") }
    val resolvers = listOf(
        "Google DNS (8.8.8.8)",
        "Cloudflare (1.1.1.1)",
        "OpenDNS (208.67.222.222)",
        "Quad9 (9.9.9.9)",
        "AdGuard DNS (94.140.14.14)"
    )

    //At launch, reset the fields based on the selected protocol
    LaunchedEffect(selectedProtocol) {
        // Reset common fields
        ipAddress = ""
        port = ""

        when (selectedProtocol) {
            "HTTP" -> {
                port = if (useSSL) "443" else "80"
                seeOnlyStatusCode = false
                trustSelfSigned = false
            }

            "DNS" -> {
                dnsQueryType = "A"
                useDnsOverHttps = false
                useDnsOverTls = false
                useRecursion = true
                useTcp4Dns = false
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

        // Show additional fields based on the selected protocol
        if (selectedProtocol == "HTTP") {
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = ipAddress,
                    onValueChange = { ipAddress = it },
                    label = { Text("Host") },
                    keyboardOptions = KeyboardOptions.Default.copy(keyboardType = KeyboardType.Text),
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = port,
                    onValueChange = { port = it },
                    label = { Text("Port") },
                    keyboardOptions = KeyboardOptions.Default.copy(keyboardType = KeyboardType.Number),
                    modifier = Modifier.width(120.dp)
                )
            }

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
        if (selectedProtocol == "DNS") {
            Spacer(modifier = Modifier.height(16.dp))

            // Domain and query type
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Domain/IP
                OutlinedTextField(
                    value = ipAddress,
                    onValueChange = { ipAddress = it },
                    label = { Text("Domain/IP") },
                    keyboardOptions = KeyboardOptions.Default.copy(keyboardType = KeyboardType.Text),
                    modifier = Modifier.weight(1f)
                )

                // Query type
                ExposedDropdownMenuBox(
                    expanded = expandedDnsType,
                    onExpandedChange = { expandedDnsType = !expandedDnsType },
                    modifier = Modifier.width(150.dp)
                ) {
                    OutlinedTextField(
                        value = dnsQueryType,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Query Type") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedDnsType) },
                        modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryEditable)
                    )

                    ExposedDropdownMenu(
                        expanded = expandedDnsType,
                        onDismissRequest = { expandedDnsType = false }
                    ) {
                        dnsTypes.forEach { type ->
                            DropdownMenuItem(
                                text = { Text(type) },
                                onClick = {
                                    dnsQueryType = type
                                    expandedDnsType = false
                                }
                            )
                        }
                    }
                }
            }

            // DNS resolver
            Spacer(modifier = Modifier.height(8.dp))
            ExposedDropdownMenuBox(
                expanded = expandedResolver,
                onExpandedChange = { expandedResolver = !expandedResolver }
            ) {
                OutlinedTextField(
                    value = selectedResolver,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("DNS Resolver") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedResolver) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(MenuAnchorType.PrimaryEditable)
                )

                // Show the list of DNS resolvers
                ExposedDropdownMenu(
                    expanded = expandedResolver,
                    onDismissRequest = { expandedResolver = false }
                ) {
                    (when {
                        useDnsOverHttps -> listOf("Cloudflare DoH (1.1.1.1)")
                        useDnsOverTls -> listOf(
                            "Cloudflare (1.1.1.1)",
                            "Google DNS (8.8.8.8)",
                            "Quad9 (9.9.9.9)"
                        )

                        else -> resolvers
                    }).forEach { resolver ->
                        DropdownMenuItem(
                            text = { Text(resolver) },
                            onClick = {
                                selectedResolver = resolver
                                expandedResolver = false
                            }
                        )
                    }
                }
            }

            // Checkbox for DNS over HTTPS/TLS and checkbox for tcp and recursion
            Column(
                modifier = Modifier.padding(top = 8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Start
                ) {
                    // Doh and DoT
                    Column(
                        modifier = Modifier.weight(1f)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(
                                checked = useDnsOverHttps,
                                onCheckedChange = { checked ->
                                    useDnsOverHttps = checked
                                    if (checked) {
                                        useDnsOverTls = false
                                        useTcp4Dns = true
                                        selectedResolver = "Cloudflare DoH (1.1.1.1)"
                                        coroutineScope.launch {
                                            Toast.makeText(
                                                context,
                                                "Currently only Cloudflare DNS over HTTPS is supported",
                                                Toast.LENGTH_LONG
                                            ).show()
                                        }
                                    } else {
                                        selectedResolver = "Google DNS (8.8.8.8)"
                                        useTcp4Dns = false
                                    }
                                }
                            )
                            Text(text = "Use DoH")
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(top = 4.dp)
                        ) {
                            Checkbox(
                                checked = useDnsOverTls,
                                onCheckedChange = { checked ->
                                    useDnsOverTls = checked
                                    if (checked) {
                                        useDnsOverHttps = false
                                        useTcp4Dns = true
                                        selectedResolver = "Cloudflare (1.1.1.1)"
                                    } else {
                                        selectedResolver = "Google DNS (8.8.8.8)"
                                        useTcp4Dns = false
                                    }
                                }
                            )
                            Text(text = "Use DoT")
                        }
                    }

                    // Recursion and TCP
                    Column(
                        modifier = Modifier.weight(1f)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(
                                checked = useRecursion,
                                onCheckedChange = { useRecursion = it }
                            )
                            Text(text = "Enable Recursion")
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(top = 4.dp)
                        ) {
                            Checkbox(
                                checked = useTcp4Dns,
                                onCheckedChange = { checked ->
                                    if (!useDnsOverHttps && !useDnsOverTls) {
                                        useTcp4Dns = checked
                                    }
                                },
                                enabled = !useDnsOverHttps && !useDnsOverTls
                            )
                            Text(text = "Use TCP")
                        }
                    }
                }
            }
        }

        // Show additional fields based on the selected protocol
        if (selectedProtocol == "NTP") {
            Spacer(modifier = Modifier.height(16.dp))

            var searchText by remember { mutableStateOf("") }
            val filteredTimezones = remember(searchText) {
                timezones.filter {
                    it.contains(searchText, ignoreCase = true)
                }
            }

            // Synchronyze the selected timezone with the search text
            LaunchedEffect(useSystemTimezone) {
                if (useSystemTimezone) {
                    searchText = ZoneId.systemDefault().id
                    selectedTimezone = ZoneId.systemDefault().id
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = ipAddress,
                    onValueChange = { ipAddress = it },
                    label = { Text("Host") },
                    keyboardOptions = KeyboardOptions.Default.copy(keyboardType = KeyboardType.Text),
                    modifier = Modifier.weight(1f)
                )

                ExposedDropdownMenuBox(
                    expanded = expandedTimezone,
                    onExpandedChange = { expandedTimezone = !expandedTimezone },
                    modifier = Modifier.width(200.dp)
                ) {
                    OutlinedTextField(
                        value = searchText,
                        onValueChange = { newText ->
                            searchText = newText
                            expandedTimezone = true
                            if (timezones.contains(newText)) {
                                selectedTimezone = newText
                            }
                        },
                        label = { Text("Timezone") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedTimezone) },
                        enabled = !useSystemTimezone,
                        modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryEditable)
                    )

                    if (filteredTimezones.isNotEmpty()) {
                        ExposedDropdownMenu(
                            expanded = expandedTimezone && !useSystemTimezone,
                            onDismissRequest = { expandedTimezone = false }
                        ) {
                            filteredTimezones.forEach { timezone ->
                                DropdownMenuItem(
                                    text = { Text(timezone) },
                                    onClick = {
                                        selectedTimezone = timezone
                                        searchText = timezone
                                        expandedTimezone = false
                                    }
                                )
                            }
                        }
                    }
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 8.dp)
            ) {
                Checkbox(
                    checked = useSystemTimezone,
                    onCheckedChange = { checked ->
                        useSystemTimezone = checked
                        if (checked) {
                            selectedTimezone = ZoneId.systemDefault().id
                            searchText = ZoneId.systemDefault().id
                        } else {
                            selectedTimezone = searchText
                        }
                    }
                )
                Text(text = "Use System Timezone")
            }
        }

        // Show additional fields based on the selected protocol
        if (selectedProtocol == "Custom") {
            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = ipAddress,
                    onValueChange = { ipAddress = it },
                    label = { Text("Host") },
                    keyboardOptions = KeyboardOptions.Default.copy(keyboardType = KeyboardType.Text),
                    modifier = Modifier.weight(1f)
                )

                OutlinedTextField(
                    value = port,
                    onValueChange = { port = it },
                    label = { Text("Port") },
                    keyboardOptions = KeyboardOptions.Default.copy(keyboardType = KeyboardType.Number),
                    modifier = Modifier.width(120.dp)
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { useTcp = false }
                ) {
                    Checkbox(
                        checked = !useTcp,
                        onCheckedChange = { useTcp = false }
                    )
                    Text(
                        text = "UDP",
                        modifier = Modifier.padding(start = 4.dp)
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { useTcp = true }
                ) {
                    Checkbox(
                        checked = useTcp,
                        onCheckedChange = { useTcp = true }
                    )
                    Text(
                        text = "TCP",
                        modifier = Modifier.padding(start = 4.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            FilledTonalButton(
                onClick = {
                    if (ipAddress.isEmpty()) {
                        coroutineScope.launch {
                            Toast.makeText(context, "Host is required", Toast.LENGTH_SHORT)
                                .show()
                        }
                        return@FilledTonalButton
                    }

                    when (selectedProtocol) {
                        "Custom", "HTTP" -> {
                            if (port.isEmpty()) {
                                coroutineScope.launch {
                                    Toast.makeText(
                                        context,
                                        "Port is required",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                                return@FilledTonalButton
                            }
                            try {
                                val portNumber = port.toInt()
                                if (portNumber !in 1..65535) {
                                    coroutineScope.launch {
                                        Toast.makeText(
                                            context,
                                            "Port must be between 1 and 65535",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                    return@FilledTonalButton
                                }
                            } catch (e: NumberFormatException) {
                                coroutineScope.launch {
                                    Toast.makeText(
                                        context,
                                        "Invalid port number",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                                return@FilledTonalButton
                            }
                        }

                        "DNS" -> {
                            if (ipAddress.isEmpty()) {
                                coroutineScope.launch {
                                    Toast.makeText(
                                        context,
                                        "Domain/IP is required",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                                return@FilledTonalButton
                            }
                            viewModel.sendDnsRequest(
                                ipAddress,
                                dnsQueryType,
                                useDnsOverHttps,
                                useDnsOverTls,
                                selectedResolver,
                                useRecursion,
                                useTcp4Dns
                            )
                        }

                        "NTP" -> {
                            if (selectedTimezone.isEmpty()) {
                                coroutineScope.launch {
                                    Toast.makeText(
                                        context,
                                        "Timezone is required",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                                return@FilledTonalButton
                            }
                        }
                    }

                    viewModel.resetResponse()
                    focusManager.clearFocus()
                    when (selectedProtocol) {
                        "HTTP" -> viewModel.sendHttpRequest(
                            selectedProtocol,
                            ipAddress,
                            port,
                            useSSL,
                            seeOnlyStatusCode,
                            trustSelfSigned
                        )

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
                    // Reset fields based on the selected protocol
                    when (selectedProtocol) {
                        "HTTP" -> {
                            ipAddress = ""
                            port = if (useSSL) "443" else "80"
                            useSSL = true
                            seeOnlyStatusCode = false
                            trustSelfSigned = false
                        }

                        "DNS" -> {
                            ipAddress = ""
                            dnsQueryType = "A"
                            selectedResolver = "Google DNS (8.8.8.8)"
                            useDnsOverHttps = false
                            useDnsOverTls = false
                            useRecursion = true
                            useTcp4Dns = false
                        }

                        "NTP" -> {
                            ipAddress = ""
                            selectedTimezone = ZoneId.systemDefault().id
                            useSystemTimezone = true
                        }

                        "Custom" -> {
                            ipAddress = ""
                            port = ""
                            useTcp = true
                        }
                    }
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


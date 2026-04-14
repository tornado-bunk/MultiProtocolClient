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

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import kotlinx.coroutines.launch
import android.Manifest
import android.os.Build
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts


import java.time.ZoneId

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClientScreen(modifier: Modifier = Modifier) {
    val viewModel: ClientViewModel = viewModel()
    val response by viewModel.response.collectAsState()
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val coroutineScope = rememberCoroutineScope()

    // Permission launcher for Android 13+ (SDK 33)
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            // Permission granted, retry the pending action if any? 
            // For now, user has to click Send again or we could automagically handle it.
            // Simpler to just let them click again as Toast will say "Permission granted" or similar if we wanted.
            coroutineScope.launch {
                Toast.makeText(context, "Permission granted. Please click Send again.", Toast.LENGTH_SHORT).show()
            }
        } else {
             coroutineScope.launch {
                Toast.makeText(context, "Permission denied. Local connections may fail.", Toast.LENGTH_LONG).show()
            }
        }
    }

    fun isLocalIp(ip: String): Boolean {
        return try {
             if (ip.isEmpty()) return false
             
             // Basic check for common local ranges
             if (ip.startsWith("192.168.") || ip.startsWith("10.")) {
                 return true
             }
             
             // 172.16.x.x - 172.31.x.x
             if (ip.startsWith("172.")) {
                 val parts = ip.split(".")
                 if (parts.size > 1) {
                     val secondOctet = parts[1].toIntOrNull()
                     return secondOctet != null && secondOctet in 16..31
                 }
             }
             
             // 169.254.x.x
             if (ip.startsWith("169.254.")) return true
             
             false
        } catch (e: Exception) {
            false
        }
    }

    fun checkAndRequestPermission(
        ip: String,
        onPermissionGranted: () -> Unit
    ) {
        if (Build.VERSION.SDK_INT >= 33 && isLocalIp(ip)) {
             if (ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.NEARBY_WIFI_DEVICES
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                onPermissionGranted()
            } else {
                permissionLauncher.launch(Manifest.permission.NEARBY_WIFI_DEVICES)
            }
        } else {
            onPermissionGranted()
        }
    }

    var useSSL by remember { mutableStateOf(true) }
    var useStartTls by remember { mutableStateOf(false) }
    var seeOnlyStatusCode by remember { mutableStateOf(false) }
    var trustSelfSigned by remember { mutableStateOf(false) }

    val protocols = listOf("HTTP", "DNS", "NTP", "Ping", "Traceroute", "SMTP", "POP3", "IMAP", "Custom")
    var selectedProtocol by remember { mutableStateOf(protocols[0]) }
    var ipAddress by remember { mutableStateOf("") }
    var port by remember { mutableStateOf("") }
    var expanded by remember { mutableStateOf(false) }

    var expandedTimezone by remember { mutableStateOf(false) }
    var selectedTimezone by remember { mutableStateOf("") }
    val timezones = remember { ZoneId.getAvailableZoneIds().sorted() }
    var useTcp by remember { mutableStateOf(true) }
    var customMessage by remember { mutableStateOf("Hello from MultiProtocolClient") }
    var useSystemTimezone by remember { mutableStateOf(true) }

    var dnsQueryType by remember { mutableStateOf("A") }
    val dnsTypes = listOf("A", "MX", "CNAME", "NS", "PTR", "ANY")
    var useDnsOverHttps by remember { mutableStateOf(false) }
    var useDnsOverTls by remember { mutableStateOf(false) }
    var useDnsOverQuic by remember { mutableStateOf(false) }
    var forceHttp3 by remember { mutableStateOf(false) }
    var useCustomResolver by remember { mutableStateOf(false) }
    var customResolverHost by remember { mutableStateOf("") }
    var customResolverPort by remember { mutableStateOf("53") }
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
    val doqResolvers = resolvers.filter {
        it.contains("AdGuard")
    }

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
                useDnsOverQuic = false
                forceHttp3 = false
                useCustomResolver = false
                customResolverHost = ""
                customResolverPort = "53"
                useRecursion = true
                useTcp4Dns = false
            }

            "NTP" -> {
                selectedTimezone = ZoneId.systemDefault().id
                useSystemTimezone = true
            }

            "Ping", "Traceroute" -> {
                // No specific reset needed beyond common fields
            }

            "SMTP" -> {
                port = "587" // Default to STARTTLS preferred often, or 25. Let's say 587 + STARTTLS
                useSSL = false
                useStartTls = true
            }

            "POP3" -> {
                port = "110"
                useSSL = false
            }

            "IMAP" -> {
                port = "143"
                useSSL = false
            }

            "Custom" -> {
                useTcp = true
                customMessage = "Hello from MultiProtocolClient"
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
        // Add a scrollable column for the entire input section so it doesn't break small screens
        Column(
            modifier = Modifier
                .weight(1f) // Takes available space above response box
                .verticalScroll(rememberScrollState())
        ) {
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
        if (selectedProtocol == "HTTP" || selectedProtocol == "SMTP" || selectedProtocol == "POP3" || selectedProtocol == "IMAP") {
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
                            if (it) {
                                useStartTls = false // Mutual exclusive usually for port defaults
                            }
                            
                            if (selectedProtocol == "HTTP") {
                                port = if (it) "443" else "80"
                            } else if (selectedProtocol == "SMTP") {
                                // If SSL is on -> 465. If Off -> Check STARTTLS logic later, but here if SSL off, restore 25 or 587?
                                // Simplified: SSL On -> 465. SSL Off -> 25 (User can enable STARTTLS to get 587)
                                port = if (it) "465" else "25"
                            } else if (selectedProtocol == "POP3") {
                                port = if (it) "995" else "110"
                            } else if (selectedProtocol == "IMAP") {
                                port = if (it) "993" else "143"
                            }
                        }
                    )
                    Text(text = "Use SSL")
                    
                    if (selectedProtocol == "HTTP") {
                        Spacer(modifier = Modifier.width(8.dp))
                        Checkbox(
                            checked = seeOnlyStatusCode,
                            onCheckedChange = { seeOnlyStatusCode = it }
                        )
                        Text(text = "Only status code")
                    }

                    if (selectedProtocol == "SMTP") {
                        Spacer(modifier = Modifier.width(8.dp))
                         Checkbox(
                            checked = useStartTls,
                            onCheckedChange = { 
                                useStartTls = it 
                                if (it) {
                                    useSSL = false
                                    port = "587"
                                } else {
                                     // If turning off STARTTLS, go back to 25?
                                     if (!useSSL) port = "25"
                                }
                            }
                        )
                        Text(text = "Use STARTTLS")
                    }
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

            // DNS resolver section
            Spacer(modifier = Modifier.height(8.dp))

            // "Use Custom DNS Server" checkbox
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 4.dp)
            ) {
                Checkbox(
                    checked = useCustomResolver,
                    onCheckedChange = { checked ->
                        useCustomResolver = checked
                        if (checked) {
                            // Set default port based on current mode
                            customResolverPort = when {
                                useDnsOverHttps -> "443"
                                useDnsOverTls -> "853"
                                useDnsOverQuic -> "853"
                                else -> "53"
                            }
                        }
                    }
                )
                Text(text = "Use Custom DNS Server")
            }

            if (useCustomResolver) {
                // Editable hostname + port fields
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = customResolverHost,
                        onValueChange = { customResolverHost = it },
                        label = { Text("Server") },
                        placeholder = {
                            Text(
                                when {
                                    useDnsOverHttps -> "e.g. dns.example.com"
                                    useDnsOverTls -> "e.g. dns.example.com"
                                    useDnsOverQuic -> "e.g. dns.example.com"
                                    else -> "e.g. 1.1.1.1"
                                }
                            )
                        },
                        keyboardOptions = KeyboardOptions.Default.copy(keyboardType = KeyboardType.Text),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = customResolverPort,
                        onValueChange = { customResolverPort = it },
                        label = { Text("Port") },
                        keyboardOptions = KeyboardOptions.Default.copy(keyboardType = KeyboardType.Number),
                        modifier = Modifier.width(100.dp)
                    )
                }
            } else {
                // Standard resolver dropdown
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

                    ExposedDropdownMenu(
                        expanded = expandedResolver,
                        onDismissRequest = { expandedResolver = false }
                    ) {
                        resolvers.forEach { resolver ->
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
            }

            // Checkbox for DNS over HTTPS/TLS/QUIC and checkbox for tcp and recursion
            Column(
                modifier = Modifier.padding(top = 8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Start
                ) {
                    // DoH, DoT and DoQ
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
                                        useDnsOverQuic = false
                                        useTcp4Dns = true
                                        if (useCustomResolver) {
                                            customResolverPort = "443"
                                        }
                                    } else {
                                        useTcp4Dns = false
                                        forceHttp3 = false
                                        if (useCustomResolver) {
                                            customResolverPort = "53"
                                        }
                                    }
                                }
                            )
                            Text(text = "Use DoH")
                        }

                        // Force HTTP/3 checkbox (visible only when DoH is active)
                        if (useDnsOverHttps) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(start = 32.dp, top = 2.dp)
                            ) {
                                Checkbox(
                                    checked = forceHttp3,
                                    onCheckedChange = { forceHttp3 = it }
                                )
                                Text(text = "Force HTTP/3")
                            }
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
                                        useDnsOverQuic = false
                                        forceHttp3 = false
                                        useTcp4Dns = true
                                        if (useCustomResolver) {
                                            customResolverPort = "853"
                                        }
                                    } else {
                                        useTcp4Dns = false
                                        if (useCustomResolver) {
                                            customResolverPort = "53"
                                        }
                                    }
                                }
                            )
                            Text(text = "Use DoT")
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(top = 4.dp)
                        ) {
                            Checkbox(
                                checked = useDnsOverQuic,
                                onCheckedChange = { checked ->
                                    useDnsOverQuic = checked
                                    if (checked) {
                                        useDnsOverHttps = false
                                        useDnsOverTls = false
                                        forceHttp3 = false
                                        useTcp4Dns = false
                                        if (useCustomResolver) {
                                            customResolverPort = "853"
                                        } else {
                                            // Default to AdGuard (supports DoQ)
                                            if (doqResolvers.isNotEmpty() && !doqResolvers.contains(selectedResolver)) {
                                                selectedResolver = doqResolvers.first()
                                            }
                                        }
                                    } else {
                                        if (useCustomResolver) {
                                            customResolverPort = "53"
                                        }
                                    }
                                }
                            )
                            Text(text = "Use DoQ")
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
                                    if (!useDnsOverHttps && !useDnsOverTls && !useDnsOverQuic) {
                                        useTcp4Dns = checked
                                    }
                                },
                                enabled = !useDnsOverHttps && !useDnsOverTls && !useDnsOverQuic
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
        if (selectedProtocol == "Ping" || selectedProtocol == "Traceroute") {
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedTextField(
                value = ipAddress,
                onValueChange = { ipAddress = it },
                label = { Text("Host") },
                keyboardOptions = KeyboardOptions.Default.copy(keyboardType = KeyboardType.Text),
                modifier = Modifier.fillMaxWidth()
            )
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
            
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = customMessage,
                onValueChange = { customMessage = it },
                label = { Text("Message") },
                keyboardOptions = KeyboardOptions.Default.copy(keyboardType = KeyboardType.Text),
                modifier = Modifier.fillMaxWidth()
            )
            }
        } // End of scrollable Column

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
                        "Custom", "HTTP", "SMTP", "POP3", "IMAP" -> {
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
                            // Validation only here, request is sent below in the permission block
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

                        "Ping", "Traceroute" -> {
                            if (ipAddress.isEmpty()) {
                                coroutineScope.launch {
                                    Toast.makeText(
                                        context,
                                        "Host is required",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                                return@FilledTonalButton
                            }
                        }
                    }

                    // Wrap the send logic in the permission check
                    
                    checkAndRequestPermission(ipAddress) {
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
                            "Custom" -> viewModel.sendCustomRequest(ipAddress, port, customMessage, useTcp)
                            "DNS" -> {
                                // DNS might be exempt if using system DNS, but if direct IP query to local resolver:
                                 viewModel.sendDnsRequest(
                                    ipAddress,
                                    dnsQueryType,
                                    useDnsOverHttps,
                                    useDnsOverTls,
                                    useDnsOverQuic,
                                    selectedResolver,
                                    useRecursion,
                                    useTcp4Dns,
                                    forceHttp3,
                                    useCustomResolver,
                                    customResolverHost,
                                    customResolverPort.toIntOrNull() ?: 53
                                )
                            }
                            "Ping" -> viewModel.sendPingRequest(ipAddress)
                            "Traceroute" -> viewModel.sendTracerouteRequest(ipAddress)
                            "SMTP" -> viewModel.sendSmtpRequest(ipAddress, port, useSSL, useStartTls)
                            "POP3" -> viewModel.sendPop3Request(ipAddress, port, useSSL)
                            "IMAP" -> viewModel.sendImapRequest(ipAddress, port, useSSL)
                        }
                        coroutineScope.launch {
                            Toast.makeText(context, "Request Sent", Toast.LENGTH_SHORT).show()
                        }
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
                            useDnsOverQuic = false
                            forceHttp3 = false
                            useCustomResolver = false
                            customResolverHost = ""
                            customResolverPort = "53"
                            useRecursion = true
                            useTcp4Dns = false
                        }

                        "NTP" -> {
                            ipAddress = ""
                            selectedTimezone = ZoneId.systemDefault().id
                            useSystemTimezone = true
                        }

                        "Ping", "Traceroute" -> {
                            ipAddress = ""
                        }

                        "SMTP" -> {
                            ipAddress = ""
                            port = "587"
                            useSSL = false
                            useStartTls = true
                        }

                        "POP3" -> {
                            ipAddress = ""
                            port = "110"
                            useSSL = false
                        }

                        "IMAP" -> {
                            ipAddress = ""
                            port = "143"
                            useSSL = false
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
                .weight(1f) // Gives half/remaining space to output box on large screens
                .heightIn(min = 200.dp) // Ensures it always has minimum height to scroll
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


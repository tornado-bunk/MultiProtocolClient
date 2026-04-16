package it.tornado.multiprotocolclient.screens
import it.tornado.multiprotocolclient.viewmodel.ClientViewModel
import it.tornado.multiprotocolclient.settings.ConsoleFontSize
import it.tornado.multiprotocolclient.settings.UiSettings
import it.tornado.multiprotocolclient.settings.renderConsoleLines

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Arrangement.spacedBy
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalClipboardManager

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import kotlinx.coroutines.launch
import android.Manifest
import android.os.Build
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts


import java.time.ZoneId

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ClientScreen(
    modifier: Modifier = Modifier,
    viewModel: ClientViewModel,
    uiSettings: UiSettings = UiSettings(),
    initialProtocol: String = allProtocols.first(),
    showProtocolPickerInline: Boolean = true,
    onChangeProtocolRequested: () -> Unit = {},
    onOpenFullscreenConsole: (() -> Unit)? = null,
    onOpenTerminalConsole: (() -> Unit)? = null
) {
    val response by viewModel.response.collectAsState()
    val renderedResponse = remember(
        response,
        uiSettings.consoleShowTimestamps,
        uiSettings.maskSensitiveOutput,
        uiSettings.consoleBufferLimit
    ) {
        renderConsoleLines(response, uiSettings)
    }
    val consoleTextStyle = when (uiSettings.consoleFontSize) {
        ConsoleFontSize.SMALL -> MaterialTheme.typography.bodySmall
        ConsoleFontSize.MEDIUM -> MaterialTheme.typography.bodyMedium
        ConsoleFontSize.LARGE -> MaterialTheme.typography.bodyLarge
    }
    val previewListState = rememberLazyListState()
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val focusManager = LocalFocusManager.current
    val coroutineScope = rememberCoroutineScope()
    val isInteractiveSessionActive by viewModel.isInteractiveSessionActive.collectAsState()

    // Permission launcher for Android 13+ (SDK 33)
    var sshUsername by remember { mutableStateOf("") }
    var sshPassword by remember { mutableStateOf("") }
    var sshPrivateKey by remember { mutableStateOf<String?>(null) }
    var sshPublicKey by remember { mutableStateOf<String?>(null) }
    var useSshKeyAuth by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            if (uiSettings.showLocalNetworkWarnings) {
                coroutineScope.launch {
                    Toast.makeText(context, "Permission granted. Please click Send again.", Toast.LENGTH_SHORT).show()
                }
            }
        } else {
            if (uiSettings.showLocalNetworkWarnings) {
                coroutineScope.launch {
                    Toast.makeText(context, "Permission denied. Local connections may fail.", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    val privateKeyLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            try {
                context.contentResolver.openInputStream(it)?.use { stream ->
                    sshPrivateKey = stream.reader().readText()
                    Toast.makeText(context, "Private key loaded", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Failed to read private key", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val publicKeyLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            try {
                context.contentResolver.openInputStream(it)?.use { stream ->
                    sshPublicKey = stream.reader().readText()
                    Toast.makeText(context, "Public key loaded", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Failed to read public key", Toast.LENGTH_SHORT).show()
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

    fun extractHttpPortFromInput(input: String): Int? {
        fun parsePortFromAuthority(authorityRaw: String): Int? {
            val authority = authorityRaw.substringAfterLast("@").trim()
            if (authority.isBlank()) return null

            if (authority.startsWith("[")) {
                val closeIdx = authority.indexOf(']')
                if (closeIdx >= 0 && closeIdx + 1 < authority.length && authority[closeIdx + 1] == ':') {
                    val portValue = authority.substring(closeIdx + 2).toIntOrNull()
                    return if (portValue != null && portValue in 1..65535) portValue else null
                }
                return null
            }

            val colonIdx = authority.lastIndexOf(':')
            if (colonIdx <= 0) return null
            val portValue = authority.substring(colonIdx + 1).toIntOrNull()
            return if (portValue != null && portValue in 1..65535) portValue else null
        }

        val raw = input.trim()
        if (raw.isBlank()) return null

        val candidate = if (raw.contains("://")) raw else "http://$raw"
        val parsedUri = runCatching { java.net.URI(candidate) }.getOrNull()
        if (parsedUri != null) {
            if (parsedUri.port in 1..65535) {
                return parsedUri.port
            }
            parsedUri.rawAuthority?.let { authority ->
                parsePortFromAuthority(authority)?.let { return it }
            }
        }

        val fallbackAuthority = raw
            .substringAfter("://", raw)
            .substringBefore("/")
            .substringBefore("?")
            .substringBefore("#")
        return parsePortFromAuthority(fallbackAuthority)
    }

    var useSSL by remember { mutableStateOf(true) }
    var useStartTls by remember { mutableStateOf(false) }
    var seeOnlyStatusCode by remember { mutableStateOf(false) }
    var trustSelfSigned by remember { mutableStateOf(false) }

    var selectedProtocol by remember { mutableStateOf(initialProtocol.ifBlank { allProtocols.first() }) }
    var ipAddress by remember { mutableStateOf("") }
    var port by remember { mutableStateOf("") }
    var expandedSection by remember { mutableStateOf(protocolSections.first().title) }

    LaunchedEffect(initialProtocol) {
        if (initialProtocol.isNotBlank() && selectedProtocol != initialProtocol) {
            selectedProtocol = initialProtocol
        }
    }

    var expandedTimezone by remember { mutableStateOf(false) }
    var selectedTimezone by remember { mutableStateOf("") }
    val timezones = remember { ZoneId.getAvailableZoneIds().sorted() }
    var useTcp by remember { mutableStateOf(true) }
    var customMessage by remember { mutableStateOf("Hello from MultiProtocolClient") }
    var useSystemTimezone by remember { mutableStateOf(true) }

    var wolMacAddress by remember { mutableStateOf("") }
    var wolBroadcast by remember { mutableStateOf("255.255.255.255") }

    var dnsQueryType by remember { mutableStateOf("A") }
    val dnsTypes = listOf("A", "MX", "CNAME", "NS", "PTR", "ANY")
    var useDnsOverHttps by remember { mutableStateOf(false) }
    var useDnsOverTls by remember { mutableStateOf(false) }
    var useDnsOverQuic by remember { mutableStateOf(false) }
    var forceHttp3 by remember { mutableStateOf(false) }
    var useCustomResolver by remember { mutableStateOf(false) }
    var customResolverHost by remember { mutableStateOf("") }
    var customResolverPort by remember { mutableStateOf("53") }
    
    var ftpUsername by remember { mutableStateOf("anonymous") }
    var ftpPassword by remember { mutableStateOf("") }
    var useSftp by remember { mutableStateOf(false) }
    
    var tftpFilename by remember { mutableStateOf("") }
    
    var snmpCommunity by remember { mutableStateOf("public") }
    var snmpOid by remember { mutableStateOf("1.3.6.1.2.1.1.1.0") }
    
    var mqttTopic by remember { mutableStateOf("#") }
    var iperfDuration by remember { mutableStateOf("10") }
    var iperfUseUdp by remember { mutableStateOf(false) }
    var iperfReverse by remember { mutableStateOf(false) }
    var mdnsServiceType by remember { mutableStateOf("_http._tcp.") }
    var mdnsTimeout by remember { mutableStateOf("8") }
    var portScanStartPort by remember { mutableStateOf("1") }
    var portScanEndPort by remember { mutableStateOf("1024") }
    var portScanTimeoutMs by remember { mutableStateOf("250") }
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
    var animateIn by remember { mutableStateOf(false) }
    val compactLayoutProtocols = setOf("Discovery", "UPnP", "SSH", "Telnet", "WHOIS", "Ping", "Traceroute")
    val defaultTimeoutSeconds = uiSettings.defaultTimeoutSeconds.coerceIn(1, 120)
    val defaultTimeoutMs = (defaultTimeoutSeconds * 100).coerceIn(50, 5000)

    LaunchedEffect(Unit) {
        animateIn = true
    }

    LaunchedEffect(renderedResponse.size, uiSettings.consoleAutoScroll) {
        if (uiSettings.consoleAutoScroll && renderedResponse.isNotEmpty()) {
            previewListState.animateScrollToItem(renderedResponse.lastIndex)
        }
    }

    //At launch, reset the fields based on the selected protocol
    LaunchedEffect(selectedProtocol) {
        if (uiSettings.keepLastFieldsPerProtocol) {
            viewModel.resetResponse()
            viewModel.disconnectInteractiveSession("Telnet")
            viewModel.disconnectInteractiveSession("SSH")
            return@LaunchedEffect
        }

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

            "iPerf3" -> {
                port = "5201"
                iperfDuration = "10"
                iperfUseUdp = false
                iperfReverse = false
            }

            "iPerf2" -> {
                port = "5001"
                iperfDuration = "10"
                iperfUseUdp = false
                iperfReverse = false
            }

            "mDNS / Bonjour" -> {
                mdnsServiceType = "_http._tcp."
                mdnsTimeout = defaultTimeoutSeconds.toString()
            }

            "Port Scanner" -> {
                portScanStartPort = "1"
                portScanEndPort = "1024"
                portScanTimeoutMs = defaultTimeoutMs.toString()
            }

            "Telnet" -> {
                port = "23"
            }

            "SSH" -> {
                port = "22"
            }

            "Custom" -> {
                useTcp = true
                customMessage = "Hello from MultiProtocolClient"
            }
        }

        // Reset response
        viewModel.resetResponse()
        viewModel.disconnectInteractiveSession("Telnet")
        viewModel.disconnectInteractiveSession("SSH")
    }

    // Main content
    AnimatedVisibility(
        visible = animateIn,
        enter = fadeIn(animationSpec = tween(280)) + slideInVertically(
            initialOffsetY = { it / 9 },
            animationSpec = tween(320)
        )
    ) {
    BoxWithConstraints(
        modifier = modifier.fillMaxSize()
    ) {
        val isTabletScreen = maxWidth >= 840.dp
        val horizontalPadding = if (isTabletScreen) 24.dp else 16.dp
        val maxContentWidth = if (isTabletScreen) 960.dp else maxWidth

        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = maxContentWidth)
                    .padding(start = horizontalPadding, top = 4.dp, end = horizontalPadding)
                    .fillMaxSize()
            ) {
                if (!showProtocolPickerInline) {
                    CenterAlignedTopAppBar(
                        title = {
                            Text(
                                text = selectedProtocol,
                                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold)
                            )
                        },
                        expandedHeight = 20.dp,
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = Color.Transparent
                        ),
                        windowInsets = WindowInsets(0, 0, 0, 0)
                    )
                }
                // Scrollable input area; horizontal scroll prevents clipping on very small screens.
                Column(
                    modifier = if (!showProtocolPickerInline || selectedProtocol in compactLayoutProtocols) {
                        Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                    } else {
                        Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .verticalScroll(rememberScrollState())
                    }
                ) {
            if (showProtocolPickerInline) {
                Text(
                    text = "Choose a protocol",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = "Protocols are grouped by category to keep the flow clear.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
                )

                protocolSections.forEach { section ->
                    ElevatedCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 10.dp),
                        onClick = {
                            expandedSection =
                                if (expandedSection == section.title) "" else section.title
                        }
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = section.title,
                                    style = MaterialTheme.typography.titleSmall
                                )
                                Icon(
                                    imageVector = if (expandedSection == section.title) {
                                        Icons.Filled.ExpandLess
                                    } else {
                                        Icons.Filled.ExpandMore
                                    },
                                    contentDescription = null
                                )
                            }

                            AnimatedVisibility(visible = expandedSection == section.title) {
                                FlowRow(
                                    horizontalArrangement = spacedBy(8.dp),
                                    verticalArrangement = spacedBy(8.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 10.dp)
                                ) {
                                    section.protocols.forEach { protocol ->
                                        FilterChip(
                                            selected = selectedProtocol == protocol,
                                            onClick = { selectedProtocol = protocol },
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

            // Show additional fields based on the selected protocol
        if (selectedProtocol == "HTTP" || selectedProtocol == "SMTP" || selectedProtocol == "POP3" || selectedProtocol == "IMAP") {
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                OutlinedTextField(
                    value = ipAddress,
                    onValueChange = { value ->
                        ipAddress = value
                        if (selectedProtocol == "HTTP") {
                            val normalizedInput = value.trim()
                            val lower = normalizedInput.lowercase()
                            val extractedPort = extractHttpPortFromInput(normalizedInput)
                            when {
                                lower.startsWith("https://") -> {
                                    useSSL = true
                                    if (extractedPort != null) {
                                        port = extractedPort.toString()
                                    } else if (port == "80") {
                                        port = "443"
                                    }
                                }
                                lower.startsWith("http://") -> {
                                    useSSL = false
                                    if (extractedPort != null) {
                                        port = extractedPort.toString()
                                    } else if (port == "443") {
                                        port = "80"
                                    }
                                }
                                extractedPort != null -> {
                                    port = extractedPort.toString()
                                }
                            }
                        }
                    },
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
            Spacer(modifier = Modifier.height(if (showProtocolPickerInline) 8.dp else 4.dp))

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

        if (selectedProtocol == "iPerf3" || selectedProtocol == "iPerf2") {
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = ipAddress,
                    onValueChange = { ipAddress = it },
                    label = { Text("Server Host") },
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

            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = iperfDuration,
                    onValueChange = { iperfDuration = it },
                    label = { Text("Duration (s)") },
                    keyboardOptions = KeyboardOptions.Default.copy(keyboardType = KeyboardType.Number),
                    modifier = Modifier.width(140.dp)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickable { iperfUseUdp = !iperfUseUdp }
            ) {
                Checkbox(
                    checked = iperfUseUdp,
                    onCheckedChange = { iperfUseUdp = it }
                )
                Text("Use UDP")
            }

            if (selectedProtocol == "iPerf3") {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { iperfReverse = !iperfReverse }
                ) {
                    Checkbox(
                        checked = iperfReverse,
                        onCheckedChange = { iperfReverse = it }
                    )
                    Text("Reverse mode (-R)")
                }
            }
        }

        if (selectedProtocol == "mDNS / Bonjour") {
            Spacer(modifier = Modifier.height(16.dp))
            Text("Discover local mDNS/Bonjour services by type.")
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = mdnsServiceType,
                    onValueChange = { mdnsServiceType = it },
                    label = { Text("Service Type") },
                    keyboardOptions = KeyboardOptions.Default.copy(keyboardType = KeyboardType.Text),
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = mdnsTimeout,
                    onValueChange = { mdnsTimeout = it },
                    label = { Text("Timeout (s)") },
                    keyboardOptions = KeyboardOptions.Default.copy(keyboardType = KeyboardType.Number),
                    modifier = Modifier.width(130.dp)
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Example service type: _http._tcp.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (selectedProtocol == "Port Scanner") {
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedTextField(
                value = ipAddress,
                onValueChange = { ipAddress = it },
                label = { Text("Target Host") },
                keyboardOptions = KeyboardOptions.Default.copy(keyboardType = KeyboardType.Text),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = portScanStartPort,
                    onValueChange = { portScanStartPort = it },
                    label = { Text("Start Port") },
                    keyboardOptions = KeyboardOptions.Default.copy(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = portScanEndPort,
                    onValueChange = { portScanEndPort = it },
                    label = { Text("End Port") },
                    keyboardOptions = KeyboardOptions.Default.copy(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = portScanTimeoutMs,
                    onValueChange = { portScanTimeoutMs = it },
                    label = { Text("Timeout ms") },
                    keyboardOptions = KeyboardOptions.Default.copy(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f)
                )
            }
        }



        // Show additional fields based on the selected protocol
        if (selectedProtocol == "Telnet" || selectedProtocol == "SSH") {
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
                    modifier = Modifier.weight(1f),
                    enabled = !isInteractiveSessionActive
                )

                OutlinedTextField(
                    value = port,
                    onValueChange = { port = it },
                    label = { Text("Port") },
                    keyboardOptions = KeyboardOptions.Default.copy(keyboardType = KeyboardType.Number),
                    modifier = Modifier.width(120.dp),
                    enabled = !isInteractiveSessionActive
                )
            }
            
            if (selectedProtocol == "SSH") {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = sshUsername,
                        onValueChange = { sshUsername = it },
                        label = { Text("Username") },
                        keyboardOptions = KeyboardOptions.Default.copy(keyboardType = KeyboardType.Text),
                        modifier = Modifier.weight(1f),
                        enabled = !isInteractiveSessionActive
                    )
                    OutlinedTextField(
                        value = sshPassword,
                        onValueChange = { sshPassword = it },
                        label = { Text(if (useSshKeyAuth) "Passphrase" else "Password") },
                        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions.Default.copy(keyboardType = KeyboardType.Password),
                        modifier = Modifier.weight(1f),
                        enabled = !isInteractiveSessionActive
                    )
                }
                
                if (!isInteractiveSessionActive) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { useSshKeyAuth = !useSshKeyAuth }
                    ) {
                        Checkbox(
                            checked = useSshKeyAuth,
                            onCheckedChange = { useSshKeyAuth = it }
                        )
                        Text(
                            text = "Use Key Authentication",
                            modifier = Modifier.padding(start = 4.dp)
                        )
                    }

                    if (useSshKeyAuth) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = { privateKeyLauncher.launch("*/*") },
                                shape = MaterialTheme.shapes.large,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(if (sshPrivateKey == null) "Load Private Key" else "Private Key Loaded")
                            }
                            OutlinedButton(
                                onClick = { publicKeyLauncher.launch("*/*") },
                                shape = MaterialTheme.shapes.large,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(if (sshPublicKey == null) "Load Public Key" else "Public Key Loaded")
                            }
                        }
                        if (sshPrivateKey != null || sshPublicKey != null) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                TextButton(onClick = {
                                    sshPrivateKey = null
                                    sshPublicKey = null
                                }) {
                                    Text("Clear Keys")
                                }
                            }
                        }
                    }
                }
            }
            
            if (selectedProtocol == "Telnet") {
                if (isInteractiveSessionActive) {
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = customMessage,
                        onValueChange = { customMessage = it },
                        label = { Text("Command") },
                        keyboardOptions = KeyboardOptions.Default.copy(keyboardType = KeyboardType.Text),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
         }

        // WoL fields
        if (selectedProtocol == "WoL") {
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedTextField(
                value = wolMacAddress,
                onValueChange = { wolMacAddress = it },
                label = { Text("MAC Address (AA:BB:CC:DD:EE:FF)") },
                keyboardOptions = KeyboardOptions.Default.copy(keyboardType = KeyboardType.Text),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = wolBroadcast,
                onValueChange = { wolBroadcast = it },
                label = { Text("Broadcast Address") },
                keyboardOptions = KeyboardOptions.Default.copy(keyboardType = KeyboardType.Text),
                modifier = Modifier.fillMaxWidth()
            )
        }

        // WHOIS fields
        if (selectedProtocol == "WHOIS") {
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedTextField(
                value = ipAddress,
                onValueChange = { ipAddress = it },
                label = { Text("Domain (e.g. google.com)") },
                keyboardOptions = KeyboardOptions.Default.copy(keyboardType = KeyboardType.Text),
                modifier = Modifier.fillMaxWidth()
            )
        }

        // Discovery fields
        if (selectedProtocol == "Discovery") {
            Spacer(modifier = Modifier.height(16.dp))
            Text("This will scan the local network using mDNS (Bonjour) and SSDP (UPnP) to find devices and services. It might take up to 10 seconds.")
        }
        
        // FTP fields
        if (selectedProtocol == "FTP") {
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
                    label = { Text(if (useSftp) "Port (22)" else "Port (21)") },
                    keyboardOptions = KeyboardOptions.Default.copy(keyboardType = KeyboardType.Number),
                    modifier = Modifier.width(120.dp)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = ftpUsername,
                    onValueChange = { ftpUsername = it },
                    label = { Text("Username") },
                    keyboardOptions = KeyboardOptions.Default.copy(keyboardType = KeyboardType.Text),
                    modifier = Modifier.weight(1f)
                )

                OutlinedTextField(
                    value = ftpPassword,
                    onValueChange = { ftpPassword = it },
                    label = { Text("Password") },
                    visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions.Default.copy(keyboardType = KeyboardType.Password),
                    modifier = Modifier.weight(1f)
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickable { useSftp = !useSftp }
            ) {
                Checkbox(
                    checked = useSftp,
                    onCheckedChange = { useSftp = it }
                )
                Text(
                    text = "Use SFTP (SSH File Transfer Protocol)",
                    modifier = Modifier.padding(start = 4.dp)
                )
            }
        }

        // TFTP fields
        if (selectedProtocol == "TFTP") {
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
                    label = { Text("Port (69)") },
                    keyboardOptions = KeyboardOptions.Default.copy(keyboardType = KeyboardType.Number),
                    modifier = Modifier.width(120.dp)
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = tftpFilename,
                onValueChange = { tftpFilename = it },
                label = { Text("Filename (leave empty for 'startup-config')") },
                keyboardOptions = KeyboardOptions.Default.copy(keyboardType = KeyboardType.Text),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "Note: TFTP does not support listing directories. You must know the exact filename.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // SNMP fields
        if (selectedProtocol == "SNMP") {
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
                    label = { Text("Port (161)") },
                    keyboardOptions = KeyboardOptions.Default.copy(keyboardType = KeyboardType.Number),
                    modifier = Modifier.width(120.dp)
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = snmpCommunity,
                    onValueChange = { snmpCommunity = it },
                    label = { Text("Community") },
                    keyboardOptions = KeyboardOptions.Default.copy(keyboardType = KeyboardType.Text),
                    modifier = Modifier.weight(1f)
                )

                OutlinedTextField(
                    value = snmpOid,
                    onValueChange = { snmpOid = it },
                    label = { Text("OID") },
                    keyboardOptions = KeyboardOptions.Default.copy(keyboardType = KeyboardType.Text),
                    modifier = Modifier.weight(1.5f)
                )
            }
        }

        // MQTT fields
        if (selectedProtocol == "MQTT") {
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = ipAddress,
                    onValueChange = { ipAddress = it },
                    label = { Text("Broker Host") },
                    keyboardOptions = KeyboardOptions.Default.copy(keyboardType = KeyboardType.Text),
                    modifier = Modifier.weight(1f)
                )

                OutlinedTextField(
                    value = port,
                    onValueChange = { port = it },
                    label = { Text(if (useSSL) "Port (8883)" else "Port (1883)") },
                    keyboardOptions = KeyboardOptions.Default.copy(keyboardType = KeyboardType.Number),
                    modifier = Modifier.width(120.dp)
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = mqttTopic,
                onValueChange = { mqttTopic = it },
                label = { Text("Topic to Subscribe (e.g., # or sensors/temp)") },
                keyboardOptions = KeyboardOptions.Default.copy(keyboardType = KeyboardType.Text),
                modifier = Modifier.fillMaxWidth()
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = ftpUsername,
                    onValueChange = { ftpUsername = it },
                    label = { Text("Username (Optional)") },
                    keyboardOptions = KeyboardOptions.Default.copy(keyboardType = KeyboardType.Text),
                    modifier = Modifier.weight(1f)
                )

                OutlinedTextField(
                    value = ftpPassword,
                    onValueChange = { ftpPassword = it },
                    label = { Text("Password (Optional)") },
                    visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions.Default.copy(keyboardType = KeyboardType.Password),
                    modifier = Modifier.weight(1f)
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickable { useSSL = !useSSL }
            ) {
                Checkbox(
                    checked = useSSL,
                    onCheckedChange = { useSSL = it }
                )
                Text(
                    text = "Use SSL/TLS",
                    modifier = Modifier.padding(start = 4.dp)
                )
            }
        }

        // UPnP fields
        if (selectedProtocol == "UPnP") {
            Spacer(modifier = Modifier.height(16.dp))
            Text("This will send a UPnP request to your router (IGD) to discover its external IP address and device details.")
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

        var showResetConfirmDialog by remember { mutableStateOf(false) }

        fun performReset() {
            focusManager.clearFocus()
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
                "Ping", "Traceroute" -> ipAddress = ""
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
                "iPerf3" -> {
                    ipAddress = ""
                    port = "5201"
                    iperfDuration = "10"
                    iperfUseUdp = false
                    iperfReverse = false
                }
                "iPerf2" -> {
                    ipAddress = ""
                    port = "5001"
                    iperfDuration = "10"
                    iperfUseUdp = false
                    iperfReverse = false
                }
                "mDNS / Bonjour" -> {
                    mdnsServiceType = "_http._tcp."
                    mdnsTimeout = defaultTimeoutSeconds.toString()
                }
                "Port Scanner" -> {
                    ipAddress = ""
                    portScanStartPort = "1"
                    portScanEndPort = "1024"
                    portScanTimeoutMs = defaultTimeoutMs.toString()
                }
                "Telnet" -> port = "23"
                "SSH" -> {
                    port = "22"
                    sshPrivateKey = null
                    sshPublicKey = null
                }
                "WoL" -> {
                    wolMacAddress = ""
                    wolBroadcast = "255.255.255.255"
                }
                "WHOIS" -> ipAddress = ""
                "FTP" -> {
                    ipAddress = ""
                    port = if (useSftp) "22" else "21"
                    ftpUsername = "anonymous"
                    ftpPassword = ""
                }
                "TFTP" -> {
                    ipAddress = ""
                    port = "69"
                    tftpFilename = ""
                }
                "SNMP" -> {
                    ipAddress = ""
                    port = "161"
                    snmpCommunity = "public"
                    snmpOid = "1.3.6.1.2.1.1.1.0"
                }
                "MQTT" -> {
                    ipAddress = ""
                    port = if (useSSL) "8883" else "1883"
                    mqttTopic = "#"
                    ftpUsername = ""
                    ftpPassword = ""
                    useSSL = false
                }
                "Discovery", "UPnP" -> Unit
                "Custom" -> {
                    ipAddress = ""
                    port = ""
                    useTcp = true
                }
            }
            viewModel.resetResponse()
            viewModel.disconnectInteractiveSession("Telnet")
            viewModel.disconnectInteractiveSession("SSH")
        }

        Spacer(modifier = Modifier.height(if (selectedProtocol in compactLayoutProtocols) 8.dp else 16.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            OutlinedButton(
                modifier = Modifier.heightIn(min = 52.dp),
                shape = MaterialTheme.shapes.large,
                onClick = {
                    if (uiSettings.confirmBeforeReset) {
                        showResetConfirmDialog = true
                    } else {
                        performReset()
                    }
                }
            ) {
                Text(text = "Reset")
            }

            Row(horizontalArrangement = Arrangement.End) {
                val interactiveMode = selectedProtocol == "Telnet" || selectedProtocol == "SSH"
                
                if (interactiveMode && isInteractiveSessionActive) {
                    FilledTonalButton(
                        modifier = Modifier.heightIn(min = 52.dp),
                        shape = MaterialTheme.shapes.extraLarge,
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                            contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                        ),
                        onClick = {
                            focusManager.clearFocus()
                            viewModel.disconnectInteractiveSession(selectedProtocol)
                        }
                    ) {
                        Text(text = "Disconnect")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                }

                if (!(interactiveMode && isInteractiveSessionActive)) {
                FilledTonalButton(
                    modifier = Modifier.heightIn(min = 56.dp),
                    shape = MaterialTheme.shapes.large,
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    onClick = {
                    if (
                        selectedProtocol != "WoL" &&
                        selectedProtocol != "Discovery" &&
                        selectedProtocol != "UPnP" &&
                        selectedProtocol != "mDNS / Bonjour" &&
                        ipAddress.isEmpty()
                    ) {
                        coroutineScope.launch {
                            Toast.makeText(context, "Host is required", Toast.LENGTH_SHORT)
                                .show()
                        }
                        return@FilledTonalButton
                    }

                    when (selectedProtocol) {
                        "Telnet", "SSH" -> {
                            if (!isInteractiveSessionActive) {
                                if (ipAddress.isEmpty()) {
                                    coroutineScope.launch {
                                        Toast.makeText(context, "Host is required", Toast.LENGTH_SHORT).show()
                                    }
                                    return@FilledTonalButton
                                }
                                if (selectedProtocol == "SSH" && sshUsername.isEmpty()) {
                                    coroutineScope.launch {
                                        Toast.makeText(context, "Username is required for SSH", Toast.LENGTH_SHORT).show()
                                    }
                                    return@FilledTonalButton
                                }
                            } else {
                                if (customMessage.isEmpty()) {
                                    return@FilledTonalButton // Do not send empty msg
                                }
                            }
                            // Port validation for connection
                            if (!isInteractiveSessionActive) {
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

                        "iPerf3", "iPerf2" -> {
                            if (ipAddress.isEmpty()) {
                                coroutineScope.launch {
                                    Toast.makeText(context, "Server host is required", Toast.LENGTH_SHORT).show()
                                }
                                return@FilledTonalButton
                            }
                            val portNumber = port.toIntOrNull()
                            val duration = iperfDuration.toIntOrNull()
                            if (portNumber == null || portNumber !in 1..65535) {
                                coroutineScope.launch {
                                    Toast.makeText(context, "Port must be between 1 and 65535", Toast.LENGTH_SHORT).show()
                                }
                                return@FilledTonalButton
                            }
                            if (duration == null || duration <= 0) {
                                coroutineScope.launch {
                                    Toast.makeText(context, "Duration must be greater than zero", Toast.LENGTH_SHORT).show()
                                }
                                return@FilledTonalButton
                            }
                        }

                        "mDNS / Bonjour" -> {
                            val timeout = mdnsTimeout.toIntOrNull()
                            if (timeout == null || timeout <= 0 || timeout > 60) {
                                coroutineScope.launch {
                                    Toast.makeText(context, "mDNS timeout must be between 1 and 60 seconds", Toast.LENGTH_SHORT).show()
                                }
                                return@FilledTonalButton
                            }
                        }

                        "Port Scanner" -> {
                            if (ipAddress.isEmpty()) {
                                coroutineScope.launch {
                                    Toast.makeText(context, "Target host is required", Toast.LENGTH_SHORT).show()
                                }
                                return@FilledTonalButton
                            }
                            val startPort = portScanStartPort.toIntOrNull()
                            val endPort = portScanEndPort.toIntOrNull()
                            val timeoutMs = portScanTimeoutMs.toIntOrNull()
                            if (startPort == null || endPort == null || startPort !in 1..65535 || endPort !in 1..65535) {
                                coroutineScope.launch {
                                    Toast.makeText(context, "Ports must be between 1 and 65535", Toast.LENGTH_SHORT).show()
                                }
                                return@FilledTonalButton
                            }
                            if (startPort > endPort) {
                                coroutineScope.launch {
                                    Toast.makeText(context, "Start port must be <= end port", Toast.LENGTH_SHORT).show()
                                }
                                return@FilledTonalButton
                            }
                            if (timeoutMs == null || timeoutMs !in 50..5000) {
                                coroutineScope.launch {
                                    Toast.makeText(context, "Timeout must be between 50 and 5000 ms", Toast.LENGTH_SHORT).show()
                                }
                                return@FilledTonalButton
                            }
                        }

                        "Ping", "Traceroute", "FTP", "TFTP", "SNMP", "MQTT" -> {
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
                        "WoL" -> {
                            if (wolMacAddress.isEmpty()) {
                                coroutineScope.launch {
                                    Toast.makeText(context, "MAC address is required", Toast.LENGTH_SHORT).show()
                                }
                                return@FilledTonalButton
                            }
                        }
                        "WHOIS" -> {
                            if (ipAddress.isEmpty()) {
                                coroutineScope.launch {
                                    Toast.makeText(context, "Domain is required", Toast.LENGTH_SHORT).show()
                                }
                                return@FilledTonalButton
                            }
                        }
                        "Discovery", "UPnP" -> {
                            // No validation required
                        }
                    }

                    // Wrap the send logic in the permission check
                    checkAndRequestPermission(ipAddress) {
                        if (interactiveMode) {
                            // Don't reset response on sendCommand during active session
                            if (!isInteractiveSessionActive) viewModel.resetResponse()
                        } else {
                            viewModel.resetResponse()
                        }
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
                            "Telnet" -> {
                                if (isInteractiveSessionActive) {
                                    viewModel.sendInteractiveCommand("Telnet", customMessage)
                                    customMessage = ""
                                } else {
                                    viewModel.connectTelnet(ipAddress, port)
                                    coroutineScope.launch { Toast.makeText(context, "Connecting...", Toast.LENGTH_SHORT).show() }
                                }
                            }
                            "SSH" -> {
                                if (isInteractiveSessionActive) {
                                    viewModel.sendInteractiveCommand("SSH", customMessage)
                                    customMessage = ""
                                } else {
                                    viewModel.connectSsh(ipAddress, port, sshUsername, sshPassword, sshPrivateKey, sshPublicKey)
                                    coroutineScope.launch { Toast.makeText(context, "Connecting...", Toast.LENGTH_SHORT).show() }
                                }
                            }
                            "Ping" -> viewModel.sendPingRequest(ipAddress)
                            "Traceroute" -> viewModel.sendTracerouteRequest(ipAddress)
                            "SMTP" -> viewModel.sendSmtpRequest(ipAddress, port, useSSL, useStartTls)
                            "POP3" -> viewModel.sendPop3Request(ipAddress, port, useSSL)
                            "IMAP" -> viewModel.sendImapRequest(ipAddress, port, useSSL)
                            "FTP" -> viewModel.sendFtpRequest(ipAddress, port, ftpUsername, ftpPassword, useSftp)
                            "TFTP" -> viewModel.sendTftpRequest(ipAddress, port, tftpFilename)
                            "SNMP" -> viewModel.sendSnmpRequest(ipAddress, port, snmpCommunity, snmpOid)
                            "MQTT" -> viewModel.sendMqttSubscribeRequest(ipAddress, port, mqttTopic, useSSL, ftpUsername, ftpPassword)
                            "iPerf3" -> viewModel.sendIperf3Request(ipAddress, port, iperfDuration, iperfUseUdp, iperfReverse)
                            "iPerf2" -> viewModel.sendIperf2Request(ipAddress, port, iperfDuration, iperfUseUdp)
                            "mDNS / Bonjour" -> viewModel.sendMdnsBonjourRequest(mdnsServiceType, mdnsTimeout)
                            "Port Scanner" -> viewModel.sendPortScannerRequest(ipAddress, portScanStartPort, portScanEndPort, portScanTimeoutMs)
                            "WoL" -> viewModel.sendWolRequest(wolMacAddress, wolBroadcast)
                            "WHOIS" -> viewModel.sendWhoisRequest(ipAddress)
                            "Discovery" -> viewModel.sendDiscoveryRequest()
                            "UPnP" -> viewModel.sendUpnpRequest()
                        }
                        coroutineScope.launch {
                            Toast.makeText(context, "Request Sent", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            ) {
                Text(text = if (interactiveMode) {
                    if (selectedProtocol == "Telnet" && isInteractiveSessionActive) "Send Cmd" else "Connect"
                } else {
                    "Send"
                })
            }
            }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f) // Gives half/remaining space to output box on large screens
                .padding(bottom = if (showProtocolPickerInline) 0.dp else 0.dp)
                .heightIn(min = 200.dp) // Ensures it always has minimum height to scroll
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    val useTerminalConsole = selectedProtocol == "SSH"
                    if (useTerminalConsole) {
                        FilledTonalButton(
                            modifier = Modifier.heightIn(min = 44.dp),
                            shape = MaterialTheme.shapes.large,
                            onClick = { onOpenTerminalConsole?.invoke() },
                            enabled = onOpenTerminalConsole != null && isInteractiveSessionActive
                        ) {
                            Text("Console")
                        }
                    } else {
                        FilledTonalButton(
                            modifier = Modifier.heightIn(min = 44.dp),
                            shape = MaterialTheme.shapes.large,
                            onClick = { onOpenFullscreenConsole?.invoke() },
                            enabled = onOpenFullscreenConsole != null
                        ) {
                            Text("Full screen")
                        }
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    OutlinedButton(
                        modifier = Modifier.heightIn(min = 44.dp),
                        shape = MaterialTheme.shapes.large,
                        onClick = {
                            val fullText = renderedResponse.joinToString("\n")
                            clipboardManager.setText(AnnotatedString(fullText))
                            coroutineScope.launch {
                                Toast.makeText(context, "Output copied to clipboard", Toast.LENGTH_SHORT).show()
                            }
                        },
                        enabled = renderedResponse.isNotEmpty()
                    ) {
                        Text("Copy output")
                    }
                }

                SelectionContainer(
                    modifier = Modifier.fillMaxSize()
                ) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        state = previewListState
                    ) {
                        items(renderedResponse) { line ->
                            when {
                                line.startsWith("=== ") -> {
                                    Text(
                                        text = line.removePrefix("=== ").removeSuffix(" ==="),
                                        style = MaterialTheme.typography.titleMedium,
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
                                            .padding(top = 6.dp, bottom = 3.dp),
                                        shape = MaterialTheme.shapes.medium,
                                        color = MaterialTheme.colorScheme.surfaceContainerHigh
                                    ) {
                                        Text(
                                            text = sectionLabel,
                                            style = MaterialTheme.typography.labelLarge,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
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

        if (showResetConfirmDialog) {
            AlertDialog(
                onDismissRequest = { showResetConfirmDialog = false },
                title = { Text("Reset fields?") },
                text = { Text("This will reset current protocol fields and clear console output.") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showResetConfirmDialog = false
                            performReset()
                        }
                    ) { Text("Reset") }
                },
                dismissButton = {
                    TextButton(onClick = { showResetConfirmDialog = false }) { Text("Cancel") }
                }
            )
        }
    }
    }
}
}
}


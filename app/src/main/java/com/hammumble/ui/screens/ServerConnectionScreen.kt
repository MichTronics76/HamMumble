package com.hammumble.ui.screens

import android.app.Activity
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.hammumble.R
import com.hammumble.data.ServerInfo
import com.hammumble.ui.components.CertificateGenerationDialog
import com.hammumble.ui.viewmodel.MumbleViewModel
import com.hammumble.util.CertificateManager
import kotlinx.coroutines.launch
import android.widget.Toast

@OptIn(ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
fun ServerConnectionScreen(
    onConnect: (ServerInfo) -> Unit,
    onBack: () -> Unit,
    viewModel: MumbleViewModel? = null,
    existingServer: ServerInfo? = null
) {
    val scrollState = rememberScrollState()
    val coroutineScope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }
    val context = LocalContext.current
    
    var serverAddress by remember { mutableStateOf(existingServer?.address ?: "") }
    var port by remember { mutableStateOf(existingServer?.port?.toString() ?: "64738") }
    var username by remember { mutableStateOf(existingServer?.username ?: "") }
    var password by remember { mutableStateOf(existingServer?.password ?: "") }
    var passwordVisible by remember { mutableStateOf(false) }
    var serverName by remember { mutableStateOf(existingServer?.name ?: "") }
    var autoConnect by remember { mutableStateOf(existingServer?.autoConnect ?: false) }
    var autoJoinChannel by remember { mutableStateOf(existingServer?.autoJoinChannel ?: "") }
    var certificatePath by remember { mutableStateOf(existingServer?.clientCertificatePath) }
    var certificatePassword by remember { mutableStateOf(existingServer?.clientCertificatePassword ?: "") }
    var certificatePasswordVisible by remember { mutableStateOf(false) }
    var registerWithServer by remember { mutableStateOf(existingServer?.registerWithServer ?: false) }
    var skipCertificateVerification by remember { mutableStateOf(existingServer?.skipCertificateVerification ?: false) }
    var showCertificateGenerationDialog by remember { mutableStateOf(false) }
    
    // Certificate file picker
    val certificatePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                // Extract filename from URI
                val fileName = uri.lastPathSegment?.substringAfterLast('/') ?: "certificate.p12"
                // Import the certificate to app storage
                val importedPath = CertificateManager.importCertificate(context, uri, fileName)
                if (importedPath != null) {
                    certificatePath = importedPath
                }
            }
        }
    }
    
    val isEditing = existingServer != null
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isEditing) "Edit Server" else stringResource(R.string.connect_to_server)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(paddingValues)
                .focusRequester(focusRequester)
                .focusable()
                .onKeyEvent { keyEvent ->
                    // Handle D-pad and keyboard scrolling
                    if (keyEvent.type == KeyEventType.KeyDown) {
                        when (keyEvent.key) {
                            Key.DirectionUp, Key.PageUp -> {
                                coroutineScope.launch {
                                    scrollState.scrollBy(-100f)
                                }
                                true
                            }
                            Key.DirectionDown, Key.PageDown -> {
                                coroutineScope.launch {
                                    scrollState.scrollBy(100f)
                                }
                                true
                            }
                            else -> false
                        }
                    } else {
                        false
                    }
                }
                .pointerInput(Unit) {
                    // Handle mouse wheel scrolling
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            val scrollDelta = event.changes.firstOrNull()?.scrollDelta
                            if (scrollDelta != null && scrollDelta.y != 0f) {
                                coroutineScope.launch {
                                    scrollState.scrollBy(scrollDelta.y * 20f)
                                }
                            }
                        }
                    }
                }
                .verticalScroll(scrollState)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "Server Details",
                        style = MaterialTheme.typography.headlineSmall
                    )
                    
                    OutlinedTextField(
                        value = serverAddress,
                        onValueChange = { serverAddress = it },
                        label = { Text(stringResource(R.string.server_address)) },
                        placeholder = { Text("mumble.example.com") },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Uri,
                            imeAction = ImeAction.Next
                        ),
                        singleLine = true
                    )
                    
                    OutlinedTextField(
                        value = port,
                        onValueChange = { port = it },
                        label = { Text(stringResource(R.string.port)) },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                            imeAction = ImeAction.Next
                        ),
                        singleLine = true
                    )
                    
                    OutlinedTextField(
                        value = serverName,
                        onValueChange = { serverName = it },
                        label = { Text("Server Name (Optional)") },
                        placeholder = { Text("My Mumble Server") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "User Credentials",
                        style = MaterialTheme.typography.headlineSmall
                    )
                    
                    OutlinedTextField(
                        value = username,
                        onValueChange = { username = it },
                        label = { Text(stringResource(R.string.username)) },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Text,
                            imeAction = ImeAction.Next
                        ),
                        singleLine = true
                    )
                    
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("${stringResource(R.string.password)} (Optional)") },
                        modifier = Modifier.fillMaxWidth(),
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Done
                        ),
                        trailingIcon = {
                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                Icon(
                                    imageVector = if (passwordVisible) Icons.Rounded.Visibility else Icons.Rounded.VisibilityOff,
                                    contentDescription = if (passwordVisible) "Hide password" else "Show password"
                                )
                            }
                        },
                        singleLine = true
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // Auto-connect checkbox
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = autoConnect,
                            onCheckedChange = { autoConnect = it }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Auto-connect on app start",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    
                    // Register with server checkbox
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = registerWithServer,
                            onCheckedChange = { registerWithServer = it }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "Register username on server",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = "Makes your username permanent (usually requires certificate)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    
                    // Skip certificate verification checkbox
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = skipCertificateVerification,
                            onCheckedChange = { skipCertificateVerification = it }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "Skip SSL certificate verification",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = "Use if server has self-signed or invalid certificates (less secure)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // Auto-join channel field
                    OutlinedTextField(
                        value = autoJoinChannel,
                        onValueChange = { autoJoinChannel = it },
                        label = { Text("Auto-join Channel (Optional)") },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("e.g., Root/MyChannel") },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Text,
                            imeAction = ImeAction.Done
                        ),
                        singleLine = true
                    )
                }
            }
            
            // Certificate Card
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "Client Certificate",
                        style = MaterialTheme.typography.headlineSmall
                    )
                    
                    Text(
                        text = "A unique certificate will be generated automatically using your username. You can also select or generate a custom certificate below.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    // Show selected certificate or button to select
                    if (certificatePath != null) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Certificate:",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                    Text(
                                        text = CertificateManager.getFileName(certificatePath) ?: "Unknown",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                }
                                IconButton(
                                    onClick = { 
                                        certificatePath = null
                                        certificatePassword = ""
                                    }
                                ) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = "Remove certificate",
                                        tint = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                }
                            }
                        }
                        
                        // Certificate password field
                        OutlinedTextField(
                            value = certificatePassword,
                            onValueChange = { certificatePassword = it },
                            label = { Text("Certificate Password") },
                            modifier = Modifier.fillMaxWidth(),
                            visualTransformation = if (certificatePasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Password,
                                imeAction = ImeAction.Done
                            ),
                            trailingIcon = {
                                IconButton(onClick = { certificatePasswordVisible = !certificatePasswordVisible }) {
                                    Icon(
                                        imageVector = if (certificatePasswordVisible) Icons.Rounded.Visibility else Icons.Rounded.VisibilityOff,
                                        contentDescription = if (certificatePasswordVisible) "Hide password" else "Show password"
                                    )
                                }
                            },
                            singleLine = true
                        )
                    } else {
                        // No certificate selected - show select and generate buttons
                        OutlinedButton(
                            onClick = {
                                // Open file picker for .p12 files
                                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                                    addCategory(Intent.CATEGORY_OPENABLE)
                                    type = "*/*"
                                    putExtra(Intent.EXTRA_MIME_TYPES, arrayOf(
                                        "application/x-pkcs12",
                                        "application/pkcs12",
                                        "application/x-pfx"
                                    ))
                                }
                                certificatePicker.launch(intent)
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Select Certificate (.p12/.pfx)")
                        }
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        OutlinedButton(
                            onClick = { showCertificateGenerationDialog = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Generate Custom Certificate")
                        }
                        
                        Text(
                            text = "Note: If you don't select a certificate, a unique one will be generated automatically using your username when you connect.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
            }
            
            // Quick Test Servers (only show when adding new server, not editing)
            if (serverAddress.isBlank() && !isEditing) {
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    text = "Quick Test Servers:",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                val testServers = listOf(
                    Triple("GatewayNederland", "gatewaynederland.nl", "64738"),
                    Triple("CBJunkies", "cbjunkies.nl", "64738")
                )
                
                testServers.forEach { (name, address, defaultPort) ->
                    OutlinedButton(
                        onClick = {
                            serverAddress = address
                            port = defaultPort
                            username = "HamMumble"
                            serverName = name
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Try $name", style = MaterialTheme.typography.bodyMedium)
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                }
            }
            
            Spacer(modifier = Modifier.height(10.dp))
            
            Button(
                onClick = {
                    if (serverAddress.isNotBlank() && username.isNotBlank()) {
                        coroutineScope.launch {
                            // Generate a unique certificate if none exists
                            var finalCertPath = certificatePath
                            var finalCertPassword = certificatePassword
                            
                            if (finalCertPath == null) {
                                Toast.makeText(
                                    context,
                                    "Generating unique certificate for ${username.trim()}...",
                                    Toast.LENGTH_SHORT
                                ).show()
                                
                                // Generate with username as common name and a secure random password
                                val autoPassword = java.util.UUID.randomUUID().toString().substring(0, 16)
                                finalCertPath = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                    CertificateManager.generateCertificate(
                                        context = context,
                                        commonName = username.trim(),
                                        email = "",
                                        password = autoPassword
                                    )
                                }
                                finalCertPassword = autoPassword
                                
                                if (finalCertPath != null) {
                                    certificatePath = finalCertPath
                                    certificatePassword = finalCertPassword
                                    Toast.makeText(
                                        context,
                                        "✓ Generated unique certificate for ${username.trim()}",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                } else {
                                    Toast.makeText(
                                        context,
                                        "⚠ Failed to generate certificate",
                                        Toast.LENGTH_LONG
                                    ).show()
                                    return@launch
                                }
                            }
                            
                            val serverInfo = ServerInfo(
                                address = serverAddress.trim(),
                                port = port.toIntOrNull() ?: 64738,
                                username = username.trim(),
                                password = password,
                                name = serverName.takeIf { it.isNotBlank() } ?: serverAddress,
                                autoConnect = autoConnect,
                                autoJoinChannel = autoJoinChannel.trim(),
                                clientCertificatePath = finalCertPath,
                                clientCertificatePassword = finalCertPassword,
                                registerWithServer = registerWithServer,
                                skipCertificateVerification = skipCertificateVerification
                            )
                            
                            // Save the server for future use
                            viewModel?.saveServer(serverInfo)
                            
                            if (isEditing) {
                                // Just save and go back when editing
                                onBack()
                            } else {
                                // Connect when adding new server
                                onConnect(serverInfo)
                            }
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                enabled = serverAddress.isNotBlank() && username.isNotBlank()
            ) {
                Text(
                    text = if (isEditing) "Save Changes" else stringResource(R.string.connect),
                    style = MaterialTheme.typography.titleMedium
                )
            }
        }
    }
    
    // Certificate generation dialog
    if (showCertificateGenerationDialog) {
        CertificateGenerationDialog(
            onDismiss = { showCertificateGenerationDialog = false },
            defaultCommonName = username.trim(),
            onCertificateGenerated = { path, certPassword ->
                certificatePath = path
                certificatePassword = certPassword
                showCertificateGenerationDialog = false
                
                // Auto-save the server with the new certificate
                if (serverAddress.isNotBlank() && username.isNotBlank()) {
                    val serverInfo = ServerInfo(
                        address = serverAddress.trim(),
                        port = port.toIntOrNull() ?: 64738,
                        username = username.trim(),
                        password = password,  // Server password (not certificate password!)
                        name = serverName.takeIf { it.isNotBlank() } ?: serverAddress,
                        autoConnect = autoConnect,
                        autoJoinChannel = autoJoinChannel.trim(),
                        clientCertificatePath = certificatePath,
                        clientCertificatePassword = certificatePassword,
                        registerWithServer = registerWithServer,
                        skipCertificateVerification = skipCertificateVerification
                    )
                    viewModel?.saveServer(serverInfo)
                    
                    // Show a toast to inform user
                    Toast.makeText(
                        context,
                        "✓ Certificate saved! Please reconnect to use it.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        )
    }
}
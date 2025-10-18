package com.hammumble.ui

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.IBinder
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.hammumble.service.MumbleService
import com.hammumble.ui.screens.*
import com.hammumble.ui.theme.HamMumbleTheme
import com.hammumble.ui.viewmodel.MumbleViewModel

class MainActivity : ComponentActivity() {
    
    private var mumbleService by mutableStateOf<MumbleService?>(null)
    private var isBound = false
    
    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            val binder = service as MumbleService.MumbleBinder
            mumbleService = binder.getService()
            isBound = true
        }
        
        override fun onServiceDisconnected(arg0: ComponentName) {
            mumbleService = null
            isBound = false
        }
    }
    
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val recordAudioGranted = permissions[Manifest.permission.RECORD_AUDIO] ?: false
        val modifyAudioGranted = permissions[Manifest.permission.MODIFY_AUDIO_SETTINGS] ?: false
        
        if (!recordAudioGranted || !modifyAudioGranted) {
            // Show permission denied dialog
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        checkAndRequestPermissions()
        
        setContent {
            HamMumbleTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MumbleApp(
                        service = mumbleService,
                        onStartService = { serverInfo ->
                            startMumbleService(serverInfo)
                        },
                        onQuit = {
                            // First, shutdown the service completely
                            mumbleService?.shutdown()
                            
                            // Unbind from service if connected
                            if (isBound) {
                                try {
                                    unbindService(serviceConnection)
                                } catch (e: Exception) {
                                    // Service may already be unbound
                                }
                                isBound = false
                            }
                            
                            // Stop the service (in case shutdown didn't work)
                            stopService(Intent(this, MumbleService::class.java))
                            
                            // Give service time to clean up
                            Thread.sleep(100)
                            
                            // Finish all activities
                            finishAffinity()
                            
                            // Force kill the process to ensure complete exit
                            android.os.Process.killProcess(android.os.Process.myPid())
                        }
                    )
                }
            }
        }
    }
    
    override fun onStart() {
        super.onStart()
        // Bind to MumbleService if it's running
        val intent = Intent(this, MumbleService::class.java)
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }
    
    override fun onStop() {
        super.onStop()
        if (isBound) {
            unbindService(serviceConnection)
            isBound = false
        }
    }
    
    private fun checkAndRequestPermissions() {
        val permissions = arrayOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.MODIFY_AUDIO_SETTINGS
        )
        
        val permissionsToRequest = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        
        if (permissionsToRequest.isNotEmpty()) {
            requestPermissionLauncher.launch(permissionsToRequest.toTypedArray())
        }
    }
    
    private fun startMumbleService(serverInfo: com.hammumble.data.ServerInfo) {
        val intent = Intent(this, MumbleService::class.java)
        intent.putExtra("server_address", serverInfo.address)
        intent.putExtra("server_port", serverInfo.port)
        intent.putExtra("username", serverInfo.username)
        intent.putExtra("password", serverInfo.password)
        intent.putExtra("server_name", serverInfo.name)
        intent.putExtra("auto_join_channel", serverInfo.autoJoinChannel)
        startForegroundService(intent)
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MumbleApp(
    service: MumbleService?,
    onStartService: (com.hammumble.data.ServerInfo) -> Unit,
    onQuit: () -> Unit
) {
    val navController = rememberNavController()
    val viewModel: MumbleViewModel = viewModel()
    val context = LocalContext.current
    
    // Track if auto-connect has been attempted (to only do it once on app start)
    var autoConnectAttempted by remember { mutableStateOf(false) }
    
    // Initialize ViewModel with context
    LaunchedEffect(Unit) {
        viewModel.initialize(context)
    }
    
    // Update ViewModel with service
    LaunchedEffect(service) {
        viewModel.setService(service)
    }
    
    // Auto-connect to server ONLY on first app start (not after manual disconnect)
    val connectionState by viewModel.connectionState.collectAsState()
    LaunchedEffect(service, connectionState) {
        if (!autoConnectAttempted && service != null && connectionState == com.hammumble.data.ConnectionState.DISCONNECTED) {
            val autoConnectServer = viewModel.getAutoConnectServer()
            if (autoConnectServer != null) {
                autoConnectAttempted = true
                onStartService(autoConnectServer)
            } else {
                // Mark as attempted even if no auto-connect server found
                autoConnectAttempted = true
            }
        }
    }
    
    NavHost(
        navController = navController,
        startDestination = "main"
    ) {
        composable("main") {
            MainScreen(
                viewModel = viewModel,
                onNavigateToConnection = {
                    viewModel.setServerToEdit(null) // Clear any existing edit
                    navController.navigate("connection")
                },
                onNavigateToSettings = {
                    navController.navigate("settings")
                },
                onStartService = onStartService,
                onEditServer = { serverInfo ->
                    viewModel.setServerToEdit(serverInfo)
                    navController.navigate("connection")
                },
                onQuit = onQuit
            )
        }
        
        composable("connection") {
            val serverToEdit by viewModel.serverToEdit.collectAsState()
            
            ServerConnectionScreen(
                onConnect = { serverInfo ->
                    onStartService(serverInfo)
                    navController.popBackStack()
                },
                onBack = {
                    viewModel.setServerToEdit(null) // Clear when going back
                    navController.popBackStack()
                },
                viewModel = viewModel,
                existingServer = serverToEdit
            )
        }
        
        composable("settings") {
            SettingsScreen(
                viewModel = viewModel,
                onBack = {
                    navController.popBackStack()
                }
            )
        }
    }
}
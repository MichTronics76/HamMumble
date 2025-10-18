package com.hammumble.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.PhoneInTalk
import androidx.compose.material.icons.rounded.Speaker
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MicOff
import androidx.compose.material.icons.rounded.Headset
import androidx.compose.material.icons.rounded.HeadsetOff
import androidx.compose.material.icons.rounded.Chat
import androidx.compose.material.icons.rounded.People
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hammumble.R
import com.hammumble.data.*
import com.hammumble.ui.components.*
import com.hammumble.ui.viewmodel.MumbleViewModel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable  
fun MainScreen(
    viewModel: MumbleViewModel,
    onNavigateToConnection: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onStartService: (ServerInfo) -> Unit,
    onEditServer: (ServerInfo) -> Unit,
    onQuit: () -> Unit
) {
    val connectionState by viewModel.connectionState.collectAsState()
    val currentServer by viewModel.currentServer.collectAsState()
    val currentUser by viewModel.currentUser.collectAsState()
    val currentChannel by viewModel.currentChannel.collectAsState()
    val channels by viewModel.channels.collectAsState()
    val users by viewModel.users.collectAsState()
    val chatMessages by viewModel.chatMessages.collectAsState()
    val newMessage by viewModel.newMessage.collectAsState()
    val isPushToTalkPressed by viewModel.isPushToTalkPressed.collectAsState()
    val audioSettings by viewModel.audioSettings.collectAsState()
    val appSettings by viewModel.appSettings.collectAsState()
    val voiceHoldTimerMs by viewModel.voiceHoldTimerMs.collectAsState()
    val savedServers by viewModel.savedServers.collectAsState()
    val serverLatencies by viewModel.serverLatencies.collectAsState()
    
    // Ping servers when the list changes or when disconnected
    LaunchedEffect(savedServers, connectionState) {
        if (connectionState == ConnectionState.DISCONNECTED || connectionState == ConnectionState.ERROR) {
            viewModel.pingAllServers()
        }
    }
    
    // Mumla-style tabs: CHANNEL and CHAT
    var selectedTab by remember { mutableStateOf(0) }
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    
    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            MumlaDrawerContent(
                connectionState = connectionState,
                currentServer = currentServer,
                onNavigateToConnection = onNavigateToConnection,
                onNavigateToSettings = onNavigateToSettings,
                onDisconnect = { 
                    viewModel.disconnect()
                    scope.launch { drawerState.close() }
                },
                onRegisterUser = { 
                    viewModel.registerWithServer()
                },
                onCloseDrawer = { scope.launch { drawerState.close() } },
                onQuit = onQuit
            )
        }
    ) {
        Scaffold(
            topBar = {
                MumlaTopBar(
                    connectionState = connectionState,
                    currentChannel = currentChannel,
                    currentServer = currentServer,
                    onMenuClick = { scope.launch { drawerState.open() } }
                )
            },
            floatingActionButton = {
                if (connectionState == ConnectionState.DISCONNECTED) {
                    FloatingActionButton(onClick = onNavigateToConnection) {
                        Icon(Icons.Default.Add, contentDescription = "Connect")
                    }
                }
            }
        ) { paddingValues ->
            if (connectionState == ConnectionState.CONNECTED) {
                MumlaConnectedView(
                    selectedTab = selectedTab,
                    onTabSelected = { selectedTab = it },
                    channels = channels,
                    users = users,
                    currentChannel = currentChannel,
                    currentUser = currentUser,
                    chatMessages = chatMessages,
                    newMessage = newMessage,
                    isPushToTalkPressed = isPushToTalkPressed,
                    audioSettings = audioSettings,
                    appSettings = appSettings,
                    voiceHoldTimerMs = voiceHoldTimerMs,
                    onChannelClick = { viewModel.joinChannel(it) },
                    onMessageChange = { viewModel.updateNewMessage(it) },
                    onSendMessage = { viewModel.sendMessage(newMessage) },
                    onToggleMute = { viewModel.toggleMute() },
                    onToggleDeafen = { viewModel.toggleDeafen() },
                    onToggleSpeakerphone = { viewModel.toggleSpeakerphone() },
                    onPushToTalkStart = { viewModel.setPushToTalkPressed(true) },
                    onPushToTalkEnd = { viewModel.setPushToTalkPressed(false) },
                    modifier = Modifier.padding(paddingValues)
                )
            } else {
                // Show connecting/disconnecting states or server list
                if (connectionState == ConnectionState.CONNECTING || 
                    connectionState == ConnectionState.DISCONNECTING) {
                    DisconnectedScreen(
                        connectionState = connectionState,
                        onConnect = onNavigateToConnection,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(paddingValues)
                            .padding(16.dp)
                    )
                } else {
                    ServerListScreen(
                        savedServers = savedServers,
                        serverLatencies = serverLatencies,
                        onServerClick = { serverInfo ->
                            // Start service with the selected server
                            onStartService(serverInfo)
                        },
                        onAddServerClick = onNavigateToConnection,
                        onEditServer = { serverInfo ->
                            onEditServer(serverInfo)
                        },
                        onDeleteServer = { serverInfo ->
                            viewModel.deleteServer(serverInfo)
                        },
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(paddingValues)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MumlaDrawerContent(
    connectionState: ConnectionState,
    currentServer: ServerInfo?,
    onNavigateToConnection: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onDisconnect: () -> Unit,
    onRegisterUser: () -> Unit,
    onCloseDrawer: () -> Unit,
    onQuit: () -> Unit
) {
    ModalDrawerSheet {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = if (connectionState == ConnectionState.CONNECTED) {
                    "Connected"
                } else {
                    "HamMumble"
                },
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            
            if (connectionState == ConnectionState.CONNECTED && currentServer != null) {
                Text(
                    text = currentServer.address,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            Divider()
            Spacer(modifier = Modifier.height(8.dp))
            
            NavigationDrawerItem(
                label = { Text("Server") },
                selected = false,
                onClick = onCloseDrawer,
                icon = { Icon(Icons.Default.Place, contentDescription = null) }
            )
            
            NavigationDrawerItem(
                label = { Text("Information") },
                selected = false,
                onClick = onCloseDrawer,
                icon = { Icon(Icons.Default.Info, contentDescription = null) }
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "SERVERS",
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            
            NavigationDrawerItem(
                label = { Text("Favourites") },
                selected = false,
                onClick = {
                    onNavigateToConnection()
                    onCloseDrawer()
                },
                icon = { Icon(Icons.Default.Star, contentDescription = null) }
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            NavigationDrawerItem(
                label = { Text("Settings") },
                selected = false,
                onClick = {
                    onNavigateToSettings()
                    onCloseDrawer()
                },
                icon = { Icon(Icons.Default.Settings, contentDescription = null) }
            )
            
            if (connectionState == ConnectionState.CONNECTED) {
                Spacer(modifier = Modifier.weight(1f))
                
                // Register user button
                OutlinedButton(
                    onClick = { 
                        onRegisterUser()
                        onCloseDrawer()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Person, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Register Username")
                }
                Spacer(modifier = Modifier.height(8.dp))
                
                Button(
                    onClick = onDisconnect,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Disconnect")
                }
                Spacer(modifier = Modifier.height(8.dp))
            } else {
                Spacer(modifier = Modifier.weight(1f))
            }
            
            // Quit button at bottom
            NavigationDrawerItem(
                label = { Text("Quit") },
                selected = false,
                onClick = {
                    onQuit()
                },
                icon = { Icon(Icons.Default.Close, contentDescription = null) },
                colors = NavigationDrawerItemDefaults.colors(
                    unselectedContainerColor = MaterialTheme.colorScheme.errorContainer,
                    unselectedIconColor = MaterialTheme.colorScheme.error,
                    unselectedTextColor = MaterialTheme.colorScheme.error
                )
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MumlaTopBar(
    connectionState: ConnectionState,
    currentChannel: Channel?,
    currentServer: ServerInfo?,
    onMenuClick: () -> Unit
) {
    TopAppBar(
        title = {
            Text(
                text = when (connectionState) {
                    ConnectionState.CONNECTED -> currentChannel?.name ?: currentServer?.address ?: "HamMumble"
                    ConnectionState.CONNECTING -> "Connecting..."
                    ConnectionState.DISCONNECTING -> "Disconnecting..."
                    ConnectionState.ERROR -> "Connection Failed"
                    else -> "HamMumble"
                }
            )
        },
        navigationIcon = {
            IconButton(onClick = onMenuClick) {
                Icon(Icons.Default.Menu, contentDescription = "Menu")
            }
        },
        actions = {
            if (connectionState == ConnectionState.CONNECTED) {
                IconButton(onClick = { /* Search functionality */ }) {
                    Icon(Icons.Default.Search, contentDescription = "Search")
                }
            }
        }
    )
}

@Composable
fun MumlaConnectedView(
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    channels: List<Channel>,
    users: List<User>,
    currentChannel: Channel?,
    currentUser: User?,
    chatMessages: List<ChatMessage>,
    newMessage: String,
    isPushToTalkPressed: Boolean,
    audioSettings: AudioSettings,
    appSettings: AppSettings,
    voiceHoldTimerMs: Int,
    onChannelClick: (Int) -> Unit,
    onMessageChange: (String) -> Unit,
    onSendMessage: () -> Unit,
    onToggleMute: () -> Unit,
    onToggleDeafen: () -> Unit,
    onToggleSpeakerphone: () -> Unit,
    onPushToTalkStart: () -> Unit,
    onPushToTalkEnd: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxSize()) {
        // Mumla-style tabs
        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary
        ) {
            Tab(
                selected = selectedTab == 0,
                onClick = { onTabSelected(0) },
                text = {
                    Text(
                        "CHANNEL",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { onTabSelected(1) },
                text = {
                    Text(
                        "CHAT",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
            )
        }
        
        // Tab content
        Box(modifier = Modifier.weight(1f)) {
            when (selectedTab) {
                0 -> MumlaChannelView(
                    channels = channels,
                    users = users,
                    currentChannel = currentChannel,
                    onChannelClick = onChannelClick
                )
                1 -> MumlaChatPanel(
                    messages = chatMessages,
                    newMessage = newMessage,
                    onMessageChange = onMessageChange,
                    onSendMessage = onSendMessage
                )
            }
        }
        
        // Mumla-style Push-to-Talk bottom bar
        MumlaPushToTalkBar(
            isMuted = currentUser?.isSelfMuted ?: false,
            isDeafened = currentUser?.isSelfDeafened ?: false,
            isPushToTalkPressed = isPushToTalkPressed,
            audioSettings = audioSettings,
            appSettings = appSettings,
            voiceHoldTimerMs = voiceHoldTimerMs,
            onToggleMute = onToggleMute,
            onToggleDeafen = onToggleDeafen,
            onToggleSpeakerphone = onToggleSpeakerphone,
            onPushToTalkStart = onPushToTalkStart,
            onPushToTalkEnd = onPushToTalkEnd
        )
    }
}

@Composable
fun MumlaChannelView(
    channels: List<Channel>,
    users: List<User>,
    currentChannel: Channel?,
    onChannelClick: (Int) -> Unit
) {
    LazyColumn(
        contentPadding = PaddingValues(vertical = 4.dp)
    ) {
        items(channels) { channel ->
            MumlaChannelItem(
                channel = channel,
                users = users.filter { it.channelId == channel.id },
                isCurrentChannel = channel.id == currentChannel?.id,
                onChannelClick = onChannelClick,
                depth = 0
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MumlaChannelItem(
    channel: Channel,
    users: List<User>,
    isCurrentChannel: Boolean,
    onChannelClick: (Int) -> Unit,
    depth: Int
) {
    val indentation = (depth * 25).dp
    
    Column {
        // Channel row
        Surface(
            onClick = { onChannelClick(channel.id) },
            modifier = Modifier.fillMaxWidth(),
            color = if (isCurrentChannel) 
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            else Color.Transparent
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = indentation, top = 8.dp, bottom = 8.dp, end = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Rounded.Folder,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = if (isCurrentChannel) 
                        MaterialTheme.colorScheme.primary 
                    else MaterialTheme.colorScheme.onSurface
                )
                
                Spacer(modifier = Modifier.width(12.dp))
                
                Text(
                    text = channel.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (isCurrentChannel) FontWeight.Bold else FontWeight.Normal,
                    modifier = Modifier.weight(1f)
                )
                
                if (users.isNotEmpty()) {
                    Text(
                        text = users.size.toString(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        
        // Users in this channel
        users.forEach { user ->
            MumlaUserItem(
                user = user,
                depth = depth + 1
            )
        }
    }
}

@Composable
fun MumlaUserItem(
    user: User,
    depth: Int
) {
    val indentation = (depth * 25).dp
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = indentation, top = 4.dp, bottom = 4.dp, end = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Enhanced talk state indicator following Mumla's priority:
        // deafened > muted > speaking > idle
        Box(
            modifier = Modifier
                .size(28.dp)
                .padding(2.dp),
            contentAlignment = Alignment.Center
        ) {
            val (stateColor, isActive) = when {
                user.isSelfDeafened || user.isDeafened -> 
                    MaterialTheme.colorScheme.error to true
                user.isSelfMuted || user.isMuted -> 
                    MaterialTheme.colorScheme.error.copy(alpha = 0.8f) to true
                user.isSpeaking -> 
                    MaterialTheme.colorScheme.primary to true
                else -> 
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f) to false
            }
            
            // Outer ring for active states
            if (isActive) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .background(
                            color = stateColor.copy(alpha = 0.3f),
                            shape = CircleShape
                        )
                )
            }
            
            // Inner circle
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .background(
                        color = stateColor,
                        shape = CircleShape
                    )
            )
        }
        
        Spacer(modifier = Modifier.width(12.dp))
        
        Text(
            text = user.name,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (user.isSpeaking) FontWeight.Bold else FontWeight.Normal,
            modifier = Modifier.weight(1f)
        )
        
        // Status icons with more detail
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            // Registered user indicator (show first if registered)
            if (user.userId >= 0) {
                Icon(
                    imageVector = Icons.Filled.Star,
                    contentDescription = "Registered User",
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                )
            }
            
            if (user.isSelfDeafened) {
                Icon(
                    imageVector = Icons.Rounded.HeadsetOff,
                    contentDescription = "Self Deafened",
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.error
                )
            } else if (user.isDeafened) {
                Icon(
                    imageVector = Icons.Rounded.HeadsetOff,
                    contentDescription = "Server Deafened",
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
                )
            }
            
            if (user.isSelfMuted) {
                Icon(
                    imageVector = Icons.Rounded.MicOff,
                    contentDescription = "Self Muted",
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.error
                )
            } else if (user.isMuted) {
                Icon(
                    imageVector = Icons.Rounded.MicOff,
                    contentDescription = "Server Muted",
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
                )
            }
        }
    }
}

@Composable
fun MumlaPushToTalkBar(
    isMuted: Boolean,
    isDeafened: Boolean,
    isPushToTalkPressed: Boolean,
    audioSettings: AudioSettings,
    appSettings: AppSettings,
    voiceHoldTimerMs: Int,
    onToggleMute: () -> Unit,
    onToggleDeafen: () -> Unit,
    onToggleSpeakerphone: () -> Unit,
    onPushToTalkStart: () -> Unit,
    onPushToTalkEnd: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 3.dp
    ) {
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            // Voice hold timer info - show when timer is active OR in VAD mode
            if (voiceHoldTimerMs > 0 || audioSettings.transmissionMode == TransmissionMode.VOICE_ACTIVITY) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (voiceHoldTimerMs > 0) {
                            "Voice Hold Timer: ${voiceHoldTimerMs}ms"
                        } else {
                            "Voice Activity Detection • Hold: ${audioSettings.voiceHoldTime}ms"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (voiceHoldTimerMs > 0) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
                Divider()
            }
            
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp, horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
            // Mute button (microphone icon)
            IconButton(
                onClick = onToggleMute,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = if (isMuted) Icons.Rounded.MicOff else Icons.Rounded.Mic,
                    contentDescription = if (isMuted) "Unmute" else "Mute",
                    tint = if (isMuted) 
                        MaterialTheme.colorScheme.error 
                    else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp)
                )
            }
            
            // Speaker button (audio output toggle)
            IconButton(
                onClick = onToggleSpeakerphone,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = if (appSettings.useSpeakerphone) 
                        Icons.Rounded.Speaker  // Luidspreker icoon
                    else 
                        Icons.Rounded.PhoneInTalk,  // Oortje/telefoon icoon
                    contentDescription = if (appSettings.useSpeakerphone) "Schakel naar oortje" else "Schakel naar luidspreker",
                    tint = if (appSettings.useSpeakerphone) 
                        MaterialTheme.colorScheme.primary 
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(32.dp)
                )
            }
            
            // Push to Talk button (large, center) - Hold mode only
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .background(
                        color = if (isPushToTalkPressed) 
                            MaterialTheme.colorScheme.error 
                        else MaterialTheme.colorScheme.primary,
                        shape = CircleShape
                    )
                    .pointerInput(Unit) {
                        // Hold mode: press and hold to talk
                        detectTapGestures(
                            onPress = {
                                onPushToTalkStart()
                                tryAwaitRelease()
                                onPushToTalkEnd()
                            }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Mic,
                    contentDescription = "Push to Talk",
                    tint = Color.White,
                    modifier = Modifier.size(40.dp)
                )
            }
            
            // Deafen button (headphone icon)
            IconButton(
                onClick = onToggleDeafen,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = if (isDeafened) Icons.Rounded.HeadsetOff else Icons.Rounded.Headset,
                    contentDescription = "Deafen",
                    tint = if (isDeafened) 
                        MaterialTheme.colorScheme.error 
                    else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp)
                )
            }
            }
        }
    }
}

@Composable
fun DisconnectedScreen(
    connectionState: ConnectionState,
    onConnect: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = when (connectionState) {
                ConnectionState.ERROR -> Icons.Default.Warning
                ConnectionState.CONNECTING -> Icons.Default.Refresh
                else -> Icons.Default.Close
            },
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = when (connectionState) {
                ConnectionState.ERROR -> "Connection Failed"
                ConnectionState.CONNECTING -> "Connecting..."
                ConnectionState.DISCONNECTING -> "Disconnecting..."
                else -> "Not Connected"
            },
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        if (connectionState == ConnectionState.DISCONNECTED || connectionState == ConnectionState.ERROR) {
            Spacer(modifier = Modifier.height(24.dp))
            
            Button(onClick = onConnect) {
                Text("Connect to Server")
            }
        }
    }
}

@Composable
fun ServerListScreen(
    savedServers: List<ServerInfo>,
    serverLatencies: Map<String, Int?>,
    onServerClick: (ServerInfo) -> Unit,
    onAddServerClick: () -> Unit,
    onEditServer: (ServerInfo) -> Unit,
    onDeleteServer: (ServerInfo) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxSize()
    ) {
        if (savedServers.isEmpty()) {
            // Empty state
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    text = "No Servers Added",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = "Add a server to get started",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                
                Button(onClick = onAddServerClick) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Add Server")
                }
            }
        } else {
            // Server list
            LazyColumn(
                contentPadding = PaddingValues(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(savedServers) { server ->
                    val serverKey = "${server.address}:${server.port}"
                    val latency = serverLatencies[serverKey]
                    
                    ServerListItem(
                        server = server,
                        latency = latency,
                        onClick = { onServerClick(server) },
                        onEdit = { onEditServer(server) },
                        onDelete = { onDeleteServer(server) }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerListItem(
    server: ServerInfo,
    latency: Int?,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Place,
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = server.name.ifEmpty { server.address },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    
                    // Auto-connect indicator
                    if (server.autoConnect) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = "Auto-connect enabled",
                            tint = Color(0xFFFFA726),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                
                Text(
                    text = "${server.address}:${server.port}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                if (server.username.isNotEmpty()) {
                    Text(
                        text = "User: ${server.username}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            // Latency indicator
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.Center
            ) {
                when {
                    latency == null -> {
                        // Checking/unknown state
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp
                        )
                    }
                    latency < 0 -> {
                        // Unreachable
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Unreachable",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(24.dp)
                        )
                        Text(
                            text = "Offline",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    else -> {
                        // Show latency with color coding
                        val (latencyColor, latencyText) = when {
                            latency < 50 -> MaterialTheme.colorScheme.primary to "Excellent"
                            latency < 100 -> Color(0xFF4CAF50) to "Good"
                            latency < 200 -> Color(0xFFFFA726) to "Fair"
                            else -> MaterialTheme.colorScheme.error to "Poor"
                        }
                        
                        Text(
                            text = "${latency}ms",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = latencyColor
                        )
                        Text(
                            text = latencyText,
                            style = MaterialTheme.typography.bodySmall,
                            color = latencyColor
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.width(8.dp))
            
            // Menu button for edit/delete
            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "More options"
                    )
                }
                
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Edit") },
                        onClick = {
                            showMenu = false
                            onEdit()
                        },
                        leadingIcon = {
                            Icon(Icons.Default.Edit, contentDescription = null)
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Delete") },
                        onClick = {
                            showMenu = false
                            onDelete()
                        },
                        leadingIcon = {
                            Icon(Icons.Default.Delete, contentDescription = null)
                        }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MumlaChatPanel(
    messages: List<ChatMessage>,
    newMessage: String,
    onMessageChange: (String) -> Unit,
    onSendMessage: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        // Messages list
        LazyColumn(
            modifier = Modifier.weight(1f),
            reverseLayout = true
        ) {
            items(messages.reversed()) { message ->
                MumlaChatMessageItem(message = message)
            }
        }
        
        // Input field
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = newMessage,
                onValueChange = onMessageChange,
                modifier = Modifier.weight(1f),
                placeholder = { Text("Type a message...") },
                maxLines = 3
            )
            Spacer(modifier = Modifier.width(8.dp))
            IconButton(
                onClick = onSendMessage,
                enabled = newMessage.isNotBlank()
            ) {
                Icon(Icons.Default.Send, contentDescription = "Send")
            }
        }
    }
}

@Composable
fun MumlaChatMessageItem(message: ChatMessage) {
    val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val formattedTime = remember(message.timestamp) {
        timeFormat.format(Date(message.timestamp))
    }
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Header row with sender and time
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Text(
                    text = message.sender,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )
                
                // Time display
                Text(
                    text = formattedTime,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
            
            Spacer(modifier = Modifier.height(4.dp))
            
            // Message text
            Text(
                text = message.message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

package com.hammumble.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hammumble.audio.AudioFeedbackManager
import com.hammumble.data.*
import com.hammumble.service.MumbleService
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.InetSocketAddress
import java.net.Socket

class MumbleViewModel : ViewModel() {
    
    private var service: MumbleService? = null
    private var audioFeedbackManager: AudioFeedbackManager? = null
    private var pttSoundEnabled: Boolean = true  // From AppSettings
    private var context: Context? = null
    
    // UI State
    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()
    
    private val _currentServer = MutableStateFlow<ServerInfo?>(null)
    val currentServer: StateFlow<ServerInfo?> = _currentServer.asStateFlow()
    
    private val _savedServers = MutableStateFlow<List<ServerInfo>>(emptyList())
    val savedServers: StateFlow<List<ServerInfo>> = _savedServers.asStateFlow()
    
    // Server being edited (null if not editing)
    private val _serverToEdit = MutableStateFlow<ServerInfo?>(null)
    val serverToEdit: StateFlow<ServerInfo?> = _serverToEdit.asStateFlow()
    
    // Map to store latency for each server (address:port -> latency in ms, null means checking/unknown)
    private val _serverLatencies = MutableStateFlow<Map<String, Int?>>(emptyMap())
    val serverLatencies: StateFlow<Map<String, Int?>> = _serverLatencies.asStateFlow()
    
    private val _currentUser = MutableStateFlow<User?>(null)
    val currentUser: StateFlow<User?> = _currentUser.asStateFlow()
    
    private val _channels = MutableStateFlow<List<Channel>>(emptyList())
    val channels: StateFlow<List<Channel>> = _channels.asStateFlow()
    
    private val _users = MutableStateFlow<List<User>>(emptyList())
    val users: StateFlow<List<User>> = _users.asStateFlow()
    
    private val _chatMessages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val chatMessages: StateFlow<List<ChatMessage>> = _chatMessages.asStateFlow()
    
    private val _audioSettings = MutableStateFlow(AudioSettings())
    val audioSettings: StateFlow<AudioSettings> = _audioSettings.asStateFlow()
    
    private val _appSettings = MutableStateFlow(AppSettings())
    val appSettings: StateFlow<AppSettings> = _appSettings.asStateFlow()
    
    // UI-specific state
    private val _isPushToTalkPressed = MutableStateFlow(false)
    val isPushToTalkPressed: StateFlow<Boolean> = _isPushToTalkPressed.asStateFlow()
    
    private val _voiceHoldTimerMs = MutableStateFlow(0)
    val voiceHoldTimerMs: StateFlow<Int> = _voiceHoldTimerMs.asStateFlow()
    
    private val _inputAudioLevel = MutableStateFlow(0f)
    val inputAudioLevel: StateFlow<Float> = _inputAudioLevel.asStateFlow()
    
    private val _outputAudioLevel = MutableStateFlow(0f)
    val outputAudioLevel: StateFlow<Float> = _outputAudioLevel.asStateFlow()
    
    private val _newMessage = MutableStateFlow("")
    val newMessage: StateFlow<String> = _newMessage.asStateFlow()
    
    fun initialize(context: Context, settings: AppSettings = AppSettings()) {
        this.context = context
        audioFeedbackManager = AudioFeedbackManager(context)
        pttSoundEnabled = settings.pttSoundEnabled
        loadSavedServers()
        loadAudioSettings()
        loadAppSettings()
    }
    
    /**
     * Check if there's a server with autoConnect enabled and return it
     */
    fun getAutoConnectServer(): ServerInfo? {
        return _savedServers.value.firstOrNull { it.autoConnect }
    }
    
    fun setService(service: MumbleService?) {
        this.service = service
        
        // Observe service state flows
        service?.let { mumbleService ->
            // Apply loaded audio settings to the service first
            mumbleService.updateAudioSettings(_audioSettings.value)
            
            viewModelScope.launch {
                mumbleService.connectionState.collect {
                    _connectionState.value = it
                }
            }
            
            viewModelScope.launch {
                mumbleService.currentServer.collect {
                    _currentServer.value = it
                }
            }
            
            viewModelScope.launch {
                mumbleService.currentUser.collect {
                    _currentUser.value = it
                }
            }
            
            viewModelScope.launch {
                mumbleService.channels.collect {
                    _channels.value = it
                }
            }
            
            viewModelScope.launch {
                mumbleService.users.collect {
                    _users.value = it
                }
            }
            
            viewModelScope.launch {
                mumbleService.chatMessages.collect {
                    _chatMessages.value = it
                }
            }
            
            viewModelScope.launch {
                mumbleService.audioSettings.collect {
                    _audioSettings.value = it
                }
            }
            
            viewModelScope.launch {
                mumbleService.voiceHoldTimerMs.collect {
                    _voiceHoldTimerMs.value = it
                }
            }
            
            viewModelScope.launch {
                mumbleService.inputAudioLevel.collect {
                    _inputAudioLevel.value = it
                }
            }
            
            viewModelScope.launch {
                mumbleService.outputAudioLevel.collect {
                    _outputAudioLevel.value = it
                }
            }
        }
    }
    
    fun connect(serverInfo: ServerInfo) {
        service?.connect(serverInfo)
    }
    
    fun disconnect() {
        service?.disconnect()
    }
    
    fun registerWithServer() {
        service?.registerUser()
    }
    
    fun sendMessage(message: String) {
        if (message.isNotBlank()) {
            // Send to current channel
            service?.sendChannelMessage(message)
            _newMessage.value = ""
        }
    }
    
    fun joinChannel(@Suppress("UNUSED_PARAMETER") channelId: Int) {
        service?.joinChannel(channelId)
    }
    
    fun toggleMute() {
        _currentUser.value?.let { user ->
            service?.setMuted(!user.isSelfMuted)
            // Play feedback sound
            audioFeedbackManager?.playNotificationSound()
        }
    }
    
    fun toggleDeafen() {
        _currentUser.value?.let { user ->
            service?.setDeafened(!user.isSelfDeafened)
            // Play feedback sound
            audioFeedbackManager?.playNotificationSound()
        }
    }
    
    fun setPushToTalkPressed(pressed: Boolean) {
        _isPushToTalkPressed.value = pressed
        
        // Play audio feedback if enabled
        if (pttSoundEnabled) {
            if (pressed) {
                audioFeedbackManager?.playPttPressSound()
            } else {
                audioFeedbackManager?.playPttReleaseSound()
            }
        }
        
        if (_audioSettings.value.transmissionMode == TransmissionMode.PUSH_TO_TALK) {
            if (pressed) {
                service?.startRecording()
            } else {
                service?.stopRecording()
            }
        }
    }
    
    fun updateAudioSettings(settings: AudioSettings) {
        _audioSettings.value = settings
        service?.updateAudioSettings(settings)
        saveAudioSettings(settings)
    }
    
    fun toggleSpeakerphone() {
        val currentSettings = _appSettings.value
        val newSettings = currentSettings.copy(useSpeakerphone = !currentSettings.useSpeakerphone)
        _appSettings.value = newSettings
        service?.setSpeakerphoneMode(newSettings.useSpeakerphone)
        saveAppSettings(newSettings)
    }
    
    fun updateNewMessage(message: String) {
        _newMessage.value = message
    }
    
    fun clearChat() {
        _chatMessages.value = emptyList()
    }
    
    // Computed properties
    val isConnected: StateFlow<Boolean> = connectionState.map { 
        it == ConnectionState.CONNECTED 
    }.stateIn(viewModelScope, SharingStarted.Lazily, false)
    
    val canTransmit: StateFlow<Boolean> = combine(
        currentUser,
        audioSettings,
        isPushToTalkPressed
    ) { user, settings, pttPressed ->
        user != null && !user.isSelfMuted && !user.isSelfDeafened && 
        (settings.transmissionMode != TransmissionMode.PUSH_TO_TALK || pttPressed)
    }.stateIn(viewModelScope, SharingStarted.Lazily, false)
    
    val currentChannel: StateFlow<Channel?> = combine(
        channels,
        currentUser
    ) { channelList, user ->
        user?.let { u ->
            channelList.find { it.id == u.channelId }
        }
    }.stateIn(viewModelScope, SharingStarted.Lazily, null)
    
    val usersInCurrentChannel: StateFlow<List<User>> = combine(
        users,
        currentUser
    ) { userList, user ->
        user?.let { u ->
            userList.filter { it.channelId == u.channelId }
        } ?: emptyList()
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    
    // === Serial PTT Functions ===
    
    fun testSerialPtt() {
        service?.testSerialPtt(500)
    }
    
    fun getAvailableSerialDevices() = service?.getAvailableSerialDevices() ?: emptyList()
    
    fun connectSerialDevice(device: android.hardware.usb.UsbDevice, settings: com.hammumble.data.SerialPttSettings): Boolean {
        return service?.connectSerialPtt(device, settings) ?: false
    }
    
    fun disconnectSerialDevice() {
        service?.disconnectSerialPtt()
    }
    
    // === Server Management Functions ===
    
    private fun loadSavedServers() {
        context?.let { ctx ->
            val prefs = ctx.getSharedPreferences("hammumble_servers", Context.MODE_PRIVATE)
            val serversJson = prefs.getString("servers", "[]") ?: "[]"
            
            try {
                val jsonArray = JSONArray(serversJson)
                val servers = mutableListOf<ServerInfo>()
                
                for (i in 0 until jsonArray.length()) {
                    val jsonObj = jsonArray.getJSONObject(i)
                    val server = ServerInfo(
                        name = jsonObj.optString("name", ""),
                        address = jsonObj.getString("address"),
                        port = jsonObj.optInt("port", 64738),
                        username = jsonObj.getString("username"),
                        password = jsonObj.optString("password", ""),
                        autoConnect = jsonObj.optBoolean("autoConnect", false),
                        autoJoinChannel = jsonObj.optString("autoJoinChannel", ""),
                        clientCertificatePath = if (jsonObj.has("clientCertificatePath")) jsonObj.getString("clientCertificatePath") else null,
                        clientCertificatePassword = jsonObj.optString("clientCertificatePassword", ""),
                        registerWithServer = jsonObj.optBoolean("registerWithServer", false),
                        skipCertificateVerification = jsonObj.optBoolean("skipCertificateVerification", false)
                    )
                    servers.add(server)
                }
                
                _savedServers.value = servers
            } catch (e: Exception) {
                e.printStackTrace()
                _savedServers.value = emptyList()
            }
        }
    }
    
    fun saveServer(serverInfo: ServerInfo) {
        val currentServers = _savedServers.value.toMutableList()
        
        // If this server has autoConnect enabled, disable it on all other servers
        if (serverInfo.autoConnect) {
            for (i in currentServers.indices) {
                if (currentServers[i].autoConnect && 
                    !(currentServers[i].address == serverInfo.address && currentServers[i].port == serverInfo.port)) {
                    currentServers[i] = currentServers[i].copy(autoConnect = false)
                }
            }
        }
        
        // Check if server already exists (by address and port)
        val existingIndex = currentServers.indexOfFirst { 
            it.address == serverInfo.address && it.port == serverInfo.port 
        }
        
        if (existingIndex >= 0) {
            // Update existing server
            currentServers[existingIndex] = serverInfo
        } else {
            // Add new server
            currentServers.add(serverInfo)
        }
        
        _savedServers.value = currentServers
        persistServers(currentServers)
        
        // Clear the server being edited
        _serverToEdit.value = null
    }
    
    fun setServerToEdit(serverInfo: ServerInfo?) {
        _serverToEdit.value = serverInfo
    }
    
    fun deleteServer(serverInfo: ServerInfo) {
        val currentServers = _savedServers.value.toMutableList()
        currentServers.removeAll { 
            it.address == serverInfo.address && it.port == serverInfo.port 
        }
        _savedServers.value = currentServers
        persistServers(currentServers)
    }
    
    private fun persistServers(servers: List<ServerInfo>) {
        context?.let { ctx ->
            val prefs = ctx.getSharedPreferences("hammumble_servers", Context.MODE_PRIVATE)
            val jsonArray = JSONArray()
            
            servers.forEach { server ->
                val jsonObj = JSONObject()
                jsonObj.put("name", server.name)
                jsonObj.put("address", server.address)
                jsonObj.put("port", server.port)
                jsonObj.put("username", server.username)
                jsonObj.put("password", server.password)
                jsonObj.put("autoConnect", server.autoConnect)
                jsonObj.put("autoJoinChannel", server.autoJoinChannel)
                if (server.clientCertificatePath != null) {
                    jsonObj.put("clientCertificatePath", server.clientCertificatePath)
                }
                jsonObj.put("clientCertificatePassword", server.clientCertificatePassword)
                jsonObj.put("registerWithServer", server.registerWithServer)
                jsonObj.put("skipCertificateVerification", server.skipCertificateVerification)
                jsonArray.put(jsonObj)
            }
            
            prefs.edit().putString("servers", jsonArray.toString()).apply()
        }
    }
    
    // === Latency/Ping Functions ===
    
    private fun getServerKey(serverInfo: ServerInfo): String {
        return "${serverInfo.address}:${serverInfo.port}"
    }
    
    /**
     * Ping a single server to measure latency
     */
    fun pingServer(serverInfo: ServerInfo) {
        val serverKey = getServerKey(serverInfo)
        
        viewModelScope.launch {
            // Set latency to null (checking state)
            _serverLatencies.value = _serverLatencies.value + (serverKey to null)
            
            val latency = measureLatency(serverInfo.address, serverInfo.port)
            
            // Update with actual latency (or -1 for unreachable)
            _serverLatencies.value = _serverLatencies.value + (serverKey to latency)
        }
    }
    
    /**
     * Ping all saved servers
     */
    fun pingAllServers() {
        _savedServers.value.forEach { server ->
            pingServer(server)
        }
    }
    
    /**
     * Measure latency to a server by attempting a TCP connection
     * Returns latency in milliseconds, or -1 if unreachable
     */
    private suspend fun measureLatency(address: String, port: Int): Int = withContext(Dispatchers.IO) {
        var socket: Socket? = null
        try {
            val startTime = System.currentTimeMillis()
            socket = Socket()
            
            // Set connection timeout to 5 seconds
            socket.connect(InetSocketAddress(address, port), 5000)
            
            val endTime = System.currentTimeMillis()
            val latency = (endTime - startTime).toInt()
            
            latency
        } catch (e: Exception) {
            // Connection failed
            -1
        } finally {
            try {
                socket?.close()
            } catch (e: Exception) {
                // Ignore close errors
            }
        }
    }
    
    /**
     * Get latency for a specific server
     */
    fun getServerLatency(serverInfo: ServerInfo): Int? {
        val serverKey = getServerKey(serverInfo)
        return _serverLatencies.value[serverKey]
    }
    
    // === Audio Settings Persistence ===
    
    private fun loadAudioSettings() {
        context?.let { ctx ->
            val prefs = ctx.getSharedPreferences("hammumble_settings", Context.MODE_PRIVATE)
            val settingsJson = prefs.getString("audio_settings", null)
            
            if (settingsJson != null) {
                try {
                    val jsonObj = JSONObject(settingsJson)
                    val loadedSettings = AudioSettings.fromJson(jsonObj)
                    _audioSettings.value = loadedSettings
                    
                    // Also update the service if it's already set
                    service?.updateAudioSettings(loadedSettings)
                } catch (e: Exception) {
                    e.printStackTrace()
                    // If loading fails, keep default settings
                }
            }
        }
    }
    
    private fun saveAudioSettings(settings: AudioSettings) {
        context?.let { ctx ->
            val prefs = ctx.getSharedPreferences("hammumble_settings", Context.MODE_PRIVATE)
            val jsonString = settings.toJson().toString()
            prefs.edit().putString("audio_settings", jsonString).apply()
        }
    }
    
    private fun loadAppSettings() {
        context?.let { ctx ->
            val prefs = ctx.getSharedPreferences("hammumble_settings", Context.MODE_PRIVATE)
            val settingsJson = prefs.getString("app_settings", null)
            
            if (settingsJson != null) {
                try {
                    val jsonObj = JSONObject(settingsJson)
                    val loadedSettings = AppSettings.fromJson(jsonObj)
                    _appSettings.value = loadedSettings
                    pttSoundEnabled = loadedSettings.pttSoundEnabled
                    
                    // Apply speakerphone setting if connected
                    service?.setSpeakerphoneMode(loadedSettings.useSpeakerphone)
                } catch (e: Exception) {
                    e.printStackTrace()
                    // If loading fails, keep default settings
                }
            }
        }
    }
    
    private fun saveAppSettings(settings: AppSettings) {
        context?.let { ctx ->
            val prefs = ctx.getSharedPreferences("hammumble_settings", Context.MODE_PRIVATE)
            val jsonString = settings.toJson().toString()
            prefs.edit().putString("app_settings", jsonString).apply()
        }
    }
}

package com.hammumble.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Binder
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.hammumble.MumbleApplication
import com.hammumble.R
import com.hammumble.audio.AudioDeviceManager
import com.hammumble.audio.MumbleRogerBeepGenerator
import com.hammumble.audio.RogerBeepGenerator
import com.hammumble.audio.VoxPreToneGenerator
import com.hammumble.data.*
import com.hammumble.hardware.SerialPttManager
import com.hammumble.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import se.lublin.humla.Constants
import se.lublin.humla.HumlaService
import se.lublin.humla.IHumlaService
import se.lublin.humla.IHumlaSession
import se.lublin.humla.model.IChannel
import se.lublin.humla.model.IMessage
import se.lublin.humla.model.IUser
import se.lublin.humla.model.Server
import se.lublin.humla.util.HumlaException
import se.lublin.humla.util.IHumlaObserver
import java.util.ArrayList
import java.security.cert.X509Certificate
import android.widget.Toast

/**
 * MumbleService - Now powered by real Humla library with full audio support
 * Maintains the same interface but uses Humla's AudioHandler internally
 */
class MumbleService : Service() {
    
    companion object {
        private const val TAG = "MumbleService"
    }
    
    private val binder = MumbleBinder()
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    
    // Real Humla service connection - includes audio!
    private var humlaService: IHumlaService? = null
    private var humlaSession: IHumlaSession? = null
    
    // State flows (keeping same interface for UI compatibility)
    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()
    
    private val _currentServer = MutableStateFlow<ServerInfo?>(null)
    val currentServer: StateFlow<ServerInfo?> = _currentServer.asStateFlow()
    
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
    
    // Reconnection state for UI observation
    private val _isReconnecting = MutableStateFlow(false)
    val isReconnectingFlow: StateFlow<Boolean> = _isReconnecting.asStateFlow()
    
    private val _reconnectAttemptCount = MutableStateFlow(0)
    val reconnectAttemptCount: StateFlow<Int> = _reconnectAttemptCount.asStateFlow()
    
    // Voice hold timer state (milliseconds remaining, 0 = not active)
    private val _voiceHoldTimerMs = MutableStateFlow(0)
    val voiceHoldTimerMs: StateFlow<Int> = _voiceHoldTimerMs.asStateFlow()
    
    // Audio level monitoring for VU meters (0.0 to 1.0)
    private val _inputAudioLevel = MutableStateFlow(0f)
    val inputAudioLevel: StateFlow<Float> = _inputAudioLevel.asStateFlow()
    
    private val _outputAudioLevel = MutableStateFlow(0f)
    val outputAudioLevel: StateFlow<Float> = _outputAudioLevel.asStateFlow()
    
    private var isTalking = false
    private var voiceStoppedAt = 0L
    
    // Audio volume management
    private var audioManager: android.media.AudioManager? = null
    private var originalVolume: Int = -1
    private var volumeWasMaxed: Boolean = false
    
    // Serial PTT manager for hardware PTT
    private var serialPttManager: SerialPttManager? = null
    
    // Audio device manager for separate TX/RX routing
    private var audioDeviceManager: AudioDeviceManager? = null
    
    // Roger beep generator for local audio feedback
    private var rogerBeepGenerator: RogerBeepGenerator? = null
    
    // Mumble roger beep generator for transmitting to Mumble
    private var mumbleRogerBeepGenerator: MumbleRogerBeepGenerator? = null
    
    // VOX pre-tone generator for triggering external VOX before voice transmission
    private var voxPreToneGenerator: VoxPreToneGenerator? = null
    
    // Auto-reconnection state
    private var isReconnecting = false
    private var reconnectAttempts = 0
    private val maxReconnectAttempts = 120  // 120 attempts * 30 seconds = 1 hour
    private var reconnectJob: Job? = null
    private var lastServerInfo: ServerInfo? = null
    private var userDisconnected = false  // Track if user manually disconnected
    
    // Registration tracking
    private var wasUserRegistered = false  // Track if user becomes registered during session
    
    // Audio level simulation jobs
    private var inputLevelJob: Job? = null
    private var outputLevelJob: Job? = null
    
    // Network monitoring
    private var connectivityManager: ConnectivityManager? = null
    private var isNetworkAvailable = false
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    
    // Track when other users stop talking (for roger beep after voice hold)
    private val userTalkStopTimes = mutableMapOf<Int, Long>() // userId -> stopTime
    private var rogerBeepTimerJob: Job? = null
    
    // Certificate connection fallback tracking
    private var connectionFailureCount = 0
    private var isAttemptingFallback = false
    private var hasTriedWithoutCertificate = false
    
    // Track when WE stop talking (for mumble roger beep after our voice hold)
    private var myLastTalkTime: Long = 0
    private var mumbleRogerBeepJob: Job? = null
    private var voiceHoldCountdownJob: Job? = null
    private var isTransmittingRogerBeep = false // Flag to prevent recursive roger beep
    
    inner class MumbleBinder : Binder() {
        fun getService(): MumbleService = this@MumbleService
    }
    
    // Humla observer to receive all events including audio
    private val humlaObserver = object : IHumlaObserver {
        override fun onConnected() {
            serviceScope.launch(Dispatchers.Main) {
                _connectionState.value = ConnectionState.CONNECTED
                
                // Reset registration tracking for new connection
                wasUserRegistered = false
                
                // Reset connection failure tracking on successful connection
                connectionFailureCount = 0
                hasTriedWithoutCertificate = false
                
                // Set audio mode to MODE_IN_COMMUNICATION for proper VoIP behavior
                audioManager?.mode = android.media.AudioManager.MODE_IN_COMMUNICATION
                android.util.Log.i("MumbleService", "Audio mode set to MODE_IN_COMMUNICATION for VoIP")
                
                // Get session when connected
                try {
                    humlaService?.let { service ->
                        humlaSession = service.HumlaSession()
                        
                        // Set system volume to maximum if enabled
                        if (_audioSettings.value.autoMaxVolume) {
                            setVolumeToMaximum()
                        }
                        
                        // Apply gain settings immediately and retry
                        val currentSettings = _audioSettings.value
                        applyGainSettings(currentSettings)
                        
                        serviceScope.launch {
                            delay(200)
                            applyGainSettings(currentSettings)
                            delay(500)
                            applyGainSettings(currentSettings)
                        }
                    }
                    updateChannels()
                    updateUsers()
                    
                    // Auto-join channel if configured
                    _currentServer.value?.autoJoinChannel?.let { channelName ->
                        if (channelName.isNotBlank()) {
                            android.util.Log.i("MumbleService", "Auto-join channel configured: $channelName")
                            serviceScope.launch {
                                // Wait longer for channels to be fully loaded
                                delay(1500)
                                android.util.Log.i("MumbleService", "Attempting to auto-join channel: $channelName")
                                autoJoinChannel(channelName)
                            }
                        }
                    }
                    
                    // Auto-register with server if configured
                    _currentServer.value?.let { server ->
                        if (server.registerWithServer) {
                            android.util.Log.i("MumbleService", "Auto-registration enabled, registering user with server")
                            serviceScope.launch {
                                // Wait a bit to ensure connection is stable
                                delay(2000)
                                registerUser()
                            }
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.e("MumbleService", "Error in onConnected", e)
                }
            }
        }
        
        override fun onConnecting() {
            serviceScope.launch(Dispatchers.Main) {
                _connectionState.value = ConnectionState.CONNECTING
            }
        }
        
        override fun onDisconnected(e: HumlaException?) {
            serviceScope.launch(Dispatchers.Main) {
                _connectionState.value = ConnectionState.DISCONNECTED
                
                // Restore audio mode to normal
                audioManager?.mode = android.media.AudioManager.MODE_NORMAL
                android.util.Log.i("MumbleService", "Audio mode restored to MODE_NORMAL")
                
                // Restore original volume if it was changed
                restoreOriginalVolume()
                
                // Check if this is a certificate-related error and try fallback
                val errorMessage = e?.message ?: ""
                val isCertificateError = errorMessage.contains("certificate", ignoreCase = true) ||
                                       errorMessage.contains("SSL", ignoreCase = true) ||
                                       errorMessage.contains("TLS", ignoreCase = true) ||
                                       errorMessage.contains("handshake", ignoreCase = true)
                
                if (!userDisconnected && lastServerInfo != null) {
                    if (isCertificateError && !hasTriedWithoutCertificate && !lastServerInfo!!.skipCertificateVerification) {
                        Log.i("MumbleService", "Certificate error detected, trying connection without certificate verification")
                        hasTriedWithoutCertificate = true
                        // Try connecting without certificate verification
                        serviceScope.launch {
                            kotlinx.coroutines.delay(1000) // Brief delay
                            tryConnection(lastServerInfo!!, usesTrustStore = false)
                        }
                    } else {
                        Log.i("MumbleService", "Connection lost unexpectedly, starting auto-reconnect")
                        if (e != null) {
                            Log.w("MumbleService", "Disconnect reason: ${e.message}")
                        }
                        // Increment failure count for trust store clearing
                        connectionFailureCount++
                        com.hammumble.util.HamMumbleTrustStore.clearTrustStoreAfterFailures(this@MumbleService, connectionFailureCount)
                        startReconnecting()
                    }
                } else if (userDisconnected) {
                    Log.i("MumbleService", "User disconnected, no auto-reconnect")
                }
            }
        }
        
        override fun onTLSHandshakeFailed(chain: Array<out X509Certificate>?) {
            // Auto-accept server certificates (for development)
            // In production, you'd want to show a dialog to the user
            android.util.Log.w("MumbleService", "TLS Handshake failed - auto-accepting certificate")
            
            if (chain != null && chain.isNotEmpty()) {
                try {
                    val certificate = chain[0]
                    val alias = _currentServer.value?.address ?: "unknown"
                    
                    // Add certificate to trust store
                    com.hammumble.util.HamMumbleTrustStore.addCertificate(
                        this@MumbleService,
                        alias,
                        certificate
                    )
                    
                    android.util.Log.i("MumbleService", "Certificate added to trust store, reconnecting...")
                    
                    // Reconnect with the trust store
                    serviceScope.launch {
                        kotlinx.coroutines.delay(500) // Brief delay
                        _currentServer.value?.let { serverInfo ->
                            // Trigger reconnect
                            disconnect()
                            kotlinx.coroutines.delay(500)
                            connect(serverInfo)
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.e("MumbleService", "Failed to accept certificate", e)
                    serviceScope.launch(Dispatchers.Main) {
                        _connectionState.value = ConnectionState.ERROR
                    }
                }
            } else {
                android.util.Log.e("MumbleService", "No certificate chain provided")
                serviceScope.launch(Dispatchers.Main) {
                    _connectionState.value = ConnectionState.ERROR
                }
            }
        }
        
        override fun onChannelAdded(channel: IChannel) {
            serviceScope.launch(Dispatchers.Main) {
                updateChannels()
            }
        }
        
        override fun onChannelStateUpdated(channel: IChannel) {
            serviceScope.launch(Dispatchers.Main) {
                updateChannels()
            }
        }
        
        override fun onChannelRemoved(channel: IChannel) {
            serviceScope.launch(Dispatchers.Main) {
                updateChannels()
            }
        }
        
        override fun onChannelPermissionsUpdated(channel: IChannel) {
            // Not used for now
        }
        
        override fun onUserConnected(user: IUser) {
            serviceScope.launch(Dispatchers.Main) {
                updateUsers()
            }
        }
        
        override fun onUserStateUpdated(user: IUser) {
            serviceScope.launch(Dispatchers.Main) {
                updateUsers()
                // Update current user if it's us
                humlaSession?.let { session ->
                    if (user.session == session.sessionId) {
                        _currentUser.value = convertUser(user)
                        
                        // Check if user just got registered (userId changed from -1 to a positive value)
                        if (user.userId >= 0 && !wasUserRegistered) {
                            wasUserRegistered = true
                            android.util.Log.i("MumbleService", "User '${user.name}' successfully registered with ID: ${user.userId}")
                            showToast("Successfully registered as '${user.name}'!")
                        }
                    }
                }
            }
        }
        
        override fun onUserTalkStateUpdated(user: IUser) {
            serviceScope.launch(Dispatchers.Main) {
                updateUsers()
                
                humlaSession?.let { session ->
                    val isNowTalking = user.talkState == se.lublin.humla.model.TalkState.TALKING
                    
                    // Check if this is us or someone else
                    if (user.session == session.sessionId) {
                        // === OUR OWN talking state (for voice hold timer) ===
                        
                        // Ignore state changes during roger beep transmission
                        if (isTransmittingRogerBeep) {
                            android.util.Log.d("MumbleService", "Ignoring talk state change during roger beep transmission")
                            return@launch
                        }
                        
                        // Only handle for Voice Activity mode
                        if (_audioSettings.value.transmissionMode == TransmissionMode.VOICE_ACTIVITY) {
                            if (isNowTalking && !isTalking) {
                                // Started talking - cancel any pending roger beep
                                isTalking = true
                                _voiceHoldTimerMs.value = 0
                                voiceHoldCountdownJob?.cancel()
                                
                                // Simulate input audio level
                                simulateInputLevel(talking = true)
                            } else if (!isNowTalking && isTalking) {
                                // Stopped talking - start voice hold countdown
                                isTalking = false
                                voiceStoppedAt = System.currentTimeMillis()
                                startVoiceHoldCountdown()
                                
                                // Stop simulating input level
                                simulateInputLevel(talking = false)
                            }
                        } else {
                            // Not VAD mode - reset timer
                            isTalking = isNowTalking
                            _voiceHoldTimerMs.value = 0
                            voiceHoldCountdownJob?.cancel()
                            
                            // Simulate input level for PTT/Continuous mode
                            simulateInputLevel(talking = isNowTalking)
                        }
                    } else {
                        // === SOMEONE ELSE talking (trigger serial PTT and track for roger beep) ===
                        
                        if (isNowTalking) {
                            // Someone started talking -> activate serial PTT
                            if (_audioSettings.value.serialPtt.enabled) {
                                android.util.Log.i("MumbleService", "User ${user.name} started talking - activating serial PTT")
                                serialPttManager?.activatePtt()
                            }
                            
                            // Remove from stop tracking (they're talking again)
                            userTalkStopTimes.remove(user.session)
                            
                            // Simulate output audio level
                            simulateOutputLevel(anyoneTalking = true)
                        } else {
                            // Someone stopped talking
                            android.util.Log.i("MumbleService", "User ${user.name} stopped talking - starting voice hold timer")
                            
                            // Track when they stopped (for roger beep after voice hold)
                            userTalkStopTimes[user.session] = System.currentTimeMillis()
                            
                            // Start timer to check voice hold and trigger roger beep
                            startRogerBeepTimer()
                            
                            // Check if ANYONE else is still talking in our channel
                            val anyoneStillTalking = checkIfAnyoneStillTalking()
                            if (!anyoneStillTalking && _audioSettings.value.serialPtt.enabled) {
                                // No one talking anymore -> deactivate serial PTT
                                android.util.Log.i("MumbleService", "No one talking anymore - deactivating serial PTT")
                                serialPttManager?.deactivatePtt()
                            }
                            
                            // Update output audio level
                            simulateOutputLevel(anyoneTalking = anyoneStillTalking)
                        }
                    }
                }
            }
        }
        
        override fun onUserRemoved(user: IUser, reason: String?) {
            serviceScope.launch(Dispatchers.Main) {
                updateUsers()
            }
        }
        
        override fun onUserJoinedChannel(user: IUser, newChannel: IChannel, oldChannel: IChannel) {
            serviceScope.launch(Dispatchers.Main) {
                updateUsers()
            }
        }
        
        override fun onMessageLogged(message: IMessage) {
            serviceScope.launch(Dispatchers.Main) {
                val chatMessage = ChatMessage(
                    sender = message.actorName ?: "Server",
                    message = message.message ?: "",
                    timestamp = System.currentTimeMillis()
                )
                
                // Check if this message was already added (within last 2 seconds with same content)
                val currentMessages = _chatMessages.value
                val isDuplicate = currentMessages.any { existing ->
                    existing.sender == chatMessage.sender &&
                    existing.message == chatMessage.message &&
                    (chatMessage.timestamp - existing.timestamp) < 2000 // within 2 seconds
                }
                
                if (!isDuplicate) {
                    _chatMessages.value = currentMessages + chatMessage
                }
            }
        }
        
        override fun onVoiceTargetChanged(mode: se.lublin.humla.util.VoiceTargetMode) {
            // Not used for now
        }
        
        override fun onLogInfo(message: String?) {}
        override fun onLogWarning(message: String?) {}
        override fun onLogError(message: String?) {}
        
        override fun onPermissionDenied(reason: String?) {
            serviceScope.launch(Dispatchers.Main) {
                val message = reason ?: "Permission denied"
                android.util.Log.w("MumbleService", "Permission denied by server: $message")
                
                // Check if this might be a registration failure
                // Registration failures typically happen shortly after we send a registration request
                if (message.contains("register", ignoreCase = true) || 
                    message.contains("SelfRegister", ignoreCase = true)) {
                    showToast("Registration failed: $message")
                } else {
                    // Generic permission denied
                    showToast("Permission denied: $message")
                }
            }
        }
    }
    
    /**
     * Check if anyone in our current channel is still talking
     * Used for serial PTT to know when to deactivate
     */
    private fun checkIfAnyoneStillTalking(): Boolean {
        return try {
            humlaSession?.let { session ->
                val currentUser = session.sessionUser
                val currentChannelId = currentUser?.channel?.id
                if (currentChannelId == null) return@let false
                
                val ourSessionId = session.sessionId
                
                // Collect all users from all channels
                val rootChannel = session.rootChannel
                if (rootChannel == null) return@let false
                
                val allUsers = mutableListOf<IUser>()
                collectUsersFromChannel(rootChannel, allUsers)
                
                // Check if anyone in our channel (excluding ourselves) is talking
                allUsers.any { user ->
                    user.channel?.id == currentChannelId &&
                    user.session != ourSessionId &&
                    user.talkState == se.lublin.humla.model.TalkState.TALKING
                }
            } ?: false
        } catch (e: Exception) {
            android.util.Log.e("MumbleService", "Error checking talking users", e)
            false
        }
    }
    
    // Voice hold timer countdown coroutine
    private fun startVoiceHoldCountdown() {
        // Cancel any existing countdown job
        voiceHoldCountdownJob?.cancel()
        
        val holdTimeMs = _audioSettings.value.voiceHoldTime
        
        voiceHoldCountdownJob = serviceScope.launch {
            while (true) {
                val elapsed = System.currentTimeMillis() - voiceStoppedAt
                val remaining = (holdTimeMs - elapsed).toInt()
                
                if (remaining <= 0) {
                    _voiceHoldTimerMs.value = 0
                    
                    // Trigger Mumble roger beep if enabled
                    val mumbleBeepSettings = _audioSettings.value.mumbleRogerBeep
                    android.util.Log.i("MumbleService", "Voice hold timer expired - Mumble roger beep enabled: ${mumbleBeepSettings.enabled}, style: ${mumbleBeepSettings.style}")
                    
                    if (mumbleBeepSettings.enabled) {
                        android.util.Log.i("MumbleService", "Triggering Mumble roger beep transmission")
                        playMumbleRogerBeep()
                    } else {
                        android.util.Log.d("MumbleService", "Mumble roger beep is disabled, skipping")
                    }
                    
                    break
                }
                
                _voiceHoldTimerMs.value = remaining
                kotlinx.coroutines.delay(50) // Update every 50ms for smooth countdown
            }
        }
    }
    
    /**
     * Play roger beep that gets transmitted to Mumble
     * This is sent when our own voice hold timer ends
     */
    private fun playMumbleRogerBeep(manageTalkingState: Boolean = true) {
        mumbleRogerBeepJob?.cancel()
        
        mumbleRogerBeepJob = serviceScope.launch {
            try {
                // Set flag to prevent recursive roger beep
                isTransmittingRogerBeep = true
                
                android.util.Log.i("MumbleService", "Generating Mumble roger beep (manageTalkingState=$manageTalkingState)")
                
                val beepGenerator = mumbleRogerBeepGenerator ?: run {
                    android.util.Log.w("MumbleService", "Mumble roger beep generator not initialized")
                    isTransmittingRogerBeep = false
                    return@launch
                }
                
                // Generate the roger beep audio
                var generatedAudio: ShortArray? = null
                beepGenerator.setAudioCallback { audioData ->
                    android.util.Log.d("MumbleService", "Mumble roger beep audio generated: ${audioData.size} samples (${audioData.size / 48}ms)")
                    generatedAudio = audioData
                }
                
                // Trigger generation (now synchronous, so audio is ready immediately)
                beepGenerator.playRogerBeep()
                
                val audioToSend = generatedAudio
                if (audioToSend == null) {
                    android.util.Log.w("MumbleService", "No audio generated for Mumble roger beep")
                    isTransmittingRogerBeep = false
                    return@launch
                }
                
                // Check if we have an active Mumble session
                if (humlaSession == null) {
                    android.util.Log.e("MumbleService", "Cannot transmit roger beep - no active Mumble session")
                    isTransmittingRogerBeep = false
                    return@launch
                }
                
                android.util.Log.i("MumbleService", "Mumble session active, preparing to transmit roger beep")
                
                // Enable talking state only if we need to manage it
                // In PTT mode, talking state is already active, so we don't need to set it again
                if (manageTalkingState) {
                    withContext(Dispatchers.Main) {
                        humlaSession?.setTalkingState(true)
                        android.util.Log.d("MumbleService", "Set talking state to TRUE for roger beep transmission")
                    }
                    
                    // Small delay to ensure talking state is active
                    delay(50)
                }
                
                android.util.Log.i("MumbleService", "Injecting ${audioToSend.size} audio samples directly into Mumble (${audioToSend.size / 48}ms duration)")
                
                // Inject audio directly into Humla's audio pipeline
                // Run on IO dispatcher because injectAudioData() blocks with Thread.sleep() for frame pacing
                val success = withContext(Dispatchers.IO) {
                    val result = humlaSession?.injectAudioData(audioToSend) ?: false
                    android.util.Log.d("MumbleService", "injectAudioData completed and returned: $result")
                    result
                }
                
                if (success) {
                    android.util.Log.i("MumbleService", "Mumble roger beep successfully transmitted")
                } else {
                    android.util.Log.e("MumbleService", "Failed to inject audio into Mumble - injectAudioData returned false")
                }
                
                // Add a small buffer delay to ensure last packets are fully transmitted
                delay(100)
                
                // Disable talking state (always do this, whether we enabled it or not)
                withContext(Dispatchers.Main) {
                    humlaSession?.setTalkingState(false)
                    android.util.Log.d("MumbleService", "Set talking state to FALSE after roger beep transmission")
                }
                
                // Small delay before clearing flag to ensure state updates are processed
                delay(100)
                
                android.util.Log.i("MumbleService", "Mumble roger beep transmission complete")
                
            } catch (e: Exception) {
                android.util.Log.e("MumbleService", "Error playing Mumble roger beep", e)
                // Make sure to stop talking on error
                withContext(Dispatchers.Main) {
                    try {
                        humlaSession?.setTalkingState(false)
                    } catch (ex: Exception) {
                        android.util.Log.e("MumbleService", "Error stopping talking state", ex)
                    }
                }
            } finally {
                // Always clear the flag
                isTransmittingRogerBeep = false
            }
        }
    }
    
    /**
     * Start timer to check when users' voice hold expires and play roger beep
     * This monitors all users who have stopped talking and plays beep after voice hold time
     */
    private fun startRogerBeepTimer() {
        // Cancel existing timer if running
        rogerBeepTimerJob?.cancel()
        
        rogerBeepTimerJob = serviceScope.launch {
            val voiceHoldTimeMs = _audioSettings.value.voiceHoldTime.toLong()
            
            while (userTalkStopTimes.isNotEmpty()) {
                val currentTime = System.currentTimeMillis()
                val expiredUsers = mutableListOf<Int>()
                
                // Check which users have exceeded voice hold time
                for ((userId, stopTime) in userTalkStopTimes) {
                    val elapsed = currentTime - stopTime
                    if (elapsed >= voiceHoldTimeMs) {
                        expiredUsers.add(userId)
                    }
                }
                
                // Remove expired users and play roger beep if any expired
                if (expiredUsers.isNotEmpty()) {
                    android.util.Log.i("MumbleService", "${expiredUsers.size} user(s) voice hold expired - playing roger beep")
                    
                    // Play roger beep (local only)
                    rogerBeepGenerator?.playRogerBeep()
                    
                    // Remove expired users from tracking
                    expiredUsers.forEach { userId ->
                        userTalkStopTimes.remove(userId)
                    }
                }
                
                // If no more users to track, exit
                if (userTalkStopTimes.isEmpty()) {
                    break
                }
                
                // Check every 100ms
                kotlinx.coroutines.delay(100)
            }
        }
    }
    
    // Service connection for real HumlaService
    private val humlaServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as HumlaService.HumlaBinder
            humlaService = binder.service
            try {
                humlaService?.registerObserver(humlaObserver)
            } catch (e: Exception) {
                // Handle registration error
            }
        }
        
        override fun onServiceDisconnected(name: ComponentName?) {
            try {
                humlaService?.unregisterObserver(humlaObserver)
            } catch (e: Exception) {
                // Service may already be gone
            }
            humlaService = null
            humlaSession = null
        }
    }
    
    override fun onBind(intent: Intent): IBinder {
        return binder
    }
    
    override fun onCreate() {
        super.onCreate()
        
        // Load saved audio settings
        loadAudioSettings()
        
        // Initialize Audio Device Manager
        audioDeviceManager = AudioDeviceManager(this)
        
        // Initialize Serial PTT Manager
        serialPttManager = SerialPttManager(this)
        
        // Initialize Roger Beep Generator
        rogerBeepGenerator = RogerBeepGenerator(this)
        
        // Initialize Mumble Roger Beep Generator
        mumbleRogerBeepGenerator = MumbleRogerBeepGenerator(this)
        
        // Initialize VOX Pre-Tone Generator
        voxPreToneGenerator = VoxPreToneGenerator()
        
        // Initialize AudioManager for volume control
        audioManager = getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
        
        // Initialize ConnectivityManager and register network callback
        connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        setupNetworkCallback()
        
        // Bind to real HumlaService from the library
        val intent = Intent(this, HumlaService::class.java)
        bindService(intent, humlaServiceConnection, Context.BIND_AUTO_CREATE)
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(MumbleApplication.NOTIFICATION_ID, createNotification())
        
        // Handle actions
        when (intent?.action) {
            "STOP_RECONNECTING" -> {
                Log.i("MumbleService", "User stopped reconnection from notification")
                stopReconnecting(success = false)
                return START_NOT_STICKY
            }
        }
        
        // Check if server info was passed in the intent
        intent?.let { i ->
            val address = i.getStringExtra("server_address")
            val port = i.getIntExtra("server_port", 64738)
            val username = i.getStringExtra("username")
            val password = i.getStringExtra("password") ?: ""
            val serverName = i.getStringExtra("server_name") ?: ""
            val autoJoinChannel = i.getStringExtra("auto_join_channel") ?: ""
            
            if (!address.isNullOrBlank() && !username.isNullOrBlank()) {
                val serverInfo = ServerInfo(
                    address = address,
                    port = port,
                    username = username,
                    password = password,
                    name = serverName,
                    autoJoinChannel = autoJoinChannel
                )
                connect(serverInfo)
            }
        }
        
        return START_NOT_STICKY
    }
    
    override fun onDestroy() {
        super.onDestroy()
        
        // Stop reconnection attempts
        stopReconnecting()
        
        // Unregister network callback
        networkCallback?.let { callback ->
            try {
                connectivityManager?.unregisterNetworkCallback(callback)
            } catch (e: Exception) {
                Log.e("MumbleService", "Failed to unregister network callback", e)
            }
        }
        
        disconnect()
        
        // Restore original volume before destroying service
        restoreOriginalVolume()
        
        // Cancel roger beep timer
        rogerBeepTimerJob?.cancel()
        userTalkStopTimes.clear()
        
        // Cancel voice hold countdown
        voiceHoldCountdownJob?.cancel()
        
        // Cancel mumble roger beep timer
        mumbleRogerBeepJob?.cancel()
        
        // Disconnect serial PTT
        serialPttManager?.disconnect()
        
        // Release roger beep generator
        rogerBeepGenerator?.release()
        
        // Release mumble roger beep generator
        mumbleRogerBeepGenerator?.release()
        
        // Release VOX pre-tone generator
        voxPreToneGenerator?.release()
        
        // Unbind from HumlaService
        try {
            unbindService(humlaServiceConnection)
        } catch (e: Exception) {
            // Service may already be unbound
        }
    }
    
    /**
     * Reload server info from SharedPreferences to get the latest certificate info.
     * This ensures we always use the most up-to-date server configuration with certificates.
     */
    private fun reloadServerInfoFromStorage(serverInfo: ServerInfo): ServerInfo {
        return try {
            val prefs = getSharedPreferences("hammumble_servers", Context.MODE_PRIVATE)
            val serversJson = prefs.getString("servers", "[]") ?: "[]"
            val jsonArray = org.json.JSONArray(serversJson)
            
            // Find matching server by address and port
            for (i in 0 until jsonArray.length()) {
                val jsonObj = jsonArray.getJSONObject(i)
                val address = jsonObj.getString("address")
                val port = jsonObj.optInt("port", 64738)
                
                if (address == serverInfo.address && port == serverInfo.port) {
                    // Found matching server, reload it completely
                    val reloaded = ServerInfo(
                        name = jsonObj.optString("name", ""),
                        address = address,
                        port = port,
                        username = jsonObj.getString("username"),
                        password = jsonObj.optString("password", ""),
                        autoConnect = jsonObj.optBoolean("autoConnect", false),
                        autoJoinChannel = jsonObj.optString("autoJoinChannel", ""),
                        clientCertificatePath = if (jsonObj.has("clientCertificatePath")) 
                            jsonObj.getString("clientCertificatePath") else null,
                        clientCertificatePassword = jsonObj.optString("clientCertificatePassword", ""),
                        registerWithServer = jsonObj.optBoolean("registerWithServer", false),
                        skipCertificateVerification = jsonObj.optBoolean("skipCertificateVerification", false)
                    )
                    android.util.Log.i("MumbleService", "✓ Reloaded server config from storage with certificate: ${reloaded.clientCertificatePath}")
                    return reloaded
                }
            }
            
            // Not found in storage, use original
            android.util.Log.w("MumbleService", "Server not found in storage, using provided info")
            serverInfo
        } catch (e: Exception) {
            android.util.Log.e("MumbleService", "Failed to reload server info from storage", e)
            serverInfo
        }
    }
    
    /**
     * Simulate input audio level based on our own talk state
     */
    private fun simulateInputLevel(talking: Boolean) {
        inputLevelJob?.cancel()
        inputLevelJob = serviceScope.launch {
            if (talking) {
                // Simulate varying audio input while talking
                while (isActive) {
                    val randomLevel = 0.5f + (Math.random().toFloat() * 0.4f) // 0.5 to 0.9
                    _inputAudioLevel.value = randomLevel
                    delay(100) // Update every 100ms for smoother animation
                }
            } else {
                // Decay to zero smoothly
                while (_inputAudioLevel.value > 0.02f && isActive) {
                    _inputAudioLevel.value = (_inputAudioLevel.value * 0.85f).coerceAtLeast(0f)
                    delay(80) // Slower decay rate
                }
                _inputAudioLevel.value = 0f
            }
        }
    }
    
    /**
     * Simulate output audio level based on other users talking
     */
    private fun simulateOutputLevel(anyoneTalking: Boolean) {
        outputLevelJob?.cancel()
        outputLevelJob = serviceScope.launch {
            if (anyoneTalking) {
                // Simulate varying audio output while receiving
                while (isActive) {
                    val randomLevel = 0.4f + (Math.random().toFloat() * 0.5f) // 0.4 to 0.9
                    _outputAudioLevel.value = randomLevel
                    delay(100) // Update every 100ms for smoother animation
                }
            } else {
                // Decay to zero smoothly
                while (_outputAudioLevel.value > 0.02f && isActive) {
                    _outputAudioLevel.value = (_outputAudioLevel.value * 0.85f).coerceAtLeast(0f)
                    delay(80) // Slower decay rate
                }
                _outputAudioLevel.value = 0f
            }
        }
    }
    
    fun connect(serverInfo: ServerInfo) {
        if (_connectionState.value != ConnectionState.DISCONNECTED) {
            return
        }
        
        // Clear trust store on first run
        com.hammumble.util.HamMumbleTrustStore.clearTrustStoreOnFirstRun(this)
        
        // Reload server info from storage to get latest certificate info
        val reloadedServerInfo = reloadServerInfoFromStorage(serverInfo)
        
        // Clear user disconnect flag and save server info for auto-reconnect
        userDisconnected = false
        lastServerInfo = reloadedServerInfo
        
        // Reset fallback tracking for new connection
        hasTriedWithoutCertificate = false
        
        // Debug: Log the reloaded serverInfo
        android.util.Log.d("MumbleService", "Connecting to server: ${reloadedServerInfo.name}")
        android.util.Log.d("MumbleService", "Reloaded certificate path: ${reloadedServerInfo.clientCertificatePath}")
        android.util.Log.d("MumbleService", "Reloaded certificate password: ${if (reloadedServerInfo.clientCertificatePassword.isNotEmpty()) "[set]" else "[empty]"}")
        android.util.Log.d("MumbleService", "Skip certificate verification: ${reloadedServerInfo.skipCertificateVerification}")
        
        // Try connection with appropriate certificate mode
        tryConnection(reloadedServerInfo, usesTrustStore = !reloadedServerInfo.skipCertificateVerification)
    }
    
    private fun tryConnection(serverInfo: ServerInfo, usesTrustStore: Boolean) {
        serviceScope.launch {
            try {
                _currentServer.value = serverInfo
                _connectionState.value = ConnectionState.CONNECTING
                
                // Wait for HumlaService to be bound
                var retries = 0
                while (humlaService == null && retries < 50) {
                    kotlinx.coroutines.delay(100)
                    retries++
                }
                
                if (humlaService == null) {
                    _connectionState.value = ConnectionState.ERROR
                    return@launch
                }
                
                // Create Humla Server object
                val server = Server(
                    -1,  // id
                    serverInfo.name.ifEmpty { serverInfo.address },
                    serverInfo.address,
                    serverInfo.port,
                    serverInfo.username,
                    serverInfo.password
                )
                
                // Convert TransmissionMode to Humla constant
                val transmitMode = when (_audioSettings.value.transmissionMode) {
                    TransmissionMode.PUSH_TO_TALK -> Constants.TRANSMIT_PUSH_TO_TALK
                    TransmissionMode.VOICE_ACTIVITY -> Constants.TRANSMIT_VOICE_ACTIVITY
                    TransmissionMode.CONTINUOUS -> Constants.TRANSMIT_CONTINUOUS
                }
                
                // Connect via HumlaService with all required parameters
                val connectIntent = Intent(this@MumbleService, HumlaService::class.java)
                connectIntent.action = HumlaService.ACTION_CONNECT
                connectIntent.putExtra(HumlaService.EXTRAS_SERVER, server)
                connectIntent.putExtra(HumlaService.EXTRAS_CLIENT_NAME, "HamMumble/1.0")
                connectIntent.putExtra(HumlaService.EXTRAS_TRANSMIT_MODE, transmitMode)
                connectIntent.putExtra(HumlaService.EXTRAS_DETECTION_THRESHOLD, _audioSettings.value.vadThreshold)
                connectIntent.putExtra(HumlaService.EXTRAS_VOICE_HOLD_TIME, _audioSettings.value.voiceHoldTime)
                connectIntent.putExtra(HumlaService.EXTRAS_AMPLITUDE_BOOST, 1.0f)
                connectIntent.putExtra(HumlaService.EXTRAS_AUTO_RECONNECT, false)
                connectIntent.putExtra(HumlaService.EXTRAS_AUTO_RECONNECT_DELAY, 5000)
                connectIntent.putExtra(HumlaService.EXTRAS_USE_OPUS, true)
                connectIntent.putExtra(HumlaService.EXTRAS_INPUT_RATE, 48000)
                connectIntent.putExtra(HumlaService.EXTRAS_INPUT_QUALITY, 40000)
                connectIntent.putExtra(HumlaService.EXTRAS_FORCE_TCP, false)
                connectIntent.putExtra(HumlaService.EXTRAS_USE_TOR, false)
                connectIntent.putExtra(HumlaService.EXTRAS_AUDIO_SOURCE, android.media.MediaRecorder.AudioSource.MIC)
                connectIntent.putExtra(HumlaService.EXTRAS_AUDIO_STREAM, android.media.AudioManager.STREAM_VOICE_CALL)
                connectIntent.putExtra(HumlaService.EXTRAS_FRAMES_PER_PACKET, 2)
                connectIntent.putExtra(HumlaService.EXTRAS_HALF_DUPLEX, false)
                connectIntent.putExtra(HumlaService.EXTRAS_ENABLE_PREPROCESSOR, true)
                
                // Add empty access tokens list (required by Humla)
                connectIntent.putStringArrayListExtra(HumlaService.EXTRAS_ACCESS_TOKENS, ArrayList<String>())
                
                // Add trust store parameters (only if usesTrustStore is true)
                if (usesTrustStore) {
                    val trustStorePath = com.hammumble.util.HamMumbleTrustStore.getTrustStorePath(this@MumbleService)
                    if (trustStorePath != null) {
                        connectIntent.putExtra(HumlaService.EXTRAS_TRUST_STORE, trustStorePath)
                        connectIntent.putExtra(HumlaService.EXTRAS_TRUST_STORE_PASSWORD, 
                            com.hammumble.util.HamMumbleTrustStore.getTrustStorePassword())
                        connectIntent.putExtra(HumlaService.EXTRAS_TRUST_STORE_FORMAT, 
                            com.hammumble.util.HamMumbleTrustStore.getTrustStoreFormat())
                        android.util.Log.d("MumbleService", "Using trust store: $trustStorePath")
                    } else {
                        android.util.Log.d("MumbleService", "No trust store yet, will create on first cert failure")
                    }
                } else {
                    android.util.Log.d("MumbleService", "Skipping certificate verification (no trust store)")
                }
                
                // Add client certificate - generate unique one if not configured
                android.util.Log.d("MumbleService", "Certificate path from serverInfo: ${serverInfo.clientCertificatePath}")
                var certPath = serverInfo.clientCertificatePath
                var certPassword = serverInfo.clientCertificatePassword
                
                // If no certificate configured, generate a unique one based on username
                if (certPath == null || certPath.isEmpty()) {
                    android.util.Log.i("MumbleService", "No certificate configured, generating unique certificate for user: ${serverInfo.username}")
                    
                    // Generate certificate with username as CN and auto-generated password
                    val autoPassword = java.util.UUID.randomUUID().toString().substring(0, 16)
                    certPath = com.hammumble.util.CertificateManager.generateCertificate(
                        context = this@MumbleService,
                        commonName = serverInfo.username,
                        email = "",
                        password = autoPassword
                    )
                    certPassword = autoPassword
                    
                    if (certPath != null) {
                        android.util.Log.i("MumbleService", "✓ Generated unique certificate: ${com.hammumble.util.CertificateManager.getFileName(certPath)}")
                        
                        // Update the server info in SharedPreferences with the new certificate
                        try {
                            val prefs = getSharedPreferences("hammumble_servers", Context.MODE_PRIVATE)
                            val serversJson = prefs.getString("servers", "[]") ?: "[]"
                            val jsonArray = org.json.JSONArray(serversJson)
                            
                            // Find and update matching server
                            for (i in 0 until jsonArray.length()) {
                                val jsonObj = jsonArray.getJSONObject(i)
                                if (jsonObj.getString("address") == serverInfo.address && 
                                    jsonObj.optInt("port", 64738) == serverInfo.port) {
                                    // Update certificate info
                                    jsonObj.put("clientCertificatePath", certPath)
                                    jsonObj.put("clientCertificatePassword", certPassword)
                                    android.util.Log.i("MumbleService", "✓ Updated server config with generated certificate")
                                    break
                                }
                            }
                            
                            // Save back to preferences
                            prefs.edit().putString("servers", jsonArray.toString()).apply()
                        } catch (e: Exception) {
                            android.util.Log.e("MumbleService", "Failed to update server with certificate", e)
                        }
                    } else {
                        android.util.Log.e("MumbleService", "Failed to generate certificate for ${serverInfo.username}")
                    }
                }
                
                if (certPath != null && certPath.isNotEmpty()) {
                    val certificateBytes = com.hammumble.util.CertificateManager.readCertificate(certPath)
                    if (certificateBytes != null) {
                        connectIntent.putExtra(HumlaService.EXTRAS_CERTIFICATE, certificateBytes)
                        connectIntent.putExtra(HumlaService.EXTRAS_CERTIFICATE_PASSWORD, certPassword)
                        android.util.Log.i("MumbleService", "✓ Using client certificate: ${com.hammumble.util.CertificateManager.getFileName(certPath)}")
                    } else {
                        android.util.Log.w("MumbleService", "Failed to read client certificate file: $certPath")
                    }
                } else {
                    android.util.Log.w("MumbleService", "No valid client certificate available")
                }
                
                startService(connectIntent)
                
            } catch (e: Exception) {
                _connectionState.value = ConnectionState.ERROR
            }
        }
    }
    
    /**
     * Setup network connectivity monitoring
     */
    private fun setupNetworkCallback() {
        networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                super.onAvailable(network)
                Log.i("MumbleService", "Network available")
                isNetworkAvailable = true
                
                // If we're trying to reconnect and network just came back, attempt immediately
                if (isReconnecting && reconnectAttempts < maxReconnectAttempts) {
                    serviceScope.launch {
                        Log.i("MumbleService", "Network restored - attempting reconnection")
                        attemptReconnect()
                    }
                }
            }
            
            override fun onLost(network: Network) {
                super.onLost(network)
                Log.i("MumbleService", "Network lost")
                isNetworkAvailable = false
                updateNotification()  // Update notification to show "Waiting for network..."
            }
        }
        
        val networkRequest = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        
        try {
            connectivityManager?.registerNetworkCallback(networkRequest, networkCallback!!)
            
            // Check initial network state
            val activeNetwork = connectivityManager?.activeNetwork
            val capabilities = connectivityManager?.getNetworkCapabilities(activeNetwork)
            isNetworkAvailable = capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
            Log.i("MumbleService", "Initial network state: available=$isNetworkAvailable")
        } catch (e: Exception) {
            Log.e("MumbleService", "Failed to register network callback", e)
        }
    }
    
    /**
     * Start automatic reconnection attempts
     */
    private fun startReconnecting() {
        if (isReconnecting) {
            return  // Already reconnecting
        }
        
        if (userDisconnected) {
            Log.i("MumbleService", "User disconnected manually, not auto-reconnecting")
            return
        }
        
        val serverToReconnect = lastServerInfo ?: _currentServer.value
        if (serverToReconnect == null) {
            Log.w("MumbleService", "No server info to reconnect to")
            return
        }
        
        Log.i("MumbleService", "Starting auto-reconnection to ${serverToReconnect.name}")
        isReconnecting = true
        reconnectAttempts = 0
        lastServerInfo = serverToReconnect
        
        // Update StateFlows for UI
        _isReconnecting.value = true
        _reconnectAttemptCount.value = 0
        
        // Start reconnection loop
        reconnectJob = serviceScope.launch {
            while (isReconnecting && reconnectAttempts < maxReconnectAttempts) {
                reconnectAttempts++
                _reconnectAttemptCount.value = reconnectAttempts
                updateNotification()  // Update notification with attempt count
                
                Log.i("MumbleService", "Reconnection attempt $reconnectAttempts/$maxReconnectAttempts")
                
                // Only attempt if network is available
                if (isNetworkAvailable) {
                    attemptReconnect()
                    
                    // Wait for connection state to change
                    delay(2000)  // Give connection attempt time to complete
                    
                    // Check if we successfully connected
                    if (_connectionState.value == ConnectionState.CONNECTED) {
                        Log.i("MumbleService", "Reconnection successful!")
                        stopReconnecting(success = true)
                        return@launch
                    }
                } else {
                    Log.i("MumbleService", "Network not available, waiting...")
                }
                
                // Wait 30 seconds before next attempt
                delay(30000)
            }
            
            // Max attempts reached
            if (reconnectAttempts >= maxReconnectAttempts) {
                Log.i("MumbleService", "Max reconnection attempts reached")
                stopReconnecting(success = false)
            }
        }
    }
    
    /**
     * Attempt a single reconnection
     */
    private suspend fun attemptReconnect() {
        val serverInfo = lastServerInfo ?: return
        
        withContext(Dispatchers.Main) {
            try {
                // Disconnect first if still connected
                if (_connectionState.value != ConnectionState.DISCONNECTED) {
                    disconnect()
                    delay(500)
                }
                
                // Attempt connection
                connect(serverInfo)
            } catch (e: Exception) {
                Log.e("MumbleService", "Reconnection attempt failed", e)
            }
        }
    }
    
    /**
     * Stop reconnection attempts
     */
    fun stopReconnecting(success: Boolean = false) {
        if (!isReconnecting) {
            return
        }
        
        Log.i("MumbleService", "Stopping reconnection (success=$success, attempts=$reconnectAttempts)")
        isReconnecting = false
        reconnectJob?.cancel()
        reconnectJob = null
        
        if (!success) {
            lastServerInfo = null
        }
        
        // Update StateFlows for UI
        _isReconnecting.value = false
        _reconnectAttemptCount.value = 0
        
        updateNotification()
    }
    
    fun disconnect() {
        // Stop any reconnection attempts
        stopReconnecting()
        
        // Mark this as a user-initiated disconnect
        userDisconnected = true
        lastServerInfo = null
        
        // Restore audio mode to normal
        audioManager?.mode = android.media.AudioManager.MODE_NORMAL
        android.util.Log.i("MumbleService", "Audio mode restored to MODE_NORMAL (manual disconnect)")
        
        // Turn off speakerphone if it was on
        audioManager?.isSpeakerphoneOn = false
        
        // Restore original volume
        restoreOriginalVolume()
        
        try {
            humlaService?.disconnect()
        } catch (e: Exception) {
            // Service may not be connected
        }
        _connectionState.value = ConnectionState.DISCONNECTED
        _currentServer.value = null
        _currentUser.value = null
        _channels.value = emptyList()
        _users.value = emptyList()
    }
    
    /**
     * Set speakerphone mode (luidspreker vs oortje)
     * @param enabled true = speaker (luidspreker), false = earpiece (oortje)
     */
    fun setSpeakerphoneMode(enabled: Boolean) {
        audioManager?.let { am ->
            am.isSpeakerphoneOn = enabled
            Log.i(TAG, "Speakerphone mode: ${if (enabled) "ON (luidspreker)" else "OFF (oortje)"}")
        }
    }
    
    /**
     * Completely shutdown the service
     */
    fun shutdown() {
        disconnect()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }
    
    fun sendChannelMessage(message: String) {
        try {
            humlaSession?.let { session ->
                val sessionUser = session.sessionUser
                val channelId = sessionUser?.channel?.id ?: return
                
                // Send the message to the server
                session.sendChannelTextMessage(channelId, message, false)
                
                // Immediately add the message to chat so it appears right away
                serviceScope.launch(Dispatchers.Main) {
                    val chatMessage = ChatMessage(
                        sender = sessionUser.name ?: "You",
                        message = message,
                        timestamp = System.currentTimeMillis()
                    )
                    _chatMessages.value = _chatMessages.value + chatMessage
                }
            }
        } catch (e: Exception) {
            // Handle error
            Log.e(TAG, "Error sending channel message", e)
        }
    }
    
    fun setMuted(muted: Boolean) {
        try {
            humlaSession?.let { session ->
                session.setSelfMuteDeafState(muted, _currentUser.value?.isSelfDeafened ?: false)
            }
        } catch (e: Exception) {
            // Handle error
        }
    }
    
    fun setDeafened(deafened: Boolean) {
        try {
            humlaSession?.let { session ->
                // Deafening automatically mutes
                session.setSelfMuteDeafState(deafened, deafened)
            }
        } catch (e: Exception) {
            // Handle error
        }
    }
    
    /**
     * Register the current user with the server.
     * This makes the username permanent on this server.
     * Requires a valid client certificate and proper server permissions.
     * 
     * Note: Registration is performed by sending a UserState message with user_id=0.
     * The server will respond with a UserState message containing the actual user_id if successful.
     */
    fun registerUser() {
        try {
            if (humlaService == null) {
                android.util.Log.w("MumbleService", "Cannot register: Not connected to server")
                showToast("Not connected to server")
                return
            }
            
            if (humlaSession == null) {
                android.util.Log.w("MumbleService", "Cannot register: Session not established")
                showToast("Session not established")
                return
            }
            
            humlaSession?.let { session ->
                val sessionUser = session.sessionUser
                if (sessionUser == null) {
                    android.util.Log.w("MumbleService", "Cannot register: User not found in session")
                    showToast("User not found in session")
                    return
                }
                
                // Check if already registered
                if (sessionUser.userId >= 0) {
                    android.util.Log.i("MumbleService", "User is already registered with ID: ${sessionUser.userId}")
                    showToast("User '${sessionUser.name}' is already registered")
                    return
                }
                
                val sessionId = sessionUser.session
                android.util.Log.i("MumbleService", "Attempting registration - Username: '${sessionUser.name}', Session: $sessionId")
                android.util.Log.d("MumbleService", "Certificate path check: '${lastServerInfo?.clientCertificatePath}'")
                
                // Check if we have a client certificate (REQUIRED for registration on most servers)
                val hasCertificate = lastServerInfo?.clientCertificatePath?.isNotEmpty() == true
                if (!hasCertificate) {
                    android.util.Log.e("MumbleService", "Registration failed: No client certificate configured (path is null or empty)")
                    showToast("❌ Registration requires a client certificate!\n\nPlease:\n1. Generate or import a certificate\n2. Reconnect to the server\n3. Try registering again")
                    return
                }
                
                // Cast to HumlaService to access registerUser method
                val humlaServiceInstance = humlaService as? HumlaService
                if (humlaServiceInstance == null) {
                    android.util.Log.e("MumbleService", "Cannot register: Failed to access Humla service")
                    showToast("Internal error: Service not available")
                    return
                }
                
                // Send registration request (UserState with user_id=0)
                humlaServiceInstance.registerUser(sessionId)
                android.util.Log.i("MumbleService", "Registration request sent to server. Waiting for server response...")
                showToast("📤 Registration request sent...\n(Certificate: ✓)")
            }
        } catch (e: Exception) {
            android.util.Log.e("MumbleService", "Error registering user", e)
            showToast("Error registering user: ${e.message}")
        }
    }
    
    private fun showToast(message: String) {
        serviceScope.launch(Dispatchers.Main) {
            Toast.makeText(this@MumbleService, message, Toast.LENGTH_LONG).show()
        }
    }
    
    fun joinChannel(channelId: Int) {
        try {
            humlaSession?.let { session ->
                android.util.Log.d("MumbleService", "Joining channel: $channelId")
                session.joinChannel(channelId)
            }
        } catch (e: Exception) {
            android.util.Log.e("MumbleService", "Error joining channel", e)
        }
    }
    
    // Audio recording control for PTT
    fun startRecording() {
        android.util.Log.d("MumbleService", "startRecording() called, session=$humlaSession")
        try {
            // Play VOX pre-tone to trigger external VOX before transmission starts
            // This ensures the VOX circuit is already open when speech begins
            voxPreToneGenerator?.playPreToneAsync(_audioSettings.value.voxPreTone)
            
            // NOTE: Serial PTT is NOT used for our own transmission!
            // It's only used when OTHERS are talking (see onUserTalkStateUpdated)
            
            humlaSession?.setTalkingState(true)
            android.util.Log.i("MumbleService", "Set talking state to TRUE")
        } catch (e: Exception) {
            android.util.Log.e("MumbleService", "Failed to start recording", e)
        }
    }
    
    fun stopRecording() {
        android.util.Log.d("MumbleService", "stopRecording() called, session=$humlaSession")
        try {
            // Check if we're already transmitting a roger beep
            if (isTransmittingRogerBeep) {
                android.util.Log.w("MumbleService", "Already transmitting roger beep, ignoring stopRecording() call")
                return
            }
            
            // Check if we should play roger beeps in PTT mode
            val currentSettings = _audioSettings.value
            val shouldPlayMumbleRogerBeep = currentSettings.transmissionMode == TransmissionMode.PUSH_TO_TALK 
                && currentSettings.mumbleRogerBeep.enabled
            val shouldPlayTransmitterRogerBeep = currentSettings.transmissionMode == TransmissionMode.PUSH_TO_TALK
                && currentSettings.rogerBeep.enabled
            
            if (shouldPlayMumbleRogerBeep) {
                android.util.Log.i("MumbleService", "PTT mode - stopping talking, then will play Mumble roger beep")
                
                // First, stop talking immediately
                humlaSession?.setTalkingState(false)
                android.util.Log.i("MumbleService", "Set talking state to FALSE")
                
                // Then schedule the roger beep with a small delay (like VAC mode does)
                // This prevents the roger beep from interfering with the talking state change
                serviceScope.launch {
                    // Small delay to ensure talking state has been processed
                    delay(50)
                    
                    // Now play the roger beep (it will manage talking state itself)
                    android.util.Log.i("MumbleService", "Playing Mumble roger beep after PTT release")
                    playMumbleRogerBeep() // Use default manageTalkingState=true like VAC mode
                }
            } else {
                // No Mumble roger beep, just stop talking immediately
                humlaSession?.setTalkingState(false)
                android.util.Log.i("MumbleService", "Set talking state to FALSE (no roger beep)")
            }
            
            // Also play local transmitter roger beep (independent of Mumble roger beep)
            if (shouldPlayTransmitterRogerBeep) {
                android.util.Log.i("MumbleService", "Playing local transmitter roger beep after PTT release")
                rogerBeepGenerator?.playRogerBeep()
            }
            
            // NOTE: Serial PTT is NOT used for our own transmission!
            // It's only used when OTHERS are talking (see onUserTalkStateUpdated)
        } catch (e: Exception) {
            android.util.Log.e("MumbleService", "Failed to stop recording", e)
        }
    }
    
    /**
     * Helper function to apply gain settings - uses same direct access as VAD settings
     */
    private fun applyGainSettings(settings: AudioSettings) {
        try {
            humlaService?.let { service ->
                if (service is HumlaService) {
                    service.setInputGain(settings.inputGain)
                    service.setOutputGain(settings.outputGain)
                    service.setMicBoost(settings.micBoost)
                }
            }
        } catch (e: Exception) {
            android.util.Log.w("MumbleService", "Failed to apply gain settings", e)
        }
    }
    
    fun updateAudioSettings(settings: AudioSettings) {
        android.util.Log.d("MumbleService", "Updating audio settings - inputGain=${settings.inputGain}, outputGain=${settings.outputGain}")
        android.util.Log.d("MumbleService", "Mumble roger beep settings - enabled: ${settings.mumbleRogerBeep.enabled}, style: ${settings.mumbleRogerBeep.style}, volume: ${settings.mumbleRogerBeep.volume}")
        _audioSettings.value = settings
        
        // Save settings to SharedPreferences
        saveAudioSettings(settings)
        
        try {
            // Update roger beep settings
            rogerBeepGenerator?.updateSettings(
                RogerBeepGenerator.RogerBeepSettings(
                    enabled = settings.rogerBeep.enabled,
                    style = settings.rogerBeep.style,
                    volume = settings.rogerBeep.volume,
                    toneDurationMs = settings.rogerBeep.toneDurationMs,
                    customAudioPath = settings.rogerBeep.customAudioPath
                )
            )
            
            // Update mumble roger beep settings
            mumbleRogerBeepGenerator?.updateSettings(settings.mumbleRogerBeep)
            android.util.Log.d("MumbleService", "Called mumbleRogerBeepGenerator.updateSettings()")
            
            // Update input and output gain - use same pattern as VAD settings
            // Apply directly via HumlaService (like VAD), not via session
            humlaService?.let { service ->
                if (service is HumlaService) {
                    service.setInputGain(settings.inputGain)
                    service.setOutputGain(settings.outputGain)
                    service.setMicBoost(settings.micBoost)
                    val boostStatus = if (settings.micBoost) " [BOOST ON]" else ""
                    android.util.Log.i("MumbleService", "✓ Gain set - Input: ${settings.inputGain}x$boostStatus, Output: ${settings.outputGain}x")
                } else {
                    android.util.Log.w("MumbleService", "humlaService is not instance of HumlaService")
                }
            } ?: android.util.Log.w("MumbleService", "humlaService is null, cannot set gain")
            
            // Update VAD settings and transmit mode via direct cast to HumlaService
            humlaService?.let { service ->
                if (service is HumlaService) {
                    // Update VAD parameters
                    service.setVADThreshold(settings.vadThreshold)
                    service.setVoiceHoldTime(settings.voiceHoldTime)
                    
                    // Update transmit mode
                    val transmitMode = when (settings.transmissionMode) {
                        TransmissionMode.PUSH_TO_TALK -> Constants.TRANSMIT_PUSH_TO_TALK
                        TransmissionMode.VOICE_ACTIVITY -> Constants.TRANSMIT_VOICE_ACTIVITY
                        TransmissionMode.CONTINUOUS -> Constants.TRANSMIT_CONTINUOUS
                    }
                    service.setTransmitMode(transmitMode)
                    
                    android.util.Log.i("MumbleService", "Updated audio settings - Mode: ${settings.transmissionMode}, VAD: ${settings.vadThreshold}, Hold: ${settings.voiceHoldTime}ms")
                }
            }
            
            // Apply audio device routing settings
            applyAudioDeviceSettings(settings.audioDevices)
            
            humlaSession?.let { session ->
                // Update talking state based on mode
                when (settings.transmissionMode) {
                    TransmissionMode.CONTINUOUS -> {
                        // Continuous: Always transmit
                        session.setTalkingState(true)
                        android.util.Log.i("MumbleService", "Set continuous transmission mode - always transmitting")
                    }
                    TransmissionMode.PUSH_TO_TALK -> {
                        // PTT: Only transmit when button pressed (controlled by startRecording/stopRecording)
                        session.setTalkingState(false)
                        android.util.Log.i("MumbleService", "Set push-to-talk mode - waiting for PTT button")
                    }
                    TransmissionMode.VOICE_ACTIVITY -> {
                        // VAD: Let Humla control transmission based on audio input
                        // DO NOT call setTalkingState - let the ActivityInputMode decide
                        android.util.Log.i("MumbleService", "Set voice activity mode - automatic detection active")
                    }
                }
            }
            
            // CRITICAL: Re-apply gain settings AFTER transmit mode change
            // because setTransmitMode reinitializes AudioHandler with default gain
            humlaService?.let { service ->
                if (service is HumlaService) {
                    service.setInputGain(settings.inputGain)
                    service.setOutputGain(settings.outputGain)
                    service.setMicBoost(settings.micBoost)
                    val boostStatus = if (settings.micBoost) " [BOOST ON]" else ""
                    android.util.Log.i("MumbleService", "✓ Re-applied gain after mode change - Input: ${settings.inputGain}x$boostStatus, Output: ${settings.outputGain}x")
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("MumbleService", "Failed to update audio settings", e)
        }
    }
    
    // Auto-join channel by name or path
    private fun autoJoinChannel(channelNameOrPath: String) {
        try {
            android.util.Log.d("MumbleService", "autoJoinChannel called with: $channelNameOrPath")
            
            humlaSession?.let { session ->
                android.util.Log.d("MumbleService", "Session found, getting root channel...")
                val rootChannel = session.rootChannel
                
                if (rootChannel == null) {
                    android.util.Log.w("MumbleService", "Root channel is null!")
                    return
                }
                
                android.util.Log.d("MumbleService", "Root channel: ${rootChannel.name}, subchannels: ${rootChannel.subchannels?.size ?: 0}")
                
                // Check if it's a path (contains "/")
                val channel = if (channelNameOrPath.contains("/")) {
                    // Path format: "Root/SubChannel1/SubChannel2"
                    android.util.Log.d("MumbleService", "Searching by path: $channelNameOrPath")
                    findChannelByPath(rootChannel, channelNameOrPath)
                } else {
                    // Simple name: search all channels
                    android.util.Log.d("MumbleService", "Searching by name: $channelNameOrPath")
                    findChannelByName(rootChannel, channelNameOrPath)
                }
                
                if (channel != null) {
                    android.util.Log.i("MumbleService", "Found channel: ${channel.name} (ID: ${channel.id}), joining...")
                    session.joinChannel(channel.id)
                    android.util.Log.i("MumbleService", "Auto-joined channel: ${channel.name}")
                } else {
                    android.util.Log.w("MumbleService", "Auto-join failed: Channel '$channelNameOrPath' not found")
                    // Log all available channels for debugging
                    val allChannels = getAllChannelsRecursive(rootChannel)
                    android.util.Log.w("MumbleService", "Available channels: ${allChannels.map { it.name }}")
                }
            } ?: run {
                android.util.Log.w("MumbleService", "Session is null, cannot auto-join channel")
            }
        } catch (e: Exception) {
            android.util.Log.e("MumbleService", "Error auto-joining channel", e)
        }
    }
    
    // Helper to get all channels for debugging
    private fun getAllChannelsRecursive(channel: IChannel): List<IChannel> {
        val channels = mutableListOf(channel)
        channel.subchannels?.forEach { subChannel ->
            channels.addAll(getAllChannelsRecursive(subChannel))
        }
        return channels
    }
    
    // Find channel by path (e.g., "Root/SubChannel1/SubChannel2")
    private fun findChannelByPath(rootChannel: IChannel, path: String): IChannel? {
        val parts = path.split("/").map { it.trim() }.filter { it.isNotEmpty() }
        if (parts.isEmpty()) return null
        
        var currentChannel = rootChannel
        
        // Start from index 0 or 1 depending on whether first part is "Root"
        val startIndex = if (parts[0].equals("Root", ignoreCase = true)) 1 else 0
        
        for (i in startIndex until parts.size) {
            val partName = parts[i]
            currentChannel = currentChannel.subchannels?.find { 
                it.name.equals(partName, ignoreCase = true) 
            } ?: return null
        }
        
        return currentChannel
    }
    
    // Find channel by name (searches all channels recursively)
    private fun findChannelByName(channel: IChannel, name: String): IChannel? {
        android.util.Log.d("MumbleService", "Checking channel: '${channel.name}' against '$name'")
        
        // Check current channel
        if (channel.name.equals(name, ignoreCase = true)) {
            android.util.Log.d("MumbleService", "Found match: ${channel.name}")
            return channel
        }
        
        // Search subchannels recursively
        channel.subchannels?.forEach { subChannel ->
            val found = findChannelByName(subChannel, name)
            if (found != null) return found
        }
        
        return null
    }
    
    // Helper methods to convert Humla models to our models
    private fun updateChannels() {
        try {
            humlaSession?.let { session ->
                val rootChannel = session.rootChannel
                if (rootChannel != null) {
                    val channels = getAllChannels(rootChannel)
                    _channels.value = channels.map { convertChannel(it) }
                }
            }
        } catch (e: Exception) {
            // Handle error
        }
    }
    
    private fun getAllChannels(channel: IChannel): List<IChannel> {
        val channels = mutableListOf(channel)
        channel.subchannels?.forEach { subChannel ->
            channels.addAll(getAllChannels(subChannel))
        }
        return channels
    }
    
    private fun convertChannel(humlaChannel: IChannel): Channel {
        return Channel(
            id = humlaChannel.id,
            name = humlaChannel.name ?: "Unknown",
            description = humlaChannel.description ?: "",
            parentId = humlaChannel.parent?.id
        )
    }
    
    private fun updateUsers() {
        try {
            humlaSession?.let { session ->
                // Collect all users from all channels
                val rootChannel = session.rootChannel
                if (rootChannel != null) {
                    val allUsers = mutableListOf<IUser>()
                    collectUsersFromChannel(rootChannel, allUsers)
                    _users.value = allUsers.map { convertUser(it) }
                }
            }
        } catch (e: Exception) {
            // Handle error
        }
    }
    
    private fun collectUsersFromChannel(channel: IChannel, users: MutableList<IUser>) {
        channel.users?.forEach { user ->
            users.add(user)
        }
        channel.subchannels?.forEach { subChannel ->
            collectUsersFromChannel(subChannel, users)
        }
    }
    
    private fun convertUser(humlaUser: IUser): User {
        return User(
            id = humlaUser.session,
            name = humlaUser.name ?: "Unknown",
            channelId = humlaUser.channel?.id ?: 0,
            isMuted = humlaUser.isMuted,
            isDeafened = humlaUser.isDeafened,
            isSelfMuted = humlaUser.isSelfMuted,
            isSelfDeafened = humlaUser.isSelfDeafened,
            isSpeaking = humlaUser.talkState?.name == "TALKING",
            userId = humlaUser.userId  // -1 if not registered, >= 0 if registered
        )
    }
    
    private fun createNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent, 
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        // Determine status text
        val status = if (isReconnecting) {
            if (!isNetworkAvailable) {
                "Waiting for network... ($reconnectAttempts/$maxReconnectAttempts)"
            } else {
                "Reconnecting... ($reconnectAttempts/$maxReconnectAttempts)"
            }
        } else {
            when (_connectionState.value) {
                ConnectionState.CONNECTED -> "Connected via Humla"
                ConnectionState.AUTHENTICATED -> "Authenticated via Humla"
                ConnectionState.CONNECTING -> "Connecting via Humla..."
                ConnectionState.DISCONNECTING -> "Disconnecting..."
                ConnectionState.ERROR -> "Connection Error"
                ConnectionState.DISCONNECTED -> "Disconnected"
            }
        }
        
        val serverName = _currentServer.value?.name ?: lastServerInfo?.name ?: "Unknown Server"
        
        val builder = NotificationCompat.Builder(this, MumbleApplication.NOTIFICATION_CHANNEL_ID)
            .setContentTitle("HamMumble (Humla-powered)")
            .setContentText("$status - $serverName")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
        
        // Add "Stop Reconnecting" action if currently reconnecting
        if (isReconnecting) {
            val stopReconnectIntent = Intent(this, MumbleService::class.java).apply {
                action = "STOP_RECONNECTING"
            }
            val stopReconnectPendingIntent = PendingIntent.getService(
                this, 1, stopReconnectIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Stop Reconnecting",
                stopReconnectPendingIntent
            )
        }
        
        return builder.build()
    }
    
    /**
     * Update the foreground notification
     */
    private fun updateNotification() {
        val notification = createNotification()
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        notificationManager.notify(MumbleApplication.NOTIFICATION_ID, notification)
    }
    
    // === Serial PTT Management Functions ===
    
    /**
     * Get list of available USB serial devices
     */
    fun getAvailableSerialDevices() = serialPttManager?.getAvailableDevices() ?: emptyList()
    
    /**
     * Get device info string
     */
    fun getSerialDeviceInfo(device: android.hardware.usb.UsbDevice) = 
        serialPttManager?.getDeviceInfo(device) ?: "Unknown Device"
    
    /**
     * Connect to serial PTT device
     */
    fun connectSerialPtt(device: android.hardware.usb.UsbDevice, settings: SerialPttSettings): Boolean {
        return serialPttManager?.connect(device, settings) ?: false
    }
    
    /**
     * Disconnect serial PTT
     */
    fun disconnectSerialPtt() {
        serialPttManager?.disconnect()
    }
    
    /**
     * Test serial PTT
     */
    fun testSerialPtt(durationMs: Long = 500) {
        serialPttManager?.testPtt(durationMs)
    }
    
    /**
     * Check if serial PTT is connected
     */
    fun isSerialPttConnected() = serialPttManager?.isConnected() ?: false
    
    // ========== Audio Device Management ==========
    
    /**
     * Get all available input devices (microphones)
     */
    fun getAvailableInputDevices(): List<AudioDeviceManager.AudioDevice> {
        return audioDeviceManager?.getInputDevices() ?: emptyList()
    }
    
    /**
     * Get all available output devices (speakers)
     */
    fun getAvailableOutputDevices(): List<AudioDeviceManager.AudioDevice> {
        return audioDeviceManager?.getOutputDevices() ?: emptyList()
    }
    
    /**
     * Apply audio device settings for separate TX/RX routing
     */
    fun applyAudioDeviceSettings(deviceSettings: AudioDeviceSettings) {
        val humlaService = humlaService ?: run {
            android.util.Log.w("MumbleService", "Cannot apply audio device settings: HumlaService not available")
            return
        }
        
        if (humlaService !is HumlaService) {
            android.util.Log.w("MumbleService", "Cannot apply audio device settings: Service is not HumlaService instance")
            return
        }
        
        android.util.Log.i("MumbleService", "Applying audio device settings - TX: ${deviceSettings.txDeviceId}, RX: ${deviceSettings.rxDeviceId}")
        
        // Set TX device (input)
        if (deviceSettings.txDeviceId >= 0) {
            val txDeviceInfo = audioDeviceManager?.getAudioDeviceInfo(deviceSettings.txDeviceId, isInput = true)
            if (txDeviceInfo != null) {
                val success = humlaService.setPreferredInputDevice(txDeviceInfo)
                if (success) {
                    android.util.Log.i("MumbleService", "TX device set successfully: ${deviceSettings.txDeviceName}")
                } else {
                    android.util.Log.w("MumbleService", "Failed to set TX device")
                }
            } else {
                android.util.Log.w("MumbleService", "TX device not found: ${deviceSettings.txDeviceId}")
            }
        } else {
            // Reset to default
            humlaService.setPreferredInputDevice(null)
            android.util.Log.i("MumbleService", "TX device reset to system default")
        }
        
        // Set RX device (output)
        if (deviceSettings.rxDeviceId >= 0) {
            val rxDeviceInfo = audioDeviceManager?.getAudioDeviceInfo(deviceSettings.rxDeviceId, isInput = false)
            if (rxDeviceInfo != null) {
                val success = humlaService.setPreferredOutputDevice(rxDeviceInfo)
                if (success) {
                    android.util.Log.i("MumbleService", "RX device set successfully: ${deviceSettings.rxDeviceName}")
                } else {
                    android.util.Log.w("MumbleService", "Failed to set RX device")
                }
            } else {
                android.util.Log.w("MumbleService", "RX device not found: ${deviceSettings.rxDeviceId}")
            }
        } else {
            // Reset to default
            humlaService.setPreferredOutputDevice(null)
            android.util.Log.i("MumbleService", "RX device reset to system default")
        }
        
        // Log all available devices for debugging
        audioDeviceManager?.logAllDevices()
        
        // Log currently routed devices
        val currentTxDevice = humlaService.getRoutedInputDevice()
        val currentRxDevice = humlaService.getRoutedOutputDevice()
        android.util.Log.i("MumbleService", "Currently routed TX device: ${currentTxDevice?.productName ?: "Unknown"} (Type: ${currentTxDevice?.type})")
        android.util.Log.i("MumbleService", "Currently routed RX device: ${currentRxDevice?.productName ?: "Unknown"} (Type: ${currentRxDevice?.type})")
    }
    
    /**
     * Get recommended separate devices for TX and RX (standard mode)
     */
    fun getRecommendedSeparateDevices(): Pair<AudioDeviceManager.AudioDevice?, AudioDeviceManager.AudioDevice?> {
        return audioDeviceManager?.getRecommendedSeparateDevices() ?: Pair(null, null)
    }
    
    /**
     * Get recommended configuration for gateway/crossband mode
     * TX: Built-in mic → Mumble → USB OUTPUT → Radio
     * RX: Radio → USB INPUT → Mumble → Built-in speaker
     */
    fun getRecommendedGatewayDevices(): Pair<AudioDeviceManager.AudioDevice?, AudioDeviceManager.AudioDevice?> {
        return audioDeviceManager?.getRecommendedGatewayDevices() ?: Pair(null, null)
    }
    
    /**
     * Get recommended configuration for full USB mode
     * Both TX and RX via USB device
     */
    fun getRecommendedFullUSBDevices(): Pair<AudioDeviceManager.AudioDevice?, AudioDeviceManager.AudioDevice?> {
        return audioDeviceManager?.getRecommendedFullUSBDevices() ?: Pair(null, null)
    }
    
    /**
     * Check if USB audio devices are connected
     */
    fun hasUSBDevices(): Boolean {
        return audioDeviceManager?.hasUSBDevices() ?: false
    }
    
    // ========== Volume Management ==========
    
    /**
     * Set system volume to maximum for VOICE_CALL stream (used by VoIP apps)
     */
    private fun setVolumeToMaximum() {
        try {
            audioManager?.let { am ->
                // Save original volume before changing
                if (originalVolume == -1) {
                    originalVolume = am.getStreamVolume(android.media.AudioManager.STREAM_VOICE_CALL)
                }
                
                // Get max volume and set it for STREAM_VOICE_CALL (proper VoIP stream)
                val maxVolume = am.getStreamMaxVolume(android.media.AudioManager.STREAM_VOICE_CALL)
                am.setStreamVolume(
                    android.media.AudioManager.STREAM_VOICE_CALL,
                    maxVolume,
                    android.media.AudioManager.FLAG_SHOW_UI  // Show volume UI so user sees the change
                )
                
                volumeWasMaxed = true
                android.util.Log.i("MumbleService", "System volume (VOICE_CALL) set to maximum: $maxVolume (was: $originalVolume)")
            }
        } catch (e: Exception) {
            android.util.Log.e("MumbleService", "Failed to set volume to maximum", e)
        }
    }
    
    /**
     * Restore original system volume
     */
    private fun restoreOriginalVolume() {
        try {
            if (volumeWasMaxed && originalVolume != -1) {
                audioManager?.setStreamVolume(
                    android.media.AudioManager.STREAM_VOICE_CALL,
                    originalVolume,
                    0  // No UI flag when restoring
                )
                android.util.Log.i("MumbleService", "System volume (VOICE_CALL) restored to: $originalVolume")
                volumeWasMaxed = false
                originalVolume = -1
            }
        } catch (e: Exception) {
            android.util.Log.e("MumbleService", "Failed to restore original volume", e)
        }
    }
    
    // === Audio Settings Persistence ===
    
    private fun loadAudioSettings() {
        try {
            val prefs = getSharedPreferences("hammumble_settings", Context.MODE_PRIVATE)
            val settingsJson = prefs.getString("audio_settings", null)
            
            if (settingsJson != null) {
                val jsonObj = org.json.JSONObject(settingsJson)
                val loadedSettings = AudioSettings.fromJson(jsonObj)
                _audioSettings.value = loadedSettings
                Log.i("MumbleService", "Audio settings loaded from SharedPreferences")
            }
        } catch (e: Exception) {
            Log.e("MumbleService", "Failed to load audio settings", e)
            // If loading fails, keep default settings
        }
    }
    
    private fun saveAudioSettings(settings: AudioSettings) {
        try {
            val prefs = getSharedPreferences("hammumble_settings", Context.MODE_PRIVATE)
            val jsonString = settings.toJson().toString()
            prefs.edit().putString("audio_settings", jsonString).apply()
            Log.i("MumbleService", "Audio settings saved to SharedPreferences")
        } catch (e: Exception) {
            Log.e("MumbleService", "Failed to save audio settings", e)
        }
    }
}

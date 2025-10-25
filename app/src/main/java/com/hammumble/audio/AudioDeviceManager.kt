package com.hammumble.audio

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.util.Log

/**
 * Manages audio device detection and routing for separate TX/RX paths.
 * Allows explicit selection of different audio devices for transmission (input)
 * and reception (output) to prevent audio loopback when using USB audio interfaces.
 */
class AudioDeviceManager(private val context: Context) {
    
    private val audioManager: AudioManager = 
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    
    companion object {
        private const val TAG = "AudioDeviceManager"
    }
    
    data class AudioDevice(
        val id: Int,
        val name: String,
        val type: Int,
        val isSource: Boolean, // true for input devices (mic), false for output devices (speaker)
        val productName: String = ""
    ) {
        fun getTypeString(): String {
            return when (type) {
                AudioDeviceInfo.TYPE_BUILTIN_MIC -> "Built-in Microphone"
                AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> "Built-in Speaker"
                AudioDeviceInfo.TYPE_WIRED_HEADSET -> "Wired Headset"
                AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> "Wired Headphones"
                AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> "Bluetooth SCO"
                AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> "Bluetooth A2DP"
                AudioDeviceInfo.TYPE_USB_DEVICE -> "USB Audio Device"
                AudioDeviceInfo.TYPE_USB_HEADSET -> "USB Headset"
                AudioDeviceInfo.TYPE_USB_ACCESSORY -> "USB Accessory"
                else -> "Unknown Device (Type: $type)"
            }
        }
        
        fun isUSB(): Boolean {
            return type == AudioDeviceInfo.TYPE_USB_DEVICE ||
                   type == AudioDeviceInfo.TYPE_USB_HEADSET ||
                   type == AudioDeviceInfo.TYPE_USB_ACCESSORY
        }
        
        fun isBluetooth(): Boolean {
            return type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                   type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP
        }
        
        fun getDisplayName(): String {
            return if (productName.isNotEmpty() && productName != name) {
                "$productName (${getTypeString()})"
            } else {
                "${getTypeString()} - $name"
            }
        }
    }
    
    /**
     * Get all available input devices (microphones)
     */
    fun getInputDevices(): List<AudioDevice> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            Log.w(TAG, "Audio device enumeration requires Android 6.0+")
            return emptyList()
        }
        
        val devices = mutableListOf<AudioDevice>()
        
        try {
            val deviceInfos = audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS)
            
            for (deviceInfo in deviceInfos) {
                if (deviceInfo.isSink) continue // Skip output devices
                
                val device = AudioDevice(
                    id = deviceInfo.id,
                    name = deviceInfo.productName?.toString() ?: "Unknown",
                    type = deviceInfo.type,
                    isSource = true,
                    productName = deviceInfo.productName?.toString() ?: ""
                )
                
                devices.add(device)
                Log.d(TAG, "Found input device: ${device.getDisplayName()} (ID: ${device.id})")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error enumerating input devices", e)
        }
        
        return devices
    }
    
    /**
     * Get all available output devices (speakers)
     */
    fun getOutputDevices(): List<AudioDevice> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            Log.w(TAG, "Audio device enumeration requires Android 6.0+")
            return emptyList()
        }
        
        val devices = mutableListOf<AudioDevice>()
        
        try {
            val deviceInfos = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            
            for (deviceInfo in deviceInfos) {
                if (deviceInfo.isSource) continue // Skip input devices
                
                val device = AudioDevice(
                    id = deviceInfo.id,
                    name = deviceInfo.productName?.toString() ?: "Unknown",
                    type = deviceInfo.type,
                    isSource = false,
                    productName = deviceInfo.productName?.toString() ?: ""
                )
                
                devices.add(device)
                Log.d(TAG, "Found output device: ${device.getDisplayName()} (ID: ${device.id})")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error enumerating output devices", e)
        }
        
        return devices
    }
    
    /**
     * Get a specific input device by ID
     */
    fun getInputDeviceById(deviceId: Int): AudioDevice? {
        return getInputDevices().find { it.id == deviceId }
    }
    
    /**
     * Get a specific output device by ID
     */
    fun getOutputDeviceById(deviceId: Int): AudioDevice? {
        return getOutputDevices().find { it.id == deviceId }
    }
    
    /**
     * Get AudioDeviceInfo for a specific device ID (needed for setPreferredDevice)
     */
    fun getAudioDeviceInfo(deviceId: Int, isInput: Boolean): AudioDeviceInfo? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return null
        }
        
        try {
            val flag = if (isInput) AudioManager.GET_DEVICES_INPUTS else AudioManager.GET_DEVICES_OUTPUTS
            val devices = audioManager.getDevices(flag)
            return devices.find { it.id == deviceId }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting AudioDeviceInfo for device $deviceId", e)
            return null
        }
    }
    
    /**
     * Check if USB audio devices are connected
     */
    fun hasUSBDevices(): Boolean {
        val inputDevices = getInputDevices()
        val outputDevices = getOutputDevices()
        
        return inputDevices.any { it.isUSB() } || outputDevices.any { it.isUSB() }
    }
    
    /**
     * Get recommended separate devices for TX and RX
     * Mode 1: Standard mode - USB for TX input, Built-in speaker for RX (prevents loopback)
     * Mode 2: Gateway mode - Built-in mic for TX, USB output for RX (crossband/repeater)
     * Mode 3: Full USB mode - USB for both TX and RX (requires stereo or dual device)
     */
    fun getRecommendedSeparateDevices(): Pair<AudioDevice?, AudioDevice?> {
        val inputDevices = getInputDevices()
        val outputDevices = getOutputDevices()
        
        // Standard mode: USB input for TX, built-in speaker for RX
        val txDevice = inputDevices.find { it.isUSB() }
            ?: inputDevices.find { it.type == AudioDeviceInfo.TYPE_BUILTIN_MIC }
        
        val rxDevice = outputDevices.find { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
            ?: outputDevices.find { !it.isUSB() }
        
        Log.i(TAG, "Recommended TX device: ${txDevice?.getDisplayName() ?: "None"}")
        Log.i(TAG, "Recommended RX device: ${rxDevice?.getDisplayName() ?: "None"}")
        
        return Pair(txDevice, rxDevice)
    }
    
    /**
     * Get recommended configuration for gateway/crossband mode
     * TX: Built-in mic (for talking into tablet) → Mumble → USB OUTPUT → Radio
     * RX: Radio → USB INPUT → Mumble → Built-in speaker (for hearing)
     */
    fun getRecommendedGatewayDevices(): Pair<AudioDevice?, AudioDevice?> {
        val inputDevices = getInputDevices()
        val outputDevices = getOutputDevices()
        
        // TX: Built-in mic (you talk into tablet)
        val txDevice = inputDevices.find { it.type == AudioDeviceInfo.TYPE_BUILTIN_MIC }
            ?: inputDevices.find { !it.isUSB() }
        
        // RX: USB output (goes to radio)
        val rxDevice = outputDevices.find { it.isUSB() }
            ?: outputDevices.find { it.type == AudioDeviceInfo.TYPE_USB_DEVICE }
        
        Log.i(TAG, "Gateway TX device (your mic): ${txDevice?.getDisplayName() ?: "None"}")
        Log.i(TAG, "Gateway RX device (to radio): ${rxDevice?.getDisplayName() ?: "None"}")
        
        return Pair(txDevice, rxDevice)
    }
    
    /**
     * Get recommended configuration for full USB audio mode
     * Both TX and RX via USB (requires proper device with multiple channels)
     */
    fun getRecommendedFullUSBDevices(): Pair<AudioDevice?, AudioDevice?> {
        val inputDevices = getInputDevices()
        val outputDevices = getOutputDevices()
        
        // TX: USB input
        val txDevice = inputDevices.find { it.isUSB() }
        
        // RX: USB output
        val rxDevice = outputDevices.find { it.isUSB() }
        
        Log.i(TAG, "Full USB TX device: ${txDevice?.getDisplayName() ?: "None"}")
        Log.i(TAG, "Full USB RX device: ${rxDevice?.getDisplayName() ?: "None"}")
        
        return Pair(txDevice, rxDevice)
    }
    
    /**
     * Log all available audio devices for debugging
     */
    fun logAllDevices() {
        Log.i(TAG, "=== Available Input Devices ===")
        getInputDevices().forEach { device ->
            Log.i(TAG, "  ${device.getDisplayName()} [ID: ${device.id}]")
        }
        
        Log.i(TAG, "=== Available Output Devices ===")
        getOutputDevices().forEach { device ->
            Log.i(TAG, "  ${device.getDisplayName()} [ID: ${device.id}]")
        }
    }
}

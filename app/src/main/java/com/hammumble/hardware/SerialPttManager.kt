package com.hammumble.hardware

import android.content.Context
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.util.Log
import com.hammumble.data.SerialPttPin
import com.hammumble.data.SerialPttSettings
import com.hoho.android.usbserial.driver.UsbSerialDriver
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.IOException

/**
 * SerialPttManager - Hardware PTT control via USB Serial
 * 
 * Supports common USB-Serial adapters:
 * - FTDI (FT232, FT2232, etc.)
 * - CP210x (CP2102, CP2104, etc.)
 * - CH340/CH341
 * - PL2303
 * - CDC ACM
 * 
 * Uses RTS or DTR pins for PTT control:
 * - HIGH = PTT OFF (receive)
 * - LOW = PTT ON (transmit)
 */
class SerialPttManager(private val context: Context) {
    
    private var serialPort: UsbSerialPort? = null
    private var settings: SerialPttSettings = SerialPttSettings()
    private var isPttActive = false
    private val scope = CoroutineScope(Dispatchers.IO)
    
    companion object {
        private const val TAG = "SerialPttManager"
        
        // Standard baud rates for serial PTT
        val BAUD_RATES = listOf(1200, 2400, 4800, 9600, 19200, 38400, 57600, 115200)
    }
    
    /**
     * Get list of available USB serial devices
     */
    fun getAvailableDevices(): List<UsbDevice> {
        val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
        val availableDrivers = UsbSerialProber.getDefaultProber().findAllDrivers(usbManager)
        return availableDrivers.map { it.device }
    }
    
    /**
     * Get human-readable device info
     */
    fun getDeviceInfo(device: UsbDevice): String {
        return buildString {
            append(device.deviceName)
            device.manufacturerName?.let { append(" - $it") }
            device.productName?.let { append(" ($it)") }
        }
    }
    
    /**
     * Connect to USB serial device
     */
    fun connect(device: UsbDevice, settings: SerialPttSettings): Boolean {
        try {
            this.settings = settings
            
            val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
            val drivers = UsbSerialProber.getDefaultProber().findAllDrivers(usbManager)
            val driver = drivers.firstOrNull { it.device == device }
            
            if (driver == null) {
                Log.e(TAG, "No driver found for device: ${device.deviceName}")
                return false
            }
            
            // Check USB permission
            if (!usbManager.hasPermission(device)) {
                Log.e(TAG, "No USB permission for device: ${device.deviceName}")
                return false
            }
            
            val connection = usbManager.openDevice(device)
            if (connection == null) {
                Log.e(TAG, "Failed to open device: ${device.deviceName}")
                return false
            }
            
            serialPort = driver.ports[0] // Use first port
            serialPort?.open(connection)
            serialPort?.setParameters(
                settings.baudRate,
                UsbSerialPort.DATABITS_8,
                UsbSerialPort.STOPBITS_1,
                UsbSerialPort.PARITY_NONE
            )
            
            // Initialize PTT to OFF state
            setPttState(false)
            
            Log.i(TAG, "Connected to ${getDeviceInfo(device)} at ${settings.baudRate} baud")
            return true
            
        } catch (e: IOException) {
            Log.e(TAG, "Failed to connect to device", e)
            disconnect()
            return false
        }
    }
    
    /**
     * Disconnect from serial device
     */
    fun disconnect() {
        try {
            // Ensure PTT is off before disconnecting
            setPttState(false)
            serialPort?.close()
            serialPort = null
            Log.i(TAG, "Disconnected from serial device")
        } catch (e: IOException) {
            Log.e(TAG, "Error disconnecting", e)
        }
    }
    
    /**
     * Activate PTT with configured delays
     */
    fun activatePtt() {
        if (!settings.enabled || serialPort == null) {
            return
        }
        
        scope.launch {
            try {
                // Pre-delay (for VOX disable, relay switching, etc.)
                if (settings.preDelay > 0) {
                    delay(settings.preDelay.toLong())
                }
                
                setPttState(true)
                isPttActive = true
                Log.d(TAG, "PTT activated")
                
            } catch (e: Exception) {
                Log.e(TAG, "Error activating PTT", e)
            }
        }
    }
    
    /**
     * Deactivate PTT with configured delays
     */
    fun deactivatePtt() {
        if (!settings.enabled || serialPort == null) {
            return
        }
        
        scope.launch {
            try {
                setPttState(false)
                isPttActive = false
                Log.d(TAG, "PTT deactivated")
                
                // Post-delay (tail time)
                if (settings.postDelay > 0) {
                    delay(settings.postDelay.toLong())
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Error deactivating PTT", e)
            }
        }
    }
    
    /**
     * Set PTT pin state (RTS or DTR)
     * true = PTT ON (transmit), false = PTT OFF (receive)
     */
    private fun setPttState(active: Boolean) {
        serialPort?.let { port ->
            when (settings.pin) {
                SerialPttPin.RTS -> {
                    port.rts = active
                    Log.v(TAG, "RTS set to $active")
                }
                SerialPttPin.DTR -> {
                    port.dtr = active
                    Log.v(TAG, "DTR set to $active")
                }
            }
        }
    }
    
    /**
     * Check if serial port is connected
     */
    fun isConnected(): Boolean {
        return serialPort != null
    }
    
    /**
     * Check if PTT is currently active
     */
    fun isPttActive(): Boolean {
        return isPttActive
    }
    
    /**
     * Update settings without reconnecting
     */
    fun updateSettings(newSettings: SerialPttSettings) {
        this.settings = newSettings
    }
    
    /**
     * Test PTT by toggling briefly
     */
    fun testPtt(durationMs: Long = 500) {
        scope.launch {
            Log.i(TAG, "Testing PTT for ${durationMs}ms")
            activatePtt()
            delay(durationMs)
            deactivatePtt()
        }
    }
}

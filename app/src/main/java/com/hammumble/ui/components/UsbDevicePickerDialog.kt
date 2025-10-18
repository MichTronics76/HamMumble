package com.hammumble.ui.components

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import android.util.Log
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog

/**
 * USB Device Picker Dialog
 * Shows available USB serial devices and requests permissions
 */
@Composable
fun UsbDevicePickerDialog(
    onDeviceSelected: (UsbDevice) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val usbManager = remember { context.getSystemService(Context.USB_SERVICE) as UsbManager }
    
    var devices by remember { mutableStateOf<List<UsbDevice>>(emptyList()) }
    var selectedDevice by remember { mutableStateOf<UsbDevice?>(null) }
    var permissionRequested by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    
    // USB Permission receiver
    DisposableEffect(context) {
        val ACTION_USB_PERMISSION = "com.hammumble.USB_PERMISSION"
        
        val usbReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (ACTION_USB_PERMISSION == intent.action) {
                    synchronized(this) {
                        val device: UsbDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
                        } else {
                            @Suppress("DEPRECATION")
                            intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                        }
                        
                        if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                            device?.let {
                                Log.i("UsbDevicePicker", "Permission granted for device: ${it.deviceName}")
                                onDeviceSelected(it)
                                onDismiss()
                            }
                        } else {
                            Log.w("UsbDevicePicker", "Permission denied for device: ${device?.deviceName}")
                            errorMessage = "USB permission denied. Please allow access to use hardware PTT."
                            permissionRequested = false
                        }
                    }
                }
            }
        }
        
        val filter = IntentFilter(ACTION_USB_PERMISSION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(usbReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(usbReceiver, filter)
        }
        
        onDispose {
            try {
                context.unregisterReceiver(usbReceiver)
            } catch (e: Exception) {
                Log.e("UsbDevicePicker", "Error unregistering receiver", e)
            }
        }
    }
    
    // Load devices
    LaunchedEffect(Unit) {
        val deviceList = usbManager.deviceList.values.toList()
        devices = deviceList
        Log.i("UsbDevicePicker", "Found ${deviceList.size} USB devices")
        deviceList.forEach { device ->
            Log.d("UsbDevicePicker", "Device: ${device.deviceName} - " +
                    "VID: 0x${device.vendorId.toString(16)} PID: 0x${device.productId.toString(16)} - " +
                    "${device.manufacturerName ?: "Unknown"} ${device.productName ?: ""}")
        }
    }
    
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 500.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Select USB Serial Device",
                        style = MaterialTheme.typography.headlineSmall
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Error message
                errorMessage?.let { error ->
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = error,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
                
                // Device list
                if (devices.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.Settings,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "No USB devices found",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Connect a USB-Serial adapter with OTG cable",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(devices) { device ->
                            UsbDeviceItem(
                                device = device,
                                isSelected = device == selectedDevice,
                                hasPermission = usbManager.hasPermission(device),
                                onDeviceClick = {
                                    selectedDevice = device
                                    if (usbManager.hasPermission(device)) {
                                        // Already have permission
                                        onDeviceSelected(device)
                                        onDismiss()
                                    } else {
                                        // Request permission
                                        errorMessage = null
                                        permissionRequested = true
                                        val ACTION_USB_PERMISSION = "com.hammumble.USB_PERMISSION"
                                        val permissionIntent = PendingIntent.getBroadcast(
                                            context,
                                            0,
                                            Intent(ACTION_USB_PERMISSION),
                                            PendingIntent.FLAG_MUTABLE
                                        )
                                        usbManager.requestPermission(device, permissionIntent)
                                    }
                                }
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Info text
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "💡 Supported: FTDI, CP210x, CH340, PL2303, CDC ACM adapters",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(8.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun UsbDeviceItem(
    device: UsbDevice,
    isSelected: Boolean,
    hasPermission: Boolean,
    onDeviceClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onDeviceClick),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        border = if (isSelected) {
            androidx.compose.foundation.BorderStroke(
                2.dp,
                MaterialTheme.colorScheme.primary
            )
        } else null
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Settings,
                contentDescription = null,
                modifier = Modifier.size(32.dp),
                tint = if (isSelected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurface
                }
            )
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = buildString {
                        device.manufacturerName?.let { append("$it ") }
                        device.productName?.let { append(it) }
                        if (device.manufacturerName == null && device.productName == null) {
                            append("USB Device")
                        }
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    }
                )
                
                Text(
                    text = buildString {
                        append(device.deviceName)
                        append(" • VID: 0x${device.vendorId.toString(16).uppercase()}")
                        append(" • PID: 0x${device.productId.toString(16).uppercase()}")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
                
                if (!hasPermission) {
                    Text(
                        text = "⚠️ Permission required - tap to grant",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

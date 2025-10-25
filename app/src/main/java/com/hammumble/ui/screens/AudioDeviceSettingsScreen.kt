package com.hammumble.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hammumble.audio.AudioDeviceManager
import com.hammumble.data.AudioDeviceSettings

/**
 * Example UI screen for audio device selection
 * This allows users to select separate devices for TX (transmission) and RX (reception)
 * to prevent audio loopback when using USB audio interfaces.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioDeviceSettingsScreen(
    currentSettings: AudioDeviceSettings,
    availableInputDevices: List<AudioDeviceManager.AudioDevice>,
    availableOutputDevices: List<AudioDeviceManager.AudioDevice>,
    onSettingsChanged: (AudioDeviceSettings) -> Unit
) {
    var txDeviceId by remember { mutableStateOf(currentSettings.txDeviceId) }
    var rxDeviceId by remember { mutableStateOf(currentSettings.rxDeviceId) }
    var preferSeparateDevices by remember { mutableStateOf(currentSettings.preferSeparateDevices) }
    
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = "Audio Device Routing",
                style = MaterialTheme.typography.headlineMedium
            )
        }
        
        item {
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Separate TX/RX Devices",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "Enable this to use different audio devices for transmission (TX) and reception (RX). " +
                               "This prevents audio loopback when using USB audio interfaces.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Enable separate device routing")
                        Switch(
                            checked = preferSeparateDevices,
                            onCheckedChange = { enabled ->
                                preferSeparateDevices = enabled
                                val updatedSettings = currentSettings.copy(
                                    preferSeparateDevices = enabled
                                )
                                onSettingsChanged(updatedSettings)
                            }
                        )
                    }
                }
            }
        }
        
        item {
            Text(
                text = "TX Device (Microphone Input)",
                style = MaterialTheme.typography.titleMedium
            )
        }
        
        // TX Device selection
        item {
            DeviceSelectionCard(
                title = "System Default",
                isSelected = txDeviceId == -1,
                onClick = {
                    txDeviceId = -1
                    val updatedSettings = currentSettings.copy(
                        txDeviceId = -1,
                        txDeviceName = "System Default"
                    )
                    onSettingsChanged(updatedSettings)
                }
            )
        }
        
        items(availableInputDevices) { device ->
            DeviceSelectionCard(
                title = device.getDisplayName(),
                subtitle = if (device.isUSB()) "USB Audio Device" else device.getTypeString(),
                isSelected = txDeviceId == device.id,
                isUSB = device.isUSB(),
                onClick = {
                    txDeviceId = device.id
                    val updatedSettings = currentSettings.copy(
                        txDeviceId = device.id,
                        txDeviceName = device.getDisplayName()
                    )
                    onSettingsChanged(updatedSettings)
                }
            )
        }
        
        item {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "RX Device (Speaker Output)",
                style = MaterialTheme.typography.titleMedium
            )
        }
        
        // RX Device selection
        item {
            DeviceSelectionCard(
                title = "System Default",
                isSelected = rxDeviceId == -1,
                onClick = {
                    rxDeviceId = -1
                    val updatedSettings = currentSettings.copy(
                        rxDeviceId = -1,
                        rxDeviceName = "System Default"
                    )
                    onSettingsChanged(updatedSettings)
                }
            )
        }
        
        items(availableOutputDevices) { device ->
            DeviceSelectionCard(
                title = device.getDisplayName(),
                subtitle = if (device.isUSB()) "USB Audio Device" else device.getTypeString(),
                isSelected = rxDeviceId == device.id,
                isUSB = device.isUSB(),
                onClick = {
                    rxDeviceId = device.id
                    val updatedSettings = currentSettings.copy(
                        rxDeviceId = device.id,
                        rxDeviceName = device.getDisplayName()
                    )
                    onSettingsChanged(updatedSettings)
                }
            )
        }
        
        item {
            Spacer(modifier = Modifier.height(16.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "💡 Tip",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = "For best results with USB audio:\n" +
                               "• TX: Use USB microphone/audio interface\n" +
                               "• RX: Use built-in speaker or separate audio device\n" +
                               "This prevents feedback/loopback issues.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceSelectionCard(
    title: String,
    subtitle: String? = null,
    isSelected: Boolean,
    isUSB: Boolean = false,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        }
                    )
                }
                if (isUSB) {
                    Text(
                        text = "🔌 USB",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            
            if (isSelected) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "Selected",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

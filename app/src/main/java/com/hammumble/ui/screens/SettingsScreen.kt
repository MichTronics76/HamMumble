package com.hammumble.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.hammumble.R
import com.hammumble.data.AudioSettings
import com.hammumble.data.RogerBeepStyle
import com.hammumble.data.SerialPttPin
import com.hammumble.data.TransmissionMode
import com.hammumble.ui.components.UsbDevicePickerDialog
import com.hammumble.ui.components.VuMeter
import com.hammumble.ui.viewmodel.MumbleViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
fun SettingsScreen(
    viewModel: MumbleViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val audioSettings by viewModel.audioSettings.collectAsState()
    val inputAudioLevel by viewModel.inputAudioLevel.collectAsState()
    val outputAudioLevel by viewModel.outputAudioLevel.collectAsState()
    val scrollState = rememberScrollState()
    val coroutineScope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }
    
    // USB Device Picker Dialog state
    var showDevicePicker by remember { mutableStateOf(false) }
    
    // File pickers for custom audio
    val transmitterAudioPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            // Take persistable URI permission
            context.contentResolver.takePersistableUriPermission(
                it,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
            viewModel.updateAudioSettings(
                audioSettings.copy(
                    rogerBeep = audioSettings.rogerBeep.copy(
                        style = RogerBeepStyle.CUSTOM,
                        customAudioPath = it.toString()
                    )
                )
            )
        }
    }
    
    val mumbleAudioPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            // Take persistable URI permission
            context.contentResolver.takePersistableUriPermission(
                it,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
            viewModel.updateAudioSettings(
                audioSettings.copy(
                    mumbleRogerBeep = audioSettings.mumbleRogerBeep.copy(
                        style = RogerBeepStyle.CUSTOM,
                        customAudioPath = it.toString()
                    )
                )
            )
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings)) },
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
            // Audio Quality Settings
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
                        text = stringResource(R.string.quality_settings),
                        style = MaterialTheme.typography.headlineSmall
                    )
                    
                    // Bitrate Slider
                    Column {
                        Text(
                            text = "${stringResource(R.string.bitrate)}: ${audioSettings.bitrate / 1000} kbps",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Slider(
                            value = audioSettings.bitrate.toFloat(),
                            onValueChange = { newValue ->
                                viewModel.updateAudioSettings(
                                    audioSettings.copy(bitrate = newValue.toInt())
                                )
                            },
                            valueRange = 8000f..128000f,
                            steps = 23 // 8k, 16k, 24k, 32k, 40k, 48k, 56k, 64k, 72k, 80k, 88k, 96k, 104k, 112k, 120k, 128k
                        )
                    }
                    
                    // Frames per Packet
                    Column {
                        Text(
                            text = "${stringResource(R.string.frames_per_packet)}: ${audioSettings.framesPerPacket}",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Slider(
                            value = audioSettings.framesPerPacket.toFloat(),
                            onValueChange = { newValue ->
                                viewModel.updateAudioSettings(
                                    audioSettings.copy(framesPerPacket = newValue.toInt())
                                )
                            },
                            valueRange = 1f..10f,
                            steps = 9
                        )
                    }
                }
            }
            
            // Transmission Mode Settings
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = stringResource(R.string.transmission_mode),
                        style = MaterialTheme.typography.headlineSmall
                    )
                    
                    TransmissionModeOption(
                        mode = TransmissionMode.CONTINUOUS,
                        currentMode = audioSettings.transmissionMode,
                        onModeSelected = { mode ->
                            viewModel.updateAudioSettings(
                                audioSettings.copy(transmissionMode = mode)
                            )
                        }
                    )
                    
                    TransmissionModeOption(
                        mode = TransmissionMode.VOICE_ACTIVITY,
                        currentMode = audioSettings.transmissionMode,
                        onModeSelected = { mode ->
                            viewModel.updateAudioSettings(
                                audioSettings.copy(transmissionMode = mode)
                            )
                        }
                    )
                    
                    TransmissionModeOption(
                        mode = TransmissionMode.PUSH_TO_TALK,
                        currentMode = audioSettings.transmissionMode,
                        onModeSelected = { mode ->
                            viewModel.updateAudioSettings(
                                audioSettings.copy(transmissionMode = mode)
                            )
                        }
                    )
                    
                    // Voice Activity Detection Threshold (only for VAD mode)
                    if (audioSettings.transmissionMode == TransmissionMode.VOICE_ACTIVITY) {
                        Divider(modifier = Modifier.padding(vertical = 8.dp))
                        
                        Column {
                            Text(
                                text = "VAD Threshold: ${(audioSettings.vadThreshold * 100).toInt()}%",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Slider(
                                value = audioSettings.vadThreshold,
                                onValueChange = { newValue ->
                                    viewModel.updateAudioSettings(
                                        audioSettings.copy(vadThreshold = newValue)
                                    )
                                },
                                valueRange = 0.1f..1.0f
                            )
                            
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            // Input VU Meter for VAD adjustment
                            Text(
                                text = "Input Level Monitor (for VAD tuning)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            VuMeter(
                                level = inputAudioLevel,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(32.dp),
                                orientation = com.hammumble.ui.components.VuMeterOrientation.HORIZONTAL,
                                showValue = true,
                                showPeakIndicator = true
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Column {
                            Text(
                                text = "Voice Hold Time: ${audioSettings.voiceHoldTime}ms",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = "Keep transmitting after voice stops",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Slider(
                                value = audioSettings.voiceHoldTime.toFloat(),
                                onValueChange = { newValue ->
                                    viewModel.updateAudioSettings(
                                        audioSettings.copy(voiceHoldTime = newValue.toInt())
                                    )
                                },
                                valueRange = 0f..2000f,
                                steps = 19 // 0, 100, 200, ..., 2000ms
                            )
                        }
                    }
                }
            }
            
            // Audio Volume & Gain Settings
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text(
                        text = "Audio Volume & Gain",
                        style = MaterialTheme.typography.headlineSmall
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Input Volume
                    Text(
                        text = "Input Volume (Microphone): ${String.format("%.1f", audioSettings.inputGain)}x",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "Adjust microphone input gain (0.5x - 5.0x)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Slider(
                        value = audioSettings.inputGain,
                        onValueChange = { newValue ->
                            viewModel.updateAudioSettings(
                                audioSettings.copy(inputGain = newValue)
                            )
                        },
                        valueRange = 0.5f..5.0f,
                        steps = 44
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Input VU Meter
                    Text(
                        text = "Input Level Monitor",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    VuMeter(
                        level = inputAudioLevel,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(40.dp),
                        orientation = com.hammumble.ui.components.VuMeterOrientation.HORIZONTAL,
                        showValue = true,
                        showPeakIndicator = true
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Output Volume
                    Text(
                        text = "Output Volume (Speaker): ${String.format("%.1f", audioSettings.outputGain)}x",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "Adjust speaker output gain (0.5x - 5.0x)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Slider(
                        value = audioSettings.outputGain,
                        onValueChange = { newValue ->
                            viewModel.updateAudioSettings(
                                audioSettings.copy(outputGain = newValue)
                            )
                        },
                        valueRange = 0.5f..5.0f,
                        steps = 44
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Output VU Meter
                    Text(
                        text = "Output Level Monitor",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    VuMeter(
                        level = outputAudioLevel,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(40.dp),
                        orientation = com.hammumble.ui.components.VuMeterOrientation.HORIZONTAL,
                        showValue = true,
                        showPeakIndicator = true
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Mic Boost Toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "🎤 Mic Boost (2x Pre-Amp)",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = "Extra 2x amplification BEFORE gain slider. Use for low-level mics or portofoon VOX.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = audioSettings.micBoost,
                            onCheckedChange = { enabled ->
                                viewModel.updateAudioSettings(
                                    audioSettings.copy(micBoost = enabled)
                                )
                            }
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    Divider()
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Auto Max Volume
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Auto Max Volume",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = "Set system volume to max when connected",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = audioSettings.autoMaxVolume,
                            onCheckedChange = { enabled ->
                                viewModel.updateAudioSettings(
                                    audioSettings.copy(autoMaxVolume = enabled)
                                )
                            }
                        )
                    }
                }
            }
            
            // Audio Processing Settings
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Audio Processing",
                        style = MaterialTheme.typography.headlineSmall
                    )
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Noise Suppression")
                        Switch(
                            checked = audioSettings.isNoiseSuppressionEnabled,
                            onCheckedChange = { enabled ->
                                viewModel.updateAudioSettings(
                                    audioSettings.copy(isNoiseSuppressionEnabled = enabled)
                                )
                            }
                        )
                    }
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Echo Cancellation")
                        Switch(
                            checked = audioSettings.isEchoCancellationEnabled,
                            onCheckedChange = { enabled ->
                                viewModel.updateAudioSettings(
                                    audioSettings.copy(isEchoCancellationEnabled = enabled)
                                )
                            }
                        )
                    }
                }
            }
            
            // VOX Pre-Tone Settings
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
                        text = "🎵 VOX Pre-Tone",
                        style = MaterialTheme.typography.headlineSmall
                    )
                    
                    Text(
                        text = "Inaudible tone before transmission to trigger external VOX",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    // Info card
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer
                        )
                    ) {
                        Text(
                            modifier = Modifier.padding(12.dp),
                            text = "VOX pre-tone plays an inaudible 20kHz tone before your voice transmission starts. This triggers the VOX circuit on your radio transmitter, ensuring it's already open when you start speaking. Prevents clipping of first syllables!",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                    
                    // Enable VOX Pre-Tone
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Enable VOX Pre-Tone")
                        Switch(
                            checked = audioSettings.voxPreTone.enabled,
                            onCheckedChange = { enabled ->
                                viewModel.updateAudioSettings(
                                    audioSettings.copy(
                                        voxPreTone = audioSettings.voxPreTone.copy(enabled = enabled)
                                    )
                                )
                            }
                        )
                    }
                    
                    if (audioSettings.voxPreTone.enabled) {
                        Divider()
                        
                        // Duration Slider
                        Column {
                            Text(
                                text = "Tone Duration: ${audioSettings.voxPreTone.durationMs} ms",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Slider(
                                value = audioSettings.voxPreTone.durationMs.toFloat(),
                                onValueChange = { newValue ->
                                    viewModel.updateAudioSettings(
                                        audioSettings.copy(
                                            voxPreTone = audioSettings.voxPreTone.copy(durationMs = newValue.toInt())
                                        )
                                    )
                                },
                                valueRange = 50f..300f,
                                steps = 4,
                                colors = SliderDefaults.colors(
                                    thumbColor = MaterialTheme.colorScheme.primary,
                                    activeTrackColor = MaterialTheme.colorScheme.primary
                                )
                            )
                            Text(
                                text = "50ms (fast) ← → 300ms (reliable)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        
                        Divider()
                        
                        // Frequency Slider
                        Column {
                            Text(
                                text = "Tone Frequency: ${audioSettings.voxPreTone.frequency} Hz",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Slider(
                                value = audioSettings.voxPreTone.frequency.toFloat(),
                                onValueChange = { newValue ->
                                    viewModel.updateAudioSettings(
                                        audioSettings.copy(
                                            voxPreTone = audioSettings.voxPreTone.copy(frequency = newValue.toInt())
                                        )
                                    )
                                },
                                valueRange = 18000f..22000f,
                                steps = 7,
                                colors = SliderDefaults.colors(
                                    thumbColor = MaterialTheme.colorScheme.primary,
                                    activeTrackColor = MaterialTheme.colorScheme.primary
                                )
                            )
                            Text(
                                text = "18kHz (audible to some) ← → 22kHz (inaudible)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        
                        Divider()
                        
                        // Volume Slider
                        Column {
                            Text(
                                text = "Tone Volume: ${(audioSettings.voxPreTone.volume * 100).toInt()}%",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Slider(
                                value = audioSettings.voxPreTone.volume,
                                onValueChange = { newValue ->
                                    viewModel.updateAudioSettings(
                                        audioSettings.copy(
                                            voxPreTone = audioSettings.voxPreTone.copy(volume = newValue)
                                        )
                                    )
                                },
                                valueRange = 0.1f..1.0f,
                                colors = SliderDefaults.colors(
                                    thumbColor = MaterialTheme.colorScheme.primary,
                                    activeTrackColor = MaterialTheme.colorScheme.primary
                                )
                            )
                            Text(
                                text = "Lower volume if it interferes with audio",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
            
            // Transmitter Roger Beep Settings
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "📻 Transmitter Roger Beep",
                        style = MaterialTheme.typography.headlineSmall
                    )
                    
                    Text(
                        text = "Audio roger beep when others stop talking (not transmitted to Mumble)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    // Info banner
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "🔊",
                                style = MaterialTheme.typography.titleLarge,
                                modifier = Modifier.padding(end = 8.dp)
                            )
                            Text(
                                text = "Transmitter roger beep plays locally when OTHER users in your channel stop talking (after voice hold timer). Perfect for repeater/gateway setups.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        }
                    }
                    
                    // Enable Transmitter Roger Beep
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Enable Transmitter Roger Beep")
                        Switch(
                            checked = audioSettings.rogerBeep.enabled,
                            onCheckedChange = { enabled ->
                                viewModel.updateAudioSettings(
                                    audioSettings.copy(
                                        rogerBeep = audioSettings.rogerBeep.copy(enabled = enabled)
                                    )
                                )
                            }
                        )
                    }
                    
                    if (audioSettings.rogerBeep.enabled) {
                        Divider()
                        
                        // Roger Beep Style Selection Dropdown
                        var expandedBeepStyle by remember { mutableStateOf(false) }
                        
                        // Group styles by category
                        val standardStyles = listOf(
                            RogerBeepStyle.CLASSIC_8_TONE,
                            RogerBeepStyle.TWO_TONE_HIGH_LOW,
                            RogerBeepStyle.THREE_TONE_UP,
                            RogerBeepStyle.FOUR_TONE_DESCEND,
                            RogerBeepStyle.SHORT_CHIRP,
                            RogerBeepStyle.LONG_BEEP,
                            RogerBeepStyle.CUSTOM
                        )
                        
                        val morseStyles = listOf(
                            RogerBeepStyle.MORSE_A,
                            RogerBeepStyle.MORSE_B,
                            RogerBeepStyle.MORSE_C,
                            RogerBeepStyle.MORSE_D,
                            RogerBeepStyle.MORSE_E,
                            RogerBeepStyle.MORSE_F,
                            RogerBeepStyle.MORSE_G,
                            RogerBeepStyle.MORSE_H,
                            RogerBeepStyle.MORSE_I,
                            RogerBeepStyle.MORSE_J,
                            RogerBeepStyle.MORSE_K,
                            RogerBeepStyle.MORSE_L,
                            RogerBeepStyle.MORSE_M,
                            RogerBeepStyle.MORSE_N,
                            RogerBeepStyle.MORSE_O,
                            RogerBeepStyle.MORSE_P,
                            RogerBeepStyle.MORSE_Q,
                            RogerBeepStyle.MORSE_R,
                            RogerBeepStyle.MORSE_S,
                            RogerBeepStyle.MORSE_T,
                            RogerBeepStyle.MORSE_U,
                            RogerBeepStyle.MORSE_V,
                            RogerBeepStyle.MORSE_W,
                            RogerBeepStyle.MORSE_X,
                            RogerBeepStyle.MORSE_Y,
                            RogerBeepStyle.MORSE_Z
                        )
                        
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = "Transmitter Roger Beep Style",
                                style = MaterialTheme.typography.titleSmall,
                                modifier = Modifier.padding(bottom = 4.dp)
                            )
                            
                            ExposedDropdownMenuBox(
                                expanded = expandedBeepStyle,
                                onExpandedChange = { expandedBeepStyle = !expandedBeepStyle }
                            ) {
                                OutlinedTextField(
                                    value = getBeepStyleName(audioSettings.rogerBeep.style),
                                    onValueChange = {},
                                    readOnly = true,
                                    label = { Text("Select Style") },
                                    trailingIcon = {
                                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedBeepStyle)
                                    },
                                    colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .menuAnchor()
                                )
                                
                                ExposedDropdownMenu(
                                    expanded = expandedBeepStyle,
                                    onDismissRequest = { expandedBeepStyle = false }
                                ) {
                                    // Standard Beeps Section
                                    Text(
                                        text = "Standard Beeps",
                                        style = MaterialTheme.typography.labelLarge,
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    
                                    standardStyles.forEach { style ->
                                        DropdownMenuItem(
                                            text = {
                                                Column {
                                                    Text(
                                                        text = getBeepStyleName(style),
                                                        style = MaterialTheme.typography.bodyMedium
                                                    )
                                                    Text(
                                                        text = getBeepStyleDescription(style),
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            },
                                            onClick = {
                                                viewModel.updateAudioSettings(
                                                    audioSettings.copy(
                                                        rogerBeep = audioSettings.rogerBeep.copy(style = style)
                                                    )
                                                )
                                                expandedBeepStyle = false
                                            },
                                            leadingIcon = {
                                                if (style == audioSettings.rogerBeep.style) {
                                                    Icon(
                                                        imageVector = androidx.compose.material.icons.Icons.Default.Check,
                                                        contentDescription = null
                                                    )
                                                }
                                            }
                                        )
                                    }
                                    
                                    Divider(modifier = Modifier.padding(vertical = 8.dp))
                                    
                                    // Morse Code Section
                                    Text(
                                        text = "Morse Code Alphabet",
                                        style = MaterialTheme.typography.labelLarge,
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    
                                    morseStyles.forEach { style ->
                                        DropdownMenuItem(
                                            text = {
                                                Column {
                                                    Text(
                                                        text = getBeepStyleName(style),
                                                        style = MaterialTheme.typography.bodyMedium
                                                    )
                                                    Text(
                                                        text = getBeepStyleDescription(style),
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            },
                                            onClick = {
                                                viewModel.updateAudioSettings(
                                                    audioSettings.copy(
                                                        rogerBeep = audioSettings.rogerBeep.copy(style = style)
                                                    )
                                                )
                                                expandedBeepStyle = false
                                            },
                                            leadingIcon = {
                                                if (style == audioSettings.rogerBeep.style) {
                                                    Icon(
                                                        imageVector = androidx.compose.material.icons.Icons.Default.Check,
                                                        contentDescription = null
                                                    )
                                                }
                                            }
                                        )
                                    }
                                }
                            }
                            
                            // Show description of current style
                            Text(
                                text = getBeepStyleDescription(audioSettings.rogerBeep.style),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                            
                            // Custom Audio File Selection
                            if (audioSettings.rogerBeep.style == RogerBeepStyle.CUSTOM) {
                                OutlinedButton(
                                    onClick = { transmitterAudioPicker.launch(arrayOf("audio/*")) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 8.dp)
                                ) {
                                    Text(
                                        text = if (audioSettings.rogerBeep.customAudioPath != null) {
                                            "Selected: ${Uri.parse(audioSettings.rogerBeep.customAudioPath).lastPathSegment ?: "Unknown"}"
                                        } else {
                                            "🎵 Select Custom Audio File"
                                        }
                                    )
                                }
                                
                                if (audioSettings.rogerBeep.customAudioPath != null) {
                                    Text(
                                        text = "Supported formats: WAV, MP3, OGG, M4A (max 5 seconds)",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(top = 4.dp)
                                    )
                                }
                            }
                        }
                        
                        Divider()
                        
                        // Volume Control
                        Column {
                            Text(
                                text = "Volume: ${(audioSettings.rogerBeep.volume * 100).toInt()}%",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = "Transmitter roger beep playback volume",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Slider(
                                value = audioSettings.rogerBeep.volume,
                                onValueChange = { newValue ->
                                    viewModel.updateAudioSettings(
                                        audioSettings.copy(
                                            rogerBeep = audioSettings.rogerBeep.copy(volume = newValue)
                                        )
                                    )
                                },
                                valueRange = 0.0f..1.0f,
                                steps = 19 // 0%, 5%, 10%, ..., 100%
                            )
                        }
                        
                        // Tone Duration
                        Column {
                            Text(
                                text = "Tone Duration: ${audioSettings.rogerBeep.toneDurationMs}ms",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = "Duration of each tone in the sequence",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Slider(
                                value = audioSettings.rogerBeep.toneDurationMs.toFloat(),
                                onValueChange = { newValue ->
                                    viewModel.updateAudioSettings(
                                        audioSettings.copy(
                                            rogerBeep = audioSettings.rogerBeep.copy(toneDurationMs = newValue.toInt())
                                        )
                                    )
                                },
                                valueRange = 20f..100f,
                                steps = 15 // 20, 25, 30, ..., 100ms
                            )
                        }
                    }
                }
            }
            
            // Mumble Roger Beep Settings
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "📡 Mumble Roger Beep",
                        style = MaterialTheme.typography.headlineSmall
                    )
                    
                    Text(
                        text = "Roger beep transmitted to Mumble when your voice hold timer ends",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    // Info banner
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "🎙️",
                                style = MaterialTheme.typography.titleLarge,
                                modifier = Modifier.padding(end = 8.dp)
                            )
                            Text(
                                text = "This roger beep is SENT to Mumble when YOU stop talking (after your voice hold timer). Other users will hear it.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                    
                    // Enable Mumble Roger Beep
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Enable Mumble Roger Beep")
                        Switch(
                            checked = audioSettings.mumbleRogerBeep.enabled,
                            onCheckedChange = { enabled ->
                                viewModel.updateAudioSettings(
                                    audioSettings.copy(
                                        mumbleRogerBeep = audioSettings.mumbleRogerBeep.copy(enabled = enabled)
                                    )
                                )
                            }
                        )
                    }
                    
                    if (audioSettings.mumbleRogerBeep.enabled) {
                        Divider()
                        
                        // Mumble Roger Beep Style Selection Dropdown
                        var expandedMumbleBeepStyle by remember { mutableStateOf(false) }
                        
                        // Group styles by category
                        val standardStyles = listOf(
                            RogerBeepStyle.CLASSIC_8_TONE,
                            RogerBeepStyle.TWO_TONE_HIGH_LOW,
                            RogerBeepStyle.THREE_TONE_UP,
                            RogerBeepStyle.FOUR_TONE_DESCEND,
                            RogerBeepStyle.SHORT_CHIRP,
                            RogerBeepStyle.LONG_BEEP,
                            RogerBeepStyle.CUSTOM
                        )
                        
                        val morseStyles = listOf(
                            RogerBeepStyle.MORSE_A,
                            RogerBeepStyle.MORSE_B,
                            RogerBeepStyle.MORSE_C,
                            RogerBeepStyle.MORSE_D,
                            RogerBeepStyle.MORSE_E,
                            RogerBeepStyle.MORSE_F,
                            RogerBeepStyle.MORSE_G,
                            RogerBeepStyle.MORSE_H,
                            RogerBeepStyle.MORSE_I,
                            RogerBeepStyle.MORSE_J,
                            RogerBeepStyle.MORSE_K,
                            RogerBeepStyle.MORSE_L,
                            RogerBeepStyle.MORSE_M,
                            RogerBeepStyle.MORSE_N,
                            RogerBeepStyle.MORSE_O,
                            RogerBeepStyle.MORSE_P,
                            RogerBeepStyle.MORSE_Q,
                            RogerBeepStyle.MORSE_R,
                            RogerBeepStyle.MORSE_S,
                            RogerBeepStyle.MORSE_T,
                            RogerBeepStyle.MORSE_U,
                            RogerBeepStyle.MORSE_V,
                            RogerBeepStyle.MORSE_W,
                            RogerBeepStyle.MORSE_X,
                            RogerBeepStyle.MORSE_Y,
                            RogerBeepStyle.MORSE_Z
                        )
                        
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = "Mumble Roger Beep Style",
                                style = MaterialTheme.typography.titleSmall,
                                modifier = Modifier.padding(bottom = 4.dp)
                            )
                            
                            ExposedDropdownMenuBox(
                                expanded = expandedMumbleBeepStyle,
                                onExpandedChange = { expandedMumbleBeepStyle = !expandedMumbleBeepStyle }
                            ) {
                                OutlinedTextField(
                                    value = getBeepStyleName(audioSettings.mumbleRogerBeep.style),
                                    onValueChange = {},
                                    readOnly = true,
                                    label = { Text("Select Style") },
                                    trailingIcon = {
                                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedMumbleBeepStyle)
                                    },
                                    colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .menuAnchor()
                                )
                                
                                ExposedDropdownMenu(
                                    expanded = expandedMumbleBeepStyle,
                                    onDismissRequest = { expandedMumbleBeepStyle = false }
                                ) {
                                    // Standard Beeps Section
                                    Text(
                                        text = "Standard Beeps",
                                        style = MaterialTheme.typography.labelLarge,
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    
                                    standardStyles.forEach { style ->
                                        DropdownMenuItem(
                                            text = {
                                                Column {
                                                    Text(
                                                        text = getBeepStyleName(style),
                                                        style = MaterialTheme.typography.bodyMedium
                                                    )
                                                    Text(
                                                        text = getBeepStyleDescription(style),
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            },
                                            onClick = {
                                                viewModel.updateAudioSettings(
                                                    audioSettings.copy(
                                                        mumbleRogerBeep = audioSettings.mumbleRogerBeep.copy(style = style)
                                                    )
                                                )
                                                expandedMumbleBeepStyle = false
                                            },
                                            leadingIcon = {
                                                if (style == audioSettings.mumbleRogerBeep.style) {
                                                    Icon(
                                                        imageVector = Icons.Default.Check,
                                                        contentDescription = null
                                                    )
                                                }
                                            }
                                        )
                                    }
                                    
                                    Divider(modifier = Modifier.padding(vertical = 8.dp))
                                    
                                    // Morse Code Section
                                    Text(
                                        text = "Morse Code Alphabet",
                                        style = MaterialTheme.typography.labelLarge,
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    
                                    morseStyles.forEach { style ->
                                        DropdownMenuItem(
                                            text = {
                                                Column {
                                                    Text(
                                                        text = getBeepStyleName(style),
                                                        style = MaterialTheme.typography.bodyMedium
                                                    )
                                                    Text(
                                                        text = getBeepStyleDescription(style),
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            },
                                            onClick = {
                                                viewModel.updateAudioSettings(
                                                    audioSettings.copy(
                                                        mumbleRogerBeep = audioSettings.mumbleRogerBeep.copy(style = style)
                                                    )
                                                )
                                                expandedMumbleBeepStyle = false
                                            },
                                            leadingIcon = {
                                                if (style == audioSettings.mumbleRogerBeep.style) {
                                                    Icon(
                                                        imageVector = Icons.Default.Check,
                                                        contentDescription = null
                                                    )
                                                }
                                            }
                                        )
                                    }
                                }
                            }
                            
                            // Show description of current style
                            Text(
                                text = getBeepStyleDescription(audioSettings.mumbleRogerBeep.style),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                            
                            // Custom Audio File Selection
                            if (audioSettings.mumbleRogerBeep.style == RogerBeepStyle.CUSTOM) {
                                OutlinedButton(
                                    onClick = { mumbleAudioPicker.launch(arrayOf("audio/*")) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 8.dp)
                                ) {
                                    Text(
                                        text = if (audioSettings.mumbleRogerBeep.customAudioPath != null) {
                                            "Selected: ${Uri.parse(audioSettings.mumbleRogerBeep.customAudioPath).lastPathSegment ?: "Unknown"}"
                                        } else {
                                            "🎵 Select Custom Audio File"
                                        }
                                    )
                                }
                                
                                if (audioSettings.mumbleRogerBeep.customAudioPath != null) {
                                    Text(
                                        text = "Supported formats: WAV, MP3, OGG, M4A (max 5 seconds)",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(top = 4.dp)
                                    )
                                }
                            }
                        }
                        
                        Divider()
                        
                        // Volume Control
                        Column {
                            Text(
                                text = "Volume: ${(audioSettings.mumbleRogerBeep.volume * 100).toInt()}%",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = "Mumble roger beep transmission volume",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Slider(
                                value = audioSettings.mumbleRogerBeep.volume,
                                onValueChange = { newValue ->
                                    viewModel.updateAudioSettings(
                                        audioSettings.copy(
                                            mumbleRogerBeep = audioSettings.mumbleRogerBeep.copy(volume = newValue)
                                        )
                                    )
                                },
                                valueRange = 0.0f..1.0f,
                                steps = 19 // 0%, 5%, 10%, ..., 100%
                            )
                        }
                        
                        // Tone Duration
                        Column {
                            Text(
                                text = "Tone Duration: ${audioSettings.mumbleRogerBeep.toneDurationMs}ms",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = "Duration of each tone in the sequence",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Slider(
                                value = audioSettings.mumbleRogerBeep.toneDurationMs.toFloat(),
                                onValueChange = { newValue ->
                                    viewModel.updateAudioSettings(
                                        audioSettings.copy(
                                            mumbleRogerBeep = audioSettings.mumbleRogerBeep.copy(toneDurationMs = newValue.toInt())
                                        )
                                    )
                                },
                                valueRange = 20f..100f,
                                steps = 15 // 20, 25, 30, ..., 100ms
                            )
                        }
                    }
                }
            }
            
            // Serial PTT Hardware Settings
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "🔌 Hardware PTT (Serial)",
                        style = MaterialTheme.typography.headlineSmall
                    )
                    
                    Text(
                        text = "Triggers radio PTT when others speak in your channel (for crossband/repeater setups)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    // Info banner explaining the feature
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "ℹ️",
                                style = MaterialTheme.typography.titleLarge,
                                modifier = Modifier.padding(end = 8.dp)
                            )
                            Text(
                                text = "Serial PTT activates when OTHERS speak in your channel. Use this to relay Mumble audio to a radio transmitter.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                    
                    // Enable Serial PTT
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Enable Hardware PTT")
                        Switch(
                            checked = audioSettings.serialPtt.enabled,
                            onCheckedChange = { enabled ->
                                viewModel.updateAudioSettings(
                                    audioSettings.copy(
                                        serialPtt = audioSettings.serialPtt.copy(enabled = enabled)
                                    )
                                )
                            }
                        )
                    }
                    
                    if (audioSettings.serialPtt.enabled) {
                        Divider()
                        
                        // PTT Pin Selection
                        Text(
                            text = "PTT Control Pin",
                            style = MaterialTheme.typography.titleSmall
                        )
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            SerialPttPin.values().forEach { pin ->
                                Row(
                                    modifier = Modifier
                                        .selectable(
                                            selected = (pin == audioSettings.serialPtt.pin),
                                            onClick = {
                                                viewModel.updateAudioSettings(
                                                    audioSettings.copy(
                                                        serialPtt = audioSettings.serialPtt.copy(pin = pin)
                                                    )
                                                )
                                            }
                                        )
                                        .padding(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(
                                        selected = (pin == audioSettings.serialPtt.pin),
                                        onClick = {
                                            viewModel.updateAudioSettings(
                                                audioSettings.copy(
                                                    serialPtt = audioSettings.serialPtt.copy(pin = pin)
                                                )
                                            )
                                        }
                                    )
                                    Text(
                                        text = pin.name,
                                        modifier = Modifier.padding(start = 4.dp)
                                    )
                                }
                            }
                        }
                        
                        Divider()
                        
                        // Pre-Delay
                        Column {
                            Text(
                                text = "Pre-Delay: ${audioSettings.serialPtt.preDelay}ms",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = "Delay before PTT activates (for VOX disable)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Slider(
                                value = audioSettings.serialPtt.preDelay.toFloat(),
                                onValueChange = { newValue ->
                                    viewModel.updateAudioSettings(
                                        audioSettings.copy(
                                            serialPtt = audioSettings.serialPtt.copy(preDelay = newValue.toInt())
                                        )
                                    )
                                },
                                valueRange = 0f..500f,
                                steps = 49 // 0, 10, 20, ..., 500ms
                            )
                        }
                        
                        // Post-Delay
                        Column {
                            Text(
                                text = "Post-Delay: ${audioSettings.serialPtt.postDelay}ms",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = "Delay after PTT deactivates (tail time)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Slider(
                                value = audioSettings.serialPtt.postDelay.toFloat(),
                                onValueChange = { newValue ->
                                    viewModel.updateAudioSettings(
                                        audioSettings.copy(
                                            serialPtt = audioSettings.serialPtt.copy(postDelay = newValue.toInt())
                                        )
                                    )
                                },
                                valueRange = 0f..500f,
                                steps = 49 // 0, 10, 20, ..., 500ms
                            )
                        }
                        
                        Divider()
                        
                        // Device Info & Actions
                        if (audioSettings.serialPtt.deviceName != null) {
                            Text(
                                text = "Connected: ${audioSettings.serialPtt.deviceName}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        } else {
                            Text(
                                text = "No device connected",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = { showDevicePicker = true },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Select Device")
                            }
                            
                            Button(
                                onClick = { viewModel.testSerialPtt() },
                                enabled = audioSettings.serialPtt.deviceName != null,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Test PTT")
                            }
                        }
                    }
                }
            }
            
            // USB Device Picker Dialog
            if (showDevicePicker) {
                UsbDevicePickerDialog(
                    onDeviceSelected = { device ->
                        // Connect to selected device
                        val success = viewModel.connectSerialDevice(device, audioSettings.serialPtt)
                        if (success) {
                            // Update settings with device name
                            viewModel.updateAudioSettings(
                                audioSettings.copy(
                                    serialPtt = audioSettings.serialPtt.copy(
                                        deviceName = "${device.manufacturerName ?: "USB"} ${device.productName ?: "Serial"}"
                                    )
                                )
                            )
                        }
                    },
                    onDismiss = { showDevicePicker = false }
                )
            }
        }
    }
}

@Composable
fun TransmissionModeOption(
    mode: TransmissionMode,
    currentMode: TransmissionMode,
    onModeSelected: (TransmissionMode) -> Unit
) {
    val modeText = when (mode) {
        TransmissionMode.CONTINUOUS -> stringResource(R.string.continuous)
        TransmissionMode.VOICE_ACTIVITY -> stringResource(R.string.voice_activity)
        TransmissionMode.PUSH_TO_TALK -> stringResource(R.string.push_to_talk_mode)
    }
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(
                selected = (mode == currentMode),
                onClick = { onModeSelected(mode) }
            )
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = (mode == currentMode),
            onClick = { onModeSelected(mode) }
        )
        Text(
            text = modeText,
            modifier = Modifier.padding(start = 8.dp),
            style = MaterialTheme.typography.bodyLarge
        )
    }
}

// Helper functions for Roger Beep Style descriptions
fun getBeepStyleName(style: RogerBeepStyle): String {
    return when (style) {
        RogerBeepStyle.CLASSIC_8_TONE -> "Classic 8-Tone"
        RogerBeepStyle.TWO_TONE_HIGH_LOW -> "Two-Tone High-Low"
        RogerBeepStyle.THREE_TONE_UP -> "Three-Tone Ascending"
        RogerBeepStyle.FOUR_TONE_DESCEND -> "Four-Tone Descending"
        RogerBeepStyle.SHORT_CHIRP -> "Short Chirp"
        RogerBeepStyle.LONG_BEEP -> "Long Beep"
        RogerBeepStyle.CUSTOM -> "Custom Audio File"
        
        // Morse alphabet
        RogerBeepStyle.MORSE_A -> "Morse 'A' (.-)"
        RogerBeepStyle.MORSE_B -> "Morse 'B' (-...)"
        RogerBeepStyle.MORSE_C -> "Morse 'C' (-.-.)"
        RogerBeepStyle.MORSE_D -> "Morse 'D' (-..)"
        RogerBeepStyle.MORSE_E -> "Morse 'E' (.)"
        RogerBeepStyle.MORSE_F -> "Morse 'F' (..-.)"
        RogerBeepStyle.MORSE_G -> "Morse 'G' (--.)"
        RogerBeepStyle.MORSE_H -> "Morse 'H' (....)"
        RogerBeepStyle.MORSE_I -> "Morse 'I' (..)"
        RogerBeepStyle.MORSE_J -> "Morse 'J' (.---)"
        RogerBeepStyle.MORSE_K -> "Morse 'K' (-.-)"
        RogerBeepStyle.MORSE_L -> "Morse 'L' (.-..)"
        RogerBeepStyle.MORSE_M -> "Morse 'M' (--)"
        RogerBeepStyle.MORSE_N -> "Morse 'N' (-.)"
        RogerBeepStyle.MORSE_O -> "Morse 'O' (---)"
        RogerBeepStyle.MORSE_P -> "Morse 'P' (.--.)"
        RogerBeepStyle.MORSE_Q -> "Morse 'Q' (--.-)"
        RogerBeepStyle.MORSE_R -> "Morse 'R' (.-.)"
        RogerBeepStyle.MORSE_S -> "Morse 'S' (...)"
        RogerBeepStyle.MORSE_T -> "Morse 'T' (-)"
        RogerBeepStyle.MORSE_U -> "Morse 'U' (..-)"
        RogerBeepStyle.MORSE_V -> "Morse 'V' (...-)"
        RogerBeepStyle.MORSE_W -> "Morse 'W' (.--)"
        RogerBeepStyle.MORSE_X -> "Morse 'X' (-..-)"
        RogerBeepStyle.MORSE_Y -> "Morse 'Y' (-.--)"
        RogerBeepStyle.MORSE_Z -> "Morse 'Z' (--..)"
    }
}

fun getBeepStyleDescription(style: RogerBeepStyle): String {
    return when (style) {
        RogerBeepStyle.CLASSIC_8_TONE -> "Traditional 8-tone ascending sequence (320ms)"
        RogerBeepStyle.TWO_TONE_HIGH_LOW -> "High-low two-tone beep (200ms)"
        RogerBeepStyle.THREE_TONE_UP -> "Three quick ascending tones (280ms)"
        RogerBeepStyle.FOUR_TONE_DESCEND -> "Four descending tones (260ms)"
        RogerBeepStyle.SHORT_CHIRP -> "Quick single chirp (80ms)"
        RogerBeepStyle.LONG_BEEP -> "Single sustained tone (250ms)"
        RogerBeepStyle.CUSTOM -> "Play custom audio file (WAV, MP3, OGG, etc.)"
        
        // Morse alphabet descriptions
        RogerBeepStyle.MORSE_A -> "Morse code letter A"
        RogerBeepStyle.MORSE_B -> "Morse code letter B"
        RogerBeepStyle.MORSE_C -> "Morse code letter C"
        RogerBeepStyle.MORSE_D -> "Morse code letter D"
        RogerBeepStyle.MORSE_E -> "Morse code letter E"
        RogerBeepStyle.MORSE_F -> "Morse code letter F"
        RogerBeepStyle.MORSE_G -> "Morse code letter G"
        RogerBeepStyle.MORSE_H -> "Morse code letter H"
        RogerBeepStyle.MORSE_I -> "Morse code letter I"
        RogerBeepStyle.MORSE_J -> "Morse code letter J"
        RogerBeepStyle.MORSE_K -> "Morse code letter K"
        RogerBeepStyle.MORSE_L -> "Morse code letter L"
        RogerBeepStyle.MORSE_M -> "Morse code letter M"
        RogerBeepStyle.MORSE_N -> "Morse code letter N"
        RogerBeepStyle.MORSE_O -> "Morse code letter O"
        RogerBeepStyle.MORSE_P -> "Morse code letter P"
        RogerBeepStyle.MORSE_Q -> "Morse code letter Q"
        RogerBeepStyle.MORSE_R -> "Morse code letter R"
        RogerBeepStyle.MORSE_S -> "Morse code letter S"
        RogerBeepStyle.MORSE_T -> "Morse code letter T"
        RogerBeepStyle.MORSE_U -> "Morse code letter U"
        RogerBeepStyle.MORSE_V -> "Morse code letter V"
        RogerBeepStyle.MORSE_W -> "Morse code letter W"
        RogerBeepStyle.MORSE_X -> "Morse code letter X"
        RogerBeepStyle.MORSE_Y -> "Morse code letter Y"
        RogerBeepStyle.MORSE_Z -> "Morse code letter Z"
    }
}
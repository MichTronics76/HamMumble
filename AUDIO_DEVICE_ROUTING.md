# Audio Device Routing - TX/RX Scheiding

## Overzicht

De HamMumble app ondersteunt nu **gescheiden audio device routing** voor TX (transmit/zenden) en RX (receive/ontvangen). Dit voorkomt audio loopback problemen wanneer je een USB geluidskaartje via OTG gebruikt.

## Waarom is dit nodig?

### Het Probleem

Wanneer je een USB audio interface aansluit:
- Android routeert **automatisch** alle audio naar hetzelfde USB device
- Beide TX (microfoon) EN RX (speaker) gebruiken dezelfde USB interface
- Dit kan resulteren in **audio feedback/loopback**

### De Oplossing

Met expliciete device selectie kun je:
- **TX** toewijzen aan USB microfoon/audio interface
- **RX** toewijzen aan ingebouwde speaker of ander device
- Volledige **scheiding** tussen zend- en ontvangst-audio

## Implementatie Details

### Nieuwe Componenten

#### 1. `AudioDeviceSettings` (Models.kt)
```kotlin
data class AudioDeviceSettings(
    val txDeviceId: Int = -1,        // -1 = system default
    val rxDeviceId: Int = -1,        // -1 = system default
    val preferSeparateDevices: Boolean = false,
    val txDeviceName: String = "",
    val rxDeviceName: String = ""
)
```

#### 2. `AudioDeviceManager` (AudioDeviceManager.kt)
Beheert audio device detectie en routing:
- `getInputDevices()` - Lijst alle beschikbare microfoons
- `getOutputDevices()` - Lijst alle beschikbare speakers
- `getAudioDeviceInfo(deviceId)` - Verkrijg AudioDeviceInfo voor setPreferredDevice()
- `hasUSBDevices()` - Check of USB audio aanwezig is
- `getRecommendedSeparateDevices()` - Intelligente aanbevelingen

#### 3. Audio Layer Updates
**AudioInput.java:**
```java
public boolean setPreferredDevice(AudioDeviceInfo deviceInfo)
public AudioDeviceInfo getRoutedDevice()
```

**AudioOutput.java:**
```java
public boolean setPreferredDevice(AudioDeviceInfo deviceInfo)
public AudioDeviceInfo getRoutedDevice()
```

**AudioHandler.java:**
```java
public boolean setPreferredInputDevice(AudioDeviceInfo deviceInfo)
public boolean setPreferredOutputDevice(AudioDeviceInfo deviceInfo)
public AudioDeviceInfo getRoutedInputDevice()
public AudioDeviceInfo getRoutedOutputDevice()
```

**HumlaService.java:**
```java
public boolean setPreferredInputDevice(AudioDeviceInfo deviceInfo)
public boolean setPreferredOutputDevice(AudioDeviceInfo deviceInfo)
public AudioDeviceInfo getRoutedInputDevice()
public AudioDeviceInfo getRoutedOutputDevice()
```

#### 4. MumbleService Integration
```kotlin
// Device management functies
fun getAvailableInputDevices(): List<AudioDeviceManager.AudioDevice>
fun getAvailableOutputDevices(): List<AudioDeviceManager.AudioDevice>
fun applyAudioDeviceSettings(deviceSettings: AudioDeviceSettings)
fun getRecommendedSeparateDevices(): Pair<AudioDevice?, AudioDevice?>
fun hasUSBDevices(): Boolean
```

### Automatische Toepassing

Audio device settings worden automatisch toegepast wanneer:
1. `updateAudioSettings()` wordt aangeroepen
2. De app verbindt met een Mumble server
3. Audio subsysteem wordt geïnitialiseerd

## Gebruik

### In Code

```kotlin
// Verkrijg beschikbare devices
val inputDevices = mumbleService.getAvailableInputDevices()
val outputDevices = mumbleService.getAvailableOutputDevices()

// Check voor USB devices
if (mumbleService.hasUSBDevices()) {
    // Krijg aanbevelingen
    val (txDevice, rxDevice) = mumbleService.getRecommendedSeparateDevices()
}

// Configureer audio devices
val deviceSettings = AudioDeviceSettings(
    txDeviceId = usbMicrophone.id,
    rxDeviceId = builtInSpeaker.id,
    preferSeparateDevices = true,
    txDeviceName = "USB Audio Interface",
    rxDeviceName = "Built-in Speaker"
)

// Update audio settings met nieuwe device configuratie
val updatedAudioSettings = currentAudioSettings.copy(
    audioDevices = deviceSettings
)
mumbleService.updateAudioSettings(updatedAudioSettings)
```

### In UI (Voorbeeld)

Een complete Compose UI is beschikbaar in `AudioDeviceSettingsScreen.kt`:

```kotlin
AudioDeviceSettingsScreen(
    currentSettings = audioSettings.audioDevices,
    availableInputDevices = mumbleService.getAvailableInputDevices(),
    availableOutputDevices = mumbleService.getAvailableOutputDevices(),
    onSettingsChanged = { newDeviceSettings ->
        val updatedSettings = audioSettings.copy(
            audioDevices = newDeviceSettings
        )
        mumbleService.updateAudioSettings(updatedSettings)
    }
)
```

## Aanbevolen Configuratie

### Voor USB Audio Interface

**Ideale Setup:**
```
TX (Zenden):  USB Audio Interface Microfoon Input
RX (Ontvangen): Ingebouwde Speaker of Aparte Audio Output
```

Dit voorkomt dat:
- Audio van RX (wat je hoort van anderen) wordt opgepikt door TX microfoon
- Feedback loops ontstaan
- Echo problemen optreden

### Automatische Aanbeveling

De `getRecommendedSeparateDevices()` functie geeft intelligente aanbevelingen:
1. **TX**: Geeft voorkeur aan USB input, fallback naar ingebouwde mic
2. **RX**: Geeft voorkeur aan ingebouwde speaker (NIET USB om loopback te voorkomen)

## Device Types

De volgende device types worden herkend:
- `TYPE_BUILTIN_MIC` - Ingebouwde microfoon
- `TYPE_BUILTIN_SPEAKER` - Ingebouwde speaker
- `TYPE_WIRED_HEADSET` - Bedrade headset
- `TYPE_BLUETOOTH_SCO` - Bluetooth SCO (voice calls)
- `TYPE_USB_DEVICE` - USB audio device
- `TYPE_USB_HEADSET` - USB headset
- `TYPE_USB_ACCESSORY` - USB accessory

## Android Versie Vereisten

- **Minimum**: Android 6.0 (API 23) voor `setPreferredDevice()`
- **Aanbevolen**: Android 6.0+ voor volledige functionaliteit
- Op oudere versies gebruikt het systeem standaard audio routing

## Logging en Debugging

De implementatie logt uitgebreid:

```
AudioDeviceManager: Found input device: USB Audio Interface (TYPE_USB_DEVICE) (ID: 12)
AudioDeviceManager: Found output device: Built-in Speaker (TYPE_BUILTIN_SPEAKER) (ID: 2)
MumbleService: TX device set successfully: USB Audio Interface
MumbleService: RX device set successfully: Built-in Speaker
MumbleService: Currently routed TX device: USB Audio Interface (Type: 7)
MumbleService: Currently routed RX device: Built-in Speaker (Type: 2)
```

## Settings Persistentie

Audio device settings worden automatisch opgeslagen in `AudioSettings` en gepersisteerd via SharedPreferences wanneer `updateAudioSettings()` wordt aangeroepen.

## Voorbeeld Scenario

### Situatie: Ham Radio Operator met USB Audio Interface

**Hardware:**
- Android tablet met OTG
- USB audio interface aangesloten (voor portofoon TX audio)
- Ingebouwde speaker voor Mumble RX audio

**Configuratie:**
```kotlin
val deviceSettings = AudioDeviceSettings(
    txDeviceId = 12,  // USB Audio Interface
    rxDeviceId = 2,   // Built-in Speaker
    preferSeparateDevices = true,
    txDeviceName = "USB Audio Interface",
    rxDeviceName = "Built-in Speaker"
)
```

**Resultaat:**
- Portofoon audio via USB → Mumble (TX)
- Mumble audio → Tablet speaker (RX)
- **Geen feedback/loopback!**

## Troubleshooting

### Devices worden niet gevonden
- Check of USB device permissions zijn verleend
- Herstart de app na het aansluiten van USB device
- Check Android versie (6.0+ vereist)

### Audio loopback blijft bestaan
- Controleer of beide devices echt verschillend zijn
- Check `getRoutedInputDevice()` en `getRoutedOutputDevice()` om te zien wat actief is
- Test met ingebouwde speaker voor RX (niet USB)

### Settings worden niet toegepast
- Check logs voor foutmeldingen
- Zorg dat HumlaService geïnitialiseerd is
- Roep `applyAudioDeviceSettings()` aan na verbinding

## Toekomstige Verbeteringen

Mogelijke uitbreidingen:
- [ ] Automatische device selectie bij USB connect/disconnect
- [ ] Device change callbacks voor real-time UI updates
- [ ] Audio routing presets (scenarios opslaan)
- [ ] Visualisatie van audio flow (TX/RX diagram)
- [ ] Advanced device filtering (sample rate, channels, etc.)

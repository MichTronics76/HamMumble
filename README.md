# HamMumble

<div align="center">

**A Modern Mumble Client for Ham Radio Operators**

[![Android](https://img.shields.io/badge/Android-8.0%2B-green.svg)](https://www.android.com/)
[![Kotlin](https://img.shields.io/badge/Kotlin-1.8-blue.svg)](https://kotlinlang.org/)
[![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-Material%203-purple.svg)](https://developer.android.com/jetpack/compose)
[![License](https://img.shields.io/badge/License-GPL--3.0-orange.svg)](LICENSE)

*Professional VoIP communication for amateur radio with modern Android technology*

[Features](#-features) • [Installation](#-installation) • [Quick Start](#-quick-start) • [User Guide](#-user-guide) • [Troubleshooting](#-troubleshooting) • [Contributing](#-contributing)

</div>

---

## 📱 About

HamMumble is a feature-rich, production-ready Mumble VoIP client specifically designed for ham radio operators. Built with modern Android technologies (Jetpack Compose & Material 3), it provides a clean, intuitive interface for voice communication over Mumble servers with specialized features for amateur radio applications including remote gateway control, repeater linking, and emergency communications.

### Why HamMumble?

- ✅ **Purpose-Built for Ham Radio**: Features tailored for amateur radio operators
- ✅ **Modern Architecture**: Built with latest Android development best practices
- ✅ **Hardware Integration**: Serial PTT support for external radio equipment
- ✅ **Professional Audio**: VU meters, adjustable gain, voice activity detection
- ✅ **Secure**: Client certificate authentication support
- ✅ **Reliable**: Auto-reconnect, channel persistence, connection monitoring
- ✅ **Open Source**: GPL-3.0 licensed, community-driven development

---

## ✨ Features

### 🎙️ Voice Communication

- **Multiple Transmission Modes**
  - Voice Activity Detection (VAD) with adjustable sensitivity
  - Push-to-Talk (PTT) with visual feedback
  - Continuous transmission mode
  
- **Advanced Audio Processing**
  - Real-time VU meters for input/output monitoring ✨
  - Adjustable input gain (0.0 - 3.0x)
  - Configurable voice hold timer (50ms - 2000ms)
  - Start tone for VOX-activated transmitters ✨
  - High-quality audio codecs (Opus, CELT, Speex)

- **Roger Beep System** ✨
  - Customizable tone frequency (300Hz - 3000Hz)
  - Adjustable volume control
  - Support for custom audio files (MP3/WAV)
  - Properly sequenced after voice hold timer
  - Server-side and client-side beep support

### 🔌 Hardware Integration

- **Serial PTT Support**
  - USB serial adapter compatibility
  - DTR/RTS signal control
  - Configurable baud rates
  - Connection status monitoring
  - Test functionality for verification

- **External Device Control**
  - Support for hardware PTT buttons
  - Radio interface compatibility
  - Gateway control capabilities

### 🌐 Server Management

- **Multi-Server Support**
  - Save unlimited server configurations
  - Quick-connect from server list
  - Auto-connect on app start (optional)
  - Auto-join specific channels ✨
  
- **Connection Features**
  - Real-time latency monitoring with color-coded indicators
    - 🟢 Excellent: < 50ms
    - 🟡 Good: < 100ms  
    - 🟠 Fair: < 200ms
    - 🔴 Poor: ≥ 200ms
  - Automatic reconnection on network loss ✨
  - Connection state persistence
  - Channel position memory

### 🔐 Security & Authentication

- **Client Certificate Support** ✨
  - PKCS#12 (.p12) certificate import
  - Password-protected certificates
  - Automatic certificate selection
  - User registration with certificates
  - Registered user visual indicators (⭐)

### 💬 Communication

- **Channel System**
  - Browse hierarchical channel structure
  - Join channels with one tap
  - User count display
  - Current channel highlighting
  
- **User Management**
  - Real-time user list with status indicators
  - Registered user identification (star icon ⭐) ✨
  - Mute/Deafen status visualization
  - Talk state indicators
  - Bold text for speaking users

- **Text Chat**
  - Send messages to current channel
  - Chat history with timestamps
  - Sender identification
  - Scrollable message view

### 🎨 Modern User Interface

- **Material 3 Design**
  - Dynamic color theming
  - Smooth animations and transitions
  - Responsive layouts for all screen sizes
  - Optimized for Android boxes and tablets ✨
  
- **Intuitive Controls**
  - Large, easy-to-press PTT button
  - Quick access mute/deafen toggles
  - Visual feedback for all actions
  - Status indicators in notification bar

- **Accessibility**
  - Touch-friendly interface
  - Clear visual status indicators
  - High-contrast color schemes
  - Proper scroll behavior on all devices ✨

---

## 📋 Requirements

### Minimum System Requirements

- **Android Version**: 8.0 Oreo (API 26) or higher
- **RAM**: 2GB minimum, 4GB recommended
- **Storage**: 50MB for app installation
- **Permissions**:
  - Microphone (required for voice communication)
  - Internet (required for server connectivity)
  - USB Host (optional, for serial PTT adapters)
  - Foreground Service (for background operation)

### Supported Devices

- **Smartphones** running Android 8.0+
- **Tablets** (optimized for larger screens)
- **Android TV Boxes** ✨
  - Fully compatible with cheap Android TV boxes and set-top boxes
  - Perfect for fixed-station amateur radio setups
  - UI optimized with scroll fixes for TV box navigation
  - Tested on various budget devices (X96, T95, H96, and similar)
  - Great for dedicated Mumble gateway or repeater link installations
  - Connect via HDMI to any monitor/TV for convenient shack integration
  - USB OTG support for external PTT hardware
- **Devices with USB OTG support** (for serial PTT integration)

---

## 🚀 Installation

### Method 1: Download Pre-built APK (Recommended)

1. Download the latest APK from [Releases](https://github.com/MichTronics76/hammumble/releases)
2. Enable "Install from Unknown Sources" in Android settings
3. Open the downloaded APK file
4. Follow the installation prompts
5. Grant required permissions when prompted

### Method 2: Build from Source

**Prerequisites:**
- Android Studio Hedgehog (2023.1.1) or newer
- JDK 17 or higher
- Android SDK with API 34
- NDK for native library compilation

**Build Steps:**

1. **Clone the repository**
   ```bash
   git clone https://github.com/MichTronics76/hammumble.git
   cd hammumble
   ```

2. **Open in Android Studio**
   - Launch Android Studio
   - Select "Open" → Navigate to cloned directory
   - Wait for Gradle sync to complete

3. **Build the project**
   
   Using command line:
   ```bash
   # Windows
   .\gradlew.bat assembleDebug
   
   # Linux/macOS
   ./gradlew assembleDebug
   ```
   
   Or use Android Studio: Build → Build Bundle(s) / APK(s) → Build APK(s)

4. **Install on device**
   
   ```bash
   # Via command line (Windows)
   adb install -r app\build\outputs\apk\debug\app-debug.apk
   
   # Via command line (Linux/macOS)
   adb install -r app/build/outputs/apk/debug/app-debug.apk
   ```
   
   Or use Android Studio's Run button (▶️) with device connected

**Build Output:**
- Debug APK: `app/build/outputs/apk/debug/app-debug.apk`
- Release APK: `app/build/outputs/apk/release/app-release.apk` (after signing)

---

## 🏁 Quick Start

### First Launch Setup

1. **Launch HamMumble** - Grant microphone permission when prompted

2. **Add Your First Server**
   - Tap the **"Add Server"** button (➕)
   - Fill in server details:
     ```
     Server Name: My Mumble Server
     Address: mumble.example.com
     Port: 64738
     Username: YourCallsign
     Password: (if required)
     ```
   - (Optional) Enable "Auto Connect" for automatic connection on app start
   - (Optional) Set "Auto Join Channel" to jump to a specific channel
   - Tap **"Save"**

3. **Connect to Server**
   - Tap the server in your list
   - Wait for connection (watch the status indicator)
   - You're connected! The channel tree will appear

4. **Start Talking**
   - **PTT Mode**: Press and hold the large center button
   - **VAD Mode**: Just speak - the app detects your voice
   - Watch the status indicators for confirmation

### Certificate Authentication (Optional)

If your server requires client certificates:

1. **Obtain Certificate**
   - Get a `.p12` certificate file from your server administrator
   - Or generate one using the Mumble desktop client

2. **Import Certificate**
   - Copy the `.p12` file to your Android device
   - In server settings, tap "Certificate Path"
   - Browse and select your `.p12` file
   - Enter the certificate password
   - Save the server configuration

3. **Automatic Registration**
   - Enable "Register with Server" option
   - On first connect, you'll be automatically registered
   - Registered users show a star icon (⭐) next to their name

---

## 📖 User Guide

### Connecting to Servers

**From Disconnected State:**
1. View your server list on the main screen
2. Tap any server to connect
3. Connection status shown in toolbar
4. Channel tree appears when connected

**Server Options:**
- Tap and hold a server for options menu
- Edit: Modify server settings
- Delete: Remove from list
- Set as default: Auto-connect on startup

### Transmission Modes

**Push-to-Talk (PTT)**
- Press and hold the large circular button
- Speak while holding
- Release to stop transmitting
- Roger beep plays after release (if enabled)
- Visual feedback shows transmission state

**Voice Activity Detection (VAD)**
- Simply start speaking
- App automatically detects voice
- Adjust sensitivity in Settings → Audio
- Voice hold timer prevents cutting off
- Use VU meters to calibrate threshold

**Continuous Transmission**
- Always transmitting
- Use with caution - may cause interference
- Suitable for monitoring or specific applications

### Audio Configuration

Access Settings via the menu (☰) → Settings

**Audio Volume & Gain**
- **Input Gain**: Boost microphone sensitivity (0.0 - 3.0x)
  - Start with 1.0 and increase if others can't hear you
  - Watch input VU meter to avoid clipping (red)
- **Output Volume**: Adjust speaker/headphone level
  - Use output VU meter to monitor received audio

**Voice Activity Detection**
- **Transmission Mode**: Select PTT, VAD, or Continuous
- **Detection Threshold**: Set voice trigger sensitivity (0.0 - 1.0)
  - Lower values = more sensitive (picks up quieter sounds)
  - Higher values = less sensitive (requires louder voice)
  - Use the input VU meter to find optimal setting
- **Voice Hold Time**: Duration to keep transmitting after speech stops
  - Recommended: 200-500ms for natural conversation
  - Shorter for rapid exchanges, longer for slower speech

**VU Meters** ✨
- **Input VU Meter**: Shows microphone input level
  - Green: Good level (0-60%)
  - Yellow: High level (60-80%)
  - Red: Clipping - reduce gain! (80-100%)
- **Output VU Meter**: Shows received audio level
- **Peak Indicators**: Show maximum levels with slow decay
- Use these to calibrate your audio settings perfectly!

**Roger Beep Configuration**
- **Enable/Disable**: Toggle roger beep on/off
- **Tone Frequency**: Adjust pitch (300Hz - 3000Hz)
  - Lower: Deep tone (recommended for most radios)
  - Higher: Sharp tone
- **Volume**: Set beep loudness (0.0 - 1.0)
- **Custom Audio File**: Use your own MP3/WAV file
  - Place file in device storage
  - Select via "Custom Beep File" option
- **Beep Timing**: Always plays AFTER voice hold timer completes

**Start Tone** (for VOX transmitters)
- **Enable**: Activate pre-transmission tone
- **Duration**: Set tone length before voice
- **Frequency**: Adjust tone pitch
- Helps open transmitter VOX before voice starts

### Serial PTT Setup

For external radio control via USB:

1. **Connect Hardware**
   - Connect USB serial adapter to Android device via OTG cable
   - Connect adapter to radio's PTT circuit

2. **Configure in App**
   - Settings → Serial PTT
   - Tap "Select USB Device"
   - Choose your adapter from the list
   - Configure settings:
     ```
     Baud Rate: 9600 (most common)
     Signal Type: DTR or RTS (depends on adapter)
     ```

3. **Test Connection**
   - Use "Test PTT" button
   - Should activate radio PTT
   - Check connection indicator (green = good)

4. **Usage**
   - Once configured, PTT button controls serial output
   - Works in all transmission modes
   - Status shown in notification bar

**Compatible Adapters:**
- FTDI FT232-based adapters (recommended)
- Prolific PL2303 adapters
- CH340 adapters
- Most USB-to-serial converters with Android support

### Channel Navigation

**Joining Channels**
- Open drawer menu (swipe from left or tap ☰)
- View channel tree structure
- Tap any channel to join
- Your current channel is highlighted
- User count shown next to channel name

**Channel Features**
- Hierarchical structure (parent/child channels)
- Expandable/collapsible sections
- Real-time user updates
- Permission-based access

### User List & Status

**User Indicators**
- ⭐ **Star Icon**: Registered user (has permanent account on server)
- 🔒 **Lock Icon**: User is deafened
- ✖️ **X Icon**: User is muted
- **Bold Name**: User is currently speaking
- **Status Circle**: 
  - Green = Speaking
  - Red = Muted/Deafened
  - Gray = Idle

**Understanding Registration**
- Registered users have permanent accounts
- Star icon (⭐) appears next to their name
- Registration requires client certificate
- Non-registered users are temporary (guest users)

### Text Chat

**Sending Messages**
1. Open chat view from main screen
2. Type message in text box
3. Tap send button
4. Message sent to current channel

**Chat Features**
- Timestamp for each message
- Sender name clearly displayed
- Auto-scroll to latest messages
- Chat history persists during session

### Quick Controls

**Main Screen Buttons**
- **Mute** (🔇): Toggle microphone on/off
  - Red = Muted
  - Default color = Active
- **Deafen** (🔊): Toggle all audio on/off
  - Deafening also mutes you
  - Red = Deafened
- **PTT Button** (⚫): Large center button for Push-to-Talk
  - Press and hold to transmit
  - Visual feedback during transmission

**Notification Controls**
- Quick mute/deafen from notification
- Connection status always visible
- Tap notification to return to app

---

## 🔧 Advanced Configuration

### Certificate Management

**Creating Certificates**
1. Use Mumble desktop client to generate certificate
2. Export as PKCS#12 (.p12) format with password
3. Transfer file to Android device (via USB, cloud, email, etc.)

**Importing Certificates**
1. Server Settings → Certificate Path
2. Browse to your .p12 file location
3. Enter certificate password
4. Save configuration
5. Connect - certificate used automatically

**Automatic Registration**
- Enable "Register with Server" in server settings
- First connection automatically registers you
- Your userId changes from -1 to a positive number
- Star icon (⭐) appears next to your name
- Future connections use same registered account

### Performance Tuning

**Low Bandwidth Situations**
- Reduce audio quality in settings
- Use lower bitrate codec (Speex)
- Disable roger beep sounds
- Lower sample rate

**High Quality Audio**
- Use Opus codec (highest quality)
- Increase bitrate setting
- Ensure stable, fast network connection
- Use wired headphones for best audio

**Battery Optimization**
- Use PTT mode instead of continuous VAD
- Lower audio quality when on mobile data
- Disable unnecessary features (VU meters when not calibrating)
- Disconnect when not actively using

### Network Configuration

**Firewall/NAT Traversal**
- Default port: 64738 (TCP/UDP)
- Ensure port is accessible through firewall
- Some corporate/public networks may block VoIP traffic
- Try mobile data if WiFi blocks connection

**Connection Quality**
- Check server address and port are correct
- Verify internet connectivity
- Try different network (WiFi vs Mobile data)
- Monitor latency indicator (keep under 100ms for best quality)
- Check server status with administrator

### Using HamMumble on Android TV Boxes 📺

**Why Android TV Boxes are Perfect for Ham Radio**

Android TV boxes are an excellent, cost-effective solution for fixed-station amateur radio Mumble installations. These devices typically cost $20-$50 and provide a dedicated, always-on platform for your shack.

**Recommended Use Cases**
- 📻 **Repeater Link Station**: Connect your repeater to Mumble networks
- 🏠 **Fixed Gateway**: Permanent connection to your favorite Mumble server
- 🎙️ **Remote Base Station**: Control remote radio equipment via Mumble
- 🔗 **Network Hub**: Bridge multiple radio systems together
- 🆘 **Emergency Communications**: Reliable, dedicated backup communication system

**Setup Recommendations**
1. **Hardware Selection**
   - Any Android TV box with Android 8.0+ works
   - Budget options: X96, T95, H96 series (all tested and working)
   - Look for devices with:
     - At least 2GB RAM (recommended)
     - USB OTG support (for PTT hardware)
     - Ethernet port (more stable than WiFi)
     - HDMI output (connect to any monitor/TV)

2. **Installation Tips**
   - Connect via Ethernet for most stable connection
   - Enable "Developer Options" → "Stay Awake" (keeps screen on)
   - Disable sleep/power saving modes
   - Install from USB drive or download via web browser
   - Use "Unknown Sources" to sideload APK

3. **Navigation**
   - Use TV box remote for navigation
   - App UI is optimized with scroll fixes for remote control
   - Mouse support via USB works well
   - Wireless keyboard/mouse recommended for initial setup

4. **Integration with Radio Equipment**
   - Connect USB serial adapter via USB port
   - Configure Serial PTT for external radio control
   - Set auto-connect to server for instant operation
   - Enable auto-join to specific channel
   - Roger beep helps confirm transmissions

5. **Reliability Tips**
   - Enable auto-reconnect feature
   - Set channel to auto-join on connect
   - Use wired Ethernet instead of WiFi when possible
   - Set display timeout to "Never" or very long duration
   - Configure static IP for consistent network access
   - Consider UPS power backup for critical installations

**Example Budget Setup ($50-$80 total)**
- Android TV Box (X96/T95): $25-$35
- USB serial adapter (FTDI): $5-$10
- USB hub (powered): $8-$15
- Cables and accessories: $5-$10
- **Total**: Reliable, dedicated Mumble gateway for less than $100!

**Performance Notes**
- Low-end boxes handle voice communication perfectly
- VU meters and animations work smoothly on budget hardware
- Multiple simultaneous users supported
- Very low power consumption (~5-10W typical)
- Silent operation (no fans in most models)

---

## 🐛 Troubleshooting

### Connection Problems

**"Cannot connect to server"**
- ✅ Verify server address is correct (no spaces, correct domain)
- ✅ Check port number (default: 64738)
- ✅ Ensure internet connection is active
- ✅ Try pinging server from another device
- ✅ Confirm server is online
- ✅ Verify firewall not blocking connection
- ✅ Try mobile data instead of WiFi (or vice versa)

**"Certificate authentication failed"**
- ✅ Ensure .p12 file is not corrupted
- ✅ Verify certificate password is correct
- ✅ Check certificate hasn't expired
- ✅ Confirm server accepts client certificates
- ✅ Try re-exporting certificate from Mumble desktop client
- ✅ Check file permissions (readable by app)

**Auto-reconnect not working**
- ✅ Verify auto-reconnect is enabled in settings
- ✅ Ensure app isn't being killed by battery optimization
- ✅ Disable battery optimization for HamMumble:
  - Settings → Apps → HamMumble → Battery → Unrestricted
- ✅ Check network stability (frequent disconnections prevent reconnect)

### Audio Issues

**Others can't hear me**
- ✅ Grant microphone permission in Android settings
- ✅ Check mute status (button should NOT be red)
- ✅ Increase input gain in Settings → Audio
- ✅ Verify correct microphone selected (built-in vs Bluetooth)
- ✅ Test mic with another app (voice recorder) to confirm it works
- ✅ Watch input VU meter - should show green/yellow when speaking
- ✅ Try speaking louder or moving closer to mic

**I can't hear others**
- ✅ Check deafen status (button should NOT be red)
- ✅ Increase device volume with volume buttons
- ✅ Verify output volume in settings
- ✅ Watch output VU meter when others speak (should show activity)
- ✅ Test speaker with music/video player
- ✅ Check if headphones are connected but removed
- ✅ Try toggling deafen off/on

**Audio cutting off / choppy**
- ✅ Increase voice hold time in Settings → Audio (try 300-500ms)
- ✅ Lower detection threshold (VAD mode) - make it more sensitive
- ✅ Use PTT mode instead of VAD for better control
- ✅ Check network stability - watch latency indicator
- ✅ Reduce background app usage
- ✅ Close other apps using microphone

**Echo or feedback**
- ✅ Use headphones instead of speaker phone
- ✅ Reduce output volume
- ✅ Ensure others in same room use headphones
- ✅ Increase distance between microphone and speaker
- ✅ Enable echo cancellation if available

**Roger beep not playing**
- ✅ Verify roger beep is enabled in settings
- ✅ Check volume setting (increase if too quiet)
- ✅ Ensure voice hold timer completes first (beep plays after)
- ✅ Verify custom audio file path (if using custom file)
- ✅ Test with default tone first
- ✅ Check file format (MP3 or WAV)

**VU meters not moving**
- ✅ Ensure microphone permission granted
- ✅ Check if in correct settings section
- ✅ Speak louder for input meter
- ✅ Have someone talk for output meter
- ✅ Restart app if meters frozen

### Serial PTT Issues

**USB device not detected**
- ✅ Check OTG cable connection is secure
- ✅ Verify device has USB host support (most modern phones do)
- ✅ Try different USB port/cable
- ✅ Restart app with device already connected
- ✅ Check adapter compatibility (FTDI chipsets work best)
- ✅ Try adapter on PC first to verify it works

**PTT not triggering radio**
- ✅ Verify correct wiring to radio PTT circuit
- ✅ Try switching between DTR/RTS mode in settings
- ✅ Test with multimeter (should show voltage change when PTT pressed)
- ✅ Check baud rate setting (9600 is most common)
- ✅ Use "Test PTT" function in app
- ✅ Verify radio PTT circuit works (test with manual switch)

**Intermittent PTT operation**
- ✅ Check all cable connections are secure
- ✅ Verify stable USB connection (not loose)
- ✅ Test adapter with PC to rule out hardware issues
- ✅ Try different USB cable
- ✅ Check for Android USB driver updates
- ✅ Disable power saving for USB in Android settings

### App Performance

**App crashes on startup**
- ✅ Clear app cache: Android Settings → Apps → HamMumble → Storage → Clear Cache
- ✅ Clear app data (will reset settings): Clear Storage
- ✅ Uninstall and reinstall application
- ✅ Check Android version compatibility (minimum 8.0)
- ✅ Ensure sufficient storage space (at least 100MB free)
- ✅ Check for corrupt server configuration files

**Scrolling not working (Android boxes)**
- ✅ Update to latest app version (scroll fix included in v9+)
- ✅ Try touch input if using mouse
- ✅ Try mouse input if using touch
- ✅ Restart app
- ✅ Check for Android OS updates

**High battery drain**
- ✅ Use PTT instead of continuous VAD monitoring
- ✅ Disconnect from server when not in use
- ✅ Lower audio quality settings
- ✅ Reduce screen brightness
- ✅ Disable VU meters when not calibrating
- ✅ Enable battery saver mode on Android

**App not staying in foreground**
- ✅ Disable battery optimization for HamMumble
- ✅ Lock app in recent apps menu (varies by manufacturer)
- ✅ Add to protected apps list (if available)
- ✅ Enable "Autostart" permission (Xiaomi, Huawei devices)

### Getting Help

If issues persist after trying troubleshooting steps:

1. **Collect Debug Information**
   - Enable developer options on Android
   - Use `adb logcat -s HamMumble MumbleService` to capture app logs
   - Note exact error messages
   - Take screenshots of the problem

2. **Report a Bug**
   - Visit [GitHub Issues](https://github.com/MichTronics76/hammumble/issues)
   - Include:
     - Android version and device model
     - App version
     - Steps to reproduce the problem
     - Logcat output (if available)
     - Screenshots showing the issue
   - Use the bug report template

3. **Community Support**
   - [GitHub Discussions](https://github.com/MichTronics76/hammumble/discussions) for questions
   - Email: support@example.com for private issues
   - Ham radio forums for general Mumble advice

---

## 🏗️ Architecture

### Technology Stack

- **Language**: Kotlin 1.8
- **UI Framework**: Jetpack Compose with Material 3
- **Architecture**: MVVM (Model-View-ViewModel)
- **Concurrency**: Kotlin Coroutines & Flow
- **Networking**: Humla library (Mumble protocol implementation)
- **Audio**: Android AudioTrack & AudioRecord APIs
- **Storage**: SharedPreferences with JSON serialization
- **Dependency Injection**: Manual DI (lightweight approach)

### Project Structure

```
hammumble/
├── app/
│   └── src/main/java/com/hammumble/
│       ├── ui/
│       │   ├── screens/           # Compose UI screens
│       │   │   ├── MainScreen.kt
│       │   │   ├── ServerListScreen.kt
│       │   │   └── SettingsScreen.kt
│       │   ├── components/        # Reusable UI components
│       │   │   ├── VuMeter.kt
│       │   │   └── MumbleComponents.kt
│       │   ├── viewmodel/         # ViewModels for state management
│       │   │   └── MumbleViewModel.kt
│       │   └── theme/             # Material 3 theming
│       │       └── Theme.kt
│       ├── service/               # Background Mumble service
│       │   └── MumbleService.kt
│       ├── network/               # Connection management
│       │   ├── MumbleConnection.kt
│       │   └── MumbleConnectionManager.kt
│       ├── audio/                 # Audio processing & feedback
│       │   ├── AudioFeedbackManager.kt
│       │   └── RogerBeepPlayer.kt
│       ├── hardware/              # Serial PTT integration
│       │   └── SerialPttManager.kt
│       ├── security/              # Certificate management
│       │   └── CertificateManager.kt
│       └── data/                  # Data models & settings
│           └── Models.kt
└── libraries/
    └── humla/                     # Mumble protocol library (GPL-3.0)
        ├── src/main/java/
        └── src/main/jni/         # Native audio codecs
```

### Key Components

- **MumbleViewModel**: Central state management, connects UI to service
- **MumbleService**: Background service maintaining Mumble connection
- **AudioFeedbackManager**: Handles roger beep and audio notifications
- **SerialPttManager**: USB serial adapter integration for external PTT
- **MumbleConnectionManager**: Network connection handling and protocol
- **VuMeter**: Real-time audio level visualization component

### Development Principles

- **Separation of Concerns**: UI, business logic, and data layers isolated
- **Reactive Programming**: StateFlow for reactive state management
- **Composition over Inheritance**: Jetpack Compose component model
- **Testability**: ViewModels and business logic unit testable
- **Performance**: Efficient state updates, lazy loading, proper lifecycle management

---

## 🎯 Roadmap

### Completed Features ✅

- [x] Voice Activity Detection (VAD)
- [x] Push-to-Talk (PTT)
- [x] Serial PTT support (USB adapters)
- [x] Roger beep (custom tones and files)
- [x] Certificate authentication
- [x] Auto-reconnect functionality
- [x] Auto-join channels
- [x] VU meters for audio monitoring
- [x] Start tone for VOX transmitters
- [x] Registered user indicators
- [x] Scroll fix for Android boxes
- [x] Material 3 UI

### Planned Features 🚧

**High Priority**
- [ ] Private messaging between users
- [ ] Channel descriptions/comments view
- [ ] Voice recording and playback
- [ ] Bluetooth PTT support
- [ ] Dark/Light theme toggle
- [ ] Landscape mode optimization

**Medium Priority**
- [ ] Widget for home screen quick controls
- [ ] GPS position sharing
- [ ] DTMF tone sending
- [ ] User avatars display
- [ ] Multi-language support
- [ ] Backup/restore settings

**Future Considerations**
- [ ] Remote control server (multi-gateway management)
- [ ] Audio equalization settings
- [ ] Noise suppression filters
- [ ] Channel recording capability
- [ ] Integration with APRS
- [ ] Emergency broadcast mode

### Known Issues 🐛

- Some USB serial adapters may have compatibility issues (FTDI recommended)
- Certificate import UI could be more intuitive
- Occasional audio sync issues on very slow connections
- Battery optimization on some manufacturers requires manual configuration

---

## 🤝 Contributing

We welcome contributions from the community! Whether you're fixing bugs, adding features, improving documentation, or testing on different devices, your help is appreciated.

### How to Contribute

1. **Fork the repository**
   - Click the "Fork" button on GitHub
   - Clone your fork locally

2. **Create a feature branch**
   ```bash
   git checkout -b feature/amazing-feature
   ```

3. **Make your changes**
   - Follow Kotlin coding conventions
   - Use Jetpack Compose for UI components
   - Write meaningful commit messages
   - Test on multiple Android versions if possible

4. **Commit your changes**
   ```bash
   git commit -m 'Add amazing feature: detailed description'
   ```

5. **Push to your branch**
   ```bash
   git push origin feature/amazing-feature
   ```

6. **Open a Pull Request**
   - Go to the original repository on GitHub
   - Click "New Pull Request"
   - Select your branch
   - Describe your changes in detail

### Development Guidelines

**Code Style**
- Follow [Kotlin Coding Conventions](https://kotlinlang.org/docs/coding-conventions.html)
- Use meaningful variable and function names
- Add comments for complex logic
- Keep functions small and focused

**UI Development**
- Use Jetpack Compose for all UI
- Follow Material 3 design guidelines
- Ensure responsive layouts
- Test on different screen sizes

**Testing**
- Test on Android 8.0+
- Verify on different manufacturers (Samsung, Google, Xiaomi, etc.)
- Check both portrait and landscape orientations
- Test with different network conditions

**Documentation**
- Update README for new features
- Add inline code documentation
- Update CHANGELOG.md
- Include screenshots for UI changes

### Reporting Issues

**Bug Reports** should include:
- Android version and device model
- App version
- Steps to reproduce
- Expected vs actual behavior
- Logcat output if possible
- Screenshots or screen recordings

**Feature Requests** should describe:
- The problem you're trying to solve
- Proposed solution
- Any alternatives considered
- Mockups or examples if applicable

---

## 📄 License

This project is licensed under the **GNU General Public License v3.0** - see the [LICENSE](LICENSE) file for complete details.

### What This Means

✅ **You CAN**:
- Use this software for any purpose
- Study and modify the source code
- Distribute copies
- Distribute modified versions

❌ **You MUST**:
- Disclose source code of modifications
- License derivatives under GPL-3.0
- Include copyright notice
- State changes made to the code

### Third-Party Libraries

| Library | License | Purpose |
|---------|---------|---------|
| [Humla](https://github.com/quite/humla) | GPL-3.0 | Mumble protocol implementation (fork of Jumble) |
| Opus Codec | BSD | High-quality audio codec |
| CELT Codec | BSD | Low-latency audio codec |
| Speex Codec | BSD | Speech audio codec |
| AndroidX Libraries | Apache 2.0 | Android Jetpack components |
| Jetpack Compose | Apache 2.0 | Modern UI toolkit |
| Kotlin | Apache 2.0 | Programming language |
| Material Icons | Apache 2.0 | UI icons |

---

## 🙏 Acknowledgments

### Special Thanks

- **[Humla](https://github.com/quite/humla) & [Jumble](https://github.com/acomminos/Jumble)**: Excellent Mumble protocol implementation
- **[Mumla](https://github.com/quite/mumla)**: Inspiration and reference implementation
- **Mumble Community**: Open protocol specification and server software
- **Ham Radio Community**: Testing, feedback, and feature suggestions
- **Contributors**: Everyone who has contributed code, bug reports, and ideas

### Inspired By

- Mumble Desktop Client
- Mumla Android Client
- Ham Radio emergency communication needs
- Remote repeater control requirements

---

## 📞 Support & Contact

### Getting Help

- **📖 Documentation**: Read this README and inline code docs
- **🐛 Bug Reports**: [GitHub Issues](https://github.com/MichTronics76/hammumble/issues)
- **💬 Discussions**: [GitHub Discussions](https://github.com/MichTronics/hammumble/discussions)

### Community

- **Development**: Active development on GitHub
- **Testing**: Beta testing program available
- **Feedback**: User feedback greatly appreciated

### Professional Support

For commercial deployments, custom features, or professional support:
- Email: info@cbjunkies.nl
- Include: Organization name, use case, requirements

---

## 📸 Screenshots

<!-- TODO: Add actual screenshots -->

<div align="center">

### Main Screen
*Connection status, PTT button, and quick controls*

### Server List
*Manage multiple Mumble servers with latency indicators*

### Channel Tree
*Browse and join channels with user counts*

### Settings - Audio
*VU meters, gain control, and voice detection settings*

### Settings - Serial PTT
*USB serial adapter configuration for hardware PTT*

</div>

---

## 🔄 Version History

See [CHANGELOG.md](CHANGELOG.md) for detailed version history.

**Latest Release: v1.0.0** (Production Ready)
- ✨ VU meters for audio monitoring
- ✨ Registered user indicators
- ✨ Certificate authentication
- ✨ Auto-reconnect and channel persistence
- ✨ Start tone for VOX transmitters
- ✨ Roger beep improvements
- 🐛 Android box scroll fix
- 🎨 Material 3 UI refresh

---

<div align="center">

## Made with ❤️ for the Ham Radio Community

**73 de HamMumble Team**

⭐ **Star this repo if you find it useful!** ⭐

[Report Bug](https://github.com/MichTronics76/hammumble/issues) • [Request Feature](https://github.com/MichTronics76/hammumble/issues) • [Contribute](CONTRIBUTING.md)

</div>

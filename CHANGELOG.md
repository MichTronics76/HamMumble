# Changelog

All notable changes to HamMumble will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [1.0.0] - 2025-10-18 - Production Release 🎉

### ✨ Added
- **VU Meters**: Real-time audio level visualization for input and output
  - Color-coded levels (green/yellow/red)
  - Peak indicators with decay animation
  - Smooth 150ms animations with FastOutSlowInEasing
  - Available in Settings → Audio sections
- **Registered User Indicators**: Star icon (⭐) shows which users have permanent accounts
  - Automatically updates when user registers
  - Visual distinction between registered and guest users
- **Certificate Authentication**: Full support for PKCS#12 (.p12) certificates
  - Certificate import from device storage
  - Password-protected certificate support
  - Automatic user registration with certificates
  - Certificate path persistence in server configuration
- **Auto-Join Channels**: Configure specific channel to join automatically after connecting
  - Per-server configuration
  - Optional setting in Add/Edit Server dialog
- **Start Tone**: Pre-transmission tone for VOX-activated transmitters
  - Helps open VOX before voice transmission
  - Configurable frequency and duration
  - Useful for remote gateway control
- **Roger Beep Improvements**:
  - Custom audio file support (MP3/WAV)
  - Server-side beep option
  - Properly sequenced after voice hold timer
  - Separate client and server beep controls
- **Auto-Reconnect**: Automatically reconnect to server on network loss
  - Configurable reconnect attempts
  - Returns to last channel after reconnection
  - Connection state persistence

### 🐛 Fixed
- **Android Box Scroll Issue**: Fixed scrolling problems on Android TV boxes and tablets
  - Proper touch event handling
  - Compatible with both mouse and touch input
- **Voice Hold Timer**: Roger beep now always plays AFTER voice hold timer completes
  - Prevents cutting off end of transmission
  - Better timing coordination
- **Certificate Loading**: Fixed NULL certificate path issue during connection
  - Proper parameter naming to avoid shadowing
  - Reloads server info before connection to get fresh certificate data
- **Build Warnings**: Resolved duplicate code blocks and scope issues
  - Fixed coroutine scope return statements
  - Added missing imports for isActive property

### 🎨 Changed
- **Material 3 UI**: Complete UI refresh with Material 3 design language
  - Dynamic color theming
  - Improved visual hierarchy
  - Better touch targets
- **VU Meter Animations**: Optimized for smooth performance
  - Increased animation duration from 50ms to 150ms
  - Reduced update frequency from 50ms to 100ms
  - Gentler decay animation (0.85 multiplier, 80ms interval)
- **User List Display**: Enhanced user status visualization
  - Bold text for speaking users
  - Multiple status indicators (registered, muted, deafened)
  - Improved color coding
- **Settings Organization**: Better grouped settings with clear sections
  - Audio Volume & Gain (with VU meters)
  - Voice Activity Detection (with VU meter for threshold calibration)
  - Roger Beep configuration
  - Serial PTT setup
  - Connection options

### 🏗️ Technical
- **Architecture**: MVVM pattern with Jetpack Compose
- **State Management**: Kotlin StateFlow for reactive updates
- **Audio Processing**: Enhanced audio feedback system
- **Concurrency**: Kotlin Coroutines for background operations
- **Storage**: JSON-based server configuration with SharedPreferences
- **Native Libraries**: NDK compilation for audio codecs (Opus, CELT, Speex)

### 📋 Requirements
- **Minimum Android Version**: 8.0 (API 26) or higher
- **Permissions Required**:
  - Microphone (required for voice transmission)
  - Internet (required for server connection)
  - USB Host (optional, for serial PTT hardware)
- **Recommended**:
  - Network: Stable WiFi or 4G/5G connection
  - Storage: 50MB free space
  - RAM: 512MB available memory

### 📦 Downloads
- **APK**: [Download from GitHub Releases](https://github.com/MichTronics76/hammumble/releases/tag/v1.0.0)
- **Source**: [View on GitHub](https://github.com/MichTronics76/hammumble)

---

## [0.9.0] - 2025-10-10 - Beta Release

### Added
- Serial PTT support for USB adapters
- Multiple transmission modes (PTT, VAD, Continuous)
- Text chat functionality
- Server management (add/edit/delete)
- Channel navigation and joining
- Mute and deafen controls
- Roger beep audio feedback
- Latency monitoring with color indicators
- Background service for persistent connection

### Fixed
- Audio synchronization issues
- Connection stability improvements
- Memory leak in audio processing
- UI state management issues

---

## [0.8.0] - 2025-10-01 - Alpha Release

### Added
- Initial Mumble protocol implementation
- Basic voice communication
- Server connection management
- Channel browsing
- User list display
- Push-to-Talk functionality
- Voice Activity Detection

### Known Issues
- Audio may cut off at end of transmission
- Occasional connection drops
- UI not optimized for tablets
- Limited error handling

---

## Future Releases

### Planned for v1.1.0 (Q4 2025)
- [ ] **Private Messaging**: Direct user-to-user messaging
- [ ] **Channel Info**: View channel descriptions and comments
- [ ] **Voice Recording**: Record and playback transmissions
- [ ] **Bluetooth PTT**: Support for Bluetooth PTT buttons
- [ ] **Theme Toggle**: Manual dark/light theme selection
- [ ] **Tablet Optimization**: Enhanced landscape mode for tablets
- [ ] **Enhanced Notifications**: Notification settings and customization

### Planned for v1.2.0 (Q1 2026)
- [ ] **Home Widget**: Quick control widget for home screen
- [ ] **GPS Sharing**: Share location with other users
- [ ] **DTMF Tones**: Generate DTMF tones for remote control
- [ ] **User Avatars**: Display custom user avatars
- [ ] **Internationalization**: Multi-language support (Dutch, German, French, Spanish)
- [ ] **Backup/Restore**: Settings and server configuration backup
- [ ] **Audio Filters**: Equalization and audio enhancement

### Future Considerations (v2.0+)
- **Advanced Features**:
  - Remote control server for multi-gateway management
  - Advanced audio processing (noise suppression, AGC)
  - Channel recording capability with scheduling
  - APRS integration for position reporting
  - Emergency broadcast mode with priority override
  - End-to-end encrypted private channels
- **Hardware Integration**:
  - CAT control for transceivers
  - GPIO support for Raspberry Pi
  - Support for additional PTT interfaces
- **Professional Features**:
  - Multi-server monitoring dashboard
  - Advanced statistics and analytics
  - Logging and audit trails
  - API for third-party integration

---

## Migration Notes

### Upgrading from 0.9.x to 1.0.0
- Server configurations are automatically migrated
- Certificate settings are preserved
- No manual action required
- Settings may reset to defaults (backup recommended)

### Upgrading from 0.8.x to 0.9.x
- Manual server re-configuration required
- Serial PTT settings need to be reconfigured
- Audio settings reset to defaults

---

## 📞 Support & Contributing

### Get Help
- **GitHub Issues**: [Report bugs or request features](https://github.com/MichTronics76/hammumble/issues)
- **GitHub Discussions**: [Community support and questions](https://github.com/MichTronics76/hammumble/discussions)
- **Documentation**: [Full user guide and API docs](https://github.com/MichTronics76/hammumble/wiki)
- **Email**: hammumble.support@example.com

### Contributing
We welcome contributions! See [CONTRIBUTING.md](CONTRIBUTING.md) for guidelines.

### Version History
- **v1.0.0** (2025-10-18): Production release with full feature set
- **v0.9.0** (2025-10-10): Beta release with serial PTT support
- **v0.8.0** (2025-10-01): Alpha release with basic functionality

---

## 📝 Changelog Legend

| Icon | Type | Description |
|------|------|-------------|
| ✨ | **Added** | New features and functionality |
| 🐛 | **Fixed** | Bug fixes and corrections |
| 🎨 | **Changed** | UI/UX improvements and visual updates |
| 🏗️ | **Technical** | Architecture, code quality, and technical changes |
| 📋 | **Requirements** | System requirements and dependency updates |
| ⚠️ | **Deprecated** | Features marked for removal in future versions |
| 🗑️ | **Removed** | Deleted features or deprecated code |
| 🔒 | **Security** | Security patches and vulnerability fixes |
| ⚡ | **Performance** | Performance improvements and optimizations |
| 📖 | **Documentation** | Documentation updates and improvements |

---

## 📄 License

HamMumble is licensed under the GNU General Public License v3.0 (GPL-3.0).
See [LICENSE](LICENSE) file for full details.

**Copyright © 2025 PD4MV Michel van Veen**

This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.

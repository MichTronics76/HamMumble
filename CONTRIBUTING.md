# Contributing to HamMumble

Thank you for considering contributing to HamMumble! We welcome contributions from the community, whether they're bug reports, feature suggestions, code improvements, or documentation enhancements.

## 📋 Table of Contents

- [Code of Conduct](#code-of-conduct)
- [How Can I Contribute?](#how-can-i-contribute)
  - [Reporting Bugs](#reporting-bugs)
  - [Suggesting Features](#suggesting-features)
  - [Code Contributions](#code-contributions)
  - [Documentation](#documentation)
- [Development Setup](#development-setup)
- [Coding Guidelines](#coding-guidelines)
- [Pull Request Process](#pull-request-process)
- [Community](#community)

## Code of Conduct

This project adheres to a Code of Conduct. By participating, you are expected to uphold this code. Please be respectful, inclusive, and professional in all interactions.

### Our Standards

- **Be Respectful**: Value different viewpoints and experiences
- **Be Collaborative**: Work together towards common goals
- **Be Professional**: Focus on what is best for the project and community
- **Be Patient**: Remember that volunteers contribute in their spare time

## How Can I Contribute?

### Reporting Bugs

Before submitting a bug report:
1. **Check existing issues** to avoid duplicates
2. **Test on latest version** to ensure the bug still exists
3. **Gather information** about your environment

When submitting a bug report, include:
- **Title**: Clear, descriptive summary
- **Description**: Detailed explanation of the issue
- **Steps to Reproduce**: Numbered list to recreate the bug
- **Expected Behavior**: What should happen
- **Actual Behavior**: What actually happens
- **Environment**:
  - Android version
  - Device model and manufacturer
  - App version
  - Network type (WiFi/Mobile)
- **Logs**: Logcat output if possible
- **Screenshots**: Visual evidence if relevant

**Example Bug Report:**

```markdown
## Battery drains rapidly when connected

**Description:**
App causes significant battery drain even when idle and connected to server.

**Steps to Reproduce:**
1. Connect to server
2. Leave app in background for 1 hour
3. Check battery usage in Android settings

**Expected:** Minimal battery usage in idle state
**Actual:** 15% battery drain in 1 hour

**Environment:**
- Android 13
- Samsung Galaxy S21
- App v1.0.0
- WiFi connection

**Logs:**
[Attach logcat or relevant logs]
```

### Suggesting Features

Feature requests are welcome! Before suggesting:
1. **Check existing suggestions** in Issues and Discussions
2. **Consider the scope** - does it fit HamMumble's purpose?
3. **Think about implementation** - is it technically feasible?

When suggesting a feature:
- **Title**: Clear feature name
- **Problem**: What problem does this solve?
- **Solution**: Proposed implementation
- **Alternatives**: Other approaches considered
- **Use Case**: Real-world scenario where this is needed
- **Mockups**: Visual designs if applicable (screenshots, drawings)

**Example Feature Request:**

```markdown
## Add support for Bluetooth PTT devices

**Problem:**
Currently only USB serial adapters are supported for external PTT. 
Many users have Bluetooth PTT buttons that would be more convenient.

**Proposed Solution:**
Add Bluetooth device scanning and pairing in Settings → PTT.
Support standard Bluetooth HID button presses.

**Alternatives Considered:**
- Using Bluetooth serial (SPP) - requires different pairing
- Third-party app integration - less seamless

**Use Case:**
Mobile operators want wireless PTT without cables when away from vehicle.

**Mockups:**
[Attach UI mockup]
```

### Code Contributions

We welcome code contributions! Here's how to get started:

#### Types of Contributions

- **Bug Fixes**: Resolve reported issues
- **Features**: Implement new functionality
- **Refactoring**: Improve code structure
- **Performance**: Optimize existing code
- **Tests**: Add unit or integration tests
- **Documentation**: Improve code comments

#### Before You Start

1. **Check Issues**: Look for "good first issue" or "help wanted" labels
2. **Claim an Issue**: Comment that you're working on it
3. **Discuss Large Changes**: Open an issue first for major features
4. **Fork the Repository**: Create your own fork

### Documentation

Documentation improvements are always welcome:

- **README Updates**: Clarify instructions, fix typos
- **Code Comments**: Explain complex logic
- **API Documentation**: Document public interfaces
- **Tutorials**: Create guides for common tasks
- **Troubleshooting**: Add solutions to common problems

## Development Setup

### Prerequisites

- **Android Studio**: Hedgehog (2023.1.1) or newer
- **JDK**: Version 17 or higher
- **Android SDK**: API 26-34 installed
- **NDK**: For compiling native audio libraries
- **Git**: Version control

### Initial Setup

1. **Fork and Clone**
   ```bash
   git clone https://github.com/YOUR_USERNAME/hammumble.git
   cd hammumble
   ```

2. **Open in Android Studio**
   - File → Open
   - Select the cloned directory
   - Wait for Gradle sync

3. **Build the Project**
   ```bash
   ./gradlew assembleDebug
   ```

4. **Run on Device/Emulator**
   - Connect Android device or start emulator
   - Click Run (▶️) in Android Studio

### Project Structure

```
hammumble/
├── app/                    # Main application module
│   ├── src/main/
│   │   ├── java/com/hammumble/
│   │   │   ├── ui/        # Compose UI components
│   │   │   ├── service/   # Background services
│   │   │   ├── network/   # Network layer
│   │   │   ├── audio/     # Audio processing
│   │   │   ├── hardware/  # Hardware integration
│   │   │   └── data/      # Data models
│   │   ├── res/           # Resources
│   │   └── AndroidManifest.xml
│   └── build.gradle
├── libraries/humla/       # Mumble protocol library
└── build.gradle
```

## Coding Guidelines

### Kotlin Style

Follow [Kotlin Coding Conventions](https://kotlinlang.org/docs/coding-conventions.html):

```kotlin
// ✅ Good
class MumbleService : Service() {
    private val connectionManager = MumbleConnectionManager()
    
    fun connectToServer(serverInfo: ServerInfo) {
        // Implementation
    }
}

// ❌ Avoid
class mumbleService:Service(){
    var ConnectionManager=MumbleConnectionManager()
    fun Connect_To_Server(server_info:ServerInfo){}
}
```

### Jetpack Compose

Use Compose best practices:

```kotlin
// ✅ Good - Descriptive function names, proper state hoisting
@Composable
fun ServerListItem(
    server: ServerInfo,
    onServerClick: (ServerInfo) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onServerClick(server) }
            .padding(16.dp)
    ) {
        Text(text = server.name)
    }
}

// ❌ Avoid - Side effects in composable
@Composable
fun BadServerList(servers: List<ServerInfo>) {
    servers.forEach { server ->
        // Don't call ViewModel methods directly
        viewModel.connectToServer(server)  // Bad!
    }
}
```

### Code Organization

- **Keep functions small**: Aim for single responsibility
- **Use meaningful names**: Self-documenting code
- **Add comments**: Explain complex logic, not obvious code
- **Avoid magic numbers**: Use named constants

```kotlin
// ✅ Good
private const val DEFAULT_PORT = 64738
private const val CONNECTION_TIMEOUT_MS = 5000L

fun connectToServer(address: String, port: Int = DEFAULT_PORT) {
    // Implementation
}

// ❌ Avoid
fun connect(addr: String, p: Int = 64738) {
    Thread.sleep(5000) // Why 5000?
}
```

### Testing

Write tests for:
- ViewModels and business logic
- Data transformations
- Network responses
- Edge cases and error conditions

```kotlin
class MumbleViewModelTest {
    @Test
    fun `connecting to server updates state correctly`() {
        // Arrange
        val viewModel = MumbleViewModel()
        
        // Act
        viewModel.connectToServer(testServer)
        
        // Assert
        assertEquals(ConnectionState.CONNECTING, viewModel.connectionState.value)
    }
}
```

## Pull Request Process

### Before Submitting

1. **Update from main**: Rebase on latest main branch
   ```bash
   git fetch upstream
   git rebase upstream/main
   ```

2. **Run tests**: Ensure all tests pass
   ```bash
   ./gradlew test
   ```

3. **Build successfully**: Verify app builds
   ```bash
   ./gradlew assembleDebug
   ```

4. **Test on device**: Manual testing on real device

5. **Update documentation**: Update README if needed

### Creating Pull Request

1. **Push to your fork**
   ```bash
   git push origin feature/your-feature-name
   ```

2. **Open Pull Request** on GitHub
   - Use a descriptive title
   - Reference related issues (#123)
   - Describe changes in detail
   - Add screenshots for UI changes
   - List testing done

**PR Template:**

```markdown
## Description
[Describe what this PR does]

## Related Issues
Fixes #123
Related to #456

## Changes Made
- Added feature X
- Fixed bug Y
- Refactored component Z

## Testing
- [x] Tested on Android 10 (Pixel 4)
- [x] Tested on Android 13 (Samsung S21)
- [x] Unit tests added/updated
- [x] Manual testing performed

## Screenshots
[If UI changes, add before/after screenshots]

## Checklist
- [x] Code follows style guidelines
- [x] Comments added for complex logic
- [x] Documentation updated
- [x] No new warnings introduced
- [x] Tested on multiple devices
```

### Review Process

1. **Automated Checks**: CI/CD runs tests
2. **Code Review**: Maintainers review code
3. **Feedback**: Address review comments
4. **Approval**: Once approved, will be merged
5. **Merge**: Squash and merge to main

### After Merge

- Your contribution will be in the next release
- You'll be added to contributors list
- Consider helping review other PRs

## Community

### Getting Help

- **GitHub Discussions**: Ask questions, share ideas
- **Issues**: For bugs and features
- **Email**: hammumble.support@example.com

### Communication Channels

- **GitHub**: Primary development discussion
- **Issues**: Bug reports and feature requests
- **Discussions**: General questions and ideas
- **Pull Requests**: Code review and collaboration

### Recognition

Contributors are recognized in:
- README.md contributors section
- Release notes
- GitHub contributors page

## Development Resources

### Useful Links

- [Android Developers](https://developer.android.com/)
- [Jetpack Compose](https://developer.android.com/jetpack/compose)
- [Kotlin Documentation](https://kotlinlang.org/docs/home.html)
- [Mumble Protocol](https://mumble-protocol.readthedocs.io/)
- [Material 3 Design](https://m3.material.io/)

### Learning Resources

- [Compose Pathway](https://developer.android.com/courses/pathways/compose)
- [Kotlin Koans](https://play.kotlinlang.org/koans)
- [Android Architecture Guide](https://developer.android.com/topic/architecture)

## Questions?

Don't hesitate to ask! We're here to help:

- Open an issue with the "question" label
- Start a discussion on GitHub
- Email the maintainers

**Thank you for contributing to HamMumble!** 🎉

---

## Quick Reference

### Commit Message Format

```
type(scope): subject

body

footer
```

**Types:**
- `feat`: New feature
- `fix`: Bug fix
- `docs`: Documentation
- `style`: Formatting
- `refactor`: Code restructure
- `test`: Tests
- `chore`: Build/tools

**Example:**
```
feat(audio): add VU meter visualization

Added real-time VU meters to audio settings for input/output monitoring.
Includes color-coded levels and peak indicators.

Fixes #42
```

### Branch Naming

- `feature/feature-name` - New features
- `fix/bug-description` - Bug fixes
- `docs/what-changed` - Documentation
- `refactor/component-name` - Refactoring

### Testing Checklist

- [ ] Unit tests pass
- [ ] App builds successfully
- [ ] Tested on physical device
- [ ] Tested on Android 8.0+
- [ ] No new warnings
- [ ] No performance regression
- [ ] Documentation updated

---

**Happy coding!** 73 de HamMumble Team

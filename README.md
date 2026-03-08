# ServerDash

A powerful Android app for remote Linux server management over SSH.

ServerDash gives you full visibility and control over your Linux servers from your phone. Monitor system metrics, manage services, run commands, interact with Claude Code, browse Git repositories, and more -- all through a secure SSH connection.

> **Note:** This project is experimental / alpha quality. It is currently tailored for Ubuntu 24.04 VPS environments. Contributions to support additional distributions and platforms are welcome.

<!-- Screenshots go here. Add app screenshots showcasing the dashboard, terminal, service management, and other key screens. -->

---

## Features

### Server Monitoring
- Connect to servers via SSH with password or key-based authentication
- Real-time system metrics: CPU, memory, disk, network, load averages
- Service management: view status, start, stop, and restart services
- Dashboard with at-a-glance server health overview

### Service Discovery
- Automatic detection of systemd services
- Docker container discovery and management
- Fleet service detection and monitoring

### Terminal
- Full terminal emulator with command execution
- ANSI color support for realistic terminal output
- Command history with quick recall
- Interactive shell sessions

### Claude Code Integration
- MCP (Model Context Protocol) server management
- Claude Code settings configuration
- CLAUDE.md file editing and management
- Interactive terminal with tmux session support

### Git and GitHub
- Repository browser with file tree navigation
- Branch listing and switching
- Commit history viewer
- Pull request browser
- Diff viewer with syntax-aware formatting

### Security
- Database encryption with SQLCipher
- Biometric and device credential app lock
- Privacy filter system with 15 configurable filter types
- SSH credentials stored in memory only, backed by EncryptedSharedPreferences
- No telemetry, no analytics -- all data stays on your device

### Home Screen Widgets
- Server status widget for quick health checks
- Services widget to monitor key services
- Quick action widgets for common operations

### Theme Customization
- Material 3 dynamic theming
- Light and dark mode support
- Customizable accent colors

### Plugin Architecture
- Modular plugin system for extensibility
- Built-in plugins: Fleet, Guardian, Claude Code
- Plugins are automatically detected and conditionally enabled
- Easy to add new plugins for additional functionality

---

## Tech Stack

| Component | Technology |
|---|---|
| Language | Kotlin |
| UI | Jetpack Compose, Material 3 |
| Dependency Injection | Hilt |
| SSH | SSHJ |
| Database | Room with SQLCipher encryption |
| Preferences | DataStore, EncryptedSharedPreferences |
| Async | Kotlin Coroutines and Flow |
| Charts | Vico |
| Widgets | Jetpack Glance |
| Architecture | Clean Architecture, MVVM |
| Min SDK | 28 (Android 9) |
| Target SDK | 35 |

---

## Getting Started

### Prerequisites

- Android Studio Ladybug or newer
- JDK 17+
- An Android device or emulator running Android 9 (API 28) or higher
- A Linux server with SSH access (Ubuntu 24.04 recommended)

### Build Instructions

1. Clone the repository:
   ```bash
   git clone https://github.com/wrxck/ServerDash.git
   cd ServerDash
   ```

2. Open the project in Android Studio.

3. Let Gradle sync complete.

4. Build and run on your device or emulator:
   ```bash
   ./gradlew assembleDebug
   ```

5. On first launch, add a server connection with your SSH credentials.

---

## Important Notes

- **Ubuntu 24.04 focused:** ServerDash is currently tailored for Ubuntu 24.04 VPS environments. Service discovery, package management commands, and system metric parsing are built around Ubuntu/Debian conventions. Other distributions may work partially but are not fully supported yet.
- **Experimental / Alpha:** This project is in early development. Expect rough edges, incomplete features, and breaking changes between versions.
- **SSH-based:** All server communication happens over SSH. You must have SSH access (password or key-based) to your server. No proprietary agents or daemons are required on the server side.

---

## Architecture

ServerDash follows Clean Architecture principles with an MVVM presentation pattern:

```
app/
  src/main/java/com/serverdash/app/
    domain/         # Use cases, repository interfaces, domain models
    data/           # Repository implementations, data sources, Room DAOs
    presentation/   # Compose UI, ViewModels, navigation
    di/             # Hilt modules
    utils/          # Shared utilities
```

- **Domain layer:** Pure Kotlin with no Android dependencies. Contains use cases, repository interfaces, and domain models.
- **Data layer:** Repository implementations, Room database, DataStore preferences, SSH session management.
- **Presentation layer:** Jetpack Compose screens, ViewModels with StateFlow, sealed event classes for UI actions, and navigation.

For detailed architecture documentation, see [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md).

---

## Contributing

Contributions are welcome. Whether it is a bug fix, new feature, plugin for another distribution, or documentation improvement, we appreciate your help.

Please read [CONTRIBUTING.md](CONTRIBUTING.md) for guidelines on code standards, PR requirements, and development setup.

Areas where contributions are especially encouraged:

- Support for additional Linux distributions (CentOS, Fedora, Arch, etc.)
- Support for additional platforms (FreeBSD, macOS remote management)
- New plugins for specialized server software
- UI/UX improvements and accessibility
- Test coverage

---

## License

This project is licensed under the MIT License. See [LICENSE](LICENSE) for details.

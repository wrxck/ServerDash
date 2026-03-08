# Plugin System

ServerDash uses a modular plugin architecture that enables features to be conditionally activated based on what software is detected on the connected server. This document explains how the plugin system works, how to add new plugins, and describes the existing built-in plugins.

---

## How It Works

### Overview

Plugins in ServerDash are feature modules that extend the app's functionality for specific server software or workflows. Rather than shipping a monolithic app where every feature is always visible, plugins ensure that users only see features relevant to their server setup.

### Detection Mechanism

When ServerDash connects to a server, it runs a series of detection checks. Each plugin defines its own detection logic, which typically involves one or more of:

- **Binary check:** Verifying that a specific executable exists on the server (e.g., checking for `fleet` in the PATH).
- **Service check:** Querying systemd for a specific service (e.g., `systemctl is-active docker`).
- **File check:** Looking for configuration files or directories that indicate the software is installed.
- **Fallback toggle:** If auto-detection is unreliable, the plugin can provide a manual toggle in settings that lets the user enable or disable it.

Detection runs asynchronously during connection setup and results are cached for the session. If a plugin is detected, its UI elements are registered in the navigation graph and made visible.

### Lifecycle

1. **Connection established:** SSH session is opened to the server.
2. **Detection phase:** All registered plugins run their detection checks concurrently.
3. **Registration:** Detected plugins register their navigation routes and settings entries.
4. **UI activation:** Bottom navigation and settings screens update to reflect available plugins.
5. **Disconnection:** Plugin state is cleared when the session ends.

---

## UI Integration

Plugins integrate with the app at several points:

### Navigation

Each plugin can define one or more screens accessible through the navigation graph. Plugin routes are added conditionally -- they only appear when the plugin is detected. The bottom navigation bar adapts to include plugin entries as needed.

### Settings

Plugins can add a section to the Settings screen. This typically includes:

- An enable/disable toggle (for manual override of auto-detection)
- Plugin-specific configuration options

### Dashboard

Plugins can contribute widgets or cards to the Dashboard screen, providing at-a-glance information related to their functionality.

---

## Existing Plugins

### Fleet

JetBrains Fleet remote development environment integration.

- **Detection:** Checks for Fleet-related services and processes on the server. Uses a fleet-first detection strategy with a fallback toggle in settings.
- **Features:**
  - Fleet workspace management
  - Remote development session monitoring
  - Service status tracking

### Guardian

Server security monitoring and audit plugin.

- **Detection:** Checks for Guardian-related binaries or configuration on the server.
- **Features:**
  - Security audit dashboards
  - Monitoring alerts and status
  - Security configuration review

### Claude Code

Integration with Anthropic's Claude Code CLI tool for AI-assisted development.

- **Detection:** Checks for the `claude` binary on the server.
- **Features:**
  - MCP (Model Context Protocol) server management
  - Claude Code settings configuration
  - CLAUDE.md file editing and management
  - Interactive terminal with tmux session support for persistent Claude Code sessions

---

## Adding a New Plugin

To add a new plugin to ServerDash, follow these steps:

### 1. Define the Plugin

Create a new package under the appropriate layer for your plugin screens and logic:

```
presentation/screens/yourplugin/
    YourPluginScreen.kt
    YourPluginViewModel.kt
```

### 2. Implement Detection

Create a detection class that determines whether the plugin should be active:

```kotlin
class YourPluginDetector @Inject constructor(
    private val sshSessionManager: SshSessionManager
) {
    suspend fun detect(serverId: Long): Boolean {
        val result = sshSessionManager.executeCommand(serverId, "which your-binary")
        return result.exitCode == 0
    }
}
```

### 3. Register Navigation Routes

Add your plugin's routes to the navigation graph conditionally:

```kotlin
// In Navigation.kt
if (pluginState.yourPluginDetected) {
    composable("your_plugin") {
        YourPluginScreen(viewModel = hiltViewModel())
    }
}
```

### 4. Add Settings Entry

If your plugin needs configuration, add a settings section:

```kotlin
// In the plugin settings section
PluginToggleItem(
    name = "Your Plugin",
    detected = pluginState.yourPluginDetected,
    enabled = pluginState.yourPluginEnabled,
    onToggle = { enabled -> viewModel.onEvent(ToggleYourPlugin(enabled)) }
)
```

### 5. Provide Dependencies

Register your plugin's dependencies with Hilt:

```kotlin
@Module
@InstallIn(SingletonComponent::class)
abstract class YourPluginModule {
    @Binds
    abstract fun bindYourPluginRepository(
        impl: YourPluginRepositoryImpl
    ): YourPluginRepository
}
```

### 6. Follow Existing Patterns

Look at the Fleet, Guardian, and Claude Code plugins for reference implementations. Each follows the same MVVM pattern with detection, conditional navigation, and optional settings integration.

---

## Guidelines for Plugin Development

- Keep plugins self-contained. Avoid tight coupling to other plugins or core features.
- Detection should be fast and non-destructive -- never modify server state during detection.
- Handle the case where the plugin's server-side software is present but not running.
- Provide a manual toggle in settings so users can override auto-detection.
- Follow the existing code standards documented in [CONTRIBUTING.md](../CONTRIBUTING.md).

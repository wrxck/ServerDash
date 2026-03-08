# Architecture

This document describes the architecture of ServerDash in detail, covering layer organization, dependency injection, SSH integration, the plugin system, state management, data persistence, and navigation.

---

## Overview

ServerDash follows Clean Architecture principles organized into three main layers:

```
domain/     # Business logic, interfaces, models (pure Kotlin)
data/       # Implementations, data sources, persistence
presentation/  # UI, ViewModels, navigation
```

Dependencies flow inward: presentation depends on domain, data depends on domain, but domain depends on nothing else. This separation keeps business logic testable and framework-independent.

---

## Domain Layer

The domain layer contains pure Kotlin code with no Android framework dependencies.

### Repository Interfaces

Repository interfaces define the contracts for data access. They live in the domain layer so that use cases depend on abstractions, not implementations.

```
domain/
  repository/
    ServerRepository.kt
    ServiceRepository.kt
    MetricsRepository.kt
    ...
```

### Use Cases

Each use case encapsulates a single piece of business logic. Use cases are injected into ViewModels via Hilt.

```kotlin
class GetServerMetricsUseCase @Inject constructor(
    private val serverRepository: ServerRepository
) {
    suspend operator fun invoke(serverId: Long): Result<ServerMetrics> {
        // Business logic here
    }
}
```

Use cases may combine data from multiple repositories, apply business rules, or transform data for the presentation layer.

### Domain Models

Domain models represent the core entities of the application: servers, services, metrics, connections, and so on. They are plain Kotlin data classes with no persistence or serialization annotations.

---

## Data Layer

The data layer provides concrete implementations of repository interfaces and manages all external data sources.

### Repository Implementations

Each repository interface has a corresponding implementation that coordinates between local and remote data sources.

```kotlin
class ServerRepositoryImpl @Inject constructor(
    private val serverDao: ServerDao,
    private val sshSessionManager: SshSessionManager
) : ServerRepository {
    // Implementation
}
```

### Room Database

Room is used for structured local data persistence. The database is encrypted with SQLCipher via a passphrase derived from the Android Keystore MasterKey.

```
data/
  local/
    database/
      AppDatabase.kt       # Room database definition
      dao/
        ServerDao.kt        # Data access objects
        ServiceDao.kt
        ...
      entity/
        ServerEntity.kt     # Room entities
        ...
```

### DataStore

Jetpack DataStore is used for key-value preferences such as theme settings, feature flags, and non-sensitive configuration.

### EncryptedSharedPreferences

Sensitive preferences (such as cached credential hints or plugin configuration) use EncryptedSharedPreferences backed by the Android Keystore.

---

## Presentation Layer

The presentation layer contains all UI code, ViewModels, and navigation logic.

### MVVM Pattern

Each screen follows the MVVM pattern:

1. **State:** A data class representing the complete UI state for the screen.
2. **Events:** A sealed class/interface representing user actions.
3. **ViewModel:** Holds state as `MutableStateFlow`, exposes it as `StateFlow`, and processes events.

```kotlin
// State
data class DashboardState(
    val servers: List<Server> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

// Events
sealed class DashboardEvent {
    data class SelectServer(val serverId: Long) : DashboardEvent()
    object Refresh : DashboardEvent()
}

// ViewModel
@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val getServersUseCase: GetServersUseCase
) : ViewModel() {
    private val _state = MutableStateFlow(DashboardState())
    val state: StateFlow<DashboardState> = _state.asStateFlow()

    fun onEvent(event: DashboardEvent) {
        when (event) {
            is DashboardEvent.SelectServer -> loadServerDetails(event.serverId)
            is DashboardEvent.Refresh -> refreshServers()
        }
    }
}
```

### Compose UI

Screens are built with Jetpack Compose and Material 3. Each screen composable:

- Collects state from the ViewModel via `collectAsStateWithLifecycle()`
- Delegates user actions to the ViewModel through the event handler
- Follows state hoisting conventions for reusable components

### Navigation

Navigation uses Jetpack Compose Navigation with a centralized navigation graph. Routes are defined as sealed classes or string constants. The navigation structure supports:

- Bottom navigation for top-level destinations (Dashboard, Terminal, Settings, etc.)
- Nested navigation graphs for feature modules
- Conditional navigation for plugin screens (only shown when the plugin is detected on the server)

```
presentation/
  navigation/
    Navigation.kt          # Main NavHost and route definitions
    BottomNavBar.kt        # Bottom navigation component
  screens/
    dashboard/
      DashboardScreen.kt
      DashboardViewModel.kt
    terminal/
      TerminalScreen.kt
      TerminalViewModel.kt
    ...
```

---

## Dependency Injection with Hilt

Hilt provides dependency injection throughout the application. Modules are organized by concern:

```
di/
  AppModule.kt            # Application-scoped dependencies
  DatabaseModule.kt       # Room database and DAOs
  NetworkModule.kt        # SSH and network-related dependencies
  RepositoryModule.kt     # Repository bindings (interface to implementation)
```

Key principles:

- All ViewModels are annotated with `@HiltViewModel` and use `@Inject constructor`.
- Repository implementations are bound to their interfaces in Hilt modules using `@Binds`.
- Singletons (database, SSH session manager) are scoped with `@Singleton`.
- No manual dependency construction anywhere in the codebase.

---

## SSH Integration

SSH is the backbone of ServerDash. All server communication happens over SSH using the SSHJ library.

### SshSessionManager

The `SshSessionManager` is a singleton that manages SSH connections. It handles:

- Connection establishment and teardown
- Session pooling and reuse
- Connection health monitoring
- Automatic reconnection on failure

### Command Execution

Commands are executed through SSH exec channels. The manager provides a coroutine-friendly API:

```kotlin
suspend fun executeCommand(serverId: Long, command: String): CommandResult
```

Results include stdout, stderr, and the exit code.

### PTY (Pseudo-Terminal)

For the interactive terminal feature, ServerDash allocates a PTY through SSH. This provides:

- Full terminal emulation with ANSI escape code support
- Interactive shell sessions
- Support for terminal-based applications (vim, htop, tmux, etc.)
- Configurable terminal dimensions

### tmux Integration

The Claude Code integration uses tmux sessions on the remote server to maintain persistent terminal sessions that survive connection interruptions.

---

## Plugin System

ServerDash has a modular plugin architecture that allows features to be conditionally enabled based on what is installed on the connected server.

### Detection

When connecting to a server, ServerDash runs detection checks to determine which plugins should be enabled. Detection typically involves checking for the presence of specific binaries, services, or configuration files on the server.

### Plugin Registration

Plugins register themselves with:

- A unique identifier
- A detection mechanism
- Navigation routes for their screens
- Optional settings entries

### Built-in Plugins

- **Fleet:** JetBrains Fleet remote development environment management
- **Guardian:** Server security monitoring and audit
- **Claude Code:** Integration with Anthropic's Claude Code CLI tool

For details on the plugin system, see [PLUGINS.md](PLUGINS.md).

---

## State Management

### StateFlow

All UI state is managed through Kotlin `StateFlow`. ViewModels hold `MutableStateFlow` internally and expose read-only `StateFlow` to the UI layer.

### Event Handling

User interactions are modeled as sealed class events. The UI dispatches events to the ViewModel, which processes them and updates state accordingly. This creates a unidirectional data flow:

```
User Action -> Event -> ViewModel -> State Update -> UI Recomposition
```

### Side Effects

One-time side effects (navigation, snackbar messages, etc.) are handled through `SharedFlow` or Compose `LaunchedEffect` blocks, keeping them separate from persistent UI state.

---

## Data Persistence

### Room with SQLCipher

The Room database stores structured data such as server configurations, cached metrics, and service information. SQLCipher encrypts the database file using a key derived from the Android Keystore `MasterKey`. This ensures data at rest is encrypted even if the device is compromised.

### DataStore

Jetpack DataStore handles user preferences and feature flags. It provides type-safe, asynchronous access to key-value pairs with Flow-based observation.

### EncryptedSharedPreferences

Sensitive configuration values use EncryptedSharedPreferences, which encrypts both keys and values using the Android Keystore.

### In-Memory Credentials

SSH passwords and private keys are held in memory only during active use. They are not written to disk in plaintext. When persisted for convenience, they are stored exclusively through EncryptedSharedPreferences.

---

## Navigation Structure

The app uses a hierarchical navigation structure:

```
Root NavHost
  |- Dashboard (start destination)
  |- Terminal
  |- Services
  |- Settings
  |- Server Detail
  |    |- Metrics
  |    |- Services
  |    |- Terminal
  |- Git
  |    |- Repositories
  |    |- Branches
  |    |- Commits
  |    |- Pull Requests
  |    |- Diff Viewer
  |- Claude Code (plugin, conditional)
  |    |- MCP Servers
  |    |- Settings
  |    |- CLAUDE.md
  |    |- Terminal
  |- Fleet (plugin, conditional)
  |- Guardian (plugin, conditional)
```

Plugin screens are added to the navigation graph conditionally based on detection results. The bottom navigation bar adapts to show or hide plugin entries accordingly.

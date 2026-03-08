# Contributing to ServerDash

Thank you for your interest in contributing to ServerDash. This guide covers the standards, processes, and setup you need to get started.

---

## Getting Started

### Development Setup

1. Clone the repository:
   ```bash
   git clone https://github.com/wrxck/ServerDash.git
   cd ServerDash
   ```

2. Open the project in Android Studio (Ladybug or newer recommended).

3. Ensure you have JDK 17+ configured.

4. Let Gradle sync complete. All dependencies will be downloaded automatically.

5. Build the project:
   ```bash
   ./gradlew assembleDebug
   ```

6. Run tests:
   ```bash
   ./gradlew test
   ```

### Testing Against a Server

You will need SSH access to a Linux server (Ubuntu 24.04 recommended) to test most features. Consider using a local VM or a disposable cloud instance for development.

---

## Code Standards

### Kotlin Style

Follow the [official Kotlin coding conventions](https://kotlinlang.org/docs/coding-conventions.html). Key points:

- Use meaningful, descriptive names for classes, functions, and variables.
- Prefer `val` over `var` where possible.
- Use data classes for plain data holders.
- Use sealed classes/interfaces for representing restricted type hierarchies.
- Keep functions small and focused on a single responsibility.

### Jetpack Compose

- **State hoisting:** Lift state out of composables and pass it down as parameters. Composables should be as stateless as possible.
- **Minimal recomposition:** Structure composables to minimize unnecessary recompositions. Avoid passing unstable types as parameters.
- **Preview functions:** Add `@Preview` composables for new UI components where practical.
- **Modifiers:** Always accept a `Modifier` parameter and apply it to the root composable.

### Architecture Patterns

- **MVVM:** Each screen has a corresponding ViewModel. ViewModels expose state via `StateFlow` and accept user actions through sealed event classes.
  ```kotlin
  // Events
  sealed class DashboardEvent {
      data class RefreshMetrics(val serverId: Long) : DashboardEvent()
      object ToggleService : DashboardEvent()
  }

  // ViewModel
  @HiltViewModel
  class DashboardViewModel @Inject constructor(
      private val getMetricsUseCase: GetMetricsUseCase
  ) : ViewModel() {
      private val _state = MutableStateFlow(DashboardState())
      val state: StateFlow<DashboardState> = _state.asStateFlow()

      fun onEvent(event: DashboardEvent) { ... }
  }
  ```

- **Repository pattern:** All data access goes through repository interfaces defined in the domain layer, with implementations in the data layer.

- **Use cases:** Business logic lives in use case classes in the domain layer. Each use case handles a single operation.
  ```kotlin
  class GetServerMetricsUseCase @Inject constructor(
      private val serverRepository: ServerRepository
  ) {
      suspend operator fun invoke(serverId: Long): Result<ServerMetrics> { ... }
  }
  ```

- **Dependency injection:** Use Hilt for all dependency injection. Do not manually construct dependencies. Provide dependencies through `@Module` annotated classes and inject with `@Inject`.

---

## Pull Request Requirements

### Before Submitting

1. Ensure your code compiles without errors.
2. Run existing tests and verify they pass.
3. Test your changes on a real device or emulator.
4. For SSH-dependent features, test against an actual server.

### PR Guidelines

- **Clear description:** Explain what the PR does and why. Link to any related issues.
- **Follow existing patterns:** Match the code style and architecture of the surrounding code. If a screen uses a ViewModel with sealed events, your additions should too.
- **Add tests:** New features should include unit tests where possible. ViewModel logic, use cases, and repository methods are good candidates for testing.
- **No breaking changes without discussion:** If your change alters existing behavior, public APIs, or data schemas, open an issue first to discuss the approach.
- **Keep PRs focused:** One feature or fix per PR. Large PRs are harder to review and more likely to introduce issues.
- **Update documentation:** If your change affects user-facing behavior or architecture, update the relevant documentation.

### Commit Messages

Use clear, descriptive commit messages. Prefix with a category when appropriate:

- `feat:` for new features
- `fix:` for bug fixes
- `refactor:` for code restructuring
- `docs:` for documentation changes
- `test:` for test additions or fixes
- `chore:` for build/tooling changes

---

## Issue Guidelines

### Bug Reports

Use the [bug report template](.github/ISSUE_TEMPLATE/bug_report.md). Include:

- A clear description of the problem
- Steps to reproduce
- Expected vs. actual behavior
- Device and Android version
- Server OS and version (if relevant)
- Logs or screenshots if available

### Feature Requests

Use the [feature request template](.github/ISSUE_TEMPLATE/feature_request.md). Include:

- A clear description of the proposed feature
- The problem it solves or the use case it enables
- Any implementation ideas you have

---

## Architecture Notes

ServerDash follows Clean Architecture with three main layers:

### Domain Layer (`domain/`)
- Repository interfaces
- Use case classes
- Domain models
- No Android framework dependencies

### Data Layer (`data/`)
- Repository implementations
- Room database and DAOs
- DataStore preferences
- SSH session management (SSHJ)
- Remote data sources

### Presentation Layer (`presentation/`)
- Jetpack Compose screens and components
- ViewModels with StateFlow
- Navigation graph
- Theme and styling

For detailed architecture documentation, see [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md).

---

## Questions?

If you have questions about contributing, open a discussion or issue on GitHub. We are happy to help you get oriented in the codebase.

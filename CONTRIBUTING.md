# Contributing to OpenMynd

Thanks for your interest in contributing! This project is a Kotlin Multiplatform (KMM) app using Compose Multiplatform and Koin.

## Getting started
- Ensure JDK 17+ is installed
- Android: set `sdk.dir` in `local.properties`
- iOS/macOS: Xcode 15+
- Run Android: `./gradlew :composeApp:assembleDebug`
- Run iOS: open `iosApp/iosApp.xcodeproj` and run the `iosApp` scheme
- Run Linux desktop: `./gradlew :composeApp:run` (no Android SDK needed)

## Project structure
- `composeApp/`: shared UI and business logic (commonMain, mobileMain, androidMain, iosMain, desktopMain)
- `iosApp/`: native iOS launcher app
- `gradle/libs.versions.toml`: dependency versions

## Development
- Use descriptive names and keep public APIs documented.
- Match existing code style and formatting. Prefer multi-line for readability.
- Avoid platform-specific code in `commonMain` unless guarded with `expect/actual`.
- Keep logging concise; avoid noisy logs in release paths.

## Bluetooth LE backends
- Mobile: default is BlueFalcon, alternate is Kable.
- Linux desktop: BlueZ over D-Bus (`BlueZDeviceConnector`).
- Switch via `selectedBackend` in `deviceConnectorModule` or pass a `ConnectorBackend` to `initKoin` on platform startup.

## Testing
- Android UI tests: `./gradlew :composeApp:pixel5Check`
- iOS simulator tests: `./gradlew :composeApp:iosSimulatorArm64Test`
- Linux desktop tests: `./gradlew :composeApp:desktopTest` (needs `dbus-daemon`; BlueZ and the XDG portal are mocked on a private bus)

## Submitting changes
1. Fork and create a topic branch
2. Write focused, small commits
3. Run the build locally and fix lints
4. Open a pull request with a clear description and screenshots when relevant

## Code of Conduct
Be respectful. This project follows the [Contributor Covenant](https://www.contributor-covenant.org/). Harassment or discrimination is not tolerated.



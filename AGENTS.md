# Repository Guidelines

## Project Structure & Module Organization
- **`app/`**: Android application module containing UI, activities, and view models. Kotlin sources live in `app/src/main/java`, resources in `app/src/main/res`.
- **`mozilla/`**: Library module wrapping Mozilla Android Components (GeckoView, engine features). Shared engine configuration and feature wiring reside here.
- **Top-level files**: `build.gradle.kts`, `settings.gradle.kts`, and `gradle/` configure the multi-module Gradle build.

## Build, Test, and Development Commands
- `./gradlew assembleDebug`: Builds the app debug APK; use before installing locally.
- `./gradlew installDebug`: Builds and deploys the debug APK to a connected device or emulator.
- `./gradlew test`: Executes JVM unit tests for all modules.
- `./gradlew connectedAndroidTest`: Runs instrumentation tests on a connected device/emulator (ensure one is available).

## Coding Style & Naming Conventions
- Kotlin code uses **4-space indentation** and trailing commas where supported.
- Prefer expressive names (`SearchActivity`, `BrowserComponents`) and `camelCase` for members; constants remain `UPPER_SNAKE_CASE`.
- Keep UI logic in activities/fragments; move shared engine logic to the `mozilla` module.
- Run `./gradlew ktlintFormat` if ktlint is configured locally; otherwise follow the existing code style conventions visible in the modules.

## Testing Guidelines
- Unit tests belong under `src/test/java`; instrumentation tests under `src/androidTest/java`.
- Name tests after the subject under test (e.g., `SearchModelTest`, `WebActivityTest`).
- When modifying engine integrations or URL handling, add or update tests ensuring navigation, search, and session flows behave as expected.
- Strive for high coverage in critical modules (`BrowserComponents`, `SearchActivity`, `WebActivity`).

## Commit & Pull Request Guidelines
- Commit messages follow the format `component: short summary` (e.g., `web: fix engine session binding`). Keep bodies focused on _what_ and _why_.
- Before opening a PR, ensure builds and relevant tests pass locally.
- PR descriptions should summarize changes, reference related issues, and include device/emulator screenshots or screen recordings for UI-facing updates.
- Highlight any follow-up tasks, configuration steps, or known limitations so reviewers can verify effectively.

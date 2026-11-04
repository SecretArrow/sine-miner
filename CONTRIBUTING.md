# Contributing to SineMiner

Thanks for your interest in improving SineMiner! This document covers the basics.

## Development setup

1. Fork + clone the repo.
2. Install JDK 17, Android SDK (API 35, build-tools 35.0.0), Android NDK 27.0.12077973.
3. Install Rust stable and the Android targets:
   ```bash
   rustup target add aarch64-linux-android armv7-linux-androideabi x86_64-linux-android i686-linux-android
   cargo install cargo-ndk --locked --version ^3
   ```
4. Open in Android Studio (Ladybug or newer) or use the Gradle wrapper from CLI.

## Before opening a pull request

- [ ] `core/` — `cargo fmt --all -- --check`
- [ ] `core/` — `cargo clippy --all-targets --all-features -- -D warnings`
- [ ] `core/` — `cargo test --all-features`
- [ ] `app/` — `./gradlew :app:lintDebug :app:testDebugUnitTest`

CI runs all of the above on every PR; the `auto-fix` workflow can apply
`cargo fmt` and `clippy --fix` automatically if you forget.

## Branch + commit conventions

- Branch name: `<type>/<short-description>` (e.g. `feat/wallet-qr`, `fix/pool-reconnect`).
- Commit message: [Conventional Commits](https://www.conventionalcommits.org/) (e.g. `feat: add wallet QR export`).
- One logical change per PR. Keep PRs small.

## Adding a new screen

1. Create `app/src/main/java/com/secretarrow/sineminer/ui/<feature>/<Feature>Screen.kt`.
2. Create `app/src/main/java/com/secretarrow/sineminer/ui/<feature>/<Feature>ViewModel.kt`.
3. Register a route in `ui/navigation/SineMinerNavGraph.kt`.
4. If it's a top-level destination, add it to `ui/navigation/TopDestination.kt`.
5. Add string resources to `app/src/main/res/values/strings.xml`.
6. Add unit tests under `app/src/test/...`.

## Adding a Rust core function

1. Implement in the appropriate module under `core/src/<module>/`.
2. Expose via JNI in `core/src/jni_bridge.rs`.
3. Declare the `external` function in `app/.../core/native/NativeBridge.kt`.
4. Add a unit test in `core/tests/integration.rs` and an instrumented test in `app/src/androidTest/`.

## Reporting bugs

Open an issue with:
- Device model + Android version
- App version (from Settings → About)
- Steps to reproduce
- Log snippets (from Logs screen if available)

## Reporting security issues

See [SECURITY.md](SECURITY.md) — do not open a public issue for security bugs.

## Code of conduct

Be excellent to each other. We follow the [Contributor Covenant](https://www.contributor-covenant.org/version/2/1/code_of_conduct/).

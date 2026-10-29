# SineMiner

> Native Android Blocknet miner — **Kotlin + Rust via JNI**.
> Port of the [seine](https://github.com/zpalmtree/seine) CPU mining path with
> a fresh Material 3 Android UI, BIP39 wallet create/import, and background
> mining via WorkManager + Foreground Service.

![License](https://img.shields.io/badge/license-MIT-blue.svg)
![Kotlin](https://img.shields.io/badge/Kotlin-2.1.0-purple.svg)
![Rust](https://img.shields.io/badge/Rust-stable-orange.svg)
![Android](https://img.shields.io/badge/Android-API%2029+-green.svg)
![Build](https://github.com/SecretArrow/sine-miner/actions/workflows/build.yml/badge.svg)
![CI](https://github.com/SecretArrow/sine-miner/actions/workflows/ci.yml/badge.svg)

## What is this?

SineMiner is a native Android app that mines [Blocknet](https://blocknet.co)
(BLOCK) using the same Argon2id proof-of-work as the desktop miner
[`seine`](https://github.com/zpalmtree/seine), but with a mobile-first UI
and a crypto wallet for receiving mined coins.

It is **not** a backup tool. The `seine` codebase was originally a Blocknet
miner, and `sine-miner` ports that mining logic to Android.

## Architecture

```
┌─────────────────────────────────────────────────────────────────────┐
│                          Android app (Kotlin)                        │
│  ┌──────────────────────────────────────────────────────────────┐   │
│  │  Jetpack Compose Material 3 UI                                │   │
│  │  Dashboard · Mining · Wallet · Pool · Logs · Settings          │   │
│  │  MVVM with ViewModel + StateFlow + Hilt DI                      │   │
│  └────────────┬─────────────────────────────────────────────────────┘   │
│               │ NativeBridge.kt (JNI declarations)                     │
│  ┌────────────▼─────────────────────────────────────────────────────┐  │
│  │  Service layer (WorkManager + Foreground Service)                │  │
│  │  NotificationHelper · MiningScheduler · MiningWorker             │  │
│  └────────────┬─────────────────────────────────────────────────────┘  │
└───────────────┼──────────────────────────────────────────────────────┘
                │ JNI (libsineminer_core.so per ABI)
┌───────────────▼──────────────────────────────────────────────────────┐
│                              Rust core                                 │
│  ┌─────────────────┐  ┌──────────────────┐  ┌──────────────────────┐ │
│  │   mining/       │  │     wallet/      │  │     stratum/         │ │
│  │  Argon2id PoW   │  │  BIP39 mnemonic  │  │  Stratum v2 client    │ │
│  │  NonceScheduler │  │  ed25519 keypair │  │  Share submission     │ │
│  │  HashrateTracker│  │  Blocknet address│  │  Reconnect w/ backoff │ │
│  └─────────────────┘  └──────────────────┘  └──────────────────────┘ │
└────────────────────────────────────────────────────────────────────┘
```

### Modules

| Path | Purpose |
|------|---------|
| `app/` | Android app (Kotlin + Compose Material 3) |
| `core/` | Rust core (mining, wallet, stratum, JNI bridge) |
| `gradle/libs.versions.toml` | Single source of truth for all dependency versions |
| `.github/workflows/` | CI/CD pipelines (lint, unit, build, e2e, release, auto-fix) |
| `scripts/` | Local helpers (build Rust, install NDK) |

## Features

- **Mining**: Argon2id PoW with configurable memory preset (256 MiB / 1 GiB / 2 GiB)
- **Wallet**: Create + import BIP39 mnemonic, ed25519 keypair, Blocknet stealth address (spend + view public key + SHA3-256 checksum, base58-encoded)
- **Pool**: Stratum v2 client to bntpool.com or any compatible pool
- **Background mining**: Foreground Service with persistent notification + WorkManager scheduler (auto-start on charge, stop on low battery)
- **Modern UI**: Jetpack Compose Material 3, dark + light themes, Material You dynamic color on Android 12+
- **Notifications**: Per-channel (mining, shares, blocks, errors)
- **Encrypted keystore**: AES-256-GCM with PBKDF2-derived key; mnemonic never persisted

## Minimum requirements

- Android 10 (API 29) or newer
- ARM64 device (arm64-v8a) or x86_64 (for emulators)
- ~2 GiB free RAM for production mining (256 MiB / 1 GiB for testing)
- Charging recommended (mining drains battery fast and heats the device)

## Building

This project does **not** require local build of the Rust core — everything is
built by GitHub Actions. To build locally, you need:

1. **JDK 17** (Temurin recommended)
2. **Android SDK** with platform 35 and build-tools 35.0.0
3. **Android NDK** 27.0.12077973 (set `ANDROID_NDK_HOME`)
4. **Rust stable** with Android targets:
   ```bash
   rustup target add aarch64-linux-android armv7-linux-androideabi x86_64-linux-android i686-linux-android
   cargo install cargo-ndk --locked --version ^3
   ```
5. Build the debug APK:
   ```bash
   ./gradlew :app:assembleDebug
   ```
   The Gradle `buildRustCore` task will invoke `cargo ndk` for each ABI and
   merge the resulting `.so` files into the APK.

## CI/CD

| Workflow | Triggers | What it does |
|----------|----------|--------------|
| [`ci.yml`](.github/workflows/ci.yml) | push / pull_request to `main` | Rust fmt + clippy + test; Android lint + unit tests |
| [`build.yml`](.github/workflows/build.yml) | push / pull_request to `main` | Cross-compile Rust for arm64-v8a + x86_64 via cargo-ndk, assemble debug APK, upload as artifact |
| [`e2e.yml`](.github/workflows/e2e.yml) | PR with `e2e` label / manual | Spin up Android API 29 emulator, run instrumented tests |
| [`release.yml`](.github/workflows/release.yml) | tag `v*` / manual | Build signed release APK + AAB, create GitHub release (draft) |
| [`auto-fix.yml`](.github/workflows/auto-fix.yml) | push / weekly Monday / manual | Run `cargo fmt`, `cargo clippy --fix`, `spotlessApply`, commit + open PR |

### Caches used (for fast builds)

- `Swatinem/rust-cache@v2` — Cargo registry + git + `core/target`
- `actions/cache@v4` — Gradle `~/.gradle/caches`, `.gradle`, `app/build`
- `android-actions/setup-android@v3` — SDK + NDK

Expected incremental build times: **~8–12 minutes** for `build.yml`,
**~4–6 minutes** for `ci.yml` (after first warmup).

## Secrets to set in the repo

For full CI/CD functionality, set these in
https://github.com/SecretArrow/sine-miner/settings/secrets/actions:

| Secret | Purpose |
|--------|---------|
| `GH_PAT` | GitHub PAT for `auto-fix.yml` to push branches + open PRs (use repo-scoped PAT with `repo` + `workflow`) |
| `SINEMINER_KEYSTORE_BASE64` | Base64-encoded release signing keystore (`.jks` or `.keystore`) |
| `SINEMINER_KEYSTORE_PASSWORD` | Keystore password |
| `SINEMINER_KEY_ALIAS` | Signing key alias inside the keystore |
| `SINEMINER_KEY_PASSWORD` | Key password |

> ⚠️ **Never commit** any token, password, or keystore file. The `.gitignore`
> blocks `*.keystore`, `*.jks`, `keystore.properties`, `.env`, and `secrets.properties`.

## Development

```bash
# Format check (Rust)
cd core && cargo fmt --all -- --check

# Lint (Rust)
cd core && cargo clippy --all-targets --all-features -- -D warnings

# Unit tests (Rust)
cd core && cargo test --all-features

# Android lint + unit tests
./gradlew :app:lintDebug :app:testDebugUnitTest

# Instrumented tests (requires running emulator)
./gradlew :app:connectedDebugAndroidTest
```

## Roadmap

- [ ] Full Stratum v2 binary framing (currently JSON-RPC subset)
- [ ] GPU mining via Vulkan (long-term)
- [ ] Cloud backup targets (Google Drive, Dropbox, WebDAV) — currently local-only
- [ ] Multi-wallet dashboard with balances via Blocknet daemon RPC
- [ ] Translation to Bahasa Indonesia (id-ID) — strings ready, messages pending)

## Acknowledgments

- [`seine`](https://github.com/zpalmtree/seine) — Blocknet miner by zpalmtree (BSD-3-Clause). SineMiner's PoW parameters, address format, and Stratum capabilities follow seine's spec.
- [Blocknet protocol](https://blocknet.co) — the BLOCK network this miner targets.
- [`bip39`](https://github.com/maciejhirsz/bip39-rs) — Rust BIP39 implementation.
- [curve25519-dalek](https://github.com/dalek-cryptography/curve25519-dalek) — Ristretto group used for Blocknet stealth addresses.

## License

MIT — see [LICENSE](LICENSE).

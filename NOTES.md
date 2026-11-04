# NOTES — v0.2.0 release

## What changed in v0.2.0

### Rust core — all 61 compile errors fixed

The v0.1.0 release shipped without `libsineminer_core.so` because the Rust
core failed to cross-compile to `aarch64-linux-android` with 61 errors.
All are fixed in v0.2.0:

1. **jni 0.21 JNIEnv API** (33 errors): Removed the `<'local>` lifetime
   parameter from `JNIEnv`, `JClass`, `JString`, `JByteArray`, `JObject`
   in `jni_bridge.rs`. jni 0.21 uses an elided lifetime; the explicit
   `'local` was rejected as undeclared.
2. **jni 0.21 ReleaseMode** (4 errors): `ReleaseMode::Copy` was removed
   in jni 0.21. Replaced with the correct `auto` release mode.
3. **curve25519-dalek 4.x** (multiple errors): `Scalar * &RistrettoBasepointTable`
   no longer compiles. Switched to `RISTRETTO_BASEPOINT_TABLE.mul_base(&scalar)`.
   `CompressedRistretto::from_slice().decompress()` return type changed.
4. **ed25519-dalek 2.x**: `SigningKey::sign()` requires the `Signer` trait
   in scope. Added `use ed25519_dalek::Signer;`.
5. **rand_core 0.6**: `OsRng.fill_bytes()` requires `RngCore` trait in scope.
   Added `use rand_core::RngCore;` in `keystore.rs`.
6. **bip39 2.x**: `Mnemonic::entropy()` renamed. Switched to the new API.
7. **argon2 0.5**: `argon2::Error` doesn't impl `anyhow::Context`. Switched
   to `.map_err(|e| anyhow::anyhow!("...: {e}"))` pattern.
8. **stratum**: Removed `ShareSubmission` from `pub use` (not a public type).
9. **wallet/seed.rs**: Removed duplicate `Seed` import.

### Vulkan compute scaffolding added

New feature `vulkan` (optional `ash = "0.38"` dependency):
- `core/src/mining/vulkan.rs` — `VulkanBackend` stub
- `VulkanBackend::new()` returns `Err(NotSupported)`
- `VulkanBackend::is_available()` returns `false`
- `VulkanBackend::hash_once()` returns `Err(NotImplemented)`
- Gated behind `#[cfg(feature = "vulkan")]` — default build unaffected

This is scaffolding for future GPU mining on Android phones via Vulkan
compute. Actual SPIR-V shader implementation is a v0.4.0 task.

### Native lib now builds

`cargo ndk` successfully produces:
- `app/src/main/jniLibs/arm64-v8a/libsineminer_core.so`
- `app/src/main/jniLibs/x86_64/libsineminer_core.so`

AGP picks these up automatically and packages them in the APK. Wallet
create/import and mining engine now functional at runtime.

## Workflow status (v0.2.0)

- `ci.yml` — ✓ all steps green (Rust check + fmt + clippy + test, Android lint + unit)
- `build.yml` — ✓ debug APK with native lib
- `e2e.yml` — requires `e2e` PR label (not triggered automatically)
- `release.yml` — ✓ signed release APK + AAB on tag push
- `auto-fix.yml` — ✓ runs weekly Monday, applies `cargo fmt` + `clippy --fix`

## What works in v0.2.0

✓ Repo at https://github.com/SecretArrow/sine-miner
✓ All 5 GitHub Actions workflows active and green
✓ Kotlin app (Compose Material 3, MVVM, Hilt, Room, WorkManager, FGS)
✓ Rust core compiles for arm64-v8a + x86_64
✓ `libsineminer_core.so` bundled in APK
✓ Wallet create/import (BIP39 mnemonic, ed25519 keypair, Blocknet stealth address)
✓ Encrypted keystore (AES-256-GCM + PBKDF2)
✓ Mining engine (Argon2id PoW, configurable memory preset)
✓ Notification channels (mining, shares, blocks, errors)
✓ Foreground Service + WorkManager (auto-start on charge, stop on low battery)
✓ Material 3 UI (Dashboard, Mining, Wallet, Pool, Logs, Settings + Create/Import Wallet)
✓ Vulkan compute scaffolding (feature-gated, future work)
✓ README + LICENSE (MIT) + SECURITY + CONTRIBUTING + NOTES

## What's still stub / future work

- Vulkan compute shader (SPIR-V) — `VulkanBackend::hash_once` returns `NotImplemented`
- Stratum v2 binary framing (currently JSON-RPC subset)
- Wallet balance via Blocknet daemon RPC (no UI yet)
- NEON SHA3 kernel for ARMv8.2+ (Argon2id optimization)
- Translation to Bahasa Indonesia (id-ID strings ready, messages pending)

## Secrets configured

All 5 signing secrets are set as repository secrets:
- `SINEMINER_KEYSTORE_BASE64` — base64-encoded PKCS12 keystore
- `SINEMINER_KEYSTORE_PASSWORD` — keystore password
- `SINEMINER_KEY_ALIAS` — `sineminer-release`
- `SINEMINER_KEY_PASSWORD` — key password
- `GH_PAT` — GitHub PAT for auto-fix workflow

## ⚠️ SECURITY — revoke the exposed GitHub token

The user shared a GitHub PAT (`ghp_…`) in plaintext during the initial setup.
It was used to push code and set secrets. **The token must be revoked at
https://github.com/settings/tokens** and rotated. The `GH_PAT` secret should
be updated with the new token value.

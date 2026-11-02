//! SineMiner Rust core — Blocknet Argon2id mining + BIP39 wallet + Stratum client.
//!
//! This crate is consumed by the Android app via JNI (see `jni_bridge.rs`).
//! It is also a normal rlib for host-side unit tests.

#![deny(clippy::all)]
#![warn(clippy::pedantic)]
#![allow(clippy::module_name_repetitions)]
#![allow(clippy::missing_errors_doc)]
#![allow(clippy::missing_panics_doc)]

pub mod mining;
pub mod stratum;
pub mod util;
pub mod wallet;

#[cfg(target_os = "android")]
pub mod jni_bridge;

pub use mining::{MiningConfig, MiningEngine, MiningEvent};
pub use stratum::{PoolNotification, ShareSubmission, StratumClient, StratumConfig, StratumError};
pub use wallet::{Wallet, WalletConfig, WalletError};

/// Semantic version of the core library. Matches Cargo.toml.
pub const CORE_VERSION: &str = env!("CARGO_PKG_VERSION");

/// Initialize global logger. Safe to call multiple times.
pub fn init_logger() {
    let _ = env_logger::Builder::from_env(env_logger::Env::default().default_filter_or("info"))
        .format_timestamp_millis()
        .try_init();
}

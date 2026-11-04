//! Stratum v2-style client for Blocknet mining pools.
//!
//! This is a minimal Stratum v2 client speaking the JSON-RPC protocol that
//! seine's `pool.rs` implements. We support:
//! - `mining.subscribe` / login with capabilities negotiation
//! - `mining.notify` (work templates)
//! - `mining.submit` (share submission)
//! - Automatic reconnect with backoff
//!
//! The full Stratum v2 binary framing used in production mining pools is NOT
//! implemented here — we speak the JSON-RPC subset that seine's pool client uses
//! over plain TCP.

pub mod capabilities;
pub mod client;
pub mod protocol;

pub use capabilities::{ClientCapabilities, CAP_LOGIN_NEGOTIATION, CAP_VALIDATION_STATUS, CAP_SUBMIT_CLAIMED_HASH, CAP_DIFFICULTY_HINT, CAP_SAME_TEMPLATE_REBIND_V1, CAP_SUBMIT_TIMING_V1};
pub use client::StratumClient;
pub use protocol::{LoginRequest, LoginResponse, NotifyMessage, SubmitRequest, SubmitResponse, PoolNotification};

/// Stratum configuration.
#[derive(Debug, Clone)]
pub struct StratumConfig {
    /// Pool host (e.g. "bntpool.com")
    pub host: String,
    /// Pool port (e.g. 3333)
    pub port: u16,
    /// Worker name (often derived from wallet address)
    pub worker: String,
    /// Optional password (most pools accept "x")
    pub password: String,
    /// Reconnect backoff base (seconds).
    pub reconnect_initial_delay_secs: u64,
    /// Maximum reconnect delay (seconds).
    pub reconnect_max_delay_secs: u64,
    /// Connect timeout (seconds).
    pub connect_timeout_secs: u64,
}

impl Default for StratumConfig {
    fn default() -> Self {
        Self {
            host: "bntpool.com".to_string(),
            port: 3333,
            worker: String::new(),
            password: "x".to_string(),
            reconnect_initial_delay_secs: 2,
            reconnect_max_delay_secs: 60,
            connect_timeout_secs: 5,
        }
    }
}

/// Stratum client errors.
#[derive(Debug, thiserror::Error)]
pub enum StratumError {
    #[error("io: {0}")]
    Io(String),
    #[error("json: {0}")]
    Json(String),
    #[error("login rejected: {0}")]
    LoginRejected(String),
    #[error("not connected")]
    NotConnected,
    #[error("timeout")]
    Timeout,
    #[error("protocol: {0}")]
    Protocol(String),
    #[error("submit rejected: {0}")]
    SubmitRejected(String),
}

//! Stratum JSON-RPC protocol messages.
//!
//! These are wire-format types, not consensus types. They follow the
//! Stratum v2 JSON-RPC subset that seine's pool.rs uses.

use serde::{Deserialize, Serialize};

/// Login/subscribe request sent to the pool.
#[derive(Debug, Clone, Serialize)]
pub struct LoginRequest {
    pub id: u64,
    pub method: String,
    pub params: LoginParams,
}

#[derive(Debug, Clone, Serialize)]
pub struct LoginParams {
    pub worker: String,
    pub password: String,
    pub capabilities: Vec<String>,
    pub protocol_version: u32,
}

impl LoginRequest {
    pub fn new(id: u64, worker: &str, password: &str, capabilities: Vec<String>) -> Self {
        Self {
            id,
            method: "mining.subscribe".to_string(),
            params: LoginParams {
                worker: worker.to_string(),
                password: password.to_string(),
                capabilities,
                protocol_version: 2,
            },
        }
    }
}

/// Login response from the pool.
#[derive(Debug, Clone, Deserialize)]
pub struct LoginResponse {
    pub id: u64,
    pub result: Option<LoginResult>,
    pub error: Option<PoolError>,
}

#[derive(Debug, Clone, Deserialize)]
pub struct LoginResult {
    pub session_id: Option<String>,
    pub accepted_capabilities: Option<Vec<String>>,
    pub difficulty: Option<f64>,
    pub target: Option<String>,
}

#[derive(Debug, Clone, Deserialize)]
pub struct PoolError {
    pub code: i64,
    pub message: String,
}

/// `mining.notify` message — a new work template.
#[derive(Debug, Clone, Deserialize)]
pub struct NotifyMessage {
    pub method: String,
    pub params: NotifyParams,
}

#[derive(Debug, Clone, Deserialize)]
pub struct NotifyParams {
    pub job_id: String,
    pub header_base: String,
    pub target: String,
    pub height: Option<u64>,
    pub prev_hash: Option<String>,
}

/// `mining.submit` request — submit a found share.
#[derive(Debug, Clone, Serialize)]
pub struct SubmitRequest {
    pub id: u64,
    pub method: String,
    pub params: SubmitParams,
}

#[derive(Debug, Clone, Serialize)]
pub struct SubmitParams {
    pub worker: String,
    pub job_id: String,
    pub nonce: u64,
    pub hash_hex: String,
}

impl SubmitRequest {
    pub fn new(id: u64, worker: &str, job_id: &str, nonce: u64, hash_hex: &str) -> Self {
        Self {
            id,
            method: "mining.submit".to_string(),
            params: SubmitParams {
                worker: worker.to_string(),
                job_id: job_id.to_string(),
                nonce,
                hash_hex: hash_hex.to_string(),
            },
        }
    }
}

/// Submit response from the pool.
#[derive(Debug, Clone, Deserialize)]
pub struct SubmitResponse {
    pub id: u64,
    pub result: Option<bool>,
    pub error: Option<PoolError>,
}

/// A notification received from the pool (push message).
#[derive(Debug, Clone)]
pub enum PoolNotification {
    /// New work template.
    Notify {
        job_id: String,
        header_base_hex: String,
        target_hex: String,
        height: u64,
    },
    /// Pool found a block, switch work.
    PoolBlockSolved,
    /// Difficulty update.
    DifficultyUpdate { new_difficulty: f64, new_target_hex: String },
    /// Share was accepted.
    ShareAccepted,
    /// Share was rejected.
    ShareRejected { reason: String },
    /// Pool disconnected us.
    Disconnected,
}

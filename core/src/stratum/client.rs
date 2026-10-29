//! Stratum TCP client — connects to a Blocknet pool, sends login, receives
//! notifications, submits shares.
//!
//! Architecture:
//! - Background task owns the TCP stream.
//! - Inbound channel: `PoolNotification` for the miner to consume.
//! - Outbound channel: `SubmitRequest` for the miner to send shares.
//! - Reconnect with exponential backoff on disconnect.

use std::sync::Arc;
use std::time::Duration;

use anyhow::Context;
use tokio::io::{AsyncBufReadExt, AsyncWriteExt, BufReader};
use tokio::net::TcpStream;
use tokio::sync::{Mutex, mpsc};
use tokio::time;

use super::capabilities::ClientCapabilities;
use super::protocol::{
    LoginRequest, LoginResponse, NotifyMessage, PoolNotification, SubmitRequest, SubmitResponse,
};
use super::{StratumConfig, StratumError};

/// Connection state.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum ConnState {
    Disconnected,
    Connecting,
    Connected,
    LoggedIn,
    Failed,
}

/// The Stratum client. Owns the connection lifecycle.
pub struct StratumClient {
    config: StratumConfig,
    state: Arc<Mutex<ConnState>>,
    notify_tx: mpsc::UnboundedSender<PoolNotification>,
    submit_rx: Arc<Mutex<mpsc::UnboundedReceiver<SubmitRequest>>>,
    next_request_id: Arc<Mutex<u64>>,
}

impl StratumClient {
    /// Create a new client. Use `start()` to launch the connection task.
    #[must_use]
    pub fn new(config: StratumConfig) -> (Self, mpsc::UnboundedReceiver<PoolNotification>, mpsc::UnboundedSender<SubmitRequest>) {
        let (notify_tx, notify_rx) = mpsc::unbounded_channel();
        let (submit_tx, submit_rx) = mpsc::unbounded_channel();
        let client = Self {
            config,
            state: Arc::new(Mutex::new(ConnState::Disconnected)),
            notify_tx,
            submit_rx: Arc::new(Mutex::new(submit_rx)),
            next_request_id: Arc::new(Mutex::new(1)),
        };
        (client, notify_rx, submit_tx)
    }

    /// Spawn the connection task. Returns immediately; the task runs in background.
    pub fn start(self) -> tokio::task::JoinHandle<()> {
        let config = self.config.clone();
        let state = Arc::clone(&self.state);
        let notify_tx = self.notify_tx.clone();
        let submit_rx = Arc::clone(&self.submit_rx);
        let next_id = Arc::clone(&self.next_request_id);

        tokio::spawn(async move {
            let mut backoff = config.reconnect_initial_delay_secs;
            loop {
                *state.lock().await = ConnState::Connecting;
                match connect_and_run(&config, &state, &notify_tx, &submit_rx, &next_id).await {
                    Ok(()) => {
                        backoff = config.reconnect_initial_delay_secs;
                    }
                    Err(e) => {
                        log::warn!("stratum client error: {e:#}");
                        backoff = (backoff * 2).min(config.reconnect_max_delay_secs);
                    }
                }
                *state.lock().await = ConnState::Disconnected;
                let _ = notify_tx.send(PoolNotification::Disconnected);
                time::sleep(Duration::from_secs(backoff)).await;
            }
        })
    }

    /// Get the current connection state.
    ///
    /// # Errors
    /// Returns an error if the lock is poisoned.
    pub async fn state(&self) -> ConnState {
        *self.state.lock().await
    }

    /// Allocate the next request id atomically.
    async fn next_id(&self) -> u64 {
        let mut g = self.next_request_id.lock().await;
        let id = *g;
        *g += 1;
        id
    }
}

async fn connect_and_run(
    config: &StratumConfig,
    state: &Arc<Mutex<ConnState>>,
    notify_tx: &mpsc::UnboundedSender<PoolNotification>,
    submit_rx: &Arc<Mutex<mpsc::UnboundedReceiver<SubmitRequest>>>,
    next_id: &Arc<Mutex<u64>>,
) -> anyhow::Result<()> {
    let addr = format!("{}:{}", config.host, config.port);
    let timeout = Duration::from_secs(config.connect_timeout_secs);
    let stream = time::timeout(timeout, TcpStream::connect(&addr))
        .await
        .context("connect timeout")?
        .context("connect failed")?;
    stream.set_nodelay(true).ok();
    *state.lock().await = ConnState::Connected;

    let (read_half, mut write_half) = stream.into_split();
    let mut reader = BufReader::new(read_half);

    // Send login
    let login_id = {
        let mut g = next_id.lock().await;
        let id = *g;
        *g += 1;
        id
    };
    let caps = ClientCapabilities::default().as_strings();
    let login = LoginRequest::new(login_id, &config.worker, &config.password, caps);
    let login_json = serde_json::to_string(&login).context("login serialize")?;
    write_half.write_all(login_json.as_bytes()).await?;
    write_half.write_all(b"\n").await?;
    write_half.flush().await?;

    // Read login response
    let mut line = String::new();
    reader.read_line(&mut line).await?;
    let resp: LoginResponse = serde_json::from_str(&line).map_err(|e| StratumError::Json(e.to_string()))?;
    if let Some(err) = resp.error {
        return Err(anyhow::anyhow!(StratumError::LoginRejected(err.message)));
    }
    *state.lock().await = ConnState::LoggedIn;
    log::info!("logged in to pool {}:{}", config.host, config.port);

    // Main loop: read notifications, submit shares
    loop {
        tokio::select! {
            // Read from pool
            read_result = reader.read_line(&mut line) => {
                match read_result {
                    Ok(0) => return Err(anyhow::anyhow!("pool closed connection")),
                    Ok(_) => {
                        if let Err(e) = handle_pool_message(&line.trim(), notify_tx) {
                            log::debug!("failed to parse pool message: {e}");
                        }
                        line.clear();
                    }
                    Err(e) => return Err(anyhow::anyhow!(StratumError::Io(e.to_string()))),
                }
            }
            // Submit pending shares
            submit_opt = async {
                let mut rx = submit_rx.lock().await;
                rx.recv().await
            } => {
                let Some(submit) = submit_opt else { continue; };
                let json = serde_json::to_string(&submit).map_err(|e| StratumError::Json(e.to_string()))?;
                write_half.write_all(json.as_bytes()).await?;
                write_half.write_all(b"\n").await?;
                write_half.flush().await?;
            }
        }
    }
}

fn handle_pool_message(line: &str, notify_tx: &mpsc::UnboundedSender<PoolNotification>) -> Result<(), String> {
    if line.is_empty() {
        return Ok(());
    }
    let value: serde_json::Value = serde_json::from_str(line).map_err(|e| e.to_string())?;

    // Check if it's a notification (has "method")
    if let Some(method) = value.get("method").and_then(|v| v.as_str()) {
        match method {
            "mining.notify" => {
                let notify: NotifyMessage = serde_json::from_value(value).map_err(|e| e.to_string())?;
                let _ = notify_tx.send(PoolNotification::Notify {
                    job_id: notify.params.job_id,
                    header_base_hex: notify.params.header_base,
                    target_hex: notify.params.target,
                    height: notify.params.height.unwrap_or(0),
                });
            }
            "mining.set_difficulty" => {
                if let Some(diff) = value.get("params").and_then(|p| p.get(0)).and_then(|v| v.as_f64()) {
                    let _ = notify_tx.send(PoolNotification::DifficultyUpdate {
                        new_difficulty: diff,
                        new_target_hex: super::super::mining::target::target_to_hex(
                            &super::super::mining::target::difficulty_to_target(diff),
                        ),
                    });
                }
            }
            "pool_block_solved" => {
                let _ = notify_tx.send(PoolNotification::PoolBlockSolved);
            }
            _ => {
                log::debug!("unknown pool method: {method}");
            }
        }
        return Ok(());
    }

    // It's a response to one of our requests
    if let Some(result) = value.get("result") {
        if let Some(err) = value.get("error") {
            if !err.is_null() {
                let reason = err.get(1).and_then(|v| v.as_str()).unwrap_or("unknown").to_string();
                let _ = notify_tx.send(PoolNotification::ShareRejected { reason });
                return Ok(());
            }
        }
        if result.as_bool() == Some(true) {
            let _ = notify_tx.send(PoolNotification::ShareAccepted);
        }
    }

    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn client_new_returns_channels() {
        let cfg = StratumConfig {
            host: "example.com".to_string(),
            port: 3333,
            worker: "worker1".to_string(),
            password: "x".to_string(),
            reconnect_initial_delay_secs: 1,
            reconnect_max_delay_secs: 5,
            connect_timeout_secs: 1,
        };
        let (_client, _notify_rx, _submit_tx) = StratumClient::new(cfg);
        // Sanity check: the constructor returned without panic.
    }

    #[test]
    fn capabilities_default_advertises_seine_subset() {
        let caps = ClientCapabilities::default();
        let strs = caps.as_strings();
        assert!(strs.contains(&super::super::capabilities::CAP_LOGIN_NEGOTIATION.to_string()));
    }
}

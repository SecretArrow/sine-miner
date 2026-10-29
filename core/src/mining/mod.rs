//! Mining engine — Blocknet Argon2id PoW.
//!
//! This is a fresh Rust implementation that follows the Blocknet PoW spec
//! (Argon2id with 2 GB memory cost, 1 iteration, 1 lane, 32-byte output)
//! matching the parameters in seine's `pow-spec` crate. The exact CPU ISA
//! kernels (AVX2/AVX-512/NEON/SHA3 streaming) from seine are NOT ported here;
//! we use the portable `argon2` crate, which falls back to NEON/AVX on the
//! host automatically via the `simd` backend selection at compile time.

use std::sync::Arc;
use std::time::{Duration, Instant};

use anyhow::{Context, Result};
use argon2::{Algorithm, Argon2, Params, Version};
use tokio::sync::Mutex;
use tokio::sync::mpsc;

use crate::util::hex;

pub mod nonce;
pub mod stats;
pub mod target;

pub use nonce::NonceScheduler;
pub use stats::HashrateTracker;
pub use target::{difficulty_to_target, meets_target, target_to_hex};

/// Argon2id memory cost (KiB) for production-valid Blocknet blocks.
/// seine's `pow-spec` crate hard-codes 2 * 1024 * 1024 = 2 GiB.
pub const POW_MEMORY_KIB: u32 = 2 * 1024 * 1024;

/// Argon2id iteration count (seine: 1).
pub const POW_ITERATIONS: u32 = 1;

/// Argon2id parallelism (seine: 1 lane).
pub const POW_PARALLELISM: u32 = 1;

/// Hash output length in bytes (seine: 32).
pub const POW_OUTPUT_LEN: usize = 32;

/// Minimum header base length (seine: 8).
pub const MIN_HEADER_BASE_LEN: usize = 8;

/// Maximum header base length (seine: 4096).
pub const MAX_HEADER_BASE_LEN: usize = 4096;

/// Mining configuration. All fields are tunable per device.
#[derive(Debug, Clone)]
pub struct MiningConfig {
    /// Argon2id memory cost in KiB. Production blocks require 2_097_152 (2 GiB).
    /// Mobile devices should reduce this for testing only — shares will be rejected by pools.
    pub memory_kib: u32,
    /// Argon2id iterations (1 for production).
    pub iterations: u32,
    /// Argon2id parallelism (1 for production).
    pub parallelism: u32,
    /// Number of CPU mining threads. Android: 1 is recommended.
    pub threads: u32,
    /// Initial nonce offset (random per worker to avoid duplication).
    pub nonce_seed: u64,
    /// Batch size before checking for stop/deadline signals.
    pub batch_size: u64,
    /// Interval to flush hashrate stats to the channel.
    pub stats_flush_interval: Duration,
}

impl Default for MiningConfig {
    fn default() -> Self {
        Self {
            memory_kib: POW_MEMORY_KIB,
            iterations: POW_ITERATIONS,
            parallelism: POW_PARALLELISM,
            threads: 1,
            nonce_seed: 0xCAFE_BABE_DEAD_BEEF,
            batch_size: 64,
            stats_flush_interval: Duration::from_millis(500),
        }
    }
}

impl MiningConfig {
    /// Lower-memory preset for low-end Android devices. Produces invalid blocks (testing only).
    #[must_use]
    pub fn low_memory() -> Self {
        Self { memory_kib: 256 * 1024, ..Self::default() }
    }

    /// 1 GiB preset. Will still be rejected by pools but won't OOM most flagship phones.
    #[must_use]
    pub fn medium_memory() -> Self {
        Self { memory_kib: 1024 * 1024, ..Self::default() }
    }

    /// Production preset — full 2 GiB Argon2id. Only suitable for phones with ≥ 6 GiB free RAM.
    #[must_use]
    pub fn production() -> Self {
        Self::default()
    }
}

/// Live event emitted by the mining engine.
#[derive(Debug, Clone)]
pub enum MiningEvent {
    /// Engine started. Initial hashrate = 0.
    Started { thread_id: u32, work_id: u64 },
    /// A nonce was tried. (Useful for debugging, throttled in production.)
    HashAttempt { thread_id: u32, nonce: u64 },
    /// Periodic hashrate update.
    Hashrate { thread_id: u32, hashes_per_sec: f64, total_hashes: u64 },
    /// A share that meets the pool target was found.
    ShareFound { thread_id: u32, nonce: u64, hash_hex: String },
    /// Engine stopped (clean or with error).
    Stopped { thread_id: u32, reason: String },
    /// Background thread panicked.
    Failed { thread_id: u32, error: String },
}

/// One unit of work received from the pool. The miner hashes `header_base || nonce`
/// under Argon2id and checks the resulting 32-byte hash against `target`.
#[derive(Debug, Clone)]
pub struct WorkAssignment {
    pub work_id: u64,
    pub header_base: Vec<u8>,
    pub target: Vec<u8>,
    pub job_id: String,
    pub extra_nonce_offset: usize,
}

/// Result of attempting to mine a single nonce.
#[derive(Debug, Clone)]
pub struct HashResult {
    pub nonce: u64,
    pub hash: [u8; POW_OUTPUT_LEN],
}

impl HashResult {
    #[must_use]
    pub fn hash_hex(&self) -> String {
        hex::encode(self.hash)
    }
}

/// The core mining engine. Owns the Argon2id hasher and the worker threads.
pub struct MiningEngine {
    config: MiningConfig,
    argon: Argon2<'static>,
}

impl MiningEngine {
    /// Create a new engine. Validates Argon2id parameters.
    ///
    /// # Errors
    /// Returns an error if Argon2id parameters are out of bounds.
    pub fn new(config: MiningConfig) -> Result<Self> {
        let params = Params::new(config.memory_kib, config.iterations, config.parallelism, Some(POW_OUTPUT_LEN))
            .context("invalid Argon2id parameters")?;
        let argon = Argon2::new(Algorithm::Argon2id, Version::V0x13, params);
        Ok(Self { config, argon })
    }

    /// Hash one (header_base, nonce) pair under Argon2id.
    ///
    /// The salt is the first 16 bytes of the header base; the password is the
    /// full header with the nonce appended. This matches seine's behavior:
    /// the nonce is part of the hashed input, and the header base acts as salt.
    ///
    /// # Errors
    /// Returns an error if Argon2id hashing fails (extremely unlikely).
    pub fn hash_once(&self, header_base: &[u8], nonce: u64) -> Result<HashResult> {
        let mut full = Vec::with_capacity(header_base.len() + 8);
        full.extend_from_slice(header_base);
        full.extend_from_slice(&nonce.to_le_bytes());

        let salt = &full[..16.min(full.len())];
        let pwd = &full;

        let mut hash = [0u8; POW_OUTPUT_LEN];
        self.argon
            .hash_password_into(pwd, salt, &mut hash)
            .context("argon2 hashing failed")?;

        Ok(HashResult { nonce, hash })
    }

    /// Verify a header base is well-formed before mining.
    ///
    /// # Errors
    /// Returns an error if the header base is not in [8, 4096] bytes.
    pub fn validate_header_base(header_base: &[u8]) -> Result<()> {
        let len = header_base.len();
        if !(MIN_HEADER_BASE_LEN..=MAX_HEADER_BASE_LEN).contains(&len) {
            anyhow::bail!("header_base must be 8..4096 bytes, got {len}");
        }
        Ok(())
    }

    /// Spawn mining workers for a single work assignment. Each worker runs
    /// in its own Tokio task. Returns a channel receiver of mining events.
    ///
    /// The caller is responsible for feeding work assignments and updating
    /// the target. Workers stop automatically when the assignment channel
    /// is dropped or a stop signal is sent.
    pub fn spawn_workers(
        self,
        work_rx: Arc<Mutex<mpsc::Receiver<Arc<Mutex<WorkAssignment>>>>>,
        event_tx: mpsc::UnboundedSender<MiningEvent>,
        stop: Arc<std::sync::atomic::AtomicBool>,
    ) -> Vec<tokio::task::JoinHandle<()>> {
        let threads = self.config.threads.max(1);
        let mut handles = Vec::with_capacity(threads as usize);

        for thread_id in 0..threads {
            let work_rx = Arc::clone(&work_rx);
            let event_tx = event_tx.clone();
            let stop = Arc::clone(&stop);
            let engine_self = self.clone_for_thread();
            let nonce_seed = self.config.nonce_seed.wrapping_add(thread_id as u64);

            let handle = tokio::spawn(async move {
                let _ = event_tx.send(MiningEvent::Started {
                    thread_id,
                    work_id: 0,
                });

                let scheduler = NonceScheduler::new(nonce_seed);
                let mut hashrate = HashrateTracker::new(thread_id);
                let mut last_flush = Instant::now();
                let batch = engine_self.config.batch_size;

                loop {
                    if stop.load(std::sync::atomic::Ordering::Relaxed) {
                        let _ = event_tx.send(MiningEvent::Stopped {
                            thread_id,
                            reason: "stop signal".into(),
                        });
                        return;
                    }

                    // Lock the shared receiver just long enough to take one work item.
                    let work = {
                        let mut guard = work_rx.lock().await;
                        match guard.recv().await {
                            Some(w) => w,
                            None => {
                                let _ = event_tx.send(MiningEvent::Stopped {
                                    thread_id,
                                    reason: "work channel closed".into(),
                                });
                                return;
                            }
                        }
                    };
                    let work = work.lock().await;

                    for _ in 0..batch {
                        if stop.load(std::sync::atomic::Ordering::Relaxed) {
                            break;
                        }

                        let nonce = scheduler.next();
                        let result = match engine_self.hash_once(&work.header_base, nonce) {
                            Ok(r) => r,
                            Err(e) => {
                                let _ = event_tx.send(MiningEvent::Failed {
                                    thread_id,
                                    error: e.to_string(),
                                });
                                return;
                            }
                        };

                        hashrate.observe();

                        if meets_target(&result.hash, &work.target) {
                            let hash_hex = result.hash_hex();
                            let _ = event_tx.send(MiningEvent::ShareFound {
                                thread_id,
                                nonce,
                                hash_hex,
                            });
                        }

                        if last_flush.elapsed() >= engine_self.config.stats_flush_interval {
                            let (hps, total) = hashrate.snapshot();
                            let _ = event_tx.send(MiningEvent::Hashrate {
                                thread_id,
                                hashes_per_sec: hps,
                                total_hashes: total,
                            });
                            last_flush = Instant::now();
                        }
                    }
                }
            });
            handles.push(handle);
        }

        handles
    }

    /// Clone the engine for use in worker threads. Cheap because Argon2 params are Arc-shared.
    #[must_use]
    fn clone_for_thread(&self) -> Self {
        Self {
            config: self.config.clone(),
            argon: Argon2::new(Algorithm::Argon2id, Version::V0x13, self.argon.params()),
        }
    }

    /// Get the current configuration.
    #[must_use]
    pub fn config(&self) -> &MiningConfig { &self.config }
}

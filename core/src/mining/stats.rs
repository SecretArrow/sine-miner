//! Hashrate tracker — exponential moving average of hashes per second.
//!
//! Each worker thread owns one tracker. Observations are timestamped; the
//! EMA smooths out jitter. We also keep the total hash count for the session.

use std::time::{Duration, Instant};

/// EMA smoothing factor. 0.1 means new sample contributes 10% to the moving average.
const EMA_ALPHA: f64 = 0.1;
/// Minimum samples before reporting a stable hashrate.
const MIN_SAMPLES_FOR_STABLE: u64 = 10;

/// Per-worker hashrate tracker.
pub struct HashrateTracker {
    thread_id: u32,
    ema: f64,
    last_sample_at: Option<Instant>,
    total_hashes: u64,
    samples: u64,
}

impl HashrateTracker {
    #[must_use]
    pub fn new(thread_id: u32) -> Self {
        Self {
            thread_id,
            ema: 0.0,
            last_sample_at: None,
            total_hashes: 0,
            samples: 0,
        }
    }

    /// Record one hash attempt.
    pub fn observe(&mut self) {
        self.total_hashes = self.total_hashes.saturating_add(1);
        self.samples = self.samples.saturating_add(1);

        let now = Instant::now();
        if let Some(last) = self.last_sample_at {
            let elapsed = now.duration_since(last).as_secs_f64().max(1e-9);
            let instant_hps = 1.0 / elapsed;
            if self.ema == 0.0 {
                self.ema = instant_hps;
            } else {
                self.ema = EMA_ALPHA * instant_hps + (1.0 - EMA_ALPHA) * self.ema;
            }
        }
        self.last_sample_at = Some(now);
    }

    /// Take a snapshot of the current hashrate and total hash count.
    #[must_use]
    pub fn snapshot(&self) -> (f64, u64) {
        // If we have very few samples, the EMA is unreliable — report 0.
        if self.samples < MIN_SAMPLES_FOR_STABLE {
            return (0.0, self.total_hashes);
        }
        (self.ema, self.total_hashes)
    }

    /// Total hashes observed since tracker creation.
    #[must_use]
    pub fn total_hashes(&self) -> u64 {
        self.total_hashes
    }

    /// Owning thread id.
    #[must_use]
    pub fn thread_id(&self) -> u32 {
        self.thread_id
    }

    /// Reset the tracker (e.g., for a new mining round).
    pub fn reset(&mut self) {
        self.ema = 0.0;
        self.last_sample_at = None;
        self.total_hashes = 0;
        self.samples = 0;
    }
}

/// Aggregate hashrate across multiple workers.
pub struct AggregateHashrate {
    workers: Vec<HashrateTracker>,
}

impl AggregateHashrate {
    #[must_use]
    pub fn new(worker_count: usize) -> Self {
        let workers = (0..worker_count)
            .map(|i| HashrateTracker::new(i as u32))
            .collect();
        Self { workers }
    }

    /// Total hashrate (sum of all workers' EMA) and total hash count.
    #[must_use]
    pub fn snapshot(&self) -> (f64, u64) {
        let total_hps: f64 = self.workers.iter().map(|w| w.snapshot().0).sum();
        let total_hashes: u64 = self.workers.iter().map(|w| w.total_hashes()).sum();
        (total_hps, total_hashes)
    }

    /// Estimate time to find a block at current hashrate, given network difficulty.
    /// Returns `None` if hashrate is 0 or difficulty is 0.
    #[must_use]
    pub fn estimate_time_to_block(
        &self,
        network_hashrate: f64,
        block_time: Duration,
    ) -> Option<Duration> {
        let (my_hps, _) = self.snapshot();
        if my_hps <= 0.0 || network_hashrate <= 0.0 {
            return None;
        }
        let share = my_hps / network_hashrate;
        let expected_secs = block_time.as_secs_f64() / share;
        Some(Duration::from_secs_f64(expected_secs))
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn snapshot_is_zero_until_min_samples() {
        let mut t = HashrateTracker::new(0);
        for _ in 0..(MIN_SAMPLES_FOR_STABLE - 1) {
            t.observe();
        }
        let (hps, total) = t.snapshot();
        assert_eq!(hps, 0.0);
        assert_eq!(total, MIN_SAMPLES_FOR_STABLE - 1);
    }

    #[test]
    fn total_hashes_count_is_accurate() {
        let mut t = HashrateTracker::new(0);
        for _ in 0..1000 {
            t.observe();
        }
        assert_eq!(t.total_hashes(), 1000);
    }

    #[test]
    fn reset_clears_state() {
        let mut t = HashrateTracker::new(0);
        for _ in 0..1000 {
            t.observe();
        }
        t.reset();
        assert_eq!(t.total_hashes(), 0);
        assert_eq!(t.samples, 0);
    }

    #[test]
    fn aggregate_sums_workers() {
        let agg = AggregateHashrate::new(4);
        // All workers have 0 EMA initially
        let (hps, total) = agg.snapshot();
        assert_eq!(hps, 0.0);
        assert_eq!(total, 0);
    }
}

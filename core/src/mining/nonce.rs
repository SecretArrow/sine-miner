//! Nonce scheduler — generates nonces deterministically per worker thread.
//!
//! Each worker has its own scheduler seeded with a unique offset. Nonces are
//! 64-bit little-endian. We use a wrapping counter starting at the seed; this
//! avoids collisions between threads without locking.

use std::sync::atomic::{AtomicU64, Ordering};

/// Per-worker nonce generator. Cheap to clone if needed (rare).
pub struct NonceScheduler {
    counter: AtomicU64,
}

impl NonceScheduler {
    /// Create a new scheduler seeded with `seed`. Two schedulers with different
    /// seeds will produce non-overlapping sequences (modulo 2^64 wrap-around).
    #[must_use]
    pub fn new(seed: u64) -> Self {
        Self {
            counter: AtomicU64::new(seed),
        }
    }

    /// Return the next nonce.
    #[must_use]
    pub fn next(&self) -> u64 {
        self.counter.fetch_add(1, Ordering::Relaxed)
    }

    /// Current nonce value (without advancing).
    #[must_use]
    pub fn current(&self) -> u64 {
        self.counter.load(Ordering::Relaxed)
    }

    /// Reset the scheduler back to a specific nonce. Used when a worker picks
    /// up new work and wants to restart scanning from a known offset.
    pub fn reset(&self, nonce: u64) {
        self.counter.store(nonce, Ordering::Relaxed);
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn next_advances_and_does_not_repeat_within_2pow32() {
        let s = NonceScheduler::new(0);
        let first = s.next();
        let second = s.next();
        assert_eq!(first, 0);
        assert_eq!(second, 1);
        // Should not repeat for a long time
        let mut last = second;
        for _ in 0..10_000 {
            let n = s.next();
            assert_ne!(n, last);
            last = n;
        }
    }

    #[test]
    fn distinct_seeds_produce_distinct_sequences() {
        let a = NonceScheduler::new(0);
        let b = NonceScheduler::new(1_000_000);
        for _ in 0..1000 {
            assert_ne!(a.next(), b.next());
        }
    }

    #[test]
    fn reset_brings_counter_back() {
        let s = NonceScheduler::new(0);
        for _ in 0..1000 {
            s.next();
        }
        s.reset(42);
        assert_eq!(s.next(), 42);
    }

    #[test]
    fn wraps_around_2pow64() {
        let s = NonceScheduler::new(u64::MAX);
        assert_eq!(s.next(), u64::MAX);
        // Wrapping
        assert_eq!(s.next(), 0);
        assert_eq!(s.next(), 1);
    }
}

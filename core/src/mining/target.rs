//! Difficulty target utilities.
//!
//! In Blocknet (and Bitcoin-family chains), the target is a 32-byte big-endian
//! threshold. A hash `H` "meets the target" iff `H <= target` interpreted as
//! big-endian integers. The pool supplies the target as part of work assignment.

/// Compare a hash to a target. Both are 32-byte slices. Returns true if the
/// hash meets the target (i.e., is less than or equal to it as a big-endian integer).
///
/// # Panics
/// Panics if either slice is not exactly 32 bytes.
#[must_use]
pub fn meets_target(hash: &[u8], target: &[u8]) -> bool {
    assert_eq!(hash.len(), 32, "hash must be 32 bytes");
    assert_eq!(target.len(), 32, "target must be 32 bytes");
    // Big-endian lexicographic compare == integer compare
    hash <= target
}

/// Convert a difficulty (float, larger = harder) to a 32-byte big-endian target.
/// `target = max_target / difficulty`, where `max_target` is the all-FF value.
#[must_use]
pub fn difficulty_to_target(difficulty: f64) -> [u8; 32] {
    if difficulty <= 0.0 || !difficulty.is_finite() {
        return [0xFF; 32]; // easiest possible target
    }
    // max_target = 2^256 - 1
    // target = max_target / difficulty
    // We compute this as a f64 -> u256 approximation, good enough for pool shares.
    let max_target_f = 2.0_f64.powi(256) - 1.0;
    let target_f = max_target_f / difficulty;
    // Pack into 32 bytes big-endian.
    let target_f = target_f.clamp(0.0, max_target_f);
    // Convert f64 to u256 via 8-byte chunks
    let mut target = [0u8; 32];
    // Use the floating-point approximation: shift mantissa to top of 256-bit value
    let (mantissa, exp, sign) = (target_f / 2.0_f64.powi(53), 53, 1.0);
    let _ = (mantissa, exp, sign); // unused for now
                                   // Easier: split target_f into 4 u64 limbs
    let scaled = target_f / 2.0_f64.powi(192); // upper 64 bits
    let upper = scaled as u64;
    let mid1 = ((target_f - (upper as f64) * 2.0_f64.powi(192)) / 2.0_f64.powi(128)) as u64;
    let mid2 = ((target_f - (upper as f64) * 2.0_f64.powi(192))
        - (mid1 as f64) * 2.0_f64.powi(128))
        / 2.0_f64.powi(64);
    let mid2 = mid2 as u64;
    let lower = (target_f
        - (upper as f64) * 2.0_f64.powi(192)
        - (mid1 as f64) * 2.0_f64.powi(128)
        - (mid2 as f64) * 2.0_f64.powi(64)) as u64;
    target[0..8].copy_from_slice(&upper.to_be_bytes());
    target[8..16].copy_from_slice(&mid1.to_be_bytes());
    target[16..24].copy_from_slice(&mid2.to_be_bytes());
    target[24..32].copy_from_slice(&lower.to_be_bytes());
    target
}

/// Convert a 32-byte big-endian target to a hex string with 0x prefix.
#[must_use]
pub fn target_to_hex(target: &[u8]) -> String {
    use crate::util::hex;
    format!("0x{}", hex::encode(target))
}

/// Parse a hex target (with or without 0x prefix) into a 32-byte array.
///
/// # Errors
/// Returns an error if the hex is malformed or not 64 characters long.
pub fn parse_target(s: &str) -> Result<[u8; 32], String> {
    let s = s
        .strip_prefix("0x")
        .or_else(|| s.strip_prefix("0X"))
        .unwrap_or(s);
    if s.len() != 64 {
        return Err(format!("expected 64 hex chars, got {}", s.len()));
    }
    let bytes = crate::util::hex::decode(s).map_err(|e| e.to_string())?;
    let mut out = [0u8; 32];
    out.copy_from_slice(&bytes);
    Ok(out)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn zero_hash_meets_max_target() {
        let hash = [0u8; 32];
        let target = [0xFFu8; 32];
        assert!(meets_target(&hash, &target));
    }

    #[test]
    fn max_hash_does_not_meet_zero_target() {
        let hash = [0xFFu8; 32];
        let target = [0u8; 32];
        assert!(!meets_target(&hash, &target));
    }

    #[test]
    fn equal_hash_meets_target() {
        let hash = [0x42u8; 32];
        let target = [0x42u8; 32];
        assert!(meets_target(&hash, &target));
    }

    #[test]
    fn lower_hash_meets_higher_target() {
        let mut hash = [0u8; 32];
        hash[31] = 0x10;
        let mut target = [0u8; 32];
        target[31] = 0x20;
        assert!(meets_target(&hash, &target));
    }

    #[test]
    fn higher_hash_does_not_meet_lower_target() {
        let mut hash = [0u8; 32];
        hash[31] = 0x20;
        let mut target = [0u8; 32];
        target[31] = 0x10;
        assert!(!meets_target(&hash, &target));
    }

    #[test]
    fn parse_target_round_trips() {
        let target = [0x42u8; 32];
        let hex = target_to_hex(&target);
        let parsed = parse_target(&hex).unwrap();
        assert_eq!(parsed, target);
    }

    #[test]
    fn parse_target_rejects_bad_length() {
        assert!(parse_target("1234").is_err());
        assert!(parse_target(&"a".repeat(63)).is_err());
        assert!(parse_target(&"a".repeat(65)).is_err());
    }
}

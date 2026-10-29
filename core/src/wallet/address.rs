//! Blocknet stealth address — spend + view public key + 4-byte SHA3-256 checksum,
//! base58-encoded.
//!
//! Format follows the documented Blocknet address scheme used in seine's
//! `address.rs`:
//! - payload: 64 bytes = spend_pub (32) || view_pub (32)
//! - checksum: first 4 bytes of SHA3-256("blocknet_stealth_address_checksum" || network_id || payload)
//! - encoded: base58(payload || checksum), 68-byte total raw
//!
//! This implementation is original — it does not import seine's code.

use bs58;
use sha3::{Digest, Sha3_256};

use super::keypair::PublicKeys;

/// Network identifiers used in the checksum.
pub const NETWORK_ID_MAINNET: &str = "blocknet_mainnet";
pub const NETWORK_ID_TESTNET: &str = "blocknet_testnet";
pub const CHECKSUM_TAG: &[u8] = b"blocknet_stealth_address_checksum";

/// Expected raw decoded length: 64 payload + 4 checksum = 68 bytes.
pub const DECODED_LEN: usize = 68;
/// Max encoded address length we accept (defensive bound).
pub const MAX_ENCODED_LEN: usize = 2048;

/// Errors from address operations.
#[derive(Debug, thiserror::Error)]
pub enum AddressError {
    #[error("address is empty")]
    Empty,
    #[error("address exceeds {0} characters")]
    TooLong(usize),
    #[error("invalid base58 encoding: {0}")]
    InvalidBase58(String),
    #[error("invalid address length: expected {DECODED_LEN} bytes, got {0}")]
    InvalidLength(usize),
    #[error("invalid checksum")]
    InvalidChecksum,
    #[error("invalid spend public key")]
    InvalidSpendKey,
    #[error("invalid view public key")]
    InvalidViewKey,
}

/// A parsed Blocknet stealth address.
#[derive(Debug, Clone, PartialEq, Eq, serde::Serialize, serde::Deserialize)]
pub struct StealthAddress {
    pub spend: [u8; 32],
    pub view: [u8; 32],
    pub network_id: String,
}

impl StealthAddress {
    /// Build a stealth address from a keypair's public keys.
    ///
    /// # Errors
    /// Returns an error if the public keys are not valid Ristretto points.
    pub fn from_keypair(pub_keys: &PublicKeys, network_id: &str) -> Result<Self, AddressError> {
        // Validate both public keys are valid Ristretto points
        use curve25519_dalek::ristretto::CompressedRistretto;
        if CompressedRistretto::from_slice(&pub_keys.spend).decompress().is_none() {
            return Err(AddressError::InvalidSpendKey);
        }
        if CompressedRistretto::from_slice(&pub_keys.view).decompress().is_none() {
            return Err(AddressError::InvalidViewKey);
        }
        Ok(Self {
            spend: pub_keys.spend,
            view: pub_keys.view,
            network_id: network_id.to_string(),
        })
    }

    /// Build the 64-byte payload (spend || view).
    #[must_use]
    pub fn payload(&self) -> [u8; 64] {
        let mut p = [0u8; 64];
        p[..32].copy_from_slice(&self.spend);
        p[32..].copy_from_slice(&self.view);
        p
    }

    /// Compute the 4-byte checksum.
    #[must_use]
    pub fn checksum(&self) -> [u8; 4] {
        let mut hasher = Sha3_256::new();
        hasher.update(CHECKSUM_TAG);
        hasher.update(self.network_id.as_bytes());
        hasher.update(self.payload());
        let digest = hasher.finalize();
        let mut out = [0u8; 4];
        out.copy_from_slice(&digest[..4]);
        out
    }

    /// Encode as a base58 string.
    #[must_use]
    pub fn encode(&self) -> String {
        let mut buf = Vec::with_capacity(DECODED_LEN);
        buf.extend_from_slice(&self.payload());
        buf.extend_from_slice(&self.checksum());
        bs58::encode(&buf).into_string()
    }

    /// Parse a base58-encoded Blocknet address. Validates both the checksum
    /// (against mainnet and testnet) and that both public keys are valid
    /// Ristretto points.
    ///
    /// # Errors
    /// Returns an error if the input is malformed.
    pub fn parse(s: &str) -> Result<Self, AddressError> {
        Self::parse_with_network(s, None)
    }

    /// Parse a base58-encoded Blocknet address with a specific network id hint.
    /// If `network_hint` is `None`, both mainnet and testnet are tried.
    ///
    /// # Errors
    /// Returns an error if the input is malformed.
    pub fn parse_with_network(s: &str, network_hint: Option<&str>) -> Result<Self, AddressError> {
        let trimmed = s.trim();
        if trimmed.is_empty() {
            return Err(AddressError::Empty);
        }
        if trimmed.len() > MAX_ENCODED_LEN {
            return Err(AddressError::TooLong(MAX_ENCODED_LEN));
        }

        let decoded = bs58::decode(trimmed)
            .into_vec()
            .map_err(|e| AddressError::InvalidBase58(e.to_string()))?;

        if decoded.len() != DECODED_LEN {
            return Err(AddressError::InvalidLength(decoded.len()));
        }

        let payload = &decoded[..64];
        let checksum = &decoded[64..];

        // Try the hinted network first, then both networks.
        let networks: Vec<&str> = match network_hint {
            Some(n) => vec![n],
            None => vec![NETWORK_ID_MAINNET, NETWORK_ID_TESTNET],
        };

        for net in networks {
            let mut hasher = Sha3_256::new();
            hasher.update(CHECKSUM_TAG);
            hasher.update(net.as_bytes());
            hasher.update(payload);
            let digest = hasher.finalize();
            if &digest[..4] == checksum {
                let mut spend = [0u8; 32];
                let mut view = [0u8; 32];
                spend.copy_from_slice(&payload[..32]);
                view.copy_from_slice(&payload[32..]);
                // Validate Ristretto points
                use curve25519_dalek::ristretto::CompressedRistretto;
                if CompressedRistretto::from_slice(&spend).decompress().is_none() {
                    return Err(AddressError::InvalidSpendKey);
                }
                if CompressedRistretto::from_slice(&view).decompress().is_none() {
                    return Err(AddressError::InvalidViewKey);
                }
                return Ok(Self {
                    spend,
                    view,
                    network_id: net.to_string(),
                });
            }
        }
        Err(AddressError::InvalidChecksum)
    }

    /// Validate just the format and checksum of an address string.
    ///
    /// # Errors
    /// Returns an error if the input is malformed.
    pub fn validate(s: &str) -> Result<(), AddressError> {
        Self::parse(s).map(|_| ())
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use super::super::keypair::Keypair;
    use super::super::seed::Seed;
    use super::super::mnemonic::{Mnemonic, WordCount};

    fn make_wallet_keys() -> (Keypair, StealthAddress) {
        let m = Mnemonic::generate(WordCount::Words24).unwrap();
        let s = Seed::from_mnemonic(&m, "").unwrap();
        let k = Keypair::from_seed(&s).unwrap();
        let a = StealthAddress::from_keypair(&k.public_keys(), NETWORK_ID_MAINNET).unwrap();
        (k, a)
    }

    #[test]
    fn encode_decode_round_trip_mainnet() {
        let (_, addr) = make_wallet_keys();
        let encoded = addr.encode();
        let decoded = StealthAddress::parse(&encoded).unwrap();
        assert_eq!(decoded, addr);
    }

    #[test]
    fn encode_decode_round_trip_testnet() {
        let (_, addr) = make_wallet_keys();
        let addr_test = StealthAddress {
            spend: addr.spend,
            view: addr.view,
            network_id: NETWORK_ID_TESTNET.to_string(),
        };
        let encoded = addr_test.encode();
        let decoded = StealthAddress::parse(&encoded).unwrap();
        assert_eq!(decoded, addr_test);
    }

    #[test]
    fn reject_empty() {
        assert!(matches!(StealthAddress::parse(""), Err(AddressError::Empty)));
    }

    #[test]
    fn reject_too_long() {
        let long = "a".repeat(MAX_ENCODED_LEN + 1);
        assert!(matches!(StealthAddress::parse(&long), Err(AddressError::TooLong(_))));
    }

    #[test]
    fn reject_bad_base58() {
        // 0 and O are not in the base58 alphabet
        assert!(StealthAddress::parse("000000").is_err());
    }

    #[test]
    fn reject_wrong_length() {
        // Valid base58 but wrong byte count
        let bad = bs58::encode(vec![0x42u8; 30]).into_string();
        assert!(matches!(StealthAddress::parse(&bad), Err(AddressError::InvalidLength(_))));
    }

    #[test]
    fn reject_corrupted_checksum() {
        let (_, addr) = make_wallet_keys();
        let mut encoded = addr.encode().into_bytes();
        // Flip the last character
        let last = encoded.last_mut().unwrap();
        *last = if *last == b'A' { b'B' } else { b'A' };
        let corrupted = String::from_utf8(encoded).unwrap();
        assert!(StealthAddress::parse(&corrupted).is_err());
    }

    #[test]
    fn network_hint_filters_correctly() {
        let (_, addr) = make_wallet_keys();
        let addr_testnet = StealthAddress {
            spend: addr.spend,
            view: addr.view,
            network_id: NETWORK_ID_TESTNET.to_string(),
        };
        let encoded = addr_testnet.encode();
        // Mainnet hint should fail
        assert!(StealthAddress::parse_with_network(&encoded, Some(NETWORK_ID_MAINNET)).is_err());
        // Testnet hint should pass
        assert!(StealthAddress::parse_with_network(&encoded, Some(NETWORK_ID_TESTNET)).is_ok());
    }
}

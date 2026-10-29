//! BIP39 seed derivation — PBKDF2-HMAC-SHA512 of (mnemonic, passphrase) to 64 bytes.
//!
//! Per BIP39, the seed is `PBKDF2-HMAC-SHA512(mnemonic_phrase, "mnemonic" || passphrase, 2048, 64)`.

use hmac::Hmac;
use pbkdf2::pbkdf2;
use sha2::Sha512;
use zeroize::Zeroize;

use super::mnemonic::Mnemonic;

/// Errors from seed derivation.
#[derive(Debug, thiserror::Error)]
pub enum SeedError {
    #[error("internal: {0}")]
    Internal(String),
}

/// A BIP39 seed (64 bytes). Use as input to key derivation.
#[derive(Clone, Zeroize)]
pub struct Seed([u8; 64]);

impl Seed {
    /// Derive a 64-byte seed from a mnemonic and an optional BIP39 passphrase.
    ///
    /// # Errors
    /// Returns an error if PBKDF2 fails (extremely unlikely).
    pub fn from_mnemonic(mnemonic: &Mnemonic, passphrase: &str) -> Result<Self, SeedError> {
        let phrase = mnemonic.to_phrase();
        let salt = format!("mnemonic{passphrase}");
        let mut seed = [0u8; 64];
        pbkdf2::<Hmac<Sha512>>(phrase.as_bytes(), salt.as_bytes(), 2048, &mut seed)
            .map_err(|e| SeedError::Internal(e.to_string()))?;
        Ok(Self(seed))
    }

    /// Get the raw 64-byte seed.
    #[must_use]
    pub fn as_bytes(&self) -> &[u8; 64] { &self.0 }

    /// Get the first 32 bytes (commonly used as ed25519 seed).
    #[must_use]
    pub fn ed25519_seed(&self) -> [u8; 32] {
        let mut out = [0u8; 32];
        out.copy_from_slice(&self.0[..32]);
        out
    }
}

impl std::fmt::Debug for Seed {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        f.debug_tuple("Seed").field(&"<redacted>").finish()
    }
}

impl Drop for Seed {
    fn drop(&mut self) {
        self.0.zeroize();
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use super::super::mnemonic::WordCount;

    #[test]
    fn seed_is_deterministic_for_same_mnemonic() {
        let m = Mnemonic::generate(WordCount::Words12).unwrap();
        let s1 = Seed::from_mnemonic(&m, "").unwrap();
        let s2 = Seed::from_mnemonic(&m, "").unwrap();
        assert_eq!(s1.as_bytes(), s2.as_bytes());
    }

    #[test]
    fn seed_differs_with_passphrase() {
        let m = Mnemonic::generate(WordCount::Words12).unwrap();
        let s1 = Seed::from_mnemonic(&m, "").unwrap();
        let s2 = Seed::from_mnemonic(&m, "salt").unwrap();
        assert_ne!(s1.as_bytes(), s2.as_bytes());
    }

    #[test]
    fn known_bip39_test_vector() {
        // From BIP39 spec: entropy all-zeros, no passphrase.
        // 12-word mnemonic: "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about"
        let phrase = "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about";
        let m = Mnemonic::parse(phrase).unwrap();
        let s = Seed::from_mnemonic(&m, "").unwrap();
        // Known test vector: 5eb00bbddcf069084889a8ab9155568165f5c453ccb85e70811aaed6f6da5fc19a5ac40b389cd370d086206dec8aa6c430da8329a27e5e4fbfc4e5d0f
        let expected = "5eb00bbddcf069084889a8ab9155568165f5c453ccb85e70811aaed6f6da5fc19a5ac40b389cd370d086206dec8aa6c430da8329a27e5e4fbfc4e5d0f";
        assert_eq!(hex::encode(s.as_bytes()), expected);
    }

    use super::super::mnemonic::Mnemonic;
}

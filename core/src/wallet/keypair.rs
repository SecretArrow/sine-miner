//! ed25519 + curve25519 keypair — Blocknet spend + view keys.
//!
//! Blocknet stealth addresses use Curve25519 Ristretto for both spend and view
//! public keys. The view key is derived from the same seed with a different
//! HKDF info tag, so a single mnemonic produces both keys deterministically.

use curve25519_dalek::constants::RISTRETTO_BASEPOINT_TABLE;
use curve25519_dalek::ristretto::{CompressedRistretto, RistrettoPoint};
use curve25519_dalek::scalar::Scalar;
use ed25519_dalek::{SigningKey, VerifyingKey};
use hkdf::Hkdf;
use sha2::Sha512;
use zeroize::Zeroize;

use super::seed::Seed;

/// Errors from keypair operations.
#[derive(Debug, thiserror::Error)]
pub enum KeypairError {
    #[error("invalid seed length: {0}")]
    InvalidSeedLength(usize),
    #[error("HKDF expansion failed")]
    HkdfFailed,
    #[error("ed25519 key generation failed: {0}")]
    Ed25519(String),
    #[error("invalid private key bytes")]
    InvalidSecret,
}

/// Public keys for a Blocknet wallet: spend + view.
#[derive(Debug, Clone, Copy, PartialEq, Eq, serde::Serialize, serde::Deserialize)]
pub struct PublicKeys {
    /// 32-byte spend public key (Ristretto compressed)
    pub spend: [u8; 32],
    /// 32-byte view public key (Ristretto compressed)
    pub view: [u8; 32],
}

impl PublicKeys {
    /// Concatenate spend || view for address payload.
    #[must_use]
    pub fn payload(&self) -> [u8; 64] {
        let mut p = [0u8; 64];
        p[..32].copy_from_slice(&self.spend);
        p[32..].copy_from_slice(&self.view);
        p
    }
}

/// View key (private + public) — used to identify incoming transactions
/// without revealing the spend key.
#[derive(Debug, Clone, Copy, serde::Serialize, serde::Deserialize)]
pub struct ViewKey {
    /// 32-byte view secret scalar
    pub secret: [u8; 32],
    /// 32-byte view public key
    pub public: [u8; 32],
}

/// Full keypair for a Blocknet wallet.
#[derive(Debug, Clone, serde::Serialize, serde::Deserialize)]
pub struct Keypair {
    /// ed25519 signing key (32 bytes) — derives both spend and view keys
    pub seed: [u8; 32],
    /// ed25519 verifying key
    pub spend_public: [u8; 32],
    /// Curve25519 view key
    pub view: ViewKey,
}

impl Keypair {
    /// Derive a keypair from a 64-byte BIP39 seed.
    ///
    /// - spend_secret = seed[0..32] (used directly as ed25519 seed)
    /// - view_secret  = HKDF-Expand(spend_secret, info="blocknet/view/v1", 32)
    /// - spend_public = ed25519 verifying key
    /// - view_public  = Ristretto scalar * basepoint
    ///
    /// # Errors
    /// Returns an error if seed length or key derivation fails.
    pub fn from_seed(seed: &Seed) -> Result<Self, KeypairError> {
        let spend_seed = seed.ed25519_seed();

        // ed25519 signing key from 32-byte seed
        let signing = SigningKey::from_bytes(&spend_seed);
        let verifying: VerifyingKey = signing.verifying_key();
        let spend_public = verifying.to_bytes();

        // View key: HKDF-Expand(spend_seed, "blocknet/view/v1") -> 32 bytes
        let h = Hkdf::<Sha512>::new(None, &spend_seed);
        let mut view_secret = [0u8; 32];
        h.expand(b"blocknet/view/v1", &mut view_secret)
            .map_err(|_| KeypairError::HkdfFailed)?;
        // Clamp to a valid Ristretto scalar
        let view_scalar = Scalar::from_bytes_mod_order_wide(&view_secret);
        let view_public_point = view_scalar * RISTRETTO_BASEPOINT_TABLE;
        let view_public = view_public_point.compress().to_bytes();

        Ok(Self {
            seed: spend_seed,
            spend_public,
            view: ViewKey {
                secret: view_secret,
                public: view_public,
            },
        })
    }

    /// Get the public keys (spend + view) for address derivation.
    #[must_use]
    pub fn public_keys(&self) -> PublicKeys {
        PublicKeys {
            spend: self.spend_public,
            view: self.view.public,
        }
    }

    /// Sign a message with the ed25519 spend key.
    ///
    /// # Errors
    /// Returns an error if signing fails.
    pub fn sign(&self, msg: &[u8]) -> Result<[u8; 64], KeypairError> {
        let signing = SigningKey::from_bytes(&self.seed);
        let sig = signing.sign(msg);
        Ok(sig.to_bytes())
    }

    /// Verify a signature with the spend public key.
    #[must_use]
    pub fn verify(&self, msg: &[u8], sig: &[u8; 64]) -> bool {
        use ed25519_dalek::Verifier;
        let verifying = match VerifyingKey::from_bytes(&self.spend_public) {
            Ok(v) => v,
            Err(_) => return false,
        };
        let s = match ed25519_dalek::Signature::from_slice(sig) {
            Ok(s) => s,
            Err(_) => return false,
        };
        verifying.verify(msg, &s).is_ok()
    }
}

impl Drop for Keypair {
    fn drop(&mut self) {
        self.seed.zeroize();
        self.view.secret.zeroize();
    }
}

/// Decompress a Ristretto point, returning None if invalid.
#[must_use]
pub fn decompress_rijndael(bytes: &[u8; 32]) -> Option<RistrettoPoint> {
    CompressedRistretto::from_slice(bytes).decompress()
}

#[cfg(test)]
mod tests {
    use super::super::mnemonic::{Mnemonic, WordCount};
    use super::*;

    #[test]
    fn keypair_is_deterministic() {
        let m = Mnemonic::generate(WordCount::Words24).unwrap();
        let s = Seed::from_mnemonic(&m, "").unwrap();
        let k1 = Keypair::from_seed(&s).unwrap();
        let k2 = Keypair::from_seed(&s).unwrap();
        assert_eq!(k1.seed, k2.seed);
        assert_eq!(k1.spend_public, k2.spend_public);
        assert_eq!(k1.view.public, k2.view.public);
    }

    #[test]
    fn keypair_differs_per_mnemonic() {
        let m1 = Mnemonic::generate(WordCount::Words12).unwrap();
        let m2 = Mnemonic::generate(WordCount::Words12).unwrap();
        let s1 = Seed::from_mnemonic(&m1, "").unwrap();
        let s2 = Seed::from_mnemonic(&m2, "").unwrap();
        let k1 = Keypair::from_seed(&s1).unwrap();
        let k2 = Keypair::from_seed(&s2).unwrap();
        assert_ne!(k1.spend_public, k2.spend_public);
        assert_ne!(k1.view.public, k2.view.public);
    }

    #[test]
    fn sign_and_verify_round_trip() {
        let m = Mnemonic::generate(WordCount::Words12).unwrap();
        let s = Seed::from_mnemonic(&m, "").unwrap();
        let k = Keypair::from_seed(&s).unwrap();
        let msg = b"hello blocknet";
        let sig = k.sign(msg).unwrap();
        assert!(k.verify(msg, &sig));
        assert!(!k.verify(b"wrong message", &sig));
    }

    #[test]
    fn view_public_is_valid_rijndael_point() {
        let m = Mnemonic::generate(WordCount::Words12).unwrap();
        let s = Seed::from_mnemonic(&m, "").unwrap();
        let k = Keypair::from_seed(&s).unwrap();
        assert!(decompress_rijndael(&k.view.public).is_some());
    }
}

use super::seed::Seed;

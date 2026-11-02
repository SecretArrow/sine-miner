//! Encrypted keystore — AES-256-GCM with PBKDF2-derived key.
//!
//! Format:
//! ```text
//! {
//!   "v": 1,
//!   "iterations": 100000,
//!   "salt": "<base64 16 bytes>",
//!   "nonce": "<base64 12 bytes>",
//!   "ciphertext": "<base64 ciphertext>"
//! }
//! ```
//!
//! AEAD provides confidentiality + integrity; PBKDF2-HMAC-SHA256 with high
//! iterations protects against brute force on the password.

use aes_gcm::aead::Aead;
use aes_gcm::{Aes256Gcm, KeyInit, Nonce};
use base64::Engine;
use pbkdf2::pbkdf2_hmac;
use rand_core::OsRng;
use sha2::Sha256;
use zeroize::Zeroize;

/// Errors from keystore operations.
#[derive(Debug, thiserror::Error)]
pub enum KeystoreError {
    #[error("invalid keystore JSON: {0}")]
    InvalidJson(String),
    #[error("unsupported keystore version: {0}")]
    UnsupportedVersion(u32),
    #[error("invalid base64: {0}")]
    InvalidBase64(String),
    #[error("decryption failed (wrong password?)")]
    DecryptionFailed,
    #[error("encryption failed: {0}")]
    EncryptionFailed(String),
    #[error("serialize: {0}")]
    Serialize(String),
    #[error("deserialize: {0}")]
    Deserialize(String),
}

const KEYSTORE_VERSION: u32 = 1;
const SALT_LEN: usize = 16;
const NONCE_LEN: usize = 12;
const KEY_LEN: usize = 32;

/// Encrypted wallet blob.
#[derive(Debug, Clone, serde::Serialize, serde::Deserialize)]
pub struct EncryptedWallet {
    /// Format version (currently 1).
    pub v: u32,
    /// PBKDF2 iterations.
    pub iterations: u32,
    /// PBKDF2 salt (base64-encoded).
    pub salt: String,
    /// AES-GCM nonce (base64-encoded).
    pub nonce: String,
    /// AES-GCM ciphertext (base64-encoded).
    pub ciphertext: String,
}

impl EncryptedWallet {
    /// Encrypt a wallet blob with a password.
    ///
    /// # Errors
    /// Returns an error if encryption fails (extremely unlikely).
    pub fn seal(plaintext: &[u8], password: &str, iterations: u32) -> Result<Self, KeystoreError> {
        let mut salt = [0u8; SALT_LEN];
        let mut nonce_bytes = [0u8; NONCE_LEN];
        OsRng.fill_bytes(&mut salt);
        OsRng.fill_bytes(&mut nonce_bytes);

        let mut key = [0u8; KEY_LEN];
        pbkdf2_hmac::<Sha256>(password.as_bytes(), &salt, iterations, &mut key);

        let cipher = Aes256Gcm::new_from_slice(&key)
            .map_err(|e| KeystoreError::EncryptionFailed(e.to_string()))?;
        let nonce = Nonce::from_slice(&nonce_bytes);
        let ciphertext = cipher
            .encrypt(nonce, plaintext)
            .map_err(|_| KeystoreError::EncryptionFailed("aes-gcm encrypt".into()))?;

        key.zeroize();

        Ok(Self {
            v: KEYSTORE_VERSION,
            iterations,
            salt: base64::engine::general_purpose::STANDARD.encode(salt),
            nonce: base64::engine::general_purpose::STANDARD.encode(nonce_bytes),
            ciphertext: base64::engine::general_purpose::STANDARD.encode(ciphertext),
        })
    }

    /// Decrypt a wallet blob with a password.
    ///
    /// # Errors
    /// Returns `DecryptionFailed` if the password is wrong or the blob is corrupted.
    pub fn open(&self, password: &str) -> Result<Vec<u8>, KeystoreError> {
        if self.v != KEYSTORE_VERSION {
            return Err(KeystoreError::UnsupportedVersion(self.v));
        }
        let salt = base64::engine::general_purpose::STANDARD
            .decode(&self.salt)
            .map_err(|e| KeystoreError::InvalidBase64(e.to_string()))?;
        let nonce_bytes = base64::engine::general_purpose::STANDARD
            .decode(&self.nonce)
            .map_err(|e| KeystoreError::InvalidBase64(e.to_string()))?;
        let ciphertext = base64::engine::general_purpose::STANDARD
            .decode(&self.ciphertext)
            .map_err(|e| KeystoreError::InvalidBase64(e.to_string()))?;

        if salt.len() != SALT_LEN || nonce_bytes.len() != NONCE_LEN {
            return Err(KeystoreError::DecryptionFailed);
        }

        let mut key = [0u8; KEY_LEN];
        pbkdf2_hmac::<Sha256>(password.as_bytes(), &salt, self.iterations, &mut key);
        let cipher =
            Aes256Gcm::new_from_slice(&key).map_err(|_| KeystoreError::DecryptionFailed)?;
        let nonce = Nonce::from_slice(&nonce_bytes);
        let plaintext = cipher
            .decrypt(nonce, ciphertext.as_ref())
            .map_err(|_| KeystoreError::DecryptionFailed)?;
        key.zeroize();
        Ok(plaintext)
    }

    /// Serialize to JSON.
    ///
    /// # Errors
    /// Returns an error if serialization fails.
    pub fn to_json(&self) -> Result<String, KeystoreError> {
        serde_json::to_string(self).map_err(|e| KeystoreError::Serialize(e.to_string()))
    }

    /// Deserialize from JSON.
    ///
    /// # Errors
    /// Returns an error if the JSON is malformed.
    pub fn from_json(s: &str) -> Result<Self, KeystoreError> {
        serde_json::from_str(s).map_err(|e| KeystoreError::Deserialize(e.to_string()))
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn seal_and_open_round_trip() {
        let plaintext = b"secret wallet data".to_vec();
        let pwd = "correct horse battery staple";
        let enc = EncryptedWallet::seal(&plaintext, pwd, 1000).unwrap();
        let json = enc.to_json().unwrap();
        let parsed = EncryptedWallet::from_json(&json).unwrap();
        let dec = parsed.open(pwd).unwrap();
        assert_eq!(dec, plaintext);
    }

    #[test]
    fn wrong_password_fails() {
        let plaintext = b"secret wallet data".to_vec();
        let enc = EncryptedWallet::seal(&plaintext, "right", 1000).unwrap();
        let result = enc.open("wrong");
        assert!(matches!(result, Err(KeystoreError::DecryptionFailed)));
    }

    #[test]
    fn corrupted_ciphertext_fails() {
        let plaintext = b"secret wallet data".to_vec();
        let mut enc = EncryptedWallet::seal(&plaintext, "pwd", 1000).unwrap();
        // Flip a bit in the ciphertext
        let mut ct = base64::engine::general_purpose::STANDARD
            .decode(&enc.ciphertext)
            .unwrap();
        if let Some(b) = ct.last_mut() {
            *b ^= 0xFF;
        }
        enc.ciphertext = base64::engine::general_purpose::STANDARD.encode(&ct);
        assert!(enc.open("pwd").is_err());
    }

    #[test]
    fn json_round_trip() {
        let enc = EncryptedWallet::seal(b"x", "pwd", 1000).unwrap();
        let json = enc.to_json().unwrap();
        let parsed = EncryptedWallet::from_json(&json).unwrap();
        assert_eq!(parsed.iterations, enc.iterations);
    }
}

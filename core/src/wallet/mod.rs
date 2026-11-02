//! Wallet module — BIP39 mnemonic, ed25519 keypair, Blocknet stealth address.
//!
//! This is a fresh Rust implementation following public cryptographic standards:
//! - BIP39 mnemonic (English wordlist)
//! - ed25519 key derivation (matching Blocknet's curve25519-dalek usage)
//! - Blocknet stealth address format (spend + view public key + 4-byte SHA3-256 checksum,
//!   base58-encoded) as documented in seine's `address.rs`
//!
//! The implementation is original; it does not import or copy code from seine or blocknet.

pub mod address;
pub mod keypair;
pub mod keystore;
pub mod mnemonic;
pub mod seed;

pub use address::{AddressError, StealthAddress};
pub use keypair::{Keypair, KeypairError, PublicKeys, ViewKey};
pub use keystore::{EncryptedWallet, KeystoreError};
pub use mnemonic::{Mnemonic, MnemonicError, WordCount};
pub use seed::Seed;

/// Wallet configuration.
#[derive(Debug, Clone, serde::Serialize, serde::Deserialize)]
pub struct WalletConfig {
    /// Encryption iterations for the keystore PBKDF2.
    pub pbkdf2_iterations: u32,
    /// Network id ("blocknet_mainnet" or "blocknet_testnet").
    pub network_id: String,
}

impl Default for WalletConfig {
    fn default() -> Self {
        Self {
            pbkdf2_iterations: 100_000,
            network_id: "blocknet_mainnet".to_string(),
        }
    }
}

/// Top-level wallet handle. Owns a keypair and metadata.
#[derive(Debug, Clone)]
pub struct Wallet {
    pub label: String,
    pub keypair: Keypair,
    pub address: StealthAddress,
    pub config: WalletConfig,
}

/// Wallet errors.
#[derive(Debug, thiserror::Error)]
pub enum WalletError {
    #[error("mnemonic error: {0}")]
    Mnemonic(#[from] MnemonicError),
    #[error("keypair error: {0}")]
    Keypair(#[from] KeypairError),
    #[error("address error: {0}")]
    Address(#[from] AddressError),
    #[error("keystore error: {0}")]
    Keystore(#[from] KeystoreError),
    #[error("invalid password")]
    InvalidPassword,
    #[error("wallet not found")]
    NotFound,
}

impl Wallet {
    /// Create a brand-new wallet with a fresh random mnemonic of the given word count.
    ///
    /// # Errors
    /// Returns an error if random generation or key derivation fails.
    pub fn create(
        label: &str,
        word_count: WordCount,
        config: WalletConfig,
    ) -> Result<(Self, Mnemonic), WalletError> {
        let mnemonic = Mnemonic::generate(word_count)?;
        let seed = Seed::from_mnemonic(&mnemonic, "")?;
        let keypair = Keypair::from_seed(&seed)?;
        let address = StealthAddress::from_keypair(&keypair, &config.network_id)?;
        Ok((
            Self {
                label: label.to_string(),
                keypair,
                address,
                config,
            },
            mnemonic,
        ))
    }

    /// Import an existing wallet from a mnemonic phrase.
    ///
    /// # Errors
    /// Returns an error if the mnemonic is invalid or key derivation fails.
    pub fn import(
        label: &str,
        mnemonic_phrase: &str,
        passphrase: &str,
        config: WalletConfig,
    ) -> Result<Self, WalletError> {
        let mnemonic = Mnemonic::parse(mnemonic_phrase)?;
        let seed = Seed::from_mnemonic(&mnemonic, passphrase)?;
        let keypair = Keypair::from_seed(&seed)?;
        let address = StealthAddress::from_keypair(&keypair, &config.network_id)?;
        Ok(Self {
            label: label.to_string(),
            keypair,
            address,
            config,
        })
    }

    /// Encrypt this wallet with a password and return a serializable blob.
    ///
    /// # Errors
    /// Returns an error if encryption fails.
    pub fn encrypt(&self, password: &str) -> Result<EncryptedWallet, WalletError> {
        let blob = serde_json::to_vec(self).map_err(|e| KeystoreError::Serialize(e.to_string()))?;
        EncryptedWallet::seal(&blob, password, self.config.pbkdf2_iterations).map_err(Into::into)
    }

    /// Decrypt a wallet from a blob.
    ///
    /// # Errors
    /// Returns an error if the password is wrong or the blob is malformed.
    pub fn decrypt(encrypted: &EncryptedWallet, password: &str) -> Result<Self, WalletError> {
        let blob = encrypted.open(password)?;
        let wallet: Self =
            serde_json::from_slice(&blob).map_err(|e| KeystoreError::Serialize(e.to_string()))?;
        Ok(wallet)
    }

    /// Get the wallet's public stealth address as a base58-encoded string.
    #[must_use]
    pub fn address_string(&self) -> String {
        self.address.encode()
    }
}

// Manual serde implementation to avoid leaking private keys in Debug
impl serde::Serialize for Wallet {
    fn serialize<S: serde::Serializer>(&self, ser: S) -> Result<S::Ok, S::Error> {
        use serde::ser::SerializeStruct;
        let mut s = ser.serialize_struct("Wallet", 4)?;
        s.serialize_field("label", &self.label)?;
        s.serialize_field("keypair", &self.keypair)?;
        s.serialize_field("address", &self.address)?;
        s.serialize_field("config", &self.config)?;
        s.end()
    }
}

impl<'de> serde::Deserialize<'de> for Wallet {
    fn deserialize<D: serde::Deserializer<'de>>(de: D) -> Result<Self, D::Error> {
        #[derive(serde::Deserialize)]
        struct Helper {
            label: String,
            keypair: Keypair,
            address: StealthAddress,
            config: WalletConfig,
        }
        let h = Helper::deserialize(de)?;
        Ok(Self {
            label: h.label,
            keypair: h.keypair,
            address: h.address,
            config: h.config,
        })
    }
}

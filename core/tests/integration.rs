//! Integration tests for the Rust core. These run on the host (not Android),
//! using the `rlib` crate type.

use sineminer_core::mining::{MiningConfig, MiningEngine};
use sineminer_core::wallet::{
    address::{NETWORK_ID_MAINNET, NETWORK_ID_TESTNET},
    Mnemonic, StealthAddress, Wallet, WalletConfig, WordCount,
};

#[test]
fn create_and_import_wallet_round_trip() {
    let cfg = WalletConfig {
        network_id: NETWORK_ID_MAINNET.to_string(),
        ..Default::default()
    };
    let (wallet, mnemonic) = Wallet::create("test", WordCount::Words24, cfg.clone()).unwrap();
    let phrase = mnemonic.to_phrase();
    let imported = Wallet::import("imported", &phrase, "", cfg).unwrap();
    assert_eq!(wallet.address_string(), imported.address_string());
}

#[test]
fn address_validates_after_creation() {
    let cfg = WalletConfig::default();
    let (wallet, _) = Wallet::create("test", WordCount::Words24, cfg).unwrap();
    let addr = wallet.address_string();
    StealthAddress::validate(&addr).expect("address should validate");
}

#[test]
fn testnet_address_rejected_with_mainnet_hint() {
    let cfg = WalletConfig {
        network_id: NETWORK_ID_TESTNET.to_string(),
        ..Default::default()
    };
    let (wallet, _) = Wallet::create("test", WordCount::Words24, cfg).unwrap();
    let addr = wallet.address_string();
    let parsed = StealthAddress::parse_with_network(&addr, Some(NETWORK_ID_MAINNET));
    assert!(parsed.is_err());
}

#[test]
fn encrypt_decrypt_wallet_round_trip() {
    let cfg = WalletConfig::default();
    let (wallet, _) = Wallet::create("test", WordCount::Words24, cfg).unwrap();
    let encrypted = wallet.encrypt("password123").unwrap();
    let json = encrypted.to_json().unwrap();
    let parsed = sineminer_core::wallet::EncryptedWallet::from_json(&json).unwrap();
    let decrypted = Wallet::decrypt(&parsed, "password123").unwrap();
    assert_eq!(decrypted.address_string(), wallet.address_string());
}

#[test]
fn wrong_password_fails_decryption() {
    let cfg = WalletConfig::default();
    let (wallet, _) = Wallet::create("test", WordCount::Words24, cfg).unwrap();
    let encrypted = wallet.encrypt("right").unwrap();
    assert!(Wallet::decrypt(&encrypted, "wrong").is_err());
}

#[test]
fn mining_engine_creates_with_low_memory_preset() {
    let cfg = MiningConfig::low_memory();
    let engine = MiningEngine::new(cfg).unwrap();
    let _ = engine.config();
}

#[test]
fn hash_once_produces_32_bytes() {
    let cfg = MiningConfig::low_memory();
    let engine = MiningEngine::new(cfg).unwrap();
    let header = vec![0x42u8; 32];
    let result = engine.hash_once(&header, 12345).unwrap();
    assert_eq!(result.hash.len(), 32);
    assert_eq!(result.nonce, 12345);
}

#[test]
fn hash_is_deterministic_for_same_input() {
    let cfg = MiningConfig::low_memory();
    let engine = MiningEngine::new(cfg).unwrap();
    let header = vec![0x42u8; 32];
    let r1 = engine.hash_once(&header, 100).unwrap();
    let r2 = engine.hash_once(&header, 100).unwrap();
    assert_eq!(r1.hash, r2.hash);
}

#[test]
fn different_nonce_produces_different_hash() {
    let cfg = MiningConfig::low_memory();
    let engine = MiningEngine::new(cfg).unwrap();
    let header = vec![0x42u8; 32];
    let r1 = engine.hash_once(&header, 100).unwrap();
    let r2 = engine.hash_once(&header, 101).unwrap();
    assert_ne!(r1.hash, r2.hash);
}

#[test]
fn header_validation_rejects_short_input() {
    assert!(MiningEngine::validate_header_base(&[0u8; 7]).is_err());
    assert!(MiningEngine::validate_header_base(&[0u8; 8]).is_ok());
    assert!(MiningEngine::validate_header_base(&[0u8; 4096]).is_ok());
    assert!(MiningEngine::validate_header_base(&[0u8; 4097]).is_err());
}

#[test]
fn mnemonic_word_counts_produce_expected_lengths() {
    for wc in [
        WordCount::Words12,
        WordCount::Words15,
        WordCount::Words18,
        WordCount::Words21,
        WordCount::Words24,
    ] {
        let m = Mnemonic::generate(wc).unwrap();
        let words = m.to_phrase().split_whitespace().count();
        assert_eq!(words, wc as usize);
    }
}

//! BIP39 mnemonic — generation, parsing, and wordlist.
//!
//! Uses the `bip39` crate for the official English wordlist. The mnemonic
//! carries 32 bytes of entropy (24 words) or any BIP39 word count.

use anyhow::Result;
use zeroize::Zeroize;

/// BIP39 word count options.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum WordCount {
    Words12 = 12,
    Words15 = 15,
    Words18 = 18,
    Words21 = 21,
    Words24 = 24,
}

impl WordCount {
    /// Entropy bits for this word count.
    #[must_use]
    pub fn entropy_bits(&self) -> usize {
        (*self as usize) * 11 - (*self as usize) / 3
    }

    /// Entropy bytes for this word count.
    #[must_use]
    pub fn entropy_bytes(&self) -> usize {
        self.entropy_bits() / 8
    }
}

/// Errors from mnemonic operations.
#[derive(Debug, thiserror::Error)]
pub enum MnemonicError {
    #[error("invalid word count: {0}")]
    InvalidWordCount(usize),
    #[error("invalid checksum")]
    InvalidChecksum,
    #[error("unknown word in mnemonic: {0}")]
    UnknownWord(String),
    #[error("internal: {0}")]
    Internal(String),
}

/// A BIP39 mnemonic phrase. Internally wraps the `bip39` crate's `Mnemonic`.
#[derive(Clone)]
pub struct Mnemonic {
    inner: bip39::Mnemonic,
}

impl std::fmt::Debug for Mnemonic {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        f.debug_tuple("Mnemonic").field(&"<redacted>").finish()
    }
}

impl Drop for Mnemonic {
    fn drop(&mut self) {
        // best-effort zeroize — bip39::Mnemonic owns its bytes
    }
}

impl Mnemonic {
    /// Generate a new random mnemonic of the given word count.
    ///
    /// # Errors
    /// Returns an error if random generation fails.
    pub fn generate(word_count: WordCount) -> Result<Self, MnemonicError> {
        let bits = match word_count {
            WordCount::Words12 => bip39::MnemonicType::Words12,
            WordCount::Words15 => bip39::MnemonicType::Words15,
            WordCount::Words18 => bip39::MnemonicType::Words18,
            WordCount::Words21 => bip39::MnemonicType::Words21,
            WordCount::Words24 => bip39::MnemonicType::Words24,
        };
        let inner =
            bip39::Mnemonic::generate(bits).map_err(|e| MnemonicError::Internal(e.to_string()))?;
        Ok(Self { inner })
    }

    /// Parse an existing mnemonic phrase. Validates checksum.
    ///
    /// # Errors
    /// Returns an error if the phrase is malformed or the checksum doesn't match.
    pub fn parse(phrase: &str) -> Result<Self, MnemonicError> {
        let phrase = phrase
            .trim()
            .split_whitespace()
            .collect::<Vec<_>>()
            .join(" ");
        let inner = bip39::Mnemonic::parse_normalized(&phrase)
            .map_err(|e| MnemonicError::Internal(e.to_string()))?;
        Ok(Self { inner })
    }

    /// Render the mnemonic as a whitespace-separated phrase.
    ///
    /// The returned string contains the secret seed words — caller must zeroize after use.
    #[must_use]
    pub fn to_phrase(&self) -> String {
        self.inner.to_string()
    }

    /// Number of words in this mnemonic.
    #[must_use]
    pub fn word_count(&self) -> usize {
        self.inner.word_count()
    }

    /// Underlying entropy bytes.
    #[must_use]
    pub fn entropy(&self) -> Vec<u8> {
        self.inner.entropy().to_vec()
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn generate_12_words_round_trip() {
        let m = Mnemonic::generate(WordCount::Words12).unwrap();
        let phrase = m.to_phrase();
        assert_eq!(phrase.split_whitespace().count(), 12);
        let parsed = Mnemonic::parse(&phrase).unwrap();
        assert_eq!(parsed.to_phrase(), phrase);
    }

    #[test]
    fn generate_24_words_round_trip() {
        let m = Mnemonic::generate(WordCount::Words24).unwrap();
        let phrase = m.to_phrase();
        assert_eq!(phrase.split_whitespace().count(), 24);
        let parsed = Mnemonic::parse(&phrase).unwrap();
        assert_eq!(parsed.to_phrase(), phrase);
    }

    #[test]
    fn reject_invalid_phrase() {
        let bad = "this is not a valid mnemonic phrase words";
        assert!(Mnemonic::parse(bad).is_err());
    }

    #[test]
    fn entropy_bytes_correct() {
        assert_eq!(WordCount::Words12.entropy_bytes(), 16);
        assert_eq!(WordCount::Words15.entropy_bytes(), 20);
        assert_eq!(WordCount::Words18.entropy_bytes(), 24);
        assert_eq!(WordCount::Words21.entropy_bytes(), 28);
        assert_eq!(WordCount::Words24.entropy_bytes(), 32);
    }
}

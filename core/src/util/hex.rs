//! Hex encoding/decoding helpers. Thin wrappers around the `hex` crate.

pub use ::hex::{decode, encode};

/// Encode bytes as lowercase hex without panics.
#[must_use]
pub fn encode_lower(bytes: &[u8]) -> String {
    ::hex::encode(bytes)
}

/// Decode a hex string, returning an error on malformed input.
///
/// # Errors
/// Returns an error if the input is not valid hexadecimal.
pub fn decode_strict(s: &str) -> Result<Vec<u8>, String> {
    ::hex::decode(s).map_err(|e| e.to_string())
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn round_trip() {
        let bytes = vec![0u8, 1, 2, 255, 0x42];
        let hexed = encode(&bytes);
        let decoded = decode(&hexed).unwrap();
        assert_eq!(decoded, bytes);
    }

    #[test]
    fn empty() {
        assert_eq!(encode_lower(&[]), "");
        assert_eq!(decode("").unwrap(), Vec::<u8>::new());
    }

    #[test]
    fn rejects_odd_length() {
        assert!(decode_strict("abc").is_err());
    }

    #[test]
    fn rejects_invalid_chars() {
        assert!(decode_strict("xy").is_err());
    }
}

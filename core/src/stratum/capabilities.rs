//! Stratum v2 capability strings (seine-compatible subset).

pub const CAP_LOGIN_NEGOTIATION: &str = "login_negotiation";
pub const CAP_VALIDATION_STATUS: &str = "validation_status";
pub const CAP_SUBMIT_CLAIMED_HASH: &str = "submit_claimed_hash";
pub const CAP_DIFFICULTY_HINT: &str = "difficulty_hint";
pub const CAP_SAME_TEMPLATE_REBIND_V1: &str = "same_template_rebind_v1";
pub const CAP_SUBMIT_TIMING_V1: &str = "submit_timing_v1";

/// Capabilities advertised by this client.
#[derive(Debug, Clone)]
pub struct ClientCapabilities {
    pub capabilities: Vec<&'static str>,
}

impl Default for ClientCapabilities {
    fn default() -> Self {
        Self {
            capabilities: vec![
                CAP_LOGIN_NEGOTIATION,
                CAP_VALIDATION_STATUS,
                CAP_SUBMIT_CLAIMED_HASH,
                CAP_DIFFICULTY_HINT,
                CAP_SAME_TEMPLATE_REBIND_V1,
                CAP_SUBMIT_TIMING_V1,
            ],
        }
    }
}

impl ClientCapabilities {
    #[must_use]
    pub fn as_strings(&self) -> Vec<String> {
        self.capabilities.iter().map(|s| (*s).to_string()).collect()
    }
}

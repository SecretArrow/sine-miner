//! Vulkan compute backend for Argon2id hashing — experimental scaffolding.
//!
//! This module provides a future stub for GPU-accelerated Argon2id hashing
//! via Vulkan compute shaders. It is gated behind the `vulkan` feature flag
//! and the `ash` crate (the standard Rust Vulkan binding).
//!
//! ## Why Vulkan on Android?
//!
//! Android phones do not have NVIDIA GPUs, so CUDA (which seine uses for
//! desktop mining) is not an option. Vulkan compute is the only cross-vendor
//! GPU compute API available on Android:
//! - Qualcomm Adreno (Snapdragon) — Vulkan 1.1+ since 2018
//! - ARM Mali (Exynos, MediaTek) — Vulkan 1.1+ since 2019
//! - Samsung Xclipse (AMD RDNA) — Vulkan 1.2+
//!
//! ## Why this is a stub
//!
//! Argon2id is memory-hard (2 GiB per hash for production Blocknet blocks).
//! GPU memory bandwidth is the bottleneck, not compute throughput. Research
//! shows only 2-4× speedup over CPU for Argon2id on GPU, vs 100×+ for SHA256.
//! Implementation requires:
//! 1. SPIR-V compute shader implementing Argon2id's Blake2b compression
//! 2. Vulkan pipeline setup (device, queue, command buffer, descriptor sets)
//! 3. Memory-mapped buffers for the 2 GiB scratch space
//! 4. Synchronization + result readback
//!
//! This stub provides the API surface; actual implementation is a follow-up.

#![cfg(feature = "vulkan")]

use crate::mining::HashResult;

/// Errors from the Vulkan backend.
#[derive(Debug, thiserror::Error)]
pub enum VulkanError {
    #[error("Vulkan not supported on this device")]
    NotSupported,
    #[error("Vulkan backend not implemented yet — this is a future stub")]
    NotImplemented,
    #[error("No Vulkan-compatible GPU found")]
    DeviceNotFound,
    #[error("Shader compilation failed: {0}")]
    ShaderCompilationFailed(String),
    #[error("I/O error: {0}")]
    Io(String),
}

/// Vulkan compute backend for Argon2id hashing.
///
/// This is a future stub. All methods return `Err(VulkanError::NotImplemented)`
/// or `Err(VulkanError::NotSupported)`. Real implementation will:
/// 1. Enumerate physical devices via `ash::vk::PhysicalDevice`
/// 2. Select a device with compute queue support
/// 3. Load a SPIR-V shader implementing Argon2id
/// 4. Allocate a 2 GiB scratch buffer (or smaller for low-memory devices)
/// 5. Dispatch compute workgroups and read back the 32-byte hash
pub struct VulkanBackend {
    /// Placeholder for the Ash Vulkan instance (not yet initialized).
    _instance: Option<()>, // Will be `ash::Instance` once implemented
    /// Placeholder for the physical device.
    _physical_device: Option<()>, // Will be `ash::vk::PhysicalDevice`
    /// Placeholder for the compute queue family index.
    _compute_queue_family: Option<u32>,
}

impl VulkanBackend {
    /// Create a new Vulkan backend. Returns `Err(NotSupported)` until implemented.
    ///
    /// # Errors
    /// Returns `VulkanError::NotSupported` until the backend is implemented.
    pub fn new() -> Result<Self, VulkanError> {
        Err(VulkanError::NotSupported)
    }

    /// Check if Vulkan is available on this device. Returns `false` until implemented.
    #[must_use]
    pub fn is_available() -> bool {
        false
    }

    /// Hash one (header_base, nonce) pair under Argon2id using Vulkan compute.
    ///
    /// # Errors
    /// Returns `VulkanError::NotImplemented` until the backend is implemented.
    pub fn hash_once(&self, _header_base: &[u8], _nonce: u64) -> Result<HashResult, VulkanError> {
        Err(VulkanError::NotImplemented)
    }

    /// Get the name of the GPU device, if available.
    #[must_use]
    pub fn device_name(&self) -> Option<&str> {
        None
    }

    /// Get the total VRAM in bytes, if available.
    #[must_use]
    pub fn vram_bytes(&self) -> Option<u64> {
        None
    }
}

impl Drop for VulkanBackend {
    fn drop(&mut self) {
        // Future: destroy Vulkan instance, devices, queues.
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn new_returns_not_supported() {
        let result = VulkanBackend::new();
        assert!(matches!(result, Err(VulkanError::NotSupported)));
    }

    #[test]
    fn is_available_returns_false() {
        assert!(!VulkanBackend::is_available());
    }

    #[test]
    fn hash_once_returns_not_implemented() {
        // Can't construct VulkanBackend directly (no public constructor that succeeds),
        // so we test the error variant via new() then manually construct for the test.
        // Since VulkanBackend::new() always returns Err, we can't get an instance.
        // This test documents the stub behavior.
        let backend = VulkanBackend {
            _instance: None,
            _physical_device: None,
            _compute_queue_family: None,
        };
        let result = backend.hash_once(&[0u8; 32], 0);
        assert!(matches!(result, Err(VulkanError::NotImplemented)));
    }
}

package com.secretarrow.sineminer.core.native

import com.secretarrow.sineminer.BuildConfig

/**
 * JNI bridge to the Rust core (`libsineminer_core.so`).
 *
 * All functions are declared `external` here and implemented in
 * `core/src/jni_bridge.rs`. Errors from Rust are thrown as
 * `RuntimeException` by the native code.
 *
 * The native library must be loaded once via `System.loadLibrary(CORE_LIB_NAME)`
 * — this is done in [com.secretarrow.sineminer.SineMinerApplication].
 */
object NativeBridge {

    /** Name of the `.so` file (without `lib` prefix and `.so` suffix). */
    const val CORE_LIB_NAME: String = BuildConfig.CORE_LIB_NAME

    // ===== Lifecycle =====

    /** Get the Rust core version string. */
    @JvmStatic external fun coreVersion(): String

    // ===== Wallet =====

    /**
     * Create a brand-new wallet with a fresh BIP39 mnemonic.
     *
     * @param label Wallet label (user-defined).
     * @param wordCount 12, 15, 18, 21, or 24.
     * @param networkId "blocknet_mainnet" or "blocknet_testnet".
     * @return `Array<String>(2) = { mnemonicPhrase, address }`.
     * @throws RuntimeException if wallet creation fails.
     */
    @JvmStatic external fun createWallet(label: String, wordCount: Int, networkId: String): Array<String>

    /**
     * Import a wallet from an existing BIP39 mnemonic phrase.
     *
     * @param label Wallet label.
     * @param mnemonic Whitespace-separated mnemonic phrase.
     * @param passphrase BIP39 passphrase (use "" for none).
     * @param networkId Network identifier.
     * @return The stealth address string.
     * @throws RuntimeException if the mnemonic is invalid.
     */
    @JvmStatic external fun importWallet(label: String, mnemonic: String, passphrase: String, networkId: String): String

    /**
     * Validate that a string is a well-formed Blocknet address (with valid checksum).
     *
     * @return `true` if valid.
     */
    @JvmStatic external fun validateAddress(address: String): Boolean

    /**
     * Encrypt a wallet to a JSON blob (for storage).
     *
     * @return The encrypted keystore JSON.
     * @throws RuntimeException if encryption fails.
     */
    @JvmStatic external fun encryptWallet(
        label: String,
        mnemonic: String,
        passphrase: String,
        password: String,
        networkId: String,
    ): String

    // ===== Mining =====

    /**
     * Start the mining engine.
     *
     * @param memoryKib Argon2id memory cost in KiB (2097152 for production, lower for testing).
     * @param threads Number of mining threads (1 recommended on Android).
     * @param nonceSeed 64-bit nonce offset seed (random per session).
     * @return A `long` handle to the engine instance. Store this and pass to other mining calls.
     * @throws RuntimeException if engine creation fails (bad params, OOM, etc).
     */
    @JvmStatic external fun startMining(memoryKib: Long, threads: Int, nonceSeed: Long): Long

    /**
     * Stop the mining engine and free resources.
     *
     * @param handle The handle returned by [startMining]. Pass 0 to no-op.
     */
    @JvmStatic external fun stopMining(handle: Long)

    /**
     * Compute one Argon2id hash for testing or pool share submission.
     *
     * @param handle The mining engine handle.
     * @param headerBase Block header base bytes (8..4096).
     * @param nonce 64-bit nonce to mix into the hash.
     * @return 32-byte hash.
     * @throws RuntimeException if hashing fails.
     */
    @JvmStatic external fun hashOnce(handle: Long, headerBase: ByteArray, nonce: Long): ByteArray

    /**
     * Check whether a 32-byte hash meets a 32-byte target (big-endian integer comparison).
     *
     * @param hash 32 bytes.
     * @param target  bytes.
     * @return `true` if `hash <= target` as big-endian integer.
     */
    @JvmStatic external fun meetsTarget(hash: ByteArray, target: ByteArray): Boolean
}

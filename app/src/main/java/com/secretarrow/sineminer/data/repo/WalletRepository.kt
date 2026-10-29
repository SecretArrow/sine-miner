package com.secretarrow.sineminer.data.repo

import com.secretarrow.sineminer.core.native.NativeBridge
import com.secretarrow.sineminer.data.local.EntityWallet
import com.secretarrow.sineminer.data.local.dao.WalletDao
import com.secretarrow.sineminer.data.model.Wallet
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Wallet repository — wraps WalletDao and the NativeBridge.
 *
 * Mnemonic phrases are kept in memory only long enough to derive the encrypted
 * keystore blob, then dropped. They are never persisted.
 */
@Singleton
class WalletRepository @Inject constructor(
    private val walletDao: WalletDao,
) {
    /** Observe all wallets, newest first. */
    fun observeAll() = walletDao.observeAll()

    fun observePrimary() = walletDao.observePrimary()

    /**
     * Create a brand-new wallet with a fresh BIP39 mnemonic.
     *
     * @return The generated mnemonic phrase. The caller MUST show it to the user
     *         once and not store it anywhere.
     */
    suspend fun create(label: String, wordCount: Int, password: String, networkId: String): CreateResult {
        val result = NativeBridge.createWallet(label, wordCount, networkId)
        val mnemonic = result[0]
        val address = result[1]
        // Encrypt with the user's password to get the keystore blob
        val encryptedBlob = NativeBridge.encryptWallet(label, mnemonic, "", password, networkId)
        val id = walletDao.insert(
            EntityWallet(
                label = label,
                address = address,
                networkId = networkId,
                encryptedBlob = encryptedBlob,
                createdAt = System.currentTimeMillis(),
                isPrimary = walletDao.count() == 0,
            )
        )
        return CreateResult(id = id, mnemonic = mnemonic, address = address)
    }

    /**
     * Import a wallet from an existing mnemonic phrase.
     *
     * @return The wallet address.
     */
    suspend fun import(
        label: String,
        mnemonic: String,
        passphrase: String,
        password: String,
        networkId: String,
    ): ImportResult {
        val address = NativeBridge.importWallet(label, mnemonic, passphrase, networkId)
        // If already exists, no-op; otherwise insert.
        val existing = walletDao.getByAddress(address)
        if (existing != null) {
            return ImportResult(id = existing.id, address = address, alreadyExisted = true)
        }
        val encryptedBlob = NativeBridge.encryptWallet(label, mnemonic, passphrase, password, networkId)
        val id = walletDao.insert(
            EntityWallet(
                label = label,
                address = address,
                networkId = networkId,
                encryptedBlob = encryptedBlob,
                createdAt = System.currentTimeMillis(),
                isPrimary = walletDao.count() == 0,
            )
        )
        return ImportResult(id = id, address = address, alreadyExisted = false)
    }

    suspend fun delete(wallet: Wallet) {
        walletDao.delete(
            EntityWallet(
                id = wallet.id,
                label = wallet.label,
                address = wallet.address,
                networkId = wallet.networkId,
                encryptedBlob = wallet.encryptedBlob,
                createdAt = wallet.createdAt,
                isPrimary = wallet.isPrimary,
            )
        )
    }

    suspend fun setPrimary(id: Long) = walletDao.setPrimary(id)

    fun validateAddress(address: String): Boolean = NativeBridge.validateAddress(address)

    fun validateMnemonic(phrase: String): Boolean {
        // The native bridge's importWallet will throw on invalid mnemonic; we wrap.
        return try {
            NativeBridge.importWallet("__validate__", phrase, "", "blocknet_mainnet")
            true
        } catch (_: Exception) {
            false
        }
    }
}

data class CreateResult(
    val id: Long,
    val mnemonic: String,
    val address: String,
)

data class ImportResult(
    val id: Long,
    val address: String,
    val alreadyExisted: Boolean,
)

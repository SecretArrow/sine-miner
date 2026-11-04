package com.secretarrow.sineminer.data.repo

import com.secretarrow.sineminer.core.native.NativeBridge
import com.secretarrow.sineminer.data.local.EntityWallet
import com.secretarrow.sineminer.data.local.dao.WalletDao
import com.secretarrow.sineminer.data.model.Wallet
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

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
    /** Observe all wallets, newest first, mapped to domain model. */
    fun observeAll(): Flow<List<Wallet>> =
        walletDao.observeAll().map { list -> list.map { it.toModel() } }

    fun observePrimary(): Flow<Wallet?> =
        walletDao.observePrimary().map { it?.toModel() }

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

    fun validateMnemonic(phrase: String): Boolean = try {
        NativeBridge.importWallet("__validate__", phrase, "", "blocknet_mainnet")
        true
    } catch (_: Exception) {
        false
    }

    /** Map Room entity → domain model. */
    private fun EntityWallet.toModel(): Wallet = Wallet(
        id = id,
        label = label,
        address = address,
        networkId = networkId,
        encryptedBlob = encryptedBlob,
        createdAt = createdAt,
        isPrimary = isPrimary,
    )
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

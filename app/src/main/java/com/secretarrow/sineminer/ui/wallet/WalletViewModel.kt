package com.secretarrow.sineminer.ui.wallet

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.secretarrow.sineminer.data.model.Wallet
import com.secretarrow.sineminer.data.repo.WalletRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class WalletUiState(
    val wallets: List<Wallet> = emptyList(),
    val loading: Boolean = true,
    val error: String? = null,
)

sealed interface WalletAction {
    data class ShowMnemonic(val mnemonic: String, val address: String) : WalletAction
    data class Imported(val address: String, val alreadyExisted: Boolean) : WalletAction
    data class Error(val message: String) : WalletAction
}

@HiltViewModel
class WalletViewModel @Inject constructor(
    private val walletRepo: WalletRepository,
) : ViewModel() {

    private val _action = MutableStateFlow<WalletAction?>(null)
    val action: StateFlow<WalletAction?> = _action.asStateFlow()

    val uiState: StateFlow<WalletUiState> = walletRepo.observeAll()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = WalletUiState(),
        )

    fun createWallet(label: String, wordCount: Int, password: String, networkId: String) {
        viewModelScope.launch {
            try {
                val result = walletRepo.create(label, wordCount, password, networkId)
                _action.value = WalletAction.ShowMnemonic(result.mnemonic, result.address)
            } catch (e: Exception) {
                _action.value = WalletAction.Error(e.message ?: "Failed to create wallet")
            }
        }
    }

    fun importWallet(label: String, mnemonic: String, passphrase: String, password: String, networkId: String) {
        viewModelScope.launch {
            try {
                val result = walletRepo.import(label, mnemonic, passphrase, password, networkId)
                _action.value = WalletAction.Imported(result.address, result.alreadyExisted)
            } catch (e: Exception) {
                _action.value = WalletAction.Error(e.message ?: "Failed to import wallet")
            }
        }
    }

    fun deleteWallet(wallet: Wallet) {
        viewModelScope.launch { walletRepo.delete(wallet) }
    }

    fun setPrimary(wallet: Wallet) {
        viewModelScope.launch { walletRepo.setPrimary(wallet.id) }
    }

    fun consumeAction() {
        _action.value = null
    }

    fun validateAddress(address: String): Boolean = walletRepo.validateAddress(address)
    fun validateMnemonic(phrase: String): Boolean = walletRepo.validateMnemonic(phrase)
}

package com.secretarrow.sineminer.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.secretarrow.sineminer.data.model.MiningStatus
import com.secretarrow.sineminer.data.model.Wallet
import com.secretarrow.sineminer.data.repo.MiningAggregate
import com.secretarrow.sineminer.data.repo.MiningRepository
import com.secretarrow.sineminer.data.repo.WalletRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * Dashboard state. Combines mining stats and wallet info into one UiState.
 */
data class DashboardUiState(
    val loading: Boolean = true,
    val hashrateHps: Double = 0.0,
    val totalHashes: Long = 0L,
    val sharesAccepted: Int = 0,
    val sharesRejected: Int = 0,
    val blocksFound: Int = 0,
    val uptimeMs: Long = 0L,
    val isMining: Boolean = false,
    val primaryWalletAddress: String? = null,
    val hasWallet: Boolean = false,
    val poolConnected: Boolean = false,
    val poolHost: String? = null,
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val walletRepo: WalletRepository,
    private val miningRepo: MiningRepository,
) : ViewModel() {

    private val _miningStatus = MutableStateFlow(MiningStatus(
        isRunning = false, hashrateHps = 0.0, totalHashes = 0L,
        sharesAccepted = 0, sharesRejected = 0, elapsedMs = 0L,
        poolConnected = false, poolHost = null, currentJobId = null,
        currentHeight = 0L, currentDifficulty = 0.0, lastError = null,
    ))
    val miningStatus: StateFlow<MiningStatus> = _miningStatus.asStateFlow()

    // UI state — combination of persisted mining stats and the current primary wallet.
    val uiState: StateFlow<DashboardUiState> = combine(
        miningRepo.observeAggregateStats(),
        walletRepo.observePrimary(),
    ) { agg: MiningAggregate, wallet: Wallet? ->
        DashboardUiState(
            loading = false,
            totalHashes = agg.totalHashes,
            sharesAccepted = agg.sharesAccepted,
            sharesRejected = agg.sharesRejected,
            blocksFound = agg.blocksFound,
            primaryWalletAddress = wallet?.address,
            hasWallet = wallet != null,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = DashboardUiState(),
    )
}

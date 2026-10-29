package com.secretarrow.sineminer.ui.pool

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.secretarrow.sineminer.data.model.PoolConfig
import com.secretarrow.sineminer.data.repo.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PoolUiState(
    val host: String = "",
    val port: Int = 3333,
    val worker: String = "",
    val password: String = "",
    val saved: Boolean = false,
    val testing: Boolean = false,
    val connected: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class PoolViewModel @Inject constructor(
    private val settingsRepo: SettingsRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(PoolUiState())
    val uiState: StateFlow<PoolUiState> = settingsRepo.poolConfig
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = PoolUiState(),
        )

    fun updateHost(host: String) { _uiState.value = _uiState.value.copy(host = host, saved = false) }
    fun updatePort(port: Int) { _uiState.value = _uiState.value.copy(port = port, saved = false) }
    fun updateWorker(worker: String) { _uiState.value = _uiState.value.copy(worker = worker, saved = false) }
    fun updatePassword(password: String) { _uiState.value = _uiState.value.copy(password = password, saved = false) }

    fun save() {
        val s = _uiState.value
        viewModelScope.launch {
            settingsRepo.setPoolConfig(PoolConfig(host = s.host, port = s.port, worker = s.worker, password = s.password))
            _uiState.value = s.copy(saved = true)
        }
    }

    fun testConnection() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(testing = true, error = null)
            try {
                // Best-effort TCP connect test (no Stratum handshake).
                val s = _uiState.value
                val socket = java.net.Socket()
                socket.connect(java.net.InetSocketAddress(s.host, s.port), 5000)
                socket.close()
                _uiState.value = s.copy(testing = false, connected = true)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(testing = false, connected = false, error = e.message)
            }
        }
    }
}

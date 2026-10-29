package com.secretarrow.sineminer.ui.logs

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.secretarrow.sineminer.data.local.EntityLogEntry
import com.secretarrow.sineminer.data.local.dao.LogEntryDao
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LogsUiState(
    val entries: List<EntityLogEntry> = emptyList(),
    val loading: Boolean = true,
)

@HiltViewModel
class LogsViewModel @Inject constructor(
    private val logDao: LogEntryDao,
) : ViewModel() {

    val uiState: StateFlow<LogsUiState> = logDao.observeRecent()
        .map { entries: List<EntityLogEntry> -> LogsUiState(entries = entries, loading = false) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = LogsUiState(),
        )

    fun clear() {
        viewModelScope.launch { logDao.clear() }
    }
}

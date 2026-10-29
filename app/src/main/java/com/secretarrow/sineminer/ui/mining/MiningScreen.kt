package com.secretarrow.sineminer.ui.mining

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.secretarrow.sineminer.R
import com.secretarrow.sineminer.ui.components.CopyableCard
import com.secretarrow.sineminer.ui.components.SineWave
import com.secretarrow.sineminer.ui.components.StatCard

@Composable
fun MiningScreen(
    viewModel: MiningViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Header card with hashrate + animated sine wave
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.Memory,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(32.dp),
                    )
                    Spacer(modifier = Modifier.size(12.dp))
                    Column {
                        Text(stringResource(R.string.mining_title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                        Text(stringResource(R.string.mining_subtitle), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                SineWave(
                    color = MaterialTheme.colorScheme.primary,
                    amplitude = 0.4f,
                    frequency = 1.5f,
                    animate = uiState.isRunning,
                )
                Text(
                    text = formatHashrate(uiState.hashrateHps),
                    style = MaterialTheme.typography.displayMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = "Total: ${formatNumber(uiState.totalHashes)} • ${formatDuration(uiState.elapsedMs)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // Control buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (!uiState.isRunning) {
                Button(
                    onClick = { viewModel.startMining() },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                ) {
                    Icon(imageVector = Icons.Filled.PlayArrow, contentDescription = null)
                    Spacer(modifier = Modifier.size(8.dp))
                    Text(stringResource(R.string.mining_start))
                }
            } else {
                OutlinedButton(
                    onClick = { viewModel.stopMining() },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) {
                    Icon(imageVector = Icons.Filled.Stop, contentDescription = null)
                    Spacer(modifier = Modifier.size(8.dp))
                    Text(stringResource(R.string.mining_stop))
                }
            }
        }

        // Config summary
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.mining_threads), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("${uiState.threads}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.size(8.dp))
                Text(stringResource(R.string.mining_memory), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("${uiState.memoryPreset.memoryKib / 1024} MiB", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
        }

        // Job info if running
        if (uiState.isRunning && uiState.currentJobId != null) {
            CopyableCard(
                icon = Icons.Filled.Memory,
                title = stringResource(R.string.mining_job_id),
                value = uiState.currentJobId ?: "",
                onCopy = { /* TODO: clipboard */ },
            )
        }

        // Warning cards
        if (uiState.memoryPreset != com.secretarrow.sineminer.data.model.MemoryPreset.PRODUCTION) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
            ) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Filled.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                    Spacer(modifier = Modifier.size(12.dp))
                    Text(stringResource(R.string.mining_warning_low_memory), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
        ) {
            Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(imageVector = Icons.Filled.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary)
                Spacer(modifier = Modifier.size(12.dp))
                Text(stringResource(R.string.mining_warning_battery), style = MaterialTheme.typography.bodyMedium)
            }
        }

        // Stat cards
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            StatCard(
                icon = Icons.Filled.Memory,
                title = stringResource(R.string.dash_total_hashes),
                value = formatNumber(uiState.totalHashes),
                modifier = Modifier.weight(1f),
            )
            StatCard(
                icon = Icons.Filled.Pause,
                title = stringResource(R.string.dash_shares),
                value = "${uiState.sharesAccepted}/${uiState.sharesRejected}",
                modifier = Modifier.weight(1f),
            )
        }
    }
}

private fun formatHashrate(hps: Double): String = when {
    hps >= 1_000_000 -> "%.2f MH/s".format(hps / 1_000_000)
    hps >= 1_000 -> "%.2f kH/s".format(hps / 1_000)
    else -> "%.2f H/s".format(hps)
}

private fun formatNumber(n: Long): String = "%,d".format(n)

private fun formatDuration(ms: Long): String {
    val s = ms / 1000
    val h = s / 3600
    val m = (s % 3600) / 60
    val sec = s % 60
    return if (h > 0) "%dh %02dm %02ds".format(h, m, sec) else "%02d:%02d".format(m, sec)
}

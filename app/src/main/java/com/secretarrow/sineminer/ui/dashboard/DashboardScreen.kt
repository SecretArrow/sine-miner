package com.secretarrow.sineminer.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Button
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.secretarrow.sineminer.R
import com.secretarrow.sineminer.ui.components.SineWave
import com.secretarrow.sineminer.ui.components.StatCard

/**
 * Dashboard screen — shows aggregate mining stats and quick actions.
 */
@Composable
fun DashboardScreen(
    onNavigateToMining: () -> Unit,
    onNavigateToWallet: () -> Unit,
    viewModel: DashboardViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Hero card with brand + sine wave
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.Dashboard,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(32.dp),
                    )
                    Spacer(modifier = Modifier.size(12.dp))
                    Column {
                        Text(
                            text = stringResource(R.string.dash_title),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = stringResource(R.string.dash_subtitle),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                SineWave(
                    color = MaterialTheme.colorScheme.primary,
                    amplitude = 0.35f,
                    frequency = 1.2f,
                )
                // Live hashrate
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = formatHashrate(uiState.hashrateHps),
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(modifier = Modifier.size(8.dp))
                    Text(
                        text = if (uiState.isMining) "Mining" else "Idle",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }
            }
        }

        // Quick action buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Button(
                onClick = onNavigateToMining,
                modifier = Modifier.weight(1f),
            ) {
                Icon(imageVector = Icons.Filled.PlayArrow, contentDescription = null)
                Spacer(modifier = Modifier.size(8.dp))
                Text(stringResource(R.string.dash_start_mining))
            }
            OutlinedButton(
                onClick = onNavigateToWallet,
                modifier = Modifier.weight(1f),
            ) {
                Icon(imageVector = Icons.Filled.AccountBalanceWallet, contentDescription = null)
                Spacer(modifier = Modifier.size(8.dp))
                Text(if (uiState.hasWallet) stringResource(R.string.dash_open_wallet) else stringResource(R.string.dash_create_wallet))
            }
        }

        // Stats grid
        Text(
            text = stringResource(R.string.dash_quick_actions),
            style = MaterialTheme.typography.titleMedium,
        )
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(0.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.height(220.dp),
        ) {
            items(
                listOf(
                    StatItem(Icons.AutoMirrored.Filled.ShowChart, MaterialTheme.colorScheme.primary, "Total Hashes", formatNumber(uiState.totalHashes)),
                    StatItem(Icons.Filled.CheckCircle, MaterialTheme.colorScheme.tertiary, "Shares", "${uiState.sharesAccepted}/${uiState.sharesRejected}"),
                    StatItem(Icons.Filled.Bolt, MaterialTheme.colorScheme.primary, "Blocks", uiState.blocksFound.toString()),
                    StatItem(Icons.Filled.Timer, MaterialTheme.colorScheme.secondary, "Uptime", formatDuration(uiState.uptimeMs)),
                )
            ) { stat ->
                StatCard(
                    icon = stat.icon,
                    iconTint = stat.tint,
                    title = stat.title,
                    value = stat.value,
                )
            }
        }

        // Pool + wallet status
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (uiState.poolConnected) Icons.Filled.CloudQueue else Icons.Filled.CloudOff,
                        contentDescription = null,
                        tint = if (uiState.poolConnected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                    )
                    Spacer(modifier = Modifier.size(12.dp))
                    Column {
                        Text(stringResource(R.string.dash_pool_status), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(uiState.poolHost ?: "Not configured", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }
    }
}

private data class StatItem(
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val tint: Color,
    val title: String,
    val value: String,
)

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
    return if (h > 0) "%dh %02dm".format(h, m) else "%02d:%02d".format(m, sec)
}

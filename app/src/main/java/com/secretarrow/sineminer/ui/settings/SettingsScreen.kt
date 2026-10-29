package com.secretarrow.sineminer.ui.settings

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
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
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
import com.secretarrow.sineminer.BuildConfig
import com.secretarrow.sineminer.R
import com.secretarrow.sineminer.data.model.MemoryPreset
import com.secretarrow.sineminer.data.model.ThemeMode

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Header
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Settings, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.size(12.dp))
            Text(stringResource(R.string.settings_title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        }

        // Appearance
        Section(title = stringResource(R.string.settings_appearance)) {
            Text(stringResource(R.string.settings_theme), style = MaterialTheme.typography.labelLarge)
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                val modes = listOf(ThemeMode.SYSTEM to R.string.settings_theme_system, ThemeMode.LIGHT to R.string.settings_theme_light, ThemeMode.DARK to R.string.settings_theme_dark)
                modes.forEachIndexed { i, (mode, labelRes) ->
                    SegmentedButton(
                        selected = uiState.themeMode == mode,
                        onClick = { viewModel.setThemeMode(mode) },
                        shape = SegmentedButtonDefaults.itemShape(i, modes.size),
                        label = { Text(stringResource(labelRes)) },
                    )
                }
            }
            Spacer(modifier = Modifier.size(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.settings_dynamic_color), modifier = Modifier.weight(1f))
                Switch(checked = uiState.useDynamicColor, onCheckedChange = viewModel::setDynamicColor)
            }
        }

        // Mining
        Section(title = stringResource(R.string.settings_mining)) {
            Text(stringResource(R.string.settings_memory_preset), style = MaterialTheme.typography.labelLarge)
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                val presets = MemoryPreset.entries
                presets.forEachIndexed { i, preset ->
                    SegmentedButton(
                        selected = uiState.memoryPreset == preset,
                        onClick = { viewModel.setMemoryPreset(preset) },
                        shape = SegmentedButtonDefaults.itemShape(i, presets.size),
                        label = { Text(stringResource(preset.labelRes)) },
                    )
                }
            }
            Spacer(modifier = Modifier.size(12.dp))
            Text(stringResource(R.string.settings_threads), style = MaterialTheme.typography.labelLarge)
            Text("${uiState.threads}", style = MaterialTheme.typography.bodyLarge)
            Slider(
                value = uiState.threads.toFloat(),
                onValueChange = { viewModel.setThreads(it.toInt()) },
                valueRange = 1f..8f,
                steps = 6,
            )
            Spacer(modifier = Modifier.size(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.settings_auto_start), modifier = Modifier.weight(1f))
                Switch(checked = uiState.autoStartOnCharge, onCheckedChange = viewModel::setAutoStart)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.settings_stop_on_battery), modifier = Modifier.weight(1f))
                Switch(checked = uiState.stopOnLowBattery, onCheckedChange = viewModel::setStopOnLowBattery)
            }
            if (uiState.stopOnLowBattery) {
                Text("Threshold: ${uiState.batteryThresholdPercent}%", style = MaterialTheme.typography.labelLarge)
                Slider(
                    value = uiState.batteryThresholdPercent.toFloat(),
                    onValueChange = { viewModel.setBatteryThreshold(it.toInt()) },
                    valueRange = 5f..50f,
                )
            }
        }

        // Notifications
        Section(title = stringResource(R.string.settings_notifications)) {
            SwitchRow(label = stringResource(R.string.settings_notif_mining), checked = uiState.notifMining, onCheckedChange = { viewModel.setNotif(it, uiState.notifShares, uiState.notifBlocks, uiState.notifErrors) })
            SwitchRow(label = stringResource(R.string.settings_notif_shares), checked = uiState.notifShares, onCheckedChange = { viewModel.setNotif(uiState.notifMining, it, uiState.notifBlocks, uiState.notifErrors) })
            SwitchRow(label = stringResource(R.string.settings_notif_blocks), checked = uiState.notifBlocks, onCheckedChange = { viewModel.setNotif(uiState.notifMining, uiState.notifShares, it, uiState.notifErrors) })
            SwitchRow(label = stringResource(R.string.settings_notif_blocks), checked = uiState.notifErrors, onCheckedChange = { viewModel.setNotif(uiState.notifMining, uiState.notifShares, uiState.notifBlocks, it) })
        }

        // About
        Section(title = stringResource(R.string.settings_about)) {
            Row {
                Text(stringResource(R.string.settings_version), modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                Text(BuildConfig.CORE_VERSION, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            }
            Row {
                Text(stringResource(R.string.settings_repo), modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                Text("github.com/SecretArrow/sine-miner", style = MaterialTheme.typography.bodyMedium)
            }
            Row {
                Text(stringResource(R.string.settings_license), modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                Text("MIT", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Card {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            content()
        }
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

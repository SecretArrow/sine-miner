package com.secretarrow.sineminer.ui.wallet

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
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.secretarrow.sineminer.R
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateWalletScreen(
    onBack: () -> Unit,
    viewModel: WalletViewModel = hiltViewModel(),
) {
    val action by viewModel.action.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val clipboard = LocalClipboardManager.current

    var label by remember { mutableStateOf("") }
    var wordCount by remember { mutableIntStateOf(24) }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var networkId by remember { mutableStateOf("blocknet_mainnet") }
    var revealedMnemonic by remember { mutableStateOf<String?>(null) }
    var revealedAddress by remember { mutableStateOf<String?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(action) {
        when (val a = action) {
            is WalletAction.ShowMnemonic -> {
                revealedMnemonic = a.mnemonic
                revealedAddress = a.address
            }
            is WalletAction.Error -> {
                errorMessage = a.message
                scope.launch { snackbarHostState.showSnackbar(a.message) }
                viewModel.consumeAction()
            }
            else -> Unit
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.wallet_create)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Label
            OutlinedTextField(
                value = label,
                onValueChange = { label = it },
                label = { Text(stringResource(R.string.wallet_label)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            // Word count selector
            Text(stringResource(R.string.wallet_word_count), style = MaterialTheme.typography.labelLarge)
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                val options = listOf(12, 15, 18, 21, 24)
                options.forEachIndexed { i, wc ->
                    SegmentedButton(
                        selected = wordCount == wc,
                        onClick = { wordCount = wc },
                        shape = SegmentedButtonDefaults.itemShape(i, options.size),
                        label = { Text("$wc") },
                    )
                }
            }

            // Password
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text(stringResource(R.string.wallet_password)) },
                singleLine = true,
                visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { showPassword = !showPassword }) {
                        Icon(
                            imageVector = if (showPassword) Icons.Filled.Warning else Icons.Filled.Check,
                            contentDescription = "Show/hide",
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )

            // Confirm password
            OutlinedTextField(
                value = confirmPassword,
                onValueChange = { confirmPassword = it },
                label = { Text(stringResource(R.string.wallet_password_confirm)) },
                singleLine = true,
                visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
            )

            // Network selector
            Text(stringResource(R.string.wallet_network), style = MaterialTheme.typography.labelLarge)
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                val networks = listOf("blocknet_mainnet" to R.string.wallet_mainnet, "blocknet_testnet" to R.string.wallet_testnet)
                networks.forEachIndexed { i, (id, labelRes) ->
                    SegmentedButton(
                        selected = networkId == id,
                        onClick = { networkId = id },
                        shape = SegmentedButtonDefaults.itemShape(i, networks.size),
                        label = { Text(stringResource(labelRes)) },
                    )
                }
            }

            // Warning
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
            ) {
                Row(modifier = Modifier.padding(16.dp)) {
                    Icon(Icons.Filled.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary)
                    Spacer(modifier = Modifier.size(12.dp))
                    Text(stringResource(R.string.wallet_mnemonic_warning), style = MaterialTheme.typography.bodyMedium)
                }
            }

            // Submit
            Button(
                onClick = {
                    errorMessage = null
                    if (password.length < 8) {
                        errorMessage = "Password must be at least 8 characters"
                        scope.launch { snackbarHostState.showSnackbar(errorMessage!!) }
                        return@Button
                    }
                    if (password != confirmPassword) {
                        errorMessage = "Passwords do not match"
                        scope.launch { snackbarHostState.showSnackbar(errorMessage!!) }
                        return@Button
                    }
                    viewModel.createWallet(label.ifBlank { "Wallet ${System.currentTimeMillis() % 1000}" }, wordCount, password, networkId)
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.wallet_create_button))
            }

            // Show mnemonic result
            if (revealedMnemonic != null && revealedAddress != null) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.wallet_mnemonic), style = MaterialTheme.typography.labelLarge)
                        Text(revealedMnemonic!!, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                        Row {
                            IconButton(onClick = {
                                clipboard.setText(AnnotatedString(revealedMnemonic!!))
                                scope.launch { snackbarHostState.showSnackbar("Mnemonic copied") }
                            }) {
                                Icon(Icons.Filled.ContentCopy, contentDescription = "Copy mnemonic")
                            }
                        }
                        Spacer(modifier = Modifier.size(8.dp))
                        Text(stringResource(R.string.wallet_address), style = MaterialTheme.typography.labelLarge)
                        Text(revealedAddress!!, style = MaterialTheme.typography.bodyMedium)
                        Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
                            Text("Done")
                        }
                    }
                }
            }

            errorMessage?.let {
                Text(it, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

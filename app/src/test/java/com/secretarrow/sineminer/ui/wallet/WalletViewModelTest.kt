package com.secretarrow.sineminer.ui.wallet

import com.secretarrow.sineminer.data.model.Wallet
import com.secretarrow.sineminer.data.repo.WalletRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class WalletViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `ui state initial value is empty wallets`() = runTest {
        val repo = mock<WalletRepository> {
            on { observeAll() } doReturnFlow flowOf(emptyList())
        }
        val vm = WalletViewModel(repo)
        assertEquals(0, vm.uiState.value.wallets.size)
        assertEquals(true, vm.uiState.value.loading)
    }

    @Test
    fun `ui state emits wallets`() = runTest {
        val wallet = Wallet(
            id = 1, label = "Test", address = "abc", networkId = "blocknet_mainnet",
            encryptedBlob = "{}", createdAt = 0L, isPrimary = true,
        )
        val repo = mock<WalletRepository> {
            on { observeAll() } doReturnFlow flowOf(listOf(wallet))
        }
        val vm = WalletViewModel(repo)
        advanceUntilIdle()
        assertEquals(1, vm.uiState.value.wallets.size)
        assertEquals("Test", vm.uiState.value.wallets[0].label)
    }

    @Test
    fun `action starts as null`() = runTest {
        val repo = mock<WalletRepository> {
            on { observeAll() } doReturnFlow flowOf(emptyList())
        }
        val vm = WalletViewModel(repo)
        assertNull(vm.action.value)
    }
}

private infix fun <T> T.doReturnFlow(value: kotlinx.coroutines.flow.Flow<*>) =
    org.mockito.kotlin.doReturn(value) as Any

// Make sure mockito-kotlin has `onBlocking` for suspend fns
private fun <T> org.mockito.kotlin.KStubbing<T>.coAnswers(stub: suspend () -> Unit) {
    org.mockito.kotlin.coJustAnswer { stub() }
}

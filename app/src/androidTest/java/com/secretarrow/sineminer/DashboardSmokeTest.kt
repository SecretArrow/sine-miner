package com.secretarrow.sineminer

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Smoke test that the main activity launches and shows the dashboard.
 */
@RunWith(AndroidJUnit4::class)
class DashboardSmokeTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun `dashboard shows hashrate hero card`() {
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Dashboard").assertExists()
    }

    @Test
    fun `bottom nav can switch to wallet tab`() {
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Wallet").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Blocknet stealth wallet").assertExists()
    }
}

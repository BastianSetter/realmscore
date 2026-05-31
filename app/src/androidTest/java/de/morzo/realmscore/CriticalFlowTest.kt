package de.morzo.realmscore

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import de.morzo.realmscore.ui.MainActivity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CriticalFlowTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private val ctx = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
    private val container by lazy { (ctx as FantasyRealmsApp).container }

    @Before
    fun resetAppState() = runBlocking {
        container.resetUseCase.resetApp()
    }

    @After
    fun cleanupAppState() = runBlocking {
        container.resetUseCase.resetApp()
    }

    @Test
    fun onboarding_is_displayed_on_first_launch() {
        composeRule.waitForIdle()
        composeRule.onNodeWithText(getString(R.string.onboarding_headline))
            .assertIsDisplayed()
    }

    @Test
    fun complete_onboarding_lands_on_home() {
        composeRule.waitForIdle()
        composeRule.onNodeWithText(getString(R.string.onboarding_name_label))
            .performTextInput("TestUser")
        composeRule.onNodeWithText(getString(R.string.onboarding_continue))
            .performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText(getString(R.string.tab_home))
            .assertIsDisplayed()
    }

    @Test
    fun settings_tab_shows_all_sections() {
        composeRule.waitForIdle()
        composeRule.onNodeWithText(getString(R.string.onboarding_name_label))
            .performTextInput("TestUser")
        composeRule.onNodeWithText(getString(R.string.onboarding_continue))
            .performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText(getString(R.string.tab_settings))
            .performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText(getString(R.string.settings_profile))
            .assertIsDisplayed()
        composeRule.onNodeWithText(getString(R.string.settings_appearance))
            .assertIsDisplayed()
        composeRule.onNodeWithText(getString(R.string.settings_about))
            .assertIsDisplayed()
    }

    @Test
    fun username_change_screen_opens_from_settings() {
        composeRule.waitForIdle()
        composeRule.onNodeWithText(getString(R.string.onboarding_name_label))
            .performTextInput("TestUser")
        composeRule.onNodeWithText(getString(R.string.onboarding_continue))
            .performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText(getString(R.string.tab_settings))
            .performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText(getString(R.string.settings_change_username))
            .performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText(getString(R.string.username_change_label))
            .assertIsDisplayed()
    }

    private fun getString(id: Int): String = ctx.getString(id)
}

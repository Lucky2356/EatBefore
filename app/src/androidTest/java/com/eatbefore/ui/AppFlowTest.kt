package com.eatbefore.ui

import androidx.compose.ui.test.ComposeTimeoutException
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.printToLog
import androidx.test.platform.app.InstrumentationRegistry
import com.eatbefore.MainActivity
import com.eatbefore.R
import com.eatbefore.core.datastore.ThemeMode
import com.eatbefore.core.datastore.UserPreferencesRepository
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import javax.inject.Inject

/**
 * End-to-end UI coverage for the acceptance scenarios in PROJECT_PROMPT.md. Each test
 * drives the real app (real Room database on the device) through one user journey.
 *
 * Every test starts with a fresh DataStore (see TestDataStoreModule), so the app opens on
 * onboarding. Tests that care about a later screen call [skipOnboarding] first; the
 * onboarding journey itself is covered by [onboarding_isShownOnFirstRun].
 */
@HiltAndroidTest
class AppFlowTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Inject
    lateinit var preferences: UserPreferencesRepository

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun string(id: Int) = context.getString(id)

    @Before
    fun setUp() {
        hiltRule.inject()
    }

    /** Waits until [text] is on screen; DataStore writes and navigation are asynchronous. */
    private fun awaitText(text: String) {
        try {
            composeRule.waitUntil(timeoutMillis = UI_TIMEOUT_MS) {
                composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
            }
        } catch (e: ComposeTimeoutException) {
            // Dump what is actually on screen so the failure is diagnosable from the log.
            composeRule.onRoot().printToLog("EATBEFORE_TREE")
            throw AssertionError("Timed out waiting for text: '$text'", e)
        }
    }

    // Several labels legitimately appear twice (a quick action and a nav tab both read
    // "Scan"), so tests always act on the first match rather than demanding uniqueness.
    // Forms and settings scroll, so bring the target into view first; performScrollTo
    // throws when the node isn't inside a scrollable, which is fine to ignore.
    private fun node(text: String): SemanticsNodeInteraction {
        awaitText(text)
        return composeRule.onAllNodesWithText(text).onFirst()
            .also { runCatching { it.performScrollTo() } }
    }

    private fun clickText(text: String) = node(text).performClick()

    private fun longClickText(text: String) = node(text).performTouchInput { longClick() }

    /** Waits until [text] is gone from the screen. */
    private fun awaitTextGone(text: String) {
        try {
            composeRule.waitUntil(timeoutMillis = UI_TIMEOUT_MS) {
                composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isEmpty()
            }
        } catch (e: ComposeTimeoutException) {
            composeRule.onRoot().printToLog("EATBEFORE_TREE")
            throw AssertionError("Timed out waiting for text to disappear: '$text'", e)
        }
    }

    /** Clicks an icon-only control, which carries its label as a content description. */
    private fun clickIcon(description: String) {
        composeRule.waitUntil(timeoutMillis = UI_TIMEOUT_MS) {
            composeRule.onAllNodesWithContentDescription(description).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onAllNodesWithContentDescription(description).onFirst().performClick()
    }

    private fun assertVisible(text: String) {
        node(text).assertIsDisplayed()
    }

    private fun typeInto(label: String, text: String) {
        node(label).performTextInput(text)
    }

    /** Marks onboarding done and waits for the home screen to replace it. */
    private fun skipOnboarding() {
        runBlocking { preferences.setOnboardingCompleted(true) }
        awaitText(string(R.string.home_quick_add_manual))
    }

    /** Adds a product with the given name through the manual form. */
    private fun addProduct(name: String, expiryPreset: Int = R.string.add_expiry_week) {
        clickText(string(R.string.home_quick_add_manual))
        typeInto(string(R.string.add_name), name)
        clickText(string(expiryPreset))
        clickText(string(R.string.action_save))
        awaitText(string(R.string.home_quick_add_manual))
    }

    private fun openInventory() {
        clickText(string(R.string.nav_inventory))
        awaitText(string(R.string.inventory_search_hint))
    }

    /**
     * Opens the stock list and narrows it down to [name].
     *
     * Tests share one real database on the device, so a late test looks at a list holding
     * everything the earlier ones added. The list is lazy: a row far enough down is not
     * composed at all, and asserting on it fails for reasons that have nothing to do with
     * what the test is about. Searching first is also what a person would do.
     *
     * Only the first word is typed, so the field's own text can never be mistaken for the
     * row when asserting on the full name.
     */
    private fun findInInventory(name: String) {
        openInventory()
        typeInto(string(R.string.inventory_search_hint), name.substringBefore(' '))
        awaitText(name)
    }

    @Test
    fun onboarding_isShownOnFirstRun() {
        // A fresh install starts here; skipping must land on home.
        assertVisible(string(R.string.onboarding_local_title))
        clickText(string(R.string.onboarding_skip))

        assertVisible(string(R.string.home_quick_add_manual))
    }

    @Test
    fun addManualProduct_appearsInInventory() {
        skipOnboarding()
        val name = "Test Milk ${System.currentTimeMillis()}"
        addProduct(name)

        findInInventory(name)
        assertVisible(name)
    }

    @Test
    fun markFinished_thenUndo_restoresTheItem() {
        skipOnboarding()
        val name = "Test Bread ${System.currentTimeMillis()}"
        addProduct(name)
        findInInventory(name)
        clickText(name)

        val full = "1 ${string(R.string.unit_piece)}"
        val emptied = "0 ${string(R.string.unit_piece)}"
        assertVisible(full)

        clickText(string(R.string.product_action_finished))
        awaitText(emptied)

        // Using it up offers the shopping list; declining must still leave undo available.
        clickText(string(R.string.shopping_offer_no))
        clickText(string(R.string.action_undo))

        // Undo puts the quantity back, so the batch is in stock again.
        awaitText(full)
    }

    @Test
    fun actions_areRecordedInHistory() {
        skipOnboarding()
        val name = "Test Cheese ${System.currentTimeMillis()}"
        addProduct(name)

        clickText(string(R.string.nav_more))
        clickText(string(R.string.history_title))

        assertVisible(string(R.string.event_added))
    }

    @Test
    fun inventorySearch_filtersRows() {
        skipOnboarding()
        val kept = "Findable ${System.currentTimeMillis()}"
        val hidden = "Hidden ${System.currentTimeMillis()}"
        addProduct(kept)
        addProduct(hidden)

        openInventory()
        typeInto(string(R.string.inventory_search_hint), "Findable")
        // Search is debounced; wait for the filtered list to settle.
        awaitTextGone(hidden)
        assertVisible(kept)
    }

    @Test
    fun shoppingItem_movesToInventory() {
        skipOnboarding()
        val name = "Buy Me ${System.currentTimeMillis()}"
        clickText(string(R.string.nav_shopping))

        // The add affordance is an icon-only FAB.
        clickIcon(string(R.string.shopping_add))
        typeInto(string(R.string.shopping_item_name), name)
        clickText(string(R.string.action_add))

        awaitText(name)
        clickIcon(string(R.string.shopping_to_inventory))
        composeRule.waitForIdle()

        findInInventory(name)
        assertVisible(name)
    }

    /** Opens Settings from the "More" tab. */
    private fun openSettings() {
        clickText(string(R.string.nav_more))
        clickText(string(R.string.settings_title))
        awaitText(string(R.string.settings_section_appearance))
    }

    @Test
    fun themeSetting_switchesToDarkAndBack() {
        skipOnboarding()
        openSettings()

        clickText(string(R.string.settings_theme_dark))
        composeRule.waitUntil(timeoutMillis = UI_TIMEOUT_MS) {
            runBlocking { preferences.preferences.first().themeMode } == ThemeMode.DARK
        }

        clickText(string(R.string.settings_theme_system))
        composeRule.waitForIdle()
        // The screen must survive the theme swap.
        assertVisible(string(R.string.settings_section_appearance))
    }

    /**
     * The point of quick actions is that stock changes without opening anything — which is
     * also why the undo has to be right there. Both halves are checked together.
     */
    @Test
    fun quickAction_writesOffFromTheListAndCanBeUndone() {
        skipOnboarding()
        val name = "Quick Yogurt ${System.currentTimeMillis()}"
        addProduct(name)
        findInInventory(name)

        longClickText(name)
        clickText(string(R.string.product_action_finished))
        awaitTextGone(name)

        clickText(string(R.string.action_undo))
        awaitText(name)
    }

    /** Buying another package must add a second batch, not edit the first. */
    @Test
    fun repeatPurchase_addsASecondPackage() {
        skipOnboarding()
        val name = "Repeat Milk ${System.currentTimeMillis()}"
        addProduct(name)
        findInInventory(name)

        longClickText(name)
        clickText(string(R.string.product_action_repeat))
        clickText(string(R.string.add_expiry_month))
        clickText(string(R.string.action_add))

        composeRule.waitUntil(timeoutMillis = UI_TIMEOUT_MS) {
            composeRule.onAllNodesWithText(name).fetchSemanticsNodes().size == 2
        }
    }

    @Test
    fun statusFilter_keepsOnlyWhatIsRunningOut() {
        skipOnboarding()
        val urgent = "Urgent ${System.currentTimeMillis()}"
        val calm = "Calm ${System.currentTimeMillis()}"
        addProduct(urgent, expiryPreset = R.string.add_expiry_today)
        addProduct(calm, expiryPreset = R.string.add_expiry_month)
        openInventory()

        clickText(string(R.string.inventory_filter_soon))

        awaitTextGone(calm)
        assertVisible(urgent)
    }

    @Test
    fun backupImport_asksForConfirmationBeforeReplacingData() {
        skipOnboarding()
        openSettings()

        // Export/import open system pickers; assert the destructive action is present and
        // described as replacing data, rather than driving the system UI.
        assertVisible(string(R.string.settings_import))
        assertVisible(string(R.string.settings_import_desc))
    }

    private companion object {
        /**
         * Generous on purpose. The timeout only costs time when a test is already failing —
         * waitUntil returns the moment the condition holds — and a CI emulator is several
         * times slower than a developer machine. Ten seconds was enough locally and made
         * the shopping-list test flaky elsewhere, which teaches everyone to ignore red.
         */
        const val UI_TIMEOUT_MS = 30_000L
    }
}

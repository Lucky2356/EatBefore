package com.eatbefore.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test

/**
 * Records the classes and methods used on the paths a user hits first, so ART can compile
 * them ahead of time instead of interpreting them on the first run after install.
 *
 * Run against a rooted (google_apis, not google_apis_playstore) emulator:
 *   ./gradlew :baselineprofile:generateBaselineProfile
 *
 * The result lands in app/src/main/baseline-prof.txt and is committed. See
 * docs/BASELINE_PROFILE.md.
 */
class BaselineProfileGenerator {

    @get:Rule
    val rule = BaselineProfileRule()

    /**
     * Startup only. Marked as a startup profile so R8 also groups these classes together
     * in the dex, which cuts page faults on the first launch.
     */
    @Test
    fun startup() = rule.collect(
        packageName = PACKAGE_NAME,
        includeInStartupProfile = true,
    ) {
        pressHome()
        startActivityAndWait()
        skipOnboardingIfPresent()
    }

    /** Everything a user touches in the first session, past the launch itself. */
    @Test
    fun journey() = rule.collect(packageName = PACKAGE_NAME) {
        pressHome()
        startActivityAndWait()

        // Onboarding shows on a fresh install, which is exactly the first-run path worth
        // compiling. It is skipped silently when the profile is regenerated over an
        // existing install.
        skipOnboardingIfPresent()

        scrollCurrentScreen()

        // The tabs a user reaches in the first session. The scanner is deliberately left
        // out: it needs the camera permission, and a permission dialog would stall the run.
        listOf(NAV_INVENTORY, NAV_SHOPPING, NAV_MORE, NAV_HOME).forEach { tag ->
            navigateTo(tag)
            scrollCurrentScreen()
        }
    }

    private fun androidx.benchmark.macro.MacrobenchmarkScope.skipOnboardingIfPresent() {
        // Onboarding has no bottom bar; its absence is the signal that we are still on it.
        if (device.wait(Until.hasObject(By.res(NAV_HOME)), UI_TIMEOUT_MS)) return

        repeat(MAX_ONBOARDING_PAGES) {
            val next = device.findObject(By.clazz("android.widget.Button")) ?: return@repeat
            next.click()
            device.waitForIdle()
        }
        device.wait(Until.hasObject(By.res(NAV_HOME)), UI_TIMEOUT_MS)
    }

    private fun androidx.benchmark.macro.MacrobenchmarkScope.navigateTo(tag: String) {
        val target = device.wait(Until.findObject(By.res(tag)), UI_TIMEOUT_MS) ?: return
        target.click()
        device.waitForIdle()
    }

    /** Scrolling is what pulls lazy-list and item composables into the profile. */
    private fun androidx.benchmark.macro.MacrobenchmarkScope.scrollCurrentScreen() {
        val scrollable = device.findObject(By.scrollable(true)) ?: return
        scrollable.setGestureMargin(device.displayWidth / GESTURE_MARGIN_FRACTION)
        scrollable.fling(Direction.DOWN)
        device.waitForIdle()
        scrollable.fling(Direction.UP)
        device.waitForIdle()
    }

    private companion object {
        const val PACKAGE_NAME = "com.eatbefore"
        const val NAV_HOME = "nav_home"
        const val NAV_INVENTORY = "nav_inventory"
        const val NAV_SHOPPING = "nav_shopping"
        const val NAV_MORE = "nav_more"
        const val UI_TIMEOUT_MS = 5_000L
        const val MAX_ONBOARDING_PAGES = 5
        const val GESTURE_MARGIN_FRACTION = 5
    }
}

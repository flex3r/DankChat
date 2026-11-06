package com.flxrs.dankchat.baselineprofile

import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.StaleObjectException
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import java.util.regex.Pattern

internal const val CHANNEL = "flex3rs"

internal val channelPattern: Pattern = Pattern.compile(CHANNEL, Pattern.CASE_INSENSITIVE)

internal fun UiDevice.completeOnboardingIfShown() {
    clickAndSettle(By.text("Get Started"), timeout = 3_000)
    // The app can resume on any onboarding page, click through whatever page shows: login and
    // notifications offer Skip, message history offers Enable
    repeat(3) {
        if (!clickAndSettle(By.text("Skip"), timeout = 2_000)) {
            clickAndSettle(By.text("Enable"), timeout = 500)
        }
    }
}

// System permission dialogs follow the system locale, the deny button is matched by resource
// id to stay locale-independent. The permission controller package differs between AOSP and
// Google builds, so only the id is matched. Repeat requests after a denial use the
// don't-ask-again variant of the deny button.
private val permissionDenyButton = Pattern.compile(".*:id/permission_deny(_and_dont_ask_again)?_button")

internal fun UiDevice.dismissPermissionDialogIfShown() {
    clickAndSettle(By.res(permissionDenyButton), timeout = 2_000)
}

internal fun UiDevice.joinChannelIfMissing() {
    dismissPermissionDialogIfShown()
    clickAndSettle(By.text("Skip tour"), timeout = 3_000)
    if (hasObject(By.text(channelPattern))) {
        return
    }
    clickAndSettle(By.desc("Add channel"), timeout = 10_000)
    enterText(CHANNEL)
    clickAndSettle(By.text("OK"))
    clickAndSettle(By.text("Skip tour"), timeout = 3_000)
}

internal fun UiDevice.awaitChatContent() {
    dismissPermissionDialogIfShown()
    wait(Until.hasObject(By.text(channelPattern)), 10_000)
    Thread.sleep(8_000)
}

// The UI can change between finding an object and clicking it, retry once with a fresh object
internal fun UiDevice.clickAndSettle(
    selector: BySelector,
    timeout: Long = 5_000,
): Boolean {
    repeat(2) {
        val obj = wait(Until.findObject(selector), timeout) ?: return false
        try {
            obj.click()
            waitForIdle()
            return true
        } catch (_: StaleObjectException) {
        }
    }
    return false
}

private fun UiDevice.enterText(text: String) {
    repeat(2) {
        val input = wait(Until.findObject(By.clazz("android.widget.EditText")), 5_000) ?: return
        try {
            input.text = text
            return
        } catch (_: StaleObjectException) {
        }
    }
}

internal fun UiDevice.scrollChat() {
    val centerX = displayWidth / 2
    val upperY = (displayHeight * 0.3).toInt()
    val lowerY = (displayHeight * 0.7).toInt()
    repeat(3) {
        swipe(centerX, upperY, centerX, lowerY, 25)
        waitForIdle()
        Thread.sleep(500)
    }
    repeat(3) {
        swipe(centerX, lowerY, centerX, upperY, 25)
        waitForIdle()
        Thread.sleep(500)
    }
}

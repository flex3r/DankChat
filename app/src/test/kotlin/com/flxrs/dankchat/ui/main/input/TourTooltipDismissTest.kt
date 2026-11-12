package com.flxrs.dankchat.ui.main.input

import android.app.Application
import android.os.SystemClock
import android.view.MotionEvent
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipState
import androidx.compose.ui.platform.ViewRootForTest
import androidx.compose.ui.test.isPopup
import androidx.compose.ui.test.junit4.v2.createComposeRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

@OptIn(ExperimentalMaterial3Api::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class TourTooltipDismissTest {
    @get:Rule
    val composeRule = createComposeRule()

    private var skipCount = 0

    @Test
    fun `tap outside a focusable tour tooltip skips the tour`() {
        setContent(focusable = true)

        tapOutsideTooltip()

        assertEquals(expected = 1, actual = skipCount)
    }

    @Test
    fun `tap outside a tour tooltip that lets touches through keeps the tour`() {
        setContent(focusable = false)

        tapOutsideTooltip()

        assertEquals(expected = 0, actual = skipCount)
    }

    private fun setContent(focusable: Boolean) {
        composeRule.setContent {
            OptionalTourTooltip(
                tooltipState = TooltipState(initialIsVisible = true, isPersistent = true),
                text = "Tour step",
                onAdvance = {},
                onSkip = { skipCount++ },
                focusable = focusable,
            ) {
                Text("Anchor")
            }
        }
        composeRule.waitForIdle()
    }

    private fun tapOutsideTooltip() {
        val root = composeRule.onNode(isPopup()).fetchSemanticsNode().root as ViewRootForTest
        val popup = root.view.rootView
        composeRule.runOnUiThread {
            val time = SystemClock.uptimeMillis()
            val event = MotionEvent.obtain(time, time, MotionEvent.ACTION_DOWN, OUTSIDE, OUTSIDE, 0)
            popup.dispatchTouchEvent(event)
            event.recycle()
        }
        composeRule.waitForIdle()
    }

    private companion object {
        const val OUTSIDE = -10f
    }
}

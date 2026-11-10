package com.flxrs.dankchat.ui.chat.messages.common

import android.app.Application
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.text.intl.LocaleList
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TextHitTestingTest {
    private val measurer =
        TextMeasurer(
            defaultFontFamilyResolver = createFontFamilyResolver(RuntimeEnvironment.getApplication()),
            defaultLocaleList = LocaleList("en-US"),
            defaultDensity = Density(density = 2f),
            defaultLayoutDirection = LayoutDirection.Ltr,
        )

    private fun TextLayoutResult.positionIn(
        index: Int,
        fraction: Float,
    ): Offset {
        val box = getBoundingBox(index)
        return Offset(box.left + box.width * fraction, box.center.y)
    }

    @Test
    fun `near half of a character resolves to that character`() {
        val layout = measurer.measure("Allow  Deny")

        assertEquals(4, layout.getCharacterOffsetForPosition(layout.positionIn(index = 4, fraction = 0.25f)))
    }

    @Test
    fun `far half of a character resolves to that character instead of the next one`() {
        val layout = measurer.measure("Allow  Deny")
        val position = layout.positionIn(index = 4, fraction = 0.75f)

        assertEquals(5, layout.getOffsetForPosition(position))
        assertEquals(4, layout.getCharacterOffsetForPosition(position))
    }

    @Test
    fun `far half of the last character resolves to that character`() {
        val layout = measurer.measure("Allow  Deny")

        assertEquals(10, layout.getCharacterOffsetForPosition(layout.positionIn(index = 10, fraction = 0.75f)))
    }

    @Test
    fun `first character of a wrapped line does not resolve to the previous line`() {
        val unwrapped = measurer.measure("Allow Deny")
        val wrapWidth = unwrapped.getBoundingBox(6).left.toInt()
        val layout = measurer.measure("Allow Deny", constraints = Constraints(maxWidth = wrapWidth))

        assertEquals(2, layout.lineCount)
        assertEquals(6, layout.getCharacterOffsetForPosition(layout.positionIn(index = 6, fraction = 0.25f)))
    }
}

package com.flxrs.dankchat.preferences.appearance

import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import com.flxrs.dankchat.R
import kotlinx.serialization.Serializable

@Serializable
enum class InputAction {
    Search,
    LastMessage,
    Stream,
    ModActions,
    Fullscreen,
    Theater,
    HideInput,
    Debug,
}

@Serializable
enum class FabAnchor { BottomEnd, TopEnd, Free }

@Serializable
data class AppearanceSettings(
    val theme: ThemePreference = ThemePreference.System,
    val trueDarkTheme: Boolean = false,
    val accentColor: AccentColor? = null,
    val paletteStyle: PaletteStylePreference = PaletteStylePreference.SystemDefault,
    val fontSize: Int = 14,
    val keepScreenOn: Boolean = true,
    val lineSeparator: Boolean = false,
    val checkeredMessages: Boolean = false,
    val showInput: Boolean = true,
    val autoDisableInput: Boolean = true,
    val showChips: Boolean = true,
    val showChangelogs: Boolean = true,
    val showCharacterCounter: Boolean = false,
    val showSendWaitTimer: Boolean = false,
    val showClearInputButton: Boolean = true,
    val showSendButton: Boolean = true,
    val swipeNavigation: Boolean = true,
    val compactChannelInfo: Boolean = false,
    val fullscreenButtonOpacity: Float = 0.75f,
    val requireFullscreenExitConfirmation: Boolean = false,
    val fabAnchor: FabAnchor = FabAnchor.BottomEnd,
    val fabOffsetXFraction: Float = 0f,
    val fabOffsetYFraction: Float = 0f,
    val inputActions: List<InputAction> =
        listOf(
            InputAction.Stream,
            InputAction.ModActions,
            InputAction.Search,
            InputAction.LastMessage,
        ),
)

enum class ThemePreference { System, Dark, Light }

@Immutable
@Serializable
enum class PaletteStylePreference(
    @get:StringRes val labelRes: Int,
    @get:StringRes val descriptionRes: Int,
    val isStandard: Boolean = true,
) {
    SystemDefault(R.string.palette_style_system_default, R.string.palette_style_system_default_desc),
    TonalSpot(R.string.palette_style_tonal_spot, R.string.palette_style_tonal_spot_desc),
    Neutral(R.string.palette_style_neutral, R.string.palette_style_neutral_desc),
    Vibrant(R.string.palette_style_vibrant, R.string.palette_style_vibrant_desc),
    Expressive(R.string.palette_style_expressive, R.string.palette_style_expressive_desc),
    Rainbow(R.string.palette_style_rainbow, R.string.palette_style_rainbow_desc, isStandard = false),
    FruitSalad(R.string.palette_style_fruit_salad, R.string.palette_style_fruit_salad_desc, isStandard = false),
    Monochrome(R.string.palette_style_monochrome, R.string.palette_style_monochrome_desc, isStandard = false),
    Fidelity(R.string.palette_style_fidelity, R.string.palette_style_fidelity_desc, isStandard = false),
    Content(R.string.palette_style_content, R.string.palette_style_content_desc, isStandard = false),
}

@Immutable
@Serializable
enum class AccentColor(
    val seedColor: Color,
    @get:StringRes val labelRes: Int,
) {
    Blue(Color(0xFF1B6EF3), R.string.accent_color_blue),
    Teal(Color(0xFF00796B), R.string.accent_color_teal),
    Green(Color(0xFF2E7D32), R.string.accent_color_green),
    Lime(Color(0xFF689F38), R.string.accent_color_lime),
    Yellow(Color(0xFFF9A825), R.string.accent_color_yellow),
    Orange(Color(0xFFEF6C00), R.string.accent_color_orange),
    Red(Color(0xFFC62828), R.string.accent_color_red),
    Pink(Color(0xFFAD1457), R.string.accent_color_pink),
    Purple(Color(0xFF6A1B9A), R.string.accent_color_purple),
    Indigo(Color(0xFF283593), R.string.accent_color_indigo),
    Brown(Color(0xFF4E342E), R.string.accent_color_brown),
    Grey(Color(0xFF546E7A), R.string.accent_color_grey),
}

package com.flxrs.dankchat.ui.chat.messages.common

import android.content.Context
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.constrainWidth
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import com.flxrs.dankchat.ui.chat.BadgeUi
import com.flxrs.dankchat.ui.chat.EmoteUi
import com.flxrs.dankchat.ui.chat.emote.EmoteSheetData
import com.flxrs.dankchat.ui.chat.emote.LocalEmoteAnimationCoordinator
import com.flxrs.dankchat.ui.chat.emote.StackedEmote
import com.flxrs.dankchat.ui.chat.emote.emoteBaseHeight
import com.flxrs.dankchat.ui.chat.emote.singleEmoteCacheKey
import com.flxrs.dankchat.ui.chat.emote.stackedCacheKey
import com.flxrs.dankchat.ui.chat.emote.toEmoteSheetData
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.toImmutableMap
import kotlin.math.ceil

private val logger = KotlinLogging.logger("MessageTextRenderer")

@Composable
fun MessageTextWithInlineContent(
    annotatedString: AnnotatedString,
    badges: ImmutableList<BadgeUi>,
    emotes: ImmutableList<EmoteUi>,
    fontSize: Float,
    animateGifs: Boolean,
    onTextClick: (Int) -> Unit,
    onEmoteClick: (List<EmoteSheetData>) -> Unit,
    modifier: Modifier = Modifier,
    asciiArtStart: Int? = null,
    onTextLongClick: ((Int) -> Unit)? = null,
    interactionSource: MutableInteractionSource? = null,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
) {
    val emoteCoordinator = LocalEmoteAnimationCoordinator.current
    val density = LocalDensity.current

    val badgeSize = emoteBaseHeight(fontSize)
    val inlineContentProviders: ImmutableMap<String, @Composable () -> Unit> =
        remember(badges, emotes, fontSize, animateGifs) {
            buildMap<String, @Composable () -> Unit> {
                badges.forEach { badge ->
                    put("BADGE_${badge.position}") {
                        // BasicText composes inline children positionally, so an explicit key
                        // prevents a slot reused for a different item from inheriting its state
                        key(badge.position, badge.url) {
                            BadgeInlineContent(badge = badge, size = badgeSize)
                        }
                    }
                }

                emotes.forEach { emote ->
                    put("EMOTE_${emote.position}") {
                        key(emote.position, emote.urls) {
                            StackedEmote(
                                emote = emote,
                                fontSize = fontSize,
                                emoteCoordinator = emoteCoordinator,
                                animateGifs = animateGifs,
                                modifier = Modifier,
                                onClick = { onEmoteClick(emote.emotes.map { it.toEmoteSheetData() }) },
                            )
                        }
                    }
                }
            }.toImmutableMap()
        }

    val baseHeightPx = with(density) { badgeSize.toPx().toInt() }
    var dimensionRefresh by remember(badges, emotes, fontSize) { mutableIntStateOf(0) }
    // Unloaded emotes get the loading placeholder size as an estimate so the map is always
    // complete and rows never fall back to subcompose measuring
    val (knownDimensions, hasEstimatedDimensions) =
        remember(badges, emotes, fontSize, emoteCoordinator, dimensionRefresh) {
            var hasEstimates = false
            val dimensions = buildMap {
                badges.forEach { badge ->
                    put("BADGE_${badge.position}", EmoteDimensions("BADGE_${badge.position}", baseHeightPx, baseHeightPx))
                }

                emotes.forEach { emote ->
                    val id = "EMOTE_${emote.position}"
                    val dims = emoteCoordinator.getDimensions(emote.dimensionKey(baseHeightPx))
                    when {
                        dims != null -> put(id, EmoteDimensions(id, dims.first, dims.second))

                        else -> {
                            hasEstimates = true
                            val estimate = baseHeightPx * (emote.emotes.firstOrNull()?.scale ?: 1)
                            put(id, EmoteDimensions(id, estimate, estimate))
                        }
                    }
                }
            }.toImmutableMap()
            dimensions to hasEstimates
        }

    // Rows composed before their emotes loaded wait for the dimensions to land, then upgrade
    // from the estimated placeholder sizes
    if (hasEstimatedDimensions) {
        LaunchedEffect(badges, emotes, fontSize) {
            emoteCoordinator.dimensionUpdates.collect {
                val allKnown = emotes.all { emote -> emoteCoordinator.getDimensions(emote.dimensionKey(baseHeightPx)) != null }
                if (allKnown) {
                    dimensionRefresh++
                }
            }
        }
    }

    val style = TextStyle(fontSize = fontSize.sp)
    when {
        // Line limited previews keep the regular layout, a scaled partial art block would be meaningless
        asciiArtStart != null && maxLines == Int.MAX_VALUE -> {
            val (prefix, art) = remember(annotatedString, asciiArtStart) {
                annotatedString.subSequence(0, asciiArtStart) to annotatedString.subSequence(asciiArtStart, annotatedString.length)
            }
            // Offsets map back to the full string, -1 marks a press outside the text
            val toFullOffset = { offset: Int ->
                when {
                    offset < 0 -> offset
                    else -> offset + asciiArtStart
                }
            }
            Column(modifier = modifier.fillMaxWidth()) {
                if (prefix.isNotEmpty()) {
                    TextWithMeasuredInlineContent(
                        text = prefix,
                        inlineContentProviders = inlineContentProviders,
                        style = style,
                        knownDimensions = knownDimensions,
                        modifier = Modifier.fillMaxWidth(),
                        interactionSource = interactionSource,
                        onTextClick = onTextClick,
                        onTextLongClick = onTextLongClick,
                    )
                }
                TextWithMeasuredInlineContent(
                    text = art,
                    inlineContentProviders = inlineContentProviders,
                    style = style,
                    knownDimensions = knownDimensions,
                    modifier = Modifier.asciiArtLayout(fontSize),
                    interactionSource = interactionSource,
                    onTextClick = { onTextClick(toFullOffset(it)) },
                    onTextLongClick = onTextLongClick?.let { onLongClick -> { onLongClick(toFullOffset(it)) } },
                )
            }
        }

        else -> {
            TextWithMeasuredInlineContent(
                text = annotatedString,
                inlineContentProviders = inlineContentProviders,
                style = style,
                knownDimensions = knownDimensions,
                maxLines = maxLines,
                overflow = overflow,
                modifier = modifier.fillMaxWidth(),
                interactionSource = interactionSource,
                onTextClick = onTextClick,
                onTextLongClick = onTextLongClick,
            )
        }
    }
}

// Twitch web chat wraps art in a 300px column, which matches 300dp at the default 14sp font
private const val ASCII_ART_WIDTH_EM = 300f / 14f

/**
 * Lays out ASCII art at the width it was drawn for, relative to the font size, so its lines wrap like on Twitch.
 * Narrower chats shrink the art uniformly instead of re-wrapping it.
 */
private fun Modifier.asciiArtLayout(fontSize: Float): Modifier = layout { measurable, constraints ->
    val artWidth = (fontSize * ASCII_ART_WIDTH_EM).sp.roundToPx()
    val placeable = measurable.measure(Constraints(minWidth = artWidth, maxWidth = artWidth))
    when {
        constraints.hasBoundedWidth && constraints.maxWidth < artWidth -> {
            val scale = constraints.maxWidth.toFloat() / artWidth
            val height = constraints.constrainHeight(ceil(placeable.height * scale).toInt())
            layout(constraints.maxWidth, height) {
                placeable.placeWithLayer(0, 0) {
                    scaleX = scale
                    scaleY = scale
                    transformOrigin = TransformOrigin(0f, 0f)
                }
            }
        }

        else -> {
            layout(constraints.constrainWidth(artWidth), constraints.constrainHeight(placeable.height)) {
                placeable.place(0, 0)
            }
        }
    }
}

private fun EmoteUi.dimensionKey(baseHeightPx: Int): String = when {
    urls.size == 1 -> singleEmoteCacheKey(urls.first(), baseHeightPx)
    else -> stackedCacheKey(baseHeightPx)
}

fun launchCustomTab(
    context: Context,
    url: String,
) {
    try {
        CustomTabsIntent
            .Builder()
            .setShowTitle(true)
            .build()
            .launchUrl(context, url.toUri())
    } catch (e: Exception) {
        logger.error(e) { "Error launching URL" }
    }
}

fun timestampSpanStyle(
    fontSize: Float,
    color: Color,
) = SpanStyle(
    fontFamily = FontFamily.Monospace,
    fontWeight = FontWeight.Bold,
    fontSize = (fontSize * 0.95f).sp,
    color = color,
    letterSpacing = (-0.03).em,
)

private const val SPACER_PREFIX = "spacer_"

fun AnnotatedString.Builder.appendInlineSpacer(width: Dp) {
    appendInlineContent("$SPACER_PREFIX${width.value}", " ")
}

fun isSpacerId(id: String): Boolean = id.startsWith(SPACER_PREFIX)

fun spacerWidthDp(id: String): Float = id.removePrefix(SPACER_PREFIX).toFloatOrNull() ?: 0f

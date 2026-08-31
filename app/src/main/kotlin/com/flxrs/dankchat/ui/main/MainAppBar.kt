package com.flxrs.dankchat.ui.main

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.composeunstyled.UnstyledScrollArea
import com.composeunstyled.UnstyledThumb
import com.composeunstyled.UnstyledVerticalScrollbar
import com.composeunstyled.rememberScrollAreaState
import com.flxrs.dankchat.R
import com.flxrs.dankchat.ui.theme.toolbarPillColor
import com.flxrs.dankchat.utils.compose.predictiveBackScale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first

@Immutable
sealed interface AppBarMenu {
    data object Main : AppBarMenu

    data object Upload : AppBarMenu

    data object Channel : AppBarMenu
}

internal const val RESTING_SCROLLBAR_ALPHA = 0.6f

internal class InlineMenuItemRegistry {
    var pressedKey by mutableStateOf<Any?>(null)
    private val items = mutableStateMapOf<Any, ItemEntry>()

    fun register(
        key: Any,
        bounds: Rect,
        onSelect: () -> Unit,
    ) {
        items[key] = ItemEntry(bounds, onSelect)
    }

    fun unregister(key: Any) {
        items.remove(key)
    }

    fun keyAt(window: Offset): Any? = items.entries.firstOrNull { it.value.bounds.contains(window) }?.key

    fun selectAt(window: Offset): Boolean {
        val entry = items.values.firstOrNull { it.bounds.contains(window) } ?: return false
        entry.onSelect()
        return true
    }

    private data class ItemEntry(
        val bounds: Rect,
        val onSelect: () -> Unit,
    )
}

internal val LocalInlineMenuItemRegistry = staticCompositionLocalOf<InlineMenuItemRegistry?> { null }

@Composable
fun InlineOverflowMenu(
    isLoggedIn: Boolean,
    channelNotificationsEnabled: Boolean,
    onDismiss: () -> Unit,
    onAction: (ToolbarAction) -> Unit,
    initialMenu: AppBarMenu = AppBarMenu.Main,
    maxHeightDp: Dp = 0.dp,
) {
    var currentMenu by remember(initialMenu) { mutableStateOf(initialMenu) }
    var backProgress by remember { mutableFloatStateOf(0f) }

    PredictiveBackHandler { progress ->
        try {
            progress.collect { event ->
                backProgress = event.progress
            }
            when (currentMenu) {
                AppBarMenu.Main -> {
                    onDismiss()
                }

                else -> {
                    backProgress = 0f
                    currentMenu = AppBarMenu.Main
                }
            }
        } catch (_: CancellationException) {
            backProgress = 0f
        }
    }

    val menuWidth = rememberMainMenuWidth(isLoggedIn)

    val scrollState = rememberScrollState()
    val scrollAreaState = rememberScrollAreaState(scrollState)
    var itemHeightPx by remember { mutableIntStateOf(0) }
    val measureModifier = Modifier.onSizeChanged { if (itemHeightPx == 0) itemHeightPx = it.height }

    val scrollbarAlpha = remember { Animatable(RESTING_SCROLLBAR_ALPHA) }
    LaunchedEffect(currentMenu) {
        scrollState.scrollTo(0)
        val maxScroll = snapshotFlow { scrollState.maxValue }.first { it != Int.MAX_VALUE }
        if (maxScroll > 0) {
            scrollbarAlpha.snapTo(1f)
            delay(400)
            scrollbarAlpha.animateTo(RESTING_SCROLLBAR_ALPHA, tween(500))
        } else {
            scrollbarAlpha.snapTo(RESTING_SCROLLBAR_ALPHA)
        }
    }

    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.toolbarPillColor,
        modifier = Modifier.predictiveBackScale(backProgress),
    ) {
        UnstyledScrollArea(
            state = scrollAreaState,
            modifier =
                Modifier
                    .width(menuWidth)
                    .heightIn(max = maxHeightDp),
        ) {
            AnimatedContent(
                targetState = currentMenu,
                transitionSpec = {
                    if (targetState != AppBarMenu.Main) {
                        (slideInHorizontally { it } + fadeIn()).togetherWith(slideOutHorizontally { -it } + fadeOut())
                    } else {
                        (slideInHorizontally { -it } + fadeIn()).togetherWith(slideOutHorizontally { it } + fadeOut())
                    }.using(SizeTransform(clip = false))
                },
                label = "InlineMenuTransition",
            ) { menu ->
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .verticalScroll(scrollState)
                            .padding(vertical = 8.dp),
                ) {
                    when (menu) {
                        AppBarMenu.Main -> MainMenuContent(
                            isLoggedIn = isLoggedIn,
                            onAction = onAction,
                            onDismiss = onDismiss,
                            onNavigateToUpload = { currentMenu = AppBarMenu.Upload },
                            onNavigateToChannel = { currentMenu = AppBarMenu.Channel },
                            modifier = measureModifier,
                        )

                        AppBarMenu.Upload -> UploadMenuContent(
                            onAction = onAction,
                            onDismiss = onDismiss,
                            onBack = { currentMenu = AppBarMenu.Main },
                            modifier = measureModifier,
                        )

                        AppBarMenu.Channel -> ChannelMenuContent(
                            isLoggedIn = isLoggedIn,
                            notificationsEnabled = channelNotificationsEnabled,
                            onAction = onAction,
                            onDismiss = onDismiss,
                            onBack = { currentMenu = AppBarMenu.Main },
                            modifier = measureModifier,
                        )
                    }
                }
            }
            if (scrollState.maxValue > itemHeightPx) {
                UnstyledVerticalScrollbar(
                    modifier =
                        Modifier
                            .align(Alignment.TopEnd)
                            .fillMaxHeight()
                            .padding(vertical = 6.dp)
                            .padding(end = 6.dp)
                            .width(4.dp),
                ) {
                    UnstyledThumb(
                        modifier = Modifier.background(
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = scrollbarAlpha.value),
                            RoundedCornerShape(100),
                        ),
                        enabled = false,
                    )
                }
            }
        }
    }
}

@Composable
private fun InlineMenuItem(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    maxLines: Int = 1,
    hasSubMenu: Boolean = false,
) {
    val registry = LocalInlineMenuItemRegistry.current
    val isPressed = registry?.pressedKey == text
    if (registry != null) {
        DisposableEffect(registry, text) {
            onDispose { registry.unregister(text) }
        }
    }
    val highlightColor = MaterialTheme.colorScheme.surfaceContainerHighest
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .onGloballyPositioned { coords ->
                    registry?.register(text, coords.boundsInWindow(), onClick)
                }.background(if (isPressed) highlightColor else Color.Transparent)
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (hasSubMenu) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun InlineSubMenuHeader(
    title: String,
    onBack: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onBack)
                .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(end = 8.dp),
        )
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun ColumnScope.MainMenuContent(
    isLoggedIn: Boolean,
    onAction: (ToolbarAction) -> Unit,
    onDismiss: () -> Unit,
    onNavigateToUpload: () -> Unit,
    onNavigateToChannel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!isLoggedIn) {
        InlineMenuItem(
            text = stringResource(R.string.login),
            icon = Icons.AutoMirrored.Filled.Login,
            onClick = {
                onAction(ToolbarAction.Login)
                onDismiss()
            },
            modifier = modifier,
        )
    } else {
        InlineMenuItem(
            text = stringResource(R.string.relogin),
            icon = Icons.Default.Refresh,
            onClick = {
                onAction(ToolbarAction.Relogin)
                onDismiss()
            },
            modifier = modifier,
        )
        InlineMenuItem(
            text = stringResource(R.string.logout),
            icon = Icons.AutoMirrored.Filled.Logout,
            onClick = {
                onAction(ToolbarAction.Logout)
                onDismiss()
            },
        )
    }

    HorizontalDivider()

    InlineMenuItem(
        text = stringResource(R.string.manage_channels),
        icon = Icons.Default.EditNote,
        onClick = {
            onAction(ToolbarAction.ManageChannels)
            onDismiss()
        },
    )
    InlineMenuItem(
        text = stringResource(R.string.remove_channel),
        icon = Icons.Default.RemoveCircleOutline,
        onClick = {
            onAction(ToolbarAction.RemoveChannel)
            onDismiss()
        },
    )
    InlineMenuItem(
        text = stringResource(R.string.reload_emotes),
        icon = Icons.Default.EmojiEmotions,
        onClick = {
            onAction(ToolbarAction.ReloadEmotes)
            onDismiss()
        },
    )
    InlineMenuItem(
        text = stringResource(R.string.reconnect),
        icon = Icons.Default.Autorenew,
        onClick = {
            onAction(ToolbarAction.Reconnect)
            onDismiss()
        },
    )

    HorizontalDivider()

    InlineMenuItem(
        text = stringResource(R.string.upload_media),
        icon = Icons.Default.CloudUpload,
        onClick = onNavigateToUpload,
        hasSubMenu = true,
    )
    InlineMenuItem(
        text = stringResource(R.string.channel),
        icon = Icons.Default.Info,
        onClick = onNavigateToChannel,
        hasSubMenu = true,
    )

    HorizontalDivider()

    InlineMenuItem(
        text = stringResource(R.string.settings),
        icon = Icons.Default.Settings,
        onClick = {
            onAction(ToolbarAction.OpenSettings)
            onDismiss()
        },
    )
}

@Composable
private fun ColumnScope.UploadMenuContent(
    onAction: (ToolbarAction) -> Unit,
    onDismiss: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    InlineSubMenuHeader(title = stringResource(R.string.upload_media), onBack = onBack)
    InlineMenuItem(
        text = stringResource(R.string.take_picture),
        icon = Icons.Default.CameraAlt,
        onClick = {
            onAction(ToolbarAction.CaptureImage)
            onDismiss()
        },
        modifier = modifier,
        maxLines = 2,
    )
    InlineMenuItem(
        text = stringResource(R.string.record_video),
        icon = Icons.Default.Videocam,
        onClick = {
            onAction(ToolbarAction.CaptureVideo)
            onDismiss()
        },
        maxLines = 2,
    )
    InlineMenuItem(
        text = stringResource(R.string.choose_media),
        icon = Icons.Default.Image,
        onClick = {
            onAction(ToolbarAction.ChooseMedia)
            onDismiss()
        },
        maxLines = 2,
    )
}

@Composable
private fun ColumnScope.ChannelMenuContent(
    isLoggedIn: Boolean,
    notificationsEnabled: Boolean,
    onAction: (ToolbarAction) -> Unit,
    onDismiss: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    InlineSubMenuHeader(title = stringResource(R.string.channel), onBack = onBack)
    InlineMenuItem(
        text =
            stringResource(
                when {
                    notificationsEnabled -> R.string.channel_highlight_notifications_on
                    else -> R.string.channel_highlight_notifications_off
                },
            ),
        icon =
            when {
                notificationsEnabled -> Icons.Default.Notifications
                else -> Icons.Default.NotificationsOff
            },
        onClick = { onAction(ToolbarAction.ToggleChannelNotifications) },
        modifier = modifier,
        maxLines = 2,
    )
    InlineMenuItem(
        text = stringResource(R.string.open_channel),
        icon = Icons.Default.OpenInBrowser,
        onClick = {
            onAction(ToolbarAction.OpenChannel)
            onDismiss()
        },
        maxLines = 2,
    )
    InlineMenuItem(
        text = stringResource(R.string.report_channel),
        icon = Icons.Default.Flag,
        onClick = {
            onAction(ToolbarAction.ReportChannel)
            onDismiss()
        },
        maxLines = 2,
    )
    if (isLoggedIn) {
        InlineMenuItem(
            text = stringResource(R.string.block_channel),
            icon = Icons.Default.Block,
            onClick = {
                onAction(ToolbarAction.BlockChannel)
                onDismiss()
            },
            maxLines = 2,
        )
    }
}

private val MENU_ITEM_CHROME_WIDTH = (16 + 20 + 12 + 20 + 16).dp // padding + icon + gap + submenu arrow + padding
private val MIN_MENU_WIDTH = 200.dp

@Composable
private fun rememberMainMenuWidth(isLoggedIn: Boolean): Dp {
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()
    val textStyle = MaterialTheme.typography.bodyLarge

    val mainItemTexts = buildList {
        if (isLoggedIn) {
            add(stringResource(R.string.relogin))
            add(stringResource(R.string.logout))
        } else {
            add(stringResource(R.string.login))
        }
        add(stringResource(R.string.manage_channels))
        add(stringResource(R.string.remove_channel))
        add(stringResource(R.string.reload_emotes))
        add(stringResource(R.string.reconnect))
        add(stringResource(R.string.upload_media))
        add(stringResource(R.string.channel))
        add(stringResource(R.string.settings))
    }

    return remember(mainItemTexts, density, textStyle) {
        val maxTextWidthPx = mainItemTexts.maxOf { textMeasurer.measure(it, textStyle).size.width }
        val totalWidthPx = with(density) { MENU_ITEM_CHROME_WIDTH.roundToPx() } + maxTextWidthPx
        maxOf(with(density) { totalWidthPx.toDp() }, MIN_MENU_WIDTH)
    }
}

package com.flxrs.dankchat.ui.main

import androidx.activity.compose.BackHandler
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imeAnimationTarget
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.window.core.layout.WindowSizeClass
import com.flxrs.dankchat.R
import com.flxrs.dankchat.data.UserName
import com.flxrs.dankchat.preferences.DankChatPreferenceStore
import com.flxrs.dankchat.preferences.appearance.InputAction
import com.flxrs.dankchat.ui.chat.FabMenuCallbacks
import com.flxrs.dankchat.ui.chat.PinnedMessageUiState
import com.flxrs.dankchat.ui.chat.PinnedMessageViewModel
import com.flxrs.dankchat.ui.chat.ScrollDirectionTracker
import com.flxrs.dankchat.ui.chat.emote.EmoteAnimationCoordinator
import com.flxrs.dankchat.ui.chat.emote.EmoteInfoViewModel
import com.flxrs.dankchat.ui.chat.emote.LocalEmoteAnimationCoordinator
import com.flxrs.dankchat.ui.chat.history.HistoryChannel
import com.flxrs.dankchat.ui.chat.mention.MentionViewModel
import com.flxrs.dankchat.ui.chat.message.MessageOptionsViewModel
import com.flxrs.dankchat.ui.chat.messages.common.launchCustomTab
import com.flxrs.dankchat.ui.chat.user.UserPopupViewModel
import com.flxrs.dankchat.ui.main.channel.ChannelManagementViewModel
import com.flxrs.dankchat.ui.main.channel.ChannelPagerUiState
import com.flxrs.dankchat.ui.main.channel.ChannelPagerViewModel
import com.flxrs.dankchat.ui.main.channel.ChannelTabViewModel
import com.flxrs.dankchat.ui.main.dialog.DialogStateViewModel
import com.flxrs.dankchat.ui.main.dialog.MainScreenDialogs
import com.flxrs.dankchat.ui.main.dialog.ModActionsViewModel
import com.flxrs.dankchat.ui.main.input.ChatBottomBar
import com.flxrs.dankchat.ui.main.input.ChatInputCallbacks
import com.flxrs.dankchat.ui.main.input.ChatInputViewModel
import com.flxrs.dankchat.ui.main.input.InputOverlay
import com.flxrs.dankchat.ui.main.input.SuggestionDropdown
import com.flxrs.dankchat.ui.main.input.TourOverlayState
import com.flxrs.dankchat.ui.main.sheet.FullScreenSheetOverlay
import com.flxrs.dankchat.ui.main.sheet.FullScreenSheetState
import com.flxrs.dankchat.ui.main.sheet.SheetNavigationViewModel
import com.flxrs.dankchat.ui.main.stream.StreamView
import com.flxrs.dankchat.ui.main.stream.StreamViewModel
import com.flxrs.dankchat.ui.tour.FeatureTourUiState
import com.flxrs.dankchat.ui.tour.FeatureTourViewModel
import com.flxrs.dankchat.ui.tour.PostOnboardingStep
import com.flxrs.dankchat.ui.tour.TourStep
import com.flxrs.dankchat.utils.compose.rememberRoundedCornerBottomPadding
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

private val ROUNDED_CORNER_THRESHOLD = 8.dp

// Per-layout parameters for the movable stream content
internal data class StreamViewConfig(
    val channel: UserName,
    val isInPipMode: Boolean = false,
    val fillPane: Boolean = false,
    val isTheaterMode: Boolean = false,
    val isTheaterChatVisible: Boolean = false,
    val canDockTheaterChat: Boolean = false,
    val overlayEndPadding: Dp = 0.dp,
)

@Suppress("ModifierNotUsedAtRoot")
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MainScreen(
    isLoggedIn: Boolean,
    onNavigateToSettings: () -> Unit,
    onLogin: () -> Unit,
    onRelogin: () -> Unit,
    onLogout: () -> Unit,
    onOpenChannel: () -> Unit,
    onReportChannel: () -> Unit,
    onOpenUrl: (String) -> Unit,
    onCaptureImage: () -> Unit,
    onCaptureVideo: () -> Unit,
    onChooseMedia: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenLogViewer: () -> Unit = {},
) {
    val density = LocalDensity.current
    val messageNotInHistoryMsg = stringResource(R.string.message_not_in_history)
    val mainScreenViewModel: MainScreenViewModel = koinViewModel()
    val channelManagementViewModel: ChannelManagementViewModel = koinViewModel()
    val channelTabViewModel: ChannelTabViewModel = koinViewModel()
    val channelPagerViewModel: ChannelPagerViewModel = koinViewModel()
    val chatInputViewModel: ChatInputViewModel = koinViewModel()
    val sheetNavigationViewModel: SheetNavigationViewModel = koinViewModel()
    val streamViewModel: StreamViewModel = koinViewModel()
    val dialogViewModel: DialogStateViewModel = koinViewModel()
    val emoteInfoViewModel: EmoteInfoViewModel = koinViewModel()
    val userPopupViewModel: UserPopupViewModel = koinViewModel()
    val messageOptionsViewModel: MessageOptionsViewModel = koinViewModel()
    val modActionsViewModel: ModActionsViewModel = koinViewModel()
    val mentionViewModel: MentionViewModel = koinViewModel()
    val preferenceStore: DankChatPreferenceStore = koinInject()
    val mainEventBus: MainEventBus = koinInject()
    val featureTourViewModel: FeatureTourViewModel = koinViewModel()
    val featureTourState by featureTourViewModel.uiState.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val scrollTargets = remember { mutableStateMapOf<UserName, String>() }
    // Lazy ref for composePagerState, used in jump handlers declared before the pager
    var composePagerStateRef by remember { mutableStateOf<PagerState?>(null) }
    val keyboardController = LocalSoftwareKeyboardController.current
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE

    val mainState by mainScreenViewModel.uiState.collectAsStateWithLifecycle()

    val ime = WindowInsets.ime
    val navBars = WindowInsets.navigationBars
    val imeTarget = WindowInsets.imeAnimationTarget
    // Target height for stability during opening animation
    val targetImeHeight = (imeTarget.getBottom(density) - navBars.getBottom(density)).coerceAtLeast(0)
    val isImeOpening = targetImeHeight > 0

    // The ime inset changes on every animation frame, only read it inside effects and derived state
    val imeHeightState = remember(density) {
        derivedStateOf { (ime.getBottom(density) - navBars.getBottom(density)).coerceAtLeast(0) }
    }
    val isImeVisible = WindowInsets.isImeVisible

    // Keyboard height tracking — VM handles debounce + persistence
    LaunchedEffect(isLandscape) { mainScreenViewModel.initKeyboardHeight(isLandscape) }
    val keyboardHeightPx by mainScreenViewModel.keyboardHeightPx.collectAsStateWithLifecycle()
    val minKeyboardHeightPx = with(density) { 100.dp.toPx() }
    LaunchedEffect(targetImeHeight, isLandscape) {
        mainScreenViewModel.trackKeyboardHeight(targetImeHeight, isLandscape, minKeyboardHeightPx)
    }

    // Close emote menu when keyboard opens, but wait for keyboard to reach
    // persisted height so scaffold padding doesn't jump during the transition
    LaunchedEffect(isImeVisible) {
        if (isImeVisible) {
            if (keyboardHeightPx > 0) {
                snapshotFlow { imeHeightState.value }
                    .first { it >= keyboardHeightPx }
            }
            chatInputViewModel.setEmoteMenuOpen(false)
        }
    }

    val inputState by chatInputViewModel.uiState(sheetNavigationViewModel.fullScreenSheetState, mentionViewModel.currentTab).collectAsStateWithLifecycle()
    val isKeyboardVisible = isImeVisible || isImeOpening
    var backProgress by remember { mutableFloatStateOf(0f) }

    // Stream state
    val streamVmState by streamViewModel.streamState.collectAsStateWithLifecycle()
    val currentStream = streamVmState.currentStream
    val hasStreamData = streamVmState.hasStreamData
    val isAudioOnly = streamVmState.isAudioOnly
    val isTheaterMode = streamVmState.isTheaterMode
    val streamState = rememberStreamToolbarState(currentStream)

    // PiP state — observe via lifecycle since onPause fires when entering PiP
    val isInPipMode = observePipMode(streamViewModel)

    // Wide split layout: side-by-side stream + chat on medium+ width windows
    val windowSizeClass = currentWindowAdaptiveInfo().windowSizeClass
    val isWideWindow =
        windowSizeClass.isWidthAtLeastBreakpoint(
            WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND,
        )
    // Follows the window, so neither the split layout nor a portrait shaped theater can flash
    val isTheaterRequested = isTheaterMode || streamVmState.isTheaterRotationSuspended
    val theaterStream =
        when {
            isTheaterRequested && isLandscape && !isInPipMode -> currentStream
            else -> null
        }
    val useTheaterLayout = theaterStream != null
    val useWideSplitLayout = isWideWindow && currentStream != null && !isInPipMode && !useTheaterLayout

    // Registered before the emote menu handler, so an open emote menu closes first
    BackHandler(enabled = useTheaterLayout) { streamViewModel.exitTheaterMode() }

    // Only intercept when menu is visible AND keyboard is fully GONE,
    // so system keyboard close gestures are not intercepted
    val isImeFullyHidden by remember { derivedStateOf { imeHeightState.value == 0 } }
    PredictiveBackHandler(enabled = inputState.isEmoteMenuOpen && isImeFullyHidden) { progress ->
        try {
            progress.collect { event ->
                backProgress = event.progress
            }
            chatInputViewModel.setEmoteMenuOpen(false)
            backProgress = 0f
        } catch (_: Exception) {
            backProgress = 0f
        }
    }

    val dialogState by dialogViewModel.state.collectAsStateWithLifecycle()
    val emoteInfoActive by emoteInfoViewModel.isActive.collectAsStateWithLifecycle(initialValue = false)
    val userPopupActive by userPopupViewModel.isActive.collectAsStateWithLifecycle(initialValue = false)
    val messageOptionsActive by messageOptionsViewModel.isActive.collectAsStateWithLifecycle(initialValue = false)
    val modActionsActive by modActionsViewModel.isActive.collectAsStateWithLifecycle(initialValue = false)

    val sheetNavState by sheetNavigationViewModel.sheetState.collectAsStateWithLifecycle()
    val fullScreenSheetState = sheetNavState.fullScreenSheet
    val isSheetOpen = fullScreenSheetState !is FullScreenSheetState.Closed
    val isHistorySheet = fullScreenSheetState is FullScreenSheetState.History
    val inputSheetState = sheetNavState.inputSheet

    // Dismiss keyboard before opening sheets to prevent animation conflicts.
    // Defers sheet rendering until the keyboard has started closing.
    val hasBottomSheet = isSheetOpen ||
        messageOptionsActive ||
        userPopupActive ||
        emoteInfoActive ||
        modActionsActive
    var sheetsReady by remember { mutableStateOf(true) }
    LaunchedEffect(hasBottomSheet) {
        if (hasBottomSheet && isImeVisible) {
            sheetsReady = false
            keyboardController?.hide()
            snapshotFlow { imeHeightState.value }
                .first { it == 0 }
        }
        sheetsReady = true
    }

    MainScreenEventHandler(
        snackbarHostState = snackbarHostState,
        mainEventBus = mainEventBus,
        dialogViewModel = dialogViewModel,
        chatInputViewModel = chatInputViewModel,
        channelTabViewModel = channelTabViewModel,
        sheetNavigationViewModel = sheetNavigationViewModel,
        mainScreenViewModel = mainScreenViewModel,
        preferenceStore = preferenceStore,
    )

    val tabState = channelTabViewModel.uiState.collectAsStateWithLifecycle().value
    val activeChannel = tabState.tabs.getOrNull(tabState.selectedIndex)?.channel
    val mutedNotificationChannels by channelManagementViewModel.mutedNotificationChannels.collectAsStateWithLifecycle()
    val channelNotificationsEnabled = activeChannel == null || activeChannel.lowercase() !in mutedNotificationChannels

    // Same key as in ChatComposable, so this resolves the active page's instance
    val activePinnedMessageViewModel =
        activeChannel?.let { channel ->
            koinViewModel<PinnedMessageViewModel>(
                key = "pinned-${channel.value}",
                parameters = { parametersOf(channel) },
            )
        }
    val activePinnedMessageState =
        activePinnedMessageViewModel
            ?.uiState
            ?.collectAsStateWithLifecycle()
            ?.value ?: PinnedMessageUiState.Hidden

    MainScreenTourEffects(
        featureTourViewModel = featureTourViewModel,
        featureTourState = featureTourState,
        mainScreenViewModel = mainScreenViewModel,
        mainState = mainState,
        channelsReady = !tabState.loading,
        channelsEmpty = tabState.tabs.isEmpty() && !tabState.loading,
    )

    MainScreenDialogs(
        dialogViewModel = dialogViewModel,
        isLoggedIn = isLoggedIn,
        activeChannel = activeChannel,
        isStreamActive = currentStream != null,
        inputSheetState = inputSheetState,
        sheetsReady = sheetsReady,
        onAddChannel = {
            channelManagementViewModel.addChannel(it)
            dialogViewModel.dismissAddChannel()
        },
        onLogout = onLogout,
        onLogin = onLogin,
        onOpenUrl = onOpenUrl,
        onOpenLogViewer = onOpenLogViewer,
        onJumpToMessage = { messageId, channel ->
            val target = channelPagerViewModel.resolveJumpTarget(channel, messageId)
            if (target != null) {
                messageOptionsViewModel.dismiss()
                sheetNavigationViewModel.closeFullScreenSheet()
                scrollTargets[target.channel] = target.messageId
                scope.launch { composePagerStateRef?.scrollToPage(target.channelIndex) }
            } else {
                scope.launch {
                    snackbarHostState.showSnackbar(messageNotInHistoryMsg)
                }
            }
        },
    )

    val isFullscreen = mainState.isFullscreen
    val showInput = mainState.showInput
    val swipeNavigation = mainState.swipeNavigation
    val effectiveShowAppBar = mainState.effectiveShowAppBar

    val toolbarTracker =
        remember {
            ScrollDirectionTracker(
                hideThresholdPx = with(density) { 100.dp.toPx() },
                showThresholdPx = with(density) { 36.dp.toPx() },
                onHide = { mainScreenViewModel.setGestureToolbarHidden(true) },
                onShow = { mainScreenViewModel.setGestureToolbarHidden(false) },
            )
        }

    val swipeDownThresholdPx = with(density) { (if (inputState.isCompactMode) 24.dp else 56.dp).toPx() }

    FullscreenSystemBarsEffect(isFullscreen || useTheaterLayout)
    TheaterOrientationEffect(isTheaterMode)
    TheaterRotationEffect(
        isTheaterMode = isTheaterMode,
        isRotationSuspended = streamVmState.isTheaterRotationSuspended,
        onSuspendTheater = { streamViewModel.suspendTheaterForRotation() },
        onResumeTheater = { streamViewModel.resumeTheaterFromRotation() },
    )

    val isInputSheet = fullScreenSheetState is FullScreenSheetState.Replies ||
        fullScreenSheetState is FullScreenSheetState.Mention ||
        fullScreenSheetState is FullScreenSheetState.Whisper
    LaunchedEffect(isInputSheet) {
        if (isInputSheet && !showInput) {
            mainScreenViewModel.toggleInput()
        }
    }

    val pagerState by channelPagerViewModel.uiState.collectAsStateWithLifecycle()

    val composePagerState =
        rememberPagerState(
            initialPage = pagerState.currentPage,
            pageCount = { pagerState.channels.size },
        ).also { composePagerStateRef = it }
    var inputHeightPx by remember { mutableIntStateOf(0) }
    var helperTextHeightPx by remember { mutableIntStateOf(0) }
    var inputOverflowExpanded by remember { mutableStateOf(false) }
    var recentMessagesExpanded by remember { mutableStateOf(false) }
    var isInputMultiline by remember { mutableStateOf(false) }
    var toolbarBottomPx by remember { mutableIntStateOf(0) }
    if (!showInput) inputHeightPx = 0
    if (showInput || inputState.helperText.isEmpty) helperTextHeightPx = 0
    val inputHeightDp = with(density) { inputHeightPx.toDp() }
    val helperTextHeightDp = with(density) { helperTextHeightPx.toDp() }
    val toolbarBottomDp = with(density) { toolbarBottomPx.toDp() }
    val bottomReserveDp = with(density) {
        maxOf(imeTarget.getBottom(density), navBars.getBottom(density)).toDp()
    }

    val focusManager = LocalFocusManager.current
    MainScreenFocusEffects(
        imeHeight = imeHeightState,
        isEmoteMenuOpen = inputState.isEmoteMenuOpen,
        currentStream = currentStream,
    )

    MainScreenPagerEffects(
        composePagerState = composePagerState,
        pagerState = pagerState,
        onSetActivePage = channelPagerViewModel::setActivePage,
        onClearNotifications = channelPagerViewModel::clearNotifications,
        onShowToolbar = { mainScreenViewModel.setGestureToolbarHidden(false) },
    )

    val emoteCoordinator: EmoteAnimationCoordinator = koinInject()
    val customTabContext = LocalContext.current
    val customTabUriHandler =
        remember(customTabContext) {
            object : UriHandler {
                override fun openUri(uri: String) {
                    launchCustomTab(customTabContext, uri)
                }
            }
        }

    CompositionLocalProvider(
        LocalEmoteAnimationCoordinator provides emoteCoordinator,
        LocalUriHandler provides customTabUriHandler,
    ) {
        var containerWidthPx by remember { mutableIntStateOf(0) }
        var containerHeightPx by remember { mutableIntStateOf(0) }
        val containerHeightDp = with(density) { containerHeightPx.toDp() }
        // Docked theater chat gets whatever width remains next to a full-height 16:9 stream,
        // so the mode only exists on screens where that remainder is usable
        val theaterDockedChatWidth = with(density) { (containerWidthPx - containerHeightPx * (16f / 9f)).toDp() }
        val canDockTheaterChat = theaterDockedChatWidth >= MIN_DOCKED_THEATER_CHAT_WIDTH
        val menuMaxHeightDp =
            (containerHeightDp - toolbarBottomDp - inputHeightDp - bottomReserveDp - 8.dp)
                .coerceAtLeast(0.dp)
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .onSizeChanged { size ->
                        containerWidthPx = size.width
                        containerHeightPx = size.height
                    }.then(if (!isFullscreen && !isInPipMode && !useTheaterLayout) Modifier.windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal)) else Modifier),
        ) {
            // Menu content height matches keyboard content area (above nav bar)
            val targetMenuHeight =
                if (keyboardHeightPx > 0) {
                    with(density) { keyboardHeightPx.toDp() }
                } else {
                    if (isLandscape) 200.dp else 350.dp
                }.coerceAtLeast(if (isLandscape) 150.dp else 250.dp)

            // Total menu height includes nav bar so the menu visually matches
            // the keyboard's full extent. Without this, the menu is shorter than
            // the keyboard by navBarHeight, causing a visible lag during reveal.
            val navBarHeightDp = with(density) { navBars.getBottom(density).toDp() }
            val roundedCornerBottomPadding = rememberRoundedCornerBottomPadding()
            val effectiveRoundedCorner =
                when {
                    roundedCornerBottomPadding >= ROUNDED_CORNER_THRESHOLD -> roundedCornerBottomPadding
                    else -> 0.dp
                }
            val totalMenuHeight = targetMenuHeight + navBarHeightDp

            // Shared scaffold bottom padding: max(ime, emote menu), with the ime ignored while a
            // dialog owns it. Resolved in the layout phase to avoid per-frame recomposition.
            val hasDialogWithInput = dialogState.showAddChannel || modActionsActive || dialogState.showManageChannels || dialogState.showNewWhisper
            val emoteMenuPadding = if (inputState.isEmoteMenuOpen) targetMenuHeight else 0.dp
            val scaffoldBottomInsets = remember(ime, navBars, hasDialogWithInput, emoteMenuPadding) {
                val emoteMenuInsets = WindowInsets(bottom = emoteMenuPadding)
                when {
                    hasDialogWithInput -> emoteMenuInsets
                    else -> ime.exclude(navBars).union(emoteMenuInsets)
                }
            }
            val targetImeDp = when {
                hasDialogWithInput -> 0.dp
                else -> with(density) { targetImeHeight.toDp() }
            }
            val scaffoldBottomTargetDp = max(targetImeDp, emoteMenuPadding)

            // Shared bottom bar content
            val bottomBar: @Composable () -> Unit = {
                ChatBottomBar(
                    showInput = showInput && !isHistorySheet,
                    textFieldState = chatInputViewModel.textFieldState,
                    uiState = inputState,
                    characterCounter = chatInputViewModel.characterCounter,
                    callbacks =
                        ChatInputCallbacks(
                            onSend = chatInputViewModel::sendMessage,
                            onLastMessageClick = chatInputViewModel::getLastMessage,
                            onRecentMessageClick = chatInputViewModel::setInputFromHistory,
                            onEmoteClick = {
                                if (!inputState.isEmoteMenuOpen) {
                                    keyboardController?.hide()
                                    chatInputViewModel.setEmoteMenuOpen(true)
                                } else {
                                    keyboardController?.show()
                                }
                            },
                            onOverlayDismiss = {
                                when (inputState.overlay) {
                                    is InputOverlay.Reply -> chatInputViewModel.setReplying(false)
                                    is InputOverlay.Whisper -> chatInputViewModel.setWhisperTarget(null)
                                    is InputOverlay.Announce -> chatInputViewModel.setAnnouncing(false)
                                    InputOverlay.None -> Unit
                                }
                            },
                            onToggleFullscreen = mainScreenViewModel::toggleFullscreen,
                            onToggleInput = {
                                mainScreenViewModel.toggleInput()
                                chatInputViewModel.setEmoteMenuOpen(false)
                            },
                            onToggleStream = {
                                when {
                                    currentStream != null -> streamViewModel.closeStream()
                                    else -> activeChannel?.let { streamViewModel.toggleStream(it) }
                                }
                            },
                            onAudioOnly = { streamViewModel.toggleAudioOnly() },
                            onToggleTheater = { streamViewModel.toggleTheaterMode() },
                            onModActions = { inputState.activeChannel?.let { modActionsViewModel.show(it) } },
                            onInputActionsChange = mainScreenViewModel::updateInputActions,
                            onSearchClick = { activeChannel?.let { sheetNavigationViewModel.openHistory(HistoryChannel.Channel(it)) } },
                            onDebugInfoClick = sheetNavigationViewModel::openDebugInfo,
                            onNewWhisper =
                                if (inputState.isWhisperTabActive) {
                                    dialogViewModel::showNewWhisper
                                } else {
                                    null
                                },
                            onRepeatedSendChange = chatInputViewModel::setRepeatedSend,
                            onInputMultilineChanged = { isInputMultiline = it },
                        ),
                    isUploading = dialogState.isUploading,
                    isLoading = tabState.loading,
                    isFullscreen = isFullscreen,
                    isModerator = mainScreenViewModel.isModeratorInChannel(inputState.activeChannel),
                    isStreamActive = currentStream != null,
                    isAudioOnly = isAudioOnly,
                    hasStreamData = hasStreamData,
                    isSheetOpen = isSheetOpen,
                    inputActions =
                        when (fullScreenSheetState) {
                            is FullScreenSheetState.Replies -> {
                                persistentListOf(InputAction.LastMessage)
                            }

                            is FullScreenSheetState.Whisper,
                            is FullScreenSheetState.Mention,
                            -> {
                                when {
                                    inputState.isWhisperTabActive && inputState.overlay is InputOverlay.Whisper -> persistentListOf(InputAction.LastMessage)
                                    else -> persistentListOf()
                                }
                            }

                            is FullScreenSheetState.History,
                            is FullScreenSheetState.Closed,
                            -> {
                                when {
                                    // Theater mode is already fullscreen, so toggling chat fullscreen makes no sense there
                                    useTheaterLayout -> mainState.inputActions.filterNot { it == InputAction.Fullscreen }.toImmutableList()

                                    else -> mainState.inputActions
                                }
                            }
                        },
                    onInputHeightChange = { inputHeightPx = it },
                    debugMode = mainState.debugMode,
                    overflowExpanded = inputOverflowExpanded,
                    onOverflowExpandedChange = {
                        inputOverflowExpanded = it
                        if (it) {
                            recentMessagesExpanded = false
                        }
                    },
                    recentMessagesExpanded = recentMessagesExpanded,
                    onRecentMessagesExpandedChange = {
                        recentMessagesExpanded = it
                        if (it) {
                            inputOverflowExpanded = false
                        }
                    },
                    onHelperTextHeightChange = { helperTextHeightPx = it },
                    isInSplitLayout = useWideSplitLayout,
                    isTheaterMode = useTheaterLayout,
                    showTheaterDockToggle = useTheaterLayout && canDockTheaterChat,
                    isTheaterChatDocked = streamVmState.isTheaterChatDocked,
                    onToggleTheaterChatMode = { streamViewModel.toggleTheaterChatMode() },
                    instantHide = isHistorySheet,
                    isRepeatedSendEnabled = mainState.isRepeatedSendEnabled,
                    overflowMenuMaxHeightDp = menuMaxHeightDp,
                    tourState =
                        remember(featureTourState.currentTourStep, featureTourState.forceOverflowOpen, featureTourState.isTourActive) {
                            TourOverlayState(
                                inputActionsTooltipState = if (featureTourState.currentTourStep == TourStep.InputActions) featureTourViewModel.inputActionsTooltipState else null,
                                overflowMenuTooltipState = if (featureTourState.currentTourStep == TourStep.OverflowMenu) featureTourViewModel.overflowMenuTooltipState else null,
                                configureActionsTooltipState = if (featureTourState.currentTourStep == TourStep.ConfigureActions) featureTourViewModel.configureActionsTooltipState else null,
                                swipeGestureTooltipState = if (featureTourState.currentTourStep == TourStep.SwipeGesture) featureTourViewModel.swipeGestureTooltipState else null,
                                forceOverflowOpen = featureTourState.forceOverflowOpen,
                                isTourActive =
                                    featureTourState.isTourActive ||
                                        featureTourState.postOnboardingStep is PostOnboardingStep.ToolbarPlusHint,
                                onAdvance = featureTourViewModel::advance,
                                onSkip = featureTourViewModel::skipTour,
                            )
                        },
                )
            }

            // Shared toolbar action handler
            val handleToolbarAction: (ToolbarAction) -> Unit = { action ->
                when (action) {
                    is ToolbarAction.SelectTab -> {
                        channelTabViewModel.selectTab(action.index)
                        scope.launch { composePagerState.scrollToPage(action.index) }
                    }

                    ToolbarAction.LongClickTab -> {
                        dialogViewModel.showManageChannels()
                    }

                    ToolbarAction.AddChannel -> {
                        featureTourViewModel.onAddedChannelFromToolbar()
                        dialogViewModel.showAddChannel()
                    }

                    ToolbarAction.OpenMentions -> {
                        sheetNavigationViewModel.openMentions()
                        channelTabViewModel.clearAllMentionCounts()
                    }

                    ToolbarAction.Login -> {
                        onLogin()
                    }

                    ToolbarAction.Relogin -> {
                        onRelogin()
                    }

                    ToolbarAction.Logout -> {
                        dialogViewModel.showLogout()
                    }

                    ToolbarAction.ManageChannels -> {
                        dialogViewModel.showManageChannels()
                    }

                    ToolbarAction.OpenChannel -> {
                        onOpenChannel()
                    }

                    ToolbarAction.RemoveChannel -> {
                        dialogViewModel.showRemoveChannel()
                    }

                    ToolbarAction.ReportChannel -> {
                        onReportChannel()
                    }

                    ToolbarAction.BlockChannel -> {
                        dialogViewModel.showBlockChannel()
                    }

                    ToolbarAction.ToggleChannelNotifications -> {
                        activeChannel?.let {
                            channelManagementViewModel.setChannelNotificationsEnabled(it, !channelNotificationsEnabled)
                        }
                    }

                    ToolbarAction.CaptureImage -> {
                        if (preferenceStore.hasExternalHostingAcknowledged) onCaptureImage() else dialogViewModel.setPendingUploadAction(onCaptureImage)
                    }

                    ToolbarAction.CaptureVideo -> {
                        if (preferenceStore.hasExternalHostingAcknowledged) onCaptureVideo() else dialogViewModel.setPendingUploadAction(onCaptureVideo)
                    }

                    ToolbarAction.ChooseMedia -> {
                        if (preferenceStore.hasExternalHostingAcknowledged) onChooseMedia() else dialogViewModel.setPendingUploadAction(onChooseMedia)
                    }

                    ToolbarAction.ReloadEmotes -> {
                        activeChannel?.let { channelManagementViewModel.reloadEmotes(it) }
                    }

                    ToolbarAction.Reconnect -> {
                        channelManagementViewModel.reconnect()
                    }

                    ToolbarAction.OpenSettings -> {
                        onNavigateToSettings()
                    }

                    ToolbarAction.TogglePinnedMessage -> {
                        activePinnedMessageViewModel?.toggleExpanded()
                    }
                }
            }

            // Shared floating toolbar
            var isToolbarMenuOpen by remember { mutableStateOf(false) }
            val floatingToolbar: @Composable (Modifier, Boolean, Boolean, Boolean) -> Unit = { toolbarModifier, visible, endAligned, showTabs ->
                FloatingToolbar(
                    tabState = tabState,
                    composePagerState = composePagerState,
                    showAppBar = effectiveShowAppBar && visible,
                    isFullscreen = isFullscreen,
                    isLoggedIn = isLoggedIn,
                    currentStream = currentStream,
                    isAudioOnly = isAudioOnly,
                    streamHeightDp = streamState.heightDp,
                    totalMentionCount = tabState.tabs.sumOf { it.mentionCount } + tabState.whisperMentionCount,
                    hasActivePinnedMessage = activePinnedMessageState != PinnedMessageUiState.Hidden,
                    isPinnedMessageShown = activePinnedMessageState is PinnedMessageUiState.Expanded,
                    channelNotificationsEnabled = channelNotificationsEnabled,
                    onAction = handleToolbarAction,
                    onAudioOnly = { streamViewModel.toggleAudioOnly() },
                    onStreamClose = { streamViewModel.closeStream() },
                    endAligned = endAligned,
                    showTabs = showTabs,
                    addChannelTooltipState = if (featureTourState.postOnboardingStep is PostOnboardingStep.ToolbarPlusHint) featureTourViewModel.addChannelTooltipState else null,
                    onAddChannelTooltipDismiss = featureTourViewModel::onToolbarHintDismissed,
                    onSkipTour = featureTourViewModel::skipTour,
                    menuMaxHeightDp = menuMaxHeightDp,
                    onToolbarBottomChange = { toolbarBottomPx = it },
                    isEmoteMenuOpen = inputState.isEmoteMenuOpen,
                    onCloseEmoteMenu = { chatInputViewModel.setEmoteMenuOpen(false) },
                    onMenuVisibleChange = { isToolbarMenuOpen = it },
                    streamToolbarAlpha = { streamState.effectiveAlpha },
                    modifier = toolbarModifier,
                )
            }

            // Shared emote menu layer
            val emoteMenuLayer: @Composable (Modifier) -> Unit = { menuModifier ->
                EmoteMenuOverlay(
                    isVisible = inputState.isEmoteMenuOpen,
                    totalMenuHeight = totalMenuHeight,
                    backProgress = backProgress,
                    onEmoteClick = { code, id ->
                        chatInputViewModel.insertEmote(code)
                        chatInputViewModel.addEmoteUsage(id)
                    },
                    onBackspace = chatInputViewModel::deleteLastWord,
                    modifier = menuModifier,
                )
            }

            // Shared pager callbacks
            val chatPagerCallbacks =
                remember {
                    ChatPagerCallbacks(
                        onOpenReplies = sheetNavigationViewModel::openReplies,
                        onRecover = {
                            mainScreenViewModel.recoverInputAndFullscreen()
                        },
                        onScrollToBottom = { mainScreenViewModel.setGestureToolbarHidden(false) },
                        onTourAdvance = featureTourViewModel::advance,
                        onTourSkip = featureTourViewModel::skipTour,
                        scrollConnection = toolbarTracker,
                    )
                }

            // Shared scaffold content (pager)
            val fabActionHandler: (InputAction) -> Unit =
                remember {
                    { action ->
                        val channel =
                            channelTabViewModel.uiState.value.let { state ->
                                state.tabs.getOrNull(state.selectedIndex)?.channel
                            }
                        when (action) {
                            InputAction.Search -> {
                                channel?.let { sheetNavigationViewModel.openHistory(HistoryChannel.Channel(it)) }
                            }

                            InputAction.LastMessage -> {
                                chatInputViewModel.getLastMessage()
                            }

                            InputAction.Stream -> {
                                val stream = streamViewModel.streamState.value.currentStream
                                when {
                                    stream != null -> streamViewModel.closeStream()
                                    else -> channel?.let { streamViewModel.toggleStream(it) }
                                }
                            }

                            InputAction.ModActions -> {
                                inputState.activeChannel?.let { modActionsViewModel.show(it) }
                            }

                            InputAction.Fullscreen -> {
                                mainScreenViewModel.toggleFullscreen()
                            }

                            InputAction.Theater -> {
                                streamViewModel.toggleTheaterMode()
                            }

                            InputAction.HideInput -> {
                                mainScreenViewModel.toggleInput()
                                chatInputViewModel.setEmoteMenuOpen(false)
                            }

                            InputAction.Debug -> {
                                sheetNavigationViewModel.openDebugInfo()
                            }
                        }
                    }
                }
            val fabMenuCallbacks =
                FabMenuCallbacks(
                    onAction = fabActionHandler,
                    onAudioOnly = { streamViewModel.toggleAudioOnly() },
                    isStreamActive = currentStream != null,
                    isAudioOnly = isAudioOnly,
                    hasStreamData = hasStreamData,
                    isFullscreen = isFullscreen,
                    isModerator = mainScreenViewModel.isModeratorInChannel(inputState.activeChannel),
                    debugMode = mainState.debugMode,
                    enabled = inputState.enabled,
                    hasLastMessage = inputState.hasLastMessage,
                )

            val scaffoldContent: @Composable (PaddingValues, Dp, Boolean) -> Unit = { paddingValues, chatTopPadding, toolbarVisible ->
                MainScreenPagerContent(
                    paddingValues = paddingValues,
                    chatTopPadding = chatTopPadding,
                    tabState = tabState,
                    composePagerState = composePagerState,
                    pagerState = pagerState,
                    isLoggedIn = isLoggedIn,
                    showInput = showInput,
                    isFullscreen = isFullscreen,
                    swipeNavigation = swipeNavigation,
                    isSheetOpen = isSheetOpen,
                    inputHeightDp = inputHeightDp,
                    helperTextHeightDp = helperTextHeightDp,
                    navBarHeightDp = navBarHeightDp,
                    effectiveRoundedCorner = effectiveRoundedCorner,
                    scrollTargets = scrollTargets.toImmutableMap(),
                    onClearScrollTarget = { scrollTargets.remove(it) },
                    callbacks = chatPagerCallbacks,
                    fabMenuCallbacks = fabMenuCallbacks,
                    showPinnedMessage = !mainState.gestureToolbarHidden && toolbarVisible,
                    isToolbarMenuOpen = isToolbarMenuOpen,
                    currentTourStep = featureTourState.currentTourStep,
                    recoveryFabTooltipState = featureTourViewModel.recoveryFabTooltipState,
                    onAddChannel = dialogViewModel::showAddChannel,
                    onLogin = onLogin,
                )
            }

            // Shared fullscreen sheet overlay
            val fullScreenSheetOverlay: @Composable () -> Unit = {
                // Per-frame ime read scoped to this overlay, and only while a sheet is visible
                val scaffoldBottomDp = when {
                    isSheetOpen -> with(density) { scaffoldBottomInsets.getBottom(this).toDp() }
                    else -> 0.dp
                }
                val bottomPadding = inputHeightDp + scaffoldBottomDp
                val effectiveBottomPadding =
                    when {
                        !showInput -> bottomPadding + max(navBarHeightDp, effectiveRoundedCorner)
                        else -> bottomPadding
                    }
                FullScreenSheetOverlay(
                    sheetState = fullScreenSheetState,
                    mentionViewModel = mentionViewModel,
                    onDismiss = sheetNavigationViewModel::closeFullScreenSheet,
                    onDismissReplies = {
                        sheetNavigationViewModel.closeFullScreenSheet()
                        chatInputViewModel.setReplying(false)
                    },
                    onWhisperReply = chatInputViewModel::setWhisperTarget,
                    bottomContentPadding = effectiveBottomPadding,
                )
            }

            // Collected inside the slot so suggestion updates only recompose the dropdown
            val suggestionDropdown: @Composable (Modifier) -> Unit = { dropdownModifier ->
                val suggestions by chatInputViewModel.suggestions.collectAsStateWithLifecycle()
                SuggestionDropdown(
                    suggestions = suggestions,
                    onSuggestionClick = chatInputViewModel::applySuggestion,
                    availableMaxHeightDp = menuMaxHeightDp,
                    modifier = dropdownModifier,
                )
            }

            val onStreamClose = {
                keyboardController?.hide()
                focusManager.clearFocus()
                streamViewModel.closeStream()
            }
            val onAudioOnly = { streamViewModel.toggleAudioOnly() }

            // The theater chat follows the finger during drags and settles to the nearest edge
            // on release, with the settled position mirrored into the ViewModel. The panel width
            // is the drag range and depends on the theater chat mode, so the layout reports it.
            val theaterChatPanelWidthPx = remember { mutableFloatStateOf(with(density) { THEATER_CHAT_WIDTH.toPx() }) }
            val theaterChatOffset =
                remember {
                    Animatable(
                        when {
                            streamVmState.isTheaterChatVisible -> 0f
                            else -> theaterChatPanelWidthPx.floatValue
                        },
                    )
                }
            LaunchedEffect(streamVmState.isTheaterChatVisible, theaterChatPanelWidthPx.floatValue) {
                val target =
                    when {
                        streamVmState.isTheaterChatVisible -> 0f
                        else -> theaterChatPanelWidthPx.floatValue
                    }
                theaterChatOffset.animateTo(target)
            }
            val theaterChatTravelThresholdPx = remember(density) { with(density) { 32.dp.toPx() } }
            val (onTheaterChatDrag, onTheaterChatDragEnd) =
                remember {
                    // A short decisive drag settles in its direction, only ambiguous gestures
                    // fall back to the nearest edge
                    var gestureTravel = 0f
                    val onDrag: (Float) -> Unit = { delta ->
                        gestureTravel += delta
                        scope.launch {
                            theaterChatOffset.snapTo((theaterChatOffset.value + delta).coerceIn(0f, theaterChatPanelWidthPx.floatValue))
                        }
                    }
                    val onDragEnd: () -> Unit = {
                        val show =
                            when {
                                gestureTravel < -theaterChatTravelThresholdPx -> true
                                gestureTravel > theaterChatTravelThresholdPx -> false
                                else -> theaterChatOffset.value < theaterChatPanelWidthPx.floatValue / 2f
                            }
                        gestureTravel = 0f
                        scope.launch {
                            theaterChatOffset.animateTo(
                                when {
                                    show -> 0f
                                    else -> theaterChatPanelWidthPx.floatValue
                                },
                            )
                        }
                        streamViewModel.setTheaterChatVisible(show)
                    }
                    onDrag to onDragEnd
                }

            // Moving between layouts must not dispose the stream — a disposed StreamView rips
            // the WebView out of its new parent and kills playback
            val streamView =
                remember {
                    movableContentOf { config: StreamViewConfig, streamModifier: Modifier ->
                        StreamView(
                            channel = config.channel,
                            isInPipMode = config.isInPipMode,
                            fillPane = config.fillPane,
                            isTheaterMode = config.isTheaterMode,
                            isTheaterChatVisible = config.isTheaterChatVisible,
                            overlayEndPadding = config.overlayEndPadding,
                            onClose = onStreamClose,
                            onAudioOnly = onAudioOnly,
                            onToggleTheater = streamViewModel::toggleTheaterMode,
                            onToggleTheaterChat = streamViewModel::toggleTheaterChat,
                            onTheaterChatDrag = onTheaterChatDrag,
                            onTheaterChatDragEnd = onTheaterChatDragEnd,
                            onTheaterDoubleTap = {
                                if (config.canDockTheaterChat && config.isTheaterChatVisible) {
                                    streamViewModel.toggleTheaterChatMode()
                                }
                            },
                            modifier = streamModifier,
                        )
                    }
                }

            if (theaterStream != null) {
                TheaterLayout(
                    currentStream = theaterStream,
                    isChatVisible = streamVmState.isTheaterChatVisible,
                    isChatDocked = streamVmState.isTheaterChatDocked,
                    dockedChatWidth = theaterDockedChatWidth,
                    canDockChat = canDockTheaterChat,
                    onToggleChatMode = { streamViewModel.toggleTheaterChatMode() },
                    onChatPanelWidthChange = { theaterChatPanelWidthPx.floatValue = it },
                    showInput = showInput,
                    isKeyboardVisible = isKeyboardVisible,
                    isSheetOpen = isSheetOpen,
                    isInputMultiline = isInputMultiline,
                    isEmoteMenuOpen = inputState.isEmoteMenuOpen,
                    inputHeightDp = inputHeightDp,
                    helperTextHeightDp = helperTextHeightDp,
                    swipeDownThresholdPx = swipeDownThresholdPx,
                    scaffoldBottomInsets = scaffoldBottomInsets,
                    chatOffsetX = { theaterChatOffset.value },
                    onChatDrag = onTheaterChatDrag,
                    onChatDragEnd = onTheaterChatDragEnd,
                    onHideInput = { mainScreenViewModel.hideInput() },
                    onOpenReplies = sheetNavigationViewModel::openReplies,
                    onRecover = { mainScreenViewModel.recoverInputAndFullscreen() },
                    streamView = streamView,
                    bottomBar = bottomBar,
                    emoteMenuLayer = emoteMenuLayer,
                    fullScreenSheetOverlay = fullScreenSheetOverlay,
                    suggestionDropdown = suggestionDropdown,
                    snackbarHostState = snackbarHostState,
                    modifier = modifier,
                )
            } else if (useWideSplitLayout) {
                WideSplitLayout(
                    currentStream = currentStream,
                    isAudioOnly = isAudioOnly,
                    streamView = streamView,
                    scaffoldContent = scaffoldContent,
                    floatingToolbar = floatingToolbar,
                    fullScreenSheetOverlay = fullScreenSheetOverlay,
                    bottomBar = bottomBar,
                    emoteMenuLayer = emoteMenuLayer,
                    snackbarHostState = snackbarHostState,
                    scaffoldBottomInsets = scaffoldBottomInsets,
                    inputHeightDp = inputHeightDp,
                    isFullscreen = isFullscreen,
                    gestureToolbarHidden = mainState.gestureToolbarHidden,
                    isKeyboardVisible = isKeyboardVisible,
                    isEmoteMenuOpen = inputState.isEmoteMenuOpen,
                    isSheetOpen = isSheetOpen,
                    isToolbarMenuOpen = isToolbarMenuOpen,
                    showInput = showInput,
                    isInputMultiline = isInputMultiline,
                    inputPopupExpanded = inputOverflowExpanded || recentMessagesExpanded,
                    forceOverflowOpen = featureTourState.forceOverflowOpen,
                    swipeDownThresholdPx = swipeDownThresholdPx,
                    suggestionDropdown = suggestionDropdown,
                    onHideInput = { mainScreenViewModel.hideInput() },
                    onDismissInputPopup = {
                        inputOverflowExpanded = false
                        recentMessagesExpanded = false
                    },
                    modifier = modifier,
                )
            } else {
                NormalStackedLayout(
                    currentStream = currentStream,
                    isAudioOnly = isAudioOnly,
                    isInputMultiline = isInputMultiline,
                    streamView = streamView,
                    hasWebViewBeenAttached = streamViewModel.hasWebViewBeenAttached,
                    streamState = streamState,
                    scaffoldContent = scaffoldContent,
                    floatingToolbar = floatingToolbar,
                    fullScreenSheetOverlay = fullScreenSheetOverlay,
                    bottomBar = bottomBar,
                    emoteMenuLayer = emoteMenuLayer,
                    snackbarHostState = snackbarHostState,
                    scaffoldBottomInsets = scaffoldBottomInsets,
                    scaffoldBottomTargetDp = scaffoldBottomTargetDp,
                    inputHeightDp = inputHeightDp,
                    isFullscreen = isFullscreen,
                    gestureToolbarHidden = mainState.gestureToolbarHidden,
                    isKeyboardVisible = isKeyboardVisible,
                    isEmoteMenuOpen = inputState.isEmoteMenuOpen,
                    isSheetOpen = isSheetOpen,
                    isInPipMode = isInPipMode,
                    containerWidthPx = containerWidthPx,
                    containerHeightPx = containerHeightPx,
                    fontSize = mainState.fontSize,
                    showInput = showInput,
                    inputPopupExpanded = inputOverflowExpanded || recentMessagesExpanded,
                    forceOverflowOpen = featureTourState.forceOverflowOpen,
                    swipeDownThresholdPx = swipeDownThresholdPx,
                    suggestionDropdown = suggestionDropdown,
                    onHideInput = { mainScreenViewModel.hideInput() },
                    onDismissInputPopup = {
                        inputOverflowExpanded = false
                        recentMessagesExpanded = false
                    },
                    modifier = modifier,
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MainScreenPagerEffects(
    composePagerState: PagerState,
    pagerState: ChannelPagerUiState,
    onSetActivePage: (Int) -> Unit,
    onClearNotifications: (Int) -> Unit,
    onShowToolbar: () -> Unit,
) {
    // Sync Compose pager with ViewModel state
    LaunchedEffect(pagerState.currentPage, pagerState.channels.size) {
        if (!composePagerState.isScrollInProgress &&
            composePagerState.currentPage != pagerState.currentPage &&
            pagerState.currentPage in 0 until composePagerState.pageCount
        ) {
            composePagerState.scrollToPage(pagerState.currentPage)
        }
    }

    // Eagerly update active channel on page change for snappy UI (room state, stream info)
    LaunchedEffect(composePagerState.currentPage) {
        if (composePagerState.currentPage != pagerState.currentPage) {
            onSetActivePage(composePagerState.currentPage)
        }
    }

    // Clear unread/mention indicators when page settles
    LaunchedEffect(composePagerState.settledPage) {
        onClearNotifications(composePagerState.settledPage)
    }

    // Pager swipe reveals toolbar
    LaunchedEffect(composePagerState.isScrollInProgress) {
        if (composePagerState.isScrollInProgress) {
            onShowToolbar()
        }
    }
}

@Composable
private fun MainScreenTourEffects(
    featureTourViewModel: FeatureTourViewModel,
    featureTourState: FeatureTourUiState,
    mainScreenViewModel: MainScreenViewModel,
    mainState: MainScreenUiState,
    channelsReady: Boolean,
    channelsEmpty: Boolean,
) {
    // Notify tour VM when channel state changes
    LaunchedEffect(channelsReady, channelsEmpty) {
        featureTourViewModel.onChannelsChanged(empty = channelsEmpty, ready = channelsReady)
    }

    // Drive tooltip dismissals and tour start from the typed step
    LaunchedEffect(featureTourState.postOnboardingStep) {
        when (featureTourState.postOnboardingStep) {
            PostOnboardingStep.FeatureTour -> {
                featureTourViewModel.addChannelTooltipState.dismiss()
                featureTourViewModel.startTour()
            }

            PostOnboardingStep.Complete, PostOnboardingStep.Idle -> {
                featureTourViewModel.addChannelTooltipState.dismiss()
            }

            PostOnboardingStep.ToolbarPlusHint -> Unit
        }
    }

    // Sync tour's input hidden state with MainScreenViewModel
    LaunchedEffect(featureTourState.gestureInputHidden, featureTourState.isTourActive) {
        if (featureTourState.isTourActive) {
            when {
                featureTourState.gestureInputHidden -> mainScreenViewModel.hideInput()
                else -> mainScreenViewModel.recoverInputAndFullscreen()
            }
        }
    }

    // Auto-advance tour when input is hidden during the SwipeGesture step
    LaunchedEffect(mainState.showInput, featureTourState.currentTourStep) {
        if (!mainState.showInput && featureTourState.currentTourStep == TourStep.SwipeGesture) {
            featureTourViewModel.advance()
        }
    }

    // Keep toolbar visible during tour
    LaunchedEffect(featureTourState.isTourActive, mainState.gestureToolbarHidden) {
        if (featureTourState.isTourActive && mainState.gestureToolbarHidden) {
            mainScreenViewModel.setGestureToolbarHidden(false)
        }
    }
}

@Composable
private fun MainScreenFocusEffects(
    imeHeight: androidx.compose.runtime.State<Int>,
    isEmoteMenuOpen: Boolean,
    currentStream: UserName?,
) {
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val emoteMenuOpenState = rememberUpdatedState(isEmoteMenuOpen)

    // Clear focus when keyboard fully reaches the bottom and emote menu is closed.
    // Uses rememberUpdatedState so snapshotFlow reads the latest emote menu state.
    // Debounced to avoid premature focus loss during transitions.
    LaunchedEffect(Unit) {
        snapshotFlow { imeHeight.value == 0 && !emoteMenuOpenState.value }
            .debounce(150)
            .distinctUntilChanged()
            .collect { shouldClearFocus ->
                if (shouldClearFocus) {
                    focusManager.clearFocus()
                }
            }
    }

    // Clear focus after stream closes — the layout shift from removing StreamView
    // can cause the TextField to regain focus and open the keyboard.
    LaunchedEffect(currentStream) {
        if (currentStream == null) {
            keyboardController?.hide()
            focusManager.clearFocus()
        }
    }
}

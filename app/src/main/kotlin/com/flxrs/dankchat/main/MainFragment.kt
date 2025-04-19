package com.flxrs.dankchat.main

import android.annotation.SuppressLint
import android.app.Activity
import android.app.PictureInPictureParams
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.res.ColorStateList
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.text.InputType
import android.text.SpannableStringBuilder
import android.text.method.LinkMovementMethod
import android.text.util.Linkify
import android.util.LayoutDirection
import android.util.Rational
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.MotionEvent
import android.view.RoundedCorner
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.MarginLayoutParams
import android.view.inputmethod.EditorInfo
import android.webkit.MimeTypeMap
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.BackEventCompat
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.core.content.ContextCompat.getSystemService
import androidx.core.content.FileProvider
import androidx.core.net.toFile
import androidx.core.net.toUri
import androidx.core.view.MenuProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsAnimationCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsCompat.Type
import androidx.core.view.doOnPreDraw
import androidx.core.view.isVisible
import androidx.core.view.postDelayed
import androidx.core.view.updateLayoutParams
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentContainerView
import androidx.fragment.app.commitNow
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import androidx.navigation.fragment.findNavController
import androidx.viewpager2.widget.ViewPager2
import com.flxrs.dankchat.BuildConfig
import com.flxrs.dankchat.DankChatViewModel
import com.flxrs.dankchat.R
import com.flxrs.dankchat.ValidationResult
import com.flxrs.dankchat.chat.ChatTabAdapter
import com.flxrs.dankchat.chat.FullScreenSheetState
import com.flxrs.dankchat.chat.InputSheetState
import com.flxrs.dankchat.chat.emote.EmoteSheetFragment
import com.flxrs.dankchat.chat.emote.EmoteSheetResult
import com.flxrs.dankchat.chat.emotemenu.EmoteMenuFragment
import com.flxrs.dankchat.chat.mention.MentionFragment
import com.flxrs.dankchat.chat.message.MessageSheetResult
import com.flxrs.dankchat.chat.message.MoreActionsMessageSheetResult
import com.flxrs.dankchat.chat.replies.RepliesFragment
import com.flxrs.dankchat.chat.replies.ReplyInputSheetFragment
import com.flxrs.dankchat.chat.suggestion.SpaceTokenizer
import com.flxrs.dankchat.chat.suggestion.Suggestion
import com.flxrs.dankchat.chat.suggestion.SuggestionsArrayAdapter
import com.flxrs.dankchat.chat.user.UserPopupResult
import com.flxrs.dankchat.data.DisplayName
import com.flxrs.dankchat.data.UserId
import com.flxrs.dankchat.data.UserName
import com.flxrs.dankchat.data.state.DataLoadingState
import com.flxrs.dankchat.data.state.ImageUploadState
import com.flxrs.dankchat.data.toUserId
import com.flxrs.dankchat.data.toUserName
import com.flxrs.dankchat.data.twitch.badge.Badge
import com.flxrs.dankchat.data.twitch.emote.ChatMessageEmote
import com.flxrs.dankchat.databinding.EditDialogBinding
import com.flxrs.dankchat.databinding.MainFragmentBinding
import com.flxrs.dankchat.preferences.DankChatPreferenceStore
import com.flxrs.dankchat.preferences.chat.ChatSettingsDataStore
import com.flxrs.dankchat.preferences.developer.DeveloperSettingsDataStore
import com.flxrs.dankchat.preferences.model.ChannelWithRename
import com.flxrs.dankchat.preferences.notifications.NotificationsSettingsDataStore
import com.flxrs.dankchat.preferences.tools.ToolsSettingsDataStore
import com.flxrs.dankchat.utils.createMediaFile
import com.flxrs.dankchat.utils.extensions.awaitState
import com.flxrs.dankchat.utils.extensions.collectFlow
import com.flxrs.dankchat.utils.extensions.expand
import com.flxrs.dankchat.utils.extensions.firstValueOrNull
import com.flxrs.dankchat.utils.extensions.hide
import com.flxrs.dankchat.utils.extensions.hideKeyboard
import com.flxrs.dankchat.utils.extensions.isCollapsed
import com.flxrs.dankchat.utils.extensions.isHidden
import com.flxrs.dankchat.utils.extensions.isInPictureInPictureMode
import com.flxrs.dankchat.utils.extensions.isLandscape
import com.flxrs.dankchat.utils.extensions.isPortrait
import com.flxrs.dankchat.utils.extensions.isVisible
import com.flxrs.dankchat.utils.extensions.navigateSafe
import com.flxrs.dankchat.utils.extensions.px
import com.flxrs.dankchat.utils.extensions.reduceDragSensitivity
import com.flxrs.dankchat.utils.extensions.withData
import com.flxrs.dankchat.utils.extensions.withTrailingSpace
import com.flxrs.dankchat.utils.extensions.withoutInvisibleChar
import com.flxrs.dankchat.utils.insets.ControlFocusInsetsAnimationCallback
import com.flxrs.dankchat.utils.insets.RootViewDeferringInsetsCallback
import com.flxrs.dankchat.utils.insets.TranslateDeferringInsetsAnimationCallback
import com.flxrs.dankchat.utils.removeExifAttributes
import com.flxrs.dankchat.utils.showErrorDialog
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.BaseTransientBottomBar
import com.google.android.material.snackbar.BaseTransientBottomBar.BaseCallback
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.tabs.TabLayoutMediator
import com.google.android.material.transition.MaterialSharedAxis
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.activityViewModel
import org.koin.androidx.viewmodel.ext.android.viewModel
import java.io.File
import java.io.IOException
import java.net.URL
import kotlin.math.roundToInt

class MainFragment : Fragment() {

    private val mainViewModel: MainViewModel by viewModel()
    private val dankChatViewModel: DankChatViewModel by activityViewModel()
    private val chatSettingsDataStore: ChatSettingsDataStore by inject()
    private val developerSettingsDataStore: DeveloperSettingsDataStore by inject()
    private val toolsSettingsDataStore: ToolsSettingsDataStore by inject()
    private val notificationsSettingsDataStore: NotificationsSettingsDataStore by inject()
    private val dankChatPreferences: DankChatPreferenceStore by inject()
    private val navController: NavController by lazy { findNavController() }
    private var bindingRef: MainFragmentBinding? = null
    private val binding get() = bindingRef!!

    private var inputBottomSheetBehavior: BottomSheetBehavior<FragmentContainerView>? = null
    private var fullscreenBottomSheetBehavior: BottomSheetBehavior<FragmentContainerView>? = null
    private val onBackPressedCallback = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            when {
                inputBottomSheetBehavior?.isVisible == true      -> inputBottomSheetBehavior?.handleBackInvoked()
                fullscreenBottomSheetBehavior?.isVisible == true -> fullscreenBottomSheetBehavior?.handleBackInvoked()
                mainViewModel.isFullscreen                       -> mainViewModel.toggleFullscreen()
            }
        }

        override fun handleOnBackProgressed(backEvent: BackEventCompat) {
            when {
                inputBottomSheetBehavior?.isVisible == true      -> inputBottomSheetBehavior?.updateBackProgress(backEvent)
                fullscreenBottomSheetBehavior?.isVisible == true -> fullscreenBottomSheetBehavior?.updateBackProgress(backEvent)
            }
        }

        override fun handleOnBackCancelled() {
            when {
                inputBottomSheetBehavior?.isVisible == true      -> inputBottomSheetBehavior?.cancelBackProgress()
                fullscreenBottomSheetBehavior?.isVisible == true -> fullscreenBottomSheetBehavior?.cancelBackProgress()
            }
        }

        override fun handleOnBackStarted(backEvent: BackEventCompat) {
            when {
                inputBottomSheetBehavior?.isVisible == true      -> inputBottomSheetBehavior?.startBackProgress(backEvent)
                fullscreenBottomSheetBehavior?.isVisible == true -> fullscreenBottomSheetBehavior?.startBackProgress(backEvent)
            }
        }
    }

    private val pageChangeCallback = object : ViewPager2.OnPageChangeCallback() {
        override fun onPageSelected(position: Int) {
            closeInputSheets()
            val newChannel = tabAdapter[position] ?: return
            mainViewModel.setActiveChannel(newChannel)
        }

        override fun onPageScrollStateChanged(state: Int) = closeInputSheets()
    }

    private val menuProvider = object : MenuProvider {
        override fun onPrepareMenu(menu: Menu) {
            with(menu) {
                val isLoggedIn = dankChatPreferences.isLoggedIn
                val shouldShowProgress = mainViewModel.shouldShowUploadProgress.value
                val hasChannels = mainViewModel.getChannels().isNotEmpty()
                val mentionIconColor = when (mainViewModel.shouldColorNotification.value) {
                    true -> R.attr.colorError
                    else -> R.attr.colorControlHighlight
                }
                findItem(R.id.menu_login)?.isVisible = !isLoggedIn
                findItem(R.id.menu_account)?.isVisible = isLoggedIn
                findItem(R.id.menu_manage)?.isVisible = hasChannels
                findItem(R.id.menu_channel)?.isVisible = hasChannels
                findItem(R.id.menu_open_channel)?.isVisible = hasChannels
                findItem(R.id.menu_block_channel)?.isVisible = isLoggedIn
                findItem(R.id.menu_mentions)?.apply {
                    isVisible = hasChannels
                    context?.let {
                        val fallback = ContextCompat.getColor(it, android.R.color.white)
                        val color = MaterialColors.getColor(it, mentionIconColor, fallback)
                        icon?.setTintList(ColorStateList.valueOf(color))
                    }
                }

                findItem(R.id.progress)?.apply {
                    isVisible = shouldShowProgress
                    actionView = ProgressBar(requireContext()).apply {
                        indeterminateTintList = ColorStateList.valueOf(MaterialColors.getColor(this, R.attr.colorOnSurfaceVariant))
                        isVisible = shouldShowProgress
                    }
                }
            }
        }

        override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
            menuInflater.inflate(R.menu.menu, menu)
        }

        override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
            when (menuItem.itemId) {
                R.id.menu_reconnect                -> mainViewModel.reconnect()
                R.id.menu_login, R.id.menu_relogin -> openLogin()
                R.id.menu_logout                   -> showLogoutConfirmationDialog()
                R.id.menu_add                      -> navigateSafe(R.id.action_mainFragment_to_addChannelDialogFragment).also { closeInputSheets() }
                R.id.menu_mentions                 -> openMentionSheet()
                R.id.menu_open_channel             -> openChannel()
                R.id.menu_remove_channel           -> removeChannel()
                R.id.menu_report_channel           -> reportChannel()
                R.id.menu_block_channel            -> blockChannel()
                R.id.menu_manage                   -> openManageChannelsDialog()
                R.id.menu_reload_emotes            -> reloadEmotes()
                R.id.menu_choose_media             -> showExternalHostingUploadDialogIfNotAcknowledged { requestGalleryMedia.launch(PickVisualMediaRequest(PickVisualMedia.ImageAndVideo)) }
                R.id.menu_capture_image            -> startCameraCapture()
                R.id.menu_capture_video            -> startCameraCapture(captureVideo = true)
                R.id.menu_clear                    -> clear()
                R.id.menu_settings                 -> navigateSafe(R.id.action_mainFragment_to_overviewSettingsFragment).also { hideKeyboard() }
                else                               -> return false
            }
            return true
        }
    }

    private fun closeInputSheets() {
        mainViewModel.closeInputSheet(keepPreviousReply = false)
        inputBottomSheetBehavior?.hide()
        binding.input.dismissDropDown()
    }

    private lateinit var tabAdapter: ChatTabAdapter
    private lateinit var tabLayoutMediator: TabLayoutMediator
    private lateinit var suggestionAdapter: SuggestionsArrayAdapter
    private var currentMediaUri = Uri.EMPTY
    private val tabSelectionListener = TabSelectionListener()

    private val requestImageCapture = registerForActivityResult(StartActivityForResult()) { if (it.resultCode == Activity.RESULT_OK) handleCaptureRequest(imageCapture = true) }
    private val requestVideoCapture = registerForActivityResult(StartActivityForResult()) { if (it.resultCode == Activity.RESULT_OK) handleCaptureRequest(imageCapture = false) }
    private val requestGalleryMedia = registerForActivityResult(PickVisualMedia()) { uri ->
        uri ?: return@registerForActivityResult
        val contentResolver = activity?.contentResolver ?: return@registerForActivityResult
        val context = context ?: return@registerForActivityResult
        val mimeType = contentResolver.getType(uri)
        val mimeTypeMap = MimeTypeMap.getSingleton()
        val extension = mimeTypeMap.getExtensionFromMimeType(mimeType)
        if (extension == null) {
            showSnackBar(getString(R.string.snackbar_upload_failed))
            return@registerForActivityResult
        }

        val copy = createMediaFile(context, extension)
        try {
            contentResolver.openInputStream(uri)?.run { copy.outputStream().use { copyTo(it) } }
            if (copy.extension == "jpg" || copy.extension == "jpeg") {
                copy.removeExifAttributes()
            }

            mainViewModel.uploadMedia(copy, imageCapture = false)
        } catch (_: Throwable) {
            copy.delete()
            showSnackBar(getString(R.string.snackbar_upload_failed))
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        exitTransition = MaterialSharedAxis(MaterialSharedAxis.X, true)
        reenterTransition = MaterialSharedAxis(MaterialSharedAxis.X, false)
        enterTransition = MaterialSharedAxis(MaterialSharedAxis.X, true)
        returnTransition = MaterialSharedAxis(MaterialSharedAxis.X, false)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        tabAdapter = ChatTabAdapter(parentFragment = this)
        bindingRef = MainFragmentBinding.inflate(inflater, container, false).apply {
            updatePictureInPictureVisibility()

            inputBottomSheetBehavior = BottomSheetBehavior.from(inputSheetFragment).apply {
                addBottomSheetCallback(inputSheetCallback)
                skipCollapsed = true
            }
            chatViewpager.setup()
            input.setup(this)

            fullscreenBottomSheetBehavior = BottomSheetBehavior.from(fullScreenSheetFragment).apply { setupFullScreenSheet() }

            tabLayoutMediator = TabLayoutMediator(tabs, chatViewpager) { tab, position ->
                tab.text = tabAdapter.getFormattedChannel(position)
            }

            tabs.setInitialColors()

            addChannelsButton.setOnClickListener { navigateSafe(R.id.action_mainFragment_to_addChannelDialogFragment) }
            toggleFullscreen.setOnClickListener { mainViewModel.toggleFullscreen() }
            toggleInput.setOnClickListener { mainViewModel.toggleInput() }
            toggleStream.setOnClickListener {
                mainViewModel.toggleStream()
                root.requestApplyInsets()
            }
            changeRoomstate.setOnClickListener { showRoomStateDialog() }
            showChips.setOnClickListener { mainViewModel.toggleChipsExpanded() }
            var offset = 0f
            splitThumb?.setOnTouchListener { v, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_MOVE -> {
                        val guideline = splitGuideline ?: return@setOnTouchListener false
                        val centered = event.rawX + offset + (v.width / 2f)
                        guideline.updateLayoutParams<ConstraintLayout.LayoutParams> {
                            guidePercent = (centered / root.width).coerceIn(MIN_GUIDELINE_PERCENT, MAX_GUIDELINE_PERCENT)
                        }
                        true
                    }

                    MotionEvent.ACTION_DOWN -> {
                        offset = v.x - event.rawX
                        true
                    }

                    else                    -> false
                }
            }
        }

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        postponeEnterTransition()
        view.setBackgroundColor(MaterialColors.getColor(view, android.R.attr.colorBackground))
        view.doOnPreDraw { startPostponedEnterTransition() }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            activity?.setPictureInPictureParams(
                PictureInPictureParams.Builder()
                    .setAspectRatio(Rational(16, 9))
                    .build()
            )
        }

        initPreferences()
        binding.splitThumb?.background?.alpha = 150
        activity?.addMenuProvider(menuProvider, viewLifecycleOwner, Lifecycle.State.STARTED)
        mainViewModel.apply {
            setIsLandscape(isLandscape)
            collectFlow(imageUploadState, ::handleImageUploadState)
            collectFlow(dataLoadingState, ::handleDataLoadingState)
            collectFlow(shouldShowUploadProgress) { activity?.invalidateMenu() }
            collectFlow(suggestions, ::setSuggestions)
            collectFlow(isFullscreenFlow) { changeActionBarVisibility(it) }
            collectFlow(shouldShowInput) {
                binding.inputLayout.isVisible = it
            }
            collectFlow(canType) {
                binding.inputLayout.isEnabled = it
                binding.input.isEnabled = it
                when {
                    it   -> binding.inputLayout.setupSendButton()
                    else -> with(binding.inputLayout) {
                        endIconDrawable = null
                        setEndIconTouchListener(null)
                        setEndIconOnClickListener(null)
                        setEndIconOnLongClickListener(null)
                    }
                }
            }
            collectFlow(inputState) { state ->
                binding.inputLayout.hint = when (state) {
                    InputState.Default      -> getString(R.string.hint_connected)
                    InputState.Replying     -> getString(R.string.hint_replying)
                    InputState.NotLoggedIn  -> getString(R.string.hint_not_logged_int)
                    InputState.Disconnected -> getString(R.string.hint_disconnected)
                }
            }
            collectFlow(bottomTextState) { (enabled, text) ->
                binding.inputLayout.helperText = text
                binding.inputLayout.isHelperTextEnabled = enabled
                binding.fullscreenHintText.text = text
            }
            collectFlow(activeChannel) { channel ->
                channel ?: return@collectFlow
                (activity as? MainActivity)?.notificationService?.setActiveChannel(channel) // TODO move
                val index = tabAdapter.indexOfChannel(channel)
                binding.tabs.getTabAt(index)?.removeBadge()
                mainViewModel.clearMentionCount(channel)
                mainViewModel.clearUnreadMessage(channel)
            }
            collectFlow(shouldShowTabs) { binding.tabs.isVisible = it && !isInPictureInPictureMode }
            collectFlow(shouldShowChipToggle) { binding.showChips.isVisible = it }
            collectFlow(areChipsExpanded) {
                val resourceId = if (it) R.drawable.ic_keyboard_arrow_up else R.drawable.ic_keyboard_arrow_down
                binding.showChips.setChipIconResource(resourceId)
            }
            collectFlow(shouldShowExpandedChips) {
                binding.toggleFullscreen.isVisible = it
                binding.toggleInput.isVisible = it
            }
            collectFlow(shouldShowStreamToggle) { binding.toggleStream.isVisible = it }
            collectFlow(hasModInChannel) { binding.changeRoomstate.isVisible = it }
            collectFlow(shouldShowViewPager) {
                binding.chatViewpager.isVisible = it && !isInPictureInPictureMode
                binding.addChannelsText.isVisible = !it
                binding.addChannelsButton.isVisible = !it
            }
            collectFlow(shouldShowEmoteMenuIcon) { showEmoteMenuIcon ->
                when {
                    showEmoteMenuIcon -> binding.inputLayout.setupEmoteMenu()
                    else              -> binding.inputLayout.startIconDrawable = null
                }
            }
            collectFlow(shouldShowFullscreenHelper) { binding.fullscreenHintText.isVisible = it }

            collectFlow(events) {
                when (it) {
                    is MainEvent.Error -> handleErrorEvent(it)
                }
            }
            collectFlow(channelMentionCount, ::updateChannelMentionBadges)
            collectFlow(unreadMessagesMap, ::updateUnreadChannelTabColors)
            collectFlow(shouldColorNotification) { activity?.invalidateMenu() }
            collectFlow(channels, mainViewModel::fetchStreamData)
            collectFlow(currentStreamedChannel) {
                val isActive = it != null
                binding.streamWebviewWrapper.isVisible = isActive
                if (!isLandscape) {
                    return@collectFlow
                }

                binding.splitThumb?.isVisible = isActive && !isInPictureInPictureMode
                binding.splitGuideline?.updateLayoutParams<ConstraintLayout.LayoutParams> {
                    guidePercent = when {
                        isActive && isInPictureInPictureMode -> PIP_GUIDELINE_PERCENT
                        isActive                             -> DEFAULT_GUIDELINE_PERCENT
                        else                                 -> DISABLED_GUIDELINE_PERCENT
                    }
                }
            }
            collectFlow(shouldEnablePictureInPictureAutoMode) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    activity?.setPictureInPictureParams(
                        PictureInPictureParams.Builder()
                            .setAutoEnterEnabled(it)
                            .build()
                    )
                }
            }
            collectFlow(useCustomBackHandling) { onBackPressedCallback.isEnabled = it }
            collectFlow(dankChatViewModel.validationResult) {
                if (isInPictureInPictureMode) {
                    return@collectFlow
                }

                when (it) {
                    // wait for username to be validated before showing snackbar
                    is ValidationResult.User             -> showSnackBar(getString(R.string.snackbar_login, it.username), onDismiss = ::openChangelogSheetIfNecessary)
                    is ValidationResult.IncompleteScopes -> MaterialAlertDialogBuilder(requireContext())
                        .setTitle(R.string.login_outdated_title)
                        .setMessage(R.string.login_outdated_message)
                        .setPositiveButton(R.string.oauth_expired_login_again) { _, _ -> openLogin() }
                        .setNegativeButton(R.string.dialog_dismiss) { _, _ -> openChangelogSheetIfNecessary() }
                        .create().show()

                    ValidationResult.TokenInvalid        -> {
                        mainViewModel.cancelDataLoad()
                        MaterialAlertDialogBuilder(requireContext())
                            .setTitle(R.string.oauth_expired_title)
                            .setMessage(R.string.oauth_expired_message)
                            .setPositiveButton(R.string.oauth_expired_login_again) { _, _ -> openLogin() }
                            .setNegativeButton(R.string.dialog_dismiss) { _, _ -> openChangelogSheetIfNecessary() } // default action is dismissing anyway
                            .create().show()
                    }

                    ValidationResult.Failure             -> showSnackBar(getString(R.string.oauth_verify_failed), onDismiss = ::openChangelogSheetIfNecessary)
                }
            }
        }

        val navBackStackEntry = navController.getBackStackEntry(R.id.mainFragment)
        val handle = navBackStackEntry.savedStateHandle
        val observer = LifecycleEventObserver { _, event ->
            if (event != Lifecycle.Event.ON_RESUME) return@LifecycleEventObserver
            handle.keys().forEach { key ->
                when (key) {
                    LOGIN_REQUEST_KEY             -> handle.withData(key, ::handleLoginRequest)
                    ADD_CHANNEL_REQUEST_KEY       -> handle.withData(key, ::addChannel)
                    HISTORY_DISCLAIMER_KEY        -> handle.withData(key, ::handleMessageHistoryDisclaimerResult)
                    USER_POPUP_RESULT_KEY         -> handle.withData(key, ::handleUserPopupResult)
                    MESSAGE_SHEET_RESULT_KEY      -> handle.withData(key, ::handleMessageSheetResult)
                    COPY_MESSAGE_SHEET_RESULT_KEY -> handle.withData(key, ::handleCopyMessageSheetResult)
                    EMOTE_SHEET_RESULT_KEY        -> handle.withData(key, ::handleEmoteSheetResult)
                    LOGOUT_REQUEST_KEY            -> handle.withData<Boolean>(key) { showLogoutConfirmationDialog() }
                    CHANNELS_REQUEST_KEY          -> handle.withData<Array<ChannelWithRename>>(key) { updateChannels(it.toList()) }
                }
            }
        }
        navBackStackEntry.lifecycle.addObserver(observer)
        viewLifecycleOwner.lifecycle.addObserver(LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_DESTROY) {
                navBackStackEntry.lifecycle.removeObserver(observer)
            }
        })

        if (dankChatPreferences.isLoggedIn && dankChatPreferences.userIdString == null) {
            dankChatPreferences.userIdString = "${dankChatPreferences.userId}".toUserId()
        }

        val channels = dankChatPreferences.channels
        val withRenames = dankChatPreferences.getChannelsWithRenames(channels)
        tabAdapter.updateFragments(withRenames)
        @SuppressLint("WrongConstant")
        binding.chatViewpager.offscreenPageLimit = OFFSCREEN_PAGE_LIMIT
        tabLayoutMediator.attach()
        binding.tabs.addOnTabSelectedListener(tabSelectionListener)

        (requireActivity() as AppCompatActivity).apply {
            setSupportActionBar(binding.toolbar)
            onBackPressedDispatcher.addCallback(viewLifecycleOwner, onBackPressedCallback)

            ViewCompat.setOnApplyWindowInsetsListener(binding.showChips) { v, insets ->
                // additional margin for chips because of display cutouts/punch holes
                val needsExtraMargin = bindingRef?.streamWebviewWrapper?.isVisible == true || isLandscape || !mainViewModel.isFullscreen
                val extraMargin = when {
                    needsExtraMargin -> 0
                    else             -> insets.getInsets(Type.displayCutout()).top
                }
                v.updateLayoutParams<ConstraintLayout.LayoutParams> {
                    topMargin = 8.px + extraMargin
                }

                WindowInsetsCompat.CONSUMED
            }

            val deferringInsetsListener = RootViewDeferringInsetsCallback(
                persistentInsetTypes = Type.systemBars() or Type.displayCutout(),
                deferredInsetTypes = Type.ime(),
                ignorePersistentInsetTypes = { mainViewModel.isFullscreen }
            )
            ViewCompat.setWindowInsetsAnimationCallback(binding.root, deferringInsetsListener)
            ViewCompat.setOnApplyWindowInsetsListener(binding.root, deferringInsetsListener)
            ViewCompat.setWindowInsetsAnimationCallback(
                binding.inputLayout,
                TranslateDeferringInsetsAnimationCallback(
                    view = binding.inputLayout,
                    persistentInsetTypes = Type.systemBars(),
                    deferredInsetTypes = Type.ime(),
                    dispatchMode = WindowInsetsAnimationCompat.Callback.DISPATCH_MODE_CONTINUE_ON_SUBTREE
                )
            )
            ViewCompat.setWindowInsetsAnimationCallback(
                binding.input,
                ControlFocusInsetsAnimationCallback(binding.input)
            )

            ViewCompat.setOnApplyWindowInsetsListener(binding.fullscreenHintText) { v, compatInsets ->
                val insets = compatInsets.toWindowInsets()
                if (insets != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val bottomLeft = insets.getRoundedCorner(RoundedCorner.POSITION_BOTTOM_LEFT)
                    val bottomRight = insets.getRoundedCorner(RoundedCorner.POSITION_BOTTOM_RIGHT)
                    if (bottomLeft == null || bottomRight == null) {
                        return@setOnApplyWindowInsetsListener compatInsets
                    }

                    val isRtl = v.layoutDirection == View.LAYOUT_DIRECTION_RTL
                    val left = when {
                        mainViewModel.isFullscreen && (isPortrait || isRtl || !mainViewModel.isStreamActive) -> bottomLeft.center.x
                        else                                                                                 -> 8.px
                    }

                    val screenWidth = window.decorView.width
                    val right = when {
                        mainViewModel.isFullscreen && (isPortrait || !isRtl || !mainViewModel.isStreamActive) -> screenWidth - bottomRight.center.x
                        else                                                                                  -> 8.px
                    }

                    v.updateLayoutParams<MarginLayoutParams> {
                        leftMargin = left
                        rightMargin = right
                    }
                }

                compatInsets
            }

            if (savedInstanceState == null && !mainViewModel.started) {
                mainViewModel.started = true // TODO ???
                if (!dankChatPreferences.hasMessageHistoryAcknowledged) {
                    navigateSafe(R.id.action_mainFragment_to_messageHistoryDisclaimerDialogFragment)
                } else {
                    mainViewModel.loadData(channels)
                }
            }
        }
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean) {
        bindingRef?.updatePictureInPictureVisibility(isInPictureInPictureMode)
    }

    override fun onPause() {
        binding.input.clearFocus()
        mainViewModel.cancelStreamData()
        super.onPause()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(CURRENT_STREAM_STATE, mainViewModel.currentStreamedChannel.value?.value)
    }

    override fun onViewStateRestored(savedInstanceState: Bundle?) {
        super.onViewStateRestored(savedInstanceState)
        savedInstanceState?.let {
            mainViewModel.setCurrentStream(it.getString(CURRENT_STREAM_STATE)?.toUserName())
        }
    }

    override fun onResume() {
        super.onResume()
        changeActionBarVisibility(mainViewModel.isFullscreenFlow.value)
        bindingRef?.updatePictureInPictureVisibility()

        (activity as? MainActivity)?.apply {
            val channel = channelToOpen
            if (channel != null) {
                val index = mainViewModel.getChannels().indexOf(channel)
                if (index >= 0) {
                    when (index) {
                        bindingRef?.chatViewpager?.currentItem -> clearNotificationsOfChannel(channel)
                        else                                   -> bindingRef?.chatViewpager?.post { bindingRef?.chatViewpager?.setCurrentItem(index, false) }
                    }
                }
                channelToOpen = null
            } else {
                val activeChannel = mainViewModel.getActiveChannel() ?: return
                clearNotificationsOfChannel(activeChannel)
            }
        }

        if (mainViewModel.isFullScreenSheetClosed) {
            val existing = childFragmentManager.fragments.filter { it is MentionFragment || it is RepliesFragment }
            if (existing.isNotEmpty()) {
                childFragmentManager.commitNow(allowStateLoss = true) {
                    existing.forEach(::remove)
                }
                fullscreenBottomSheetBehavior?.hide()
            }
        }

        when {
            mainViewModel.isInputSheetClosed -> {
                val existing = childFragmentManager.fragments.filter { it is ReplyInputSheetFragment || it is EmoteSheetFragment }
                if (existing.isNotEmpty()) {
                    childFragmentManager.commitNow(allowStateLoss = true) {
                        existing.forEach(::remove)
                    }
                    inputBottomSheetBehavior?.hide()
                    bindingRef?.chatViewpager?.updateLayoutParams<MarginLayoutParams> { bottomMargin = 0 }
                }
            }

            mainViewModel.isReplySheetOpen   -> {
                val reply = mainViewModel.currentReply ?: return
                startReply(reply.replyMessageId, reply.replyName)
            }
        }
    }

    override fun onDestroyView() {
        binding.tabs.removeOnTabSelectedListener(tabSelectionListener)
        binding.chatViewpager.unregisterOnPageChangeCallback(pageChangeCallback)
        inputBottomSheetBehavior?.removeBottomSheetCallback(inputSheetCallback)
        fullscreenBottomSheetBehavior?.removeBottomSheetCallback(fullScreenSheetCallback)
        tabLayoutMediator.detach()
        inputBottomSheetBehavior = null
        fullscreenBottomSheetBehavior = null
        binding.chatViewpager.adapter = null
        bindingRef = null
        super.onDestroyView()
    }

    fun openUserPopup(
        targetUserId: UserId,
        targetUserName: UserName,
        targetDisplayName: DisplayName,
        channel: UserName?,
        badges: List<Badge>,
        isWhisperPopup: Boolean = false
    ) {
        val directions = MainFragmentDirections.actionMainFragmentToUserPopupDialogFragment(
            targetUserId = targetUserId,
            targetUserName = targetUserName,
            targetDisplayName = targetDisplayName,
            channel = channel,
            isWhisperPopup = isWhisperPopup,
            badges = badges.toTypedArray(),
        )
        navigateSafe(directions)
    }

    fun openMessageSheet(messageId: String, channel: UserName?, fullMessage: String, canReply: Boolean, canModerate: Boolean) {
        val directions = MainFragmentDirections.actionMainFragmentToMessageSheetFragment(messageId, channel, fullMessage, canReply, canModerate)
        navigateSafe(directions)
    }

    fun openEmoteSheet(emotes: List<ChatMessageEmote>) {
        val directions = MainFragmentDirections.actionMainFragmentToEmoteSheetFragment(emotes.toTypedArray())
        navigateSafe(directions)
    }

    fun mentionUser(user: UserName, display: DisplayName) {
        val template = notificationsSettingsDataStore.current().mentionFormat.template
        val mention = "${template.replace("name", user.valueOrDisplayName(display))} "
        insertText(mention)
    }

    fun whisperUser(user: UserName) {
        openMentionSheet(openWhisperTab = true)

        val current = binding.input.text.toString()
        val command = "/w $user"
        if (current.startsWith(command)) {
            return
        }

        val text = "$command $current"
        binding.input.setText(text)
        binding.input.setSelection(text.length)
    }

    fun openReplies(replyMessageId: String) {
        inputBottomSheetBehavior?.hide()
        val fragment = RepliesFragment.newInstance(replyMessageId)
        childFragmentManager.commitNow(allowStateLoss = true) {
            replace(R.id.full_screen_sheet_fragment, fragment)
        }
        fullscreenBottomSheetBehavior?.expand()
    }

    fun insertEmote(code: String, id: String) {
        insertText("$code ")
        mainViewModel.addEmoteUsage(id)
    }

    private fun openChangelogSheetIfNecessary() {
        if (dankChatPreferences.shouldShowChangelog()) {
            navigateSafe(R.id.action_mainFragment_to_changelogSheetFragment)
        }
    }

    private fun openMentionSheet(openWhisperTab: Boolean = false) {
        when {
            openWhisperTab && mainViewModel.isWhisperTabOpen -> return
            openWhisperTab && mainViewModel.isMentionTabOpen -> {
                val fragment = childFragmentManager.fragments.filterIsInstance<MentionFragment>().firstOrNull()
                if (fragment == null) {
                    createAndOpenMentionSheet(openWhisperTab = true)
                    return
                }

                mainViewModel.setFullScreenSheetState(FullScreenSheetState.Whisper)
                fragment.scrollToWhisperTab()
            }

            else                                             -> createAndOpenMentionSheet(openWhisperTab)
        }
    }

    private fun createAndOpenMentionSheet(openWhisperTab: Boolean = false) {
        inputBottomSheetBehavior?.hide()
        lifecycleScope.launch {
            fullscreenBottomSheetBehavior?.awaitState(BottomSheetBehavior.STATE_HIDDEN)
            val fragment = MentionFragment.newInstance(openWhisperTab)
            childFragmentManager.commitNow(allowStateLoss = true) {
                replace(R.id.full_screen_sheet_fragment, fragment)
            }
            fullscreenBottomSheetBehavior?.expand()
        }
    }

    private fun handleMessageSheetResult(result: MessageSheetResult) = when (result) {
        is MessageSheetResult.OpenMoreActions -> openMoreActionsMessageSheet(result.messageId, result.fullMessage)
        is MessageSheetResult.Copy            -> copyAndShowSnackBar(result.message, R.string.snackbar_message_copied)
        is MessageSheetResult.Reply           -> startReply(result.replyMessageId, result.replyName)
        is MessageSheetResult.ViewThread      -> openReplies(result.rootThreadId)
    }

    private fun openMoreActionsMessageSheet(messageId: String, fullMessage: String) {
        val directions = MainFragmentDirections.actionMainFragmentToMoreActionsMessageSheetFragment(messageId, fullMessage)
        navigateSafe(directions)
    }

    private fun handleCopyMessageSheetResult(result: MoreActionsMessageSheetResult) = when (result) {
        is MoreActionsMessageSheetResult.Copy   -> copyAndShowSnackBar(result.message, R.string.snackbar_message_copied)
        is MoreActionsMessageSheetResult.CopyId -> copyAndShowSnackBar(result.id, R.string.snackbar_message_id_copied)
    }

    private fun handleEmoteSheetResult(result: EmoteSheetResult) = when (result) {
        is EmoteSheetResult.Copy -> copyAndShowSnackBar(result.emoteName, R.string.emote_copied)
        is EmoteSheetResult.Use  -> insertEmote(result.emoteName, result.id)
    }

    private fun copyAndShowSnackBar(value: String, @StringRes snackBarLabel: Int) {
        getSystemService(requireContext(), ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText(CLIPBOARD_LABEL_MESSAGE, value))
        showSnackBar(
            message = getString(snackBarLabel),
            action = getString(R.string.snackbar_paste) to {
                val preparedMessage = value
                    .withoutInvisibleChar
                    .withTrailingSpace
                insertText(preparedMessage)
            }
        )
    }

    private fun startReply(replyMessageId: String, replyName: UserName) {
        val fragment = ReplyInputSheetFragment.newInstance(replyMessageId, replyName)
        childFragmentManager.commitNow(allowStateLoss = true) {
            replace(R.id.input_sheet_fragment, fragment)
        }
        inputBottomSheetBehavior?.expand()
        binding.root.post {
            binding.chatViewpager.updateLayoutParams<MarginLayoutParams> {
                bottomMargin = binding.inputSheetFragment.height
            }
        }
    }

    private fun insertText(text: String) {
        if (!dankChatPreferences.isLoggedIn) {
            return
        }

        val current = binding.input.text.toString()
        val index = binding.input.selectionStart.takeIf { it >= 0 } ?: current.length
        val builder = StringBuilder(current).insert(index, text)

        binding.input.setText(builder.toString())
        binding.input.setSelection(index + text.length)
    }

    private fun openLogin() {
        val directions = MainFragmentDirections.actionMainFragmentToLoginFragment()
        navigateSafe(directions)
        hideKeyboard()
    }

    private fun handleMessageHistoryDisclaimerResult(result: Boolean) {
        dankChatPreferences.setCurrentInstalledVersionCode()
        dankChatPreferences.hasMessageHistoryAcknowledged = true
        lifecycleScope.launch {
            chatSettingsDataStore.update { it.copy(loadMessageHistory = result) }
        }
        mainViewModel.loadData()
    }

    private fun handleUserPopupResult(result: UserPopupResult) {
        when (result) {
            is UserPopupResult.Error   -> showSnackBar(getString(R.string.user_popup_error, result.throwable?.message.orEmpty()))
            is UserPopupResult.Mention -> {
                lifecycleScope.launch {
                    if (mainViewModel.isMentionTabOpen) {
                        mainViewModel.setFullScreenSheetState(FullScreenSheetState.Closed)
                        fullscreenBottomSheetBehavior?.awaitState(BottomSheetBehavior.STATE_HIDDEN)
                    }
                    mentionUser(result.targetUser, result.targetDisplayName)
                }
            }

            is UserPopupResult.Whisper -> whisperUser(result.targetUser)
        }
    }

    private fun addChannel(channel: String) {
        val lowerCaseChannel = channel.lowercase().removePrefix("#").toUserName()
        var newTabIndex = mainViewModel.getChannels().indexOf(lowerCaseChannel)
        if (newTabIndex == -1) {
            val updatedChannels = mainViewModel.joinChannel(lowerCaseChannel)
            newTabIndex = updatedChannels.lastIndex
            mainViewModel.loadData(channelList = listOf(lowerCaseChannel))
            dankChatPreferences.channels = updatedChannels

            tabAdapter.addFragment(lowerCaseChannel)
        }
        binding.chatViewpager.setCurrentItem(newTabIndex, false)

        mainViewModel.setActiveChannel(lowerCaseChannel)
        activity?.invalidateMenu()
    }

    private fun sendMessage(): Boolean {
        val msg = binding.input.text?.toString().orEmpty()
        mainViewModel.trySendMessageOrCommand(msg)
        binding.input.setText("")

        if (mainViewModel.isReplySheetOpen) {
            inputBottomSheetBehavior?.hide()
        }

        return true
    }

    private fun repeatSendMessage(event: DankChatInputLayout.TouchEvent) {
        val input = binding.input.text.toString().ifBlank { return }
        mainViewModel.setRepeatedSend(event == DankChatInputLayout.TouchEvent.HOLD_START, input)
    }

    private fun getLastMessage(): Boolean {
        if (binding.input.text.isNotBlank()) {
            return false
        }

        val lastMessage = mainViewModel.getLastMessage() ?: return false
        binding.input.setText(lastMessage)
        binding.input.setSelection(lastMessage.length)

        return true
    }

    private fun handleImageUploadState(result: ImageUploadState) {
        when (result) {
            is ImageUploadState.Loading, ImageUploadState.None -> return
            is ImageUploadState.Failed                         -> showSnackBar(
                message = result.errorMessage?.let { getString(R.string.snackbar_upload_failed_cause, it) } ?: getString(R.string.snackbar_upload_failed),
                onDismiss = { result.mediaFile.delete() },
                action = getString(R.string.snackbar_retry) to { mainViewModel.uploadMedia(result.mediaFile, result.imageCapture) })

            is ImageUploadState.Finished                       -> {
                val clipboard = getSystemService(requireContext(), ClipboardManager::class.java)
                clipboard?.setPrimaryClip(ClipData.newPlainText(CLIPBOARD_LABEL, result.url))
                showSnackBar(
                    message = getString(R.string.snackbar_image_uploaded, result.url),
                    action = getString(R.string.snackbar_paste) to { insertText(result.url) }
                )
            }
        }
    }

    private fun handleDataLoadingState(result: DataLoadingState) {
        when (result) {
            is DataLoadingState.Loading, DataLoadingState.Finished, DataLoadingState.None -> return
            is DataLoadingState.Reloaded                                                  -> showSnackBar(getString(R.string.snackbar_data_reloaded))
            is DataLoadingState.Failed                                                    -> {
                val message = when (result.errorCount) {
                    1    -> getString(R.string.snackbar_data_load_failed_cause, result.errorMessage)
                    else -> getString(R.string.snackbar_data_load_failed_multiple_causes, result.errorMessage)
                }
                showSnackBar(
                    message = message,
                    maxLines = 8,
                    duration = Snackbar.LENGTH_LONG,
                    action = getString(R.string.snackbar_retry) to {
                        mainViewModel.retryDataLoading(result.dataFailures, result.chatFailures)
                    })
            }
        }
    }

    private fun handleErrorEvent(event: MainEvent.Error) {
        if (developerSettingsDataStore.current().debugMode) {
            binding.root.showErrorDialog(event.throwable)
        }
    }

    private fun handleLoginRequest(success: Boolean) {
        val name = dankChatPreferences.userName
        if (success && name != null) {
            mainViewModel.closeAndReconnect()
            showSnackBar(getString(R.string.snackbar_login, name), onDismiss = ::openChangelogSheetIfNecessary)
        } else {
            dankChatPreferences.clearLogin()
            showSnackBar(getString(R.string.snackbar_login_failed), onDismiss = ::openChangelogSheetIfNecessary)
        }
    }

    private fun handleCaptureRequest(imageCapture: Boolean) {
        if (currentMediaUri == Uri.EMPTY) return
        var mediaFile: File? = null

        try {
            mediaFile = currentMediaUri.toFile()
            currentMediaUri = Uri.EMPTY
            mainViewModel.uploadMedia(mediaFile, imageCapture)
        } catch (_: IOException) {
            currentMediaUri = Uri.EMPTY
            mediaFile?.delete()
            showSnackBar(getString(R.string.snackbar_upload_failed))
        }
    }

    private fun setSuggestions(suggestions: Triple<List<Suggestion.UserSuggestion>, List<Suggestion.EmoteSuggestion>, List<Suggestion.CommandSuggestion>>) {
        if (binding.input.isPopupShowing) {
            return
        }

        suggestionAdapter.setSuggestions(suggestions)
    }

    private inline fun showExternalHostingUploadDialogIfNotAcknowledged(crossinline action: () -> Unit) {
        // show host name in dialog, another nice thing we get is it also detect some invalid URLs
        val host = runCatching {
            URL(toolsSettingsDataStore.current().uploaderConfig.uploadUrl).host
        }.getOrElse { "" }

        // if config is invalid, just let the error handled by HTTP client
        if (host.isNotBlank() && !dankChatPreferences.hasExternalHostingAcknowledged) {
            val spannable = SpannableStringBuilder(getString(R.string.external_upload_disclaimer, host))
            Linkify.addLinks(spannable, Linkify.WEB_URLS)

            MaterialAlertDialogBuilder(requireContext())
                .setCancelable(false)
                .setTitle(R.string.nuuls_upload_title)
                .setMessage(spannable)
                .setPositiveButton(R.string.dialog_ok) { dialog, _ ->
                    dialog.dismiss()
                    dankChatPreferences.hasExternalHostingAcknowledged = true
                    action()
                }
                .show().also { it.findViewById<TextView>(android.R.id.message)?.movementMethod = LinkMovementMethod.getInstance() }
        } else {
            action()
        }
    }

    private fun startCameraCapture(captureVideo: Boolean = false) {
        val packageManager = activity?.packageManager ?: return
        val (action, extension) = when {
            captureVideo -> MediaStore.ACTION_VIDEO_CAPTURE to "mp4"
            else         -> MediaStore.ACTION_IMAGE_CAPTURE to "jpg"
        }
        showExternalHostingUploadDialogIfNotAcknowledged {
            Intent(action).also { captureIntent ->
                captureIntent.resolveActivity(packageManager)?.also {
                    try {
                        createMediaFile(requireContext(), extension).apply { currentMediaUri = toUri() }
                    } catch (ex: IOException) {
                        null
                    }?.also {
                        val uri = FileProvider.getUriForFile(requireContext(), "${BuildConfig.APPLICATION_ID}.fileprovider", it)
                        captureIntent.putExtra(MediaStore.EXTRA_OUTPUT, uri)
                        when {
                            captureVideo -> requestVideoCapture.launch(captureIntent)
                            else         -> requestImageCapture.launch(captureIntent)
                        }
                    }
                }
            }
        }
    }

    private fun changeActionBarVisibility(isFullscreen: Boolean) {
        if (isInPictureInPictureMode) {
            return
        }

        hideKeyboard()
        (activity as? MainActivity)?.setFullScreen(isFullscreen)
    }

    private fun clear() {
        val position = binding.tabs.selectedTabPosition
        val channel = tabAdapter[position] ?: return
        mainViewModel.clear(channel)
    }

    private fun reloadEmotes() {
        val position = binding.tabs.selectedTabPosition
        val channel = tabAdapter[position] ?: return
        mainViewModel.reloadEmotes(channel)
    }

    private fun initPreferences() {
        if (dankChatPreferences.isLoggedIn && dankChatPreferences.oAuthKey.isNullOrBlank()) {
            dankChatPreferences.clearLogin()
        }

        collectFlow(chatSettingsDataStore.suggestions) {
            binding.input.setSuggestionAdapter(it, suggestionAdapter)
        }
    }

    private fun showSnackBar(
        message: String,
        maxLines: Int = 1,
        @BaseTransientBottomBar.Duration duration: Int = Snackbar.LENGTH_SHORT,
        onDismiss: () -> Unit = {},
        action: Pair<String, () -> Unit>? = null
    ) {
        bindingRef?.let { binding ->
            binding.inputLayout.post {
                Snackbar.make(binding.coordinator, message, duration).apply {
                    if (binding.inputLayout.isVisible) {
                        anchorView = binding.inputLayout
                    }
                    setTextMaxLines(maxLines)
                    addCallback(object : Snackbar.Callback() {
                        override fun onDismissed(transientBottomBar: Snackbar?, event: Int) {
                            when (event) {
                                BaseCallback.DISMISS_EVENT_CONSECUTIVE, BaseCallback.DISMISS_EVENT_TIMEOUT, BaseCallback.DISMISS_EVENT_SWIPE -> onDismiss()
                                else                                                                                                         -> return
                            }
                        }
                    })
                    action?.let { (msg, onAction) -> setAction(msg) { onAction() } }

                }.show()
            }
        }
    }

    private fun showLogoutConfirmationDialog() = MaterialAlertDialogBuilder(requireContext())
        .setTitle(getString(R.string.confirm_logout_title))
        .setMessage(getString(R.string.confirm_logout_message))
        .setPositiveButton(getString(R.string.confirm_logout_positive_button)) { dialog, _ ->
            mainViewModel.clearDataForLogout()
            dialog.dismiss()
        }
        .setNegativeButton(getString(R.string.dialog_cancel)) { dialog, _ -> dialog.dismiss() }
        .create().show()

    private fun openChannel() {
        val channel = mainViewModel.getActiveChannel() ?: return
        val url = "https://twitch.tv/$channel"
        Intent(Intent.ACTION_VIEW).also {
            it.data = url.toUri()
            startActivity(it)
        }
    }

    private fun reportChannel() {
        val activeChannel = mainViewModel.getActiveChannel() ?: return
        val url = "https://twitch.tv/$activeChannel/report"
        Intent(Intent.ACTION_VIEW).also {
            it.data = url.toUri()
            startActivity(it)
        }
    }

    private fun blockChannel() {
        closeInputSheets()
        val activeChannel = mainViewModel.getActiveChannel() ?: return
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.confirm_channel_block_title)
            .setMessage(getString(R.string.confirm_channel_block_message_named, activeChannel))
            .setPositiveButton(R.string.confirm_user_block_positive_button) { _, _ ->
                mainViewModel.blockUser()
                removeChannel()
                showSnackBar(getString(R.string.channel_blocked_message))
            }
            .setNegativeButton(R.string.dialog_cancel) { d, _ -> d.dismiss() }
            .show()
    }

    private fun removeChannel() {
        closeInputSheets()
        val activeChannel = mainViewModel.getActiveChannel() ?: return
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.confirm_channel_removal_title)
            // should give user more info that it's gonna delete the currently active channel (unlike when clicking delete from manage channels list, where is very obvious)
            .setMessage(getString(R.string.confirm_channel_removal_message_named, activeChannel))
            .setPositiveButton(R.string.confirm_channel_removal_positive_button) { _, _ ->
                dankChatPreferences.removeChannel(activeChannel)
                val withRenames = dankChatPreferences.getChannelsWithRenames()
                updateChannels(withRenames)
            }
            .setNegativeButton(R.string.dialog_cancel) { _, _ -> }
            .create().show()
    }

    private fun openManageChannelsDialog() {
        closeInputSheets()
        val direction = MainFragmentDirections.actionMainFragmentToChannelsDialogFragment(channels = mainViewModel.getChannels().toTypedArray())
        navigateSafe(direction)
    }

    private fun showRoomStateDialog() {
        val currentRoomState = mainViewModel.currentRoomState ?: return
        val activeStates = currentRoomState.activeStates
        val choices = resources.getStringArray(R.array.roomstate_entries)

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.confirm_user_roomstate_title)
            .setPositiveButton(R.string.dialog_ok) { d, _ -> d.dismiss() }
            .setMultiChoiceItems(choices, activeStates) { d, index, isChecked ->
                if (!isChecked) {
                    mainViewModel.changeRoomState(index, enabled = false)
                    d.dismiss()
                    return@setMultiChoiceItems
                }

                when (index) {
                    0, 1, 3 -> {
                        mainViewModel.changeRoomState(index, enabled = true)
                        d.dismiss()
                    }

                    else    -> {
                        val title = choices[index]
                        val hint = if (index == 2) R.string.seconds else R.string.minutes
                        val content = EditDialogBinding.inflate(LayoutInflater.from(requireContext()), null, false).apply {
                            dialogEdit.setText(10.toString())
                            dialogEdit.inputType = EditorInfo.TYPE_CLASS_NUMBER
                            dialogEditLayout.setHint(hint)
                        }

                        d.dismiss()
                        MaterialAlertDialogBuilder(requireContext())
                            .setTitle(title)
                            .setView(content.root)
                            .setPositiveButton(R.string.dialog_ok) { editDialog, _ ->
                                val input = content.dialogEdit.text?.toString().orEmpty()
                                mainViewModel.changeRoomState(index, enabled = true, time = input)
                                editDialog.dismiss()
                            }
                            .setNegativeButton(R.string.dialog_cancel) { editDialog, _ ->
                                editDialog.dismiss()
                            }
                            .show()
                    }
                }
            }
            .show()
    }

    private fun updateChannels(updatedChannelsWithRenames: List<ChannelWithRename>) {
        val updatedChannels = updatedChannelsWithRenames.map(ChannelWithRename::channel)
        val oldChannels = mainViewModel.getChannels()
        val oldIndex = binding.chatViewpager.currentItem
        val oldActiveChannel = oldChannels.getOrNull(oldIndex)

        val index = updatedChannelsWithRenames
            .indexOfFirst { it.channel == oldActiveChannel }
            .coerceAtLeast(0)
        val activeChannel = updatedChannels.getOrNull(index)

        tabAdapter.updateFragments(updatedChannelsWithRenames)
        mainViewModel.updateChannels(updatedChannels)
        mainViewModel.setActiveChannel(activeChannel)

        binding.chatViewpager.setCurrentItem(index, false)
        binding.root.postDelayed(TAB_SCROLL_DELAY_MS) {
            binding.tabs.setScrollPosition(index, 0f, false)
        }

        activity?.invalidateMenu()
        updateChannelMentionBadges(channels = mainViewModel.channelMentionCount.firstValueOrNull.orEmpty())
        updateUnreadChannelTabColors(channels = mainViewModel.unreadMessagesMap.firstValueOrNull.orEmpty())
    }

    private fun updateUnreadChannelTabColors(channels: Map<UserName, Boolean>) {
        channels.forEach { (channel, _) ->
            when (val index = tabAdapter.indexOfChannel(channel)) {
                binding.chatViewpager.currentItem -> mainViewModel.clearUnreadMessage(channel)
                else                              -> {
                    val tab = binding.tabs.getTabAt(index)
                    binding.tabs.post { tab?.setTextColor(R.attr.colorOnSurface) }
                }
            }
        }
    }

    private fun updateChannelMentionBadges(channels: Map<UserName, Int>) {
        channels.forEach { (channel, count) ->
            val index = tabAdapter.indexOfChannel(channel)
            if (count > 0) {
                when (index) {
                    binding.chatViewpager.currentItem -> mainViewModel.clearMentionCount(channel) // mention is in active channel
                    else                              -> binding.tabs.getTabAt(index)?.apply { orCreateBadge }
                }
            } else {
                binding.tabs.getTabAt(index)?.removeBadge()
            }
        }
    }

    private fun MainFragmentBinding.updatePictureInPictureVisibility(isInPictureInPicture: Boolean = isInPictureInPictureMode) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            appbarLayout.isVisible = !isInPictureInPicture
            tabs.isVisible = !isInPictureInPicture && mainViewModel.shouldShowTabs.value
            chatViewpager.isVisible = !isInPictureInPicture && mainViewModel.shouldShowViewPager.value
            fullScreenSheetFragment.isVisible = !isInPictureInPicture
            inputSheetFragment.isVisible = !isInPictureInPicture
            inputLayout.isVisible = !isInPictureInPicture && mainViewModel.shouldShowInput.value
            fullscreenHintText.isVisible = !isInPictureInPicture && mainViewModel.shouldShowFullscreenHelper.value
            showChips.isVisible = !isInPictureInPicture && mainViewModel.shouldShowChipToggle.value
            splitThumb?.isVisible = !isInPictureInPicture && streamWebviewWrapper.isVisible
            splitGuideline?.updateLayoutParams<ConstraintLayout.LayoutParams> {
                guidePercent = when {
                    !mainViewModel.isStreamActive -> DISABLED_GUIDELINE_PERCENT
                    isInPictureInPicture          -> PIP_GUIDELINE_PERCENT
                    else                          -> DEFAULT_GUIDELINE_PERCENT
                }
            }
        }
    }

    private fun BottomSheetBehavior<FragmentContainerView>.setupFullScreenSheet() {
        addBottomSheetCallback(fullScreenSheetCallback)
        hide()
    }

    private fun ViewPager2.setup() {
        adapter = tabAdapter
        reduceDragSensitivity()
        registerOnPageChangeCallback(pageChangeCallback)
    }

    private fun DankChatInputLayout.setupSendButton() {
        setEndIconDrawable(R.drawable.ic_send)
        val touchListenerAdded = when {
            developerSettingsDataStore.current().repeatedSending -> {
                setEndIconOnClickListener { } // for ripple effects
                setEndIconTouchListener { holdTouchEvent ->
                    when (holdTouchEvent) {
                        DankChatInputLayout.TouchEvent.CLICK      -> sendMessage()
                        DankChatInputLayout.TouchEvent.LONG_CLICK -> getLastMessage()
                        else                                      -> repeatSendMessage(holdTouchEvent)
                    }
                }
            }

            else                                                 -> false
        }

        if (!touchListenerAdded) {
            setEndIconOnClickListener { sendMessage() }
            setEndIconOnLongClickListener { getLastMessage() }
        }
    }

    private fun DankChatInputLayout.setupEmoteMenu() {
        setStartIconDrawable(R.drawable.ic_insert_emoticon)
        setStartIconOnClickListener {
            if (mainViewModel.isEmoteSheetOpen) {
                closeInputSheetAndSetState()
                return@setStartIconOnClickListener
            }

            if (isLandscape) {
                hideKeyboard()
                binding.input.clearFocus()
            }

            childFragmentManager.commitNow(allowStateLoss = true) {
                replace(R.id.input_sheet_fragment, EmoteMenuFragment())
            }
            inputBottomSheetBehavior?.expand()
        }
    }

    private fun closeInputSheetAndSetState() {
        val previousState = mainViewModel.closeInputSheet()
        lifecycleScope.launch {
            inputBottomSheetBehavior?.awaitState(BottomSheetBehavior.STATE_HIDDEN)
            if (previousState is InputSheetState.Replying) {
                startReply(previousState.replyMessageId, previousState.replyName)
            }
        }
    }

    private val fullScreenSheetCallback = object : BottomSheetBehavior.BottomSheetCallback() {
        override fun onSlide(bottomSheet: View, slideOffset: Float) = Unit
        override fun onStateChanged(bottomSheet: View, newState: Int) {
            val behavior = fullscreenBottomSheetBehavior ?: return
            when {
                behavior.isHidden    -> {
                    mainViewModel.setFullScreenSheetState(FullScreenSheetState.Closed)
                    val channel = tabAdapter[binding.tabs.selectedTabPosition] ?: return
                    mainViewModel.setSuggestionChannel(channel)

                    val existing = childFragmentManager.fragments.filter { it is MentionFragment || it is RepliesFragment }
                    childFragmentManager.commitNow(allowStateLoss = true) {
                        existing.forEach(::remove)
                    }
                }

                behavior.isCollapsed -> behavior.hide()
            }
        }
    }

    private val inputSheetCallback = object : BottomSheetBehavior.BottomSheetCallback() {
        override fun onSlide(bottomSheet: View, slideOffset: Float) = Unit

        override fun onStateChanged(bottomSheet: View, newState: Int) {
            val behavior = inputBottomSheetBehavior ?: return
            if (!mainViewModel.isFullscreenFlow.value && isLandscape && mainViewModel.isEmoteSheetOpen) {
                when (newState) {
                    BottomSheetBehavior.STATE_EXPANDED, BottomSheetBehavior.STATE_COLLAPSED -> {
                        (activity as? AppCompatActivity)?.supportActionBar?.hide()
                       // binding.tabs.visibility = View.GONE
                    }

                    else                                                                    -> {
                        (activity as? AppCompatActivity)?.supportActionBar?.show()
                        //binding.tabs.visibility = View.VISIBLE
                    }
                }
            }

            if (behavior.isHidden) {
                val previousState = mainViewModel.closeInputSheet()
                val existing = childFragmentManager.fragments.filter { it is EmoteMenuFragment || it is ReplyInputSheetFragment }
                childFragmentManager.commitNow(allowStateLoss = true) {
                    existing.forEach(::remove)
                }
                when (previousState) {
                    is InputSheetState.Replying -> startReply(previousState.replyMessageId, previousState.replyName)
                    else                        -> binding.chatViewpager.updateLayoutParams<MarginLayoutParams> { bottomMargin = 0 }
                }
            }

            //binding.streamWebviewWrapper.isVisible = !mainViewModel.isEmoteSheetOpen && mainViewModel.isStreamActive
        }
    }

    private fun DankChatInput.setup(binding: MainFragmentBinding) {
        imeOptions = EditorInfo.IME_ACTION_SEND or EditorInfo.IME_FLAG_NO_FULLSCREEN
        setRawInputType(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES)
        setTokenizer(SpaceTokenizer())
        suggestionAdapter = SuggestionsArrayAdapter(binding.input.context, chatSettingsDataStore) { count ->
            dropDownHeight = if (count > 4) {
                (binding.root.height / 4.0).roundToInt()
            } else {
                ViewGroup.LayoutParams.WRAP_CONTENT
            }
            dropDownWidth = (binding.root.width * 0.6).roundToInt()
        }

        setOnItemClickListener { parent, _, position, _ ->
            val suggestion = parent.getItemAtPosition(position)
            if (suggestion is Suggestion.EmoteSuggestion) {
                mainViewModel.addEmoteUsage(suggestion.emote.id)
            }
        }

        setOnEditorActionListener { _, actionId, _ ->
            return@setOnEditorActionListener when (actionId) {
                EditorInfo.IME_ACTION_SEND -> sendMessage()
                else                       -> false
            }
        }
        setOnKeyListener { _, keyCode, _ ->
            when (keyCode) {
                KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                    if (!isItemSelected()) sendMessage() else false
                }

                else                                                  -> false
            }
        }

        setOnFocusChangeListener { _, hasFocus ->
            val isFullscreen = mainViewModel.isFullscreenFlow.value
            (activity as? MainActivity)?.setFullScreen(isFullscreen, changeActionBarVisibility = false)
            mainViewModel.setInputFocus(hasFocus)

            if (hasFocus && mainViewModel.isEmoteSheetOpen) {
                closeInputSheetAndSetState()
            }

            if (isPortrait) {
                return@setOnFocusChangeListener
            }

            binding.tabs.isVisible = !hasFocus && mainViewModel.shouldShowTabs.value
            binding.streamWebviewWrapper.isVisible = !hasFocus && !mainViewModel.isEmoteSheetOpen && mainViewModel.isStreamActive
            when {
                hasFocus      -> (activity as? MainActivity)?.supportActionBar?.hide()
                !isFullscreen -> (activity as? MainActivity)?.supportActionBar?.show()
            }
        }
    }

    companion object {
        private const val DISABLED_GUIDELINE_PERCENT = 0f
        private const val DEFAULT_GUIDELINE_PERCENT = 0.6f
        private const val PIP_GUIDELINE_PERCENT = 1f
        private const val MAX_GUIDELINE_PERCENT = 0.8f
        private const val MIN_GUIDELINE_PERCENT = 0.2f
        private const val CLIPBOARD_LABEL = "dankchat_media_url"
        private const val CLIPBOARD_LABEL_MESSAGE = "dankchat_message"
        private const val TAB_SCROLL_DELAY_MS = 1000 / 60 * 10L
        private const val OFFSCREEN_PAGE_LIMIT = 2
        private const val CURRENT_STREAM_STATE = "current_stream_state"

        const val LOGOUT_REQUEST_KEY = "logout_key"
        const val LOGIN_REQUEST_KEY = "login_key"
        const val CHANNELS_REQUEST_KEY = "channels_key"
        const val ADD_CHANNEL_REQUEST_KEY = "add_channel_key"
        const val HISTORY_DISCLAIMER_KEY = "history_disclaimer_key"
        const val USER_POPUP_RESULT_KEY = "user_popup_key"
        const val MESSAGE_SHEET_RESULT_KEY = "message_sheet_key"
        const val COPY_MESSAGE_SHEET_RESULT_KEY = "copy_message_sheet_key"
        const val EMOTE_SHEET_RESULT_KEY = "emote_sheet_key"
    }
}

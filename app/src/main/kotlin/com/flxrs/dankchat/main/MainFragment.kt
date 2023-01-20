package com.flxrs.dankchat.main

import android.annotation.SuppressLint
import android.app.Activity
import android.content.*
import android.content.res.ColorStateList
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.text.SpannableStringBuilder
import android.text.method.LinkMovementMethod
import android.text.util.Linkify
import android.view.*
import android.view.inputmethod.EditorInfo
import android.webkit.MimeTypeMap
import android.widget.FrameLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.core.content.ContextCompat.getSystemService
import androidx.core.content.FileProvider
import androidx.core.content.edit
import androidx.core.net.toFile
import androidx.core.net.toUri
import androidx.core.view.*
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.commit
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.navigation.NavController
import androidx.navigation.fragment.findNavController
import androidx.preference.PreferenceManager
import androidx.viewpager2.widget.ViewPager2
import com.flxrs.dankchat.BuildConfig
import com.flxrs.dankchat.DankChatViewModel
import com.flxrs.dankchat.R
import com.flxrs.dankchat.ValidationResult
import com.flxrs.dankchat.chat.ChatTabAdapter
import com.flxrs.dankchat.chat.menu.EmoteMenuFragment
import com.flxrs.dankchat.chat.suggestion.SpaceTokenizer
import com.flxrs.dankchat.chat.suggestion.Suggestion
import com.flxrs.dankchat.chat.suggestion.SuggestionsArrayAdapter
import com.flxrs.dankchat.chat.user.UserPopupResult
import com.flxrs.dankchat.data.*
import com.flxrs.dankchat.data.state.DataLoadingState
import com.flxrs.dankchat.data.state.ImageUploadState
import com.flxrs.dankchat.data.twitch.badge.Badge
import com.flxrs.dankchat.data.twitch.chat.ConnectionState
import com.flxrs.dankchat.data.twitch.emote.GenericEmote
import com.flxrs.dankchat.databinding.EditDialogBinding
import com.flxrs.dankchat.databinding.MainFragmentBinding
import com.flxrs.dankchat.preferences.DankChatPreferenceStore
import com.flxrs.dankchat.utils.createMediaFile
import com.flxrs.dankchat.utils.extensions.*
import com.flxrs.dankchat.utils.removeExifAttributes
import com.flxrs.dankchat.utils.showErrorDialog
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.BaseTransientBottomBar
import com.google.android.material.snackbar.BaseTransientBottomBar.BaseCallback
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.tabs.TabLayoutMediator
import dagger.hilt.android.AndroidEntryPoint
import java.io.File
import java.io.IOException
import java.net.URL
import java.util.*
import javax.inject.Inject
import kotlin.math.roundToInt

@AndroidEntryPoint
class MainFragment : Fragment() {

    private val mainViewModel: MainViewModel by viewModels()
    private val dankChatViewModel: DankChatViewModel by activityViewModels()
    private val navController: NavController by lazy { findNavController() }
    private var bindingRef: MainFragmentBinding? = null
    private val binding get() = bindingRef!!

    private var emoteMenuBottomSheetBehavior: BottomSheetBehavior<FrameLayout>? = null
    private var mentionBottomSheetBehavior: BottomSheetBehavior<View>? = null
    private val onBackPressedCallback = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            when {
                mainViewModel.isEmoteSheetOpen                -> closeEmoteMenu()
                mentionBottomSheetBehavior?.isVisible == true -> mentionBottomSheetBehavior?.hide()
                mainViewModel.isFullscreen                    -> mainViewModel.toggleFullscreen()
            }
        }
    }

    private val pageChangeCallback = object : ViewPager2.OnPageChangeCallback() {
        override fun onPageSelected(position: Int) {
            if (position in tabAdapter.channels.indices) {
                val newChannel = tabAdapter.channels[position].lowercase()
                mainViewModel.setActiveChannel(newChannel)

            }
        }

        override fun onPageScrollStateChanged(state: Int) {
            closeEmoteMenu()
            binding.input.dismissDropDown()
        }
    }

    @Inject
    lateinit var dankChatPreferences: DankChatPreferenceStore

    private lateinit var preferenceListener: SharedPreferences.OnSharedPreferenceChangeListener
    private lateinit var preferences: SharedPreferences
    private lateinit var tabAdapter: ChatTabAdapter
    private lateinit var tabLayoutMediator: TabLayoutMediator
    private lateinit var suggestionAdapter: SuggestionsArrayAdapter
    private var currentMediaUri = Uri.EMPTY
    private val tabSelectionListener = TabSelectionListener()

    private val requestImageCapture = registerForActivityResult(StartActivityForResult()) { if (it.resultCode == Activity.RESULT_OK) handleCaptureRequest(imageCapture = true) }
    private val requestVideoCapture = registerForActivityResult(StartActivityForResult()) { if (it.resultCode == Activity.RESULT_OK) handleCaptureRequest() }
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

            mainViewModel.uploadMedia(copy)
        } catch (t: Throwable) {
            copy.delete()
            showSnackBar(getString(R.string.snackbar_upload_failed))
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        tabAdapter = ChatTabAdapter(parentFragment = this, dankChatPreferenceStore = dankChatPreferences)
        bindingRef = MainFragmentBinding.inflate(inflater, container, false).apply {
            emoteMenuBottomSheetBehavior = BottomSheetBehavior.from(bottomSheetFrame).apply {
                addBottomSheetCallback(emoteMenuCallBack)
                skipCollapsed = true
            }
            chatViewpager.setup()
            input.setup(this)

            childFragmentManager.findFragmentById(R.id.mention_fragment)?.let {
                mentionBottomSheetBehavior = BottomSheetBehavior.from(it.requireView()).apply { setupMentionSheet() }
            }

            tabLayoutMediator = TabLayoutMediator(tabs, chatViewpager) { tab, position ->
                tab.text = tabAdapter.channelsWithRenames[position].value
            }

            tabs.setInitialColors()
            tabs.getTabAt(tabs.selectedTabPosition)?.removeBadge()
            tabs.addOnTabSelectedListener(tabSelectionListener)

            addChannelsButton.setOnClickListener { navigateSafe(R.id.action_mainFragment_to_addChannelDialogFragment) }
            toggleFullscreen.setOnClickListener { mainViewModel.toggleFullscreen() }
            toggleStream.setOnClickListener {
                mainViewModel.toggleStream()
                root.requestApplyInsets()
            }
            changeRoomstate.setOnClickListener { showRoomStateDialog() }
            showChips.setOnClickListener { mainViewModel.toggleChipsExpanded() }
            splitThumb?.setOnTouchListener { _, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_MOVE -> {
                        val guideline = splitGuideline ?: return@setOnTouchListener false
                        val width = resources.displayMetrics.widthPixels
                        guideline.updateLayoutParams<ConstraintLayout.LayoutParams> {
                            guidePercent = (event.rawX / width).coerceIn(MIN_GUIDELINE_PERCENT, MAX_GUIDELINE_PERCENT)
                        }
                        true
                    }

                    MotionEvent.ACTION_DOWN -> true
                    else                    -> false
                }
            }
        }

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        initPreferences(view.context)
        binding.splitThumb?.background?.alpha = 150
        activity?.addMenuProvider(object : MenuProvider {
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
//                menuInflater.inflateWithCrowdin(R.menu.menu, menu, resources)
            }

            override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                when (menuItem.itemId) {
                    R.id.menu_reconnect                -> mainViewModel.reconnect()
                    R.id.menu_login, R.id.menu_relogin -> openLogin()
                    R.id.menu_logout                   -> showLogoutConfirmationDialog()
                    R.id.menu_add                      -> navigateSafe(R.id.action_mainFragment_to_addChannelDialogFragment)
                    R.id.menu_mentions                 -> {
                        closeEmoteMenu()
                        mentionBottomSheetBehavior?.expand()
                    }

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
        }, viewLifecycleOwner, Lifecycle.State.RESUMED)

        mainViewModel.apply {
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
            collectFlow(connectionState) { state ->
                binding.inputLayout.hint = when (state) {
                    ConnectionState.CONNECTED               -> getString(R.string.hint_connected)
                    ConnectionState.CONNECTED_NOT_LOGGED_IN -> getString(R.string.hint_not_logged_int)
                    ConnectionState.DISCONNECTED            -> getString(R.string.hint_disconnected)
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
                val index = tabAdapter.channels.indexOf(channel)
                binding.tabs.getTabAt(index)?.removeBadge()
                mainViewModel.clearMentionCount(channel)
                mainViewModel.clearUnreadMessage(channel)
            }
            collectFlow(shouldShowTabs) { binding.tabs.isVisible = it }
            collectFlow(shouldShowChipToggle) { binding.showChips.isVisible = it }
            collectFlow(areChipsExpanded) {
                val resourceId = if (it) R.drawable.ic_keyboard_arrow_up else R.drawable.ic_keyboard_arrow_down
                binding.showChips.setChipIconResource(resourceId)
            }
            collectFlow(shouldShowExpandedChips) { binding.toggleFullscreen.isVisible = it }
            collectFlow(shouldShowStreamToggle) { binding.toggleStream.isVisible = it }
            collectFlow(hasModInChannel) { binding.changeRoomstate.isVisible = it }
            collectFlow(shouldShowViewPager) {
                binding.chatViewpager.isVisible = it
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
            collectFlow(channels) {
                if (!it.isNullOrEmpty()) {
                    mainViewModel.fetchStreamData(it)
                }
            }
            collectFlow(currentStreamedChannel) {
                val isActive = it != null
                binding.streamWebviewWrapper.isVisible = isActive
                if (!isLandscape) {
                    return@collectFlow
                }

                binding.splitThumb?.isVisible = isActive
                binding.splitGuideline?.updateLayoutParams<ConstraintLayout.LayoutParams> {
                    guidePercent = when {
                        isActive -> DEFAULT_GUIDELINE_PERCENT
                        else     -> DISABLED_GUIDELINE_PERCENT
                    }
                }
            }
            collectFlow(useCustomBackHandling) { onBackPressedCallback.isEnabled = it }
            collectFlow(dankChatViewModel.validationResult) {
                when (it) {
                    // wait for username to be validated before showing snackbar
                    is ValidationResult.User             -> showSnackBar(getString(R.string.snackbar_login, it.username))
                    is ValidationResult.IncompleteScopes -> MaterialAlertDialogBuilder(requireContext())
                        .setTitle(R.string.login_outdated_title)
                        .setMessage(R.string.login_outdated_message)
                        .setPositiveButton(R.string.oauth_expired_login_again) { _, _ -> openLogin() }
                        .setNegativeButton(R.string.dialog_dismiss) { _, _ -> }
                        .create().show()

                    ValidationResult.TokenInvalid        -> {
                        MaterialAlertDialogBuilder(requireContext())
                            .setTitle(R.string.oauth_expired_title)
                            .setMessage(R.string.oauth_expired_message)
                            .setPositiveButton(R.string.oauth_expired_login_again) { _, _ -> openLogin() }
                            .setNegativeButton(R.string.dialog_dismiss) { _, _ -> } // default action is dismissing anyway
                            .create().show()
                    }

                    ValidationResult.Failure             -> showSnackBar(getString(R.string.oauth_verify_failed))
                }
            }
        }

        val navBackStackEntry = navController.getBackStackEntry(R.id.mainFragment)
        val handle = navBackStackEntry.savedStateHandle
        val observer = LifecycleEventObserver { _, event ->
            if (event != Lifecycle.Event.ON_RESUME) return@LifecycleEventObserver
            handle.keys().forEach { key ->
                when (key) {
                    LOGIN_REQUEST_KEY       -> handle.withData(key, ::handleLoginRequest)
                    ADD_CHANNEL_REQUEST_KEY -> handle.withData(key, ::addChannel)
                    HISTORY_DISCLAIMER_KEY  -> handle.withData(key, ::handleMessageHistoryDisclaimerResult)
                    USER_POPUP_RESULT_KEY   -> handle.withData(key, ::handleUserPopupResult)
                    LOGOUT_REQUEST_KEY      -> handle.withData<Boolean>(key) {
                        showLogoutConfirmationDialog()
                    }

                    CHANNELS_REQUEST_KEY    -> handle.withData<Array<UserName>>(key) {
                        updateChannels(it.toList())
                    }
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

        val channels = dankChatPreferences.getChannels()
        tabAdapter.updateFragments(channels)
        @SuppressLint("WrongConstant")
        binding.chatViewpager.offscreenPageLimit = OFFSCREEN_PAGE_LIMIT
        tabLayoutMediator.attach()

        (requireActivity() as AppCompatActivity).apply {
            setSupportActionBar(binding.toolbar)
            onBackPressedDispatcher.addCallback(viewLifecycleOwner, onBackPressedCallback)

            var wasKeyboardOpen = false
            ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
                val isKeyboardVisible = insets.isVisible(WindowInsetsCompat.Type.ime())
                if (wasKeyboardOpen == isKeyboardVisible) {
                    return@setOnApplyWindowInsetsListener insets
                }

                wasKeyboardOpen = isKeyboardVisible
                if (binding.input.isFocused && !isKeyboardVisible) {
                    binding.input.clearFocus()
                }

                insets
            }
            ViewCompat.setOnApplyWindowInsetsListener(binding.showChips) { v, insets ->
                val needsExtraMargin = binding.streamWebviewWrapper.isVisible || isLandscape || !mainViewModel.isFullscreenFlow.value
                val extraMargin = when {
                    needsExtraMargin -> 0
                    else             -> insets.getInsets(WindowInsetsCompat.Type.displayCutout()).top
                }

                v.updateLayoutParams<ConstraintLayout.LayoutParams> {
                    topMargin = 8.px + extraMargin
                }
                WindowInsetsCompat.CONSUMED
            }
            ViewCompat.setOnApplyWindowInsetsListener(binding.inputLayout) { v, insets ->
                v.updatePadding(bottom = insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom)
                WindowInsetsCompat.CONSUMED
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

    override fun onPause() {
        binding.input.clearFocus()
        mainViewModel.cancelStreamData()
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        closeEmoteMenu()
        changeActionBarVisibility(mainViewModel.isFullscreenFlow.value)

        (activity as? MainActivity)?.apply {
            val channel = channelToOpen
            if (channel != null) {
                val index = mainViewModel.getChannels().indexOf(channel)
                if (index >= 0) {
                    when (index) {
                        binding.chatViewpager.currentItem -> clearNotificationsOfChannel(channel)
                        else                              -> binding.chatViewpager.setCurrentItem(index, false)
                    }
                }
                channelToOpen = null
            } else {
                val activeChannel = mainViewModel.activeChannel.value ?: return
                clearNotificationsOfChannel(activeChannel)
            }
        }
    }

    override fun onDestroyView() {
        binding.tabs.removeOnTabSelectedListener(tabSelectionListener)
        binding.chatViewpager.unregisterOnPageChangeCallback(pageChangeCallback)
        emoteMenuBottomSheetBehavior?.removeBottomSheetCallback(emoteMenuCallBack)
        tabLayoutMediator.detach()
        emoteMenuBottomSheetBehavior = null
        mentionBottomSheetBehavior = null
        binding.chatViewpager.adapter = null
        bindingRef = null
        if (::preferences.isInitialized) {
            preferences.unregisterOnSharedPreferenceChangeListener(preferenceListener)
        }
        super.onDestroyView()
    }

    fun openUserPopup(
        targetUserId: UserId,
        targetUserName: UserName,
        targetDisplayName: DisplayName,
        messageId: String,
        channel: UserName?,
        badges: List<Badge>,
        isWhisperPopup: Boolean = false
    ) {
        val directions = MainFragmentDirections.actionMainFragmentToUserPopupDialogFragment(
            targetUserId = targetUserId,
            targetUserName = targetUserName,
            targetDisplayName = targetDisplayName,
            messageId = messageId,
            channel = channel,
            isWhisperPopup = isWhisperPopup,
            badges = badges.toTypedArray(),
        )
        navigateSafe(directions)
    }

    fun mentionUser(user: UserName, display: DisplayName) {
        val template = preferences.getString(getString(R.string.preference_mention_format_key), "name") ?: "name"
        val mention = "${template.replace("name", user.valueOrDisplayName(display))} "
        insertText(mention)
    }

    fun whisperUser(user: UserName) {
        if (!binding.input.isEnabled) return

        val current = binding.input.text.toString()
        val text = "/w $user $current"

        binding.input.setText(text)
        binding.input.setSelection(text.length)
    }

    fun insertText(text: String) {
        if (!binding.input.isEnabled) return

        val current = binding.input.text.toString()
        val index = binding.input.selectionStart.takeIf { it >= 0 } ?: current.length
        val builder = StringBuilder(current).insert(index, text)

        binding.input.setText(builder.toString())
        binding.input.setSelection(index + text.length)
    }

    fun insertEmote(emote: GenericEmote) {
        insertText("${emote.code} ")
        mainViewModel.addEmoteUsage(emote)
    }

    fun showEmoteMenu() = emoteMenuBottomSheetBehavior?.expand()

    private fun openLogin() {
        val directions = MainFragmentDirections.actionMainFragmentToLoginFragment()
        navigateSafe(directions)
        hideKeyboard()
    }

    private fun handleMessageHistoryDisclaimerResult(result: Boolean) {
        dankChatPreferences.hasMessageHistoryAcknowledged = true
        preferences.edit { putBoolean(getString(R.string.preference_load_message_history_key), result) }
        mainViewModel.loadData()
    }

    private fun handleUserPopupResult(result: UserPopupResult) = when (result) {
        is UserPopupResult.Error   -> binding.root.showShortSnackbar(getString(R.string.user_popup_error, result.throwable?.message.orEmpty()))
        is UserPopupResult.Mention -> mentionUser(result.targetUser, result.targetDisplayName)
        is UserPopupResult.Whisper -> whisperUser(result.targetUser)
    }

    private fun addChannel(channel: String) {
        val lowerCaseChannel = channel.lowercase().removePrefix("#").toUserName()
        var newTabIndex = mainViewModel.getChannels().indexOf(lowerCaseChannel)
        if (newTabIndex == -1) {
            val updatedChannels = mainViewModel.joinChannel(lowerCaseChannel)
            newTabIndex = updatedChannels.lastIndex
            mainViewModel.loadData(channelList = listOf(lowerCaseChannel))
            dankChatPreferences.channelsString = updatedChannels.joinToString(separator = ",")

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
                action = getString(R.string.snackbar_retry) to { mainViewModel.uploadMedia(result.mediaFile) })

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
                    multiLine = true,
                    duration = Snackbar.LENGTH_LONG,
                    action = getString(R.string.snackbar_retry) to {
                        mainViewModel.retryDataLoading(result.dataFailures, result.chatFailures)
                    })
            }
        }
    }

    private fun handleErrorEvent(event: MainEvent.Error) {
        if (preferences.getBoolean(getString(R.string.preference_debug_mode_key), false)) {
            binding.root.showErrorDialog(event.throwable)
        }
    }

    private fun handleLoginRequest(success: Boolean) {
        val name = dankChatPreferences.userName
        if (success && name != null) {
            mainViewModel.closeAndReconnect()
            showSnackBar(getString(R.string.snackbar_login, name))
        } else {
            dankChatPreferences.clearLogin()
            showSnackBar(getString(R.string.snackbar_login_failed))
        }
    }

    private fun handleCaptureRequest(imageCapture: Boolean = false) {
        if (currentMediaUri == Uri.EMPTY) return
        var mediaFile: File? = null

        try {
            mediaFile = currentMediaUri.toFile()
            currentMediaUri = Uri.EMPTY

            // only remove exif data if an image was selected
            if (imageCapture) {
                mediaFile.removeExifAttributes()
            }

            mainViewModel.uploadMedia(mediaFile)
        } catch (e: IOException) {
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
            URL(dankChatPreferences.customImageUploader.uploadUrl).host
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
        hideKeyboard()
        (activity as? MainActivity)?.setFullScreen(isFullscreen)
    }

    private fun clear() {
        val position = binding.tabs.selectedTabPosition
        if (position in tabAdapter.channels.indices)
            mainViewModel.clear(tabAdapter.channels[position])
    }

    private fun reloadEmotes(channel: UserName? = null) {
        val position = channel?.let(tabAdapter.channels::indexOf) ?: binding.tabs.selectedTabPosition
        if (position in tabAdapter.channels.indices) {
            mainViewModel.reloadEmotes(tabAdapter.channels[position])
        }
    }

    private fun initPreferences(context: Context) {
        val keepScreenOnKey = getString(R.string.preference_keep_screen_on_key)
        val suggestionsKey = getString(R.string.preference_suggestions_key)
        if (dankChatPreferences.isLoggedIn && dankChatPreferences.oAuthKey.isNullOrBlank()) {
            dankChatPreferences.clearLogin()
        }

        preferences = PreferenceManager.getDefaultSharedPreferences(context)
        if (::preferenceListener.isInitialized) {
            preferences.unregisterOnSharedPreferenceChangeListener(preferenceListener)
        }

        preferenceListener = SharedPreferences.OnSharedPreferenceChangeListener { p, key ->
            when (key) {
                keepScreenOnKey -> keepScreenOn(p.getBoolean(key, true))
                suggestionsKey  -> binding.input.setSuggestionAdapter(p.getBoolean(key, true), suggestionAdapter)
            }
        }
        preferences.apply {
            registerOnSharedPreferenceChangeListener(preferenceListener)
            keepScreenOn(getBoolean(keepScreenOnKey, true))
            binding.input.setSuggestionAdapter(getBoolean(suggestionsKey, true), suggestionAdapter)
        }
    }

    private fun showSnackBar(
        message: String,
        multiLine: Boolean = false,
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
                    if (multiLine) {
                        (view.findViewById<View?>(com.google.android.material.R.id.snackbar_text) as? TextView?)?.isSingleLine = false
                    }

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
        val activeChannel = mainViewModel.getActiveChannel() ?: return
        val channels = mainViewModel.getChannels().ifEmpty { return }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.confirm_channel_removal_title)
            // should give user more info that it's gonna delete the currently active channel (unlike when clicking delete from manage channels list, where is very obvious)
            .setMessage(getString(R.string.confirm_channel_removal_message_named, activeChannel))
            .setPositiveButton(R.string.confirm_channel_removal_positive_button) { _, _ ->
                val updatedChannels = channels - activeChannel
                updateChannels(updatedChannels)
            }
            .setNegativeButton(R.string.dialog_cancel) { _, _ -> }
            .create().show()
    }

    private fun openManageChannelsDialog() {
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

    private fun updateChannels(updatedChannels: List<UserName>) {
        val oldChannels = mainViewModel.getChannels()
        val oldIndex = binding.chatViewpager.currentItem
        val oldActiveChannel = oldChannels[oldIndex]

        val index = updatedChannels.indexOf(oldActiveChannel).coerceAtLeast(0)
        val activeChannel = updatedChannels.getOrNull(index)

        tabAdapter.updateFragments(updatedChannels)
        mainViewModel.updateChannels(updatedChannels)
        mainViewModel.setActiveChannel(activeChannel)

        dankChatPreferences.channelsString = updatedChannels
            .takeIf { it.isNotEmpty() }
            ?.joinToString(separator = ",")
            ?.also {
                binding.chatViewpager.setCurrentItem(index, false)
                binding.root.postDelayed(TAB_SCROLL_DELAY_MS) {
                    binding.tabs.setScrollPosition(index, 0f, false)
                }
            }

        activity?.invalidateMenu()
        updateChannelMentionBadges(channels = mainViewModel.channelMentionCount.firstValueOrNull.orEmpty())
        updateUnreadChannelTabColors(channels = mainViewModel.unreadMessagesMap.firstValueOrNull.orEmpty())
    }

    private fun updateUnreadChannelTabColors(channels: Map<UserName, Boolean>) {
        channels.forEach { (channel, _) ->
            when (val index = tabAdapter.channels.indexOf(channel)) {
                binding.tabs.selectedTabPosition -> mainViewModel.clearUnreadMessage(channel)
                else                             -> {
                    val tab = binding.tabs.getTabAt(index)
                    tab?.setTextColor(R.attr.colorOnSurface)
                }
            }
        }
    }

    private fun updateChannelMentionBadges(channels: Map<UserName, Int>) {
        channels.forEach { (channel, count) ->
            val index = tabAdapter.channels.indexOf(channel)
            if (count > 0) {
                when (index) {
                    binding.tabs.selectedTabPosition -> mainViewModel.clearMentionCount(channel) // mention is in active channel
                    else                             -> binding.tabs.getTabAt(index)?.apply { orCreateBadge }
                }
            } else {
                binding.tabs.getTabAt(index)?.removeBadge()
            }
        }
    }

    private fun BottomSheetBehavior<View>.setupMentionSheet() {
        hide()
        addBottomSheetCallback(object : BottomSheetBehavior.BottomSheetCallback() {
            override fun onSlide(bottomSheet: View, slideOffset: Float) = Unit
            override fun onStateChanged(bottomSheet: View, newState: Int) {
                mainViewModel.setMentionSheetOpen(mentionBottomSheetBehavior?.isMoving == true || mentionBottomSheetBehavior?.isVisible == true)
                when {
                    mentionBottomSheetBehavior?.isExpanded == true -> mainViewModel.setSuggestionChannel("w".toUserName())
                    mentionBottomSheetBehavior?.isHidden == true   -> mainViewModel.setSuggestionChannel(tabAdapter.channels[binding.chatViewpager.currentItem])
                }
            }
        })
    }

    private fun ViewPager2.setup() {
        adapter = tabAdapter
        reduceDragSensitivity()
        registerOnPageChangeCallback(pageChangeCallback)
    }

    private fun DankChatInputLayout.setupSendButton() {
        setEndIconDrawable(R.drawable.ic_send)
        val touchListenerAdded = when {
            dankChatPreferences.repeatedSendingEnabled -> {
                setEndIconOnClickListener { } // for ripple effects
                setEndIconTouchListener { holdTouchEvent ->
                    when (holdTouchEvent) {
                        DankChatInputLayout.TouchEvent.CLICK      -> sendMessage()
                        DankChatInputLayout.TouchEvent.LONG_CLICK -> getLastMessage()
                        else                                      -> repeatSendMessage(holdTouchEvent)
                    }
                }
            }

            else                                       -> false
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
                closeEmoteMenu()
                return@setStartIconOnClickListener
            }

            if (isLandscape) {
                hideKeyboard()
                binding.input.clearFocus()
            }

            childFragmentManager.commit {
                replace(R.id.bottom_sheet_frame, EmoteMenuFragment())
            }
        }
    }

    private fun closeEmoteMenu() {
        val behavior = emoteMenuBottomSheetBehavior ?: return
        if (!mainViewModel.isEmoteSheetOpen) {
            return
        }

        mainViewModel.setEmoteSheetOpen(false)
        behavior.hide()
        val existing = childFragmentManager.fragments.find { it is EmoteMenuFragment } ?: return
        childFragmentManager.commit {
            remove(existing)
        }
    }

    private val emoteMenuCallBack = object : BottomSheetBehavior.BottomSheetCallback() {
        override fun onSlide(bottomSheet: View, slideOffset: Float) = Unit

        override fun onStateChanged(bottomSheet: View, newState: Int) {
            val behavior = emoteMenuBottomSheetBehavior ?: return
            mainViewModel.setEmoteSheetOpen(behavior.isMoving || behavior.isVisible)
            binding.streamWebviewWrapper.isVisible = newState == BottomSheetBehavior.STATE_HIDDEN && mainViewModel.isStreamActive
            if (!mainViewModel.isFullscreenFlow.value && isLandscape) {
                when (newState) {
                    BottomSheetBehavior.STATE_EXPANDED, BottomSheetBehavior.STATE_COLLAPSED -> {
                        (activity as? AppCompatActivity)?.supportActionBar?.hide()
                        binding.tabs.visibility = View.GONE
                    }

                    else                                                                    -> {
                        (activity as? AppCompatActivity)?.supportActionBar?.show()
                        binding.tabs.visibility = View.VISIBLE
                    }
                }
            }
        }
    }

    private fun DankChatInput.setup(binding: MainFragmentBinding) {
        setTokenizer(SpaceTokenizer())
        suggestionAdapter = SuggestionsArrayAdapter(binding.input.context, dankChatPreferences) { count ->
            dropDownHeight = if (count > 4) {
                (binding.root.measuredHeight / 2.0).roundToInt()
            } else {
                ViewGroup.LayoutParams.WRAP_CONTENT
            }
            dropDownWidth = (binding.root.measuredWidth * 0.6).roundToInt()
        }

        setOnItemClickListener { parent, _, position, _ ->
            val suggestion = parent.getItemAtPosition(position)
            if (suggestion is Suggestion.EmoteSuggestion) {
                mainViewModel.addEmoteUsage(suggestion.emote)
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
            mainViewModel.setShowChips(!hasFocus)

            if (isPortrait) {
                val mentionsView = childFragmentManager.findFragmentById(R.id.mention_fragment)?.view
                binding.bottomSheetFrame
                    .takeIf { !mainViewModel.isEmoteSheetOpen }
                    ?.isInvisible = true

                mentionsView
                    .takeIf { mentionBottomSheetBehavior?.isVisible == false }
                    ?.isInvisible = true

                binding.root.post {
                    (activity as? MainActivity)?.setFullScreen(enabled = !hasFocus && isFullscreen, changeActionBarVisibility = false)
                    binding.bottomSheetFrame.isInvisible = false
                    mentionsView?.isInvisible = false
                }
                return@setOnFocusChangeListener
            }

            binding.tabs.isVisible = !hasFocus && !isFullscreen
            binding.streamWebviewWrapper.isVisible = !hasFocus && !mainViewModel.isEmoteSheetOpen && mainViewModel.isStreamActive

            when {
                hasFocus -> (activity as? MainActivity)?.apply {
                    supportActionBar?.hide()
                    setFullScreen(enabled = false, changeActionBarVisibility = false)
                }

                else     -> (activity as? MainActivity)?.setFullScreen(isFullscreen)
            }

            binding.root.requestApplyInsets()
        }
    }

    companion object {
        private const val DISABLED_GUIDELINE_PERCENT = 0f
        private const val DEFAULT_GUIDELINE_PERCENT = 0.6f
        private const val MAX_GUIDELINE_PERCENT = 0.8f
        private const val MIN_GUIDELINE_PERCENT = 0.2f
        private const val CLIPBOARD_LABEL = "dankchat_media_url"
        private const val TAB_SCROLL_DELAY_MS = 1000 / 60 * 10L
        private const val OFFSCREEN_PAGE_LIMIT = 2

        const val LOGOUT_REQUEST_KEY = "logout_key"
        const val LOGIN_REQUEST_KEY = "login_key"
        const val CHANNELS_REQUEST_KEY = "channels_key"
        const val ADD_CHANNEL_REQUEST_KEY = "add_channel_key"
        const val HISTORY_DISCLAIMER_KEY = "history_disclaimer_key"
        const val USER_POPUP_RESULT_KEY = "user_popup_key"
    }
}

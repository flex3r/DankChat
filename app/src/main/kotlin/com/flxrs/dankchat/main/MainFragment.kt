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
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.addCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.launch
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.ContextCompat.getSystemService
import androidx.core.content.FileProvider
import androidx.core.content.edit
import androidx.core.net.toFile
import androidx.core.net.toUri
import androidx.core.view.*
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.navigation.NavController
import androidx.navigation.fragment.findNavController
import androidx.preference.PreferenceManager
import androidx.viewpager2.widget.ViewPager2
import com.flxrs.dankchat.BuildConfig
import com.flxrs.dankchat.R
import com.flxrs.dankchat.chat.ChatTabAdapter
import com.flxrs.dankchat.chat.menu.EmoteMenuAdapter
import com.flxrs.dankchat.chat.menu.EmoteMenuTab
import com.flxrs.dankchat.chat.suggestion.SuggestionsArrayAdapter
import com.flxrs.dankchat.chat.suggestion.SpaceTokenizer
import com.flxrs.dankchat.chat.suggestion.Suggestion
import com.flxrs.dankchat.chat.user.UserPopupResult
import com.flxrs.dankchat.data.state.DataLoadingState
import com.flxrs.dankchat.data.state.ImageUploadState
import com.flxrs.dankchat.data.twitch.connection.ConnectionState
import com.flxrs.dankchat.data.twitch.emote.GenericEmote
import com.flxrs.dankchat.databinding.EditDialogBinding
import com.flxrs.dankchat.databinding.MainFragmentBinding
import com.flxrs.dankchat.preferences.DankChatPreferenceStore
import com.flxrs.dankchat.utils.*
import com.flxrs.dankchat.utils.extensions.*
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.card.MaterialCardView
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.BaseTransientBottomBar
import com.google.android.material.snackbar.BaseTransientBottomBar.BaseCallback
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.tabs.TabLayoutMediator
import com.google.android.material.textfield.TextInputLayout
import dagger.hilt.android.AndroidEntryPoint
import java.io.File
import java.io.IOException
import java.util.*
import javax.inject.Inject
import kotlin.math.roundToInt

@AndroidEntryPoint
class MainFragment : Fragment() {

    private val mainViewModel: MainViewModel by viewModels()
    private val navController: NavController by lazy { findNavController() }
    private var bindingRef: MainFragmentBinding? = null
    private val binding get() = bindingRef!!
    private var emoteMenuBottomSheetBehavior: BottomSheetBehavior<MaterialCardView>? = null
    private var mentionBottomSheetBehavior: BottomSheetBehavior<View>? = null


    @Inject
    lateinit var dankChatPreferences: DankChatPreferenceStore

    private lateinit var preferenceListener: SharedPreferences.OnSharedPreferenceChangeListener
    private lateinit var preferences: SharedPreferences
    private lateinit var tabAdapter: ChatTabAdapter
    private lateinit var tabLayoutMediator: TabLayoutMediator
    private lateinit var emoteMenuAdapter: EmoteMenuAdapter
    private lateinit var suggestionAdapter: SuggestionsArrayAdapter
    private var currentMediaUri = Uri.EMPTY
    private val tabSelectionListener = TabSelectionListener()

    private val requestImageCapture = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { if (it.resultCode == Activity.RESULT_OK) handleCaptureRequest(imageCapture = true) }
    private val requestVideoCapture = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { if (it.resultCode == Activity.RESULT_OK) handleCaptureRequest() }
    private val requestGalleryMedia = registerForActivityResult(GetImageOrVideoContract()) { uri ->
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
        tabAdapter = ChatTabAdapter(this)
        emoteMenuAdapter = EmoteMenuAdapter(::insertEmote)

        bindingRef = MainFragmentBinding.inflate(inflater, container, false).apply {
            emoteMenuBottomSheetBehavior = BottomSheetBehavior.from(emoteMenuBottomSheet)
            vm = mainViewModel
            lifecycleOwner = this@MainFragment
            chatViewpager.setup(this)
            input.setup(this)

            childFragmentManager.findFragmentById(R.id.mention_fragment)?.let {
                mentionBottomSheetBehavior = BottomSheetBehavior.from(it.requireView()).apply { setupMentionSheet() }
            }

            tabLayoutMediator = TabLayoutMediator(tabs, chatViewpager) { tab, position ->
                val channelName = tabAdapter.titleList[position]
                tab.text = dankChatPreferences.getRenamedChannel(channelName) ?: channelName
                tab.setInitialColor()
            }.apply { attach() }

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
                    val hasChannels = !mainViewModel.getChannels().isNullOrEmpty()
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
                            icon.setTintList(ColorStateList.valueOf(color))
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
                    R.id.menu_reconnect      -> mainViewModel.reconnect()
                    R.id.menu_login          -> openLogin()
                    R.id.menu_relogin        -> openLogin(isRelogin = true)
                    R.id.menu_logout         -> showLogoutConfirmationDialog()
                    R.id.menu_add            -> navigateSafe(R.id.action_mainFragment_to_addChannelDialogFragment)
                    R.id.menu_mentions       -> mentionBottomSheetBehavior?.expand()
                    R.id.menu_open_channel   -> openChannel()
                    R.id.menu_remove_channel -> removeChannel()
                    R.id.menu_report_channel -> reportChannel()
                    R.id.menu_block_channel  -> blockChannel()
                    R.id.menu_manage         -> openManageChannelsDialog()
                    R.id.menu_reload_emotes  -> reloadEmotes()
                    R.id.menu_choose_media   -> showNuulsUploadDialogIfNotAcknowledged { requestGalleryMedia.launch() }
                    R.id.menu_capture_image  -> startCameraCapture()
                    R.id.menu_capture_video  -> startCameraCapture(captureVideo = true)
                    R.id.menu_clear          -> clear()
                    R.id.menu_settings       -> navigateSafe(R.id.action_mainFragment_to_overviewSettingsFragment).also { hideKeyboard() }
                    else                     -> return false
                }
                return true
            }
        }, viewLifecycleOwner, Lifecycle.State.RESUMED)

        mainViewModel.apply {
            collectFlow(imageUploadEventFlow, ::handleImageUploadState)
            collectFlow(dataLoadingEventFlow, ::handleDataLoadingState)
            collectFlow(shouldShowUploadProgress) { activity?.invalidateMenu() }
            collectFlow(suggestions, ::setSuggestions)
            collectFlow(emoteTabItems, emoteMenuAdapter::submitList)
            collectFlow(isFullscreenFlow) { changeActionBarVisibility(it) }
            collectFlow(canType) {
                when {
                    it   -> binding.inputLayout.setup()
                    else -> with(binding.inputLayout) {
                        setEndIconOnClickListener(null)
                        setEndIconOnLongClickListener(null)
                        setStartIconOnClickListener(null)
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
                (activity as? MainActivity)?.notificationService?.setActiveChannel(channel) // TODO move
                val index = tabAdapter.titleList.indexOf(channel)
                binding.tabs.getTabAt(index)?.removeBadge()
                mainViewModel.clearMentionCount(channel)
                mainViewModel.clearUnreadMessage(channel)
            }

            collectFlow(events) {
                when (it) {
                    is MainViewModel.Event.Error -> handleErrorEvent(it)
                }
            }
            collectFlow(channelMentionCount) { channels ->
                channels.forEach { (channel, count) ->
                    val index = tabAdapter.titleList.indexOf(channel)
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
            collectFlow(unreadMessagesMap) { channels ->
                channels.forEach { (channel, _) ->
                    when (val index = tabAdapter.titleList.indexOf(channel)) {
                        binding.tabs.selectedTabPosition -> mainViewModel.clearUnreadMessage(channel)
                        else                             -> {
                            val tab = binding.tabs.getTabAt(index)
                            tab?.setTextColor(R.attr.colorOnSurface)
                        }
                    }
                }
            }
            collectFlow(shouldColorNotification) { activity?.invalidateMenu() }
            collectFlow(channels) {
                if (!it.isNullOrEmpty()) {
                    mainViewModel.fetchStreamData(it)
                }
            }
            collectFlow(currentStreamedChannel) {
                if (!isLandscape) {
                    return@collectFlow
                }

                binding.splitThumb?.isVisible = it.isNotBlank()
                binding.splitGuideline?.updateLayoutParams<ConstraintLayout.LayoutParams> {
                    guidePercent = when {
                        it.isBlank() -> DISABLED_GUIDELINE_PERCENT
                        else         -> DEFAULT_GUIDELINE_PERCENT
                    }
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
                    THEME_CHANGED_KEY       -> handle.withData<Boolean>(key) {
                        binding.root.post { ActivityCompat.recreate(requireActivity()) }
                    }
                    CHANNELS_REQUEST_KEY    -> handle.withData<Array<String>>(key) {
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
            dankChatPreferences.userIdString = "${dankChatPreferences.userId}"
        }

        val channels = dankChatPreferences.getChannels()
        channels.forEach { tabAdapter.addFragment(it) }
        binding.chatViewpager.offscreenPageLimit = calculatePageLimit(channels.size)

        (requireActivity() as AppCompatActivity).apply {
            setSupportActionBar(binding.toolbar)
            onBackPressedDispatcher.addCallback(viewLifecycleOwner) {
                when {
                    emoteMenuBottomSheetBehavior?.isVisible == true -> emoteMenuBottomSheetBehavior?.hide()
                    mentionBottomSheetBehavior?.isVisible == true   -> mentionBottomSheetBehavior?.hide()
                    mainViewModel.isFullscreen                      -> mainViewModel.toggleFullscreen()
                    else                                            -> finish()
                }
            }

            ViewCompat.setOnApplyWindowInsetsListener(binding.showChips) { v, insets ->
                val needsExtraMargin = binding.streamWebview.isVisible || isLandscape || !mainViewModel.isFullscreenFlow.value
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
                    mainViewModel.loadData(
                        channelList = channels,
                        isUserChange = false,
                        loadTwitchData = true,
                    )
                    val name = dankChatPreferences.userName
                    if (dankChatPreferences.isLoggedIn && name != null) {
                        showSnackBar(getString(R.string.snackbar_login, name))
                    }
                }
            }
        }
    }

    override fun onPause() {
        binding.input.clearFocus()
        mainViewModel.cancelStreamDataTimer()
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        emoteMenuBottomSheetBehavior?.hide()
        changeActionBarVisibility(mainViewModel.isFullscreenFlow.value)

        (activity as? MainActivity)?.apply {
            if (channelToOpen.isNotBlank()) {
                val index = mainViewModel.getChannels().indexOf(channelToOpen)
                if (index >= 0) {
                    when (index) {
                        binding.chatViewpager.currentItem -> clearNotificationsOfChannel(channelToOpen)
                        else                              -> binding.chatViewpager.setCurrentItem(index, false)
                    }
                }
                channelToOpen = ""
            } else {
                val activeChannel = mainViewModel.activeChannel.value
                clearNotificationsOfChannel(activeChannel)
            }
        }
    }

    override fun onDestroyView() {
        bindingRef?.tabs?.removeOnTabSelectedListener(tabSelectionListener)
        bindingRef = null
        emoteMenuBottomSheetBehavior = null
        mentionBottomSheetBehavior = null
        if (::preferences.isInitialized) {
            preferences.unregisterOnSharedPreferenceChangeListener(preferenceListener)
        }
        super.onDestroyView()
    }

    fun openUserPopup(targetUserId: String, targetUserName: String, messageId: String, channel: String?, isWhisperPopup: Boolean = false) {
        val directions = MainFragmentDirections.actionMainFragmentToUserPopupDialogFragment(
            targetUserId = targetUserId,
            targetUserName = targetUserName,
            messageId = messageId,
            channel = channel,
            isWhisperPopup = isWhisperPopup
        )
        navigateSafe(directions)
    }

    fun mentionUser(user: String) {
        val template = preferences.getString(getString(R.string.preference_mention_format_key), "name") ?: "name"
        val mention = "${template.replace("name", user)} "
        insertText(mention)
    }

    fun whisperUser(user: String) {
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

    private fun openLogin(isRelogin: Boolean = false) {
        val directions = MainFragmentDirections.actionMainFragmentToLoginFragment(isRelogin)
        navigateSafe(directions)
        hideKeyboard()
    }

    private fun handleMessageHistoryDisclaimerResult(result: Boolean) {
        dankChatPreferences.hasMessageHistoryAcknowledged = true
        preferences.edit { putBoolean(getString(R.string.preference_load_message_history_key), result) }
        mainViewModel.loadData(
            isUserChange = false,
            loadTwitchData = true,
        )

        val name = dankChatPreferences.userName
        if (dankChatPreferences.isLoggedIn && name != null) {
            showSnackBar(getString(R.string.snackbar_login, name))
        }
    }

    private fun handleUserPopupResult(result: UserPopupResult) = when (result) {
        is UserPopupResult.Error   -> binding.root.showShortSnackbar(getString(R.string.user_popup_error, result.throwable?.message.orEmpty()))
        is UserPopupResult.Mention -> mentionUser(result.targetUser)
        is UserPopupResult.Whisper -> whisperUser(result.targetUser)
    }

    private fun addChannel(channel: String) {
        val lowerCaseChannel = channel.lowercase(Locale.getDefault()).removePrefix("#")
        var newTabIndex = mainViewModel.getChannels().indexOf(lowerCaseChannel)
        if (newTabIndex == -1) {
            val updatedChannels = mainViewModel.joinChannel(lowerCaseChannel)
            newTabIndex = updatedChannels.lastIndex
            mainViewModel.loadData(
                channelList = listOf(lowerCaseChannel),
                isUserChange = false,
                loadTwitchData = false,
                loadSupibot = false,
            )
            dankChatPreferences.channelsString = updatedChannels.joinToString(separator = ",")

            tabAdapter.addFragment(lowerCaseChannel)
            binding.chatViewpager.offscreenPageLimit = calculatePageLimit(updatedChannels.size)
        }
        binding.chatViewpager.setCurrentItem(newTabIndex, false)

        mainViewModel.setActiveChannel(channel)
        activity?.invalidateMenu()
    }

    private fun insertEmote(emote: GenericEmote) {
        insertText("${emote.code} ")
        mainViewModel.addEmoteUsage(emote)
    }

    private fun sendMessage(): Boolean {
        val msg = binding.input.text?.toString().orEmpty()
        mainViewModel.trySendMessage(msg)
        binding.input.setText("")

        return true
    }

    private fun getLastMessage(): Boolean {
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
                clipboard?.setPrimaryClip(ClipData.newPlainText("nuuls image url", result.url))
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
            is DataLoadingState.Failed                                                    -> showSnackBar(
                message = getString(R.string.snackbar_data_load_failed_cause, result.errorMessage),
                multiLine = true,
                duration = Snackbar.LENGTH_LONG,
                action = getString(R.string.snackbar_retry) to {
                    when {
                        result.parameters.isReloadEmotes -> reloadEmotes(result.parameters.channels.first())
                        else                             -> mainViewModel.loadData(result.parameters)
                    }
                })
        }
    }

    private fun handleErrorEvent(event: MainViewModel.Event.Error) {
        if (preferences.getBoolean(getString(R.string.preference_debug_mode_key), false)) {
            binding.root.showErrorDialog(event.throwable)
        }
    }

    private fun handleLoginRequest(success: Boolean) {
        val name = dankChatPreferences.userName
        if (success && name != null) {
            mainViewModel.closeAndReconnect(loadTwitchData = true)
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

    private fun setSuggestions(suggestions: List<Suggestion>) {
        if (binding.input.isPopupShowing) {
            return
        }

        suggestionAdapter.setSuggestions(suggestions)
    }

    private inline fun showNuulsUploadDialogIfNotAcknowledged(crossinline action: () -> Unit) {
        if (!dankChatPreferences.hasNuulsAcknowledged) {
            val spannable = SpannableStringBuilder(getString(R.string.nuuls_upload_disclaimer))
            Linkify.addLinks(spannable, Linkify.WEB_URLS)

            MaterialAlertDialogBuilder(requireContext())
                .setCancelable(false)
                .setTitle(R.string.nuuls_upload_title)
                .setMessage(spannable)
                .setPositiveButton(R.string.dialog_ok) { dialog, _ ->
                    dialog.dismiss()
                    dankChatPreferences.hasNuulsAcknowledged = true
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
        showNuulsUploadDialogIfNotAcknowledged {
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

        with(binding) {
            input.clearFocus()
            tabs.isVisible = !isFullscreen
            root.requestApplyInsets()
        }
    }

    private fun clear() {
        val position = binding.tabs.selectedTabPosition
        if (position in tabAdapter.titleList.indices)
            mainViewModel.clear(tabAdapter.titleList[position])
    }

    private fun reloadEmotes(channel: String? = null) {
        val position = channel?.let(tabAdapter.titleList::indexOf) ?: binding.tabs.selectedTabPosition
        if (position in tabAdapter.titleList.indices) {
            mainViewModel.reloadEmotes(tabAdapter.titleList[position])
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
            // TODO refactor to single viewmodel method
            dankChatPreferences.clearLogin()
            mainViewModel.closeAndReconnect()
            mainViewModel.clearIgnores()
            mainViewModel.clearEmoteUsages()
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
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.confirm_user_block_title)
            .setMessage(R.string.confirm_user_block_message)
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
        val updatedChannels = channels - activeChannel
        updateChannels(updatedChannels)
    }

    private fun openManageChannelsDialog() {
        val direction = MainFragmentDirections.actionMainFragmentToChannelsDialogFragment(channels = mainViewModel.getChannels().toTypedArray())
        navigateSafe(direction)
    }

    private fun showRoomStateDialog() {
        val currentRoomState = mainViewModel.currentRoomState
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

    private fun updateChannels(updatedChannels: List<String>) {
        val oldChannels = mainViewModel.getChannels()
        val oldIndex = binding.chatViewpager.currentItem
        val oldActiveChannel = oldChannels[oldIndex]

        val index = updatedChannels.indexOf(oldActiveChannel).coerceAtLeast(0)
        val activeChannel = updatedChannels.getOrNull(index) ?: ""

        mainViewModel.updateChannels(updatedChannels)
        mainViewModel.setActiveChannel(activeChannel)
        tabAdapter.updateFragments(updatedChannels)

        if (updatedChannels.isNotEmpty()) {
            dankChatPreferences.channelsString = updatedChannels.joinToString(",")
            binding.chatViewpager.setCurrentItem(index, false)
        } else {
            dankChatPreferences.channelsString = null
        }

        binding.chatViewpager.offscreenPageLimit = calculatePageLimit(updatedChannels.size)
        activity?.invalidateMenu()
    }

    private fun BottomSheetBehavior<View>.setupMentionSheet() {
        hide()
        addBottomSheetCallback(object : BottomSheetBehavior.BottomSheetCallback() {
            override fun onSlide(bottomSheet: View, slideOffset: Float) = Unit
            override fun onStateChanged(bottomSheet: View, newState: Int) {
                mainViewModel.setMentionSheetOpen(mentionBottomSheetBehavior?.isMoving == true || mentionBottomSheetBehavior?.isVisible == true)
                when {
                    mentionBottomSheetBehavior?.isExpanded == true -> mainViewModel.setSuggestionChannel("w")
                    mentionBottomSheetBehavior?.isHidden == true   -> mainViewModel.setSuggestionChannel(tabAdapter.titleList[binding.chatViewpager.currentItem])
                }
            }
        })
    }

    private fun calculatePageLimit(size: Int): Int = when {
        size > 1 -> size - 1
        else     -> ViewPager2.OFFSCREEN_PAGE_LIMIT_DEFAULT
    }

    private fun ViewPager2.setup(binding: MainFragmentBinding) {
        adapter = tabAdapter
        reduceDragSensitivity()
        registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                if (position in tabAdapter.titleList.indices) {
                    val newChannel = tabAdapter.titleList[position].lowercase(Locale.getDefault())
                    mainViewModel.setActiveChannel(newChannel)
                    emoteMenuBottomSheetBehavior?.hide()
                    binding.input.dismissDropDown()
                }
            }
        })
    }

    private fun TextInputLayout.setup() {
        setEndIconOnClickListener { sendMessage() }
        setEndIconOnLongClickListener { getLastMessage() }
        setStartIconOnClickListener {
            if (emoteMenuBottomSheetBehavior?.isVisible == true || emoteMenuAdapter.currentList.isEmpty()) {
                emoteMenuBottomSheetBehavior?.hide()
                return@setStartIconOnClickListener
            }

            if (isLandscape) {
                hideKeyboard()
                binding.input.clearFocus()
            }

            val heightScaleFactor = 0.5
            binding.apply {
                bottomSheetViewPager.adapter = emoteMenuAdapter
                bottomSheetViewPager.updateLayoutParams {
                    height = (resources.displayMetrics.heightPixels * heightScaleFactor).toInt()
                }
                TabLayoutMediator(bottomSheetTabs, bottomSheetViewPager) { tab, pos ->
                    val menuTab = EmoteMenuTab.values()[pos]
                    tab.text = when (menuTab) {
                        EmoteMenuTab.SUBS    -> getString(R.string.emote_menu_tab_subs)
                        EmoteMenuTab.CHANNEL -> getString(R.string.emote_menu_tab_channel)
                        EmoteMenuTab.GLOBAL  -> getString(R.string.emote_menu_tab_global)
                        EmoteMenuTab.RECENT  -> getString(R.string.emote_menu_tab_recent)
                    }
                }.attach()
            }

            postDelayed(50) {
                emoteMenuBottomSheetBehavior?.apply {
                    peekHeight = (resources.displayMetrics.heightPixels * heightScaleFactor).toInt()
                    expand()

                    addBottomSheetCallback(object : BottomSheetBehavior.BottomSheetCallback() {
                        override fun onSlide(bottomSheet: View, slideOffset: Float) = Unit

                        override fun onStateChanged(bottomSheet: View, newState: Int) {
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
                    })
                }
            }
        }
    }

    private fun CustomMultiAutoCompleteTextView.setup(binding: MainFragmentBinding) {
        setTokenizer(SpaceTokenizer())
        suggestionAdapter = SuggestionsArrayAdapter(binding.input.context) { count ->
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
                (activity as? MainActivity)?.setFullScreen(enabled = !hasFocus && isFullscreen, changeActionBarVisibility = false)
                return@setOnFocusChangeListener
            }

            binding.tabs.isVisible = !hasFocus && !isFullscreen
            binding.streamWebview.isVisible = !hasFocus && mainViewModel.isStreamActive

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

        const val LOGOUT_REQUEST_KEY = "logout_key"
        const val LOGIN_REQUEST_KEY = "login_key"
        const val THEME_CHANGED_KEY = "theme_changed_key"
        const val CHANNELS_REQUEST_KEY = "channels_key"
        const val ADD_CHANNEL_REQUEST_KEY = "add_channel_key"
        const val HISTORY_DISCLAIMER_KEY = "history_disclaimer_key"
        const val USER_POPUP_RESULT_KEY = "user_popup_key"
    }
}
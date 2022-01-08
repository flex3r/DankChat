package com.flxrs.dankchat.main

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
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import androidx.navigation.fragment.findNavController
import androidx.preference.PreferenceManager
import androidx.viewpager2.widget.ViewPager2
import com.flxrs.dankchat.BuildConfig
import com.flxrs.dankchat.R
import com.flxrs.dankchat.chat.ChatTabAdapter
import com.flxrs.dankchat.chat.menu.EmoteMenuAdapter
import com.flxrs.dankchat.chat.menu.EmoteMenuTab
import com.flxrs.dankchat.chat.suggestion.EmoteSuggestionsArrayAdapter
import com.flxrs.dankchat.chat.suggestion.SpaceTokenizer
import com.flxrs.dankchat.chat.suggestion.Suggestion
import com.flxrs.dankchat.chat.user.UserPopupResult
import com.flxrs.dankchat.databinding.EditDialogBinding
import com.flxrs.dankchat.databinding.MainFragmentBinding
import com.flxrs.dankchat.preferences.DankChatPreferenceStore
import com.flxrs.dankchat.preferences.screens.ChatSettingsFragment
import com.flxrs.dankchat.service.state.DataLoadingState
import com.flxrs.dankchat.service.state.ImageUploadState
import com.flxrs.dankchat.service.twitch.connection.ConnectionState
import com.flxrs.dankchat.service.twitch.emote.ThirdPartyEmoteType
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
    private lateinit var suggestionAdapter: EmoteSuggestionsArrayAdapter
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

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        tabAdapter = ChatTabAdapter(this)
        emoteMenuAdapter = EmoteMenuAdapter(::insertEmote)

        bindingRef = MainFragmentBinding.inflate(inflater, container, false).apply {
            emoteMenuBottomSheetBehavior = BottomSheetBehavior.from(emoteMenuBottomSheet)
            vm = mainViewModel
            lifecycleOwner = this@MainFragment
            chatViewpager.setup(this)
            input.setup(this)
            inputLayout.setup()

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
        }

        mainViewModel.apply {
            collectFlow(imageUploadEventFlow, ::handleImageUploadState)
            collectFlow(dataLoadingEventFlow, ::handleDataLoadingState)
            collectFlow(shouldShowUploadProgress) { activity?.invalidateOptionsMenu() }
            collectFlow(suggestions, ::setSuggestions)
            collectFlow(emoteItems, emoteMenuAdapter::submitList)
            collectFlow(isFullscreen) { changeActionBarVisibility(it) }
            collectFlow(canType) { if (it) binding.inputLayout.setup() }
            collectFlow(connectionState) { state ->
                binding.inputLayout.hint = when (state) {
                    ConnectionState.CONNECTED               -> getString(R.string.hint_connected)
                    ConnectionState.CONNECTED_NOT_LOGGED_IN -> getString(R.string.hint_not_logged_int)
                    ConnectionState.DISCONNECTED            -> getString(R.string.hint_disconnected)
                }
            }
            collectFlow(currentBottomText) {
                binding.inputLayout.helperText = it
                binding.fullscreenHintText.text = it
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
                            tab?.setTextColor(R.attr.colorOnSecondaryContainer, layerWithOnSurface = true)
                        }
                    }
                }
            }
            collectFlow(shouldColorNotification) { activity?.invalidateOptionsMenu() }
            collectFlow(channels) {
                if (!it.isNullOrEmpty()) {
                    fetchStreamInformation()
                }
            }
        }

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        initPreferences(view.context)

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
        binding.chatViewpager.offscreenPageLimit = ViewPager2.OFFSCREEN_PAGE_LIMIT_DEFAULT

        (requireActivity() as AppCompatActivity).apply {
            setHasOptionsMenu(true)
            setSupportActionBar(binding.toolbar)
            onBackPressedDispatcher.addCallback(viewLifecycleOwner) {
                when {
                    emoteMenuBottomSheetBehavior?.isVisible == true -> emoteMenuBottomSheetBehavior?.hide()
                    mentionBottomSheetBehavior?.isVisible == true   -> mentionBottomSheetBehavior?.hide()
                    else                                            -> finish()
                }
            }

            ViewCompat.setOnApplyWindowInsetsListener(binding.toggleFullscreen) { v, insets ->
                if (!isPortrait) {
                    return@setOnApplyWindowInsetsListener WindowInsetsCompat.CONSUMED
                }

                val extraMargin = when {
                    binding.streamWebview.isVisible -> 0
                    else                            -> insets.getInsets(WindowInsetsCompat.Type.displayCutout()).top
                }
                v.updateLayoutParams<ConstraintLayout.LayoutParams> {
                    topMargin = 8.dp + extraMargin
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
                    val oAuth = dankChatPreferences.oAuthKey ?: ""
                    val name = dankChatPreferences.userName ?: ""
                    val id = dankChatPreferences.userIdString ?: ""
                    val shouldLoadHistory = preferences.getBoolean(getString(R.string.preference_load_message_history_key), true)
                    val shouldLoadSupibot = preferences.getBoolean(getString(R.string.preference_supibot_suggestions_key), false)
                    val scrollBackLength = ChatSettingsFragment.correctScrollbackLength(preferences.getInt(getString(R.string.preference_scrollback_length_key), 10))
                    val loadThirdPartyKeys = preferences.getStringSet(getString(R.string.preference_visible_emotes_key), resources.getStringArray(R.array.emotes_entry_values).toSet()).orEmpty()
                    val loadThirdPartyData = ThirdPartyEmoteType.mapFromPreferenceSet(loadThirdPartyKeys)

                    mainViewModel.loadData(
                        oAuth = oAuth,
                        id = id,
                        name = name,
                        channelList = channels,
                        isUserChange = false,
                        loadTwitchData = true,
                        loadThirdPartyData = loadThirdPartyData,
                        loadHistory = shouldLoadHistory,
                        loadSupibot = shouldLoadSupibot,
                        scrollBackLength = scrollBackLength
                    )

                    if (name.isNotBlank() && oAuth.isNotBlank()) {
                        showSnackBar(getString(R.string.snackbar_login, name))
                    }
                }
            }
        }
    }

    override fun onPause() {
        binding.input.clearFocus()
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        emoteMenuBottomSheetBehavior?.hide()
        changeActionBarVisibility(mainViewModel.isFullscreen.value)

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

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.menu, menu)
    }

    override fun onPrepareOptionsMenu(menu: Menu) {
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
            findItem(R.id.menu_open)?.isVisible = hasChannels
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

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.menu_reconnect     -> mainViewModel.reconnect()
            R.id.menu_login         -> openLogin()
            R.id.menu_relogin       -> openLogin(isRelogin = true)
            R.id.menu_logout        -> showLogoutConfirmationDialog()
            R.id.menu_add           -> navigateSafe(R.id.action_mainFragment_to_addChannelDialogFragment)
            R.id.menu_mentions      -> mentionBottomSheetBehavior?.expand()
            R.id.menu_open          -> openChannel()
            R.id.menu_manage        -> openManageChannelsDialog()
            R.id.menu_reload_emotes -> reloadEmotes()
            R.id.menu_choose_media  -> showNuulsUploadDialogIfNotAcknowledged { requestGalleryMedia.launch() }
            R.id.menu_capture_image -> startCameraCapture()
            R.id.menu_capture_video -> startCameraCapture(captureVideo = true)
            R.id.menu_clear         -> clear()
            R.id.menu_settings      -> navigateSafe(R.id.action_mainFragment_to_overviewSettingsFragment).also { hideKeyboard() }
            else                    -> return false
        }
        return true
    }

    fun openUserPopup(targetUserId: String, messageId: String, channel: String?, isWhisperPopup: Boolean = false) {
        val currentUserId = dankChatPreferences.userIdString ?: return
        if (!dankChatPreferences.isLoggedIn) {
            return
        }

        val directions = MainFragmentDirections.actionMainFragmentToUserPopupDialogFragment(targetUserId, currentUserId, messageId, channel, isWhisperPopup)
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
        val shouldLoadSupibot = preferences.getBoolean(getString(R.string.preference_supibot_suggestions_key), false)
        val scrollBackLength = ChatSettingsFragment.correctScrollbackLength(preferences.getInt(getString(R.string.preference_scrollback_length_key), 10))

        val oAuth = dankChatPreferences.oAuthKey ?: ""
        val name = dankChatPreferences.userName ?: ""
        val id = dankChatPreferences.userIdString ?: ""
        val channels = dankChatPreferences.getChannels()
        val loadThirdPartyKeys = preferences.getStringSet(getString(R.string.preference_visible_emotes_key), resources.getStringArray(R.array.emotes_entry_values).toSet()).orEmpty()
        val loadThirdPartyData = ThirdPartyEmoteType.mapFromPreferenceSet(loadThirdPartyKeys)

        mainViewModel.loadData(
            oAuth = oAuth,
            id = id,
            name = name,
            channelList = channels,
            isUserChange = false,
            loadTwitchData = true,
            loadThirdPartyData = loadThirdPartyData,
            loadHistory = result,
            loadSupibot = shouldLoadSupibot,
            scrollBackLength = scrollBackLength
        )

        if (name.isNotBlank() && oAuth.isNotBlank()) {
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
            val oauth = dankChatPreferences.oAuthKey ?: ""
            val id = dankChatPreferences.userIdString ?: ""
            val name = dankChatPreferences.userName ?: ""
            val shouldLoadHistory = preferences.getBoolean(getString(R.string.preference_load_message_history_key), true)
            val scrollBackLength = ChatSettingsFragment.correctScrollbackLength(preferences.getInt(getString(R.string.preference_scrollback_length_key), 10))
            val loadThirdPartyKeys = preferences.getStringSet(getString(R.string.preference_visible_emotes_key), resources.getStringArray(R.array.emotes_entry_values).toSet()).orEmpty()
            val loadThirdPartyData = ThirdPartyEmoteType.mapFromPreferenceSet(loadThirdPartyKeys)

            val updatedChannels = mainViewModel.joinChannel(lowerCaseChannel)
            newTabIndex = updatedChannels.size - 1
            mainViewModel.loadData(
                oauth,
                id,
                name = name,
                channelList = listOf(lowerCaseChannel),
                isUserChange = false,
                loadTwitchData = false,
                loadThirdPartyData = loadThirdPartyData,
                loadHistory = shouldLoadHistory,
                loadSupibot = false,
                scrollBackLength
            )
            dankChatPreferences.channelsString = updatedChannels.joinToString(separator = ",")

            tabAdapter.addFragment(lowerCaseChannel)
        }
        binding.chatViewpager.setCurrentItem(newTabIndex, false)

        mainViewModel.setActiveChannel(channel)
        activity?.invalidateOptionsMenu()
    }

    private fun insertEmote(emote: String) = insertText("$emote ")

    private fun fetchStreamInformation() {
        lifecycleScope.launchWhenStarted {
            val key = getString(R.string.preference_streaminfo_key)
            if (preferences.getBoolean(key, true)) {
                val oAuth = dankChatPreferences.oAuthKey ?: return@launchWhenStarted
                mainViewModel.fetchStreamData(oAuth) {
                    resources.getQuantityString(R.plurals.viewers, it, it)
                }
            }
        }
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
        val oAuth = dankChatPreferences.oAuthKey
        val name = dankChatPreferences.userName
        val id = dankChatPreferences.userIdString
        val loadThirdPartyKeys = preferences.getStringSet(getString(R.string.preference_visible_emotes_key), resources.getStringArray(R.array.emotes_entry_values).toSet()).orEmpty()
        val loadThirdPartyData = ThirdPartyEmoteType.mapFromPreferenceSet(loadThirdPartyKeys)

        if (success && !oAuth.isNullOrBlank() && !name.isNullOrBlank() && !id.isNullOrBlank()) {
            mainViewModel.closeAndReconnect(name, oAuth, id, loadTwitchData = true, loadThirdPartyData = loadThirdPartyData)
            dankChatPreferences.isLoggedIn = true
            showSnackBar(getString(R.string.snackbar_login, name))
        } else {
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
            mediaFile?.delete()
            showSnackBar(getString(R.string.snackbar_upload_failed))
        }
    }

    private fun setSuggestions(suggestions: List<Suggestion>) {
        with(suggestionAdapter) {
            setNotifyOnChange(false)
            clear()
            addAll(suggestions)
        }
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
        if (position in 0 until tabAdapter.titleList.size)
            mainViewModel.clear(tabAdapter.titleList[position])
    }

    private fun reloadEmotes(channel: String? = null) {
        val position = channel?.let(tabAdapter.titleList::indexOf) ?: binding.tabs.selectedTabPosition
        if (position in 0 until tabAdapter.titleList.size) {
            val oAuth = dankChatPreferences.oAuthKey.orEmpty()
            val userId = dankChatPreferences.userIdString.orEmpty()
            val loadThirdPartyKeys = preferences.getStringSet(getString(R.string.preference_visible_emotes_key), resources.getStringArray(R.array.emotes_entry_values).toSet()).orEmpty()
            val loadThirdPartyData = ThirdPartyEmoteType.mapFromPreferenceSet(loadThirdPartyKeys)
            mainViewModel.reloadEmotes(tabAdapter.titleList[position], oAuth, userId, loadThirdPartyData)
        }
    }

    // TODO extract and make preferences injectable
    private fun initPreferences(context: Context) {
        val roomStateKey = getString(R.string.preference_roomstate_key)
        val streamInfoKey = getString(R.string.preference_streaminfo_key)
        val inputKey = getString(R.string.preference_show_input_key)
        val customMentionsKey = getString(R.string.preference_custom_mentions_key)
        val blacklistKey = getString(R.string.preference_blacklist_key)
        val keepScreenOnKey = getString(R.string.preference_keep_screen_on_key)
        val suggestionsKey = getString(R.string.preference_suggestions_key)
        val timestampFormatKey = getString(R.string.preference_timestamp_format_key)
        val loadSupibotKey = getString(R.string.preference_supibot_suggestions_key)
        val scrollBackLengthKey = getString(R.string.preference_scrollback_length_key)
        val preferEmotesSuggestionsKey = getString(R.string.preference_prefer_emote_suggestions_key)
        val showStreamToggleKey = getString(R.string.preference_show_stream_key)
        if (dankChatPreferences.isLoggedIn && dankChatPreferences.oAuthKey.isNullOrBlank()) {
            dankChatPreferences.clearLogin()
        }

        preferences = PreferenceManager.getDefaultSharedPreferences(context)
        if (::preferenceListener.isInitialized) preferences.unregisterOnSharedPreferenceChangeListener(preferenceListener)
        preferenceListener = SharedPreferences.OnSharedPreferenceChangeListener { p, key ->
            when (key) {
                roomStateKey               -> mainViewModel.setRoomStateEnabled(p.getBoolean(key, true))
                streamInfoKey              -> {
                    fetchStreamInformation()
                    mainViewModel.setStreamInfoEnabled(p.getBoolean(key, true))
                }
                inputKey                   -> mainViewModel.setInputEnabled(p.getBoolean(key, true))
                customMentionsKey          -> mainViewModel.setMentionEntries(p.getStringSet(key, emptySet()))
                blacklistKey               -> mainViewModel.setBlacklistEntries(p.getStringSet(key, emptySet()))
                loadSupibotKey             -> mainViewModel.setSupibotSuggestions(p.getBoolean(key, false))
                scrollBackLengthKey        -> mainViewModel.setScrollBackLength(ChatSettingsFragment.correctScrollbackLength(p.getInt(scrollBackLengthKey, 10)))
                keepScreenOnKey            -> keepScreenOn(p.getBoolean(key, true))
                suggestionsKey             -> binding.input.setSuggestionAdapter(p.getBoolean(key, true), suggestionAdapter)
                preferEmotesSuggestionsKey -> mainViewModel.setPreferEmotesSuggestions(p.getBoolean(key, false))
                showStreamToggleKey        -> mainViewModel.setCanShowStream(p.getBoolean(key, true))
            }
        }
        preferences.apply {
            registerOnSharedPreferenceChangeListener(preferenceListener)
            keepScreenOn(getBoolean(keepScreenOnKey, true))
            DateTimeUtils.setPattern(getString(timestampFormatKey, "HH:mm") ?: "HH:mm")
            mainViewModel.apply {
                setRoomStateEnabled(getBoolean(roomStateKey, true))
                setStreamInfoEnabled(getBoolean(streamInfoKey, true))
                setInputEnabled(getBoolean(inputKey, true))
                setCanShowStream(getBoolean(showStreamToggleKey, true))
                setPreferEmotesSuggestions(getBoolean(preferEmotesSuggestionsKey, false))
                binding.input.setSuggestionAdapter(getBoolean(suggestionsKey, true), suggestionAdapter)

                setMentionEntries(getStringSet(customMentionsKey, emptySet()))
                setBlacklistEntries(getStringSet(blacklistKey, emptySet()))
            }
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
            val loadThirdPartyKeys = preferences.getStringSet(getString(R.string.preference_visible_emotes_key), resources.getStringArray(R.array.emotes_entry_values).toSet()).orEmpty()
            val loadThirdPartyData = ThirdPartyEmoteType.mapFromPreferenceSet(loadThirdPartyKeys)
            dankChatPreferences.clearLogin()
            mainViewModel.closeAndReconnect(name = "", oAuth = "", userId = "", loadThirdPartyData = loadThirdPartyData)
            mainViewModel.clearIgnores()
            dialog.dismiss()
        }
        .setNegativeButton(getString(R.string.dialog_cancel)) { dialog, _ -> dialog.dismiss() }
        .create().show()

    private fun openChannel() {
        val channel = mainViewModel.activeChannel.value
        val url = "https://twitch.tv/$channel"
        Intent(Intent.ACTION_VIEW).also {
            it.data = url.toUri()
            startActivity(it)
        }
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

        activity?.invalidateOptionsMenu()
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

    private fun ViewPager2.setup(binding: MainFragmentBinding) {
        adapter = tabAdapter
        registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                if (position in 0 until tabAdapter.titleList.size) {
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
                    tab.text = when (EmoteMenuTab.values()[pos]) {
                        EmoteMenuTab.SUBS    -> getString(R.string.emote_menu_tab_subs)
                        EmoteMenuTab.CHANNEL -> getString(R.string.emote_menu_tab_channel)
                        EmoteMenuTab.GLOBAL  -> getString(R.string.emote_menu_tab_global)
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
                            if (mainViewModel.isFullscreen.value && isLandscape) {
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
        //setDropDownBackgroundResource(R.color.colorPrimary)
        setTokenizer(SpaceTokenizer())
        suggestionAdapter = EmoteSuggestionsArrayAdapter(binding.input.context) { count ->
            dropDownHeight = if (count > 2) {
                (binding.chatViewpager.measuredHeight / 1.3).roundToInt()
            } else {
                ViewGroup.LayoutParams.WRAP_CONTENT
            }
            dropDownWidth = (binding.chatViewpager.measuredWidth * 0.6).roundToInt()
        }
        suggestionAdapter.setNotifyOnChange(false)

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
            val isFullscreen = mainViewModel.isFullscreen.value
            if (!isLandscape) {
                (activity as? MainActivity)?.setFullScreen(enabled = !hasFocus && isFullscreen, changeActionBarVisibility = false)
                return@setOnFocusChangeListener
            }

            binding.tabs.isVisible = !hasFocus && !isFullscreen
            binding.streamWebview.isVisible = !hasFocus

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
        private val TAG = MainFragment::class.java.simpleName
        private const val DISCLAIMER_TAG = "message_history_disclaimer_dialog"

        const val LOGOUT_REQUEST_KEY = "logout_key"
        const val LOGIN_REQUEST_KEY = "login_key"
        const val THEME_CHANGED_KEY = "theme_changed_key"
        const val CHANNELS_REQUEST_KEY = "channels_key"
        const val ADD_CHANNEL_REQUEST_KEY = "add_channel_key"
        const val HISTORY_DISCLAIMER_KEY = "history_disclaimer_key"
        const val USER_POPUP_RESULT_KEY = "user_popup_key"
    }
}
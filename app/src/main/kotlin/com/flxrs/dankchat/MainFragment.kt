package com.flxrs.dankchat

import android.Manifest
import android.app.Activity
import android.content.*
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
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
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.ContextCompat.getSystemService
import androidx.core.content.FileProvider
import androidx.core.content.edit
import androidx.core.net.toFile
import androidx.core.net.toUri
import androidx.core.view.*
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import androidx.navigation.fragment.findNavController
import androidx.preference.PreferenceManager
import androidx.viewpager2.widget.ViewPager2
import com.flxrs.dankchat.chat.ChatTabAdapter
import com.flxrs.dankchat.chat.menu.EmoteMenuAdapter
import com.flxrs.dankchat.chat.menu.EmoteMenuTab
import com.flxrs.dankchat.chat.suggestion.EmoteSuggestionsArrayAdapter
import com.flxrs.dankchat.chat.suggestion.SpaceTokenizer
import com.flxrs.dankchat.chat.suggestion.Suggestion
import com.flxrs.dankchat.databinding.MainFragmentBinding
import com.flxrs.dankchat.preferences.ChatSettingsFragment
import com.flxrs.dankchat.preferences.DankChatPreferenceStore
import com.flxrs.dankchat.service.state.DataLoadingState
import com.flxrs.dankchat.service.state.ImageUploadState
import com.flxrs.dankchat.service.twitch.connection.SystemMessageType
import com.flxrs.dankchat.utils.*
import com.flxrs.dankchat.utils.dialog.AddChannelDialogFragment
import com.flxrs.dankchat.utils.dialog.MessageHistoryDisclaimerDialogFragment
import com.flxrs.dankchat.utils.extensions.*
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.BaseTransientBottomBar.BaseCallback
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.tabs.TabLayoutMediator
import com.google.android.material.textfield.TextInputLayout
import dagger.hilt.android.AndroidEntryPoint
import java.io.File
import java.io.IOException
import java.util.*
import kotlin.math.roundToInt

@AndroidEntryPoint
class MainFragment : Fragment() {

    private val viewModel: DankChatViewModel by activityViewModels()
    private val navController: NavController by lazy { findNavController() }
    private var bindingRef: MainFragmentBinding? = null
    private val binding get() = bindingRef!!
    private var emoteMenuBottomSheetBehavior: BottomSheetBehavior<MaterialCardView>? = null
    private var mentionBottomSheetBehavior: BottomSheetBehavior<View>? = null


    private lateinit var twitchPreferences: DankChatPreferenceStore
    private lateinit var preferenceListener: SharedPreferences.OnSharedPreferenceChangeListener
    private lateinit var preferences: SharedPreferences
    private lateinit var tabAdapter: ChatTabAdapter
    private lateinit var tabLayoutMediator: TabLayoutMediator
    private lateinit var emoteMenuAdapter: EmoteMenuAdapter
    private lateinit var suggestionAdapter: EmoteSuggestionsArrayAdapter
    private var currentMediaUri = Uri.EMPTY

    private val requestGalleryPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { if (it) requestGalleryMedia.launch() }
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
            showSnackbar(getString(R.string.snackbar_upload_failed))
            return@registerForActivityResult
        }

        val copy = createMediaFile(context, extension)
        try {
            contentResolver.openInputStream(uri)?.run { copy.outputStream().use { copyTo(it) } }
            if (copy.extension == "jpg" || copy.extension == "jpeg") {
                copy.removeExifAttributes()
            }

            viewModel.uploadMedia(copy)
        } catch (t: Throwable) {
            copy.delete()
            showSnackbar(getString(R.string.snackbar_upload_failed))
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        tabAdapter = ChatTabAdapter(this)
        emoteMenuAdapter = EmoteMenuAdapter(::insertEmote)
        bindingRef = MainFragmentBinding.inflate(inflater, container, false).apply {
            emoteMenuBottomSheetBehavior = BottomSheetBehavior.from(emoteMenuBottomSheet)
            vm = viewModel
            lifecycleOwner = this@MainFragment
            chatViewpager.setup(this)
            input.setup(this)
            inputLayout.setup()

            childFragmentManager.findFragmentById(R.id.mention_fragment)?.let {
                mentionBottomSheetBehavior = BottomSheetBehavior.from(it.requireView()).apply { setupMentionSheet() }
            }

            tabLayoutMediator = TabLayoutMediator(tabs, chatViewpager) { tab, position ->
                tab.text = tabAdapter.titleList[position]
            }.apply { attach() }
            tabs.getTabAt(tabs.selectedTabPosition)?.removeBadge()

            showActionbarFab.setOnClickListener { viewModel.appbarEnabled.value = true }
        }

        viewModel.apply {
            imageUploadedEvent.observe(viewLifecycleOwner, ::handleImageUploadEvent)
            dataLoadingEvent.observe(viewLifecycleOwner, ::handleDataLoadingEvent)
            showUploadProgress.observe(viewLifecycleOwner) { activity?.invalidateOptionsMenu() }
            suggestions.observe(viewLifecycleOwner, ::setSuggestions)
            emoteItems.observe(viewLifecycleOwner, emoteMenuAdapter::submitList)
            appbarEnabled.observe(viewLifecycleOwner) { changeActionBarVisibility(it) }
            canType.observe(viewLifecycleOwner) { if (it) binding.inputLayout.setup() }
            connectionState.observe(viewLifecycleOwner) { hint ->
                if (hint == SystemMessageType.NOT_LOGGED_IN && twitchPreferences.hasMessageHistoryAcknowledged) {
                    showApiChangeInformationIfNotAcknowledged()
                }

                binding.inputLayout.hint = when (hint) {
                    SystemMessageType.CONNECTED -> getString(R.string.hint_connected)
                    SystemMessageType.NOT_LOGGED_IN -> getString(R.string.hint_not_logged_int)
                    else -> getString(R.string.hint_disconnected)
                }
            }
            bottomText.observe(viewLifecycleOwner) {
                binding.inputLayout.helperText = it
                binding.fullscreenHintText.text = it
            }
            activeChannel.observe(viewLifecycleOwner) {
                (activity as? MainActivity)?.clearNotificationsOfChannel(it)
                val index = tabAdapter.titleList.indexOf(it)
                binding.tabs.getTabAt(index)?.removeBadge()
                viewModel.clearMentionCount(it)
            }

            errorEvent.observe(viewLifecycleOwner) {
                if (preferences.getBoolean(getString(R.string.preference_debug_mode_key), false)) {
                    binding.root.showErrorDialog(it)
                }
            }
            channelMentionCount.observe(viewLifecycleOwner) {
                it.forEach { (channel, count) ->
                    val index = tabAdapter.titleList.indexOf(channel)
                    if (count > 0) {
                        when (index) {
                            binding.tabs.selectedTabPosition -> viewModel.clearMentionCount(channel) // mention is in active channel
                            else -> binding.tabs.getTabAt(index)?.apply { orCreateBadge }
                        }
                    } else {
                        binding.tabs.getTabAt(index)?.removeBadge()
                    }
                }
            }
            shouldColorNotification.observe(viewLifecycleOwner) { activity?.invalidateOptionsMenu() }
        }

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        navController.currentBackStackEntry?.savedStateHandle?.apply {
            getLiveData<Boolean>(LOGIN_REQUEST_KEY).observe(viewLifecycleOwner) {
                handleLoginRequest(it)
                remove<Boolean>(LOGIN_REQUEST_KEY)
            }
            getLiveData<Boolean>(LOGOUT_REQUEST_KEY).observe(viewLifecycleOwner) {
                showLogoutConfirmationDialog()
                remove<Boolean>(LOGOUT_REQUEST_KEY)
            }
            getLiveData<Boolean>(THEME_CHANGED_KEY).observe(viewLifecycleOwner) {
                remove<Boolean>(THEME_CHANGED_KEY)
                binding.root.post { ActivityCompat.recreate(requireActivity()) }
            }
        }

        initPreferences(view.context)
        if (twitchPreferences.isLoggedIn && twitchPreferences.userIdString == null) {
            twitchPreferences.userIdString = "${twitchPreferences.userId}"
        }

        val channels = twitchPreferences.channelsString?.split(',') ?: twitchPreferences.channels?.also { twitchPreferences.channels = null }
        channels?.forEach { tabAdapter.addFragment(it) }
        val asList = channels?.toList() ?: emptyList()
        binding.chatViewpager.offscreenPageLimit = calculatePageLimit(asList.size)
        viewModel.channels.value = asList
        fetchStreamInformation()

        (requireActivity() as AppCompatActivity).apply {
            setHasOptionsMenu(true)
            setSupportActionBar(binding.toolbar)
            onBackPressedDispatcher.addCallback(viewLifecycleOwner) {
                when {
                    emoteMenuBottomSheetBehavior?.isVisible == true -> emoteMenuBottomSheetBehavior?.hide()
                    mentionBottomSheetBehavior?.isVisible == true -> mentionBottomSheetBehavior?.hide()
                    else -> finishAndRemoveTask()
                }
            }

            ViewCompat.setOnApplyWindowInsetsListener(binding.showActionbarFab) { v, insets ->
                if (resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT && v.isVisible) {
                    v.y = when {
                        binding.input.hasFocus() -> 0f
                        else -> insets
                            .getInsets(WindowInsetsCompat.Type.displayCutout())
                            .top.toFloat()
                    }
                }

                insets
            }

            if (savedInstanceState == null && !viewModel.started) {
                if (!twitchPreferences.hasMessageHistoryAcknowledged) {
                    MessageHistoryDisclaimerDialogFragment().show(parentFragmentManager, DISCLAIMER_TAG)
                } else {
                    val oAuth = twitchPreferences.oAuthKey ?: ""
                    val name = twitchPreferences.userName ?: ""
                    val id = twitchPreferences.userIdString ?: ""
                    val shouldLoadHistory = preferences.getBoolean(getString(R.string.preference_load_message_history_key), true)
                    val shouldLoadSupibot = preferences.getBoolean(getString(R.string.preference_supibot_suggestions_key), false)
                    val scrollBackLength = ChatSettingsFragment.correctScrollbackLength(preferences.getInt(getString(R.string.preference_scrollback_length_key), 10))

                    viewModel.loadData(
                        oAuth = oAuth,
                        id = id,
                        name = name,
                        loadTwitchData = true,
                        loadHistory = shouldLoadHistory,
                        loadSupibot = shouldLoadSupibot,
                        scrollBackLength = scrollBackLength
                    )

                    if (name.isNotBlank() && oAuth.isNotBlank()) {
                        showSnackbar(getString(R.string.snackbar_login, name))
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
        changeActionBarVisibility(viewModel.appbarEnabled.value ?: true)

        (activity as? MainActivity)?.apply {
            if (channelToOpen.isNotBlank()) {
                val index = viewModel.channels.value?.indexOf(channelToOpen) ?: -1
                if (index >= 0) {
                    when (index) {
                        binding.chatViewpager.currentItem -> clearNotificationsOfChannel(channelToOpen)
                        else -> binding.chatViewpager.setCurrentItem(index, false)
                    }
                }
                channelToOpen = ""
            } else {
                val activeChannel = viewModel.activeChannel.value ?: return
                clearNotificationsOfChannel(activeChannel)
            }
        }
    }

    override fun onDestroyView() {
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
            val isLoggedIn = twitchPreferences.isLoggedIn
            val shouldShowProgress = viewModel.showUploadProgress.value ?: false
            val hasChannels = !viewModel.channels.value.isNullOrEmpty()
            val mentionIconColor = when (viewModel.shouldColorNotification.value) {
                true -> R.color.color_error
                else -> android.R.color.white
            }
            findItem(R.id.menu_login)?.isVisible = !isLoggedIn
            findItem(R.id.menu_remove)?.isVisible = hasChannels
            findItem(R.id.menu_open)?.isVisible = hasChannels
            findItem(R.id.menu_mentions)?.apply {
                isVisible = hasChannels
                context?.let {
                    icon.setTintList(ContextCompat.getColorStateList(it, mentionIconColor))
                }
            }

            findItem(R.id.progress)?.apply {
                isVisible = shouldShowProgress
                actionView = ProgressBar(requireContext()).apply {
                    indeterminateTintList = ContextCompat.getColorStateList(requireContext(), android.R.color.white)
                    isVisible = shouldShowProgress
                }
            }
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.menu_reconnect -> viewModel.reconnect(false)
            R.id.menu_login -> navigateSafe(R.id.action_mainFragment_to_loginFragment).also { hideKeyboard() }
            R.id.menu_add -> openAddChannelDialog()
            R.id.menu_mentions -> mentionBottomSheetBehavior?.expand()
            R.id.menu_open -> openChannel()
            R.id.menu_remove -> removeChannel()
            R.id.menu_reload_emotes -> reloadEmotes()
            R.id.menu_choose_media -> checkPermissionForGallery()
            R.id.menu_capture_image -> startCameraCapture()
            R.id.menu_capture_video -> startCameraCapture(captureVideo = true)
            R.id.menu_hide -> viewModel.appbarEnabled.value = false
            R.id.menu_clear -> clear()
            R.id.menu_settings -> navigateSafe(R.id.action_mainFragment_to_overviewSettingsFragment).also { hideKeyboard() }
            else -> return false
        }
        return true
    }

    fun onMessageHistoryDisclaimerResult(result: Boolean) {
        twitchPreferences.hasMessageHistoryAcknowledged = true
        preferences.edit { putBoolean(getString(R.string.preference_load_message_history_key), result) }
        val shouldLoadSupibot = preferences.getBoolean(getString(R.string.preference_supibot_suggestions_key), false)
        val scrollBackLength = ChatSettingsFragment.correctScrollbackLength(preferences.getInt(getString(R.string.preference_scrollback_length_key), 10))

        if (viewModel.connectionState.value == SystemMessageType.NOT_LOGGED_IN) {
            showApiChangeInformationIfNotAcknowledged()
        }

        val oAuth = twitchPreferences.oAuthKey ?: ""
        val name = twitchPreferences.userName ?: ""
        val id = twitchPreferences.userIdString ?: ""
        viewModel.loadData(oAuth = oAuth, id = id, name = name, loadTwitchData = true, loadHistory = result, loadSupibot = shouldLoadSupibot, scrollBackLength = scrollBackLength)

        if (name.isNotBlank() && oAuth.isNotBlank()) {
            showSnackbar(getString(R.string.snackbar_login, name))
        }
    }

    fun addChannel(channel: String) {
        val lowerCaseChannel = channel.toLowerCase(Locale.getDefault())
        val channels = viewModel.channels.value ?: emptyList()
        if (!channels.contains(lowerCaseChannel)) {
            val oauth = twitchPreferences.oAuthKey ?: ""
            val id = twitchPreferences.userIdString ?: ""
            val name = twitchPreferences.userName ?: ""
            val shouldLoadHistory = preferences.getBoolean(getString(R.string.preference_load_message_history_key), true)
            val scrollBackLength = ChatSettingsFragment.correctScrollbackLength(preferences.getInt(getString(R.string.preference_scrollback_length_key), 10))

            val updatedChannels = viewModel.joinChannel(lowerCaseChannel)
            if (updatedChannels != null) {
                viewModel.loadData(oauth, id, name = name, channelList = listOf(channel), loadTwitchData = false, loadHistory = shouldLoadHistory, loadSupibot = false, scrollBackLength)
                twitchPreferences.channelsString = updatedChannels.joinToString(",")

                tabAdapter.addFragment(lowerCaseChannel)
                binding.chatViewpager.offscreenPageLimit = calculatePageLimit(updatedChannels.size)
                binding.chatViewpager.setCurrentItem(updatedChannels.size - 1, false)

                fetchStreamInformation()
                activity?.invalidateOptionsMenu()
            }
        }
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

    private fun insertEmote(emote: String) = insertText("$emote ")

    private fun fetchStreamInformation() {
        lifecycleScope.launchWhenStarted {
            val key = getString(R.string.preference_streaminfo_key)
            if (preferences.getBoolean(key, true)) {
                val oAuth = twitchPreferences.oAuthKey ?: return@launchWhenStarted
                viewModel.fetchStreamData(oAuth) {
                    resources.getQuantityString(R.plurals.viewers, it, it)
                }
            }
        }
    }

    private fun sendMessage(): Boolean {
        val msg = binding.input.text.toString()
        val activeChannel = viewModel.activeChannel.value ?: return true
        if (viewModel.mentionSheetOpen.value == true && viewModel.whisperTabSelected.value == true && !msg.startsWith("/w ")) return true

        viewModel.sendMessage(activeChannel, msg)
        binding.input.setText("")

        return true
    }

    private fun getLastMessage(): Boolean {
        viewModel.activeChannel.value?.let {
            val lastMessage = viewModel.lastMessage[it] ?: return false
            binding.input.setText(lastMessage)
            binding.input.setSelection(lastMessage.length)
        }
        return true
    }

    private fun handleImageUploadEvent(result: ImageUploadState) {
        when (result) {
            is ImageUploadState.Loading -> return
            is ImageUploadState.Failed -> showSnackbar(
                message = result.errorMessage?.let { getString(R.string.snackbar_upload_failed_cause, it) } ?: getString(R.string.snackbar_upload_failed),
                onDismiss = { result.mediaFile.delete() },
                action = getString(R.string.snackbar_retry) to { viewModel.uploadMedia(result.mediaFile) })
            is ImageUploadState.Finished -> {
                val clipboard = getSystemService(requireContext(), ClipboardManager::class.java)
                clipboard?.setPrimaryClip(ClipData.newPlainText("nuuls image url", result.url))
                showSnackbar(getString(R.string.snackbar_image_uploaded, result.url))
            }
        }
    }

    private fun handleDataLoadingEvent(result: DataLoadingState) {
        when (result) {
            is DataLoadingState.Loading, DataLoadingState.Finished -> return
            is DataLoadingState.Reloaded -> showSnackbar(getString(R.string.snackbar_data_reloaded))
            is DataLoadingState.Failed -> showSnackbar(
                message = getString(R.string.snackbar_data_load_failed_cause, result.t.message),
                action = getString(R.string.snackbar_retry) to {
                    when {
                        result.parameters.isReloadEmotes -> reloadEmotes(result.parameters.channels.first())
                        else -> viewModel.loadData(result.parameters)
                    }
                })
        }
    }

    private fun handleLoginRequest(success: Boolean) {
        val oAuth = twitchPreferences.oAuthKey
        val name = twitchPreferences.userName
        val id = twitchPreferences.userIdString

        if (success && !oAuth.isNullOrBlank() && !name.isNullOrBlank() && !id.isNullOrBlank()) {
            viewModel.close(name, oAuth, id, true)
            twitchPreferences.isLoggedIn = true
            showSnackbar(getString(R.string.snackbar_login, name))
        } else {
            showSnackbar(getString(R.string.snackbar_login_failed))
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

            viewModel.uploadMedia(mediaFile)
        } catch (e: IOException) {
            mediaFile?.delete()
            showSnackbar(getString(R.string.snackbar_upload_failed))
        }
    }

    private fun setSuggestions(suggestions: List<Suggestion>) {
        with(suggestionAdapter) {
            setNotifyOnChange(false)
            clear()
            addAll(suggestions)
        }
    }

    private fun showApiChangeInformationIfNotAcknowledged() {
        if (!twitchPreferences.hasApiChangeAcknowledged) {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.anon_connection_disclaimer_title)
                .setMessage(R.string.anon_connection_disclaimer_message)
                .setPositiveButton(R.string.dialog_ok) { dialog, _ -> dialog.dismiss() }
                .setOnDismissListener { twitchPreferences.hasApiChangeAcknowledged = true }
                .show()
        }
    }

    private inline fun showNuulsUploadDialogIfNotAcknowledged(crossinline action: () -> Unit) {
        if (!twitchPreferences.hasNuulsAcknowledged) {
            val spannable = SpannableStringBuilder(getString(R.string.nuuls_upload_disclaimer))
            Linkify.addLinks(spannable, Linkify.WEB_URLS)

            MaterialAlertDialogBuilder(requireContext())
                .setCancelable(false)
                .setTitle(R.string.nuuls_upload_title)
                .setMessage(spannable)
                .setPositiveButton(R.string.dialog_ok) { dialog, _ ->
                    dialog.dismiss()
                    twitchPreferences.hasNuulsAcknowledged = true
                    action()
                }
                .show().also { it.findViewById<TextView>(android.R.id.message)?.movementMethod = LinkMovementMethod.getInstance() }
        } else action()
    }

    private fun checkPermissionForGallery() {
        showNuulsUploadDialogIfNotAcknowledged {
            when (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                PackageManager.PERMISSION_GRANTED -> requestGalleryPermission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                else -> requestGalleryMedia.launch()
            }
        }
    }

    private fun startCameraCapture(captureVideo: Boolean = false) {
        val packageManager = activity?.packageManager ?: return
        val packageName = activity?.packageName ?: return
        val (action, extension) = when {
            captureVideo -> MediaStore.ACTION_VIDEO_CAPTURE to "mp4"
            else -> MediaStore.ACTION_IMAGE_CAPTURE to "jpg"
        }
        showNuulsUploadDialogIfNotAcknowledged {
            Intent(action).also { captureIntent ->
                captureIntent.resolveActivity(packageManager)?.also {
                    try {
                        createMediaFile(requireContext(), extension).apply { currentMediaUri = toUri() }
                    } catch (ex: IOException) {
                        null
                    }?.also {
                        val uri = FileProvider.getUriForFile(requireContext(), "$packageName.fileprovider", it)
                        captureIntent.putExtra(MediaStore.EXTRA_OUTPUT, uri)
                        when {
                            captureVideo -> requestVideoCapture.launch(captureIntent)
                            else -> requestImageCapture.launch(captureIntent)
                        }
                    }
                }
            }
        }
    }

    private fun changeActionBarVisibility(enabled: Boolean) {
        hideKeyboard()
        binding.input.clearFocus()
        val isDarkMode = preferences.getBoolean(getString(R.string.preference_dark_theme_key), true)
        var lightModeFlags = 0
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1 && !isDarkMode) {
            lightModeFlags = (lightModeFlags or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR)
        }

        if (enabled) {
            binding.root.systemUiVisibility = View.VISIBLE or lightModeFlags
            (activity as? AppCompatActivity)?.supportActionBar?.show()
            binding.showActionbarFab.visibility = View.GONE
            binding.tabs.visibility = View.VISIBLE
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && activity?.isInMultiWindowMode == false) {
                binding.root.systemUiVisibility = (View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        or View.SYSTEM_UI_FLAG_FULLSCREEN
                        or lightModeFlags)
            }

            (activity as? AppCompatActivity)?.supportActionBar?.hide()
            binding.tabs.visibility = View.GONE
            binding.showActionbarFab.visibility = View.VISIBLE
        }
        binding.root.requestApplyInsets()
    }

    private fun clear() {
        val position = binding.tabs.selectedTabPosition
        if (position in 0 until tabAdapter.titleList.size)
            viewModel.clear(tabAdapter.titleList[position])
    }

    private fun reloadEmotes(channel: String? = null) {
        val position = channel?.let(tabAdapter.titleList::indexOf) ?: binding.tabs.selectedTabPosition
        if (position in 0 until tabAdapter.titleList.size) {
            val oAuth = twitchPreferences.oAuthKey ?: return
            val userId = twitchPreferences.userIdString ?: return
            viewModel.reloadEmotes(tabAdapter.titleList[position], oAuth, userId)
        }
    }

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
        twitchPreferences = DankChatPreferenceStore(context)
        if (twitchPreferences.isLoggedIn && twitchPreferences.oAuthKey.isNullOrBlank()) {
            twitchPreferences.clearLogin()
        }

        preferences = PreferenceManager.getDefaultSharedPreferences(context)
        if (::preferenceListener.isInitialized) preferences.unregisterOnSharedPreferenceChangeListener(preferenceListener)
        preferenceListener = SharedPreferences.OnSharedPreferenceChangeListener { p, key ->
            when (key) {
                roomStateKey -> viewModel.setRoomStateEnabled(p.getBoolean(key, true))
                streamInfoKey -> {
                    fetchStreamInformation()
                    viewModel.setStreamInfoEnabled(p.getBoolean(key, true))
                }
                inputKey -> viewModel.inputEnabled.value = p.getBoolean(key, true)
                customMentionsKey -> viewModel.setMentionEntries(p.getStringSet(key, emptySet()))
                blacklistKey -> viewModel.setBlacklistEntries(p.getStringSet(key, emptySet()))
                loadSupibotKey -> viewModel.setSupibotSuggestions(p.getBoolean(key, false))
                scrollBackLengthKey -> viewModel.setScrollbackLength(ChatSettingsFragment.correctScrollbackLength(p.getInt(scrollBackLengthKey, 10)))
                keepScreenOnKey -> keepScreenOn(p.getBoolean(key, true))
                suggestionsKey -> binding.input.setSuggestionAdapter(p.getBoolean(key, true), suggestionAdapter)
            }
        }
        preferences.apply {
            registerOnSharedPreferenceChangeListener(preferenceListener)
            keepScreenOn(getBoolean(keepScreenOnKey, true))
            TimeUtils.setPattern(getString(timestampFormatKey, "HH:mm") ?: "HH:mm")
            viewModel.apply {
                setRoomStateEnabled(getBoolean(roomStateKey, true))
                setStreamInfoEnabled(getBoolean(streamInfoKey, true))
                inputEnabled.value = getBoolean(inputKey, true)
                binding.input.setSuggestionAdapter(getBoolean(suggestionsKey, true), suggestionAdapter)

                setMentionEntries(getStringSet(customMentionsKey, emptySet()))
                setBlacklistEntries(getStringSet(blacklistKey, emptySet()))
            }
        }
    }

    private fun showSnackbar(message: String, onDismiss: () -> Unit = {}, action: Pair<String, () -> Unit>? = null) {
        bindingRef?.let { binding ->
            binding.inputLayout.post {
                Snackbar.make(binding.coordinator, message, Snackbar.LENGTH_SHORT).apply {
                    if (binding.inputLayout.isVisible) anchorView = binding.inputLayout
                    addCallback(object : Snackbar.Callback() {
                        override fun onDismissed(transientBottomBar: Snackbar?, event: Int) {
                            when (event) {
                                BaseCallback.DISMISS_EVENT_CONSECUTIVE, BaseCallback.DISMISS_EVENT_TIMEOUT, BaseCallback.DISMISS_EVENT_SWIPE -> onDismiss()
                                else -> return
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
            twitchPreferences.clearLogin()
            viewModel.close(name = "", oAuth = "", userId = "")
            viewModel.clearIgnores()
            dialog.dismiss()
        }
        .setNegativeButton(getString(R.string.confirm_logout_negative_button)) { dialog, _ -> dialog.dismiss() }
        .create().show()

    private fun openAddChannelDialog() = AddChannelDialogFragment.create(
        R.string.add_dialog_title,
        R.string.dialog_cancel,
        R.string.dialog_ok,
        textHint = getString(R.string.add_channel_hint)
    ).show(parentFragmentManager, DIALOG_TAG)

    private fun openChannel() {
        val channel = viewModel.activeChannel.value ?: return
        val url = "https://twitch.tv/$channel"
        Intent(Intent.ACTION_VIEW).also {
            it.data = url.toUri()
            startActivity(it)
        }
    }

    private fun removeChannel() {
        val channels = viewModel.partChannel()
        if (channels != null) {
            val index = binding.chatViewpager.currentItem
            if (channels.isNotEmpty()) {
                twitchPreferences.channelsString = channels.joinToString(",")
                val newPos = (index - 1).coerceAtLeast(0)
                binding.chatViewpager.setCurrentItem(newPos, false)
            } else {
                twitchPreferences.channelsString = null
            }

            binding.chatViewpager.offscreenPageLimit = calculatePageLimit(channels.size)
            tabAdapter.removeFragment(index)
            activity?.invalidateOptionsMenu()
        }
    }

    private fun BottomSheetBehavior<View>.setupMentionSheet() {
        hide()
        addBottomSheetCallback(object : BottomSheetBehavior.BottomSheetCallback() {
            override fun onSlide(bottomSheet: View, slideOffset: Float) = Unit
            override fun onStateChanged(bottomSheet: View, newState: Int) {
                viewModel.setMentionSheetOpen(mentionBottomSheetBehavior?.isMoving == true || mentionBottomSheetBehavior?.isVisible == true)
                when {
                    mentionBottomSheetBehavior?.isExpanded == true -> viewModel.setSuggestionChannel("w")
                    mentionBottomSheetBehavior?.isHidden == true -> viewModel.setSuggestionChannel(tabAdapter.titleList[binding.chatViewpager.currentItem])
                }
            }
        })
    }

    private fun calculatePageLimit(size: Int): Int = when {
        size > 1 -> size - 1
        else -> ViewPager2.OFFSCREEN_PAGE_LIMIT_DEFAULT
    }

    private fun ViewPager2.setup(binding: MainFragmentBinding) {
        adapter = tabAdapter
        registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                if (position in 0 until tabAdapter.titleList.size) {
                    val newChannel = tabAdapter.titleList[position].toLowerCase(Locale.getDefault())
                    viewModel.setActiveChannel(newChannel)
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

            if (isLandscape) hideKeyboard()
            val heightScaleFactor = 0.5
            binding.apply {
                bottomSheetViewPager.adapter = emoteMenuAdapter
                bottomSheetViewPager.updateLayoutParams {
                    height = (resources.displayMetrics.heightPixels * heightScaleFactor).toInt()
                }
                TabLayoutMediator(bottomSheetTabs, bottomSheetViewPager) { tab, pos ->
                    tab.text = when (EmoteMenuTab.values()[pos]) {
                        EmoteMenuTab.SUBS -> getString(R.string.emote_menu_tab_subs)
                        EmoteMenuTab.CHANNEL -> getString(R.string.emote_menu_tab_channel)
                        EmoteMenuTab.GLOBAL -> getString(R.string.emote_menu_tab_global)
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
                            if (viewModel.appbarEnabled.value == true && isLandscape) {
                                when (newState) {
                                    BottomSheetBehavior.STATE_EXPANDED, BottomSheetBehavior.STATE_COLLAPSED -> {
                                        (activity as? AppCompatActivity)?.supportActionBar?.hide()
                                        binding.tabs.visibility = View.GONE
                                    }
                                    else -> {
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
                else -> false
            }
        }
        setOnKeyListener { _, keyCode, _ ->
            when (keyCode) {
                KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                    if (!isItemSelected()) sendMessage() else false
                }
                else -> false
            }
        }

        var wasLandScapeNotFullscreen = false
        setOnFocusChangeListener { _, hasFocus ->
            val isDarkMode = preferences.getBoolean(getString(R.string.preference_dark_theme_key), true)
            var lightModeFlags = 0
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1 && !isDarkMode) {
                lightModeFlags = (lightModeFlags or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
                        or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR)
            }

            binding.root.systemUiVisibility = when {
                !hasFocus && wasLandScapeNotFullscreen && resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE -> {
                    wasLandScapeNotFullscreen = false
                    (activity as? AppCompatActivity)?.supportActionBar?.show()
                    binding.showActionbarFab.visibility = View.GONE
                    binding.tabs.visibility = View.VISIBLE
                    View.VISIBLE or lightModeFlags
                }
                !hasFocus && binding.showActionbarFab.isVisible -> {
                    wasLandScapeNotFullscreen = false
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && activity?.isInMultiWindowMode == true) {
                        View.VISIBLE or lightModeFlags
                    } else {
                        (View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                                or View.SYSTEM_UI_FLAG_FULLSCREEN
                                or lightModeFlags)
                    }
                }
                hasFocus && !binding.showActionbarFab.isVisible && resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE -> {
                    wasLandScapeNotFullscreen = true
                    (activity as? AppCompatActivity)?.supportActionBar?.hide()
                    binding.showActionbarFab.visibility = View.VISIBLE
                    binding.tabs.visibility = View.GONE
                    View.VISIBLE or lightModeFlags
                }
                else -> {
                    wasLandScapeNotFullscreen = false
                    View.VISIBLE or lightModeFlags
                }
            }
            binding.root.requestApplyInsets()
        }
    }

    companion object {
        private val TAG = MainFragment::class.java.simpleName
        private const val DIALOG_TAG = "add_channel_dialog"
        private const val DISCLAIMER_TAG = "message_history_disclaimer_dialog"

        const val LOGOUT_REQUEST_KEY = "logout_key"
        const val LOGIN_REQUEST_KEY = "login_key"
        const val THEME_CHANGED_KEY = "theme_changed_key"
    }
}
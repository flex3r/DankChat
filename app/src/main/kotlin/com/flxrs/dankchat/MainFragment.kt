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
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.ContextCompat.getSystemService
import androidx.core.content.FileProvider
import androidx.core.content.edit
import androidx.core.view.isVisible
import androidx.core.view.postDelayed
import androidx.core.view.updateLayoutParams
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.observe
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
import com.flxrs.dankchat.preferences.DankChatPreferenceStore
import com.flxrs.dankchat.service.state.DataLoadingState
import com.flxrs.dankchat.service.state.ImageUploadState
import com.flxrs.dankchat.service.twitch.connection.SystemMessageType
import com.flxrs.dankchat.utils.CustomMultiAutoCompleteTextView
import com.flxrs.dankchat.utils.MediaUtils
import com.flxrs.dankchat.utils.dialog.EditTextDialogFragment
import com.flxrs.dankchat.utils.dialog.MessageHistoryDisclaimerDialogFragment
import com.flxrs.dankchat.utils.extensions.hideKeyboard
import com.flxrs.dankchat.utils.extensions.keepScreenOn
import com.flxrs.dankchat.utils.extensions.navigateSafe
import com.flxrs.dankchat.utils.showErrorDialog
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.tabs.TabLayoutMediator
import com.google.android.material.textfield.TextInputLayout
import org.koin.androidx.viewmodel.ext.android.sharedViewModel
import java.io.File
import java.io.IOException
import java.util.*
import kotlin.math.max
import kotlin.math.roundToInt

class MainFragment : Fragment() {

    private val viewModel: DankChatViewModel by sharedViewModel()
    private val navController: NavController by lazy { findNavController() }
    private lateinit var twitchPreferences: DankChatPreferenceStore
    private lateinit var preferenceListener: SharedPreferences.OnSharedPreferenceChangeListener
    private lateinit var preferences: SharedPreferences
    private lateinit var binding: MainFragmentBinding
    private lateinit var tabAdapter: ChatTabAdapter
    private lateinit var tabLayoutMediator: TabLayoutMediator
    private lateinit var emoteMenuAdapter: EmoteMenuAdapter
    private lateinit var bottomSheetBehavior: BottomSheetBehavior<MaterialCardView>
    private lateinit var suggestionAdapter: EmoteSuggestionsArrayAdapter
    private var currentMediaPath = ""

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        tabAdapter = ChatTabAdapter(childFragmentManager, lifecycle)
        emoteMenuAdapter = EmoteMenuAdapter(::insertEmote)
        binding = MainFragmentBinding.inflate(inflater, container, false).apply {
            bottomSheetBehavior = BottomSheetBehavior.from(bottomSheet)
            vm = viewModel
            lifecycleOwner = this@MainFragment
            viewPager.setup(this)
            input.setup(this)
            inputLayout.setup()

            tabLayoutMediator = TabLayoutMediator(tabs, viewPager) { tab, position ->
                tab.text = tabAdapter.titleList[position]
            }.apply { attach() }

            showActionbarFab.setOnClickListener { viewModel.appbarEnabled.value = true }
        }

        viewModel.apply {
            imageUploadedEvent.observe(viewLifecycleOwner, ::handleImageUploadEvent)
            dataLoadingEvent.observe(viewLifecycleOwner, ::handleDataLoadingEvent)
            showUploadProgress.observe(viewLifecycleOwner) { activity?.invalidateOptionsMenu() }
            emoteAndUserSuggestions.observe(viewLifecycleOwner, ::setSuggestions)
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
            }

            errorEvent.observe(viewLifecycleOwner) {
                if (preferences.getBoolean(getString(R.string.preference_debug_mode_key), false)) {
                    binding.root.showErrorDialog(it)
                }
            }
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
                binding.root.post { requireActivity().recreate() }
            }
        }

        initPreferences(view.context)
        val channels = twitchPreferences.channelsString?.split(',') ?: twitchPreferences.channels?.also { twitchPreferences.channels = null }
        channels?.forEach { tabAdapter.addFragment(it) }
        val asList = channels?.toList() ?: emptyList()
        binding.viewPager.offscreenPageLimit = calculatePageLimit(asList.size)
        viewModel.channels.value = asList
        fetchStreamInformation()

        (requireActivity() as AppCompatActivity).apply {
            setHasOptionsMenu(true)
            setSupportActionBar(binding.toolbar)
            onBackPressedDispatcher.addCallback(viewLifecycleOwner) {
                if (bottomSheetBehavior.state == BottomSheetBehavior.STATE_EXPANDED || bottomSheetBehavior.state == BottomSheetBehavior.STATE_COLLAPSED) {
                    bottomSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN
                } else {
                    finishAndRemoveTask()
                }
            }

            window.decorView.setOnApplyWindowInsetsListener { _, insets ->
                binding.showActionbarFab.apply {
                    if (resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT && isVisible) {
                        y = if (binding.input.hasFocus()) {
                            max(insets.stableInsetTop.toFloat() - insets.systemWindowInsetTop, 0f)
                        } else {
                            insets.stableInsetTop.toFloat()
                        }
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
                    val id = twitchPreferences.userId
                    val shouldLoadHistory = preferences.getBoolean(getString(R.string.preference_load_message_history_key), true)
                    viewModel.loadData(oauth = oAuth, id = id, loadTwitchData = true, loadHistory = shouldLoadHistory, name = name)

                    if (name.isNotBlank() && oAuth.isNotBlank()) {
                        showSnackbar(getString(R.string.snackbar_login, name))
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        if (::preferences.isInitialized) {
            preferences.unregisterOnSharedPreferenceChangeListener(preferenceListener)
        }
        super.onDestroy()
    }

    override fun onPause() {
        binding.input.clearFocus()
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        bottomSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN
        changeActionBarVisibility(viewModel.appbarEnabled.value ?: true)

        (activity as? MainActivity)?.apply {
            if (channelToOpen.isNotBlank()) {
                val index = viewModel.channels.value?.indexOf(channelToOpen)
                if (index != null && index >= 0) {
                    if (index == binding.viewPager.currentItem) {
                        clearNotificationsOfChannel(channelToOpen)
                    } else {
                        binding.viewPager.setCurrentItem(index, false)
                    }
                }
                channelToOpen = ""
            } else {
                val activeChannel = viewModel.activeChannel.value ?: return
                clearNotificationsOfChannel(activeChannel)
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.menu, menu)
    }

    override fun onPrepareOptionsMenu(menu: Menu) {
        with(menu) {
            val isLoggedIn = twitchPreferences.isLoggedIn
            val shouldShowProgress = viewModel.showUploadProgress.value ?: false
            findItem(R.id.menu_login)?.isVisible = !isLoggedIn
            findItem(R.id.menu_remove)?.isVisible = !viewModel.channels.value.isNullOrEmpty()
            findItem(R.id.menu_open)?.isVisible = !viewModel.channels.value.isNullOrEmpty()

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
            R.id.menu_login -> navigateSafe(R.id.action_mainFragment_to_loginFragment)
            R.id.menu_add -> openAddChannelDialog()
            R.id.menu_open -> openChannel()
            R.id.menu_remove -> removeChannel()
            R.id.menu_reload_emotes -> reloadEmotes()
            R.id.menu_choose_image -> checkPermissionForGallery()
            R.id.menu_choose_video -> checkPermissionForGallery(pickVideo = true)
            R.id.menu_capture_image -> startCameraCapture()
            R.id.menu_capture_video -> startCameraCapture(captureVideo = true)
            R.id.menu_hide -> viewModel.appbarEnabled.value = false
            R.id.menu_clear -> clear()
            R.id.menu_settings -> navigateSafe(R.id.action_mainFragment_to_overviewSettingsFragment)
            else -> return false
        }
        return true
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        when (requestCode) {
            GALLERY_REQUEST, GALLERY_REQUEST_VIDEO -> handleGalleryRequest(resultCode, data)
            CAPTURE_REQUEST, CAPTURE_REQUEST_VIDEO -> handleCaptureRequest(requestCode, resultCode)
        }
        super.onActivityResult(requestCode, resultCode, data)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        if ((requestCode == GALLERY_REQUEST || requestCode == GALLERY_REQUEST_VIDEO) && grantResults.getOrNull(0) == PackageManager.PERMISSION_GRANTED) {
            startGalleryPicker(pickVideo = requestCode == GALLERY_REQUEST_VIDEO)
        }
    }

    fun onMessageHistoryDisclaimerResult(result: Boolean) {
        twitchPreferences.hasMessageHistoryAcknowledged = true
        preferences.edit { putBoolean(getString(R.string.preference_load_message_history_key), result) }

        if (viewModel.connectionState.value == SystemMessageType.NOT_LOGGED_IN) {
            showApiChangeInformationIfNotAcknowledged()
        }

        val oAuth = twitchPreferences.oAuthKey ?: ""
        val name = twitchPreferences.userName ?: ""
        val id = twitchPreferences.userId
        viewModel.loadData(oauth = oAuth, id = id, loadTwitchData = true, loadHistory = result, name = name)

        if (name.isNotBlank() && oAuth.isNotBlank()) {
            showSnackbar(getString(R.string.snackbar_login, name))
        }
    }

    fun addChannel(channel: String) {
        val lowerCaseChannel = channel.toLowerCase(Locale.getDefault())
        val channels = viewModel.channels.value ?: emptyList()
        if (!channels.contains(lowerCaseChannel)) {
            val oauth = twitchPreferences.oAuthKey ?: ""
            val id = twitchPreferences.userId
            val name = twitchPreferences.userName ?: ""
            val shouldLoadHistory = preferences.getBoolean(getString(R.string.preference_load_message_history_key), true)

            val updatedChannels = viewModel.joinChannel(lowerCaseChannel)
            if (updatedChannels != null) {
                viewModel.loadData(oauth, id, loadTwitchData = false, loadHistory = shouldLoadHistory, name = name, channelList = listOf(channel))
                twitchPreferences.channelsString = updatedChannels.joinToString(",")

                tabAdapter.addFragment(lowerCaseChannel)
                binding.viewPager.offscreenPageLimit = calculatePageLimit(updatedChannels.size)
                binding.viewPager.setCurrentItem(updatedChannels.size - 1, false)

                fetchStreamInformation()
                activity?.invalidateOptionsMenu()
            }
        }
    }

    fun mentionUser(user: String) {
        if (binding.input.isEnabled) {
            val current = binding.input.text.trimEnd().toString()
            val template = preferences.getString(getString(R.string.preference_mention_format_key), "name") ?: "name"
            val mention = template.replace("name", user)
            val inputWithMention = if (current.isBlank()) "$mention " else "$current $mention "

            binding.input.setText(inputWithMention)
            binding.input.setSelection(inputWithMention.length)
        }
    }

    private fun insertEmote(emote: String) {
        val current = binding.input.text.toString()
        val currentWithEmote = "$current$emote "
        binding.input.setText(currentWithEmote)
        binding.input.setSelection(currentWithEmote.length)
    }

    private fun fetchStreamInformation() {
        lifecycleScope.launchWhenResumed {
            val key = getString(R.string.preference_streaminfo_key)
            if (preferences.getBoolean(key, true)) {
                val oAuth = twitchPreferences.oAuthKey ?: return@launchWhenResumed
                viewModel.fetchStreamData(oAuth) {
                    resources.getQuantityString(R.plurals.viewers, it, it)
                }
            }
        }
    }

    private fun sendMessage(): Boolean {
        viewModel.activeChannel.value?.let {
            val msg = binding.input.text.toString()
            viewModel.sendMessage(it, msg)
            binding.input.setText("")
        }
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
            is ImageUploadState.Failed -> result.errorMessage?.let { showSnackbar(getString(R.string.snackbar_upload_failed_cause, it)) } ?: showSnackbar(getString(R.string.snackbar_upload_failed))
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
            is DataLoadingState.Failed -> showSnackbar(getString(R.string.snackbar_data_load_failed_cause, result.t.message))
        }
    }

    private fun handleLoginRequest(success: Boolean) {
        val oAuth = twitchPreferences.oAuthKey
        val name = twitchPreferences.userName
        val id = twitchPreferences.userId

        if (success && !oAuth.isNullOrBlank() && !name.isNullOrBlank() && id != 0) {
            viewModel.close(name, oAuth, true, id)
            twitchPreferences.isLoggedIn = true
            showSnackbar(getString(R.string.snackbar_login, name))
        } else {
            showSnackbar(getString(R.string.snackbar_login_failed))
        }
    }

    private fun handleGalleryRequest(resultCode: Int, data: Intent?) {
        if (resultCode == Activity.RESULT_OK) {
            val uri = data?.data ?: return
            val contentResolver = activity?.contentResolver ?: return
            val context = context ?: return
            val mimeType = contentResolver.getType(uri)
            val mimeTypeMap = MimeTypeMap.getSingleton()
            val extension = mimeTypeMap.getExtensionFromMimeType(mimeType)
            if (extension == null) {
                showSnackbar(getString(R.string.snackbar_upload_failed))
                return
            }

            val copy = MediaUtils.createMediaFile(context, extension)
            try {
                contentResolver.openInputStream(uri)?.run { copy.outputStream().use { copyTo(it) } }
                if (copy.extension == "jpg" || copy.extension == "jpeg") {
                    MediaUtils.removeExifAttributes(copy.absolutePath)
                }

                viewModel.uploadMedia(copy)
            } catch (t: Throwable) {
                copy.delete()
                showSnackbar(getString(R.string.snackbar_upload_failed))
            }
        }
    }

    private fun handleCaptureRequest(requestCode: Int, resultCode: Int) {
        if (resultCode == Activity.RESULT_OK) {
            val mediaFile = File(currentMediaPath)
            try {
                // only remove exif data if an image was selected
                if (requestCode == CAPTURE_REQUEST) {
                    MediaUtils.removeExifAttributes(currentMediaPath)
                }

                viewModel.uploadMedia(mediaFile)
            } catch (e: IOException) {
                mediaFile.delete()
                showSnackbar(getString(R.string.snackbar_upload_failed))
            }
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

    private fun checkPermissionForGallery(pickVideo: Boolean = false) {
        showNuulsUploadDialogIfNotAcknowledged {
            if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                val requestCode = if (pickVideo) GALLERY_REQUEST_VIDEO else GALLERY_REQUEST
                requestPermissions(arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), requestCode)
            } else startGalleryPicker(pickVideo)
        }
    }

    private fun startCameraCapture(captureVideo: Boolean = false) {
        val packageManager = activity?.packageManager ?: return
        val packageName = activity?.packageName ?: return
        val (action, extension, request) = when {
            captureVideo -> Triple(MediaStore.ACTION_VIDEO_CAPTURE, "mp4", CAPTURE_REQUEST_VIDEO)
            else -> Triple(MediaStore.ACTION_IMAGE_CAPTURE, "jpg", CAPTURE_REQUEST)
        }
        showNuulsUploadDialogIfNotAcknowledged {
            Intent(action).also { captureIntent ->
                captureIntent.resolveActivity(packageManager)?.also {
                    try {
                        MediaUtils.createMediaFile(requireContext(), extension).apply { currentMediaPath = absolutePath }
                    } catch (ex: IOException) {
                        null
                    }?.also {
                        val uri = FileProvider.getUriForFile(requireContext(), "$packageName.fileprovider", it)
                        captureIntent.putExtra(MediaStore.EXTRA_OUTPUT, uri)
                        startActivityForResult(captureIntent, request)
                    }
                }
            }
        }
    }

    private fun startGalleryPicker(pickVideo: Boolean = false) {
        val packageManager = activity?.packageManager ?: return
        val (mediaStoreUri, requestCode) = when {
            pickVideo -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI to GALLERY_REQUEST_VIDEO
            else -> MediaStore.Images.Media.EXTERNAL_CONTENT_URI to GALLERY_REQUEST
        }
        Intent(Intent.ACTION_PICK, mediaStoreUri).run {
            resolveActivity(packageManager)?.also { startActivityForResult(this, requestCode) }
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
            var isMultiWindow = false
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                isMultiWindow = activity?.isInMultiWindowMode ?: false
            }

            if (!isMultiWindow) {
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

    private fun reloadEmotes() {
        val position = binding.tabs.selectedTabPosition
        if (position in 0 until tabAdapter.titleList.size) {
            val oAuth = twitchPreferences.oAuthKey ?: return
            val userId = twitchPreferences.userId
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
        twitchPreferences = DankChatPreferenceStore(context)
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
                keepScreenOnKey -> keepScreenOn(p.getBoolean(key, true))
                suggestionsKey -> binding.input.setSuggestionAdapter(p.getBoolean(key, true), suggestionAdapter)
            }
        }
        preferences = PreferenceManager.getDefaultSharedPreferences(context).apply {
            registerOnSharedPreferenceChangeListener(preferenceListener)
            keepScreenOn(getBoolean(keepScreenOnKey, true))
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

    private fun showSnackbar(message: String) {
        binding.inputLayout.post {
            Snackbar.make(binding.coordinator, message, Snackbar.LENGTH_SHORT).apply {
                if (binding.inputLayout.isVisible) anchorView = binding.inputLayout
            }.show()
        }
    }

    private fun showLogoutConfirmationDialog() = MaterialAlertDialogBuilder(requireContext())
        .setTitle(getString(R.string.confirm_logout_title))
        .setMessage(getString(R.string.confirm_logout_message))
        .setPositiveButton(getString(R.string.confirm_logout_positive_button)) { dialog, _ ->
            twitchPreferences.userName = ""
            twitchPreferences.oAuthKey = ""
            twitchPreferences.userId = 0
            twitchPreferences.isLoggedIn = false

            viewModel.close("", "")
            viewModel.clearIgnores()
            dialog.dismiss()
        }
        .setNegativeButton(getString(R.string.confirm_logout_negative_button)) { dialog, _ -> dialog.dismiss() }
        .create().show()

    private fun openAddChannelDialog() = EditTextDialogFragment.create(
        R.string.add_dialog_title,
        R.string.dialog_cancel,
        R.string.dialog_ok,
        textHint = getString(R.string.add_channel_hint)
    ).show(parentFragmentManager, DIALOG_TAG)

    private fun openChannel() {
        val channel = viewModel.activeChannel.value ?: return
        val url = "https://twitch.tv/$channel"
        Intent(Intent.ACTION_VIEW).also {
            it.data = Uri.parse(url)
            startActivity(it)
        }
    }

    private fun removeChannel() {
        val channels = viewModel.partChannel()
        if (channels != null) {
            val index = binding.viewPager.currentItem
            if (channels.isNotEmpty()) {
                twitchPreferences.channelsString = channels.joinToString(",")
                val newPos = (index - 1).coerceAtLeast(0)
                binding.viewPager.setCurrentItem(newPos, false)
            } else {
                twitchPreferences.channelsString = null
            }

            binding.viewPager.offscreenPageLimit = calculatePageLimit(channels.size)
            tabAdapter.removeFragment(index)
            activity?.invalidateOptionsMenu()
        }
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
                    bottomSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN
                    binding.input.dismissDropDown()
                }
            }
        })
    }

    private fun TextInputLayout.setup() {
        setEndIconOnClickListener { sendMessage() }
        setEndIconOnLongClickListener { getLastMessage() }
        setStartIconOnClickListener {
            if (bottomSheetBehavior.state == BottomSheetBehavior.STATE_EXPANDED
                || bottomSheetBehavior.state == BottomSheetBehavior.STATE_COLLAPSED
                || emoteMenuAdapter.currentList.isEmpty()
            ) {
                bottomSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN
                return@setStartIconOnClickListener
            }

            val isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
            if (isLandscape) {
                hideKeyboard()
            }

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
                bottomSheetBehavior.apply {
                    peekHeight = (resources.displayMetrics.heightPixels * heightScaleFactor).toInt()
                    state = BottomSheetBehavior.STATE_EXPANDED

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
            binding.input.dropDownHeight = if (count > 2) {
                (binding.viewPager.measuredHeight / 1.3).roundToInt()
            } else {
                ViewGroup.LayoutParams.WRAP_CONTENT
            }
            binding.input.dropDownWidth = (binding.viewPager.measuredWidth * 0.6).roundToInt()
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
                    if (!isPopupShowing) sendMessage() else false
                }
                else -> false
            }
        }

        var wasLandScapeNotFullscreen = false
        setOnFocusChangeListener { _, hasFocus ->
            val isDarkMode =
                preferences.getBoolean(getString(R.string.preference_dark_theme_key), true)
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
                    var isMultiWindow = false
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        isMultiWindow = activity?.isInMultiWindowMode ?: false
                    }

                    if (!isMultiWindow) {
                        (View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                                or View.SYSTEM_UI_FLAG_FULLSCREEN
                                or lightModeFlags)
                    } else View.VISIBLE or lightModeFlags
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
        private const val GALLERY_REQUEST = 69
        private const val GALLERY_REQUEST_VIDEO = 70
        private const val CAPTURE_REQUEST = 420
        private const val CAPTURE_REQUEST_VIDEO = 421

        const val LOGOUT_REQUEST_KEY = "logout_key"
        const val LOGIN_REQUEST_KEY = "login_key"
        const val THEME_CHANGED_KEY = "theme_changed_key"
    }
}
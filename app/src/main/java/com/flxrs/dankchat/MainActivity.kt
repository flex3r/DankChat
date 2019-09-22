package com.flxrs.dankchat

import android.Manifest
import android.app.Activity
import android.content.*
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Color
import android.os.Bundle
import android.os.IBinder
import android.provider.MediaStore
import android.view.*
import android.view.inputmethod.EditorInfo
import android.webkit.MimeTypeMap
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.observe
import androidx.preference.PreferenceManager
import androidx.viewpager2.widget.ViewPager2
import com.flxrs.dankchat.chat.ChatTabAdapter
import com.flxrs.dankchat.chat.suggestion.EmoteSuggestionsArrayAdapter
import com.flxrs.dankchat.chat.suggestion.SpaceTokenizer
import com.flxrs.dankchat.databinding.MainActivityBinding
import com.flxrs.dankchat.preferences.DankChatPreferenceStore
import com.flxrs.dankchat.preferences.SettingsActivity
import com.flxrs.dankchat.service.TwitchService
import com.flxrs.dankchat.service.api.TwitchApi
import com.flxrs.dankchat.service.twitch.emote.GenericEmote
import com.flxrs.dankchat.utils.CustomMultiAutoCompleteTextView
import com.flxrs.dankchat.utils.MediaUtils
import com.flxrs.dankchat.utils.dialog.AddChannelDialogResultHandler
import com.flxrs.dankchat.utils.dialog.AdvancedLoginDialogResultHandler
import com.flxrs.dankchat.utils.dialog.EditTextDialogFragment
import com.flxrs.dankchat.utils.extensions.isServiceRunning
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.tabs.TabLayoutMediator
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.viewModel
import java.io.File
import java.io.IOException
import java.util.*
import kotlin.math.roundToInt

class MainActivity : AppCompatActivity(), AddChannelDialogResultHandler,
    AdvancedLoginDialogResultHandler {
    private val viewModel: DankChatViewModel by viewModel()
    private val channels = mutableListOf<String>()
    private lateinit var twitchPreferences: DankChatPreferenceStore
    private lateinit var preferenceListener: SharedPreferences.OnSharedPreferenceChangeListener
    private lateinit var preferences: SharedPreferences
    private lateinit var binding: MainActivityBinding
    private lateinit var tabAdapter: ChatTabAdapter
    private lateinit var tabLayoutMediator: TabLayoutMediator
    private var currentImagePath = ""
    private var showProgressBar = false
    private var currentChannel: String? = null

    private var twitchService: TwitchService? = null
    private var isBound = false

    private val twitchServiceConnection = TwitchServiceConnection()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        initPreferences()

        tabAdapter = ChatTabAdapter(supportFragmentManager, lifecycle)
        twitchPreferences.getChannelsAsString()?.let { channels.addAll(it.split(',')) }
            ?: twitchPreferences.getChannels()?.let {
                channels.addAll(it)
                twitchPreferences.setChannels(null)
            }
        channels.forEach { tabAdapter.addFragment(it) }

        binding = DataBindingUtil.setContentView<MainActivityBinding>(this, R.layout.main_activity)
            .apply {
                vm = viewModel
                lifecycleOwner = this@MainActivity
                viewPager.setup()
                input.setup()

                tabLayoutMediator = TabLayoutMediator(tabs, viewPager) { tab, position ->
                    tab.text = tabAdapter.titleList[position]
                }.apply { attach() }

                inputLayout.setEndIconOnClickListener { sendMessage() }
                showActionbarFab.setOnClickListener { viewModel.appbarEnabled.value = true }
            }

        window.decorView.setOnApplyWindowInsetsListener { _, insets ->
            window.decorView.onApplyWindowInsets(insets)
            val cutout = insets.stableInsetTop
            if (resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT
                && binding.showActionbarFab.visibility == View.VISIBLE
                && !binding.input.hasFocus()
            ) {
                binding.showActionbarFab.apply {
                    y = cutout.toFloat()
                    requestLayout()
                }

            }
            insets
        }

        viewModel.apply {
            imageUploadedEvent.observe(this@MainActivity, ::handleImageUploadEvent)
            emoteCodes.observe(this@MainActivity, ::setupSuggestionAdapter)
            appbarEnabled.observe(this@MainActivity) { changeActionBarVisibility(it) }

            canType.observe(this@MainActivity) {
                binding.input.isEnabled = it == "Start chatting"
                binding.inputLayout.hint = it
            }

            bottomTextEnabled.observe(this@MainActivity) {
                binding.inputLayout.isHelperTextEnabled = it
            }
            bottomText.observe(this@MainActivity) {
                binding.inputLayout.apply {
                    val helperId = com.google.android.material.R.id.textinput_helper_text
                    val previous = helperText
                    helperText = it

                    if (previous?.isBlank() == false) findViewById<TextView>(helperId)?.run {
                        updatePadding(top = 12) //TODO check if still needed
                    }
                }
            }
        }

        if (savedInstanceState == null && twitchService?.startedConnection != true) {
            loadData()
        }

        setSupportActionBar(binding.toolbar)
        updateViewPagerVisibility()
        fetchStreamInformation()
    }

    override fun onDestroy() {
        super.onDestroy()
        preferences.unregisterOnSharedPreferenceChangeListener(preferenceListener)
    }

    override fun onPause() {
        super.onPause()
        binding.input.clearFocus()
    }

    override fun onResume() {
        super.onResume()
        reconnect(true)
    }

    override fun onStart() {
        super.onStart()
        Intent(this, TwitchService::class.java).also {
            if (twitchService == null && !isServiceRunning(TwitchService::class.java)) {
                ContextCompat.startForegroundService(this, it)
                val oauth = twitchPreferences.getOAuthKey() ?: ""
                val name = twitchPreferences.getUserName() ?: ""
                if (name.isNotBlank() && oauth.isNotBlank()) showSnackbar("Logged in as $name")
            }
            if (!isBound) bindService(it, twitchServiceConnection, 0)
        }
    }

    override fun onStop() {
        super.onStop()
        if (isBound) {
            unbindService(twitchServiceConnection)
            isBound = false
        }
    }

    override fun onBackPressed() {
        moveTaskToBack(true)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu, menu)
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu?): Boolean {
        menu?.apply {
            val isLoggedIn = twitchPreferences.isLoggedin()
            findItem(R.id.menu_login)?.isVisible = !isLoggedIn
            findItem(R.id.menu_remove)?.isVisible = channels.isNotEmpty()

            findItem(R.id.progress)?.apply {
                isVisible = showProgressBar
                actionView = ProgressBar(this@MainActivity).apply {
                    indeterminateTintList =
                        ContextCompat.getColorStateList(this@MainActivity, android.R.color.white)
                    isVisible = showProgressBar
                }
            }
        }
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.menu_reconnect -> reconnect(false)
            R.id.menu_login_default -> Intent(this, LoginActivity::class.java).apply {
                startActivityForResult(this, LOGIN_REQUEST)
            }
            R.id.menu_login_advanced -> showAdvancedLoginDialog()
            R.id.menu_add -> addChannel()
            R.id.menu_remove -> removeChannel()
            R.id.menu_reload_emotes -> reloadEmotes()
            R.id.menu_choose_image -> checkPermissionForGallery()
            R.id.menu_capture_image -> startCameraCapture()
            R.id.menu_hide -> viewModel.appbarEnabled.value = false
            R.id.menu_clear -> clear()
            R.id.menu_settings -> Intent(this, SettingsActivity::class.java).apply {
                startActivityForResult(this, SETTINGS_REQUEST)
            }
            else -> return false
        }
        return true
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        when (requestCode) {
            LOGIN_REQUEST -> handleLoginRequest(resultCode)
            GALLERY_REQUEST -> handleGalleryRequest(resultCode, data)
            CAPTURE_REQUEST -> handleCaptureRequest(resultCode)
            SETTINGS_REQUEST -> if (resultCode == Activity.RESULT_OK
                && data?.getBooleanExtra(LOGOUT_REQUEST_KEY, false) == true
            ) {
                showLogoutConfirmationDialog()
            }
        }
        super.onActivityResult(requestCode, resultCode, data)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        if (requestCode == GALLERY_REQUEST && grantResults.getOrNull(0) == PackageManager.PERMISSION_GRANTED) {
            startGalleryPicker()
        }
    }

    override fun onAddChannelDialogResult(channel: String) {
        val lowerCaseChannel = channel.toLowerCase(Locale.getDefault())
        if (!channels.contains(lowerCaseChannel)) {
            val oauth = twitchPreferences.getOAuthKey() ?: ""
            val id = twitchPreferences.getUserId()

            twitchService?.joinChannel(lowerCaseChannel)
            viewModel.loadData(lowerCaseChannel, oauth, id, load3rdParty = true, reAuth = false)
            channels.add(lowerCaseChannel)
            twitchPreferences.setChannelsString(channels.joinToString(","))

            tabAdapter.addFragment(lowerCaseChannel)
            binding.viewPager.offscreenPageLimit = calculatePageLimit()
            binding.viewPager.setCurrentItem(channels.size - 1, false)

            fetchStreamInformation()
            invalidateOptionsMenu()
            updateViewPagerVisibility()
        }
    }

    override fun onAdvancedLoginDialogResult(token: String) {
        val tokenWithoutSuffix = when {
            token.startsWith("oauth:", true) -> token.substringAfter(':')
            else -> token
        }

        lifecycleScope.launch {
            TwitchApi.getUser(tokenWithoutSuffix)?.let {
                if (it.name.isNotBlank()) {
                    twitchPreferences.apply {
                        setOAuthKey("oauth:$tokenWithoutSuffix")
                        setUserName(it.name.toLowerCase(Locale.getDefault()))
                        setUserId(it.id)
                        setLoggedIn(true)
                    }
                    twitchService?.close {
                        connectAndJoinChannels(it.name, "oauth:$tokenWithoutSuffix")
                        loadData("oauth:$tokenWithoutSuffix", it.id)
                    }
                    showSnackbar("Logged in as ${it.name}")
                } else showSnackbar("Failed to login")
            } ?: showSnackbar("Invalid OAuth token")
        }
    }

    fun mentionUser(user: String) {
        if (binding.input.isEnabled) {
            val current = binding.input.text.trimEnd().toString()
            val template =
                preferences.getString(getString(R.string.preference_mention_format_key), "name")
                    ?: "name"
            val mention = template.replace("name", user)
            val inputWithMention = if (current.isBlank()) "$mention " else "$current $mention "

            binding.input.setText(inputWithMention)
            binding.input.setSelection(inputWithMention.length)
        }
    }

    private fun fetchStreamInformation() {
        val key = getString(R.string.preference_streaminfo_key)
        if (::preferences.isInitialized && preferences.getBoolean(key, true)) {
            viewModel.fetchStreamData(channels) {
                resources.getQuantityString(R.plurals.viewers, it, it)
            }
        }
    }

    private fun sendMessage(): Boolean {
        currentChannel?.let {
            val msg = binding.input.text.toString()
            twitchService?.sendMessage(it, msg)
            binding.input.setText("")
        }
        return true
    }

    private fun handleImageUploadEvent(result: Pair<String?, File>) {
        val message = result.first?.let {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("nuuls image url", it))
            "Copied: $it"
        } ?: "Error during upload"

        showSnackbar(message)
        showProgressBar = false
        invalidateOptionsMenu()
        result.second.delete()
    }

    private fun handleLoginRequest(resultCode: Int) {
        val oauth = twitchPreferences.getOAuthKey()
        val name = twitchPreferences.getUserName()
        val id = twitchPreferences.getUserId()

        if (resultCode == Activity.RESULT_OK && !oauth.isNullOrBlank() && !name.isNullOrBlank() && id != 0) {
            twitchService?.close {
                connectAndJoinChannels(name, oauth)
                loadData(oauth, id)
            }

            twitchPreferences.setLoggedIn(true)
            showSnackbar("Logged in as $name")
        } else {
            showSnackbar("Failed to login")
        }
    }

    private fun handleGalleryRequest(resultCode: Int, data: Intent?) {
        if (resultCode == Activity.RESULT_OK) {
            val uri = data?.data ?: return
            val mimeType = contentResolver.getType(uri)
            val mimeTypeMap = MimeTypeMap.getSingleton()
            val extension = mimeTypeMap.getExtensionFromMimeType(mimeType)
            if (extension == null) {
                showSnackbar("Error during upload")
                return
            }

            val copy = MediaUtils.createImageFile(this, extension)
            try {
                contentResolver.openInputStream(uri)?.run { copy.outputStream().use { copyTo(it) } }
                if (copy.extension == "jpg" || copy.extension == "jpeg") {
                    MediaUtils.removeExifAttributes(copy.absolutePath)
                }

                viewModel.uploadImage(copy)
                showProgressBar = true
                invalidateOptionsMenu()
            } catch (t: Throwable) {
                copy.delete()
                showSnackbar("Error during upload")
            }
        }
    }

    private fun handleCaptureRequest(resultCode: Int) {
        if (resultCode == Activity.RESULT_OK) {
            val imageFile = File(currentImagePath)
            try {
                MediaUtils.removeExifAttributes(currentImagePath)

                viewModel.uploadImage(imageFile)
                showProgressBar = true
                invalidateOptionsMenu()
            } catch (e: IOException) {
                imageFile.delete()
                showSnackbar("Error during upload")
            }
        }
    }

    private fun setupSuggestionAdapter(suggestions: List<GenericEmote>) {
        val adapter = EmoteSuggestionsArrayAdapter(this@MainActivity, suggestions) { count ->
            binding.input.dropDownHeight = if (count > 2) {
                (binding.viewPager.measuredHeight / 1.3).roundToInt()
            } else {
                ViewGroup.LayoutParams.WRAP_CONTENT
            }
            binding.input.dropDownWidth = binding.viewPager.measuredWidth / 2
        }

        binding.input.setAdapter(adapter)
    }

    private fun showNuulsUploadDialogIfNotAcknowledged(action: () -> Unit) {
        if (!twitchPreferences.getNuulsAcknowledge()) {
            MaterialAlertDialogBuilder(this)
                .setCancelable(false)
                .setTitle(R.string.nuuls_upload_title)
                .setMessage(R.string.nuuls_upload_disclaimer)
                .setPositiveButton(R.string.dialog_positive_button) { dialog, _ ->
                    dialog.dismiss()
                    twitchPreferences.setNuulsAcknowledge(true)
                    action()
                }
                .show()
        } else action()
    }

    private fun checkPermissionForGallery() {
        showNuulsUploadDialogIfNotAcknowledged {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                ) != PackageManager.PERMISSION_GRANTED
            )
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
                    GALLERY_REQUEST
                )
            else startGalleryPicker()
        }
    }

    private fun startCameraCapture() {
        showNuulsUploadDialogIfNotAcknowledged {
            Intent(MediaStore.ACTION_IMAGE_CAPTURE).also { captureIntent ->
                captureIntent.resolveActivity(packageManager)?.also {
                    try {
                        MediaUtils.createImageFile(this).apply { currentImagePath = absolutePath }
                    } catch (ex: IOException) {
                        null
                    }?.also {
                        val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", it)
                        captureIntent.putExtra(MediaStore.EXTRA_OUTPUT, uri)
                        startActivityForResult(captureIntent, CAPTURE_REQUEST)
                    }
                }
            }
        }
    }

    private fun startGalleryPicker() {
        Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI).run {
            resolveActivity(packageManager)?.also { startActivityForResult(this, GALLERY_REQUEST) }
        }
    }

    private fun changeActionBarVisibility(enabled: Boolean) {
        if (enabled) {
            window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN)
            supportActionBar?.show()
            binding.showActionbarFab.visibility = View.GONE
            binding.tabs.visibility = View.VISIBLE
        } else {
            window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_FULLSCREEN)
            supportActionBar?.hide()
            binding.tabs.visibility = View.GONE
            binding.showActionbarFab.visibility = View.VISIBLE
        }

        if (binding.input.hasFocus()) {
            binding.input.requestFocus()
        }
    }

    private fun clear() {
        val position = binding.tabs.selectedTabPosition
        if (position in 0 until tabAdapter.titleList.size)
            viewModel.clear(tabAdapter.titleList[position])
    }

    private fun reloadEmotes() {
        val position = binding.tabs.selectedTabPosition
        if (position in 0 until tabAdapter.titleList.size) {
            val oauth = twitchPreferences.getOAuthKey() ?: ""
            val userId = twitchPreferences.getUserId()
            viewModel.reloadEmotes(tabAdapter.titleList[position], oauth, userId)
        }
    }

    private fun reconnect(onlyIfNecessary: Boolean = false) {
        twitchService?.reconnect(onlyIfNecessary)
    }

    private fun connectAndJoinChannels(name: String, oauth: String) {
        if (twitchService?.startedConnection == false) {
            channels.forEachIndexed { i, channel ->
                if (i == 0) twitchService?.connect(name, oauth)
                twitchService?.joinChannel(channel)
            }
        }
    }

    private fun loadData(
        oAuth: String = twitchPreferences.getOAuthKey()?.substringAfter("oauth:") ?: "",
        id: Int = twitchPreferences.getUserId()
    ) {
        if (channels.isEmpty()) {
            viewModel.loadData("", oAuth, id, true, reAuth = true)
        } else channels.forEachIndexed { i, channel ->
            viewModel.loadData(channel, oAuth, id, true, reAuth = i == 0)
        }
    }

    private fun initPreferences() {
        twitchPreferences = DankChatPreferenceStore(this)
        preferenceListener = SharedPreferences.OnSharedPreferenceChangeListener { p, key ->
            when (key) {
                getString(R.string.preference_roomstate_key) -> {
                    viewModel.setRoomStateEnabled(p.getBoolean(key, true))
                }
                getString(R.string.preference_streaminfo_key) -> {
                    fetchStreamInformation()
                    viewModel.setStreamInfoEnabled(p.getBoolean(key, true))
                }
            }
        }
        preferences = PreferenceManager.getDefaultSharedPreferences(this).apply {
            registerOnSharedPreferenceChangeListener(preferenceListener)
            val roomStateKey = getString(R.string.preference_roomstate_key)
            viewModel.setRoomStateEnabled(getBoolean(roomStateKey, true))

            val streamInfoKey = getString(R.string.preference_streaminfo_key)
            viewModel.setStreamInfoEnabled(getBoolean(streamInfoKey, true))
        }
    }

    private fun updateViewPagerVisibility() {
        viewModel.shouldShowViewPager.value = channels.size > 0
    }

    private fun showSnackbar(message: String) {
        Snackbar.make(binding.root, message, Snackbar.LENGTH_SHORT).apply {
            view.setBackgroundResource(R.color.colorPrimary)
            setTextColor(Color.WHITE)
        }.show()
    }

    private fun showLogoutConfirmationDialog() = MaterialAlertDialogBuilder(this)
        .setTitle(getString(R.string.confirm_logout_title))
        .setMessage(getString(R.string.confirm_logout_message))
        .setPositiveButton(getString(R.string.confirm_logout_positive_button)) { dialog, _ ->
            twitchService?.close { connectAndJoinChannels("", "") }
            twitchPreferences.setUserName("")
            twitchPreferences.setOAuthKey("")
            twitchPreferences.setUserId(0)
            twitchPreferences.setLoggedIn(false)
            viewModel.clearIgnores()
            dialog.dismiss()
        }
        .setNegativeButton(getString(R.string.confirm_logout_negative_button)) { dialog, _ -> dialog.dismiss() }
        .create().show()

    private fun showAdvancedLoginDialog() = EditTextDialogFragment.create(
        R.string.login_with_oauth,
        R.string.confirm_logout_negative_button,
        R.string.login,
        R.string.required_oauth_scopes,
        "Token",
        false
    ).show(supportFragmentManager, DIALOG_TAG)

    private fun addChannel() = EditTextDialogFragment.create(
        R.string.dialog_title,
        R.string.dialog_negative_button,
        R.string.dialog_positive_button,
        textHint = "Channel",
        isAddChannel = true
    ).show(supportFragmentManager, DIALOG_TAG)

    private fun removeChannel() {
        val index = binding.viewPager.currentItem
        val channel = channels[index]
        channels.remove(channel)
        twitchService?.partChannel(channel)
        viewModel.removeChannelData(channel)

        if (channels.size > 0) {
            twitchPreferences.setChannelsString(channels.joinToString(","))
            val newPos = (index - 1).coerceAtLeast(0)
            binding.viewPager.setCurrentItem(newPos, false)
        } else {
            twitchPreferences.setChannelsString(null)
            currentChannel = null
        }

        binding.viewPager.offscreenPageLimit = calculatePageLimit()
        tabAdapter.removeFragment(index)

        invalidateOptionsMenu()
        updateViewPagerVisibility()
    }

    private fun calculatePageLimit(): Int {
        return if (channels.size > 1) {
            channels.size - 1
        } else {
            ViewPager2.OFFSCREEN_PAGE_LIMIT_DEFAULT
        }
    }

    private fun ViewPager2.setup() {
        adapter = tabAdapter
        offscreenPageLimit = calculatePageLimit()
        registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                if (position in 0 until tabAdapter.fragmentList.size) {
                    val newChannel = tabAdapter.titleList[position].toLowerCase(Locale.getDefault())
                    currentChannel = newChannel
                    viewModel.setActiveChannel(newChannel)
                }
            }
        })
    }

    private fun CustomMultiAutoCompleteTextView.setup() {
        setDropDownBackgroundResource(R.color.colorPrimary)
        setTokenizer(SpaceTokenizer())
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

        setOnFocusChangeListener { _, hasFocus ->
            window.decorView.systemUiVisibility = when {
                !hasFocus && binding.showActionbarFab.isVisible -> (View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        or View.SYSTEM_UI_FLAG_FULLSCREEN)
                !hasFocus && !binding.showActionbarFab.isVisible -> (View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN)
                else -> (View.SYSTEM_UI_FLAG_LAYOUT_STABLE)
            }
        }
    }

    private inner class TwitchServiceConnection : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            val binder = service as TwitchService.LocalBinder
            twitchService = binder.service
            isBound = true

            val oauth = twitchPreferences.getOAuthKey() ?: ""
            val name = twitchPreferences.getUserName() ?: ""
            connectAndJoinChannels(name, oauth)
        }

        override fun onServiceDisconnected(className: ComponentName?) {
            twitchService = null
            isBound = false
        }
    }

    companion object {
        private val TAG = MainActivity::class.java.simpleName
        private const val DIALOG_TAG = "add_channel_dialog"
        private const val LOGIN_REQUEST = 42
        private const val GALLERY_REQUEST = 69
        private const val CAPTURE_REQUEST = 420
        private const val SETTINGS_REQUEST = 777

        const val LOGOUT_REQUEST_KEY = "logout_key"
    }
}
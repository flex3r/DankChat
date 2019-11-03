package com.flxrs.dankchat

import android.Manifest
import android.app.Activity
import android.content.*
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.provider.MediaStore
import android.util.Log
import android.view.*
import android.view.inputmethod.EditorInfo
import android.webkit.MimeTypeMap
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.view.isVisible
import androidx.core.view.postDelayed
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.observe
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.preference.PreferenceManager
import androidx.viewpager2.widget.ViewPager2
import com.flxrs.dankchat.chat.ChatTabAdapter
import com.flxrs.dankchat.chat.menu.EmoteMenuAdapter
import com.flxrs.dankchat.chat.menu.EmoteMenuTab
import com.flxrs.dankchat.chat.suggestion.EmoteSuggestionsArrayAdapter
import com.flxrs.dankchat.chat.suggestion.SpaceTokenizer
import com.flxrs.dankchat.databinding.MainActivityBinding
import com.flxrs.dankchat.preferences.DankChatPreferenceStore
import com.flxrs.dankchat.preferences.SettingsActivity
import com.flxrs.dankchat.service.TwitchService
import com.flxrs.dankchat.service.api.TwitchApi
import com.flxrs.dankchat.service.twitch.connection.ConnectionState
import com.flxrs.dankchat.service.twitch.emote.GenericEmote
import com.flxrs.dankchat.utils.CustomMultiAutoCompleteTextView
import com.flxrs.dankchat.utils.MediaUtils
import com.flxrs.dankchat.utils.dialog.AddChannelDialogResultHandler
import com.flxrs.dankchat.utils.dialog.AdvancedLoginDialogResultHandler
import com.flxrs.dankchat.utils.dialog.EditTextDialogFragment
import com.flxrs.dankchat.utils.extensions.hideKeyboard
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.tabs.TabLayoutMediator
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.viewModel
import java.io.File
import java.io.IOException
import java.util.*
import kotlin.math.max
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
    private lateinit var emoteMenuAdapter: EmoteMenuAdapter
    private lateinit var broadcastReceiver: BroadcastReceiver
    private lateinit var bottomSheetBehavior: BottomSheetBehavior<MaterialCardView>
    private var currentImagePath = ""
    private var showProgressBar = false

    private var currentChannel: String? = null
    private var twitchService: TwitchService? = null

    private var isBound = false
    private val twitchServiceConnection = TwitchServiceConnection()
    private var isLoggingIn = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        initPreferences()

        broadcastReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) = handleShutDown()
        }

        val filter = IntentFilter(SHUTDOWN_REQUEST_FILTER)
        LocalBroadcastManager.getInstance(this).registerReceiver(broadcastReceiver, filter)

        tabAdapter = ChatTabAdapter(supportFragmentManager, lifecycle)
        twitchPreferences.getChannelsAsString()?.let { channels.addAll(it.split(',')) }
            ?: twitchPreferences.getChannels()?.let {
                channels.addAll(it)
                twitchPreferences.setChannels(null)
            }
        channels.forEach { tabAdapter.addFragment(it) }

        emoteMenuAdapter = EmoteMenuAdapter(::insertEmote)

        binding = DataBindingUtil.setContentView<MainActivityBinding>(this, R.layout.main_activity)
            .apply {
                bottomSheetBehavior = BottomSheetBehavior.from(bottomSheet)
                vm = viewModel
                lifecycleOwner = this@MainActivity
                viewPager.setup()
                input.setup()
                inputLayout.setup()

                tabLayoutMediator = TabLayoutMediator(tabs, viewPager) { tab, position ->
                    tab.text = tabAdapter.titleList[position]
                }.apply { attach() }

                showActionbarFab.setOnClickListener { viewModel.appbarEnabled.value = true }
            }

        window.decorView.setOnApplyWindowInsetsListener { _, insets ->
            binding.showActionbarFab.apply {
                if (resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT
                    && isVisible
                ) {
                    y = if (binding.input.hasFocus()) {
                        max(insets.stableInsetTop.toFloat() - insets.systemWindowInsetTop, 0f)
                    } else {
                        insets.stableInsetTop.toFloat()
                    }
                }
            }
            insets
        }

        viewModel.apply {
            imageUploadedEvent.observe(this@MainActivity, ::handleImageUploadEvent)
            emoteSuggestions.observe(this@MainActivity, ::setupSuggestionAdapter)
            emoteItems.observe(this@MainActivity, emoteMenuAdapter::submitList)
            appbarEnabled.observe(this@MainActivity) { changeActionBarVisibility(it) }
            canType.observe(this@MainActivity) { if (it) binding.inputLayout.setup() }
            connectionState.observe(this@MainActivity) { hint ->
                binding.inputLayout.hint = when (hint) {
                    ConnectionState.CONNECTED     -> getString(R.string.hint_connected)
                    ConnectionState.NOT_LOGGED_IN -> getString(R.string.hint_not_logged_int)
                    ConnectionState.DISCONNECTED  -> getString(R.string.hint_disconnected)
                }
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

        if (savedInstanceState == null && !isLoggingIn && twitchService?.startedConnection != true) {
            val oauth = twitchPreferences.getOAuthKey() ?: ""
            val name = twitchPreferences.getUserName() ?: ""
            loadData(oAuth = oauth, name = name)

            if (name.isNotBlank() && oauth.isNotBlank()) {
                showSnackbar(getString(R.string.snackbar_login, name))
            }
        }

        setSupportActionBar(binding.toolbar)
        updateViewPagerVisibility()
        fetchStreamInformation()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (!isChangingConfigurations) {
            handleShutDown()
        }
    }

    override fun onPause() {
        super.onPause()
        binding.input.clearFocus()
    }

    override fun onResume() {
        super.onResume()
        bottomSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN
        changeActionBarVisibility(viewModel.appbarEnabled.value ?: true)
    }

    override fun onStart() {
        super.onStart()
        if (!isBound) Intent(this, TwitchService::class.java).also {
            try {
                ContextCompat.startForegroundService(this, it)
                bindService(it, twitchServiceConnection, Context.BIND_AUTO_CREATE)
            } catch (t: Throwable) {
                Log.e(TAG, Log.getStackTraceString(t))
            }
        }
    }

    override fun onStop() {
        super.onStop()
        if (isBound) {
            isBound = false
            try {
                unbindService(twitchServiceConnection)
            } catch (t: Throwable) {
                Log.e(TAG, Log.getStackTraceString(t))
            }
            if (!isChangingConfigurations) {
                twitchService?.shouldNotifyOnMention = true
            }
        }
    }

    override fun onBackPressed() {
        if (bottomSheetBehavior.state == BottomSheetBehavior.STATE_EXPANDED
            || bottomSheetBehavior.state == BottomSheetBehavior.STATE_COLLAPSED
        ) {
            bottomSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN
        } else {
            moveTaskToBack(true)
        }
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
            R.id.menu_reconnect      -> twitchService?.reconnect(false)
            R.id.menu_login_default  -> Intent(this, LoginActivity::class.java).apply {
                isLoggingIn = true
                startActivityForResult(this, LOGIN_REQUEST)
            }
            R.id.menu_login_advanced -> showAdvancedLoginDialog()
            R.id.menu_add            -> addChannel()
            R.id.menu_remove         -> removeChannel()
            R.id.menu_reload_emotes  -> reloadEmotes()
            R.id.menu_choose_image   -> checkPermissionForGallery()
            R.id.menu_capture_image  -> startCameraCapture()
            R.id.menu_hide           -> viewModel.appbarEnabled.value = false
            R.id.menu_clear          -> clear()
            R.id.menu_settings       -> Intent(this, SettingsActivity::class.java).apply {
                startActivityForResult(this, SETTINGS_REQUEST)
            }
            else                     -> return false
        }
        return true
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        when (requestCode) {
            LOGIN_REQUEST    -> handleLoginRequest(resultCode)
            GALLERY_REQUEST  -> handleGalleryRequest(resultCode, data)
            CAPTURE_REQUEST  -> handleCaptureRequest(resultCode)
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
            val name = twitchPreferences.getUserName() ?: ""

            twitchService?.joinChannel(lowerCaseChannel)
            viewModel.loadData(
                listOf(lowerCaseChannel),
                oauth,
                id,
                load3rdParty = true,
                loadTwitchData = false,
                name = name
            )
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
            else                             -> token
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
                        loadData(it.name, tokenWithoutSuffix, it.id)
                    }
                    showSnackbar(getString(R.string.snackbar_login, it.name))
                } else showSnackbar(getString(R.string.snackbar_login_failed))
            } ?: showSnackbar(getString(R.string.snackbar_invalid_oauth))
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

    private fun insertEmote(emote: String) {
        val current = binding.input.text.toString()
        val currentWithEmote = "$current$emote "
        binding.input.setText(currentWithEmote)
        binding.input.setSelection(currentWithEmote.length)
    }

    private fun handleShutDown() {
        preferences.unregisterOnSharedPreferenceChangeListener(preferenceListener)
        LocalBroadcastManager.getInstance(this).unregisterReceiver(broadcastReceiver)
        finishAndRemoveTask()
        Intent(this, TwitchService::class.java).also {
            stopService(it)
        }

        android.os.Process.killProcess(android.os.Process.myPid())
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
            getString(R.string.snackbar_image_uploaded, it)
        } ?: getString(R.string.snackbar_upload_failed)

        showSnackbar(message)
        showProgressBar = false
        invalidateOptionsMenu()
        result.second.delete()
    }

    private fun handleLoginRequest(resultCode: Int) {
        isLoggingIn = true
        val oauth = twitchPreferences.getOAuthKey()
        val name = twitchPreferences.getUserName()
        val id = twitchPreferences.getUserId()

        if (resultCode == Activity.RESULT_OK && !oauth.isNullOrBlank() && !name.isNullOrBlank() && id != 0) {
            twitchService?.close {
                connectAndJoinChannels(name, oauth)
                loadData(name, oauth, id)
            }

            twitchPreferences.setLoggedIn(true)
            showSnackbar(getString(R.string.snackbar_login, name))
        } else {
            showSnackbar(getString(R.string.snackbar_login_failed))
        }
    }

    private fun handleGalleryRequest(resultCode: Int, data: Intent?) {
        if (resultCode == Activity.RESULT_OK) {
            val uri = data?.data ?: return
            val mimeType = contentResolver.getType(uri)
            val mimeTypeMap = MimeTypeMap.getSingleton()
            val extension = mimeTypeMap.getExtensionFromMimeType(mimeType)
            if (extension == null) {
                showSnackbar(getString(R.string.snackbar_upload_failed))
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
                showSnackbar(getString(R.string.snackbar_upload_failed))
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
                showSnackbar(getString(R.string.snackbar_upload_failed))
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
            binding.input.dropDownWidth = (binding.viewPager.measuredWidth * 0.6).roundToInt()
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
        hideKeyboard(binding.input)
        binding.input.clearFocus()
        val isDarkMode = preferences.getBoolean(getString(R.string.preference_dark_theme_key), true)
        var lightModeFlags = 0
        if (!isDarkMode) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                lightModeFlags = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                lightModeFlags = lightModeFlags or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
            }
        }

        if (enabled) {
            window.decorView.systemUiVisibility = View.VISIBLE or lightModeFlags
            supportActionBar?.show()
            binding.showActionbarFab.visibility = View.GONE
            binding.tabs.visibility = View.VISIBLE
        } else {
            var isMultiWindow = false
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                isMultiWindow = isInMultiWindowMode
            }

            if (!isMultiWindow) {
                window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        or View.SYSTEM_UI_FLAG_FULLSCREEN
                        or lightModeFlags)
            }
            supportActionBar?.hide()
            binding.tabs.visibility = View.GONE
            binding.showActionbarFab.visibility = View.VISIBLE
        }
        window.decorView.requestApplyInsets()
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

    private fun connectAndJoinChannels(name: String, oauth: String) {
        if (twitchService?.startedConnection == false) {
            if (channels.isEmpty()) {
                twitchService?.connect(name, oauth)
            } else {
                channels.forEachIndexed { i, channel ->
                    if (i == 0) twitchService?.connect(name, oauth)
                    twitchService?.joinChannel(channel)
                }
            }
        }
    }

    private fun loadData(
        name: String,
        oAuth: String = twitchPreferences.getOAuthKey()?.substringAfter("oauth:") ?: "",
        id: Int = twitchPreferences.getUserId()
    ) {
        if (channels.isEmpty()) {
            viewModel.loadData(listOf(""), oAuth, id, true, loadTwitchData = true, name = name)
        } else {
            viewModel.loadData(channels, oAuth, id, true, loadTwitchData = true, name = name)
        }
    }

    private fun initPreferences() {
        val roomStateKey = getString(R.string.preference_roomstate_key)
        val streamInfoKey = getString(R.string.preference_streaminfo_key)
        val inputKey = getString(R.string.preference_show_input_key)
        val darkThemeKey = getString(R.string.preference_dark_theme_key)
        val customMentionsKey = getString(R.string.preference_custom_mentions_key)
        val blacklistKey = getString(R.string.preference_blacklist_key)
        twitchPreferences = DankChatPreferenceStore(this)
        preferenceListener = SharedPreferences.OnSharedPreferenceChangeListener { p, key ->
            when (key) {
                roomStateKey      -> viewModel.setRoomStateEnabled(p.getBoolean(key, true))
                streamInfoKey     -> {
                    fetchStreamInformation()
                    viewModel.setStreamInfoEnabled(p.getBoolean(key, true))
                }
                inputKey          -> viewModel.inputEnabled.value = p.getBoolean(key, true)
                darkThemeKey      -> delegate.localNightMode = if (p.getBoolean(key, true)) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
                customMentionsKey -> viewModel.setMentionEntries(p.getStringSet(key, emptySet()))
                blacklistKey      -> viewModel.setBlacklistEntries(p.getStringSet(key, emptySet()))
            }
        }
        preferences = PreferenceManager.getDefaultSharedPreferences(this).apply {
            registerOnSharedPreferenceChangeListener(preferenceListener)
            viewModel.apply {
                setRoomStateEnabled(getBoolean(roomStateKey, true))
                setStreamInfoEnabled(getBoolean(streamInfoKey, true))
                inputEnabled.value = getBoolean(inputKey, true)
                delegate.localNightMode = if (getBoolean(darkThemeKey, true)) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
                setMentionEntries(getStringSet(customMentionsKey, emptySet()))
                setBlacklistEntries(getStringSet(blacklistKey, emptySet()))
            }
        }
    }

    private fun updateViewPagerVisibility() {
        viewModel.shouldShowViewPager.value = channels.size > 0
    }

    private fun showSnackbar(message: String) {
        binding.inputLayout.post {
            Snackbar.make(binding.root, message, Snackbar.LENGTH_SHORT).apply {
                if (binding.inputLayout.isVisible) {
                    anchorView = binding.inputLayout
                }
            }.show()
        }
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
        getString(R.string.advanced_login_hint),
        false
    ).show(supportFragmentManager, DIALOG_TAG)

    private fun addChannel() = EditTextDialogFragment.create(
        R.string.dialog_title,
        R.string.dialog_negative_button,
        R.string.dialog_positive_button,
        textHint = getString(R.string.add_channel_hint),
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
                    bottomSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN
                }
            }
        })
    }

    private fun TextInputLayout.setup() {
        setEndIconOnClickListener { sendMessage() }
        setStartIconOnClickListener {
            if (bottomSheetBehavior.state == BottomSheetBehavior.STATE_EXPANDED
                || bottomSheetBehavior.state == BottomSheetBehavior.STATE_COLLAPSED
                || emoteMenuAdapter.currentList.isEmpty()
            ) {
                bottomSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN
                return@setStartIconOnClickListener
            }

            val isLandscape =
                resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
            if (isLandscape) {
                hideKeyboard(this)
            }

            val heightScaleFactor = 0.5
            binding.apply {
                bottomSheetViewPager.adapter = emoteMenuAdapter
                bottomSheetViewPager.updateLayoutParams {
                    height = (resources.displayMetrics.heightPixels * heightScaleFactor).toInt()
                }
                TabLayoutMediator(
                    bottomSheetTabs,
                    bottomSheetViewPager
                ) { tab, pos ->
                    tab.text = when (EmoteMenuTab.values()[pos]) {
                        EmoteMenuTab.SUBS    -> getString(R.string.emote_menu_tab_subs)
                        EmoteMenuTab.CHANNEL -> getString(R.string.emote_menu_tab_channel)
                        EmoteMenuTab.GLOBAL  -> getString(R.string.emote_menu_tab_global)
                    }
                }.attach()
            }

            postDelayed(50) {
                bottomSheetBehavior.apply {
                    peekHeight =
                        (resources.displayMetrics.heightPixels * heightScaleFactor).toInt()
                    state = BottomSheetBehavior.STATE_EXPANDED
                    addBottomSheetCallback(object : BottomSheetBehavior.BottomSheetCallback() {
                        override fun onSlide(bottomSheet: View, slideOffset: Float) = Unit

                        override fun onStateChanged(bottomSheet: View, newState: Int) {
                            if (viewModel.appbarEnabled.value == true && isLandscape) {
                                when (newState) {
                                    BottomSheetBehavior.STATE_EXPANDED, BottomSheetBehavior.STATE_COLLAPSED -> {
                                        supportActionBar?.hide()
                                        binding.tabs.visibility = View.GONE
                                    }
                                    else                                                                    -> {
                                        supportActionBar?.show()
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

    private fun CustomMultiAutoCompleteTextView.setup() {
        //setDropDownBackgroundResource(R.color.colorPrimary)
        setTokenizer(SpaceTokenizer())
        setOnEditorActionListener { _, actionId, _ ->
            return@setOnEditorActionListener when (actionId) {
                EditorInfo.IME_ACTION_SEND -> sendMessage()
                else                       -> false
            }
        }
        setOnKeyListener { _, keyCode, _ ->
            when (keyCode) {
                KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                    if (!isPopupShowing) sendMessage() else false
                }
                else                                                  -> false
            }
        }

        var wasLandScapeNotFullscreen = false
        setOnFocusChangeListener { _, hasFocus ->
            val isDarkMode = preferences.getBoolean(getString(R.string.preference_dark_theme_key), true)
            var lightModeFlags = 0
            if (!isDarkMode) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    lightModeFlags = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                    lightModeFlags = lightModeFlags or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
                }
            }

            window.decorView.systemUiVisibility = when {
                !hasFocus && wasLandScapeNotFullscreen && resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE          -> {
                    wasLandScapeNotFullscreen = false
                    supportActionBar?.show()
                    binding.showActionbarFab.visibility = View.GONE
                    binding.tabs.visibility = View.VISIBLE
                    View.VISIBLE or lightModeFlags
                }
                !hasFocus && binding.showActionbarFab.isVisible                                                                               -> {
                    wasLandScapeNotFullscreen = false
                    var isMultiWindow = false
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        isMultiWindow = isInMultiWindowMode
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
                    supportActionBar?.hide()
                    binding.showActionbarFab.visibility = View.VISIBLE
                    binding.tabs.visibility = View.GONE
                    View.VISIBLE or lightModeFlags
                }
                else                                                                                                                          -> {
                    wasLandScapeNotFullscreen = false
                    View.VISIBLE or lightModeFlags
                }
            }
            window.decorView.requestApplyInsets()
        }
    }

    private inner class TwitchServiceConnection : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            val binder = service as TwitchService.LocalBinder
            twitchService = binder.service
            twitchService?.shouldNotifyOnMention = false
            isBound = true
            if (isLoggingIn) {
                return
            }

            if (twitchService?.startedConnection == true) {
                if (!isChangingConfigurations) {
                    twitchService?.reconnect(true)
                }

            } else {
                val oauth = twitchPreferences.getOAuthKey() ?: ""
                val name = twitchPreferences.getUserName() ?: ""
                connectAndJoinChannels(name, oauth)
            }
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
        const val SHUTDOWN_REQUEST_FILTER = "shutdown_request_filter"
    }
}
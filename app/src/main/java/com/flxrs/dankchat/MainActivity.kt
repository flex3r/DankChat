package com.flxrs.dankchat

import android.Manifest
import android.app.Activity
import android.content.*
import android.content.pm.PackageManager
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
import com.flxrs.dankchat.utils.MediaUtils
import com.flxrs.dankchat.utils.dialog.AddChannelDialogResultHandler
import com.flxrs.dankchat.utils.dialog.AdvancedLoginDialogResultHandler
import com.flxrs.dankchat.utils.dialog.EditTextDialogFragment
import com.flxrs.dankchat.utils.extensions.isServiceRunning
import com.flxrs.dankchat.utils.extensions.timer
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.tabs.TabLayoutMediator
import kotlinx.coroutines.Job
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
    private lateinit var adapter: ChatTabAdapter
    private lateinit var tabLayoutMediator: TabLayoutMediator
    private var currentImagePath = ""
    private var showProgressBar = false
    private var currentChannel = ""
    private var fetchJob: Job? = null

    private var twitchService: TwitchService? = null
    private var isBound = false

    private val twitchServiceConnection = TwitchServiceConnection()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        twitchPreferences = DankChatPreferenceStore(this)
        preferenceListener = SharedPreferences.OnSharedPreferenceChangeListener { p, key ->
            when (key) {
                getString(R.string.preference_roomstate_key) -> viewModel.setRoomStateEnabled(
                    p.getBoolean(
                        key,
                        true
                    )
                )
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


        adapter = ChatTabAdapter(supportFragmentManager, lifecycle)
        twitchPreferences.getChannelsAsString()?.let { channels.addAll(it.split(',')) }
            ?: twitchPreferences.getChannels()?.let {
                channels.addAll(it)
                twitchPreferences.setChannels(null)
            }
        channels.forEach { adapter.addFragment(it) }

        binding = DataBindingUtil.setContentView<MainActivityBinding>(this, R.layout.main_activity)
            .apply {
                viewPager.adapter = adapter
                viewPager.offscreenPageLimit = calculatePageLimit()
                viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
                    override fun onPageSelected(position: Int) {
                        if (position in 0 until adapter.fragmentList.size) {
                            currentChannel =
                                adapter.titleList[position].toLowerCase(Locale.getDefault())
                            viewModel.setActiveChannel(currentChannel)
                        }
                    }
                })
                tabLayoutMediator = TabLayoutMediator(tabs, viewPager) { tab, position ->
                    tab.text = adapter.titleList[position]
                }
                tabLayoutMediator.attach()

                input.apply {
                    setDropDownBackgroundResource(R.color.colorPrimary)
                    setTokenizer(SpaceTokenizer())
                    setOnEditorActionListener { _, actionId, _ ->
                        return@setOnEditorActionListener when (actionId) {
                            EditorInfo.IME_ACTION_SEND -> sendMessage()
                            else -> false
                        }
                    }
                    setOnKeyListener { _, keyCode, _ ->
                        return@setOnKeyListener when (keyCode) {
                            KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> if (!isPopupShowing) sendMessage() else false
                            else -> false
                        }
                    }
                }


                inputLayout.setEndIconOnClickListener { sendMessage() }

                showActionbarFab.setOnClickListener { viewModel.appbarEnabled.value = true }
            }

        viewModel.apply {
            imageUploadedEvent.observe(this@MainActivity) { (urlOrError, file) ->
                val message: String = if (!urlOrError.startsWith("Error")) {
                    val clipboard =
                        getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("nuuls image url", urlOrError))
                    "Copied: $urlOrError"
                } else urlOrError

                showSnackbar(message)
                showProgressBar = false
                invalidateOptionsMenu()
                file.delete()
            }
            appbarEnabled.observe(this@MainActivity) { changeActionBarVisibility(it) }
            canType.observe(this@MainActivity) {
                binding.input.isEnabled = it == "Start chatting"
                binding.inputLayout.hint = it
            }
            emoteCodes.observe(this@MainActivity) {
                val adapter = EmoteSuggestionsArrayAdapter(this@MainActivity, it) { count ->
                    binding.input.dropDownHeight = if (count > 2) {
                        (binding.viewPager.measuredHeight / 1.3).roundToInt()
                    } else {
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    }
                    binding.input.dropDownWidth = binding.viewPager.measuredWidth / 2
                }

                binding.input.setAdapter(adapter)
            }
            bottomTextEnabled.observe(this@MainActivity) {
                binding.inputLayout.isHelperTextEnabled = it
            }
            bottomText.observe(this@MainActivity) {
                binding.inputLayout.run {
                    val helperId = com.google.android.material.R.id.textinput_helper_text
                    val previous = helperText
                    helperText = it

                    if (previous?.isBlank() == false) findViewById<TextView>(helperId)?.run {
                        updatePadding(top = 12) //TODO check if still needed
                    }

                }
            }

        }

        if (savedInstanceState == null) {
            loadData()
        }

        setSupportActionBar(binding.toolbar)
        updateViewPagerVisibility()
        fetchStreamInformation()
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
        menu?.run {
            val isLoggedIn = twitchPreferences.isLoggedin()
            findItem(R.id.menu_login)?.isVisible = !isLoggedIn
            findItem(R.id.menu_remove)?.isVisible = channels.isNotEmpty()

            findItem(R.id.progress)?.run {
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
            R.id.menu_login_default -> Intent(this, LoginActivity::class.java).run {
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
            R.id.menu_settings -> Intent(this, SettingsActivity::class.java).run {
                startActivityForResult(this, SETTINGS_REQUEST)
            }
            else -> return false
        }
        return true
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        when (requestCode) {
            LOGIN_REQUEST -> {
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
            GALLERY_REQUEST -> {
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
                        contentResolver.openInputStream(uri)?.run {
                            copy.outputStream().use { copyTo(it) }
                        }
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
            CAPTURE_REQUEST -> {
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
            SETTINGS_REQUEST -> {
                if (resultCode == Activity.RESULT_OK
                    && data?.getBooleanExtra(LOGOUT_REQUEST_KEY, false) == true
                ) {
                    showLogoutConfirmationDialog()
                }
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

            adapter.addFragment(lowerCaseChannel)
            binding.viewPager.offscreenPageLimit = calculatePageLimit()
            binding.viewPager.setCurrentItem(channels.size - 1, false)

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
        //TODO viewmodel
        val key = getString(R.string.preference_streaminfo_key)
        fetchJob?.cancel()

        fetchJob = lifecycleScope.launchWhenCreated {
            if (::preferences.isInitialized && preferences.getBoolean(key, true)) {
                timer(STREAM_REFRESH_RATE) {
                    val streamData = mutableMapOf<String, String>()
                    channels.forEach { channel ->
                        TwitchApi.getStream(channel)?.let {
                            val text = resources.getQuantityString(
                                R.plurals.viewers,
                                it.viewers,
                                it.viewers
                            )
                            streamData[channel] = text
                        }
                    }
                    viewModel.setStreamData(streamData)
                }
            }
        }
    }

    private fun sendMessage(): Boolean {
        val msg = binding.input.text.toString()
        twitchService?.sendMessage(currentChannel, msg)
        binding.input.setText("")
        return true
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
            supportActionBar?.show()
            binding.showActionbarFab.visibility = View.GONE
            binding.tabs.visibility = View.VISIBLE
        } else {
            supportActionBar?.hide()
            binding.tabs.visibility = View.GONE
            binding.showActionbarFab.visibility = View.VISIBLE
        }
    }

    private fun clear() {
        val position = binding.tabs.selectedTabPosition
        if (position in 0 until adapter.titleList.size)
            viewModel.clear(adapter.titleList[position])
    }

    private fun reloadEmotes() {
        val position = binding.tabs.selectedTabPosition
        if (position in 0 until adapter.titleList.size) {
            val oauth = twitchPreferences.getOAuthKey() ?: ""
            val userId = twitchPreferences.getUserId()
            viewModel.reloadEmotes(adapter.titleList[position], oauth, userId)
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
        oauth: String = twitchPreferences.getOAuthKey() ?: "",
        id: Int = twitchPreferences.getUserId()
    ) {
        if (channels.isEmpty()) {
            viewModel.loadData("", oauth, id, true, reAuth = true)
        } else channels.forEachIndexed { i, channel ->
            viewModel.loadData(channel, oauth, id, true, reAuth = i == 0)
        }
    }

    private fun updateViewPagerVisibility() = with(binding) {
        //TODO databinding
        if (channels.size > 0) {
            viewPager.visibility = View.VISIBLE
            tabs.visibility = View.VISIBLE
            inputLayout.visibility = View.VISIBLE
            input.visibility = View.VISIBLE
            divider.visibility = View.VISIBLE
            addChannelsText.visibility = View.GONE
        } else {
            viewPager.visibility = View.GONE
            tabs.visibility = View.GONE
            inputLayout.visibility = View.GONE
            input.visibility = View.GONE
            divider.visibility = View.GONE
            addChannelsText.visibility = View.VISIBLE
        }
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
            binding.viewPager.setCurrentItem(0, false)
        } else twitchPreferences.setChannelsString(null)

        binding.viewPager.offscreenPageLimit = calculatePageLimit()
        adapter.removeFragment(index)

        invalidateOptionsMenu()
        updateViewPagerVisibility()
    }

    private fun calculatePageLimit() =
        if (channels.size > 1) channels.size - 1 else ViewPager2.OFFSCREEN_PAGE_LIMIT_DEFAULT

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
        private const val STREAM_REFRESH_RATE = 30_000L

        const val LOGOUT_REQUEST_KEY = "logout_key"
    }
}
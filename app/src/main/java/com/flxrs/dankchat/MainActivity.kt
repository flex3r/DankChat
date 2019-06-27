package com.flxrs.dankchat

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.provider.MediaStore
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.WindowManager
import android.widget.ProgressBar
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.view.isVisible
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.Observer
import androidx.viewpager2.widget.ViewPager2
import com.flxrs.dankchat.chat.ChatTabAdapter
import com.flxrs.dankchat.databinding.MainActivityBinding
import com.flxrs.dankchat.preferences.TwitchAuthStore
import com.flxrs.dankchat.utils.AddChannelDialogFragment
import com.flxrs.dankchat.utils.MediaUtils
import com.flxrs.dankchat.utils.reduceDragSensitivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import org.koin.androidx.viewmodel.ext.android.viewModel
import java.io.File
import java.io.IOException


class MainActivity : AppCompatActivity() {
	private val viewModel: DankChatViewModel by viewModel()
	private val channels = mutableListOf<String>()
	private lateinit var authStore: TwitchAuthStore
	private lateinit var binding: MainActivityBinding
	private lateinit var adapter: ChatTabAdapter
	private lateinit var tabLayoutMediator: TabLayoutMediator
	private var currentImagePath = ""
	private var showProgressBar = false

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

		authStore = TwitchAuthStore(this)
		val oauth = authStore.getOAuthKey() ?: ""
		val name = authStore.getUserName() ?: ""
		val id = authStore.getUserId()

		adapter = ChatTabAdapter(supportFragmentManager, lifecycle)
		authStore.getChannels()?.run { channels.addAll(this) }
		channels.forEach { adapter.addFragment(it) }

		binding = DataBindingUtil.setContentView<MainActivityBinding>(this, R.layout.main_activity).apply {
			viewPager.adapter = adapter
			viewPager.reduceDragSensitivity()
			viewPager.offscreenPageLimit = if (channels.size > 1) channels.size - 1 else ViewPager2.OFFSCREEN_PAGE_LIMIT_DEFAULT
			tabLayoutMediator = TabLayoutMediator(tabs, viewPager) { tab, position -> tab.text = adapter.titleList[position] }
			tabLayoutMediator.attach()
			tabs.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
				override fun onTabReselected(tab: TabLayout.Tab?) = Unit

				override fun onTabSelected(tab: TabLayout.Tab?) = Unit

				override fun onTabUnselected(tab: TabLayout.Tab?) {
					val position = tab?.position ?: -1
					if (position in 0 until adapter.fragmentList.size) {
						adapter.fragmentList[position].clearInputFocus()
					}
				}
			})
			showActionbarFab.setOnClickListener { showActionBar() }
		}

		viewModel.imageUploadedEvent.observe(this, Observer {
			val message = if (!it.startsWith("Error")) {
				val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
				clipboard.primaryClip = android.content.ClipData.newPlainText("nuuls image url", it)
				"Copied: $it"
			} else it
			showSnackbar(message)
			showProgressBar = false
			invalidateOptionsMenu()
		})

		setSupportActionBar(binding.toolbar)
		updateViewPagerVisibility()

		if (savedInstanceState == null) {
			if (name.isNotBlank() && oauth.isNotBlank()) showSnackbar("Logged in as $name")
			connectAndJoinChannels(name, oauth, id, true)
		}
	}

	override fun onPause() {
		val position = binding.tabs.selectedTabPosition
		if (position in 0 until adapter.fragmentList.size) {
			adapter.fragmentList[position].clearInputFocus()
		}
		super.onPause()
	}

	override fun onResume() {
		super.onResume()
		reconnect(true)
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
			findItem(R.id.menu_login)?.run {
				if (authStore.isLoggedin()) setTitle(R.string.logout) else setTitle(R.string.login)
			}
			findItem(R.id.menu_remove)?.run {
				isVisible = channels.isNotEmpty()
			}
			findItem(R.id.progress)?.run {
				isVisible = showProgressBar
				actionView = ProgressBar(this@MainActivity).apply {
					indeterminateTintList = ContextCompat.getColorStateList(this@MainActivity, android.R.color.white)
					isVisible = showProgressBar
				}
			}
		}
		return true
	}

	override fun onOptionsItemSelected(item: MenuItem): Boolean {
		when (item.itemId) {
			R.id.menu_reconnect     -> reconnect(false)
			R.id.menu_login         -> updateLoginState()
			R.id.menu_add           -> addChannel()
			R.id.menu_remove        -> removeChannel()
			R.id.menu_clear         -> clear()
			R.id.menu_reload_emotes -> reloadEmotes()
			R.id.menu_choose_image  -> checkPermissionForGallery()
			R.id.menu_capture_image -> startCameraCapture()
			R.id.menu_hide          -> hideActionBar()
			else                    -> return false
		}
		return true
	}

	override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
		when (requestCode) {
			LOGIN_REQUEST   -> {
				val oauth = authStore.getOAuthKey()
				val name = authStore.getUserName()
				val id = authStore.getUserId()

				if (resultCode == Activity.RESULT_OK && !oauth.isNullOrBlank() && !name.isNullOrBlank() && id != 0) {
					viewModel.close { connectAndJoinChannels(name, oauth, id) }

					authStore.setLoggedIn(true)
					showSnackbar("Logged in as $name")
				} else {
					showSnackbar("Failed to login")
				}
			}
			GALLERY_REQUEST -> {
				if (resultCode == Activity.RESULT_OK) {
					val uri = data?.data
					val path = MediaUtils.getImagePathFromUri(this, uri) ?: return
					viewModel.uploadImage(File(path))
					showProgressBar = true
					invalidateOptionsMenu()
				}
			}
			CAPTURE_REQUEST -> {
				if (resultCode == Activity.RESULT_OK) {
					try {
						MediaUtils.removeExifAttributes(currentImagePath)
						viewModel.uploadImage(File(currentImagePath))
						showProgressBar = true
						invalidateOptionsMenu()
					} catch (e: IOException) {
						showSnackbar("Error during upload")
					}
				}
			}
		}
		super.onActivityResult(requestCode, resultCode, data)
	}

	override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
		if (requestCode == GALLERY_REQUEST && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
			startGalleryPicker()
		}
	}

	private fun checkPermissionForGallery() {
		if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
			ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), GALLERY_REQUEST)
		} else {
			startGalleryPicker()
		}
	}

	private fun startCameraCapture() {
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

	private fun startGalleryPicker() {
		Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI).also { galleryIntent ->
			galleryIntent.resolveActivity(packageManager)?.also {
				startActivityForResult(galleryIntent, GALLERY_REQUEST)
			}
		}
	}

	private fun hideActionBar() {
		supportActionBar?.hide()
		binding.tabs.visibility = View.GONE
		binding.showActionbarFab.visibility = View.VISIBLE
	}

	private fun showActionBar() {
		supportActionBar?.show()
		binding.showActionbarFab.visibility = View.GONE
		binding.tabs.visibility = View.VISIBLE
	}

	private fun clear() {
		val position = binding.tabs.selectedTabPosition
		if (position in 0 until adapter.fragmentList.size) {
			viewModel.clear(adapter.titleList[position])
		}
	}

	private fun reloadEmotes() {
		val position = binding.tabs.selectedTabPosition
		if (position in 0 until adapter.fragmentList.size) {
			val oauth = authStore.getOAuthKey() ?: ""
			val userId = authStore.getUserId()
			viewModel.reloadEmotes(adapter.titleList[position], oauth, userId)
		}
	}

	private fun reconnect(onlyIfNecessary: Boolean = false) {
		viewModel.reconnect(onlyIfNecessary)
	}

	private fun connectAndJoinChannels(name: String, oauth: String, id: Int, load3rdPartyEmotesAndBadges: Boolean = false) {
		if (channels.isEmpty()) {
			viewModel.connectOrJoinChannel("", name, oauth, id, false, doReauth = true)
		} else channels.forEachIndexed { i, channel ->
			viewModel.connectOrJoinChannel(channel, name, oauth, id, load3rdPartyEmotesAndBadges, i == 0)
		}
	}

	private fun updateViewPagerVisibility() = with(binding) {
		if (channels.size > 0) {
			viewPager.visibility = View.VISIBLE
			tabs.visibility = View.VISIBLE
			addChannelsText.visibility = View.GONE
		} else {
			viewPager.visibility = View.GONE
			tabs.visibility = View.GONE
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
				viewModel.close { connectAndJoinChannels("", "", 0) }
				authStore.setUserName("")
				authStore.setOAuthKey("")
				authStore.setUserId(0)
				authStore.setLoggedIn(false)
				dialog.dismiss()
			}
			.setNegativeButton(getString(R.string.confirm_logout_negative_button)) { dialog, _ -> dialog.dismiss() }
			.create().show()

	private fun updateLoginState() {
		if (authStore.isLoggedin()) {
			showLogoutConfirmationDialog()
		} else {
			Intent(this, LoginActivity::class.java).run { startActivityForResult(this, LOGIN_REQUEST) }
		}
	}

	private fun addChannel() {
		AddChannelDialogFragment {
			if (!channels.contains(it)) {
				val oauth = authStore.getOAuthKey() ?: ""
				val name = authStore.getUserName() ?: ""
				val id = authStore.getUserId()
				viewModel.connectOrJoinChannel(it, name, oauth, id, true)
				channels.add(it)
				authStore.setChannels(channels.toMutableSet())

				adapter.addFragment(it)
				binding.viewPager.setCurrentItem(channels.size - 1, true)
				binding.viewPager.offscreenPageLimit = if (channels.size > 1) channels.size - 1 else ViewPager2.OFFSCREEN_PAGE_LIMIT_DEFAULT

				invalidateOptionsMenu()
				updateViewPagerVisibility()
			}
		}.show(supportFragmentManager, DIALOG_TAG)
	}

	private fun removeChannel() {
		val index = binding.viewPager.currentItem
		val channel = channels[index]
		channels.remove(channel)
		authStore.setChannels(channels.toMutableSet())
		viewModel.partChannel(channel)
		if (channels.size > 0) {
			binding.viewPager.setCurrentItem(0, true)
		}

		binding.viewPager.offscreenPageLimit = if (channels.size > 1) channels.size - 1 else ViewPager2.OFFSCREEN_PAGE_LIMIT_DEFAULT
		adapter.removeFragment(index)

		invalidateOptionsMenu()
		updateViewPagerVisibility()
	}

	companion object {
		private val TAG = MainActivity::class.java.simpleName
		private const val DIALOG_TAG = "add_channel_dialog"
		private const val LOGIN_REQUEST = 42
		private const val GALLERY_REQUEST = 69
		private const val CAPTURE_REQUEST = 420
	}
}

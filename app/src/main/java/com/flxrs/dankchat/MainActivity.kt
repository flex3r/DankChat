package com.flxrs.dankchat

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.databinding.DataBindingUtil
import androidx.viewpager2.widget.ViewPager2
import com.flxrs.dankchat.chat.ChatFragment
import com.flxrs.dankchat.chat.ChatTabAdapter
import com.flxrs.dankchat.databinding.MainActivityBinding
import com.flxrs.dankchat.preferences.TwitchAuthStore
import com.flxrs.dankchat.utils.AddChannelDialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import com.microsoft.appcenter.AppCenter
import com.microsoft.appcenter.crashes.Crashes
import com.microsoft.appcenter.distribute.Distribute
import com.microsoft.appcenter.distribute.ReleaseDetails
import com.microsoft.appcenter.distribute.UpdateAction
import org.koin.androidx.viewmodel.ext.android.viewModel


class MainActivity : AppCompatActivity() {
	private val viewModel: DankChatViewModel by viewModel()
	private val channels = mutableListOf<String>()
	private lateinit var authStore: TwitchAuthStore
	private lateinit var binding: MainActivityBinding
	private lateinit var adapter: ChatTabAdapter
	private lateinit var tabLayoutMediator: TabLayoutMediator

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)

		Distribute.setListener(::showUpdateDialog)
		AppCenter.start(application, "067a0d4f-9e69-4ffd-9b46-0c5ccb2843a8", Distribute::class.java, Crashes::class.java)

		authStore = TwitchAuthStore(this)
		val oauth = authStore.getOAuthKey() ?: ""
		val name = authStore.getUserName() ?: ""
		val id = authStore.getUserId()

		adapter = ChatTabAdapter(supportFragmentManager, lifecycle)
		authStore.getChannels()?.run { channels.addAll(this) }
		channels.forEach { adapter.addFragment(ChatFragment.newInstance(it), it) }

		binding = DataBindingUtil.setContentView<MainActivityBinding>(this, R.layout.main_activity).apply {
			viewPager.adapter = adapter
			viewPager.offscreenPageLimit = if (channels.size > 0) channels.size - 1 else ViewPager2.OFFSCREEN_PAGE_LIMIT_DEFAULT
			tabLayoutMediator = TabLayoutMediator(tabs, viewPager) { tab, position -> tab.text = adapter.titleList[position] }
			tabLayoutMediator.attach()
			tabs.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
				override fun onTabReselected(tab: TabLayout.Tab?) = Unit

				override fun onTabSelected(tab: TabLayout.Tab?) = Unit

				override fun onTabUnselected(tab: TabLayout.Tab?) {
					tab?.position?.let {
						(adapter.getItem(it) as? ChatFragment)?.clearInputFocus()
					}
				}
			})
		}

		setSupportActionBar(binding.toolbar)
		updateViewPagerVisibility()

		if (savedInstanceState == null) {
			if (name.isNotBlank() && oauth.isNotBlank()) showSnackbar("Logged in as $name")
			connectAndJoinChannels(name, oauth, id, true)
		}
	}

	override fun onCreateOptionsMenu(menu: Menu): Boolean {
		menuInflater.inflate(R.menu.menu, menu)
		return true
	}

	override fun onPrepareOptionsMenu(menu: Menu?): Boolean {
		menu?.findItem(R.id.menu_login)?.run {
			if (authStore.isLoggedin()) {
				setTitle(R.string.logout)
				setIcon(R.drawable.ic_exit_to_app_24dp)
				setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM)
			} else {
				setTitle(R.string.login)
				setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER)
			}
		}
		return true
	}

	override fun onOptionsItemSelected(item: MenuItem): Boolean {
		when (item.itemId) {
			R.id.menu_login  -> updateLoginState()
			R.id.menu_add    -> addChannel()
			R.id.menu_remove -> removeChannel()
			else             -> return false
		}
		return true
	}

	override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
		if (requestCode == LOGIN_REQUEST) {
			val oauth = authStore.getOAuthKey()
			val name = authStore.getUserName()
			val id = authStore.getUserId()

			if (resultCode == Activity.RESULT_OK && !oauth.isNullOrBlank() && !name.isNullOrBlank()) {
				viewModel.close()
				connectAndJoinChannels(name, oauth, id)

				authStore.setLoggedIn(true)
				showSnackbar("Logged in as $name")
				invalidateOptionsMenu()
			} else {
				showSnackbar("Failed to login")
			}
		}
		super.onActivityResult(requestCode, resultCode, data)
	}

	private fun connectAndJoinChannels(name: String, oauth: String, id: Int, load3rdPartyEmotesAndBadges: Boolean = false) {
		if (channels.isEmpty()) {
			viewModel.connectOrJoinChannel("", name, oauth, id, false, forceReconnect = true)
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

	private fun showSnackbar(message: String) = Snackbar.make(binding.root, message, Snackbar.LENGTH_SHORT).show()

	private fun showLogoutConfirmationDialog() = MaterialAlertDialogBuilder(this)
			.setTitle(getString(R.string.confirm_logout_title))
			.setMessage(getString(R.string.confirm_logout_message))
			.setPositiveButton(getString(R.string.confirm_logout_positive_button)) { dialog, _ ->
				viewModel.close()
				connectAndJoinChannels("", "", 0)

				authStore.setUserName("")
				authStore.setOAuthKey("")
				authStore.setUserId(0)
				authStore.setLoggedIn(false)
				invalidateOptionsMenu()
				dialog.dismiss()
			}
			.setNegativeButton(getString(R.string.confirm_logout_negative_button)) { dialog, _ -> dialog.dismiss() }
			.create().show()

	private fun showUpdateDialog(activity: Activity?, releaseDetails: ReleaseDetails?): Boolean {
		val releaseNotes = releaseDetails?.releaseNotes
		MaterialAlertDialogBuilder(this@MainActivity)
				.setCancelable(false)
				.setTitle(getString(R.string.new_version_title))
				.setMessage(releaseNotes)
				.setNegativeButton(getString(R.string.new_version_negative_button)) { _, _ -> Distribute.notifyUpdateAction(UpdateAction.POSTPONE) }
				.setPositiveButton(getString(R.string.new_version_positive_button)) { _, _ -> Distribute.notifyUpdateAction(UpdateAction.UPDATE) }
				.create().show()
		return true
	}

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

				adapter.addFragment(ChatFragment.newInstance(it), it)
				binding.viewPager.setCurrentItem(channels.size - 1, true)
				binding.viewPager.offscreenPageLimit = channels.size - 1

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
			binding.viewPager.offscreenPageLimit = channels.size - 1
		} else {
			binding.viewPager.offscreenPageLimit = ViewPager2.OFFSCREEN_PAGE_LIMIT_DEFAULT
		}

		adapter.removeFragment(index)
		updateViewPagerVisibility()
	}

	companion object {
		private val TAG = MainActivity::class.java.simpleName
		private const val DIALOG_TAG = "add_channel_dialog"
		private const val LOGIN_REQUEST = 42
	}
}

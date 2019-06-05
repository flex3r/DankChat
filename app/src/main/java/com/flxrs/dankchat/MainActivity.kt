package com.flxrs.dankchat

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
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
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.tabs.TabLayoutMediator
import com.microsoft.appcenter.AppCenter
import com.microsoft.appcenter.crashes.Crashes
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

		AppCenter.start(application, "2be94b1a-8bd0-447d-8e06-db527eb6e944",
				Crashes::class.java)

		authStore = TwitchAuthStore(this)
		val oauth = authStore.getOAuthKey() ?: ""
		val name = authStore.getUserName() ?: ""

		adapter = ChatTabAdapter(supportFragmentManager, lifecycle)
		authStore.getChannels()?.run { channels.addAll(this) }
		channels.forEach { adapter.addFragment(ChatFragment.newInstance(it), it) }

		binding = DataBindingUtil.setContentView<MainActivityBinding>(this, R.layout.main_activity).apply {
			viewPager.adapter = adapter
			viewPager.offscreenPageLimit = if (channels.size > 0) channels.size - 1 else ViewPager2.OFFSCREEN_PAGE_LIMIT_DEFAULT
			tabLayoutMediator = TabLayoutMediator(tabs, viewPager) { tab, position -> tab.text = adapter.titleList[position] }
			tabLayoutMediator.attach()
		}
		setSupportActionBar(binding.toolbar)
		updateViewPagerVisibility()

		if (savedInstanceState == null) {
			if (name.isNotBlank() && oauth.isNotBlank()) showSnackbar("Logged in as $name")
			connectAndJoinChannels(name, oauth, true)
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

			if (resultCode == Activity.RESULT_OK && !oauth.isNullOrBlank() && !name.isNullOrBlank()) {
				viewModel.close()
				connectAndJoinChannels(name, oauth)

				authStore.setLoggedIn(true)
				showSnackbar("Logged in as $name")
				invalidateOptionsMenu()
			} else {
				showSnackbar("Failed to login")
			}
		}
		super.onActivityResult(requestCode, resultCode, data)
	}

	private fun connectAndJoinChannels(name: String, oauth: String, loadEmotesAndBadges: Boolean = false) {
		if (channels.isEmpty()) {
			viewModel.connectOrJoinChannel("", name, oauth, false)
		} else channels.forEachIndexed { i, channel ->
			Log.d(TAG, "$i")
			viewModel.connectOrJoinChannel(channel, name, oauth, loadEmotesAndBadges, i == 0)
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

	private fun updateLoginState() {
		if (authStore.isLoggedin()) {
			viewModel.close()
			connectAndJoinChannels("", "")

			authStore.setUserName("")
			authStore.setOAuthKey("")
			authStore.setLoggedIn(false)
			invalidateOptionsMenu()
		} else {
			Intent(this, LoginActivity::class.java).run { startActivityForResult(this, LOGIN_REQUEST) }
		}
	}

	private fun addChannel() {
		AddChannelDialogFragment {
			if (!channels.contains(it)) {
				val oauth = authStore.getOAuthKey() ?: ""
				val name = authStore.getUserName() ?: ""
				viewModel.connectOrJoinChannel(it, name, oauth, true)
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

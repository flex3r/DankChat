package com.flxrs.dankchat

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.databinding.DataBindingUtil
import com.flxrs.dankchat.chat.ChatFragment
import com.flxrs.dankchat.chat.ViewPagerAdapter
import com.flxrs.dankchat.databinding.MainActivityBinding
import com.flxrs.dankchat.preferences.TwitchAuthStore
import com.flxrs.dankchat.utils.AddChannelDialogFragment
import com.google.android.material.snackbar.Snackbar
import org.koin.androidx.viewmodel.ext.android.viewModel


class MainActivity : AppCompatActivity() {
	private val viewModel: DankChatViewModel by viewModel()
	private val channels = mutableSetOf<String>()
	private lateinit var authStore: TwitchAuthStore
	private lateinit var binding: MainActivityBinding
	private lateinit var adapter: ViewPagerAdapter

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		//delegate.localNightMode = AppCompatDelegate.MODE_NIGHT_YES
		authStore = TwitchAuthStore(this)
		val oauth = authStore.getOAuthKey() ?: ""
		val name = authStore.getUserName() ?: ""
		binding = DataBindingUtil.setContentView(this, R.layout.main_activity)

		adapter = ViewPagerAdapter(supportFragmentManager)
		channels.add("pajlada")
		channels.add("flex3rs")
		channels.add("forsen")
		channels.forEach {
			adapter.addFragment(ChatFragment.newInstance(it), it)

		}
		binding.apply {
			viewPager.adapter = adapter
			viewPager.offscreenPageLimit = channels.size - 1
			tabs.setupWithViewPager(viewPager)
		}

		if (savedInstanceState == null) channels.forEach {
			viewModel.connectOrJoinChannel(it, oauth, name, true)
		}
	}

	override fun onCreateOptionsMenu(menu: Menu): Boolean {
		menuInflater.inflate(R.menu.menu, menu)
		return true
	}

	override fun onPrepareOptionsMenu(menu: Menu?): Boolean {
		val item = menu?.findItem(R.id.menu_login)
		if (authStore.isLoggedin()) {
			item?.setTitle(R.string.logout)
			item?.setIcon(R.drawable.ic_exit_to_app_24dp)
			item?.setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM)
		} else {
			item?.setTitle(R.string.login)
			item?.setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER)
		}
		return true
	}

	override fun onOptionsItemSelected(item: MenuItem): Boolean {
		when (item.itemId) {
			R.id.menu_login -> handleLoginOrLogout()
			R.id.menu_add   -> addChannel()
			else            -> return false
		}
		return true
	}

	override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
		if (requestCode == LOGIN_REQUEST) {
			val oauth = authStore.getOAuthKey()
			val name = authStore.getUserName()
			if (resultCode == Activity.RESULT_OK && oauth != null && name != null) {
				viewModel.close()
				channels.forEachIndexed { index, channel ->
					viewModel.connectOrJoinChannel(channel, oauth, name, forceReconnect = index == 0)
				}
				authStore.setLoggedIn(true)
			} else {
				Snackbar.make(binding.root, "Failed to login", Snackbar.LENGTH_SHORT).show()
			}
		}
		super.onActivityResult(requestCode, resultCode, data)
	}

	private fun handleLoginOrLogout() {
		if (authStore.isLoggedin()) {
			viewModel.close()
			channels.forEach {
				viewModel.connectOrJoinChannel(it, "", "")
			}
			authStore.setOAuthKey("")
			authStore.setLoggedIn(false)
		} else {
			val intent = Intent(this, LoginActivity::class.java)
			startActivityForResult(intent, LOGIN_REQUEST)

		}
	}

	private fun addChannel() {
		AddChannelDialogFragment {
			if (!channels.contains(it)) {
				val oauth = authStore.getOAuthKey() ?: ""
				val name = authStore.getUserName() ?: ""
				viewModel.connectOrJoinChannel(it, oauth, name, true)

				channels.add(it)
				adapter.addFragment(ChatFragment.newInstance(it), it)

				binding.viewPager.currentItem = channels.size - 1
				binding.viewPager.offscreenPageLimit = channels.size - 1
			}
		}.show(supportFragmentManager, DIALOG_TAG)
	}

	fun removeChannel(channel: String) {
		viewModel.partChannel(channel)

		val index = channels.indexOf(channel)
		channels.remove(channel)
		adapter.removeFragment(index)
		binding.viewPager.currentItem = 0
		binding.viewPager.offscreenPageLimit = channels.size - 1
	}

	companion object {
		private val TAG = MainActivity::class.java.simpleName
		private const val DIALOG_TAG = "add_channel_dialog"
		private const val LOGIN_REQUEST = 42
	}
}

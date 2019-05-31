package com.flxrs.dankchat

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.databinding.DataBindingUtil
import com.flxrs.dankchat.chat.ChatFragment
import com.flxrs.dankchat.chat.ViewPagerAdapter
import com.flxrs.dankchat.databinding.MainActivityBinding
import com.flxrs.dankchat.preferences.TwitchAuthStore
import com.google.android.material.snackbar.Snackbar
import org.koin.androidx.viewmodel.ext.android.viewModel


class MainActivity : AppCompatActivity() {
	val viewModel: DankChatViewModel by viewModel()
	private val channels = mutableSetOf<String>()
	private lateinit var authStore: TwitchAuthStore
	private lateinit var binding: MainActivityBinding

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		delegate.localNightMode = AppCompatDelegate.MODE_NIGHT_YES
		authStore = TwitchAuthStore(this)
		val oauth = authStore.getOAuthKey() ?: ""
		val name = authStore.getUserName() ?: ""
		binding = DataBindingUtil.setContentView(this, R.layout.main_activity)

		val adapter = ViewPagerAdapter(supportFragmentManager)
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
		} else {
			item?.setTitle(R.string.login)
		}
		return true
	}

	override fun onOptionsItemSelected(item: MenuItem): Boolean {
		when (item.itemId) {
			R.id.menu_login -> handleLoginOrLogout()
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

	companion object {
		private val TAG = MainActivity::class.java.simpleName
		private const val LOGIN_REQUEST = 42
	}
}

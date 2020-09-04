package com.flxrs.dankchat

import android.content.*
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.navigation.NavController
import androidx.navigation.findNavController
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager
import com.flxrs.dankchat.preferences.*
import com.flxrs.dankchat.service.NotificationService
import com.flxrs.dankchat.utils.dialog.AddChannelDialogResultHandler
import com.flxrs.dankchat.utils.dialog.MessageHistoryDisclaimerResultHandler
import com.flxrs.dankchat.utils.extensions.navigateSafe
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : AppCompatActivity(R.layout.main_activity), AddChannelDialogResultHandler, MessageHistoryDisclaimerResultHandler, PreferenceFragmentCompat.OnPreferenceStartFragmentCallback {
    private val channels = mutableListOf<String>()
    private val viewModel: DankChatViewModel by viewModels()
    private lateinit var twitchPreferences: DankChatPreferenceStore
    private lateinit var broadcastReceiver: BroadcastReceiver
    private var notificationService: NotificationService? = null
    private val pendingChannelsToClear = mutableListOf<String>()
    private val navController: NavController by lazy { findNavController(R.id.main_content) }

    private val twitchServiceConnection = TwitchServiceConnection()
    var isBound = false
    var channelToOpen = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        broadcastReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) = handleShutDown()
        }

        val filter = IntentFilter(SHUTDOWN_REQUEST_FILTER)
        LocalBroadcastManager.getInstance(this).registerReceiver(broadcastReceiver, filter)
        twitchPreferences = DankChatPreferenceStore(this)
        twitchPreferences.channelsString?.let { channels.addAll(it.split(',')) }
            ?: twitchPreferences.channels?.let {
                channels.addAll(it)
                twitchPreferences.channels = null
            }

        viewModel.activeChannel.observe(this) { notificationService?.activeTTSChannel = it }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (!isChangingConfigurations) {
            handleShutDown()
        }
    }

    override fun onStart() {
        super.onStart()
        if (!isBound) Intent(this, NotificationService::class.java).also {
            try {
                isBound = true
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
            if (!isChangingConfigurations) {
                notificationService?.shouldNotifyOnMention = true
            }

            isBound = false
            try {
                unbindService(twitchServiceConnection)
            } catch (t: Throwable) {
                Log.e(TAG, Log.getStackTraceString(t))
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        return navController.navigateUp() || super.onSupportNavigateUp()
    }

    override fun onAddChannelDialogResult(channel: String) {
        val fragment = supportFragmentManager.primaryNavigationFragment?.childFragmentManager?.fragments?.first()
        if (fragment is MainFragment) {
            fragment.addChannel(channel)
        }
        invalidateOptionsMenu()
    }

    override fun onDisclaimerResult(shouldLoadHistory: Boolean) {
        val fragment = supportFragmentManager.primaryNavigationFragment?.childFragmentManager?.fragments?.first()
        if (fragment is MainFragment) {
            fragment.onMessageHistoryDisclaimerResult(shouldLoadHistory)
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        val channelExtra = intent?.getStringExtra(OPEN_CHANNEL_KEY) ?: ""
        channelToOpen = channelExtra
    }

    override fun onPreferenceStartFragment(caller: PreferenceFragmentCompat, pref: Preference): Boolean {
        when (pref.fragment.substringAfterLast(".")) {
            AppearanceSettingsFragment::class.java.simpleName -> caller.navigateSafe(R.id.action_overviewSettingsFragment_to_appearanceSettingsFragment)
            NotificationsSettingsFragment::class.java.simpleName -> caller.navigateSafe(R.id.action_overviewSettingsFragment_to_notificationsSettingsFragment)
            ChatSettingsFragment::class.java.simpleName -> caller.navigateSafe(R.id.action_overviewSettingsFragment_to_chatSettingsFragment)
            DeveloperSettingsFragment::class.java.simpleName -> caller.navigateSafe(R.id.action_overviewSettingsFragment_to_developerSettingsFragment)
            else -> return false
        }
        return true
    }

    fun clearNotificationsOfChannel(channel: String) = when {
        isBound && notificationService != null -> notificationService?.clearNotificationsOfChannel(channel)
        else -> pendingChannelsToClear += channel
    }

    fun setTTSEnabled(enabled: Boolean) = notificationService?.setTTSEnabled(enabled)

    private fun handleShutDown() {
        LocalBroadcastManager.getInstance(this).unregisterReceiver(broadcastReceiver)
        finishAndRemoveTask()
        Intent(this, NotificationService::class.java).also {
            stopService(it)
        }

        android.os.Process.killProcess(android.os.Process.myPid())
    }

    private inner class TwitchServiceConnection : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            val binder = service as NotificationService.LocalBinder
            notificationService = binder.service
            isBound = true

            if (pendingChannelsToClear.isNotEmpty()) {
                pendingChannelsToClear.forEach { notificationService?.clearNotificationsOfChannel(it) }
                pendingChannelsToClear.clear()
            }

            if (viewModel.started) {
                if (!isChangingConfigurations) {
                    viewModel.reconnect(true)
                }

            } else {
                viewModel.started = true
                val oauth = twitchPreferences.oAuthKey ?: ""
                val name = twitchPreferences.userName ?: ""
                viewModel.connectAndJoinChannels(name, oauth)

                val ttsEnabled = PreferenceManager.getDefaultSharedPreferences(this@MainActivity).getBoolean(getString(R.string.preference_tts_key), false)
                notificationService?.setTTSEnabled(ttsEnabled)
            }

            binder.service.checkForNotification()
        }

        override fun onServiceDisconnected(className: ComponentName?) {
            notificationService = null
            isBound = false
        }
    }

    companion object {
        private val TAG = MainActivity::class.java.simpleName
        const val SHUTDOWN_REQUEST_FILTER = "shutdown_request_filter"
        const val OPEN_CHANNEL_KEY = "open_channel"
    }
}
package com.flxrs.dankchat

import android.content.*
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import android.view.Menu
import android.widget.ProgressBar
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.databinding.DataBindingUtil
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.navigation.findNavController
import androidx.preference.PreferenceManager
import com.flxrs.dankchat.databinding.MainActivityBinding
import com.flxrs.dankchat.preferences.DankChatPreferenceStore
import com.flxrs.dankchat.service.TwitchService
import com.flxrs.dankchat.utils.dialog.AddChannelDialogResultHandler
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.koin.androidx.viewmodel.ext.android.viewModel

class MainActivity : AppCompatActivity(), AddChannelDialogResultHandler {
    private val channels = mutableListOf<String>()
    private val viewModel: DankChatViewModel by viewModel()
    private lateinit var twitchPreferences: DankChatPreferenceStore
    private lateinit var preferences: SharedPreferences

    //    private lateinit var preferenceListener: SharedPreferences.OnSharedPreferenceChangeListener
    private lateinit var binding: MainActivityBinding
    private lateinit var broadcastReceiver: BroadcastReceiver
    private var twitchService: TwitchService? = null

    private val twitchServiceConnection = TwitchServiceConnection()
    var isBound = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        broadcastReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) = handleShutDown()
        }

        val filter = IntentFilter(SHUTDOWN_REQUEST_FILTER)
        LocalBroadcastManager.getInstance(this).registerReceiver(broadcastReceiver, filter)
        binding = DataBindingUtil.setContentView(this, R.layout.main_activity)
        twitchPreferences = DankChatPreferenceStore(this)
        preferences = PreferenceManager.getDefaultSharedPreferences(this)
        twitchPreferences.getChannelsAsString()?.let { channels.addAll(it.split(',')) }
            ?: twitchPreferences.getChannels()?.let {
                channels.addAll(it)
                twitchPreferences.setChannels(null)
            }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (!isChangingConfigurations) {
            handleShutDown()
        }
    }

    override fun onStart() {
        super.onStart()
        if (!isBound) Intent(this, TwitchService::class.java).also {
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

    override fun onSupportNavigateUp(): Boolean {
        return findNavController(R.id.main_content).navigateUp() || super.onSupportNavigateUp()
    }

    override fun onAddChannelDialogResult(channel: String) {
        val fragment = supportFragmentManager.primaryNavigationFragment?.childFragmentManager?.fragments?.first()
        if (fragment is MainFragment) {
            fragment.addChannel(channel)
        }
        invalidateOptionsMenu()
    }

    private fun handleShutDown() {
        LocalBroadcastManager.getInstance(this).unregisterReceiver(broadcastReceiver)
        finishAndRemoveTask()
        Intent(this, TwitchService::class.java).also {
            stopService(it)
        }

        android.os.Process.killProcess(android.os.Process.myPid())
    }

    private inner class TwitchServiceConnection : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            val binder = service as TwitchService.LocalBinder
            twitchService = binder.service
            twitchService?.shouldNotifyOnMention = false
            isBound = true
//            if (isLoggingIn) {
//                return
//            }

            if (viewModel.started) {
                if (!isChangingConfigurations) {
                    viewModel.reconnect(true)
                }

            } else {
                viewModel.started = true
                val oauth = twitchPreferences.getOAuthKey() ?: ""
                val name = twitchPreferences.getUserName() ?: ""
                viewModel.connectAndJoinChannels(name, oauth)
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
package com.flxrs.dankchat.main

import android.Manifest
import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.doOnAttach
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import androidx.navigation.findNavController
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager
import com.flxrs.dankchat.DankChatViewModel
import com.flxrs.dankchat.R
import com.flxrs.dankchat.data.DataRepository
import com.flxrs.dankchat.data.NotificationService
import com.flxrs.dankchat.databinding.MainActivityBinding
import com.flxrs.dankchat.preferences.DankChatPreferenceStore
import com.flxrs.dankchat.preferences.ui.*
import com.flxrs.dankchat.utils.extensions.hasPermission
import com.flxrs.dankchat.utils.extensions.isAtLeastTiramisu
import com.flxrs.dankchat.utils.extensions.navigateSafe
import com.google.android.material.color.DynamicColors
import com.google.android.material.color.DynamicColorsOptions
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity(), PreferenceFragmentCompat.OnPreferenceStartFragmentCallback {

    @Inject
    lateinit var dankChatPreferences: DankChatPreferenceStore

    private val viewModel: DankChatViewModel by viewModels()
    private val pendingChannelsToClear = mutableListOf<String>()
    private val navController: NavController by lazy { findNavController(R.id.main_content) }
    private var bindingRef: MainActivityBinding? = null
    private val binding get() = bindingRef!!

    private val requestPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        // just start the service, we don't care if the permission has been granted or not xd
        startService()
    }

    private val twitchServiceConnection = TwitchServiceConnection()
    var notificationService: NotificationService? = null
    var isBound = false
    var channelToOpen = ""

//    override fun getDelegate() = BaseContextWrappingDelegate(super.getDelegate())

    override fun onCreate(savedInstanceState: Bundle?) {
        val preferences = PreferenceManager.getDefaultSharedPreferences(this)
        val isTrueDarkModeEnabled = preferences.getBoolean(getString(R.string.preference_true_dark_theme_key), false)
        val isDynamicColorAvailable = DynamicColors.isDynamicColorAvailable()
        when {
            isTrueDarkModeEnabled && isDynamicColorAvailable -> {
                val dynamicColorsOptions = DynamicColorsOptions.Builder()
                    .setThemeOverlay(R.style.AppTheme_TrueDarkOverlay)
                    .build()
                DynamicColors.applyToActivityIfAvailable(this, dynamicColorsOptions)
            }
            isTrueDarkModeEnabled                            -> {
                theme.applyStyle(R.style.AppTheme_TrueDarkTheme, true)
                window.peekDecorView()?.context?.theme?.applyStyle(R.style.AppTheme_TrueDarkTheme, true)
            }
            else                                             -> DynamicColors.applyToActivityIfAvailable(this)
        }

        super.onCreate(savedInstanceState)
        bindingRef = DataBindingUtil.setContentView(this, R.layout.main_activity)

        if (dankChatPreferences.isLoggedIn && dankChatPreferences.oAuthKey.isNullOrBlank()) {
            dankChatPreferences.clearLogin()
        }

        viewModel.commands
            .flowWithLifecycle(lifecycle, minActiveState = Lifecycle.State.CREATED)
            .onEach {
                when (it) {
                    DataRepository.ServiceEvent.Shutdown -> handleShutDown()
                }
            }
            .launchIn(lifecycleScope)
    }

    override fun onDestroy() {
        super.onDestroy()
        bindingRef = null
        if (!isChangingConfigurations) {
            handleShutDown()
        }
    }


    @SuppressLint("InlinedApi")
    override fun onStart() {
        super.onStart()
        val needsNotificationPermission = isAtLeastTiramisu && hasPermission(Manifest.permission.POST_NOTIFICATIONS)
        when {
            needsNotificationPermission -> requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            // start service without notification permission
            else                        -> startService()
        }
    }

    private fun startService() {
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

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        val channelExtra = intent?.getStringExtra(OPEN_CHANNEL_KEY) ?: ""
        channelToOpen = channelExtra
    }

    override fun onPreferenceStartFragment(caller: PreferenceFragmentCompat, pref: Preference): Boolean {
        val fragmentTag = pref.fragment ?: return false
        when (fragmentTag.substringAfterLast(".")) {
            AppearanceSettingsFragment::class.java.simpleName    -> caller.navigateSafe(R.id.action_overviewSettingsFragment_to_appearanceSettingsFragment)
            NotificationsSettingsFragment::class.java.simpleName -> caller.navigateSafe(R.id.action_overviewSettingsFragment_to_notificationsSettingsFragment)
            ChatSettingsFragment::class.java.simpleName          -> caller.navigateSafe(R.id.action_overviewSettingsFragment_to_chatSettingsFragment)
            ToolsSettingsFragment::class.java.simpleName         -> caller.navigateSafe(R.id.action_overviewSettingsFragment_to_toolsSettingsFragment)
            DeveloperSettingsFragment::class.java.simpleName     -> caller.navigateSafe(R.id.action_overviewSettingsFragment_to_developerSettingsFragment)
            else                                                 -> return false
        }
        return true
    }

    fun clearNotificationsOfChannel(channel: String) = when {
        isBound && notificationService != null -> notificationService?.setActiveChannel(channel)
        else                                   -> pendingChannelsToClear += channel
    }

    fun setFullScreen(enabled: Boolean, changeActionBarVisibility: Boolean = true) = binding.root.doOnAttach {
        WindowCompat.setDecorFitsSystemWindows(window, !enabled)
        val windowInsetsController = WindowCompat.getInsetsController(window, it)
        when {
            enabled -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && !isInMultiWindowMode) {
                    windowInsetsController.apply {
                        systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                        hide(WindowInsetsCompat.Type.systemBars())
                    }
                }
                if (changeActionBarVisibility) {
                    supportActionBar?.hide()
                }
            }
            else    -> {
                windowInsetsController.show(WindowInsetsCompat.Type.systemBars())
                if (changeActionBarVisibility) {
                    supportActionBar?.show()
                }
            }
        }
    }

    private fun handleShutDown() {
        stopService(Intent(this, NotificationService::class.java))
        finish()
        android.os.Process.killProcess(android.os.Process.myPid())
    }

    private inner class TwitchServiceConnection : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            val binder = service as NotificationService.LocalBinder
            notificationService = binder.service
            isBound = true

            if (pendingChannelsToClear.isNotEmpty()) {
                pendingChannelsToClear.forEach { notificationService?.setActiveChannel(it) }
                pendingChannelsToClear.clear()
            }

            viewModel.init(tryReconnect = !isChangingConfigurations)
            binder.service.checkForNotification()
        }

        override fun onServiceDisconnected(className: ComponentName?) {
            notificationService = null
            isBound = false
        }
    }

    companion object {
        private val TAG = MainActivity::class.java.simpleName
        const val OPEN_CHANNEL_KEY = "open_channel"
    }
}
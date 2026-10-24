package com.flxrs.dankchat.ui.main

import android.Manifest
import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.content.res.Configuration
import android.os.Bundle
import android.os.IBinder
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import androidx.navigation.NavOptionsBuilder
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.flxrs.dankchat.BuildConfig
import com.flxrs.dankchat.DankChatViewModel
import com.flxrs.dankchat.data.UserName
import com.flxrs.dankchat.data.notification.ChatTTSPlayer
import com.flxrs.dankchat.data.notification.NotificationService
import com.flxrs.dankchat.data.repo.data.ServiceEvent
import com.flxrs.dankchat.data.toUserName
import com.flxrs.dankchat.preferences.DankChatPreferenceStore
import com.flxrs.dankchat.preferences.about.AboutScreen
import com.flxrs.dankchat.preferences.appearance.AppearanceSettingsDataStore
import com.flxrs.dankchat.preferences.appearance.AppearanceSettingsScreen
import com.flxrs.dankchat.preferences.appearance.ThemePreference
import com.flxrs.dankchat.preferences.battery.BatterySettingsScreen
import com.flxrs.dankchat.preferences.chat.ChatSettingsScreen
import com.flxrs.dankchat.preferences.chat.commands.CustomCommandsScreen
import com.flxrs.dankchat.preferences.chat.userdisplay.UserDisplayScreen
import com.flxrs.dankchat.preferences.dankchataccount.DankChatAccountScreen
import com.flxrs.dankchat.preferences.developer.DeveloperSettingsScreen
import com.flxrs.dankchat.preferences.notifications.NotificationsSettingsScreen
import com.flxrs.dankchat.preferences.notifications.highlights.HighlightsScreen
import com.flxrs.dankchat.preferences.notifications.ignores.IgnoresScreen
import com.flxrs.dankchat.preferences.overview.OverviewSettingsScreen
import com.flxrs.dankchat.preferences.overview.SettingsNavigation
import com.flxrs.dankchat.preferences.stream.StreamsSettingsScreen
import com.flxrs.dankchat.preferences.tools.ToolsSettingsScreen
import com.flxrs.dankchat.preferences.tools.tts.TTSUserIgnoreListScreen
import com.flxrs.dankchat.preferences.tools.upload.ImageUploaderScreen
import com.flxrs.dankchat.ui.changelog.ChangelogScreen
import com.flxrs.dankchat.ui.crash.CrashViewerSheet
import com.flxrs.dankchat.ui.log.LogViewerSheet
import com.flxrs.dankchat.ui.login.LoginScreen
import com.flxrs.dankchat.ui.onboarding.OnboardingDataStore
import com.flxrs.dankchat.ui.onboarding.OnboardingScreen
import com.flxrs.dankchat.ui.theme.DankChatTheme
import com.flxrs.dankchat.utils.extensions.hasPermission
import com.flxrs.dankchat.utils.extensions.isAtLeastTiramisu
import com.flxrs.dankchat.utils.extensions.isInSupportedPictureInPictureMode
import com.flxrs.dankchat.utils.extensions.keepScreenOn
import com.flxrs.dankchat.utils.extensions.parcelable
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel

private val logger = KotlinLogging.logger("MainActivity")

class MainActivity : ComponentActivity() {
    private val viewModel: DankChatViewModel by viewModel()
    private val mediaUploadViewModel: MediaUploadViewModel by viewModel()
    private val dankChatPreferenceStore: DankChatPreferenceStore by inject()
    private val appearanceSettingsDataStore: AppearanceSettingsDataStore by inject()
    private val mainEventBus: MainEventBus by inject()
    private val onboardingDataStore: OnboardingDataStore by inject()
    private val chatTTSPlayer: ChatTTSPlayer by inject()

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            startService()
        }

    private val requestImageCapture =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (it.resultCode == RESULT_OK) mediaUploadViewModel.uploadCapture(imageCapture = true)
        }

    private val requestVideoCapture =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (it.resultCode == RESULT_OK) mediaUploadViewModel.uploadCapture(imageCapture = false)
        }

    private val requestGalleryMedia =
        registerForActivityResult(PickVisualMedia()) { uri ->
            uri ?: return@registerForActivityResult
            mediaUploadViewModel.uploadGalleryMedia(uri)
        }

    private val twitchServiceConnection = TwitchServiceConnection()
    private var notificationService: NotificationService? = null
    private var isBound = false

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()

        val theme = appearanceSettingsDataStore.current().theme
        val systemDark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        val isDark = when (theme) {
            ThemePreference.Dark -> true
            ThemePreference.Light -> false
            ThemePreference.System -> systemDark
        }
        val barStyle = when {
            isDark -> SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
            else -> SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
        }
        enableEdgeToEdge(statusBarStyle = barStyle, navigationBarStyle = barStyle)
        window.isNavigationBarContrastEnforced = false

        super.onCreate(savedInstanceState)

        chatTTSPlayer.start()
        setupComposeUi()
        // Recreations and launches from recents carry the original intent, which was already handled
        if (savedInstanceState == null && (intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY) == 0) {
            emitNotificationTarget(intent)
        }

        viewModel.checkLogin()
        viewModel.serviceEvents
            .flowWithLifecycle(lifecycle, minActiveState = Lifecycle.State.CREATED)
            .onEach {
                logger.info { "Received service event: $it" }
                when (it) {
                    ServiceEvent.Shutdown -> handleShutDown()
                }
            }.launchIn(lifecycleScope)

        viewModel.keepScreenOn
            .flowWithLifecycle(lifecycle, minActiveState = Lifecycle.State.CREATED)
            .onEach {
                logger.info { "Setting FLAG_KEEP_SCREEN_ON to $it" }
                keepScreenOn(it)
            }.launchIn(lifecycleScope)
    }

    private fun setupComposeUi() {
        setContent {
            val navController = rememberNavController()
            DankChatTheme {
                val isLoggedIn by viewModel.isLoggedIn.collectAsStateWithLifecycle(
                    initialValue = dankChatPreferenceStore.isLoggedIn,
                )

                val onboardingCompleted = onboardingDataStore.current().hasCompletedOnboarding

                Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                    NavHost(
                        navController = navController,
                        startDestination = if (onboardingCompleted) Main else Onboarding,
                        // Match the regular back press so the back gesture uses each destination's pop transitions
                        predictivePopEnterTransition = { MainNavTransitions.forDestination(targetState.destination).popEnter(this) },
                        predictivePopExitTransition = { MainNavTransitions.forDestination(initialState.destination).popExit(this) },
                    ) {
                        composable<Onboarding>(
                            enterTransition = MainNavTransitions.Fade.enter,
                            exitTransition = MainNavTransitions.Fade.exit,
                            popEnterTransition = MainNavTransitions.Fade.popEnter,
                            popExitTransition = MainNavTransitions.Fade.popExit,
                        ) {
                            OnboardingScreen(
                                onNavigateToLogin = {
                                    navController.navigateIfResumed(Login)
                                },
                                onComplete = {
                                    navController.navigateIfResumed(Main) {
                                        popUpTo(Onboarding) { inclusive = true }
                                    }
                                },
                            )
                        }
                        composable<Main>(
                            exitTransition = MainNavTransitions.Slide.exit,
                            popEnterTransition = MainNavTransitions.Slide.popEnter,
                        ) {
                            MainScreen(
                                isLoggedIn = isLoggedIn,
                                onNavigateToSettings = {
                                    navController.navigateIfResumed(Settings)
                                },
                                onLogin = {
                                    navController.navigateIfResumed(Login)
                                },
                                onRelogin = {
                                    navController.navigateIfResumed(Login)
                                },
                                onLogout = {
                                    viewModel.clearDataForLogout()
                                },
                                onOpenChannel = {
                                    val channel = viewModel.activeChannel.value ?: return@MainScreen
                                    val url = "https://twitch.tv/$channel"
                                    Intent(Intent.ACTION_VIEW).also {
                                        it.data = url.toUri()
                                        startActivity(it)
                                    }
                                },
                                onReportChannel = {
                                    val channel = viewModel.activeChannel.value ?: return@MainScreen
                                    val url = "https://twitch.tv/$channel/report"
                                    Intent(Intent.ACTION_VIEW).also {
                                        it.data = url.toUri()
                                        startActivity(it)
                                    }
                                },
                                onOpenUrl = { url: String ->
                                    Intent(Intent.ACTION_VIEW).also {
                                        it.data = url.toUri()
                                        startActivity(it)
                                    }
                                },
                                onCaptureImage = {
                                    startCameraCapture(captureVideo = false)
                                },
                                onCaptureVideo = {
                                    startCameraCapture(captureVideo = true)
                                },
                                onChooseMedia = {
                                    requestGalleryMedia.launch(PickVisualMediaRequest(PickVisualMedia.ImageAndVideo))
                                },
                                onOpenLogViewer = {
                                    navController.navigateIfResumed(LogViewer())
                                },
                            )
                        }
                        composable<Login>(
                            enterTransition = MainNavTransitions.Fade.enter,
                            exitTransition = MainNavTransitions.Fade.exit,
                            popEnterTransition = MainNavTransitions.Fade.popEnter,
                            popExitTransition = MainNavTransitions.Fade.popExit,
                        ) {
                            LoginScreen(
                                onLoginSuccess = { navController.popBackStackIfResumed() },
                                onCancel = { navController.popBackStackIfResumed() },
                            )
                        }
                        composable<Settings>(
                            enterTransition = MainNavTransitions.Slide.enter,
                            exitTransition = MainNavTransitions.Slide.exit,
                            popEnterTransition = MainNavTransitions.Slide.popEnter,
                            popExitTransition = MainNavTransitions.Slide.popExit,
                        ) {
                            OverviewSettingsScreen(
                                isLoggedIn = isLoggedIn,
                                hasChangelog = com.flxrs.dankchat.ui.changelog.DankChatVersion.HAS_CHANGELOG,
                                onBack = { navController.popBackStackIfResumed() },
                                onLogout = {
                                    lifecycleScope.launch {
                                        mainEventBus.emitEvent(MainEvent.LogOutRequested)
                                        navController.popBackStackIfResumed()
                                    }
                                },
                                onNavigate = { destination ->
                                    when (destination) {
                                        SettingsNavigation.Appearance -> navController.navigateIfResumed(AppearanceSettings)
                                        SettingsNavigation.Notifications -> navController.navigateIfResumed(NotificationsSettings)
                                        SettingsNavigation.Chat -> navController.navigateIfResumed(ChatSettings)
                                        SettingsNavigation.Streams -> navController.navigateIfResumed(StreamsSettings)
                                        SettingsNavigation.Battery -> navController.navigateIfResumed(BatterySettings)
                                        SettingsNavigation.Tools -> navController.navigateIfResumed(ToolsSettings)
                                        SettingsNavigation.Developer -> navController.navigateIfResumed(DeveloperSettings)
                                        SettingsNavigation.DankChatAccount -> navController.navigateIfResumed(DankChatAccountSettings)
                                        SettingsNavigation.Changelog -> navController.navigateIfResumed(ChangelogSettings)
                                        SettingsNavigation.About -> navController.navigateIfResumed(AboutSettings)
                                    }
                                },
                            )
                        }

                        composable<AppearanceSettings>(
                            enterTransition = MainNavTransitions.Slide.enter,
                            exitTransition = MainNavTransitions.Slide.exit,
                            popEnterTransition = MainNavTransitions.Slide.popEnter,
                            popExitTransition = MainNavTransitions.Slide.popExit,
                        ) {
                            AppearanceSettingsScreen(
                                onBack = { navController.popBackStackIfResumed() },
                            )
                        }
                        composable<NotificationsSettings>(
                            enterTransition = MainNavTransitions.Slide.enter,
                            exitTransition = MainNavTransitions.Slide.exit,
                            popEnterTransition = MainNavTransitions.Slide.popEnter,
                            popExitTransition = MainNavTransitions.Slide.popExit,
                        ) {
                            NotificationsSettingsScreen(
                                onNavToHighlights = { navController.navigateIfResumed(HighlightsSettings) },
                                onNavToIgnores = { navController.navigateIfResumed(IgnoresSettings) },
                                onNavBack = { navController.popBackStackIfResumed() },
                            )
                        }
                        composable<HighlightsSettings>(
                            enterTransition = MainNavTransitions.Slide.enter,
                            exitTransition = MainNavTransitions.Slide.exit,
                            popEnterTransition = MainNavTransitions.Slide.popEnter,
                            popExitTransition = MainNavTransitions.Slide.popExit,
                        ) {
                            HighlightsScreen(
                                onNavBack = { navController.popBackStackIfResumed() },
                            )
                        }
                        composable<IgnoresSettings>(
                            enterTransition = MainNavTransitions.Slide.enter,
                            exitTransition = MainNavTransitions.Slide.exit,
                            popEnterTransition = MainNavTransitions.Slide.popEnter,
                            popExitTransition = MainNavTransitions.Slide.popExit,
                        ) {
                            IgnoresScreen(
                                onNavBack = { navController.popBackStackIfResumed() },
                            )
                        }
                        composable<ChatSettings>(
                            enterTransition = MainNavTransitions.Slide.enter,
                            exitTransition = MainNavTransitions.Slide.exit,
                            popEnterTransition = MainNavTransitions.Slide.popEnter,
                            popExitTransition = MainNavTransitions.Slide.popExit,
                        ) {
                            ChatSettingsScreen(
                                onNavToCommands = { navController.navigateIfResumed(CustomCommandsSettings) },
                                onNavToUserDisplays = { navController.navigateIfResumed(UserDisplaySettings) },
                                onNavToBattery = { navController.navigateIfResumed(BatterySettings) },
                                onNavBack = { navController.popBackStackIfResumed() },
                            )
                        }
                        composable<CustomCommandsSettings>(
                            enterTransition = MainNavTransitions.Slide.enter,
                            exitTransition = MainNavTransitions.Slide.exit,
                            popEnterTransition = MainNavTransitions.Slide.popEnter,
                            popExitTransition = MainNavTransitions.Slide.popExit,
                        ) {
                            CustomCommandsScreen(
                                onNavBack = { navController.popBackStackIfResumed() },
                            )
                        }
                        composable<UserDisplaySettings>(
                            enterTransition = MainNavTransitions.Slide.enter,
                            exitTransition = MainNavTransitions.Slide.exit,
                            popEnterTransition = MainNavTransitions.Slide.popEnter,
                            popExitTransition = MainNavTransitions.Slide.popExit,
                        ) {
                            UserDisplayScreen(
                                onNavBack = { navController.popBackStackIfResumed() },
                            )
                        }
                        composable<DankChatAccountSettings>(
                            enterTransition = MainNavTransitions.Slide.enter,
                            exitTransition = MainNavTransitions.Slide.exit,
                            popEnterTransition = MainNavTransitions.Slide.popEnter,
                            popExitTransition = MainNavTransitions.Slide.popExit,
                        ) {
                            DankChatAccountScreen(
                                onNavBack = { navController.popBackStackIfResumed() },
                            )
                        }
                        composable<StreamsSettings>(
                            enterTransition = MainNavTransitions.Slide.enter,
                            exitTransition = MainNavTransitions.Slide.exit,
                            popEnterTransition = MainNavTransitions.Slide.popEnter,
                            popExitTransition = MainNavTransitions.Slide.popExit,
                        ) {
                            StreamsSettingsScreen(
                                onBack = { navController.popBackStackIfResumed() },
                            )
                        }
                        composable<BatterySettings>(
                            enterTransition = MainNavTransitions.Slide.enter,
                            exitTransition = MainNavTransitions.Slide.exit,
                            popEnterTransition = MainNavTransitions.Slide.popEnter,
                            popExitTransition = MainNavTransitions.Slide.popExit,
                        ) {
                            BatterySettingsScreen(
                                onBack = { navController.popBackStackIfResumed() },
                            )
                        }
                        composable<ToolsSettings>(
                            enterTransition = MainNavTransitions.Slide.enter,
                            exitTransition = MainNavTransitions.Slide.exit,
                            popEnterTransition = MainNavTransitions.Slide.popEnter,
                            popExitTransition = MainNavTransitions.Slide.popExit,
                        ) {
                            ToolsSettingsScreen(
                                onNavToImageUploader = { navController.navigateIfResumed(ImageUploaderSettings) },
                                onNavToTTSUserIgnoreList = { navController.navigateIfResumed(TTSUserIgnoreListSettings) },
                                onNavBack = { navController.popBackStackIfResumed() },
                            )
                        }
                        composable<ImageUploaderSettings>(
                            enterTransition = MainNavTransitions.Slide.enter,
                            exitTransition = MainNavTransitions.Slide.exit,
                            popEnterTransition = MainNavTransitions.Slide.popEnter,
                            popExitTransition = MainNavTransitions.Slide.popExit,
                        ) {
                            ImageUploaderScreen(
                                onNavBack = { navController.popBackStackIfResumed() },
                            )
                        }
                        composable<TTSUserIgnoreListSettings>(
                            enterTransition = MainNavTransitions.Slide.enter,
                            exitTransition = MainNavTransitions.Slide.exit,
                            popEnterTransition = MainNavTransitions.Slide.popEnter,
                            popExitTransition = MainNavTransitions.Slide.popExit,
                        ) {
                            TTSUserIgnoreListScreen(
                                onNavBack = { navController.popBackStackIfResumed() },
                            )
                        }
                        composable<DeveloperSettings>(
                            enterTransition = MainNavTransitions.Slide.enter,
                            exitTransition = MainNavTransitions.Slide.exit,
                            popEnterTransition = MainNavTransitions.Slide.popEnter,
                            popExitTransition = MainNavTransitions.Slide.popExit,
                        ) {
                            DeveloperSettingsScreen(
                                onBack = { navController.popBackStackIfResumed() },
                                onOpenLogViewer = { fileName -> navController.navigateIfResumed(LogViewer(fileName)) },
                                onOpenCrashViewer = { crashId -> navController.navigateIfResumed(CrashViewer(crashId)) },
                            )
                        }
                        composable<CrashViewer>(
                            enterTransition = MainNavTransitions.Slide.enter,
                            exitTransition = MainNavTransitions.Slide.exit,
                            popEnterTransition = MainNavTransitions.Slide.popEnter,
                            popExitTransition = MainNavTransitions.Slide.popExit,
                        ) {
                            CrashViewerSheet(
                                onDismiss = { navController.popBackStackIfResumed() },
                            )
                        }
                        composable<LogViewer>(
                            enterTransition = MainNavTransitions.Slide.enter,
                            exitTransition = MainNavTransitions.Slide.exit,
                            popEnterTransition = MainNavTransitions.Slide.popEnter,
                            popExitTransition = MainNavTransitions.Slide.popExit,
                        ) {
                            LogViewerSheet(
                                onDismiss = { navController.popBackStackIfResumed() },
                            )
                        }
                        composable<ChangelogSettings>(
                            enterTransition = MainNavTransitions.Slide.enter,
                            exitTransition = MainNavTransitions.Slide.exit,
                            popEnterTransition = MainNavTransitions.Slide.popEnter,
                            popExitTransition = MainNavTransitions.Slide.popExit,
                        ) {
                            ChangelogScreen(
                                onBack = { navController.popBackStackIfResumed() },
                            )
                        }
                        composable<AboutSettings>(
                            enterTransition = MainNavTransitions.Slide.enter,
                            exitTransition = MainNavTransitions.Slide.exit,
                            popEnterTransition = MainNavTransitions.Slide.popEnter,
                            popExitTransition = MainNavTransitions.Slide.popExit,
                        ) {
                            AboutScreen(
                                onBack = { navController.popBackStackIfResumed() },
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (!isChangingConfigurations && !isInSupportedPictureInPictureMode) {
            handleShutDown()
        }
    }

    @SuppressLint("InlinedApi")
    override fun onStart() {
        super.onStart()
        val hasCompletedOnboarding = onboardingDataStore.current().hasCompletedOnboarding
        val needsNotificationPermission = hasCompletedOnboarding && isAtLeastTiramisu && !hasPermission(Manifest.permission.POST_NOTIFICATIONS)
        when {
            needsNotificationPermission -> requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            else -> startService()
        }
    }

    private fun startService() {
        if (!isBound) {
            Intent(this, NotificationService::class.java).also {
                try {
                    isBound = true
                    ContextCompat.startForegroundService(this, it)
                    bindService(it, twitchServiceConnection, BIND_AUTO_CREATE)
                } catch (t: Throwable) {
                    logger.error(t) { "Failed to start foreground service" }
                }
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
                logger.error(t) { "Failed to unbind service" }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        emitNotificationTarget(intent)
    }

    private fun emitNotificationTarget(intent: Intent) {
        val channel = intent.userNameExtra(OPEN_CHANNEL_KEY) ?: return
        val messageId = intent.getStringExtra(OPEN_MESSAGE_KEY)
        val whisperTarget = intent.userNameExtra(OPEN_WHISPER_TARGET_KEY)
        lifecycleScope.launch {
            mainEventBus.emitEvent(MainEvent.OpenChannel(channel, messageId, whisperTarget))
        }
    }

    private fun Intent.userNameExtra(key: String): UserName? = parcelable<UserName>(key) ?: getStringExtra(key)?.toUserName()

    private fun handleShutDown() {
        stopService(Intent(this, NotificationService::class.java))
        finish()
        android.os.Process.killProcess(android.os.Process.myPid())
    }

    private fun startCameraCapture(captureVideo: Boolean = false) {
        val (action, extension) =
            when {
                captureVideo -> MediaStore.ACTION_VIDEO_CAPTURE to "mp4"
                else -> MediaStore.ACTION_IMAGE_CAPTURE to "jpg"
            }
        Intent(action).also { captureIntent ->
            captureIntent.resolveActivity(packageManager)?.also {
                mediaUploadViewModel.createCaptureFile(extension)?.also { file ->
                    val uri = FileProvider.getUriForFile(this, "${BuildConfig.APPLICATION_ID}.fileprovider", file)
                    captureIntent.putExtra(MediaStore.EXTRA_OUTPUT, uri)
                    when {
                        captureVideo -> requestVideoCapture.launch(captureIntent)
                        else -> requestImageCapture.launch(captureIntent)
                    }
                }
            }
        }
    }

    private inner class TwitchServiceConnection : ServiceConnection {
        override fun onServiceConnected(
            className: ComponentName,
            service: IBinder,
        ) {
            val binder = service as NotificationService.LocalBinder
            notificationService = binder.service
            isBound = true
        }

        override fun onServiceDisconnected(className: ComponentName?) {
            notificationService = null
            isBound = false
        }
    }

    companion object {
        const val OPEN_CHANNEL_KEY = "open_channel"
        const val OPEN_MESSAGE_KEY = "open_message"
        const val OPEN_WHISPER_TARGET_KEY = "open_whisper_target"
    }
}

// Entries stay below RESUMED while a transition runs, so this drops double taps that would pop or push twice
private val NavController.isCurrentEntryResumed: Boolean
    get() = currentBackStackEntry?.lifecycle?.currentState == Lifecycle.State.RESUMED

private fun NavController.popBackStackIfResumed() {
    if (isCurrentEntryResumed) {
        popBackStack()
    }
}

private fun <T : Any> NavController.navigateIfResumed(
    route: T,
    builder: NavOptionsBuilder.() -> Unit = {},
) {
    if (isCurrentEntryResumed) {
        navigate(route, builder)
    }
}

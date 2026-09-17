package com.flxrs.dankchat.ui.main

import android.Manifest
import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import android.os.IBinder
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.net.toFile
import androidx.core.net.toUri
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavBackStackEntry
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.flxrs.dankchat.BuildConfig
import com.flxrs.dankchat.DankChatViewModel
import com.flxrs.dankchat.R
import com.flxrs.dankchat.data.UserName
import com.flxrs.dankchat.data.api.ApiException
import com.flxrs.dankchat.data.notification.ChatTTSPlayer
import com.flxrs.dankchat.data.notification.NotificationService
import com.flxrs.dankchat.data.repo.data.DataRepository
import com.flxrs.dankchat.data.repo.data.ServiceEvent
import com.flxrs.dankchat.data.toUserName
import com.flxrs.dankchat.di.DispatchersProvider
import com.flxrs.dankchat.preferences.DankChatPreferenceStore
import com.flxrs.dankchat.preferences.about.AboutScreen
import com.flxrs.dankchat.preferences.appearance.AppearanceSettingsDataStore
import com.flxrs.dankchat.preferences.appearance.AppearanceSettingsScreen
import com.flxrs.dankchat.preferences.appearance.ThemePreference
import com.flxrs.dankchat.preferences.battery.BatterySettingsScreen
import com.flxrs.dankchat.preferences.chat.ChatSettingsScreen
import com.flxrs.dankchat.preferences.chat.commands.CustomCommandsScreen
import com.flxrs.dankchat.preferences.chat.userdisplay.UserDisplayScreen
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
import com.flxrs.dankchat.utils.createMediaFile
import com.flxrs.dankchat.utils.extensions.hasPermission
import com.flxrs.dankchat.utils.extensions.isAtLeastTiramisu
import com.flxrs.dankchat.utils.extensions.isInSupportedPictureInPictureMode
import com.flxrs.dankchat.utils.extensions.keepScreenOn
import com.flxrs.dankchat.utils.extensions.parcelable
import com.flxrs.dankchat.utils.removeExifAttributes
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel
import java.io.IOException

private val logger = KotlinLogging.logger("MainActivity")

class MainActivity : ComponentActivity() {
    private val viewModel: DankChatViewModel by viewModel()
    private val dankChatPreferenceStore: DankChatPreferenceStore by inject()
    private val appearanceSettingsDataStore: AppearanceSettingsDataStore by inject()
    private val mainEventBus: MainEventBus by inject()
    private val onboardingDataStore: OnboardingDataStore by inject()
    private val dataRepository: DataRepository by inject()
    private val chatTTSPlayer: ChatTTSPlayer by inject()
    private val dispatchersProvider: DispatchersProvider by inject()
    private var currentMediaUri: Uri = Uri.EMPTY

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            startService()
        }

    private val requestImageCapture =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (it.resultCode == RESULT_OK) handleCaptureRequest(imageCapture = true)
        }

    private val requestVideoCapture =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (it.resultCode == RESULT_OK) handleCaptureRequest(imageCapture = false)
        }

    private val requestGalleryMedia =
        registerForActivityResult(PickVisualMedia()) { uri ->
            uri ?: return@registerForActivityResult
            val contentResolver = contentResolver
            val mimeType = contentResolver.getType(uri)
            val extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType)
            if (extension == null) {
                lifecycleScope.launch { mainEventBus.emitEvent(MainEvent.UploadFailed(getString(R.string.snackbar_upload_failed), createMediaFile(this@MainActivity), false)) }
                return@registerForActivityResult
            }

            val copy = createMediaFile(this, extension)
            try {
                contentResolver.openInputStream(uri)?.use { input -> copy.outputStream().use { input.copyTo(it) } }
                if (copy.extension == "jpg" || copy.extension == "jpeg") {
                    copy.removeExifAttributes()
                }
                uploadMedia(copy, imageCapture = false)
            } catch (_: Throwable) {
                copy.delete()
                lifecycleScope.launch { mainEventBus.emitEvent(MainEvent.UploadFailed(null, copy, false)) }
            }
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
        emitNotificationTarget(intent)

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
                    ) {
                        composable<Onboarding>(
                            enterTransition = { fadeIn(animationSpec = tween(220, delayMillis = 90)) },
                            exitTransition = { fadeOut(animationSpec = tween(90)) },
                            popEnterTransition = { fadeIn(animationSpec = tween(220, delayMillis = 90)) },
                            popExitTransition = { fadeOut(animationSpec = tween(90)) },
                        ) {
                            OnboardingScreen(
                                onNavigateToLogin = {
                                    navController.navigate(Login)
                                },
                                onComplete = {
                                    navController.navigate(Main) {
                                        popUpTo(Onboarding) { inclusive = true }
                                    }
                                },
                            )
                        }
                        composable<Main>(
                            exitTransition = { scaleOut(targetScale = 0.92f, animationSpec = tween(300)) + fadeOut(animationSpec = tween(200)) },
                            popEnterTransition = { slideInHorizontally(initialOffsetX = { -it / 3 }, animationSpec = tween(300)) + fadeIn(animationSpec = tween(300)) },
                        ) {
                            MainScreen(
                                isLoggedIn = isLoggedIn,
                                onNavigateToSettings = {
                                    navController.navigate(Settings)
                                },
                                onLogin = {
                                    navController.navigate(Login)
                                },
                                onRelogin = {
                                    navController.navigate(Login)
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
                                    navController.navigate(LogViewer())
                                },
                            )
                        }
                        composable<Login>(
                            enterTransition = { fadeIn(animationSpec = tween(220, delayMillis = 90)) },
                            exitTransition = { fadeOut(animationSpec = tween(90)) },
                            popEnterTransition = { fadeIn(animationSpec = tween(220, delayMillis = 90)) },
                            popExitTransition = { fadeOut(animationSpec = tween(90)) },
                        ) {
                            LoginScreen(
                                onLoginSuccess = { navController.popBackStack() },
                                onCancel = { navController.popBackStack() },
                            )
                        }
                        composable<Settings>(
                            enterTransition = { slideInHorizontally(initialOffsetX = { it / 3 }, animationSpec = tween(300)) + fadeIn(animationSpec = tween(200)) },
                            exitTransition = { scaleOut(targetScale = 0.92f, animationSpec = tween(300)) + fadeOut(animationSpec = tween(200)) },
                            popEnterTransition = { slideInHorizontally(initialOffsetX = { -it / 3 }, animationSpec = tween(300)) + fadeIn(animationSpec = tween(300)) },
                            popExitTransition = { scaleOut(targetScale = 0.92f, animationSpec = tween(300)) + fadeOut(animationSpec = tween(200)) },
                        ) {
                            OverviewSettingsScreen(
                                isLoggedIn = isLoggedIn,
                                hasChangelog = com.flxrs.dankchat.ui.changelog.DankChatVersion.HAS_CHANGELOG,
                                onBack = { navController.popBackStack() },
                                onLogout = {
                                    lifecycleScope.launch {
                                        mainEventBus.emitEvent(MainEvent.LogOutRequested)
                                        navController.popBackStack()
                                    }
                                },
                                onNavigate = { destination ->
                                    when (destination) {
                                        SettingsNavigation.Appearance -> navController.navigate(AppearanceSettings)
                                        SettingsNavigation.Notifications -> navController.navigate(NotificationsSettings)
                                        SettingsNavigation.Chat -> navController.navigate(ChatSettings)
                                        SettingsNavigation.Streams -> navController.navigate(StreamsSettings)
                                        SettingsNavigation.Battery -> navController.navigate(BatterySettings)
                                        SettingsNavigation.Tools -> navController.navigate(ToolsSettings)
                                        SettingsNavigation.Developer -> navController.navigate(DeveloperSettings)
                                        SettingsNavigation.Changelog -> navController.navigate(ChangelogSettings)
                                        SettingsNavigation.About -> navController.navigate(AboutSettings)
                                    }
                                },
                            )
                        }

                        val subEnter: @JvmSuppressWildcards AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
                            slideInHorizontally(initialOffsetX = { it / 3 }, animationSpec = tween(300)) + fadeIn(animationSpec = tween(200))
                        }
                        val subExit: @JvmSuppressWildcards AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
                            scaleOut(targetScale = 0.92f, animationSpec = tween(300)) + fadeOut(animationSpec = tween(200))
                        }
                        val subPopEnter: @JvmSuppressWildcards AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
                            slideInHorizontally(initialOffsetX = { -it / 3 }, animationSpec = tween(300)) + fadeIn(animationSpec = tween(300))
                        }
                        val subPopExit: @JvmSuppressWildcards AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
                            scaleOut(targetScale = 0.92f, animationSpec = tween(300)) + fadeOut(animationSpec = tween(200))
                        }

                        composable<AppearanceSettings>(
                            enterTransition = subEnter,
                            exitTransition = subExit,
                            popEnterTransition = subPopEnter,
                            popExitTransition = subPopExit,
                        ) {
                            AppearanceSettingsScreen(
                                onBack = { navController.popBackStack() },
                            )
                        }
                        composable<NotificationsSettings>(
                            enterTransition = subEnter,
                            exitTransition = subExit,
                            popEnterTransition = subPopEnter,
                            popExitTransition = subPopExit,
                        ) {
                            NotificationsSettingsScreen(
                                onNavToHighlights = { navController.navigate(HighlightsSettings) },
                                onNavToIgnores = { navController.navigate(IgnoresSettings) },
                                onNavBack = { navController.popBackStack() },
                            )
                        }
                        composable<HighlightsSettings>(
                            enterTransition = subEnter,
                            exitTransition = subExit,
                            popEnterTransition = subPopEnter,
                            popExitTransition = subPopExit,
                        ) {
                            HighlightsScreen(
                                onNavBack = { navController.popBackStack() },
                            )
                        }
                        composable<IgnoresSettings>(
                            enterTransition = subEnter,
                            exitTransition = subExit,
                            popEnterTransition = subPopEnter,
                            popExitTransition = subPopExit,
                        ) {
                            IgnoresScreen(
                                onNavBack = { navController.popBackStack() },
                            )
                        }
                        composable<ChatSettings>(
                            enterTransition = subEnter,
                            exitTransition = subExit,
                            popEnterTransition = subPopEnter,
                            popExitTransition = subPopExit,
                        ) {
                            ChatSettingsScreen(
                                onNavToCommands = { navController.navigate(CustomCommandsSettings) },
                                onNavToUserDisplays = { navController.navigate(UserDisplaySettings) },
                                onNavToBattery = { navController.navigate(BatterySettings) },
                                onNavBack = { navController.popBackStack() },
                            )
                        }
                        composable<CustomCommandsSettings>(
                            enterTransition = subEnter,
                            exitTransition = subExit,
                            popEnterTransition = subPopEnter,
                            popExitTransition = subPopExit,
                        ) {
                            CustomCommandsScreen(
                                onNavBack = { navController.popBackStack() },
                            )
                        }
                        composable<UserDisplaySettings>(
                            enterTransition = subEnter,
                            exitTransition = subExit,
                            popEnterTransition = subPopEnter,
                            popExitTransition = subPopExit,
                        ) {
                            UserDisplayScreen(
                                onNavBack = { navController.popBackStack() },
                            )
                        }
                        composable<StreamsSettings>(
                            enterTransition = subEnter,
                            exitTransition = subExit,
                            popEnterTransition = subPopEnter,
                            popExitTransition = subPopExit,
                        ) {
                            StreamsSettingsScreen(
                                onBack = { navController.popBackStack() },
                            )
                        }
                        composable<BatterySettings>(
                            enterTransition = subEnter,
                            exitTransition = subExit,
                            popEnterTransition = subPopEnter,
                            popExitTransition = subPopExit,
                        ) {
                            BatterySettingsScreen(
                                onBack = { navController.popBackStack() },
                            )
                        }
                        composable<ToolsSettings>(
                            enterTransition = subEnter,
                            exitTransition = subExit,
                            popEnterTransition = subPopEnter,
                            popExitTransition = subPopExit,
                        ) {
                            ToolsSettingsScreen(
                                onNavToImageUploader = { navController.navigate(ImageUploaderSettings) },
                                onNavToTTSUserIgnoreList = { navController.navigate(TTSUserIgnoreListSettings) },
                                onNavBack = { navController.popBackStack() },
                            )
                        }
                        composable<ImageUploaderSettings>(
                            enterTransition = subEnter,
                            exitTransition = subExit,
                            popEnterTransition = subPopEnter,
                            popExitTransition = subPopExit,
                        ) {
                            ImageUploaderScreen(
                                onNavBack = { navController.popBackStack() },
                            )
                        }
                        composable<TTSUserIgnoreListSettings>(
                            enterTransition = subEnter,
                            exitTransition = subExit,
                            popEnterTransition = subPopEnter,
                            popExitTransition = subPopExit,
                        ) {
                            TTSUserIgnoreListScreen(
                                onNavBack = { navController.popBackStack() },
                            )
                        }
                        composable<DeveloperSettings>(
                            enterTransition = subEnter,
                            exitTransition = subExit,
                            popEnterTransition = subPopEnter,
                            popExitTransition = subPopExit,
                        ) {
                            DeveloperSettingsScreen(
                                onBack = { navController.popBackStack() },
                                onOpenLogViewer = { fileName -> navController.navigate(LogViewer(fileName)) },
                                onOpenCrashViewer = { crashId -> navController.navigate(CrashViewer(crashId)) },
                            )
                        }
                        composable<CrashViewer>(
                            enterTransition = subEnter,
                            exitTransition = subExit,
                            popEnterTransition = subPopEnter,
                            popExitTransition = subPopExit,
                        ) {
                            CrashViewerSheet(
                                onDismiss = { navController.popBackStack() },
                            )
                        }
                        composable<LogViewer>(
                            enterTransition = subEnter,
                            exitTransition = subExit,
                            popEnterTransition = subPopEnter,
                            popExitTransition = subPopExit,
                        ) {
                            LogViewerSheet(
                                onDismiss = { navController.popBackStack() },
                            )
                        }
                        composable<ChangelogSettings>(
                            enterTransition = subEnter,
                            exitTransition = subExit,
                            popEnterTransition = subPopEnter,
                            popExitTransition = subPopExit,
                        ) {
                            ChangelogScreen(
                                onBack = { navController.popBackStack() },
                            )
                        }
                        composable<AboutSettings>(
                            enterTransition = subEnter,
                            exitTransition = subExit,
                            popEnterTransition = subPopEnter,
                            popExitTransition = subPopExit,
                        ) {
                            AboutScreen(
                                onBack = { navController.popBackStack() },
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

    fun clearNotificationsOfChannel(channel: UserName) {
        notificationService?.clearNotificationsForChannel(channel)
    }

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
                try {
                    createMediaFile(this, extension).apply { currentMediaUri = toUri() }
                } catch (_: IOException) {
                    null
                }?.also { file ->
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

    private fun handleCaptureRequest(imageCapture: Boolean) {
        if (currentMediaUri == Uri.EMPTY) return
        var mediaFile: java.io.File? = null

        try {
            mediaFile = currentMediaUri.toFile()
            currentMediaUri = Uri.EMPTY
            uploadMedia(mediaFile, imageCapture)
        } catch (_: IOException) {
            currentMediaUri = Uri.EMPTY
            mediaFile?.delete()
            lifecycleScope.launch { mainEventBus.emitEvent(MainEvent.UploadFailed(null, mediaFile ?: return@launch, imageCapture)) }
        }
    }

    private fun uploadMedia(
        file: java.io.File,
        imageCapture: Boolean,
    ) {
        lifecycleScope.launch {
            mainEventBus.emitEvent(MainEvent.UploadLoading)
            withContext(dispatchersProvider.io) {
                if (imageCapture) {
                    runCatching { file.removeExifAttributes() }
                }
            }
            val result = withContext(dispatchersProvider.io) { dataRepository.uploadMedia(file) }
            result.fold(
                onSuccess = { url ->
                    file.delete()
                    mainEventBus.emitEvent(MainEvent.UploadSuccess(url))
                },
                onFailure = { throwable ->
                    val message =
                        when (throwable) {
                            is ApiException -> "${throwable.status} ${throwable.message}"
                            else -> throwable.message
                        }
                    mainEventBus.emitEvent(MainEvent.UploadFailed(message, file, imageCapture))
                },
            )
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

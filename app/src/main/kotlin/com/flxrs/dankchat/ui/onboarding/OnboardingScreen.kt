package com.flxrs.dankchat.ui.onboarding

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.flxrs.dankchat.R
import com.flxrs.dankchat.utils.compose.buildLinkAnnotation
import com.flxrs.dankchat.utils.extensions.isAtLeastTiramisu
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel

private const val PAGE_COUNT = 4

@Composable
fun OnboardingScreen(
    onNavigateToLogin: () -> Unit,
    onComplete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: OnboardingViewModel = koinViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val pagerState =
        rememberPagerState(
            initialPage = state.initialPage,
            pageCount = { PAGE_COUNT },
        )
    SideEffect(pagerState.currentPage) {
        viewModel.setCurrentPage(pagerState.currentPage)
    }

    // Auto-advance past login page when login is detected by the ViewModel
    LaunchedEffect(state.loginCompleted) {
        if (state.loginCompleted && pagerState.currentPage == 1) {
            pagerState.animateScrollToPage(2)
        }
    }

    Surface(modifier = modifier.fillMaxSize()) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .safeDrawingPadding()
                    .padding(horizontal = 24.dp),
        ) {
            LinearProgressIndicator(
                progress = { (pagerState.currentPage + 1).toFloat() / PAGE_COUNT },
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
            )

            HorizontalPager(
                state = pagerState,
                userScrollEnabled = false,
                modifier = Modifier.weight(1f),
            ) { page ->
                when (page) {
                    0 -> {
                        WelcomePage(
                            onStart = { scope.launch { pagerState.animateScrollToPage(1) } },
                        )
                    }

                    1 -> {
                        LoginPage(
                            loginCompleted = state.loginCompleted,
                            onLogin = onNavigateToLogin,
                            onSkip = { scope.launch { pagerState.animateScrollToPage(2) } },
                            onContinue = { scope.launch { pagerState.animateScrollToPage(2) } },
                        )
                    }

                    2 -> {
                        MessageHistoryPage(
                            decided = state.messageHistoryDecided,
                            onEnable = {
                                viewModel.onMessageHistoryDecision(enabled = true)
                                scope.launch { pagerState.animateScrollToPage(3) }
                            },
                            onDisable = {
                                viewModel.onMessageHistoryDecision(enabled = false)
                                scope.launch { pagerState.animateScrollToPage(3) }
                            },
                        )
                    }

                    3 -> {
                        NotificationsPage(
                            onContinue = {
                                viewModel.completeOnboarding(onComplete)
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun OnboardingPage(
    title: String,
    icon: @Composable () -> Unit,
    body: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        icon()
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(12.dp))
        body()
        Spacer(modifier = Modifier.height(32.dp))
        content()
    }
}

@Composable
private fun OnboardingBody(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge,
        textAlign = TextAlign.Center,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun WelcomePage(
    onStart: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OnboardingPage(
        icon = {
            Icon(
                painter = painterResource(R.drawable.ic_dank_chat_mono_cropped),
                contentDescription = null,
                modifier = Modifier.size(128.dp),
                tint = MaterialTheme.colorScheme.inverseOnSurface,
            )
        },
        title = stringResource(R.string.onboarding_welcome_title),
        body = { OnboardingBody(stringResource(R.string.onboarding_welcome_body)) },
        modifier = modifier,
    ) {
        Button(onClick = onStart) {
            Text(stringResource(R.string.onboarding_get_started))
        }
    }
}

@Composable
private fun LoginPage(
    loginCompleted: Boolean,
    onLogin: () -> Unit,
    onSkip: () -> Unit,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OnboardingPage(
        icon = {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.Login,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
        },
        title = stringResource(R.string.onboarding_login_title),
        body = {
            OnboardingBody(stringResource(R.string.onboarding_login_body))
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.onboarding_login_disclaimer),
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        modifier = modifier,
    ) {
        AnimatedContent(
            targetState = loginCompleted,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "login_state",
        ) { completed ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                when {
                    completed -> {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.onboarding_login_success),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = onContinue) {
                            Text(stringResource(R.string.onboarding_continue))
                        }
                    }

                    else -> {
                        Button(onClick = onLogin) {
                            Text(stringResource(R.string.onboarding_login_button))
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        TextButton(onClick = onSkip) {
                            Text(stringResource(R.string.onboarding_skip))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MessageHistoryPage(
    decided: Boolean,
    onEnable: () -> Unit,
    onDisable: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OnboardingPage(
        icon = {
            Icon(
                imageVector = Icons.Default.History,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
        },
        title = stringResource(R.string.onboarding_history_title),
        body = {
            val bodyText = stringResource(R.string.onboarding_history_body)
            val url = "https://recent-messages.robotty.de/"
            val linkAnnotation = buildLinkAnnotation(url)
            val annotatedBody =
                remember(bodyText, linkAnnotation) {
                    buildAnnotatedString {
                        val urlStart = bodyText.indexOf(url)
                        when {
                            urlStart >= 0 -> {
                                append(bodyText.substring(0, urlStart))
                                withLink(link = linkAnnotation) {
                                    append(url)
                                }
                                append(bodyText.substring(urlStart + url.length))
                            }

                            else -> {
                                append(bodyText)
                            }
                        }
                    }
                }
            Text(
                text = annotatedBody,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        modifier = modifier,
    ) {
        if (!decided) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = onDisable) {
                    Text(stringResource(R.string.onboarding_history_disable))
                }
                Button(onClick = onEnable) {
                    Text(stringResource(R.string.onboarding_history_enable))
                }
            }
        }
    }
}

private enum class NotificationPermissionState { Pending, Granted, Denied }

@SuppressLint("InlinedApi")
@Composable
private fun NotificationsPage(
    onContinue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var permissionState by remember { mutableStateOf(NotificationPermissionState.Pending) }

    val permissionLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission(),
        ) { granted ->
            if (granted) {
                onContinue()
            } else {
                permissionState = NotificationPermissionState.Denied
            }
        }

    // Re-check permission when returning from notification settings — auto-advance if granted
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            if (isAtLeastTiramisu && permissionState == NotificationPermissionState.Denied) {
                val granted =
                    ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.POST_NOTIFICATIONS,
                    ) == PackageManager.PERMISSION_GRANTED
                if (granted) {
                    onContinue()
                }
            }
        }
    }

    OnboardingPage(
        icon = {
            Icon(
                imageVector = Icons.Default.Notifications,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
        },
        title = stringResource(R.string.onboarding_notifications_title),
        body = { OnboardingBody(stringResource(R.string.onboarding_notifications_body)) },
        modifier = modifier,
    ) {
        if (isAtLeastTiramisu) {
            AnimatedContent(
                targetState = permissionState,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "notification_state",
            ) { state ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    when (state) {
                        NotificationPermissionState.Granted -> {
                            Button(onClick = onContinue) {
                                Text(stringResource(R.string.onboarding_continue))
                            }
                        }

                        NotificationPermissionState.Denied -> {
                            Text(
                                text = stringResource(R.string.onboarding_notifications_rationale),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 32.dp),
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            FilledTonalButton(
                                onClick = {
                                    val intent =
                                        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                        }
                                    context.startActivity(intent)
                                },
                            ) {
                                Text(stringResource(R.string.onboarding_notifications_open_settings))
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            TextButton(onClick = onContinue) {
                                Text(stringResource(R.string.onboarding_skip))
                            }
                        }

                        NotificationPermissionState.Pending -> {
                            Button(
                                onClick = {
                                    permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                },
                            ) {
                                Text(stringResource(R.string.onboarding_notifications_allow))
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            TextButton(onClick = onContinue) {
                                Text(stringResource(R.string.onboarding_skip))
                            }
                        }
                    }
                }
            }
        } else {
            // Pre-Tiramisu: no runtime permission needed
            Button(onClick = onContinue) {
                Text(stringResource(R.string.onboarding_continue))
            }
        }
    }
}

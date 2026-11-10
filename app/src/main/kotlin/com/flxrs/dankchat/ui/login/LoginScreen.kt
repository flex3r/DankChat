package com.flxrs.dankchat.ui.login

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.view.ViewGroup
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import com.flxrs.dankchat.R
import com.flxrs.dankchat.ui.login.LoginViewModel.LoginEvent
import com.flxrs.dankchat.ui.main.DismissibleSnackbar
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel

private val logger = KotlinLogging.logger("LoginScreen")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    onLoginSuccess: () -> Unit,
    onCancel: () -> Unit,
) {
    val viewModel: LoginViewModel = koinViewModel()
    var isLoading by remember { mutableStateOf(true) }
    var isZoomedOut by remember { mutableStateOf(false) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var webViewGeneration by remember { mutableIntStateOf(0) }

    val snackbarHostState = remember { SnackbarHostState() }
    val resources = LocalResources.current

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            snackbarHostState.currentSnackbarData?.dismiss()
            val errorMessage =
                when (event) {
                    LoginEvent.Success -> {
                        onLoginSuccess()
                        return@collect
                    }

                    LoginEvent.TokenMissing -> {
                        R.string.snackbar_login_failed
                    }

                    LoginEvent.ValidationFailed -> {
                        R.string.oauth_verify_failed
                    }
                }

            // A pending snackbar must not hold back the result of the next attempt
            launch {
                val result =
                    snackbarHostState.showSnackbar(
                        message = resources.getString(errorMessage),
                        actionLabel = resources.getString(R.string.snackbar_retry),
                        duration = SnackbarDuration.Indefinite,
                    )
                if (result == SnackbarResult.ActionPerformed) {
                    webViewRef?.loadUrl(viewModel.loginUrl)
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.login)) },
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.dialog_cancel))
                    }
                },
                actions = {
                    IconButton(onClick = {
                        val newZoom = if (isZoomedOut) 100 else 50
                        webViewRef?.settings?.textZoom = newZoom
                        isZoomedOut = !isZoomedOut
                    }) {
                        Icon(
                            imageVector = if (isZoomedOut) Icons.Default.ZoomIn else Icons.Default.ZoomOut,
                            contentDescription = stringResource(if (isZoomedOut) R.string.login_menu_zoom_in else R.string.login_menu_zoom_out),
                        )
                    }
                },
            )
        },
        snackbarHost = {
            SnackbarHost(
                hostState = snackbarHostState,
                snackbar = { data -> DismissibleSnackbar(data) },
            )
        },
    ) { paddingValues ->
        Column(
            modifier =
                Modifier
                    .padding(paddingValues)
                    .fillMaxSize(),
        ) {
            if (isLoading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            key(webViewGeneration) {
                AndroidView(
                    factory = { context ->
                        WebView(context).also { webViewRef = it }.apply {
                            layoutParams =
                                ViewGroup.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                )
                            @SuppressLint("SetJavaScriptEnabled")
                            settings.javaScriptEnabled = true
                            settings.setSupportZoom(true)
                            settings.textZoom = if (isZoomedOut) 50 else 100

                            clearCache(true)
                            clearFormData()

                            webViewClient =
                                object : WebViewClient() {
                                    override fun onPageStarted(
                                        view: WebView?,
                                        url: String?,
                                        favicon: Bitmap?,
                                    ) {
                                        isLoading = true
                                    }

                                    override fun onPageFinished(
                                        view: WebView?,
                                        url: String?,
                                    ) {
                                        isLoading = false
                                    }

                                    override fun shouldOverrideUrlLoading(
                                        view: WebView?,
                                        request: WebResourceRequest?,
                                    ): Boolean {
                                        val url = request?.url ?: return false
                                        if (!viewModel.isRedirectUrl(url.toString())) {
                                            return false
                                        }

                                        viewModel.parseToken(url.fragment.orEmpty())
                                        return true // Consume
                                    }

                                    override fun onReceivedError(
                                        view: WebView?,
                                        request: WebResourceRequest?,
                                        error: WebResourceError?,
                                    ) {
                                        logger.error { "Error: ${error?.description}" }
                                        isLoading = false
                                    }

                                    // Default behavior would crash the whole app when the render process dies
                                    override fun onRenderProcessGone(
                                        view: WebView?,
                                        detail: RenderProcessGoneDetail?,
                                    ): Boolean {
                                        logger.warn { "Login WebView render process gone (didCrash=${detail?.didCrash()}), recreating" }
                                        webViewGeneration++
                                        return true
                                    }
                                }

                            loadUrl(viewModel.loginUrl)
                        }
                    },
                    onRelease = { it.destroy() },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }

    BackHandler {
        onCancel()
    }
}

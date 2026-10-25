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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import com.flxrs.dankchat.R
import io.github.oshai.kotlinlogging.KotlinLogging
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

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            if (event.successful) {
                onLoginSuccess()
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
                                        val fragment = request?.url?.fragment ?: return false
                                        viewModel.parseToken(fragment)
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

package com.flxrs.dankchat

import android.annotation.SuppressLint
import android.app.Activity
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.webkit.*
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceManager
import com.flxrs.dankchat.databinding.LoginActivityBinding
import com.flxrs.dankchat.preferences.DankChatPreferenceStore
import com.flxrs.dankchat.service.api.TwitchApi
import kotlinx.coroutines.launch
import java.util.*

class LoginActivity : AppCompatActivity() {

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val darkThemeKey = getString(R.string.preference_dark_theme_key)
        PreferenceManager.getDefaultSharedPreferences(this).apply {
            delegate.localNightMode = if (getBoolean(darkThemeKey, true)) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
        }
        DataBindingUtil.setContentView<LoginActivityBinding>(this, R.layout.login_activity).apply {
            setSupportActionBar(loginToolbar)
            supportActionBar?.apply {
                setDisplayHomeAsUpEnabled(true)
                title = getString(R.string.login_title)
            }

            webview.apply {
                with(settings) {
                    javaScriptEnabled = true
                    setSupportZoom(true)
                }
                CookieManager.getInstance().removeAllCookies(null)
                clearCache(true)
                clearFormData()
                webViewClient = TwitchAuthClient()
                loadUrl(TwitchApi.LOGIN_URL)
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }

    private inner class TwitchAuthClient : WebViewClient() {
        override fun onReceivedError(
            view: WebView?,
            request: WebResourceRequest?,
            error: WebResourceError?
        ) {
            setResult(Activity.RESULT_CANCELED)
            finish()
        }

        @SuppressWarnings("DEPRECATION")
        override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
            val urlString = url ?: ""
            val fragment = Uri.parse(urlString).fragment ?: ""
            parseOAuthToken(fragment)
            return false
        }

        @RequiresApi(Build.VERSION_CODES.N)
        override fun shouldOverrideUrlLoading(
            view: WebView?,
            request: WebResourceRequest?
        ): Boolean {
            val fragment = request?.url?.fragment ?: ""
            parseOAuthToken(fragment)
            return false
        }

        private fun parseOAuthToken(fragment: String) {
            if (fragment.startsWith("access_token=")) {
                val token = fragment.substringAfter("access_token=").substringBefore("&scope=")
                lifecycleScope.launch {
                    TwitchApi.getUser(token)?.let {
                        if (it.name.isNotBlank()) {
                            DankChatPreferenceStore(this@LoginActivity).apply {
                                setOAuthKey("oauth:$token")
                                setUserName(it.name.toLowerCase(Locale.getDefault()))
                                setUserId(it.id)
                            }
                            setResult(Activity.RESULT_OK)
                            finish()
                        } else setResult(Activity.RESULT_CANCELED)
                    } ?: setResult(Activity.RESULT_CANCELED)
                    finish()
                }
            }
        }
    }
}
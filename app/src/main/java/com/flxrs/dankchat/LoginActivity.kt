package com.flxrs.dankchat

import android.app.Activity
import android.os.Bundle
import android.webkit.*
import androidx.appcompat.app.AppCompatActivity
import androidx.databinding.DataBindingUtil
import com.flxrs.dankchat.databinding.LoginActivityBinding
import com.flxrs.dankchat.preferences.TwitchAuthStore
import com.flxrs.dankchat.utils.TwitchApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LoginActivity : AppCompatActivity() {

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		val binding = DataBindingUtil.setContentView<LoginActivityBinding>(this, R.layout.login_activity)
		binding.webview.apply {
			with(settings) {
				javaScriptEnabled = true
				javaScriptCanOpenWindowsAutomatically = true
				setSupportZoom(true)
			}
			CookieManager.getInstance().removeAllCookies(null)
			clearCache(true)
			clearFormData()
			webViewClient = TwitchAuthClient()
			loadUrl(TwitchApi.LOGIN_URL)
		}
	}

	private inner class TwitchAuthClient : WebViewClient() {

		override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
			setResult(Activity.RESULT_CANCELED)
			finish()
		}

		override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
			val fragment = request?.url?.fragment ?: ""
			if (fragment.startsWith("access_token=")) {
				val token = fragment.substringAfter("access_token=").substringBefore("&scope=")
				GlobalScope.launch {
					val name = TwitchApi.getUserDataAsync(token).await()
					withContext(Dispatchers.Main) {
						if (name.isNotBlank()) {
							val authStore = TwitchAuthStore(this@LoginActivity)
							authStore.setOAuthKey("oauth:$token")
							authStore.setUserName(name.toLowerCase())
						}
						setResult(Activity.RESULT_OK)
						finish()
					}
				}
			}
			return false
		}
	}

	companion object {
		private val TAG = LoginActivity::class.java.simpleName
	}
}
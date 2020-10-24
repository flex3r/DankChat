package com.flxrs.dankchat.login

import android.annotation.SuppressLint
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.*
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.net.toUri
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.flxrs.dankchat.MainFragment
import com.flxrs.dankchat.R
import com.flxrs.dankchat.databinding.LoginFragmentBinding
import com.flxrs.dankchat.preferences.DankChatPreferenceStore
import com.flxrs.dankchat.service.api.TwitchApi
import com.flxrs.dankchat.service.api.dto.UserDtos
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import java.util.*
import javax.inject.Inject

@AndroidEntryPoint
class LoginFragment : Fragment() {

    private lateinit var binding: LoginFragmentBinding
    private lateinit var dankChatPreferenceStore: DankChatPreferenceStore

    @Inject
    lateinit var twitchApi: TwitchApi

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        binding = LoginFragmentBinding.inflate(inflater, container, false)
        binding.webview.apply {
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

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        setHasOptionsMenu(true)
        (requireActivity() as AppCompatActivity).apply {
            setSupportActionBar(binding.loginToolbar)
            supportActionBar?.apply {
                setDisplayHomeAsUpEnabled(true)
                title = getString(R.string.login_title)
            }
        }
        dankChatPreferenceStore = DankChatPreferenceStore(view.context)
    }

    private inner class TwitchAuthClient : WebViewClient() {
        @SuppressWarnings("DEPRECATION")
        override fun onReceivedError(view: WebView?, errorCode: Int, description: String?, failingUrl: String?) {
            showErrorSnackBar(errorCode, description)
            Log.e(TAG, "Error $errorCode in WebView: $description")
        }

        @RequiresApi(Build.VERSION_CODES.M)
        override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
            val message = error?.description
            val code = error?.errorCode
            showErrorSnackBar(code, message)
            Log.e(TAG, "Error $code in WebView: $message")
        }

        @SuppressWarnings("DEPRECATION")
        override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
            val urlString = url ?: ""
            val fragment = urlString.toUri().fragment ?: return false
            parseOAuthToken(fragment)
            return false
        }

        @RequiresApi(Build.VERSION_CODES.N)
        override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
            val fragment = request?.url?.fragment ?: return false
            parseOAuthToken(fragment)
            return false
        }

        private fun parseOAuthToken(fragment: String) {
            if (fragment.startsWith("access_token=")) {
                val token = fragment.substringAfter("access_token=").substringBefore("&scope=")
                lifecycleScope.launchWhenResumed {
                    val result = twitchApi.validateUser(token)
                    val successful = saveLoginDetails(token, result)

                    with(findNavController()) {
                        previousBackStackEntry?.savedStateHandle?.set(MainFragment.LOGIN_REQUEST_KEY, successful)
                        navigateUp()
                    }
                }
            }
        }

        private fun saveLoginDetails(oAuth: String, validateDto: UserDtos.ValidateUser?): Boolean {
            return when {
                validateDto == null || validateDto.login.isBlank() -> false
                else -> {
                    dankChatPreferenceStore.apply {
                        oAuthKey = "oauth:$oAuth"
                        userName = validateDto.login.toLowerCase(Locale.getDefault())
                        userIdString = validateDto.userId
                    }
                    true
                }
            }
        }

        private fun showErrorSnackBar(errorCode: Int?, errorDescription: CharSequence?) {
            val rootView = activity?.findViewById<View>(android.R.id.content) ?: return
            Snackbar.make(rootView, "Error $errorCode: $errorDescription", Snackbar.LENGTH_LONG).show()
        }
    }

    companion object {
        private val TAG = LoginFragment::class.java.simpleName
    }
}
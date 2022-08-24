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
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.flxrs.dankchat.R
import com.flxrs.dankchat.data.api.ApiManager
import com.flxrs.dankchat.databinding.LoginFragmentBinding
import com.flxrs.dankchat.main.MainFragment
import com.flxrs.dankchat.preferences.DankChatPreferenceStore
import com.flxrs.dankchat.utils.extensions.collectFlow
import com.flxrs.dankchat.utils.extensions.setupDarkTheme
import com.flxrs.dankchat.utils.extensions.showLongSnackbar
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class LoginFragment : Fragment() {

    private var bindingRef: LoginFragmentBinding? = null
    private val binding get() = bindingRef!!
    private val loginViewModel: LoginViewModel by viewModels()

    @Inject
    lateinit var dankChatPreferences: DankChatPreferenceStore

    @Inject
    lateinit var apiManager: ApiManager

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        bindingRef = LoginFragmentBinding.inflate(inflater, container, false)
        binding.webview.apply {
            with(settings) {
                javaScriptEnabled = true
                setSupportZoom(true)
                setupDarkTheme(resources)
            }

            clearCache(true)
            clearFormData()

            webViewClient = TwitchAuthClient()
            loadUrl(ApiManager.LOGIN_URL)
        }

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        (requireActivity() as AppCompatActivity).apply {
            setSupportActionBar(binding.loginToolbar)
            supportActionBar?.apply {
                setDisplayHomeAsUpEnabled(true)
                title = getString(R.string.login_title)
            }
        }
        collectFlow(loginViewModel.events) { (successful) ->
            with(findNavController()) {
                runCatching {
                    val handle = previousBackStackEntry?.savedStateHandle ?: return@collectFlow
                    handle[MainFragment.LOGIN_REQUEST_KEY] = successful
                }
                navigateUp()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        bindingRef = null
    }

    private inner class TwitchAuthClient : WebViewClient() {
        @SuppressWarnings("DEPRECATION")
        override fun onReceivedError(view: WebView?, errorCode: Int, description: String?, failingUrl: String?) {
            bindingRef?.root?.showLongSnackbar("Error $errorCode: $description")
            Log.e(TAG, "Error $errorCode in WebView: $description")
        }

        @RequiresApi(Build.VERSION_CODES.M)
        override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
            val message = error?.description
            val code = error?.errorCode
            bindingRef?.root?.showLongSnackbar("Error $code: $message")
            Log.e(TAG, "Error $code in WebView: $message")
        }

        @SuppressWarnings("DEPRECATION")
        override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
            val urlString = url ?: ""
            val fragment = urlString.toUri().fragment ?: return false
            loginViewModel.parseToken(fragment)
            return false
        }

        @RequiresApi(Build.VERSION_CODES.N)
        override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
            val fragment = request?.url?.fragment ?: return false
            loginViewModel.parseToken(fragment)
            return false
        }
    }

    companion object {
        private val TAG = LoginFragment::class.java.simpleName
    }
}
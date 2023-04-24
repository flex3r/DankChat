package com.flxrs.dankchat.login

import android.annotation.SuppressLint
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.addCallback
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.net.toUri
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.flxrs.dankchat.R
import com.flxrs.dankchat.databinding.LoginFragmentBinding
import com.flxrs.dankchat.main.MainFragment
import com.flxrs.dankchat.preferences.DankChatPreferenceStore
import com.flxrs.dankchat.utils.extensions.collectFlow
import com.flxrs.dankchat.utils.extensions.showLongSnackbar
import com.flxrs.dankchat.utils.insets.RootViewDeferringInsetsCallback
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class LoginFragment : Fragment() {

    private var bindingRef: LoginFragmentBinding? = null
    private val binding get() = bindingRef!!
    private val loginViewModel: LoginViewModel by viewModels()

    @Inject
    lateinit var dankChatPreferences: DankChatPreferenceStore

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        bindingRef = LoginFragmentBinding.inflate(inflater, container, false)
        binding.webview.apply {
            with(settings) {
                javaScriptEnabled = true
                setSupportZoom(true)
            }

            clearCache(true)
            clearFormData()

            webViewClient = TwitchAuthClient()
            loadUrl(loginViewModel.loginUrl)
        }

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val deferringInsetsListener = RootViewDeferringInsetsCallback(
            persistentInsetTypes = WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout(),
            deferredInsetTypes = WindowInsetsCompat.Type.ime()
        )
        ViewCompat.setWindowInsetsAnimationCallback(binding.root, deferringInsetsListener)
        ViewCompat.setOnApplyWindowInsetsListener(binding.root, deferringInsetsListener)

        (requireActivity() as AppCompatActivity).apply {
            binding.loginToolbar.setNavigationOnClickListener { showCancelLoginDialog() }
            onBackPressedDispatcher.addCallback(viewLifecycleOwner) {
                when {
                    binding.webview.canGoBack() -> binding.webview.goBack()
                    else                        -> showCancelLoginDialog()
                }
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

    private fun showCancelLoginDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.confirm_login_cancel_title)
            .setMessage(R.string.confirm_login_cancel_message)
            .setPositiveButton(R.string.confirm_login_cancel_positive_button) { _, _ -> findNavController().popBackStack() }
            .setNegativeButton(R.string.dialog_dismiss) { _, _ -> }
            .create().show()
    }

    @Suppress("OVERRIDE_DEPRECATION")
    private inner class TwitchAuthClient : WebViewClient() {
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

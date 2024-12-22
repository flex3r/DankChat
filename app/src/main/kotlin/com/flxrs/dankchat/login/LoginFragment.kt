package com.flxrs.dankchat.login

import android.annotation.SuppressLint
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
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
import androidx.core.view.MenuProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.navigation.fragment.findNavController
import com.flxrs.dankchat.R
import com.flxrs.dankchat.databinding.LoginFragmentBinding
import com.flxrs.dankchat.main.MainFragment
import com.flxrs.dankchat.preferences.DankChatPreferenceStore
import com.flxrs.dankchat.utils.extensions.collectFlow
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.transition.MaterialSharedAxis
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        exitTransition = MaterialSharedAxis(MaterialSharedAxis.X, true)
        reenterTransition = MaterialSharedAxis(MaterialSharedAxis.X, false)
        enterTransition = MaterialSharedAxis(MaterialSharedAxis.X, true)
        returnTransition = MaterialSharedAxis(MaterialSharedAxis.X, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemInsets = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemInsets.left, systemInsets.top, systemInsets.right, systemInsets.bottom)
            ViewCompat.onApplyWindowInsets(v, insets)
        }

        (requireActivity() as AppCompatActivity).apply {
            binding.loginToolbar.setNavigationOnClickListener { showCancelLoginDialog() }
            onBackPressedDispatcher.addCallback(viewLifecycleOwner) {
                when {
                    binding.webview.canGoBack() -> binding.webview.goBack()
                    else                        -> showCancelLoginDialog()
                }
            }
            addMenuProvider(menuProvider, viewLifecycleOwner, Lifecycle.State.RESUMED)
            setSupportActionBar(binding.loginToolbar)
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

    private val menuProvider = object : MenuProvider {
        override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
            menuInflater.inflate(R.menu.login_menu, menu)
        }

        override fun onPrepareMenu(menu: Menu) {
            val isDefaultZoom = binding.webview.settings.textZoom == 100
            menu.findItem(R.id.zoom_out)?.isVisible = isDefaultZoom
            menu.findItem(R.id.zoom_in)?.isVisible = !isDefaultZoom
        }

        override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
            when (menuItem.itemId) {
                R.id.zoom_in  -> binding.webview.settings.textZoom = 100
                R.id.zoom_out -> binding.webview.settings.textZoom = 50
                else          -> return false
            }
            activity?.invalidateMenu()
            return true
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
            //bindingRef?.root?.showLongSnackbar("Error $errorCode: $description")
            Log.e(TAG, "Error $errorCode in WebView: $description")
        }

        @RequiresApi(Build.VERSION_CODES.M)
        override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
            val message = error?.description
            val code = error?.errorCode
            //bindingRef?.root?.showLongSnackbar("Error $code: $message")
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

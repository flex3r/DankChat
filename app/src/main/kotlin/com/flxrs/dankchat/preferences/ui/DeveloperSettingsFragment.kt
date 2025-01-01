package com.flxrs.dankchat.preferences.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import androidx.core.view.doOnPreDraw
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.navigation.fragment.findNavController
import androidx.preference.Preference
import com.flxrs.dankchat.R
import com.flxrs.dankchat.databinding.CustomLoginBottomsheetBinding
import com.flxrs.dankchat.databinding.EditDialogBinding
import com.flxrs.dankchat.databinding.RmHostBottomsheetBinding
import com.flxrs.dankchat.databinding.SettingsFragmentBinding
import com.flxrs.dankchat.preferences.DankChatPreferenceStore
import com.flxrs.dankchat.preferences.ui.customlogin.CustomLoginState
import com.flxrs.dankchat.preferences.ui.customlogin.CustomLoginViewModel
import com.flxrs.dankchat.utils.extensions.collectFlow
import com.flxrs.dankchat.utils.extensions.showRestartRequired
import com.flxrs.dankchat.utils.extensions.truncate
import com.flxrs.dankchat.utils.extensions.withTrailingSlash
import com.flxrs.dankchat.utils.extensions.withoutOAuthPrefix
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputLayout
import com.google.android.material.transition.MaterialFadeThrough
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel
import kotlin.math.roundToInt

class DeveloperSettingsFragment : MaterialPreferenceFragmentCompat() {

    private var bottomSheetDialog: BottomSheetDialog? = null
    private var customLoginBinding: CustomLoginBottomsheetBinding? = null
    private val customLoginViewModel: CustomLoginViewModel by viewModel()

    private val dankChatPreferenceStore: DankChatPreferenceStore by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enterTransition = MaterialFadeThrough()
        returnTransition = MaterialFadeThrough()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        postponeEnterTransition()
        (view.parent as? View)?.doOnPreDraw { startPostponedEnterTransition() }

        SettingsFragmentBinding.bind(view).apply {
            settingsToolbar.setNavigationOnClickListener { findNavController().navigateUp() }
            settingsToolbar.title = getString(R.string.preference_developer_header)
        }

        findPreference<Preference>(getString(R.string.preference_rm_host_key))?.apply {
            setOnPreferenceClickListener { showRmHostPreference(view) }
        }
        findPreference<Preference>(getString(R.string.preference_custom_login_key))?.apply {
            setOnPreferenceClickListener { showCustomLoginSheet(view) }
        }
        collectFlow(customLoginViewModel.customLoginState) {
            val loginBinding = customLoginBinding ?: return@collectFlow
            loginBinding.tokenInputLayout.error = null

            if (it !is CustomLoginState.Loading) {
                loginBinding.verifyLoadingBar.isVisible = false
                loginBinding.verifyLogin.isVisible = true
            }

            when (it) {
                CustomLoginState.Default          -> Unit
                is CustomLoginState.Failure       -> loginBinding.tokenInputLayout.error = getString(R.string.custom_login_error_fallback, it.error.truncate(maxLength = 120))
                CustomLoginState.TokenEmpty       -> loginBinding.tokenInputLayout.error = getString(R.string.custom_login_error_empty_token)
                CustomLoginState.TokenInvalid     -> loginBinding.tokenInputLayout.error = getString(R.string.custom_login_error_invalid_token)
                is CustomLoginState.MissingScopes -> {
                    val scopes = it.missingScopes.joinToString()
                    MaterialAlertDialogBuilder(view.context)
                        .setTitle(R.string.custom_login_missing_scopes_title)
                        .setMessage(getString(R.string.custom_login_missing_scopes_text, scopes))
                        .setPositiveButton(R.string.custom_login_missing_scopes_continue) { _, _ ->
                            customLoginViewModel.saveLogin(it.token, it.validation)
                            bottomSheetDialog?.dismiss()
                            view.showRestartRequired()
                        }
                        .setNegativeButton(R.string.dialog_cancel) { _, _ ->
                            loginBinding.tokenInputLayout.error = getString(R.string.custom_login_error_missing_scopes, scopes.truncate(maxLength = 120))
                        }
                        .show()
                }

                CustomLoginState.Loading          -> {
                    loginBinding.verifyLoadingBar.isVisible = true
                    loginBinding.verifyLogin.isVisible = false
                }

                CustomLoginState.Validated        -> {
                    bottomSheetDialog?.dismiss()
                    view.showRestartRequired()
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        bottomSheetDialog?.dismiss()
        bottomSheetDialog = null
        customLoginBinding = null
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.developer_settings, rootKey)
    }

    private fun showRmHostPreference(root: View): Boolean {
        val context = root.context
        val windowHeight = resources.displayMetrics.heightPixels
        val peekHeight = (windowHeight * 0.6).roundToInt()
        val currentHost = dankChatPreferenceStore.customRmHost
        val binding = RmHostBottomsheetBinding.inflate(LayoutInflater.from(context), root as? ViewGroup, false).apply {
            rmHostInput.setText(currentHost)
            hostReset.setOnClickListener {
                rmHostInput.setText(dankChatPreferenceStore.resetRmHost())
            }
            rmHostSheet.updateLayoutParams {
                height = windowHeight
            }
        }

        bottomSheetDialog = BottomSheetDialog(context).apply {
            setContentView(binding.root)
            setOnDismissListener {
                val newHost = binding.rmHostInput.text
                    ?.toString()
                    ?.withTrailingSlash ?: return@setOnDismissListener

                if (newHost != currentHost) {
                    dankChatPreferenceStore.customRmHost = newHost
                    view?.showRestartRequired()
                }
                bottomSheetDialog = null
            }
            behavior.isFitToContents = false
            behavior.peekHeight = peekHeight
            show()
        }

        return true
    }

    private fun showCustomLoginSheet(root: View): Boolean {
        val context = root.context
        val windowHeight = resources.displayMetrics.heightPixels
        val peekHeight = (windowHeight * 0.6).roundToInt()
        val binding = CustomLoginBottomsheetBinding.inflate(LayoutInflater.from(context), root as? ViewGroup, false).apply {
            tokenInput.setText(dankChatPreferenceStore.oAuthKey?.withoutOAuthPrefix)

            customLoginSheet.updateLayoutParams {
                height = windowHeight
            }
            verifyLogin.setOnClickListener {
                customLoginViewModel.validateCustomLogin(oAuthToken = tokenInput.text?.toString().orEmpty())
            }

            loginShowScopes.setOnClickListener {
                val layout = EditDialogBinding.inflate(LayoutInflater.from(it.context)).apply {
                    val scopes = customLoginViewModel.getScopes()
                    dialogEdit.setText(scopes)
                    dialogEdit.isSingleLine = false
                    dialogEditLayout.endIconMode = TextInputLayout.END_ICON_CUSTOM
                    dialogEditLayout.endIconDrawable = ContextCompat.getDrawable(it.context, R.drawable.ic_copy)
                    dialogEditLayout.setEndIconOnClickListener {
                        val clipData = ClipData.newPlainText("Login scopes", scopes)
                        context.getSystemService<ClipboardManager>()?.setPrimaryClip(clipData)
                    }
                }
                MaterialAlertDialogBuilder(it.context)
                    .setTitle(R.string.custom_login_required_scopes)
                    .setView(layout.root)
                    .setNeutralButton(R.string.dialog_dismiss) { _, _ -> }
                    .show()
            }

            loginReset.setOnClickListener {
                tokenInputLayout.error = null
                tokenInput.setText(dankChatPreferenceStore.oAuthKey.orEmpty())
            }
        }

        customLoginBinding = binding
        bottomSheetDialog = BottomSheetDialog(context).apply {
            setContentView(binding.root)
            setOnDismissListener {
                bottomSheetDialog = null
                customLoginBinding = null
            }
            behavior.isFitToContents = false
            behavior.peekHeight = peekHeight
            show()
        }

        return true
    }
}

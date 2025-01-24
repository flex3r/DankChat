package com.flxrs.dankchat.preferences.tools

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.view.doOnPreDraw
import androidx.fragment.app.Fragment
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.findNavController
import com.flxrs.dankchat.theme.DankChatTheme
import com.google.android.material.transition.MaterialFadeThrough

class ToolsSettingsFragment : Fragment() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enterTransition = MaterialFadeThrough()
        returnTransition = MaterialFadeThrough()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        postponeEnterTransition()
        (view.parent as? View)?.doOnPreDraw { startPostponedEnterTransition() }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                DankChatTheme {
                    val navController = rememberNavController()
                    NavHost(
                        navController = navController,
                        startDestination = ToolsSettingsRoute.ToolsSettings,
                        enterTransition = { fadeIn() + scaleIn(initialScale = 0.92f) },
                        exitTransition = { fadeOut() },
                        popEnterTransition = { fadeIn() + scaleIn(initialScale = 0.92f) },
                        popExitTransition = { fadeOut() },
                    ) {
                        composable<ToolsSettingsRoute.ToolsSettings> {
                            ToolsSettingsScreen(
                                onNavToImageUploader = { navController.navigate(ToolsSettingsRoute.ImageUploader) },
                                onNavToTTSUserIgnoreList = { navController.navigate(ToolsSettingsRoute.TTSUserIgnoreList) },
                                onNavBack = { findNavController().popBackStack() },
                            )
                        }
                        composable<ToolsSettingsRoute.ImageUploader> {
                            /*ImageUploaderScreen(
                                onNavBack = { navController.popBackStack() },
                            )*/
                        }
                        composable<ToolsSettingsRoute.TTSUserIgnoreList> {
                            /*TTSUserIgnoreListScreen(
                                onNavBack = { navController.popBackStack() },
                            )*/
                        }
                    }
                }
            }
        }
    }

//    private fun showImageUploaderPreference(root: View): Boolean {
//        val context = root.context
//        val windowHeight = resources.displayMetrics.heightPixels
//        val peekHeight = (windowHeight * 0.6).roundToInt()
//        val binding = UploaderBottomsheetBinding.inflate(LayoutInflater.from(context), root as? ViewGroup, false).apply {
//            uploader = dankChatPreferenceStore.customImageUploader
//            uploaderReset.setOnClickListener {
//                MaterialAlertDialogBuilder(context)
//                    .setTitle(R.string.reset_media_uploader_dialog_title)
//                    .setMessage(R.string.reset_media_uploader_dialog_message)
//                    .setPositiveButton(R.string.reset_media_uploader_dialog_positive) { _, _ -> uploader = dankChatPreferenceStore.resetImageUploader() }
//                    .setNegativeButton(R.string.dialog_cancel) { _, _ -> }
//                    .create().show()
//            }
//            uploaderSheet.updateLayoutParams {
//                height = windowHeight
//            }
//            ViewCompat.setOnApplyWindowInsetsListener(uploaderSheet) { v, insets ->
//                v.updatePadding(bottom = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom)
//                WindowInsetsCompat.CONSUMED
//            }
//            LinkifyCompat.addLinks(uploaderDescription, Linkify.WEB_URLS)
//        }
//
//        bottomSheetDialog = BottomSheetDialog(context).apply {
//            setContentView(binding.root)
//            setOnDismissListener {
//                binding.uploader?.let { uploader ->
//                    val validated = uploader.copy(
//                        headers = uploader.headers?.takeIf { it.isNotBlank() },
//                        imageLinkPattern = uploader.imageLinkPattern?.takeIf { it.isNotBlank() },
//                        deletionLinkPattern = uploader.deletionLinkPattern?.takeIf { it.isNotBlank() },
//                    )
//                    dankChatPreferenceStore.customImageUploader = validated
//                } ?: dankChatPreferenceStore.resetImageUploader()
//            }
//            behavior.isFitToContents = false
//            behavior.peekHeight = peekHeight
//            show()
//        }
//
//        return true
//    }
//
//    private fun showTtsIgnoreListPreference(root: View, key: String, sharedPreferences: SharedPreferences): Boolean {
//        val context = root.context
//        val windowHeight = resources.displayMetrics.heightPixels
//        val peekHeight = (windowHeight * 0.6).roundToInt()
//        val items = runCatching {
//            sharedPreferences
//                .getStringSet(key, emptySet())
//                .orEmpty()
//                .map { TtsIgnoreItem.Entry(it) }
//                .plus(TtsIgnoreItem.AddEntry)
//        }.getOrDefault(emptyList())
//
//        val ttsIgnoreListAdapter = TtsIgnoreListAdapter(items.toMutableList())
//        val binding = TtsIgnoreListBottomsheetBinding.inflate(LayoutInflater.from(context), root as? ViewGroup, false).apply {
//            ttsIgnoreList.adapter = ttsIgnoreListAdapter
//            ttsIgnoreListSheet.updateLayoutParams {
//                height = windowHeight
//            }
//            ViewCompat.setOnApplyWindowInsetsListener(ttsIgnoreList) { v, insets ->
//                v.updatePadding(bottom = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom)
//                WindowInsetsCompat.CONSUMED
//            }
//        }
//
//        bottomSheetDialog = BottomSheetDialog(context).apply {
//            setContentView(binding.root)
//            setOnDismissListener {
//                val stringSet = ttsIgnoreListAdapter.items
//                    .filterIsInstance<TtsIgnoreItem.Entry>()
//                    .filter { it.user.isNotBlank() }
//                    .map { it.user }
//                    .toSet()
//
//                sharedPreferences.edit { putStringSet(key, stringSet) }
//            }
//            behavior.isFitToContents = false
//            behavior.peekHeight = peekHeight
//            show()
//        }
//
//        return true
//    }
}

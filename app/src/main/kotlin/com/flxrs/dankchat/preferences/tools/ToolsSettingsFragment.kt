package com.flxrs.dankchat.preferences.tools

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.view.doOnPreDraw
import androidx.fragment.app.Fragment
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.findNavController
import com.flxrs.dankchat.preferences.tools.tts.TTSUserIgnoreListScreen
import com.flxrs.dankchat.preferences.tools.upload.ImageUploaderScreen
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
                    val backStack = navController.currentBackStackEntryAsState().value
                    SideEffect {
                        Log.d("XXX", "${backStack?.destination?.route}")
                    }
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
                            ImageUploaderScreen(
                                onNavBack = { navController.popBackStack() },
                            )
                        }
                        composable<ToolsSettingsRoute.TTSUserIgnoreList> {
                            TTSUserIgnoreListScreen(
                                onNavBack = { navController.popBackStack() },
                            )
                        }
                    }
                }
            }
        }
    }
}

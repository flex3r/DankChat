package com.flxrs.dankchat.preferences.notifications

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
import androidx.navigation.fragment.findNavController
import com.flxrs.dankchat.preferences.notifications.highlights.HighlightsScreen
import com.flxrs.dankchat.preferences.notifications.ignores.IgnoresScreen
import com.flxrs.dankchat.theme.DankChatTheme
import com.google.android.material.transition.MaterialFadeThrough

class NotificationsSettingsFragment : Fragment() {

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
                        startDestination = NotificationsSettingsRoute.NotificationsSettings,
                        enterTransition = { fadeIn() + scaleIn(initialScale = 0.92f) },
                        exitTransition = { fadeOut() },
                        popEnterTransition = { fadeIn() + scaleIn(initialScale = 0.92f) },
                        popExitTransition = { fadeOut() },
                    ) {
                        composable<NotificationsSettingsRoute.NotificationsSettings> {
                            NotificationsSettingsScreen(
                                onNavToHighlights = { navController.navigate(NotificationsSettingsRoute.Highlights) },
                                onNavToIgnores = { navController.navigate(NotificationsSettingsRoute.Ignores) },
                                onNavBack = { findNavController().popBackStack() },
                            )
                        }
                        composable<NotificationsSettingsRoute.Highlights> {
                            HighlightsScreen(
                                onNavBack = { navController.popBackStack() },
                            )
                        }
                        composable<NotificationsSettingsRoute.Ignores> {
                            IgnoresScreen(
                                onNavBack = { navController.popBackStack() },
                            )
                        }
                    }
                }
            }
        }
    }
}

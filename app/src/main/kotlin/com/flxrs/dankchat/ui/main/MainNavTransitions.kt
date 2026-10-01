package com.flxrs.dankchat.ui.main

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute

typealias NavEnterTransition = @JvmSuppressWildcards AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition
typealias NavExitTransition = @JvmSuppressWildcards AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition

class MainNavTransitions(
    val enter: NavEnterTransition,
    val exit: NavExitTransition,
    val popEnter: NavEnterTransition,
    val popExit: NavExitTransition,
) {
    companion object {
        val Fade =
            MainNavTransitions(
                enter = { fadeIn(animationSpec = tween(220, delayMillis = 90)) },
                exit = { fadeOut(animationSpec = tween(90)) },
                popEnter = { fadeIn(animationSpec = tween(220, delayMillis = 90)) },
                popExit = { fadeOut(animationSpec = tween(90)) },
            )

        val Slide =
            MainNavTransitions(
                enter = { slideInHorizontally(initialOffsetX = { it / 3 }, animationSpec = tween(300)) + fadeIn(animationSpec = tween(200)) },
                exit = { scaleOut(targetScale = 0.92f, animationSpec = tween(300)) + fadeOut(animationSpec = tween(200)) },
                popEnter = { slideInHorizontally(initialOffsetX = { -it / 3 }, animationSpec = tween(300)) + fadeIn(animationSpec = tween(300)) },
                popExit = { scaleOut(targetScale = 0.92f, animationSpec = tween(300)) + fadeOut(animationSpec = tween(200)) },
            )

        fun forDestination(destination: NavDestination): MainNavTransitions = when {
            destination.hasRoute<Onboarding>() || destination.hasRoute<Login>() -> Fade
            else -> Slide
        }
    }
}

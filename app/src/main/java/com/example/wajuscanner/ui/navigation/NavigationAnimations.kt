package com.example.wajuscanner.ui.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.navigation.NavBackStackEntry

/**
 * Navigation animation specs for the app.
 *
 * Push (forward): new screen slides in from the right, old screen slides out to the left.
 * Pop (backward): returning screen slides in from the left, current screen slides out to the right.
 *
 * Keep the durations short (220 ms) so the navigation feels responsive without
 * holding the user back; the fade tames the leading/trailing edge so the slide
 * does not look like a cut. Values were chosen to match modern Android defaults
 * and feel native to the platform.
 */
object NavigationAnimations {
    private const val DURATION_MS = 220

    /**
     * Enter transition when navigating forward (push) to a new destination.
     * The new screen slides in from the right.
     */
    fun slideInFromRight(): EnterTransition =
        slideInHorizontally(
            animationSpec = tween(durationMillis = DURATION_MS),
            initialOffsetX = { fullWidth -> fullWidth }
        ) + fadeIn(animationSpec = tween(durationMillis = DURATION_MS))

    /**
     * Exit transition when navigating forward (push). The current screen
     * slides out to the left, making room for the new screen coming in.
     */
    fun slideOutToLeft(): ExitTransition =
        slideOutHorizontally(
            animationSpec = tween(durationMillis = DURATION_MS),
            targetOffsetX = { fullWidth -> -fullWidth / 4 }
        ) + fadeOut(animationSpec = tween(durationMillis = DURATION_MS))

    /**
     * Enter transition when popping back to a previous destination.
     * The returning screen slides in from the left.
     */
    fun slideInFromLeft(): EnterTransition =
        slideInHorizontally(
            animationSpec = tween(durationMillis = DURATION_MS),
            initialOffsetX = { fullWidth -> -fullWidth / 4 }
        ) + fadeIn(animationSpec = tween(durationMillis = DURATION_MS))

    /**
     * Exit transition when popping. The current screen slides out to the right.
     */
    fun slideOutToRight(): ExitTransition =
        slideOutHorizontally(
            animationSpec = tween(durationMillis = DURATION_MS),
            targetOffsetX = { fullWidth -> fullWidth }
        ) + fadeOut(animationSpec = tween(durationMillis = DURATION_MS))
}

typealias EnterTransitionBuilder =
    AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition

typealias ExitTransitionBuilder =
    AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition

val enterTransition: EnterTransitionBuilder = { NavigationAnimations.slideInFromRight() }
val exitTransition: ExitTransitionBuilder = { NavigationAnimations.slideOutToLeft() }
val popEnterTransition: EnterTransitionBuilder = { NavigationAnimations.slideInFromLeft() }
val popExitTransition: ExitTransitionBuilder = { NavigationAnimations.slideOutToRight() }
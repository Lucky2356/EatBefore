package com.eatbefore.ui

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.navigation.NavBackStackEntry
import com.eatbefore.core.designsystem.theme.Motion
import com.eatbefore.navigation.isTopLevelRoute

/*
 * How one screen becomes another.
 *
 * There was nothing here at all: a screen was replaced between two frames, so opening a
 * product and returning from it looked identical, and after a tap it took a moment to work
 * out whether the app had gone forward or back. Direction is the whole point — going in
 * slides one way, coming out slides the other, and the eye reads it without being told.
 *
 * Switching tabs is a different move and gets a different shape. The five bottom-bar
 * destinations are siblings, not a stack; sliding between them would claim a left-to-right
 * order that does not exist, so they cross-fade with a slight scale instead.
 *
 * The slide is a small fraction of the width rather than the whole of it. A full-width
 * slide on a 6-inch screen is a lurch, and it makes every navigation feel slower than it is.
 */

private val AnimatedContentTransitionScope<NavBackStackEntry>.betweenTabs: Boolean
    get() = isTopLevelRoute(initialState.destination.route) &&
        isTopLevelRoute(targetState.destination.route)

/** Arriving on a screen pushed on top of the previous one. */
fun AnimatedContentTransitionScope<NavBackStackEntry>.forwardEnter(): EnterTransition =
    if (betweenTabs) tabEnter() else slideEnter(fromRight = true)

/** The screen being covered. */
fun AnimatedContentTransitionScope<NavBackStackEntry>.forwardExit(): ExitTransition =
    if (betweenTabs) tabExit() else slideExit(toRight = false)

/** Coming back to the screen underneath. */
fun AnimatedContentTransitionScope<NavBackStackEntry>.backEnter(): EnterTransition =
    if (betweenTabs) tabEnter() else slideEnter(fromRight = false)

/** The screen being dismissed. */
fun AnimatedContentTransitionScope<NavBackStackEntry>.backExit(): ExitTransition =
    if (betweenTabs) tabExit() else slideExit(toRight = true)

private fun slideEnter(fromRight: Boolean): EnterTransition =
    fadeIn(Motion.fadeIn()) + slideInHorizontally(Motion.screen()) { width ->
        (width * Motion.SCREEN_SLIDE_FRACTION).toInt().let { if (fromRight) it else -it }
    }

private fun slideExit(toRight: Boolean): ExitTransition =
    fadeOut(Motion.fadeOut()) + slideOutHorizontally(Motion.screen()) { width ->
        (width * Motion.SCREEN_SLIDE_FRACTION).toInt().let { if (toRight) it else -it }
    }

private fun tabEnter(): EnterTransition =
    fadeIn(Motion.fadeIn()) + scaleIn(Motion.fadeIn(), initialScale = TAB_SCALE)

private fun tabExit(): ExitTransition =
    fadeOut(Motion.fadeOut()) + scaleOut(Motion.fadeOut(), targetScale = TAB_SCALE)

/** Barely a scale. Enough to give the fade somewhere to come from, not enough to notice. */
private const val TAB_SCALE = 0.96f

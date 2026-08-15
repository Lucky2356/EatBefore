package com.eatbefore.core.designsystem.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.ui.unit.IntOffset

/**
 * The app's motion scale.
 *
 * Until now the app had no motion at all — not one animated value, appearance or screen
 * transition. Everything cut: a screen replaced the previous one between two frames, a
 * product deleted by an undoable action vanished with nothing to say it had gone, and the
 * list closed the gap instantly, so an accidental tap left no trace on screen to notice.
 * Motion here is not decoration; it is what makes a change legible as a change.
 *
 * Springs rather than durations for anything that moves or resizes: a spring picks up an
 * interrupting gesture from wherever it is, while a fixed-duration tween has to restart.
 * Tweens are kept only for pure fades, where there is no position to be interrupted.
 *
 * Not `MotionScheme` from material3: reaching it means opting the whole theme into
 * `MaterialExpressiveTheme`, which is experimental in 1.4.0, and what comes back is the
 * same handful of spring constants.
 */
object Motion {
    /** Small, frequent changes: a chip selecting, a card pressing, a colour crossing over. */
    fun <T> quick(): FiniteAnimationSpec<T> = spring(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMediumLow,
    )

    /** Things that move or resize on screen: list items, banners opening and closing. */
    fun <T> standard(): FiniteAnimationSpec<T> = spring(
        dampingRatio = Spring.DampingRatioLowBouncy,
        stiffness = Spring.StiffnessMediumLow,
    )

    /**
     * Position for items entering or leaving a list. Slightly stiffer than [standard] and
     * without bounce: a whole list overshooting at once looks like a wobble, not a spring.
     */
    fun listItem(): FiniteAnimationSpec<IntOffset> = spring(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMediumLow,
    )

    /** Fading in — short, and starting slowly so the arrival is not a flash. */
    fun <T> fadeIn(): FiniteAnimationSpec<T> = tween(durationMillis = FADE_IN_MS, easing = Emphasized)

    /** Fading out — quicker than fading in, because leaving needs no attention. */
    fun <T> fadeOut(): FiniteAnimationSpec<T> = tween(durationMillis = FADE_OUT_MS, easing = Emphasized)

    /** Sliding a screen in or out. Long enough to read direction, short enough not to wait. */
    fun <T> screen(): FiniteAnimationSpec<T> = tween(durationMillis = SCREEN_MS, easing = Emphasized)

    /** How far a screen slides, as a fraction of its width. A full width reads as a lurch. */
    const val SCREEN_SLIDE_FRACTION = 0.12f

    /** How much a card shrinks under a finger. */
    const val PRESSED_SCALE = 0.97f

    private const val FADE_IN_MS = 220
    private const val FADE_OUT_MS = 140
    private const val SCREEN_MS = 300

    /** Material's emphasized easing: leaves quickly, arrives gently. */
    private val Emphasized = CubicBezierEasing(0.2f, 0f, 0f, 1f)
}

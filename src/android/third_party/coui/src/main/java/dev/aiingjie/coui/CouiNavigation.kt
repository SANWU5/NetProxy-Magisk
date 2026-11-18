package dev.aiingjie.coui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.IntOffset

/**
 * ColorOS page push / pop. Push: the new page slides in from the end edge while the old page drifts back
 * by 30 % of the width and dims to 50 %. Pop reverses it. 350 ms on the COUI page curves.
 */
fun couiPageTransition(forward: Boolean): ContentTransform {
    val d = CouiMotion.PAGE
    return if (forward) {
        (slideInHorizontally(tween<IntOffset>(d, easing = CouiMotion.PageEnter)) { it }) togetherWith
            (slideOutHorizontally(tween<IntOffset>(d, easing = CouiMotion.PageExitBehind)) { -it * 3 / 10 } +
                fadeOut(tween(d, easing = CouiMotion.Ease), targetAlpha = 0.5f))
    } else {
        (slideInHorizontally(tween<IntOffset>(d, easing = CouiMotion.PageReturnBehind)) { -it * 3 / 10 } +
            fadeIn(tween(d, easing = CouiMotion.Ease), initialAlpha = 0.5f)) togetherWith
            (slideOutHorizontally(tween<IntOffset>(d, easing = CouiMotion.PageExit)) { it })
    }.apply { targetContentZIndex = if (forward) 1f else -1f }
}

/**
 * A minimal page stack with the ColorOS push / pop transition.
 *
 * [stack] holds the pushed routes; an empty list shows the root page (route ""). The system back gesture
 * calls [onBack] while the stack is not empty. [onRootVisible] reports whether the root page is on screen
 * with no dialog or bottom panel over it, which is when a floating dock should be visible.
 */
@Composable
fun CouiPageHost(
    stack: List<String>,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onRootVisible: ((Boolean) -> Unit)? = null,
    page: @Composable (route: String) -> Unit,
) {
    val overlay = LocalCouiOverlay.current
    val holder = rememberSaveableStateHolder()
    if (LocalOnBackPressedDispatcherOwner.current != null) BackHandler(enabled = stack.isNotEmpty(), onBack = onBack)
    val rootVisible = stack.isEmpty() && !overlay.isCovering
    LaunchedEffect(rootVisible) { onRootVisible?.invoke(rootVisible) }
    AnimatedContent(
        targetState = stack,
        modifier = modifier,
        transitionSpec = { couiPageTransition(targetState.size >= initialState.size) },
        contentKey = { it.lastOrNull() ?: "" },
        label = "coui-page",
    ) { current ->
        val route = current.lastOrNull() ?: ""
        holder.SaveableStateProvider(route.ifEmpty { "root" }) { page(route) }
    }
}

/** Root tabs cross-fade in 240 ms and each tab keeps its own scroll position. */
@Composable
fun CouiTabs(selected: Int, modifier: Modifier = Modifier, tab: @Composable (index: Int) -> Unit) {
    val holder = rememberSaveableStateHolder()
    Crossfade(selected, modifier, animationSpec = tween(CouiMotion.FAST, easing = CouiMotion.Ease), label = "coui-tab") { current ->
        holder.SaveableStateProvider("tab$current") { tab(current) }
    }
}

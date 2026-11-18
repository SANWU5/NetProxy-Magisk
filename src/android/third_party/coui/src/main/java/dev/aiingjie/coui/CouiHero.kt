package dev.aiingjie.coui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/**
 * The home-page hero: a card-shaped [CouiStarfield] with your content centered on it.
 *
 * It registers itself as a dark surface ([couiDarkSurface]), so when a [CouiLargeTitlePage] title scrolls over
 * it the title switches to a light ink automatically. Pass [frozenTime] (seconds) for static screenshots.
 */
@Composable
fun CouiHeroCard(
    modifier: Modifier = Modifier,
    height: Dp = 196.dp,
    colors: CouiStarfieldColors = CouiStarfieldDefaults.Navy,
    frozenTime: Float? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val time: () -> Float = if (frozenTime != null) {
        { frozenTime }
    } else {
        rememberCouiStarfieldTime()
    }
    Box(
        modifier.padding(horizontal = CouiDimens.cardMargin).fillMaxWidth().height(height).clip(CouiShapes.card)
            .background(colors.skyBottom).couiDarkSurface(),
        contentAlignment = Alignment.Center,
    ) {
        CouiStarfield(Modifier.matchParentSize(), colors, time = time)
        content()
    }
}

/**
 * The hero logo's tap response: squash to 86 % and wobble back on a soft spring, a short tilt shake, and a
 * light haptic tick. Apply it to any logo or illustration.
 */
fun Modifier.couiJellyTap(onTap: () -> Unit = {}): Modifier = composed {
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val squash = remember { Animatable(1f) }
    val tilt = remember { Animatable(0f) }
    this
        .graphicsLayer { scaleX = squash.value; scaleY = squash.value; rotationZ = tilt.value }
        .clickable(remember { MutableInteractionSource() }, indication = null) {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            scope.launch {
                squash.snapTo(.86f)
                squash.animateTo(1f, spring(dampingRatio = .32f, stiffness = 320f))
            }
            scope.launch {
                tilt.snapTo(0f)
                tilt.animateTo(0f, keyframes {
                    durationMillis = 560
                    -9f at 80
                    7f at 190
                    -4f at 300
                    2f at 410
                })
            }
            onTap()
        }
}

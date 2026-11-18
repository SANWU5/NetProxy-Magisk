package dev.aiingjie.coui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned

class CouiTones {
    internal val dark = mutableStateMapOf<Any, Rect>()

    fun darkBehind(area: Rect): Boolean {
        if (area.isEmpty) return false
        val need = area.width * area.height * 0.5f
        return dark.values.any { region ->
            val overlap = region.intersect(area)
            !overlap.isEmpty && overlap.width * overlap.height >= need
        }
    }
}

val LocalCouiTones = staticCompositionLocalOf<CouiTones?> { null }

fun Modifier.couiDarkSurface(): Modifier = composed {
    val tones = LocalCouiTones.current ?: return@composed Modifier
    val key = remember { Any() }
    DisposableEffect(tones) { onDispose { tones.dark.remove(key) } }
    Modifier.onGloballyPositioned { tones.dark[key] = it.boundsInRoot() }
}

class CouiToneProbe internal constructor(private val tones: CouiTones) {
    internal var area by mutableStateOf(Rect.Zero)
    val modifier: Modifier = Modifier.onGloballyPositioned { area = it.boundsInRoot() }
    internal val dark: State<Boolean> = derivedStateOf { tones.darkBehind(area) }
}

@Composable
internal fun rememberToneProbe(tones: CouiTones): CouiToneProbe = remember(tones) { CouiToneProbe(tones) }

@Composable
internal fun adaptiveInk(probe: CouiToneProbe, normal: Color, onDark: Color = Color(0xFFFCF6EF)): Color {
    val target = if (probe.dark.value) onDark else normal
    return animateColorAsState(target, tween(CouiMotion.FAST), label = "adaptive-ink").value
}

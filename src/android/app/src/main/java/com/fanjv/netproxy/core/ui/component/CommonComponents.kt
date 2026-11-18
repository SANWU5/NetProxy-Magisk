package com.fanjv.netproxy.core.ui.component

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.fanjv.netproxy.core.ui.theme.LocalEnableBlur
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.blur.BlendColorEntry
import top.yukonga.miuix.kmp.blur.BlurColors
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.blur.textureBlur
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.shader.isRenderEffectSupported
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme

/**
 * 跨屏幕复用的通用小组件：返回按钮、模糊栏容器、状态标签、警告卡片。
 */

/**
 * 详情页通用的顶栏返回按钮；在 RTL 布局下水平镜像图标。
 */
@Composable
fun BackIconButton(onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        val layoutDirection = LocalLayoutDirection.current
        Icon(
            modifier = Modifier.graphicsLayer {
                if (layoutDirection == LayoutDirection.Rtl) scaleX = -1f
            },
            imageVector = MiuixIcons.Back,
            contentDescription = null,
            tint = colorScheme.onSurface,
        )
    }
}

@Composable
internal fun rememberCommitAction(
    commit: suspend () -> Boolean,
): (() -> Unit) -> Unit {
    val scope = rememberCoroutineScope()
    var committing by remember { mutableStateOf(false) }
    return { action ->
        if (!committing) {
            committing = true
            scope.launch {
                try { if (commit()) action() } finally { committing = false }
            }
        }
    }
}

@Composable
internal fun rememberCommitOnLeave(requestCommit: () -> Unit): (() -> Unit) -> Unit {
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { requestCommit() }
    var leaving by remember { mutableStateOf(false) }
    return { action ->
        if (!leaving) {
            leaving = true
            requestCommit()
            action()
        }
    }
}

/** 当模糊可用时创建模糊背景层（[LayerBackdrop]），否则返回 null。 */
@Composable
fun rememberBlurBackdrop(enableBlur: Boolean = LocalEnableBlur.current): LayerBackdrop? {
    if (!enableBlur || !isRenderEffectSupported()) return null
    val surfaceColor = colorScheme.surface
    return rememberLayerBackdrop {
        drawRect(surfaceColor)
        drawContent()
    }
}

/** 顶/底栏的模糊容器：有背景层时做纹理模糊，否则原样透传。 */
@Composable
fun BlurredBar(
    backdrop: LayerBackdrop?,
    content: @Composable () -> Unit,
) {
    val surfaceColor = colorScheme.surface
    Box(
        modifier = if (backdrop != null) {
            Modifier.textureBlur(
                backdrop = backdrop,
                shape = RectangleShape,
                blurRadius = 25f,
                colors = BlurColors(
                    blendColors = listOf(
                        BlendColorEntry(color = surfaceColor.copy(alpha = 0.8f)),
                    ),
                ),
            )
        } else {
            Modifier
        }
    ) {
        content()
    }
}

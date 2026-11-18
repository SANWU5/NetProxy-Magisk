package dev.aiingjie.coui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import androidx.compose.foundation.border
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.draw.drawBehind

data class CouiDialogButton(val text: String, val kind: Kind = Kind.Normal, val onClick: () -> Unit = {}) {
    enum class Kind { Primary, Normal, Danger }
}

internal class DialogSpec(
    val title: String?, val message: String?, val buttons: List<CouiDialogButton>, val cancelable: Boolean,
    val onDismiss: () -> Unit, val content: (@Composable () -> Unit)?,
)

internal class PanelSpec(val title: String?, val withCards: Boolean, val onDismiss: () -> Unit, val content: @Composable ColumnScope.() -> Unit)

internal class MenuSpec(val anchor: Rect, val items: List<String>, val selected: Int, val onSelect: (Int) -> Unit)

internal class SnackSpec(val serial: Int, val text: String, val action: String?, val onAction: (() -> Unit)?)

@Stable
class CouiOverlayState {
    internal var dialog by mutableStateOf<DialogSpec?>(null)
    internal var panel by mutableStateOf<PanelSpec?>(null)
    internal var menu by mutableStateOf<MenuSpec?>(null)
    internal var snack by mutableStateOf<SnackSpec?>(null)
    private var serial = 0

    fun showDialog(
        title: String?,
        message: String? = null,
        buttons: List<CouiDialogButton>,
        cancelable: Boolean = true,
        onDismiss: () -> Unit = {},
        content: (@Composable () -> Unit)? = null,
    ) { dialog = DialogSpec(title, message, buttons, cancelable, onDismiss, content) }

    fun dismissDialog() { dialog?.onDismiss?.invoke(); dialog = null }

    fun showPanel(title: String?, withCards: Boolean = true, onDismiss: () -> Unit = {}, content: @Composable ColumnScope.() -> Unit) {
        panel = PanelSpec(title, withCards, onDismiss, content)
    }

    fun dismissPanel() { panel?.onDismiss?.invoke(); panel = null }

    fun showMenu(anchor: Rect, items: List<String>, selected: Int, onSelect: (Int) -> Unit) { menu = MenuSpec(anchor, items, selected, onSelect) }

    fun dismissMenu() { menu = null }

    fun showSnack(text: String, action: String? = null, onAction: (() -> Unit)? = null) {
        serial += 1
        snack = SnackSpec(serial, text, action, onAction)
    }

    fun dismissSnack() { snack = null }

    /** True while a dialog or bottom panel is showing, for example to hide a floating dock meanwhile. */
    val isCovering: Boolean get() = dialog != null || panel != null
}

val LocalCouiOverlay = staticCompositionLocalOf { CouiOverlayState() }

@Composable
fun CouiOverlayHost(state: CouiOverlayState = remember { CouiOverlayState() }, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalCouiOverlay provides state) {
        Box(Modifier.fillMaxSize()) {
            content()
            SnackLayer(state)
            PanelLayer(state)
            DialogLayer(state)
            MenuLayer(state)
        }
    }
}

@Composable
private fun SafeBackHandler(enabled: Boolean, onBack: () -> Unit) {
    if (LocalOnBackPressedDispatcherOwner.current != null) BackHandler(enabled, onBack)
}

@Composable
private fun Dim(visible: Boolean, onClick: () -> Unit) {
    val colors = Coui.colors
    AnimatedVisibility(visible, enter = fadeIn(tween(CouiMotion.DIALOG_IN)), exit = fadeOut(tween(CouiMotion.DIALOG_OUT))) {
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = colors.dim))
            .clickable(remember { MutableInteractionSource() }, indication = null, onClick = onClick))
    }
}

@Composable
private fun DialogLayer(state: CouiOverlayState) {
    var shown by remember { mutableStateOf<DialogSpec?>(null) }
    val current = state.dialog
    if (current != null) shown = current
    val visible = current != null
    Dim(visible) { if (shown?.cancelable == true) state.dismissDialog() }
    SafeBackHandler(visible) { if (shown?.cancelable == true) state.dismissDialog() }
    Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.navigationBars), contentAlignment = Alignment.BottomCenter) {
        AnimatedVisibility(
            visible,
            enter = scaleIn(tween(CouiMotion.DIALOG_IN, easing = CouiMotion.MoveEase), initialScale = 0.8f) +
                fadeIn(tween(CouiMotion.DIALOG_IN, easing = CouiMotion.MoveEase)),
            exit = fadeOut(tween(CouiMotion.DIALOG_OUT, easing = CouiMotion.OutEase)),
        ) {
            shown?.let { DialogCard(it) { state.dialog = null } }
        }
    }
}

@Composable
private fun DialogCard(spec: DialogSpec, close: () -> Unit) {
    val colors = Coui.colors
    val type = Coui.type
    Column(
        Modifier.padding(start = 16.dp, end = 16.dp, bottom = 32.dp).widthIn(max = 392.dp).fillMaxWidth()
            .clip(CouiShapes.dialog).background(colors.surface)
            .clickable(remember { MutableInteractionSource() }, indication = null) {},
    ) {
        if (spec.title != null) BasicText(spec.title, Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 6.dp),
            style = type.dialogTitle.copy(color = colors.label, textAlign = TextAlign.Center))
        if (spec.message != null) BasicText(spec.message, Modifier.fillMaxWidth().heightIn(max = 300.dp).verticalScroll(rememberScrollState())
            .padding(start = 24.dp, end = 24.dp, top = if (spec.title == null) 24.dp else 0.dp, bottom = 16.dp),
            style = type.dialogMessage.copy(color = colors.labelSecondary, textAlign = TextAlign.Center))
        spec.content?.invoke()
        Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp)) {
            spec.buttons.forEachIndexed { i, b ->
                if (i > 0) Spacer(Modifier.height(8.dp))
                // 每个按钮都有自己的底色：主按钮用强调色填充，其余按钮用浅灰底，危险操作用红字。
                val (fill, color) = when (b.kind) {
                    CouiDialogButton.Kind.Primary -> colors.accent to colors.onColor
                    CouiDialogButton.Kind.Danger -> colors.container8 to colors.error
                    CouiDialogButton.Kind.Normal -> colors.container8 to colors.label
                }
                Box(
                    Modifier.fillMaxWidth().heightIn(min = 44.dp).clip(RoundedCornerShape(50)).background(fill)
                        .couiPressable(role = Role.Button) { close(); b.onClick() },
                    contentAlignment = Alignment.Center,
                ) { BasicText(b.text, style = type.dialogButton.copy(color = color)) }
            }
        }
    }
}

@Composable
private fun PanelLayer(state: CouiOverlayState) {
    var shown by remember { mutableStateOf<PanelSpec?>(null) }
    val current = state.panel
    if (current != null) shown = current
    val visible = current != null
    Dim(visible) { state.dismissPanel() }
    SafeBackHandler(visible) { state.dismissPanel() }
    val colors = Coui.colors
    Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.navigationBars).padding(top = 40.dp), contentAlignment = Alignment.BottomCenter) {
        AnimatedVisibility(
            visible,
            enter = slideInVertically(tween(CouiMotion.DIALOG_IN, easing = CouiMotion.MoveEase)) { it },
            exit = slideOutVertically(tween(CouiMotion.DIALOG_IN, easing = CouiMotion.OutEase)) { it },
        ) {
            val spec = shown ?: return@AnimatedVisibility
            Column(
                Modifier.padding(start = 16.dp, end = 16.dp, bottom = 32.dp).widthIn(max = 540.dp).fillMaxWidth()
                    .clip(CouiShapes.panel).background(if (spec.withCards) colors.surfaceWithCard else colors.surface)
                    .clickable(remember { MutableInteractionSource() }, indication = null) {},
            ) {
                Box(Modifier.fillMaxWidth().height(16.dp), contentAlignment = Alignment.Center) {
                    Box(Modifier.padding(top = 4.dp).size(36.dp, 4.dp).clip(RoundedCornerShape(2.dp)).background(colors.controls))
                }
                if (spec.title != null) BasicText(spec.title, Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 12.dp),
                    style = Coui.type.panelTitle.copy(color = colors.label, textAlign = TextAlign.Center))
                Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom = 16.dp), content = spec.content)
            }
        }
    }
}

@Composable
private fun MenuLayer(state: CouiOverlayState) {
    var shown by remember { mutableStateOf<MenuSpec?>(null) }
    val current = state.menu
    if (current != null) shown = current
    val visible = current != null
    // Previews and screenshot tests draw a single frame, so the menu starts fully open there.
    val inspection = LocalInspectionMode.current
    val progress = remember { Animatable(if (inspection) 1f else 0f) }
    LaunchedEffect(visible) {
        if (visible) progress.animateTo(1f, CouiMotion.pressSpring())
        else { progress.animateTo(0f, tween(CouiMotion.DIALOG_OUT, easing = CouiMotion.OutEase)); shown = null }
    }
    SafeBackHandler(visible) { state.dismissMenu() }
    val spec = shown ?: return
    val colors = Coui.colors
    Box(Modifier.fillMaxSize().clickable(remember { MutableInteractionSource() }, indication = null) { state.dismissMenu() })
    val margin = 16.dp
    Layout(modifier = Modifier.fillMaxSize(), content = {
        Column(
            Modifier.widthIn(min = 168.dp, max = 256.dp)
                .graphicsLayer {
                    val p = progress.value
                    alpha = p.coerceIn(0f, 1f); scaleX = 0.9f + 0.1f * p; scaleY = 0.9f + 0.1f * p
                    transformOrigin = TransformOrigin(1f, 0f)
                    compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.ModulateAlpha
                }
                .popupShadow(CouiShapes.menu, colors.surface, colors.isDark) { progress.value.coerceIn(0f, 1f) }
                .clip(CouiShapes.menu).background(colors.surface)
                .border(0.5.dp, if (colors.isDark) Color.White.copy(alpha = 0.10f) else Color.Black.copy(alpha = 0.06f), CouiShapes.menu)
                .padding(vertical = 8.dp),
        ) {
            spec.items.forEachIndexed { i, item ->
                val selected = i == spec.selected
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 48.dp)
                        .couiPressable(role = Role.Button) { state.dismissMenu(); if (!selected) spec.onSelect(i) }
                        .padding(start = 16.dp, end = 18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    BasicText(item, Modifier.weight(1f).padding(vertical = 8.dp),
                        style = Coui.type.menuItem.copy(color = if (selected) colors.accentText else colors.label))
                    if (selected) CouiIcon(CouiGlyph.Check, colors.accentText) else Spacer(Modifier.width(24.dp))
                }
            }
        }
    }) { measurables, constraints ->
        val p = measurables.first().measure(Constraints(maxWidth = constraints.maxWidth, maxHeight = constraints.maxHeight))
        val m = margin.roundToPx()
        val gap = 8.dp.roundToPx()
        val anchor = spec.anchor
        val x = (anchor.right - m - p.width).roundToInt().coerceIn(m, max(m, constraints.maxWidth - m - p.width))
        val below = anchor.bottom.roundToInt() - 8.dp.roundToPx()
        val y = if (below + p.height + m <= constraints.maxHeight) below + gap - 8.dp.roundToPx()
            else max(m, anchor.top.roundToInt() - gap - p.height)
        layout(constraints.maxWidth, constraints.maxHeight) { p.place(x, min(y, constraints.maxHeight - p.height - m)) }
    }
}

@Composable
private fun SnackLayer(state: CouiOverlayState) {
    var shown by remember { mutableStateOf<SnackSpec?>(null) }
    val current = state.snack
    if (current != null) shown = current
    LaunchedEffect(current?.serial) { if (current != null) { delay(3000); if (state.snack?.serial == current.serial) state.dismissSnack() } }
    val colors = Coui.colors
    Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.navigationBars), contentAlignment = Alignment.BottomCenter) {
        AnimatedVisibility(
            current != null,
            enter = slideInVertically(tween(CouiMotion.FAST, easing = CouiMotion.MoveEase)) { it / 2 } + fadeIn(tween(CouiMotion.FAST)),
            exit = slideOutVertically(tween(CouiMotion.VERY_FAST, easing = CouiMotion.OutEase)) { it / 2 } + fadeOut(tween(CouiMotion.VERY_FAST)),
        ) {
            val spec = shown ?: return@AnimatedVisibility
            Row(
                Modifier.padding(start = 16.dp, end = 16.dp, bottom = 64.dp).widthIn(max = 368.dp).fillMaxWidth()
                    .shadow(12.dp, CouiShapes.snack, ambientColor = Color.Black.copy(alpha = 0.14f), spotColor = Color.Black.copy(alpha = 0.14f))
                    .clip(CouiShapes.snack).background(colors.surfaceTop).padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BasicText(spec.text, Modifier.weight(1f), style = Coui.type.snack.copy(color = colors.label))
                if (spec.action != null) BasicText(spec.action,
                    Modifier.padding(start = 12.dp).widthIn(max = 110.dp)
                        .clickable(remember { MutableInteractionSource() }, indication = null) { state.dismissSnack(); spec.onAction?.invoke() },
                    style = Coui.type.buttonSmall.copy(color = colors.accentText))
            }
        }
    }
}

private fun Modifier.popupShadow(shape: androidx.compose.ui.graphics.Shape, fill: Color, dark: Boolean, strength: () -> Float): Modifier = drawBehind {
    val k = strength()
    if (k <= 0.01f) return@drawBehind
    val outline = shape.createOutline(size, layoutDirection, this)
    val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = fill.copy(alpha = 1f).toArgb()
        setShadowLayer(28.dp.toPx(), 0f, 8.dp.toPx(), Color.Black.copy(alpha = (if (dark) 0.6f else 0.2f) * k).toArgb())
    }
    drawIntoCanvas { canvas ->
        val native = canvas.nativeCanvas
        when (outline) {
            is androidx.compose.ui.graphics.Outline.Rounded -> {
                val r = outline.roundRect
                native.drawRoundRect(r.left, r.top, r.right, r.bottom, r.topLeftCornerRadius.x, r.topLeftCornerRadius.y, paint)
            }
            is androidx.compose.ui.graphics.Outline.Rectangle ->
                native.drawRect(outline.rect.left, outline.rect.top, outline.rect.right, outline.rect.bottom, paint)
            is androidx.compose.ui.graphics.Outline.Generic -> native.drawPath(outline.path.asAndroidPath(), paint)
        }
    }
}

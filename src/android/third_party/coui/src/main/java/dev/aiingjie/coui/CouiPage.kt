package dev.aiingjie.coui

import android.graphics.RuntimeShader
import android.os.Build
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.LocalOverscrollFactory
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.layer.CompositingStrategy
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp

@Composable
private fun NoStretch(content: @Composable () -> Unit) = CompositionLocalProvider(LocalOverscrollFactory provides null, content = content)

@Composable
private fun listPadding(top: Dp, bottom: Dp = 0.dp): PaddingValues {
    val nav = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    return PaddingValues(top = top + CouiDimens.listTop, bottom = CouiDimens.listBottom + nav + bottom)
}

val CouiDockSpace = 72.dp

private val MaxBlur = 18.dp
private val Handover = 72.dp
private val RampDistance = 32.dp

private const val VARIABLE_BLUR = """
uniform shader content;
uniform float2 size;
uniform float2 direction;
uniform float bottom;
uniform float maxRadius;

half4 main(float2 p) {
    float t = clamp((bottom - p.y) / bottom, 0.0, 1.0);
    float r = maxRadius * t * t * (3.0 - 2.0 * t);
    if (r < 0.75) return content.eval(p);
    half4 sum = half4(0.0);
    float total = 0.0;
    for (int i = -10; i <= 10; i++) {
        float x = float(i) / 10.0;
        float w = exp(-2.5 * x * x);
        float2 q = clamp(p + direction * (x * r), float2(0.5), size - float2(0.5));
        sum += content.eval(q) * w;
        total += w;
    }
    return sum / total;
}
"""

@Composable
private fun Modifier.barBackdrop(state: LazyListState, bar: () -> Float): Modifier {
    val page = Coui.colors.page
    val content = rememberGraphicsLayer()
    val band = rememberGraphicsLayer()
    val passes = remember {
        if (Build.VERSION.SDK_INT >= 33) RuntimeShader(VARIABLE_BLUR) to RuntimeShader(VARIABLE_BLUR) else null
    }
    return drawWithContent {
        content.record { this@drawWithContent.drawContent() }
        drawLayer(content)
        val ramp = RampDistance.toPx()
        val strength = if (state.firstVisibleItemIndex > 0) 1f else (state.firstVisibleItemScrollOffset / ramp).coerceIn(0f, 1f)
        val barBottom = bar()
        val end = minOf(barBottom + Handover.toPx(), size.height)
        if (strength <= 0f || end <= 1f) return@drawWithContent
        if (passes != null && Build.VERSION.SDK_INT >= 33) {
            val (across, down) = passes
            for (shader in listOf(across, down)) {
                shader.setFloatUniform("size", size.width, end)
                shader.setFloatUniform("bottom", end)
                shader.setFloatUniform("maxRadius", MaxBlur.toPx() * strength)
            }
            across.setFloatUniform("direction", 1f, 0f)
            down.setFloatUniform("direction", 0f, 1f)
            band.renderEffect = android.graphics.RenderEffect.createChainEffect(
                android.graphics.RenderEffect.createRuntimeShaderEffect(down, "content"),
                android.graphics.RenderEffect.createRuntimeShaderEffect(across, "content"),
            ).asComposeRenderEffect()
            band.clip = true
            band.record(size = IntSize(size.width.toInt(), end.toInt())) {
                drawRect(page)
                drawLayer(content)
            }
            drawLayer(band)
        }
        val b = (barBottom / end).coerceIn(0f, 1f)
        fun veil(at: Float, alpha: Float) = b * at to page.copy(alpha = alpha * strength)
        drawRect(
            Brush.verticalGradient(veil(0f, 0.85f), veil(0.2f, 0.74f), veil(0.4f, 0.55f), veil(0.6f, 0.33f),
                veil(0.8f, 0.13f), veil(1f, 0f), endY = end),
            size = Size(size.width, end),
        )
    }
}

@Composable
fun CouiLargeTitlePage(
    title: String,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
    bottomSpace: Dp = 0.dp,
    actions: @Composable RowScope.() -> Unit = {},
    content: LazyListScope.() -> Unit,
) {
    val colors = Coui.colors
    val density = LocalDensity.current
    val range = with(density) { (CouiDimens.largeTitleArea - CouiDimens.barHeight).toPx() }
    var offset by remember { mutableFloatStateOf(0f) }
    val connection = remember(range) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (available.y >= 0f) return Offset.Zero
                val next = (offset + available.y).coerceIn(-range, 0f)
                val used = next - offset
                offset = next
                return Offset(0f, used)
            }

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (available.y <= 0f) return Offset.Zero
                val next = (offset + available.y).coerceIn(-range, 0f)
                val used = next - offset
                offset = next
                return Offset(0f, used)
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                if (offset > -range && offset < 0f) {
                    val target = if (offset < -range / 2f) -range else 0f
                    animate(offset, target, animationSpec = tween(CouiMotion.FAST, easing = CouiMotion.MoveEase)) { v, _ -> offset = v }
                }
                return Velocity.Zero
            }
        }
    }
    val collapse = if (range == 0f) 0f else -offset / range
    val status = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val area = CouiDimens.largeTitleArea + with(density) { offset.toDp() }
    val areaPx = with(density) { (status + CouiDimens.largeTitleArea).toPx() }
    val tones = remember { CouiTones() }
    val probe = rememberToneProbe(tones)
    Box(modifier.fillMaxSize().background(colors.page)) {
        NoStretch {
            CompositionLocalProvider(LocalCouiTones provides tones) {
                LazyColumn(Modifier.fillMaxSize().nestedScroll(connection).barBackdrop(listState) { areaPx + offset },
                    state = listState, contentPadding = listPadding(status + area, bottomSpace), content = content)
            }
        }
        Box(Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars).height(area)) {
            Row(Modifier.align(Alignment.TopEnd).height(CouiDimens.barHeight).padding(end = 12.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), content = actions)
            val type = Coui.type
            BasicText(
                title,
                Modifier.padding(start = 16.dp, end = 64.dp)
                    .graphicsLayer {
                        val s = 1f - 0.25f * collapse
                        scaleX = s; scaleY = s
                        transformOrigin = TransformOrigin(0f, 0f)
                        val expandedTop = CouiDimens.largeTitleTop.toPx()
                        val collapsedTop = ((CouiDimens.barHeight.toPx() - size.height * 0.75f) / 2f)
                        translationY = expandedTop + (collapsedTop - expandedTop) * collapse
                    }
                    .then(probe.modifier),
                style = type.largeTitle.copy(color = adaptiveInk(probe, colors.label)),
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
fun CouiSubPage(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    listState: LazyListState = rememberLazyListState(),
    actions: @Composable RowScope.() -> Unit = {},
    header: (@Composable () -> Unit)? = null,
    backDescription: String = "返回",
    content: LazyListScope.() -> Unit,
) {
    val colors = Coui.colors
    val density = LocalDensity.current
    val status = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    // First guess: the bar plus, when there is a header, a 52dp search field. onSizeChanged then measures the real
    // height; the guess only keeps the first frame from drawing the list under the header.
    var barPx by remember {
        mutableIntStateOf(with(density) { (status + CouiDimens.barHeight + if (header != null) 52.dp else 0.dp).roundToPx() })
    }
    val tones = remember { CouiTones() }
    val probe = rememberToneProbe(tones)
    Box(modifier.fillMaxSize().background(colors.page)) {
        NoStretch {
            CompositionLocalProvider(LocalCouiTones provides tones) {
                LazyColumn(Modifier.fillMaxSize().barBackdrop(listState) { barPx.toFloat() }, state = listState,
                    contentPadding = listPadding(with(density) { barPx.toDp() }), content = content)
            }
        }
        Column(Modifier.fillMaxWidth().onSizeChanged { barPx = it.height }) {
            Row(Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars).height(CouiDimens.barHeight).padding(start = 12.dp, end = 12.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CouiBarButton(CouiGlyph.Back, backDescription, onBack)
                Column(Modifier.weight(1f).padding(start = 4.dp).then(probe.modifier)) {
                    BasicText(title, style = Coui.type.barTitle.copy(color = adaptiveInk(probe, colors.label)), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (subtitle != null) BasicText(subtitle, style = Coui.type.barSubtitle.copy(
                        color = adaptiveInk(probe, colors.labelSecondary, Color(0xB3FCF6EF))),
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                actions()
            }
            header?.invoke()
        }
    }
}


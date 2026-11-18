package dev.aiingjie.coui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

@Immutable
data class CouiColors(
    val isDark: Boolean,
    val page: Color,
    val card: Color,
    val surface: Color,
    val surfaceWithCard: Color,
    val surfaceTop: Color,
    val label: Color,
    val labelSecondary: Color,
    val labelTertiary: Color,
    val labelQuaternary: Color,
    val divider: Color,
    val press: Color,
    val controls: Color,
    val container8: Color,
    val container12: Color,
    val pressBackground: Color,
    val accent: Color,
    val accentText: Color,
    val accentSecondary: Color,
    val accentDisabled: Color,
    val error: Color,
    val success: Color,
    val warning: Color,
    val orange: Color,
    val redDot: Color,
    val dim: Float,
    val onColor: Color,
    val thumb: Color,
    val thumbDisabled: Color,
)

val CouiLightColors = CouiColors(
    isDark = false,
    page = Color(0xFFF0F1F2), card = Color(0xFFFFFFFF), surface = Color(0xFFFFFFFF), surfaceWithCard = Color(0xFFF0F1F2),
    surfaceTop = Color(0xFFFFFFFF),
    label = Color(0xE6000000), labelSecondary = Color(0x8A000000), labelTertiary = Color(0x42000000), labelQuaternary = Color(0x14000000),
    divider = Color(0x1F000000), press = Color(0x1F000000), controls = Color(0x29000000), container8 = Color(0x14000000),
    container12 = Color(0x1F000000), pressBackground = Color(0x14000000),
    accent = Color(0xFF0080FF), accentText = Color(0xFF0080FF), accentSecondary = Color(0x260080FF), accentDisabled = Color(0x4D0080FF),
    error = Color(0xFFDB382C), success = Color(0xFF00BD13), warning = Color(0xFFFFB200), orange = Color(0xFFFF7700),
    redDot = Color(0xFFEB3B2F), dim = 0.2f, onColor = Color.White, thumb = Color.White, thumbDisabled = Color(0x8AFFFFFF),
)

val CouiDarkColors = CouiColors(
    isDark = true,
    page = Color(0xFF000000), card = Color(0x1AFFFFFF), surface = Color(0xFF1E1E1E), surfaceWithCard = Color(0xFF1E1E1E),
    surfaceTop = Color(0xFF333333),
    label = Color(0xE6FFFFFF), labelSecondary = Color(0x8AFFFFFF), labelTertiary = Color(0x4DFFFFFF), labelQuaternary = Color(0x26FFFFFF),
    divider = Color(0x33FFFFFF), press = Color(0x33FFFFFF), controls = Color(0x40FFFFFF), container8 = Color(0x1AFFFFFF),
    container12 = Color(0x33FFFFFF), pressBackground = Color(0x26FFFFFF),
    accent = Color(0xFF1A8CFF), accentText = Color(0xFF5C9DFF), accentSecondary = Color(0x401A8CFF), accentDisabled = Color(0x4D1A8CFF),
    error = Color(0xFFFF6C61), success = Color(0xFF24B232), warning = Color(0xFFE5A100), orange = Color(0xFFF08222),
    redDot = Color(0xFFEB3B2F), dim = 0.5f, onColor = Color.White, thumb = Color.White, thumbDisabled = Color(0x29FFFFFF),
)

fun CouiColors.withAccent(accent: Color, accentText: Color = accent): CouiColors = copy(
    accent = accent, accentText = accentText,
    accentSecondary = accent.copy(alpha = if (isDark) 0.25f else 0.15f),
    accentDisabled = accent.copy(alpha = 0.3f),
)

val CouiColors.cardOpaque: Color get() = if (isDark) card.compositeOver(page) else card

fun CouiColors.withDarkGray(level: Float): CouiColors {
    if (!isDark || level <= 0f) return this
    val l = level.coerceIn(0f, 1f)
    fun gray(v: Float) = Color(v / 255f, v / 255f, v / 255f)
    val surface = gray(30f + 18f * l)
    return copy(page = gray(36f * l), surface = surface, surfaceWithCard = surface, surfaceTop = gray(51f + 16f * l))
}

@Immutable
data class CouiType(
    val largeTitle: TextStyle,
    val largeTitleCollapsed: TextStyle,
    val barTitle: TextStyle,
    val barSubtitle: TextStyle,
    val rowTitle: TextStyle,
    val rowSummary: TextStyle,
    val rowValue: TextStyle,
    val jumpValue: TextStyle,
    val category: TextStyle,
    val footer: TextStyle,
    val body: TextStyle,
    val buttonLarge: TextStyle,
    val buttonSmall: TextStyle,
    val tipsTitle: TextStyle,
    val tipsBody: TextStyle,
    val dialogTitle: TextStyle,
    val dialogMessage: TextStyle,
    val dialogButton: TextStyle,
    val menuItem: TextStyle,
    val snack: TextStyle,
    val panelTitle: TextStyle,
)

private val Tight = LineHeightStyle(alignment = LineHeightStyle.Alignment.Center, trim = LineHeightStyle.Trim.None)

private fun style(size: TextUnit, weight: FontWeight, lineHeight: Float) =
    TextStyle(fontSize = size, fontWeight = weight, lineHeight = lineHeight.em, lineHeightStyle = Tight)

@Composable
@ReadOnlyComposable
private fun Dp.fixedSp(): TextUnit = with(LocalDensity.current) { this@fixedSp.toSp() }

@Composable
private fun couiType(): CouiType {
    val medium = FontWeight.Medium
    val normal = FontWeight.Normal
    return CouiType(
        largeTitle = style(32.dp.fixedSp(), FontWeight.SemiBold, 1.3f),
        largeTitleCollapsed = style(24.dp.fixedSp(), FontWeight.SemiBold, 1.3f),
        barTitle = style(18.dp.fixedSp(), FontWeight(550), 1.1429f),
        barSubtitle = style(13.dp.fixedSp(), normal, 1.1739f),
        rowTitle = style(16.sp, medium, 1.158f),
        rowSummary = style(13.sp, normal, 1.1739f),
        rowValue = style(13.sp, normal, 1.1739f),
        jumpValue = style(14.dp.fixedSp(), normal, 1.2245f),
        category = style(12.sp, medium, 1.143f),
        footer = style(12.sp, normal, 1.35f),
        body = style(14.sp, normal, 1.2245f),
        buttonLarge = style(16.sp, medium, 1.1579f),
        buttonSmall = style(14.sp, medium, 1.2245f),
        tipsTitle = style(16.sp, medium, 1.158f),
        tipsBody = style(13.sp, normal, 1.35f),
        dialogTitle = style(16.sp, medium, 1.25f),
        dialogMessage = style(14.sp, normal, 1.4f),
        dialogButton = style(16.sp, medium, 1.1579f),
        menuItem = style(16.sp, normal, 1.158f),
        snack = style(14.dp.fixedSp(), normal, 1.3f),
        panelTitle = style(18.sp, FontWeight(550), 1.1429f),
    )
}

object CouiDimens {
    val listTop = 12.dp
    val listBottom = 32.dp
    val cardMargin = 16.dp
    val textInset = 32.dp
    val rowInnerPadding = 16.dp
    val categoryGap = 16.dp
    val rowMinHeight = 48.dp
    val cardEdgeExtra = 2.dp
    val textBlockVertical = 10.dp
    val titleSummaryGap = 2.dp
    val titleToValue = 16.dp
    val jumpValueMaxWidth = 162.dp
    val arrowGap = 4.dp
    val widgetGap = 16.dp
    val iconToText = 16.dp
    val dividerThickness = 0.33.dp
    val cardRadius = 20.dp
    val cardRadiusSmooth = 24.dp
    val barHeight = 52.dp
    val largeTitleArea = 110.dp
    val largeTitleTop = 54.dp
    val panelRadius = 28.dp
    val dialogRadius = 32.dp
    val menuRadius = 20.dp
    val snackRadius = 16.dp
}

val LocalCouiColors = staticCompositionLocalOf { CouiLightColors }
val LocalCouiType = staticCompositionLocalOf<CouiType> { error("CouiTheme missing") }

object Coui {
    val colors: CouiColors
        @Composable @ReadOnlyComposable get() = LocalCouiColors.current
    val type: CouiType
        @Composable @ReadOnlyComposable get() = LocalCouiType.current
}

@Composable
fun CouiTheme(
    dark: Boolean = isSystemInDarkTheme(),
    colors: CouiColors = if (dark) CouiDarkColors else CouiLightColors,
    content: @Composable () -> Unit,
) {
    val type = couiType()
    val stable = remember(colors) { colors }
    CompositionLocalProvider(LocalCouiColors provides stable, LocalCouiType provides type, content = content)
}

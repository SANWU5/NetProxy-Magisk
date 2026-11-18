package dev.aiingjie.coui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Pure-Compose rendering of the floating dock: same layout, icons and colors as [CouiDockBar], but no touch
 * physics and no system blur. Meant for previews, screenshots and design docs; use [CouiDockBar] in an app.
 */
@Composable
fun CouiDockPreview(
    tabs: List<CouiDockTab>,
    selected: Int,
    modifier: Modifier = Modifier,
    dark: Boolean = Coui.colors.isDark,
    onSelect: (Int) -> Unit = {},
) {
    val touch = remember { CouiDockTouch() }
    Box(
        modifier
            .shadow(12.dp, CircleShape, ambientColor = Color(0x8A000000), spotColor = Color(0x8A000000))
            .clip(CircleShape)
            .background(if (dark) Color(0xFF262628) else Color(0xFFF7F7F7))
            .border(0.5.dp, if (dark) Color(0x1AFFFFFF) else Color(0x99FFFFFF), CircleShape),
    ) {
        CouiDockContent(tabs, selected, touch, dark, onSelect)
    }
}

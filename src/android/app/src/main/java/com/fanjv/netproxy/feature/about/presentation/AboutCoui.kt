package com.fanjv.netproxy.feature.about.presentation

import android.graphics.Canvas
import android.graphics.drawable.Drawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.createBitmap
import dev.aiingjie.coui.CouiGroup
import dev.aiingjie.coui.CouiHeroCard
import dev.aiingjie.coui.CouiJumpRow
import dev.aiingjie.coui.CouiSmoothShape
import dev.aiingjie.coui.CouiSubPage
import dev.aiingjie.coui.couiJellyTap

/** 关于页：星空横幅展示应用图标、名称与版本，下方为项目链接。 */
@Composable
internal fun AboutScreenCoui(
    state: AboutUiState,
    actions: AboutScreenActions,
) {
    val context = LocalContext.current
    val appIcon = remember(context) {
        context.packageManager.getApplicationIcon(context.applicationInfo).toImageBitmap(256)
    }
    CouiSubPage(title = state.title, onBack = actions.onBack) {
        item("hero") {
            CouiHeroCard(Modifier.padding(top = 8.dp), height = 240.dp) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Image(appIcon, null, Modifier.size(88.dp).clip(CouiSmoothShape(24.dp)).couiJellyTap())
                    BasicText(
                        state.appName,
                        Modifier.padding(top = 12.dp),
                        style = TextStyle(color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold),
                    )
                    BasicText(
                        state.versionName,
                        Modifier.padding(top = 4.dp),
                        style = TextStyle(color = Color.White.copy(alpha = 0.72f), fontSize = 14.sp),
                    )
                }
            }
        }
        item("links") {
            CouiGroup {
                state.links.forEach { link ->
                    CouiJumpRow(link.label, { actions.onOpenLink(link.url) })
                }
            }
        }
    }
}

private fun Drawable.toImageBitmap(fallbackSize: Int): ImageBitmap {
    val width = intrinsicWidth.takeIf { it > 0 } ?: fallbackSize
    val height = intrinsicHeight.takeIf { it > 0 } ?: fallbackSize
    val bitmap = createBitmap(width, height)
    val canvas = Canvas(bitmap)
    setBounds(0, 0, canvas.width, canvas.height)
    draw(canvas)
    return bitmap.asImageBitmap()
}

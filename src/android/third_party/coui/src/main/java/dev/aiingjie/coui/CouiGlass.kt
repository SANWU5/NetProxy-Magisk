package dev.aiingjie.coui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Outline
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.viewinterop.AndroidView
import kotlin.math.roundToInt

@SuppressLint("ViewConstructor")
internal class CouiGlassView(context: Context) : FrameLayout(context) {
    private val density = resources.displayMetrics.density
    private var dark = false
    private var applied: Boolean? = null

    init {
        isClickable = false
        isFocusable = false
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        clipToOutline = true
        outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setRoundRect(0, 0, view.width, view.height, minOf(view.width, view.height) / 2f)
            }
        }
        elevation = 8f * density
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            outlineSpotShadowColor = 0x66000000
            outlineAmbientShadowColor = 0x66000000
        }
    }

    fun setDark(value: Boolean) {
        if (dark == value && applied != null) return
        dark = value
        applied = null
        refresh()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        applied = null
        refresh()
    }

    private fun refresh() {
        if (width <= 0 || height <= 0 || applied != null) return
        val radius = minOf(width, height) / 2f
        val material = ColorOsMaterial.applyBottomBar(this, dark, radius)
        applied = material
        background = if (material) null else GradientDrawable().apply {
            cornerRadius = radius
            setColor(if (dark) 0xFF262628.toInt() else 0xFFF7F7F7.toInt())
            setStroke(maxOf(1, (density / 2).roundToInt()), if (dark) 0x1AFFFFFF else 0x99FFFFFF.toInt())
        }
    }
}

@Composable
fun CouiGlass(modifier: Modifier, content: @Composable BoxScope.() -> Unit) {
    val dark = Coui.colors.page.luminance() < .5f
    Box(modifier, contentAlignment = Alignment.Center) {
        AndroidView(factory = { CouiGlassView(it) }, update = { it.setDark(dark) }, modifier = Modifier.matchParentSize())
        content()
    }
}

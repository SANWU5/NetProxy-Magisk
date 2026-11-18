// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 aiingjie
// Adapted from C17 ColorOsMaterial.kt.
package dev.aiingjie.coui

import android.graphics.BlendMode
import android.graphics.BlendModeColorFilter
import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import android.view.View
import java.lang.reflect.Method

internal object ColorOsMaterial {
    private class Api(
        val background: Method,
        val util: Class<*>,
        val corner: Class<*>,
        val edge: Class<*>,
        val shadow: Class<*>,
        val setCorner: Method,
        val setEdge: Method,
        val setShadow: Method,
    )

    private val api: Api? by lazy {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return@lazy null
        runCatching {
            val util = Class.forName("com.oplus.view.material.OplusMaterialUtil")
            val corner = Class.forName("com.oplus.view.material.OplusMaterialCornerParams")
            val edge = Class.forName("com.oplus.view.material.OplusMaterialEdgeParams")
            val shadow = Class.forName("com.oplus.view.material.OplusMaterialShadowParams")
            Api(
                background = Class.forName("com.oplus.view.OplusViewBackgroundRenderEffect")
                    .getMethod("setBackgroundRenderEffect", RenderEffect::class.java, View::class.java),
                util = util, corner = corner, edge = edge, shadow = shadow,
                setCorner = util.getMethod("setCornerParams", View::class.java, corner),
                setEdge = util.getMethod("setEdgeParams", View::class.java, edge),
                setShadow = util.getMethod("setShadowParams", View::class.java, shadow),
            )
        }.getOrNull()
    }

    fun supported(): Boolean = api != null

    fun applyBottomBar(view: View, dark: Boolean, radiusPx: Float): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return false
        val api = api ?: return false
        return try {
            val blur = RenderEffect.createBlurEffect(25f, 25f, Shader.TileMode.MIRROR)
            val first = if (dark) BlendModeColorFilter(0x80666666.toInt(), BlendMode.OVERLAY)
                else BlendModeColorFilter(0xB3666666.toInt(), BlendMode.COLOR_DODGE)
            val second = if (dark) BlendModeColorFilter(0xB3262626.toInt(), BlendMode.LUMINOSITY)
                else BlendModeColorFilter(0x99F5F5F5.toInt(), BlendMode.LUMINOSITY)
            val effect = RenderEffect.createColorFilterEffect(second, RenderEffect.createColorFilterEffect(first, blur))
            api.background.invoke(null, effect, view)
            val corner = api.corner.getConstructor(Float::class.javaPrimitiveType, Float::class.javaPrimitiveType)
                .newInstance(radiusPx, 2f)
            api.setCorner.invoke(null, view, corner)
            val density = view.resources.displayMetrics.density
            val edgeAlpha = if (dark) .8f else .25f
            val fadeIn = if (dark) .03f else .1f
            val edge = api.edge.getConstructor(Int::class.javaPrimitiveType, Float::class.javaPrimitiveType,
                Float::class.javaPrimitiveType, Float::class.javaPrimitiveType).newInstance(1, 2.5f * density, edgeAlpha, 0f)
            api.setEdge.invoke(null, view, edge)
            val shadow = api.shadow.getConstructor(Int::class.javaPrimitiveType, Float::class.javaPrimitiveType,
                Float::class.javaPrimitiveType, Float::class.javaPrimitiveType).newInstance(1, 1f, fadeIn, 1f)
            api.setShadow.invoke(null, view, shadow)
            view.invalidate()
            true
        } catch (_: Throwable) {
            clear(view)
            false
        }
    }

    fun clear(view: View) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        val api = api ?: return
        runCatching { api.background.invoke(null, null, view) }
        runCatching {
            val edge = api.edge.getConstructor(Int::class.javaPrimitiveType, Float::class.javaPrimitiveType,
                Float::class.javaPrimitiveType, Float::class.javaPrimitiveType).newInstance(0, 0f, 0f, 0f)
            api.setEdge.invoke(null, view, edge)
        }
        runCatching {
            val shadow = api.shadow.getConstructor(Int::class.javaPrimitiveType, Float::class.javaPrimitiveType,
                Float::class.javaPrimitiveType, Float::class.javaPrimitiveType).newInstance(0, 0f, 0f, 0f)
            api.setShadow.invoke(null, view, shadow)
        }
        view.invalidate()
    }
}

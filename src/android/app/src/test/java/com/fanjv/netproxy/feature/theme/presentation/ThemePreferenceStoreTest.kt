package com.fanjv.netproxy.feature.theme.presentation

import android.content.SharedPreferences
import com.fanjv.netproxy.core.ui.theme.ColorMode
import java.lang.reflect.Proxy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemePreferenceStoreTest {
    @Test
    fun emptyPreferencesFollowSystemAppearance() {
        val settings = ThemePreferenceStore(preferences()).read()

        assertEquals(ColorMode.SYSTEM.value, settings.colorMode)
        assertTrue(settings.enableBlur)
        assertTrue(settings.enableSmoothCorner)
        assertFalse(settings.enablePredictiveBack)
        assertEquals(1.0f, settings.pageScale, 0f)
    }

    @Test
    fun initialUiStateUsesTheSameDefaultsAsStoredPreferences() {
        val settings = ThemePreferenceStore(preferences()).read()
        val state = ThemeUiState()

        assertEquals(settings.colorMode, state.colorMode)
        assertEquals(settings.enableBlur, state.enableBlur)
        assertEquals(settings.enablePredictiveBack, state.enablePredictiveBack)
        assertEquals(settings.enableSmoothCorner, state.enableSmoothCorner)
        assertEquals(settings.pageScale, state.pageScale, 0f)
    }

    @Test
    fun savedThemeChoicesRemainUnchanged() {
        val settings = ThemePreferenceStore(
            preferences(
                "color_mode" to ColorMode.DARK.value,
                "enable_blur" to false,
                "enable_smooth_corner" to false,
                "enable_predictive_back" to true,
                "page_scale" to 0.9f,
            )
        ).read()

        assertEquals(ColorMode.DARK.value, settings.colorMode)
        assertFalse(settings.enableBlur)
        assertFalse(settings.enableSmoothCorner)
        assertTrue(settings.enablePredictiveBack)
        assertEquals(0.9f, settings.pageScale, 0f)
    }

    @Test
    fun legacyMonetAndAmoledModesKeepTheirAppearance() {
        mapOf(
            ColorMode.MONET_SYSTEM to ColorMode.SYSTEM,
            ColorMode.MONET_LIGHT to ColorMode.LIGHT,
            ColorMode.MONET_DARK to ColorMode.DARK,
            ColorMode.DARK_AMOLED to ColorMode.DARK,
        ).forEach { (legacy, appearance) ->
            val state = ThemeManager(preferences("color_mode" to legacy.value)).state.value

            assertEquals(legacy.value, state.colorMode)
            assertEquals(appearance, ColorMode.fromValue(state.colorMode).appearance)
        }
    }

    private fun preferences(vararg values: Pair<String, Any>): SharedPreferences {
        val saved = values.toMap()
        return Proxy.newProxyInstance(
            SharedPreferences::class.java.classLoader,
            arrayOf(SharedPreferences::class.java),
        ) { _, method, arguments ->
            when (method.name) {
                "contains" -> saved.containsKey(arguments!![0])
                "getInt", "getBoolean", "getString", "getFloat" ->
                    saved[arguments!![0]] ?: arguments[1]
                else -> error("Unexpected SharedPreferences call: ${method.name}")
            }
        } as SharedPreferences
    }
}

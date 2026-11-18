package com.fanjv.netproxy.navigation

import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import dev.aiingjie.coui.CouiDockBar
import dev.aiingjie.coui.CouiDockTab
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 悬浮底栏是附着在 Activity 内容层上的 View（COUI 用系统材质模糊与触感反馈实现），
 * 不在 Compose 树里；这里负责把主分页状态与它双向同步。
 *
 * 模块可用性变化会改变分页数量，底栏标签固定在创建时，所以标签变化时重建底栏。
 */
internal class DockHost(private val activity: ComponentActivity) {
    private val page = MutableStateFlow(0)
    private val requests = MutableSharedFlow<Int>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    private var root: FrameLayout? = null
    private var bar: CouiDockBar? = null
    private var labels: List<String> = emptyList()
    private var shown = false
    private var dark: Boolean? = null

    val selectedPage: StateFlow<Int> = page.asStateFlow()

    /** 用户在底栏上点按或滑动选中的分页。 */
    val pageRequests: SharedFlow<Int> = requests.asSharedFlow()

    fun attach(root: FrameLayout) {
        this.root = root
        bar?.let { it.attach(root); watchInsets(it) }
    }

    fun setTabs(tabs: List<CouiDockTab>) {
        val next = tabs.map { it.label }
        if (next == labels && bar != null) return
        labels = next
        bar?.let { root?.removeView(it.view) }
        val created = CouiDockBar(activity, tabs, page) { requests.tryEmit(it) }
        bar = created
        root?.let { created.attach(it); watchInsets(created) }
        dark?.let(created::setDark)
        created.setShown(shown)
    }

    /** 底栏跟随应用的浅色或深色设置。 */
    fun setDark(value: Boolean) {
        dark = value
        bar?.setDark(value)
    }

    fun setPage(index: Int) {
        page.value = index
    }

    fun setShown(value: Boolean) {
        shown = value
        bar?.setShown(value)
    }

    private fun watchInsets(dock: CouiDockBar) {
        ViewCompat.setOnApplyWindowInsetsListener(dock.view) { _, insets ->
            dock.setBottomInset(insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom)
            insets
        }
        ViewCompat.requestApplyInsets(dock.view)
    }
}

internal val LocalDockHost = staticCompositionLocalOf<DockHost> { error("LocalDockHost not provided") }

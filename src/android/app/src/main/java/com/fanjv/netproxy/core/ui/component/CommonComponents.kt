package com.fanjv.netproxy.core.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import kotlinx.coroutines.launch

/** 先提交待保存的修改，成功后再执行动作；提交期间忽略重复触发。 */
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

/** 离开页面或应用进入后台时请求提交修改；离开动作只执行一次。 */
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

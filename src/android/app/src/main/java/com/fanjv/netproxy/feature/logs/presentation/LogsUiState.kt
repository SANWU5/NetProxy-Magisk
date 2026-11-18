package com.fanjv.netproxy.feature.logs.presentation

import androidx.compose.runtime.Immutable
import com.fanjv.netproxy.feature.logs.data.LogItem
import com.fanjv.netproxy.feature.logs.data.LogType

@Immutable
internal data class LogContent(
    val entries: List<LogItem> = emptyList(),
    val loading: Boolean = true,
    val error: String = "",
)

@Immutable
internal data class LogsUiState(
    val service: LogContent = LogContent(),
    val kernel: LogContent = LogContent(),
    /** 记录核心日志开关；尚未读到时为空。 */
    val logging: Boolean? = null,
    val loggingBusy: Boolean = false,
) {
    operator fun get(type: LogType): LogContent = if (type == LogType.SERVICE) service else kernel
    fun with(type: LogType, content: LogContent): LogsUiState =
        if (type == LogType.SERVICE) copy(service = content) else copy(kernel = content)
}

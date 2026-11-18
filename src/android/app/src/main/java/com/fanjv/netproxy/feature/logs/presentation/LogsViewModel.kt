package com.fanjv.netproxy.feature.logs.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fanjv.netproxy.core.ui.userMessage
import com.fanjv.netproxy.feature.logs.data.LogRepository
import com.fanjv.netproxy.feature.logs.data.LogType
import com.fanjv.netproxy.feature.settings.data.ConfigRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

/** 通过 netproxyctl 读取、清理并导出脱敏日志，并管理记录核心日志开关。 */
internal class LogsViewModel(
    private val repository: LogRepository,
    private val config: ConfigRepository,
    scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
) : ViewModel(scope) {
    private val _state = MutableStateFlow(LogsUiState())
    val state: StateFlow<LogsUiState> = _state.asStateFlow()
    private val reads = mutableMapOf<LogType, Job>()
    private val revisions = mutableMapOf<LogType, Long>()
    private var loggingRevision = ""

    /** 读取记录核心日志开关；失败时返回错误信息给页面提示。 */
    fun loadLogging(onError: (String) -> Unit) {
        viewModelScope.launch {
            runCatching { config.readLogging() }
                .onSuccess { value ->
                    loggingRevision = value.revision
                    _state.update { it.copy(logging = value.enabled) }
                }
                .onFailure { error ->
                    if (error is CancellationException) throw error
                    onError(error.userMessage())
                }
        }
    }

    /** 切换记录核心日志；服务运行时模块会重载核心使其立即生效。onResult 收到失败信息，成功时为空。 */
    fun setLogging(enabled: Boolean, onResult: (String?) -> Unit) {
        if (_state.value.loggingBusy || _state.value.logging == null) return
        _state.update { it.copy(loggingBusy = true) }
        viewModelScope.launch {
            runCatching { config.applyLogging(enabled, loggingRevision) }
                .onSuccess { revision ->
                    loggingRevision = revision
                    _state.update { it.copy(logging = enabled, loggingBusy = false) }
                    onResult(null)
                }
                .onFailure { error ->
                    if (error is CancellationException) throw error
                    _state.update { it.copy(loggingBusy = false) }
                    onResult(error.userMessage())
                }
        }
    }

    private fun invalidate(type: LogType): Long {
        reads.remove(type)?.cancel()
        val revision = (revisions[type] ?: 0) + 1
        revisions[type] = revision
        return revision
    }

    fun refresh(type: LogType) {
        val revision = invalidate(type)
        _state.update { it.with(type, it[type].copy(loading = true, error = "")) }
        reads[type] = viewModelScope.launch {
            runCatching {
                repository.read(type)
            }.onSuccess { logs ->
                currentCoroutineContext().ensureActive()
                if (revisions[type] != revision) return@onSuccess
                _state.update { it.with(type, LogContent(entries = logs, loading = false)) }
            }.onFailure { error ->
                if (error is CancellationException) throw error
                if (revisions[type] != revision) return@onFailure
                _state.update { it.with(type, it[type].copy(loading = false, error = error.userMessage())) }
            }
        }
    }

    fun clear(type: LogType, onResult: (Boolean) -> Unit = {}) {
        val revision = invalidate(type)
        viewModelScope.launch {
            runCatching { repository.clear(type) }
                .onSuccess {
                    currentCoroutineContext().ensureActive()
                    invalidate(type)
                    _state.update { it.with(type, LogContent(loading = false)) }
                    onResult(true)
                }
                .onFailure { error ->
                    if (error is CancellationException) throw error
                    if (revisions[type] != revision) return@onFailure
                    _state.update { it.with(type, it[type].copy(loading = false)) }
                    onResult(false)
                }
        }
    }

    suspend fun createReport(): File {
        return repository.createReport()
    }

}

package com.fanjv.netproxy.feature.smart.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fanjv.netproxy.core.ui.userMessage
import com.fanjv.netproxy.feature.catalog.data.NodeRepository
import com.fanjv.netproxy.feature.catalog.model.CatalogNodeGroup
import com.fanjv.netproxy.feature.smart.data.SmartGroupRepository
import com.fanjv.netproxy.feature.smart.model.SmartGroups
import com.fanjv.netproxy.feature.smart.model.SmartNodeRef
import com.fanjv.netproxy.feature.smart.model.SmartSummary
import com.fanjv.netproxy.feature.smart.model.key
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal data class SmartUiState(
    val smart: SmartGroups = SmartGroups(),
    /** 全部节点分组，用于显示成员名称与编辑成员。 */
    val nodes: List<CatalogNodeGroup> = emptyList(),
    val loaded: Boolean = false,
    val loading: Boolean = false,
    /** 正在执行的写操作；非空时界面禁用其他写入。 */
    val busy: String = "",
    val error: String = "",
    /** 最近一次测速失败或超时的成员（「来源分组 id/节点名」）。内核会删除失败的测速记录，页面据此继续显示失败。 */
    val failed: Set<String> = emptySet(),
)

internal sealed interface SmartEvent {
    data class Generated(val summary: SmartSummary) : SmartEvent
    data object Selected : SmartEvent
    data object Saved : SmartEvent
    data class Failed(val message: String) : SmartEvent
}

internal class SmartGroupsViewModel(
    private val repository: SmartGroupRepository,
    private val nodes: NodeRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(SmartUiState())
    val state: StateFlow<SmartUiState> = _state.asStateFlow()
    private val _events = MutableStateFlow<List<SmartEvent>>(emptyList())
    val events: StateFlow<List<SmartEvent>> = _events.asStateFlow()
    private var refreshJob: Job? = null
    private var runtimeJob: Job? = null

    fun setVisible(visible: Boolean) {
        if (!visible) {
            runtimeJob?.cancel()
            runtimeJob = null
            return
        }
        refresh(silent = _state.value.loaded)
        if (runtimeJob?.isActive == true) return
        runtimeJob = viewModelScope.launch {
            repository.observeRuntime().collect { result ->
                if (result.isSuccess && _state.value.busy.isEmpty()) refresh(silent = true)
            }
        }
    }

    fun refresh(silent: Boolean = false) {
        if (refreshJob?.isActive == true || _state.value.busy.isNotEmpty()) return
        if (!silent) _state.update { it.copy(loading = true, error = "") }
        refreshJob = viewModelScope.launch {
            try {
                val smart = repository.current()
                val groups = nodes.snapshot().groups
                _state.update { it.copy(smart = smart, nodes = groups, loaded = true, loading = false, error = "") }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _state.update { it.copy(loading = false, error = error.userMessage()) }
            }
        }
    }

    fun consumeEvents() = _events.update { emptyList() }

    fun generate(english: Boolean) = write("generate") {
        val response = repository.generate(english)
        emit(SmartEvent.Generated(response.summary))
        response.smart
    }

    /** 切换订阅分组范围或自选的分组，模块随即重新分组，地区分组内容随之变化。 */
    fun setSources(mode: String, groups: List<String>, english: Boolean) = write("sources") {
        val response = repository.setSources(mode, groups, english)
        emit(SmartEvent.Generated(response.summary))
        response.smart
    }

    fun select(id: String) = write("select:$id") {
        repository.select(id)
        emit(SmartEvent.Selected)
        repository.current()
    }

    /** 在分组内选择节点（manual 为空表示自动选择），节点页随之改用这个分组。 */
    fun selectNode(id: String, manual: SmartNodeRef?) = write("select:$id") {
        repository.selectNode(id, manual)
        emit(SmartEvent.Selected)
        repository.current()
    }

    fun test(id: String) = write(if (id.isEmpty()) "test" else "test:$id") {
        val smart = repository.test(id)
        val tested = smart.groups.filter { id.isEmpty() || it.id == id }
        val testedKeys = tested.flatMap { group -> group.members.map { it.key } }.toSet()
        val failed = tested.flatMap { group -> group.delays.filterValues { it < 0 }.keys }.toSet()
        _state.update { it.copy(failed = it.failed - testedKeys + failed) }
        smart
    }

    /** 新建（id 为空）或保存分组，成功后回调新的分组 id。 */
    fun save(id: String?, name: String, members: List<SmartNodeRef>, onDone: (String) -> Unit) {
        var savedId = ""
        write("save", onDone = { onDone(savedId) }) {
            val response = if (id == null) repository.create(name, members) else repository.save(id, name, members)
            savedId = response.id
            emit(SmartEvent.Saved)
            response.smart
        }
    }

    fun delete(id: String, onDone: () -> Unit) = write("delete", onDone = onDone) { repository.delete(id) }

    private fun write(name: String, onDone: () -> Unit = {}, block: suspend () -> SmartGroups) {
        if (_state.value.busy.isNotEmpty()) return
        _state.update { it.copy(busy = name) }
        viewModelScope.launch {
            try {
                val smart = block()
                _state.update { it.copy(smart = smart, busy = "", loaded = true) }
                onDone()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _state.update { it.copy(busy = "") }
                emit(SmartEvent.Failed(error.userMessage()))
            }
        }
    }

    private fun emit(event: SmartEvent) = _events.update { it + event }
}

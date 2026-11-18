package com.fanjv.netproxy.feature.chain.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fanjv.netproxy.core.ui.userMessage
import com.fanjv.netproxy.feature.apps.data.AppIconCache
import com.fanjv.netproxy.feature.apps.data.AppPackageRepository
import com.fanjv.netproxy.feature.chain.data.ChainRepository
import com.fanjv.netproxy.feature.chain.model.ChainAppChoice
import com.fanjv.netproxy.feature.chain.model.ChainImportResult
import com.fanjv.netproxy.feature.chain.model.ChainMode
import com.fanjv.netproxy.feature.chain.model.ChainState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal data class ChainUiState(
    val chain: ChainState = ChainState(),
    val loaded: Boolean = false,
    val loading: Boolean = false,
    /** 正在执行的写操作；非空时界面禁用其他写入，避免并发修改同一份设置。 */
    val busy: String = "",
    val error: String = "",
    val apps: List<ChainAppChoice> = emptyList(),
    val appsLoading: Boolean = false,
    val appsError: String = "",
)

/** 一次写操作的结果，交给页面用轻提示展示。 */
internal sealed interface ChainEvent {
    data class Imported(val result: ChainImportResult) : ChainEvent
    data class Failed(val message: String) : ChainEvent
    data object Saved : ChainEvent
}

internal class ChainViewModel(
    private val repository: ChainRepository,
    private val packages: AppPackageRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(ChainUiState())
    val state: StateFlow<ChainUiState> = _state.asStateFlow()
    private val _events = MutableStateFlow<List<ChainEvent>>(emptyList())
    val events: StateFlow<List<ChainEvent>> = _events.asStateFlow()
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
                val chain = repository.current()
                _state.update { it.copy(chain = chain, loaded = true, loading = false, error = "") }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _state.update { it.copy(loading = false, error = error.userMessage()) }
            }
        }
    }

    fun consumeEvents() = _events.update { emptyList() }

    fun setMode(mode: ChainMode) {
        val current = _state.value.chain
        var manual = current.manual
        // 切到需要手动后置的模式时默认选第一个后置，用户随后可以改选。
        if (mode.usesManual && manual.isEmpty()) manual = current.landings.firstOrNull()?.tag.orEmpty()
        write("mode") { repository.saveMode(mode.value, manual) }
    }

    fun setManual(tag: String) = write("manual") { repository.saveMode(_state.value.chain.mode, tag) }

    fun importLandings(content: String, type: String, onDone: () -> Unit) {
        if (_state.value.busy.isNotEmpty()) return
        _state.update { it.copy(busy = "import") }
        viewModelScope.launch {
            try {
                val response = repository.importLandings(content, type)
                _state.update { it.copy(chain = response.chain, busy = "") }
                emit(ChainEvent.Imported(response.result))
                onDone()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _state.update { it.copy(busy = "") }
                emit(ChainEvent.Failed(error.userMessage()))
            }
        }
    }

    fun renameLanding(tag: String, name: String) = write("rename") { repository.renameLanding(tag, name) }

    fun removeLanding(tag: String) = write("remove") { repository.removeLanding(tag) }

    fun testLandings(tag: String = "") = write(if (tag.isEmpty()) "test" else "test:$tag", announce = false) {
        repository.testLandings(tag)
    }

    fun savePolicy(id: String?, name: String, enabled: Boolean, landing: String, packages: List<String>, onDone: () -> Unit) =
        write("policy", onDone = onDone) {
            if (id == null) repository.createPolicy(name, enabled, landing, packages)
            else repository.savePolicy(id, name, enabled, landing, packages)
        }

    fun setPolicyEnabled(id: String, enabled: Boolean) {
        val policy = _state.value.chain.policies.firstOrNull { it.id == id } ?: return
        write("policy:$id", announce = false) {
            repository.savePolicy(id, policy.name, enabled, policy.landing, policy.packages)
        }
    }

    fun deletePolicy(id: String, onDone: () -> Unit) = write("policy", onDone = onDone) { repository.deletePolicy(id) }

    /** 读取全部用户的应用；同一包名合并为一项，因为策略按包名匹配所有用户。 */
    fun loadApps() {
        if (_state.value.appsLoading || _state.value.apps.isNotEmpty()) return
        _state.update { it.copy(appsLoading = true, appsError = "") }
        viewModelScope.launch {
            try {
                val snapshot = packages.snapshot()
                val apps = snapshot.users.flatMap { it.apps }
                    .groupBy { it.packageName }
                    .map { (name, entries) ->
                        val app = entries.first()
                        ChainAppChoice(name, app.label.ifBlank { name }, app.iconKey, app.system)
                    }
                    .sortedWith(compareBy<ChainAppChoice> { it.system }.thenBy { it.label.lowercase() })
                _state.update { it.copy(apps = apps, appsLoading = false) }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _state.update { it.copy(appsLoading = false, appsError = error.userMessage()) }
            }
        }
    }

    suspend fun loadIcons(keys: List<String>) = coroutineScope {
        val generation = AppIconCache.generation
        keys.filter { it.isNotEmpty() && AppIconCache.cachedIcon(it) == null }.distinct().map { key ->
            async {
                try {
                    AppIconCache.putIcon(key, packages.icon(key), generation)
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Exception) {
                    // 单个图标读取失败只显示占位图，不影响应用列表。
                }
            }
        }.awaitAll()
    }

    private fun write(
        name: String,
        announce: Boolean = true,
        onDone: () -> Unit = {},
        block: suspend () -> ChainState,
    ) {
        if (_state.value.busy.isNotEmpty()) return
        _state.update { it.copy(busy = name) }
        viewModelScope.launch {
            try {
                val chain = block()
                _state.update { it.copy(chain = chain, busy = "", loaded = true) }
                if (announce) emit(ChainEvent.Saved)
                onDone()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _state.update { it.copy(busy = "") }
                emit(ChainEvent.Failed(error.userMessage()))
            }
        }
    }

    private fun emit(event: ChainEvent) = _events.update { it + event }
}

package com.fanjv.netproxy.feature.chain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** 后置模式与模块 `data/chain/chain.json` 的 mode 字段一一对应。 */
internal enum class ChainMode(val value: String) {
    Off("off"), Manual("manual"), Policy("policy"), ManualPolicy("manual_policy");

    val usesManual: Boolean get() = this == Manual || this == ManualPolicy
    val usesPolicies: Boolean get() = this == Policy || this == ManualPolicy

    companion object {
        fun of(value: String): ChainMode = entries.firstOrNull { it.value == value } ?: Off
    }
}

/** 后置代理摘要；模块不会返回密码。 */
@Serializable
internal data class ChainLanding(
    val tag: String,
    val type: String,
    val server: String = "",
    val port: Int = 0,
    val username: String = "",
    @SerialName("has_password") val hasPassword: Boolean = false,
    val tls: Boolean = false,
)

@Serializable
internal data class ChainPolicy(
    val id: String,
    val name: String,
    val enabled: Boolean,
    val landing: String,
    val packages: List<String> = emptyList(),
)

@Serializable
internal data class ChainState(
    val mode: String = ChainMode.Off.value,
    val manual: String = "",
    val landings: List<ChainLanding> = emptyList(),
    val policies: List<ChainPolicy> = emptyList(),
    val running: Boolean = false,
    /** 前置实际使用的节点，形如「分组/节点」；服务停止时为空。 */
    val front: String = "",
    /** 「链式代理」选择器当前选中的后置。 */
    val landing: String = "",
    val delays: Map<String, Int> = emptyMap(),
) {
    val chainMode: ChainMode get() = ChainMode.of(mode)
}

@Serializable
internal data class ChainDiagnostic(
    val index: Int = 0,
    val source: String = "",
    val code: String = "",
    val message: String = "",
)

@Serializable
internal data class ChainImportResult(
    val added: List<String> = emptyList(),
    val skipped: List<String> = emptyList(),
    val diagnostics: List<ChainDiagnostic> = emptyList(),
)

@Serializable
internal data class ChainImportResponse(
    val chain: ChainState,
    @SerialName("import") val result: ChainImportResult,
)

@Serializable
internal data class ChainPolicyResponse(val chain: ChainState, val policy: ChainPolicy)

/** 应用选择器中的一个应用；同一包名在多个用户下只出现一次，策略按包名匹配。 */
internal data class ChainAppChoice(
    val packageName: String,
    val label: String,
    val iconKey: String,
    val system: Boolean,
)

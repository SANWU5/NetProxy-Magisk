package com.fanjv.netproxy.feature.smart.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** 按来源分组与节点名引用的一个节点。 */
@Serializable
internal data class SmartNodeRef(
    @SerialName("group_id") val groupId: String,
    val node: String,
)

/** 一个智能分组（按地区）或自建分组。 */
@Serializable
internal data class SmartGroup(
    val id: String,
    val name: String,
    val kind: String,
    val region: String = "",
    val members: List<SmartNodeRef> = emptyList(),
    /** 成员中仍然存在的节点数；为 0 时运行时不会生成这个分组。 */
    val available: Int = 0,
    /** 地区分组有手动加入或移除的节点；重新智能分组时会保留这些修改。 */
    val edited: Boolean = false,
    /** 分组内手动选择的节点；为空表示分组使用自动选择。从外部选择分组时沿用它。 */
    val manual: SmartNodeRef? = null,
    /** 分组当前实际使用的节点，形如「分组/节点」；服务停止时为空。 */
    val selected: String = "",
    val delay: Int = 0,
    /** 成员最近一次测速的延迟，键为「来源分组 id/节点名」；-1 表示刚才的测速失败或超时。 */
    val delays: Map<String, Int> = emptyMap(),
) {
    val custom: Boolean get() = kind == "custom"
    val selectedNode: String get() = selected.substringAfter('/', selected)
}

/** 成员在延迟表与选择中的键：「来源分组 id/节点名」。 */
internal val SmartNodeRef.key: String get() = "$groupId/$node"

@Serializable
internal data class SmartGroups(
    val groups: List<SmartGroup> = emptyList(),
    @SerialName("generated_at") val generatedAt: String = "",
    /** 节点页当前使用的智能分组 id；为空表示使用普通分组。 */
    val active: String = "",
    val running: Boolean = false,
    /** 重新智能分组使用的订阅分组范围；available 是可以选择的订阅与本地分组。 */
    val sources: SmartSources = SmartSources(),
    val available: List<SmartSourceGroup> = emptyList(),
)

/** 订阅分组范围：all 使用全部订阅与本地分组，custom 只使用 groups 中的分组。 */
@Serializable
internal data class SmartSources(
    val mode: String = MODE_ALL,
    val groups: List<String> = emptyList(),
) {
    val isCustom: Boolean get() = mode == MODE_CUSTOM

    companion object {
        const val MODE_ALL = "all"
        const val MODE_CUSTOM = "custom"
    }
}

@Serializable
internal data class SmartSourceGroup(
    val id: String,
    val name: String,
    val type: String = "",
    @SerialName("node_count") val nodeCount: Int = 0,
)

@Serializable
internal data class SmartSummary(
    val regions: Int = 0,
    val nodes: Int = 0,
    val unclassified: Int = 0,
    val informational: Int = 0,
)

@Serializable
internal data class SmartGenerateResponse(val smart: SmartGroups, val summary: SmartSummary)

@Serializable
internal data class SmartSaveResponse(val smart: SmartGroups, val id: String)

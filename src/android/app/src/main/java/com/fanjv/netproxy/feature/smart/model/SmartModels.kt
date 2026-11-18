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
    /** 自动选择当前使用的节点，形如「分组/节点」；服务停止时为空。 */
    val selected: String = "",
    val delay: Int = 0,
) {
    val custom: Boolean get() = kind == "custom"
    val selectedNode: String get() = selected.substringAfter('/', selected)
}

@Serializable
internal data class SmartGroups(
    val groups: List<SmartGroup> = emptyList(),
    @SerialName("generated_at") val generatedAt: String = "",
    /** 节点页当前使用的智能分组 id；为空表示使用普通分组。 */
    val active: String = "",
    val running: Boolean = false,
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

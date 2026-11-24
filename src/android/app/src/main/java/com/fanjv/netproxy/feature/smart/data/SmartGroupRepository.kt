package com.fanjv.netproxy.feature.smart.data

import com.fanjv.netproxy.core.api.ModuleApiClient
import com.fanjv.netproxy.feature.smart.model.SmartGenerateResponse
import com.fanjv.netproxy.feature.smart.model.SmartGroups
import com.fanjv.netproxy.feature.smart.model.SmartNodeRef
import com.fanjv.netproxy.feature.smart.model.SmartSaveResponse
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.put
import kotlinx.serialization.json.add
import kotlinx.serialization.json.putJsonArray

/** 智能分组与自建分组的模块接口。 */
internal class SmartGroupRepository(private val client: ModuleApiClient) {
    suspend fun current(): SmartGroups = client.read("/smart-groups")

    /** 节点或服务状态变化会改变自动选择的结果，页面据此刷新。 */
    fun observeRuntime(): Flow<Result<Unit>> = client.observe(setOf("groups", "status")).map { result -> result.map { } }

    /** 手动触发智能分组：重新按地区整理全部节点，保留手动修改与自建分组。 */
    suspend fun generate(english: Boolean): SmartGenerateResponse =
        decode(client.post("/smart-groups/generate", buildJsonObject { put("english", english) }).data)

    /** 设置订阅分组范围并立即重新分组：custom 只使用 groups 中的订阅或本地分组。 */
    suspend fun setSources(mode: String, groups: List<String>, english: Boolean): SmartGenerateResponse =
        decode(client.put("/smart-groups/sources", buildJsonObject {
            put("mode", mode)
            putJsonArray("groups") { groups.forEach(::add) }
            put("english", english)
        }).data)

    suspend fun create(name: String, members: List<SmartNodeRef>): SmartSaveResponse =
        decode(client.post("/smart-groups", body(name, members)).data)

    suspend fun save(id: String, name: String, members: List<SmartNodeRef>): SmartSaveResponse =
        decode(client.put("/smart-groups/$id", body(name, members)).data)

    suspend fun delete(id: String): SmartGroups = decode(client.delete("/smart-groups/$id").data)

    /** 让分组的自动选择重新测速；id 为空时测试全部分组。 */
    suspend fun test(id: String): SmartGroups = decode(client.post("/smart-groups/delay", buildJsonObject { put("id", id) }).data)

    /** 从外部选择分组：节点页改用这个分组，沿用分组内的选择（从未手动选择过时为自动选择）。 */
    suspend fun select(id: String) {
        client.put("/selection", buildJsonObject { put("smart_group_id", id) })
    }

    /** 在分组内选择节点；manual 为空表示改用分组的自动选择。节点页随之改用这个分组。 */
    suspend fun selectNode(id: String, manual: SmartNodeRef?) {
        client.put("/smart-groups/$id/selection", buildJsonObject {
            if (manual != null) put("manual", buildJsonObject {
                put("group_id", manual.groupId)
                put("node", manual.node)
            })
        })
    }

    private fun body(name: String, members: List<SmartNodeRef>) = buildJsonObject {
        put("name", name)
        putJsonArray("members") {
            members.forEach { member ->
                addJsonObject {
                    put("group_id", member.groupId)
                    put("node", member.node)
                }
            }
        }
    }

    private inline fun <reified T> decode(data: JsonElement): T = client.json.decodeFromJsonElement(data)
}

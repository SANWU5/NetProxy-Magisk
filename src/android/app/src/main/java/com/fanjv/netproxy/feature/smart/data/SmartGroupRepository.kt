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
import kotlinx.serialization.json.putJsonArray

/** 智能分组与自建分组的模块接口。 */
internal class SmartGroupRepository(private val client: ModuleApiClient) {
    suspend fun current(): SmartGroups = client.read("/smart-groups")

    /** 节点或服务状态变化会改变自动选择的结果，页面据此刷新。 */
    fun observeRuntime(): Flow<Result<Unit>> = client.observe(setOf("groups", "status")).map { result -> result.map { } }

    /** 手动触发智能分组：重新按地区整理全部节点，保留手动修改与自建分组。 */
    suspend fun generate(english: Boolean): SmartGenerateResponse =
        decode(client.post("/smart-groups/generate", buildJsonObject { put("english", english) }).data)

    suspend fun create(name: String, members: List<SmartNodeRef>): SmartSaveResponse =
        decode(client.post("/smart-groups", body(name, members)).data)

    suspend fun save(id: String, name: String, members: List<SmartNodeRef>): SmartSaveResponse =
        decode(client.put("/smart-groups/$id", body(name, members)).data)

    suspend fun delete(id: String): SmartGroups = decode(client.delete("/smart-groups/$id").data)

    /** 让分组的自动选择重新测速；id 为空时测试全部分组。 */
    suspend fun test(id: String): SmartGroups = decode(client.post("/smart-groups/delay", buildJsonObject { put("id", id) }).data)

    /** 节点页改用这个分组的自动选择。 */
    suspend fun select(id: String) {
        client.put("/selection", buildJsonObject { put("smart_group_id", id) })
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

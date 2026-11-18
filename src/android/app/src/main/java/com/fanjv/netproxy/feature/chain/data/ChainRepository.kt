package com.fanjv.netproxy.feature.chain.data

import com.fanjv.netproxy.feature.chain.model.ChainFront
import com.fanjv.netproxy.core.api.ModuleApiClient
import com.fanjv.netproxy.feature.chain.model.ChainImportResponse
import com.fanjv.netproxy.feature.chain.model.ChainPolicyResponse
import com.fanjv.netproxy.feature.chain.model.ChainState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

/** 链式代理的模块接口：后置代理、后置模式与按应用策略。 */
internal class ChainRepository(private val client: ModuleApiClient) {
    suspend fun current(): ChainState = client.read("/chain")

    /** 节点或服务状态变化会改变前置与后置的实际选择，页面据此刷新。 */
    fun observeRuntime(): Flow<Result<Unit>> = client.observe(setOf("groups", "status")).map { result -> result.map { } }

    suspend fun saveMode(mode: String, manual: String): ChainState =
        decode(client.put("/chain", buildJsonObject {
            put("mode", mode)
            put("manual", manual)
        }).data)

    /** 设置链式前置；front 为空表示跟随节点页的选择。 */
    suspend fun saveFront(front: ChainFront?): ChainState =
        decode(client.put("/chain/front", buildJsonObject {
            when {
                front == null -> Unit
                front.smartId.isNotEmpty() -> put("smart_id", front.smartId)
                else -> {
                    put("group_id", front.groupId)
                    if (front.node.isNotEmpty()) put("node", front.node)
                }
            }
        }).data)

    suspend fun importLandings(content: String, type: String): ChainImportResponse =
        client.json.decodeFromJsonElement(client.post("/chain/landings", buildJsonObject {
            put("content", content)
            put("type", type)
        }).data)

    suspend fun renameLanding(tag: String, name: String): ChainState =
        decode(client.put("/chain/landings", buildJsonObject {
            put("tag", tag)
            put("name", name)
        }).data)

    suspend fun removeLanding(tag: String): ChainState =
        decode(client.delete("/chain/landings", mapOf("tag" to tag)).data)

    suspend fun testLandings(tag: String): ChainState =
        decode(client.post("/chain/landings/delay", buildJsonObject { put("tag", tag) }).data)

    suspend fun createPolicy(name: String, enabled: Boolean, landing: String, packages: List<String>): ChainState =
        client.json.decodeFromJsonElement<ChainPolicyResponse>(
            client.post("/chain/policies", policyBody(name, enabled, landing, packages)).data
        ).chain

    suspend fun savePolicy(id: String, name: String, enabled: Boolean, landing: String, packages: List<String>): ChainState =
        decode(client.put("/chain/policies/$id", policyBody(name, enabled, landing, packages)).data)

    suspend fun deletePolicy(id: String): ChainState = decode(client.delete("/chain/policies/$id").data)

    private fun policyBody(name: String, enabled: Boolean, landing: String, packages: List<String>) = buildJsonObject {
        put("name", name)
        put("enabled", enabled)
        put("landing", landing)
        putJsonArray("packages") { packages.forEach(::add) }
    }

    private fun decode(data: JsonElement): ChainState = client.json.decodeFromJsonElement(data)
}

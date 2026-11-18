package com.fanjv.netproxy.feature.catalog.presentation.nodes.edit.components

import dev.aiingjie.coui.CouiCard
import dev.aiingjie.coui.CouiCategory
import dev.aiingjie.coui.CouiMenuRow
import dev.aiingjie.coui.CouiSwitchRow
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.fanjv.netproxy.R

@Composable
internal fun ServerConfigSection(
    tag: String,
    onTagChange: (String) -> Unit,
    server: String,
    onServerChange: (String) -> Unit,
    serverPort: String,
    onServerPortChange: (String) -> Unit,
    type: String,
    onTypeChange: (String) -> Unit,
    uuid: String,
    onUuidChange: (String) -> Unit,
    flow: String,
    onFlowChange: (String) -> Unit,
    security: String,
    onSecurityChange: (String) -> Unit,
    alterId: String,
    onAlterIdChange: (String) -> Unit,
    method: String,
    onMethodChange: (String) -> Unit,
    password: String,
    onPasswordChange: (String) -> Unit,
    plugin: String,
    onPluginChange: (String) -> Unit,
    pluginOpts: String,
    onPluginOptsChange: (String) -> Unit,
    upMbps: String,
    onUpMbpsChange: (String) -> Unit,
    downMbps: String,
    onDownMbpsChange: (String) -> Unit,
    obfsType: String,
    onObfsTypeChange: (String) -> Unit,
    obfsPassword: String,
    onObfsPasswordChange: (String) -> Unit,
    serverPorts: String,
    onServerPortsChange: (String) -> Unit,
    hopInterval: String,
    onHopIntervalChange: (String) -> Unit,
    congestionControl: String,
    onCongestionControlChange: (String) -> Unit,
    udpRelayMode: String,
    onUdpRelayModeChange: (String) -> Unit,
    udpOverStream: Boolean,
    onUdpOverStreamChange: (Boolean) -> Unit,
    zeroRttHandshake: Boolean,
    onZeroRttHandshakeChange: (Boolean) -> Unit,
    heartbeat: String,
    onHeartbeatChange: (String) -> Unit,
    focusManager: androidx.compose.ui.focus.FocusManager,
    alterIdLabel: String,
    udpRelayModeTitle: String
) {
    Column {
        // ==================== 1. 常规设置 ====================
        Column {
            CouiCategory(stringResource(R.string.basic_settings))
        }
        Column {
            NodeTextField(
                value = tag,
                onValueChange = { onTagChange(it) },
                label = stringResource(R.string.node_tag),
                singleLine = true,
                modifier = Modifier.padding(bottom = 12.dp),
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done)
            )
        }
        Column {
            ProtocolSelector(
                selectedProtocol = type,
                availableProtocols = listOf(
                    "vless",
                    "vmess",
                    "shadowsocks",
                    "trojan",
                    "hysteria2",
                    "tuic",
                    "anytls"
                ),
                onProtocolSelected = { onTypeChange(it) }
            )
        }
        Column {
            NodeTextField(
                value = server,
                onValueChange = { onServerChange(it) },
                label = stringResource(R.string.server_address_label),
                singleLine = true,
                modifier = Modifier.padding(bottom = 12.dp),
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done)
            )
        }
        Column {
            NodeTextField(
                value = serverPort,
                onValueChange = { onServerPortChange(it) },
                label = stringResource(R.string.server_port_label),
                singleLine = true,
                modifier = Modifier.padding(bottom = 12.dp),
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                keyboardOptions = KeyboardOptions(
                    imeAction = ImeAction.Done,
                    keyboardType = KeyboardType.Number
                )
            )
        }

        // ==================== 2. 协议专属设置 ====================
        Column {
            CouiCategory(stringResource(R.string.protocol_specific_config, type.uppercase()))
        }
        when (type) {
            "vless" -> {
                Column {
                    NodeTextField(
                        value = uuid,
                        onValueChange = { onUuidChange(it) },
                        label = stringResource(R.string.user_id),
                        singleLine = true,
                        modifier = Modifier.padding(bottom = 12.dp),
                        keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done)
                    )
                }
                Column {
                    val flowOptions = listOf("none", "xtls-rprx-vision")
                    CouiCard(
                        modifier = Modifier.padding(bottom = 12.dp)
                    ) {
                        CouiMenuRow(
                            title = stringResource(R.string.flow_control),
                            options = flowOptions,
                            selected = flowOptions.indexOf(flow).coerceAtLeast(0),
                            onSelect = { index -> onFlowChange(flowOptions[index]) }
                        )
                    }
                }
            }

            "vmess" -> {
                Column {
                    NodeTextField(
                        value = uuid,
                        onValueChange = { onUuidChange(it) },
                        label = stringResource(R.string.user_id),
                        singleLine = true,
                        modifier = Modifier.padding(bottom = 12.dp),
                        keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done)
                    )
                }
                Column {
                    val vmessSecurities =
                        listOf("auto", "none", "zero", "aes-128-gcm", "chacha20-poly1305")
                    CouiCard(
                        modifier = Modifier.padding(bottom = 12.dp)
                    ) {
                        CouiMenuRow(
                            title = stringResource(R.string.security_label),
                            options = vmessSecurities,
                            selected = vmessSecurities.indexOf(security)
                                .coerceAtLeast(0),
                            onSelect = { index ->
                                onSecurityChange(vmessSecurities[index])
                            }
                        )
                    }
                }
                Column {
                    NodeTextField(
                        value = alterId,
                        onValueChange = { onAlterIdChange(it) },
                        label = alterIdLabel,
                        singleLine = true,
                        modifier = Modifier.padding(bottom = 12.dp),
                        keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                        keyboardOptions = KeyboardOptions(
                            imeAction = ImeAction.Done,
                            keyboardType = KeyboardType.Number
                        )
                    )
                }
            }

            "shadowsocks" -> {
                Column {
                    val ssMethods = listOf(
                        "aes-128-gcm",
                        "aes-256-gcm",
                        "chacha20-ietf-poly1305",
                        "2022-blake3-aes-128-gcm",
                        "2022-blake3-aes-256-gcm",
                        "2022-blake3-chacha20-poly1305"
                    )
                    CouiCard(
                        modifier = Modifier.padding(bottom = 12.dp)
                    ) {
                        CouiMenuRow(
                            title = stringResource(R.string.method_label),
                            options = ssMethods,
                            selected = ssMethods.indexOf(method).coerceAtLeast(0),
                            onSelect = { index -> onMethodChange(ssMethods[index]) }
                        )
                    }
                }
                Column {
                    NodeTextField(
                        value = password,
                        onValueChange = { onPasswordChange(it) },
                        label = stringResource(R.string.password_key_label),
                        singleLine = true,
                        modifier = Modifier.padding(bottom = 12.dp),
                        keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done)
                    )
                }
                Column {
                    NodeTextField(
                        value = plugin,
                        onValueChange = { onPluginChange(it) },
                        label = stringResource(R.string.plugin_label),
                        singleLine = true,
                        modifier = Modifier.padding(bottom = 12.dp),
                        keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done)
                    )
                }
                Column {
                    NodeTextField(
                        value = pluginOpts,
                        onValueChange = { onPluginOptsChange(it) },
                        label = stringResource(R.string.plugin_options),
                        singleLine = true,
                        modifier = Modifier.padding(bottom = 12.dp),
                        keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done)
                    )
                }
            }

            "trojan", "anytls" -> {
                Column {
                    NodeTextField(
                        value = password,
                        onValueChange = { onPasswordChange(it) },
                        label = stringResource(R.string.password_only),
                        singleLine = true,
                        modifier = Modifier.padding(bottom = 12.dp),
                        keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done)
                    )
                }
            }

            "hysteria2" -> {
                Column {
                    NodeTextField(
                        value = password,
                        onValueChange = { onPasswordChange(it) },
                        label = stringResource(R.string.password_label),
                        singleLine = true,
                        modifier = Modifier.padding(bottom = 12.dp),
                        keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done)
                    )
                }
                Column {
                    NodeTextField(
                        value = upMbps,
                        onValueChange = { onUpMbpsChange(it) },
                        label = stringResource(R.string.up_mbps),
                        singleLine = true,
                        modifier = Modifier.padding(bottom = 12.dp),
                        keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                        keyboardOptions = KeyboardOptions(
                            imeAction = ImeAction.Done,
                            keyboardType = KeyboardType.Number
                        )
                    )
                }
                Column {
                    NodeTextField(
                        value = downMbps,
                        onValueChange = { onDownMbpsChange(it) },
                        label = stringResource(R.string.down_mbps),
                        singleLine = true,
                        modifier = Modifier.padding(bottom = 12.dp),
                        keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                        keyboardOptions = KeyboardOptions(
                            imeAction = ImeAction.Done,
                            keyboardType = KeyboardType.Number
                        )
                    )
                }
                Column {
                    val obfsTypeList = listOf("none", "salamander")
                    CouiCard(
                        modifier = Modifier.padding(bottom = 12.dp)
                    ) {
                        CouiMenuRow(
                            title = stringResource(R.string.obfs_type),
                            options = obfsTypeList,
                            selected = obfsTypeList.indexOf(obfsType).coerceAtLeast(0),
                            onSelect = { index ->
                                onObfsTypeChange(obfsTypeList[index])
                            }
                        )
                    }
                }
                if (obfsType != "none") {
                    Column {
                        NodeTextField(
                            value = obfsPassword,
                            onValueChange = { onObfsPasswordChange(it) },
                            label = stringResource(R.string.obfs_password),
                            singleLine = true,
                            modifier = Modifier.padding(bottom = 12.dp),
                            keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done)
                        )
                    }
                }
                Column {
                    NodeTextField(
                        value = serverPorts,
                        onValueChange = { onServerPortsChange(it) },
                        label = stringResource(R.string.server_ports_hop),
                        singleLine = true,
                        modifier = Modifier.padding(bottom = 12.dp),
                        keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                        keyboardOptions = KeyboardOptions(
                            imeAction = ImeAction.Done,
                            keyboardType = KeyboardType.Number
                        )
                    )
                }
                Column {
                    NodeTextField(
                        value = hopInterval,
                        onValueChange = { onHopIntervalChange(it) },
                        label = stringResource(R.string.hop_interval_seconds),
                        singleLine = true,
                        modifier = Modifier.padding(bottom = 12.dp),
                        keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                        keyboardOptions = KeyboardOptions(
                            imeAction = ImeAction.Done,
                            keyboardType = KeyboardType.Text
                        )
                    )
                }
            }

            "tuic" -> {
                Column {
                    NodeTextField(
                        value = uuid,
                        onValueChange = { onUuidChange(it) },
                        label = stringResource(R.string.user_id_uuid),
                        singleLine = true,
                        modifier = Modifier.padding(bottom = 12.dp),
                        keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done)
                    )
                }
                Column {
                    NodeTextField(
                        value = password,
                        onValueChange = { onPasswordChange(it) },
                        label = stringResource(R.string.password_label),
                        singleLine = true,
                        modifier = Modifier.padding(bottom = 12.dp),
                        keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done)
                    )
                }
                Column {
                    val ccOptions = listOf("cubic", "bbr", "new_reno")
                    val urmOptions = listOf("quic", "native")
                    CouiCard(
                        modifier = Modifier.padding(bottom = 12.dp)
                    ) {
                        CouiMenuRow(
                            title = stringResource(R.string.congestion_control),
                            options = ccOptions,
                            selected = ccOptions.indexOf(congestionControl)
                                .coerceAtLeast(0),
                            onSelect = { index ->
                                onCongestionControlChange(ccOptions[index])
                            }
                        )
                        CouiMenuRow(
                            title = udpRelayModeTitle,
                            options = urmOptions,
                            selected = urmOptions.indexOf(udpRelayMode)
                                .coerceAtLeast(0),
                            onSelect = { index ->
                                onUdpRelayModeChange(urmOptions[index])
                            }
                        )
                        CouiSwitchRow(
                            title = stringResource(R.string.udp_over_stream),
                            checked = udpOverStream,
                            onCheckedChange = { onUdpOverStreamChange(it) }
                        )
                        CouiSwitchRow(
                            title = stringResource(R.string.zero_rtt_handshake),
                            checked = zeroRttHandshake,
                            onCheckedChange = { onZeroRttHandshakeChange(it) }
                        )
                    }
                }
                Column {
                    NodeTextField(
                        value = heartbeat,
                        onValueChange = { onHeartbeatChange(it) },
                        label = stringResource(R.string.heartbeat_interval_seconds),
                        singleLine = true,
                        modifier = Modifier.padding(bottom = 12.dp),
                        keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                        keyboardOptions = KeyboardOptions(
                            imeAction = ImeAction.Done,
                            keyboardType = KeyboardType.Text
                        )
                    )
                }
            }
        }


    }
}


package com.fanjv.netproxy.feature.catalog.presentation.nodes.edit.components

import dev.aiingjie.coui.CouiCard
import dev.aiingjie.coui.CouiCategory
import dev.aiingjie.coui.CouiMenuRow
import dev.aiingjie.coui.CouiSwitchRow
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.fanjv.netproxy.R

/** TLS、uTLS、Reality 和 ECH 配置区。 */
@Composable
internal fun TlsConfigSection(
    enabled: Boolean,
    serverName: String,
    insecure: Boolean,
    disableSni: Boolean,
    alpn: String,
    fingerprint: String,
    realityEnabled: Boolean,
    realityPublicKey: String,
    realityShortId: String,
    echEnabled: Boolean,
    echConfig: String,
    echQueryServerName: String,
    alpnLabel: String,
    utlsFingerprintLabel: String,
    realityPublicKeyLabel: String,
    realityShortIdLabel: String,
    echConfigLabel: String,
    echDnsServerNameLabel: String,
    onEnabledChange: (Boolean) -> Unit,
    onServerNameChange: (String) -> Unit,
    onInsecureChange: (Boolean) -> Unit,
    onDisableSniChange: (Boolean) -> Unit,
    onAlpnChange: (String) -> Unit,
    onFingerprintChange: (String) -> Unit,
    onRealityEnabledChange: (Boolean) -> Unit,
    onRealityPublicKeyChange: (String) -> Unit,
    onRealityShortIdChange: (String) -> Unit,
    onEchEnabledChange: (Boolean) -> Unit,
    onEchConfigChange: (String) -> Unit,
    onEchQueryServerNameChange: (String) -> Unit,
    onImeDone: () -> Unit
) {
    Column(verticalArrangement = Arrangement.Top) {
        CouiCategory(stringResource(R.string.security_tls_config_security))
        CouiCard(
            modifier = Modifier.padding(bottom = 12.dp)
        ) {
            CouiSwitchRow(
                title = stringResource(R.string.enable_tls),
                checked = enabled,
                onCheckedChange = onEnabledChange
            )
        }
        if (enabled) {
            EditorField(
                serverName,
                stringResource(R.string.server_name),
                onServerNameChange,
                onImeDone
            )
            CouiCard(
                modifier = Modifier.padding(bottom = 12.dp)
            ) {
                CouiSwitchRow(
                    title = stringResource(R.string.allow_insecure),
                    checked = insecure,
                    onCheckedChange = onInsecureChange
                )
                CouiSwitchRow(
                    title = stringResource(R.string.disable_sni),
                    checked = disableSni,
                    onCheckedChange = onDisableSniChange
                )
            }
            EditorField(alpn, alpnLabel, onAlpnChange, onImeDone)
            CouiCard(
                modifier = Modifier.padding(bottom = 12.dp)
            ) {
                val fingerprints = listOf("chrome", "firefox", "safari", "randomised", "none")
                CouiMenuRow(
                    title = utlsFingerprintLabel,
                    options = fingerprints,
                    selected = fingerprints.indexOf(fingerprint).coerceAtLeast(0),
                    onSelect = { index -> onFingerprintChange(fingerprints[index]) }
                )
                CouiSwitchRow(
                    title = stringResource(R.string.enable_reality),
                    checked = realityEnabled,
                    onCheckedChange = onRealityEnabledChange
                )
            }
            if (realityEnabled) {
                EditorField(
                    realityPublicKey,
                    realityPublicKeyLabel,
                    onRealityPublicKeyChange,
                    onImeDone
                )
                EditorField(realityShortId, realityShortIdLabel, onRealityShortIdChange, onImeDone)
            }
            CouiCard(
                modifier = Modifier.padding(bottom = 12.dp)
            ) {
                CouiSwitchRow(
                    title = stringResource(R.string.enable_ech),
                    checked = echEnabled,
                    onCheckedChange = onEchEnabledChange
                )
            }
            if (echEnabled) {
                NodeTextField(
                    value = echConfig,
                    onValueChange = onEchConfigChange,
                    label = echConfigLabel,
                    minLines = 2,
                    maxLines = 4,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                EditorField(
                    echQueryServerName,
                    echDnsServerNameLabel,
                    onEchQueryServerNameChange,
                    onImeDone
                )
            }
        }
    }
}


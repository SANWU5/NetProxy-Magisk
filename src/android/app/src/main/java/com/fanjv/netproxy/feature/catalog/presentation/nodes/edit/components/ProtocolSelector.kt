package com.fanjv.netproxy.feature.catalog.presentation.nodes.edit.components

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.fanjv.netproxy.R
import dev.aiingjie.coui.CouiCard
import dev.aiingjie.coui.CouiMenuRow

/** 节点协议选择器。 */
@Composable
internal fun ProtocolSelector(
    selectedProtocol: String,
    availableProtocols: List<String>,
    onProtocolSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    CouiCard(modifier.padding(bottom = 12.dp)) {
        CouiMenuRow(
            title = stringResource(R.string.protocol_type),
            options = availableProtocols.map(String::uppercase),
            selected = availableProtocols.indexOf(selectedProtocol).coerceAtLeast(0),
            onSelect = { index ->
                availableProtocols.getOrNull(index)?.let(onProtocolSelected)
            }
        )
    }
}

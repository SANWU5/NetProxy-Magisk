package com.fanjv.netproxy.feature.catalog.presentation.nodes.edit.components

import dev.aiingjie.coui.CouiCard
import dev.aiingjie.coui.CouiCategory
import dev.aiingjie.coui.CouiMenuRow
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.fanjv.netproxy.R

/** 传输层配置区。 */
@Composable
internal fun TransportSection(
    transportType: String,
    path: String,
    host: String,
    serviceName: String,
    onTransportTypeChange: (String) -> Unit,
    onPathChange: (String) -> Unit,
    onHostChange: (String) -> Unit,
    onServiceNameChange: (String) -> Unit,
    onImeDone: () -> Unit
) {
    val types = listOf("none", "ws", "grpc", "http", "httpupgrade")
    Column(verticalArrangement = Arrangement.Top) {
        CouiCategory(stringResource(R.string.transport_settings))
        CouiCard(
            modifier = Modifier.padding(bottom = 12.dp)
        ) {
            CouiMenuRow(
                title = stringResource(R.string.transport_protocol),
                options = types.map { if (it == "none") "DIRECT (TCP)" else it.uppercase() },
                selected = types.indexOf(transportType).coerceAtLeast(0),
                onSelect = { index -> onTransportTypeChange(types[index]) }
            )
        }
        if (transportType in setOf("ws", "httpupgrade", "http", "h2")) {
            EditorField(
                value = path,
                label = stringResource(R.string.path_default),
                onValueChange = onPathChange,
                onImeDone = onImeDone
            )
            EditorField(
                value = host,
                label = stringResource(R.string.domain_name_host),
                onValueChange = onHostChange,
                onImeDone = onImeDone
            )
        }
        if (transportType == "grpc") {
            EditorField(
                value = serviceName,
                label = stringResource(R.string.service_name),
                onValueChange = onServiceNameChange,
                onImeDone = onImeDone
            )
        }
    }
}



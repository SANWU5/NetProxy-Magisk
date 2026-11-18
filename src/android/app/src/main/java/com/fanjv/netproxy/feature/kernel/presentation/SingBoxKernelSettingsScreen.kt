package com.fanjv.netproxy.feature.kernel.presentation

import com.fanjv.netproxy.core.ui.component.MenuAction
import com.fanjv.netproxy.core.ui.component.CouiBarMenuButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fanjv.netproxy.R
import com.fanjv.netproxy.core.di.netProxyViewModel
import com.fanjv.netproxy.navigation.LocalNavigator
import com.fanjv.netproxy.navigation.Route
import dev.aiingjie.coui.CouiGroup
import dev.aiingjie.coui.CouiJumpRow
import dev.aiingjie.coui.CouiLoading
import dev.aiingjie.coui.CouiRow
import dev.aiingjie.coui.CouiSubPage
import dev.aiingjie.coui.LocalCouiOverlay
import dev.aiingjie.coui.couiCardItems

/** 主配置分区、本地规则和只读运行时共用配置工作台。 */
@Composable
internal fun SingBoxKernelSettingsScreen(
    viewModel: SingBoxConfigViewModel = netProxyViewModel(),
    onBack: () -> Unit,
) {
    val navigator = LocalNavigator.current
    val overlay = LocalCouiOverlay.current
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { viewModel.refreshDocuments() }

    val checkSuccess = stringResource(R.string.singbox_check_success)
    val checkFailed = stringResource(R.string.singbox_check_failed)
    val restartText = stringResource(R.string.restart_core)
    val checkText = stringResource(R.string.singbox_check_all)
    val sections = listOf(
        SingBoxDocumentCategory.Config to stringResource(R.string.singbox_main_config),
        SingBoxDocumentCategory.LocalRule to stringResource(R.string.singbox_rule_files),
        SingBoxDocumentCategory.Runtime to stringResource(R.string.singbox_runtime_configs),
    )

    CouiSubPage(
        title = stringResource(R.string.kernel_settings),
        onBack = onBack,
        actions = {
            CouiBarMenuButton(stringResource(R.string.more_actions), listOf(
                MenuAction(restartText) { viewModel.restartService() },
                MenuAction(checkText) {
                    viewModel.checkConfig { success -> overlay.showSnack(if (success) checkSuccess else checkFailed) }
                },
            ), enabled = state.documents.isNotEmpty())
        },
    ) {
        if (state.documents.isEmpty()) {
            item {
                CouiGroup(first = true) {
                    if (state.isLoadingDocuments) CouiRow(stringResource(R.string.loading), leading = { CouiLoading() })
                    else CouiRow(stringResource(
                        if (state.documentsError) R.string.singbox_documents_load_failed else R.string.singbox_documents_empty))
                }
            }
            return@CouiSubPage
        }
        var first = true
        sections.forEach { (category, title) ->
            val documents = state.documents.filter { it.category == category }
            couiCardItems(documents, key = { "${category.name}:${it.id}" }, title = title, first = first) { document ->
                CouiJumpRow(documentTitle(document), { navigator.push(Route.JsonEdit(document.id)) },
                    summary = documentSummary(document))
            }
            if (documents.isNotEmpty()) first = false
        }
    }
}

@Composable
internal fun documentTitle(document: SingBoxDocument): String = when (
    if (document.id == "inbound" || document.id.startsWith("inbound/")) document.id
    else document.section.ifEmpty { document.id }
) {
    "inbound" -> stringResource(R.string.inbound_full_json)
    "inbound/backend" -> stringResource(R.string.inbound_backend)
    "inbound/root_policy" -> stringResource(R.string.inbound_root_processes)
    "inbound/ebpf" -> stringResource(R.string.ebpf_core_settings)
    "inbound/tun" -> stringResource(R.string.tun_settings)
    "singbox/config.json" -> stringResource(R.string.singbox_document_full)
    "outbounds" -> stringResource(R.string.singbox_document_outbounds)
    "log" -> stringResource(R.string.singbox_document_log)
    "experimental" -> stringResource(R.string.singbox_document_experimental)
    "dns" -> stringResource(R.string.singbox_document_dns)
    "inbounds" -> stringResource(R.string.singbox_document_inbounds)
    "providers" -> stringResource(R.string.singbox_document_providers)
    "endpoints" -> stringResource(R.string.singbox_document_endpoints)
    "ntp" -> stringResource(R.string.singbox_document_ntp)
    "certificate" -> stringResource(R.string.singbox_document_certificate)
    "certificate_providers" -> stringResource(R.string.singbox_document_certificate_providers)
    "network_namespaces" -> stringResource(R.string.singbox_document_network_namespaces)
    "route" -> stringResource(R.string.singbox_document_route)
    "http_clients" -> stringResource(R.string.singbox_document_http_clients)
    "services" -> stringResource(R.string.singbox_document_services)
    else -> document.filename
}

@Composable
private fun documentSummary(document: SingBoxDocument): String {
    val description = when (document.section.ifEmpty { document.id }) {
        "singbox/config.json" -> stringResource(R.string.singbox_document_full_summary)
        "outbounds" -> stringResource(R.string.singbox_document_outbounds_summary)
        "log" -> stringResource(R.string.singbox_document_log_summary)
        "experimental" -> stringResource(R.string.singbox_document_experimental_summary)
        "dns" -> stringResource(R.string.singbox_document_dns_summary)
        "inbounds" -> stringResource(R.string.singbox_document_inbounds_summary)
        "providers" -> stringResource(R.string.singbox_document_providers_summary)
        "endpoints" -> stringResource(R.string.singbox_document_endpoints_summary)
        "ntp" -> stringResource(R.string.singbox_document_ntp_summary)
        "certificate" -> stringResource(R.string.singbox_document_certificate_summary)
        "certificate_providers" -> stringResource(R.string.singbox_document_certificate_providers_summary)
        "network_namespaces" -> stringResource(R.string.singbox_document_network_namespaces_summary)
        "route" -> stringResource(R.string.singbox_document_route_summary)
        "http_clients" -> stringResource(R.string.singbox_document_http_clients_summary)
        "services" -> stringResource(R.string.singbox_document_services_summary)
        else -> when (document.category) {
            SingBoxDocumentCategory.LocalRule -> stringResource(R.string.singbox_document_source_summary)
            SingBoxDocumentCategory.Runtime -> stringResource(R.string.singbox_document_runtime_summary)
            SingBoxDocumentCategory.Config -> stringResource(R.string.singbox_document_custom_summary)
            SingBoxDocumentCategory.Inbound -> stringResource(R.string.inbound_full_json)
        }
    }
    return if (document.editable) {
        "${document.filename} · $description"
    } else {
        "${document.filename} · ${stringResource(R.string.read_only)} · $description"
    }
}

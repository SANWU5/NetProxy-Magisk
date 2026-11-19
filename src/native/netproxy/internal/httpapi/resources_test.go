package httpapi

import (
	"context"
	"encoding/json/jsontext"
	json "encoding/json/v2"
	"net/http"
	"net/http/httptest"
	"net/url"
	"os"
	"path/filepath"
	"strings"
	"testing"
	"time"

	"github.com/Fanju6/NetProxy-Magisk/src/native/netproxy/internal/apiconfig"
	"github.com/Fanju6/NetProxy-Magisk/src/native/netproxy/internal/catalog"
	"github.com/Fanju6/NetProxy-Magisk/src/native/netproxy/internal/chain"
	moduleconfig "github.com/Fanju6/NetProxy-Magisk/src/native/netproxy/internal/config"
	"github.com/Fanju6/NetProxy-Magisk/src/native/netproxy/internal/contract"
	"github.com/Fanju6/NetProxy-Magisk/src/native/netproxy/internal/module"
	"github.com/Fanju6/NetProxy-Magisk/src/native/netproxy/internal/paths"
	"github.com/Fanju6/NetProxy-Magisk/src/native/netproxy/internal/service"
	"github.com/Fanju6/NetProxy-Magisk/src/native/netproxy/internal/smartgroup"
)

const resourceTestModule = `{
  "auto_start": false,
  "selection": {"group_id": "default", "node_tag": ""},
  "wifi": {"enabled": false, "mode": "blacklist", "blacklist": [], "whitelist": [], "proxy_on_non_wifi": true}
}`

const resourceTestInbound = `{
  "backend": "tun", "root_policy": "default",
  "app": {"enabled": false, "mode": "blacklist", "proxy_apps": [], "bypass_apps": []},
  "ebpf": {"type": "ebpf", "tag": "netproxy-in", "local": {"enabled": true}, "shared": {"enabled": false}},
  "tun": {"type": "tun", "tag": "netproxy-in", "interface_name": "netproxy", "address": ["172.19.0.1/30"], "auto_route": true, "auto_redirect": true}
}`

type resourceResponse struct {
	Schema  int            `json:"schema"`
	OK      bool           `json:"ok"`
	Code    string         `json:"code"`
	Message string         `json:"message"`
	Data    jsontext.Value `json:"data"`
}

func resourceFixture(t testing.TB) *Server {
	t.Helper()
	root := t.TempDir()
	t.Setenv("NETPROXY_MODULE_DIR", root)
	t.Setenv("NETPROXY_DEV_ROOT", filepath.Join(root, "dev"))
	options := module.NewOptions(root)
	options.Telemetry = nil
	options.RequestTimeout = 20 * time.Millisecond
	options.ServiceAddress = "127.0.0.1:1"
	resourceWriteFile(t, options.ModuleConfig, resourceTestModule)
	resourceWriteFile(t, options.InboundConfig, resourceTestInbound)
	resourceWriteFile(t, paths.SingBoxConfig(options.SingBoxDir), `{"experimental":{"clash_api":{"default_mode":"Rule"}},"route":{"rules":[{"clash_mode":"Direct","action":"route","outbound":"direct"}]}}`)
	if err := catalog.EnsureGroup(t.Context(), catalog.GroupOptions{Root: options.CatalogRoot, GroupID: "default", Name: "本地配置", Type: "local"}); err != nil {
		t.Fatal(err)
	}
	if _, err := catalog.AppendNode(t.Context(), catalog.MutationOptions{
		GroupDir: filepath.Join(options.CatalogRoot, "default"), GroupID: "default", Type: "local",
		Input: "socks://127.0.0.1:1080#" + url.PathEscape("节点 / 02"),
	}); err != nil {
		t.Fatal(err)
	}
	config, err := apiconfig.Initialize(t.Context(), filepath.Join(root, "config", "api.json"))
	if err != nil {
		t.Fatal(err)
	}
	server, err := New(options, config)
	if err != nil {
		t.Fatal(err)
	}
	return server
}

func resourceWriteFile(t testing.TB, path, content string) {
	t.Helper()
	if err := os.MkdirAll(filepath.Dir(path), 0o700); err != nil {
		t.Fatal(err)
	}
	if err := os.WriteFile(path, []byte(content), 0o600); err != nil {
		t.Fatal(err)
	}
}

func resourceJSON(t *testing.T, value any) string {
	t.Helper()
	content, err := json.Marshal(value, json.Deterministic(true))
	if err != nil {
		t.Fatal(err)
	}
	return string(content)
}

func resourceRequest(t *testing.T, server *Server, method, target, body string) (*httptest.ResponseRecorder, resourceResponse) {
	t.Helper()
	var request *http.Request
	if body == "" {
		request = httptest.NewRequest(method, target, nil)
	} else {
		request = httptest.NewRequest(method, target, strings.NewReader(body))
		request.Header.Set("Content-Type", "application/json")
	}
	request.Host = server.config.Listen
	request.Header.Set("Authorization", "Bearer "+server.config.Secret)
	response := httptest.NewRecorder()
	server.ServeHTTP(response, request)
	var result resourceResponse
	if err := json.Unmarshal(response.Body.Bytes(), &result); err != nil {
		t.Fatalf("无效响应 %d: %s: %v", response.Code, response.Body.String(), err)
	}
	if result.Schema != 1 || result.Code == "" || result.Message == "" || result.OK != (response.Code == http.StatusOK) {
		t.Fatalf("响应契约不一致 %d: %+v", response.Code, result)
	}
	if response.Header().Get("Cache-Control") != "no-store" {
		t.Fatal("响应允许缓存私有数据")
	}
	return response, result
}

func resourceAssertSuccess(t *testing.T, server *Server, method, target, body, code string, data any) {
	t.Helper()
	response, result := resourceRequest(t, server, method, target, body)
	if response.Code != http.StatusOK || result.Code != code {
		t.Fatalf("请求 %s %s 失败 %d: %s", method, target, response.Code, response.Body.String())
	}
	if data != nil {
		if err := json.Unmarshal(result.Data, data); err != nil {
			t.Fatal(err)
		}
	}
}

func TestResourcesReadWhileCoreStopped(t *testing.T) {
	server := resourceFixture(t)
	var status service.Status
	resourceAssertSuccess(t, server, "GET", "/api/v1/service", "", "service.status", &status)
	if status.State != "stopped" || status.PID != nil || status.ActiveBackend != nil || status.ConfiguredBackend != "tun" || status.OutboundMode != "Rule" {
		t.Fatalf("停止状态错误: %+v", status)
	}
	for _, request := range []struct{ path, code string }{
		{"/api/v1/catalog", "catalog.groups"},
		{"/api/v1/nodes?group_id=default", "node.list"},
		{"/api/v1/nodes/snapshot?group_id=default", "node.snapshot"},
		{"/api/v1/selection", "node.current"},
		{"/api/v1/mode", "mode.current"},
		{"/api/v1/chain", "chain.current"},
		{"/api/v1/subscriptions", "subscription.list"},
		{"/api/v1/configs", "config.list"},
		{"/api/v1/config?target=inbound", "config.read"},
		{"/api/v1/app-policy", "app.list"},
		{"/api/v1/logs?kind=service", "logs.show"},
	} {
		t.Run(request.path, func(t *testing.T) {
			resourceAssertSuccess(t, server, "GET", request.path, "", request.code, nil)
		})
	}
	var groups []catalog.GroupSnapshot
	resourceAssertSuccess(t, server, "GET", "/api/v1/nodes", "", "node.list", &groups)
	if len(groups) != 1 || len(groups[0].Nodes) != 1 || groups[0].Nodes[0].Tag != "节点 / 02" {
		t.Fatalf("停止时持久节点不可读: %+v", groups)
	}
	var summaries []catalog.GroupSummary
	resourceAssertSuccess(t, server, "GET", "/api/v1/subscriptions", "", "subscription.list", &summaries)
	if len(summaries) != 1 || summaries[0].ID != "default" || summaries[0].Type != "local" || !summaries[0].Active {
		t.Fatalf("订阅页遗漏活动本地分组: %+v", summaries)
	}
	var local catalog.GroupSnapshot
	resourceAssertSuccess(t, server, "GET", "/api/v1/subscriptions/default", "", "subscription.show", &local)
	if local.Group.ID != "default" || !local.Group.Active || len(local.Nodes) != 1 {
		t.Fatalf("本地分组详情不可读: %+v", local)
	}
	for _, path := range []string{server.options.StateFile, server.options.WorkerPIDFile, server.options.RuntimeDir} {
		if _, err := os.Stat(path); !os.IsNotExist(err) {
			t.Fatalf("读取启动了服务或 Worker: %s %v", path, err)
		}
	}
}

func TestResourcesChainLandingsModesAndPolicies(t *testing.T) {
	server := resourceFixture(t)
	var view module.ChainView
	resourceAssertSuccess(t, server, "GET", "/api/v1/chain", "", "chain.current", &view)
	if view.Mode != "off" || len(view.Landings) != 0 || view.Running {
		t.Fatalf("初始链式设置错误: %+v", view)
	}
	response, result := resourceRequest(t, server, "PUT", "/api/v1/chain", `{"mode":"manual","manual":""}`)
	if response.Code != http.StatusUnprocessableEntity || result.Code != "chain.invalid" {
		t.Fatalf("没有后置时开启手动模式应返回 422: %s", response.Body.String())
	}
	var imported struct {
		Chain  module.ChainView   `json:"chain"`
		Import chain.ImportResult `json:"import"`
	}
	body := resourceJSON(t, map[string]string{"content": "203.0.113.1:1080:user:pass\nhttp://u:p@203.0.113.2:8080#办公\nbad line", "type": "socks"})
	response, result = resourceRequest(t, server, "POST", "/api/v1/chain/landings", body)
	if response.Code != http.StatusOK || result.Code != "chain.landings_imported" {
		t.Fatalf("导入失败: %s", response.Body.String())
	}
	if err := json.Unmarshal(result.Data, &imported); err != nil {
		t.Fatal(err)
	}
	if len(imported.Import.Added) != 2 || len(imported.Import.Diagnostics) != 1 || len(imported.Chain.Landings) != 2 {
		t.Fatalf("导入结果错误: %+v", imported)
	}
	if imported.Chain.Landings[0].Username != "user" || !imported.Chain.Landings[0].HasPassword || strings.Contains(response.Body.String(), `"pass"`) {
		t.Fatalf("后置摘要错误或泄露密码: %+v", imported.Chain.Landings[0])
	}
	resourceAssertSuccess(t, server, "PUT", "/api/v1/chain", `{"mode":"manual_policy","manual":"办公"}`, "chain.saved", &view)
	if view.Mode != "manual_policy" || view.Manual != "办公" {
		t.Fatalf("模式未保存: %+v", view)
	}
	var created struct {
		Chain  module.ChainView `json:"chain"`
		Policy chain.Policy     `json:"policy"`
	}
	resourceAssertSuccess(t, server, "POST", "/api/v1/chain/policies", `{"name":"海外","enabled":true,"landing":"203.0.113.1:1080","packages":["com.example.app"," com.example.app "]}`, "chain.policy_created", &created)
	if created.Policy.ID == "" || len(created.Policy.Packages) != 1 || len(created.Chain.Policies) != 1 {
		t.Fatalf("策略创建结果错误: %+v", created)
	}
	// 不同策略组可以包含同一应用（按顺序先命中的生效）；启用的白名单组必须选择应用。
	response, result = resourceRequest(t, server, "POST", "/api/v1/chain/policies", `{"name":"空组","enabled":true,"landing":"办公","packages":[]}`)
	if response.Code != http.StatusUnprocessableEntity || !strings.Contains(result.Message, "至少需要选择一个应用") {
		t.Fatalf("启用的空白名单组应被拒绝: %s", response.Body.String())
	}
	response, _ = resourceRequest(t, server, "PUT", "/api/v1/chain/policies/order", `{"ids":["missing"]}`)
	if response.Code != http.StatusUnprocessableEntity {
		t.Fatalf("无效的策略组顺序应被拒绝: %s", response.Body.String())
	}
	response, result = resourceRequest(t, server, "DELETE", "/api/v1/chain/landings?tag="+url.QueryEscape("办公"), "")
	if response.Code != http.StatusUnprocessableEntity || !strings.Contains(result.Message, "手动模式") {
		t.Fatalf("删除手动后置应被拒绝: %s", response.Body.String())
	}
	resourceAssertSuccess(t, server, "PUT", "/api/v1/chain/landings", `{"tag":"203.0.113.1:1080","name":"住宅"}`, "chain.landing_renamed", &view)
	if view.Policies[0].Landing != "住宅" {
		t.Fatalf("重命名未同步策略引用: %+v", view.Policies)
	}
	response, result = resourceRequest(t, server, "PUT", "/api/v1/chain/policies/missing", `{"name":"x","enabled":false,"landing":"住宅","packages":[]}`)
	if response.Code != http.StatusNotFound || result.Code != "chain.policy_not_found" {
		t.Fatalf("不存在的策略应返回 404: %s", response.Body.String())
	}
	resourceAssertSuccess(t, server, "PUT", "/api/v1/chain/policies/"+created.Policy.ID, `{"name":"海外应用","enabled":false,"landing":"住宅","packages":["com.example.app"]}`, "chain.policy_saved", &view)
	resourceAssertSuccess(t, server, "DELETE", "/api/v1/chain/policies/"+created.Policy.ID, "", "chain.policy_deleted", &view)
	if len(view.Policies) != 0 {
		t.Fatalf("策略未删除: %+v", view.Policies)
	}
	response, result = resourceRequest(t, server, "POST", "/api/v1/chain/landings/delay", `{"tag":""}`)
	if response.Code != http.StatusConflict || result.Code != "chain.service_stopped" {
		t.Fatalf("服务停止时测速应返回 409: %s", response.Body.String())
	}
	if _, err := os.Stat(filepath.Join(server.options.ModuleDir, "data", "chain", "landing.json")); err != nil {
		t.Fatalf("后置列表未写入 data/chain: %v", err)
	}
}

func TestResourcesSmartGroupsAndChainFront(t *testing.T) {
	server := resourceFixture(t)
	var view module.SmartGroupsView
	resourceAssertSuccess(t, server, "GET", "/api/v1/smart-groups", "", "smart.list", &view)
	if len(view.Groups) != 0 || view.Active != "" {
		t.Fatalf("初始智能分组错误: %+v", view)
	}
	for _, input := range []string{"socks://127.0.0.1:1081#" + url.PathEscape("🇭🇰香港 01"), "socks://127.0.0.1:1082#" + url.PathEscape("剩余流量 10GB")} {
		if _, err := catalog.AppendNode(t.Context(), catalog.MutationOptions{
			GroupDir: filepath.Join(server.options.CatalogRoot, "default"), GroupID: "default", Type: "local", Input: input,
		}); err != nil {
			t.Fatal(err)
		}
	}
	var generated struct {
		Smart   module.SmartGroupsView `json:"smart"`
		Summary smartgroup.Summary     `json:"summary"`
	}
	resourceAssertSuccess(t, server, "POST", "/api/v1/smart-groups/generate", `{"english":false}`, "smart.generated", &generated)
	if generated.Summary != (smartgroup.Summary{Regions: 2, Nodes: 2, Unclassified: 1, Informational: 1}) ||
		len(generated.Smart.Groups) != 2 || generated.Smart.Groups[0].ID != "smart-hk" || generated.Smart.Groups[0].Available != 1 {
		t.Fatalf("智能分组结果错误: %+v", generated)
	}
	resourceAssertSuccess(t, server, "PUT", "/api/v1/selection", `{"smart_group_id":"smart-hk"}`, "node.selected", nil)
	var selection service.Selection
	resourceAssertSuccess(t, server, "GET", "/api/v1/selection", "", "node.current", &selection)
	if selection.SelectorMode != "smart" || selection.SmartGroupName != "🇭🇰 香港" || selection.Selected != "Smart/🇭🇰 香港" {
		t.Fatalf("智能分组未成为节点选择: %+v", selection)
	}
	// 分组内手动选择节点并记住；省略 manual 时回到分组的自动选择。
	resourceAssertSuccess(t, server, "PUT", "/api/v1/smart-groups/smart-hk/selection", `{"manual":{"group_id":"default","node":"🇭🇰香港 01"}}`, "node.selected", nil)
	view = module.SmartGroupsView{}
	resourceAssertSuccess(t, server, "GET", "/api/v1/smart-groups", "", "smart.list", &view)
	if hk := view.Groups[0]; view.Active != "smart-hk" || hk.Manual == nil || *hk.Manual != (smartgroup.NodeRef{GroupID: "default", Node: "🇭🇰香港 01"}) {
		t.Fatalf("分组内的手动选择未保存: %+v", view)
	}
	response, result := resourceRequest(t, server, "PUT", "/api/v1/smart-groups/smart-hk/selection", `{"manual":{"group_id":"default","node":"不在分组"}}`)
	if response.Code != http.StatusUnprocessableEntity || result.Code != "smart.invalid" {
		t.Fatalf("不在分组中的节点应被拒绝: %s", response.Body.String())
	}
	resourceAssertSuccess(t, server, "PUT", "/api/v1/smart-groups/smart-hk/selection", `{}`, "node.selected", nil)
	view = module.SmartGroupsView{}
	resourceAssertSuccess(t, server, "GET", "/api/v1/smart-groups", "", "smart.list", &view)
	if view.Groups[0].Manual != nil {
		t.Fatalf("改回自动选择后不应保留手动节点: %+v", view.Groups[0])
	}
	var saved struct {
		Smart module.SmartGroupsView `json:"smart"`
		ID    string                 `json:"id"`
	}
	resourceAssertSuccess(t, server, "POST", "/api/v1/smart-groups", `{"name":"自选","members":[{"group_id":"default","node":"节点 / 02"}]}`, "smart.created", &saved)
	if !strings.HasPrefix(saved.ID, "custom-") || len(saved.Smart.Groups) != 3 {
		t.Fatalf("自建分组创建结果错误: %+v", saved)
	}
	var chainView module.ChainView
	resourceAssertSuccess(t, server, "PUT", "/api/v1/chain/front", `{"smart_id":"`+saved.ID+`"}`, "chain.front_saved", &chainView)
	if chainView.FrontSetting == nil || chainView.FrontSetting.SmartID != saved.ID {
		t.Fatalf("前置未保存: %+v", chainView)
	}
	response, result = resourceRequest(t, server, "PUT", "/api/v1/chain/front", `{"group_id":"default","node":"不存在"}`)
	if response.Code != http.StatusUnprocessableEntity || !strings.Contains(result.Message, "前置") {
		t.Fatalf("不存在的前置节点应被拒绝: %s", response.Body.String())
	}
	resourceAssertSuccess(t, server, "PUT", "/api/v1/smart-groups/"+saved.ID, `{"name":"自选 2","members":[{"group_id":"default","node":"节点 / 02"}]}`, "smart.saved", &saved)
	resourceAssertSuccess(t, server, "DELETE", "/api/v1/smart-groups/smart-hk", "", "smart.deleted", &view)
	selection = service.Selection{}
	resourceAssertSuccess(t, server, "GET", "/api/v1/selection", "", "node.current", &selection)
	if selection.SelectorMode != "urltest" || selection.SmartGroupID != "" {
		t.Fatalf("删除正在使用的分组后应回到普通分组: %+v", selection)
	}
	resourceAssertSuccess(t, server, "DELETE", "/api/v1/smart-groups/"+saved.ID, "", "smart.deleted", &view)
	chainView = module.ChainView{}
	resourceAssertSuccess(t, server, "GET", "/api/v1/chain", "", "chain.current", &chainView)
	if chainView.FrontSetting != nil {
		t.Fatalf("删除前置使用的分组后应跟随节点页: %+v", chainView.FrontSetting)
	}
	response, result = resourceRequest(t, server, "PUT", "/api/v1/smart-groups/missing", `{"name":"x","members":[]}`)
	if response.Code != http.StatusNotFound || result.Code != "smart.not_found" {
		t.Fatalf("不存在的分组应返回 404: %s", response.Body.String())
	}
	response, result = resourceRequest(t, server, "POST", "/api/v1/smart-groups", `{"name":"空","members":[]}`)
	if response.Code != http.StatusUnprocessableEntity || result.Code != "smart.invalid" {
		t.Fatalf("空的自建分组应被拒绝: %s", response.Body.String())
	}
}

func TestResourcesNodeTagsPreserveSlashes(t *testing.T) {
	server := resourceFixture(t)
	query := url.Values{"group_id": {"default"}, "node_tag": {"节点 / 02"}}.Encode()
	resourceAssertSuccess(t, server, "GET", "/api/v1/nodes/get?"+query, "", "node.loaded", nil)
	resourceAssertSuccess(t, server, "GET", "/api/v1/nodes/export?"+query, "", "node.exported", nil)
	body := resourceJSON(t, map[string]string{"group_id": "default", "node_tag": "节点 / 02"})
	resourceAssertSuccess(t, server, "PUT", "/api/v1/selection", body, "node.selected", nil)
	config, err := moduleconfig.LoadModule(server.options.ModuleConfig)
	if err != nil || config.SelectedNodeTag != "节点 / 02" {
		t.Fatalf("标签未完整保存: %+v %v", config, err)
	}
	var selection service.Selection
	resourceAssertSuccess(t, server, "GET", "/api/v1/selection", "", "node.current", &selection)
	if selection.SelectedNodeRef != "default/节点 / 02" {
		t.Fatalf("读取丢失标签: %+v", selection)
	}
	edit := resourceJSON(t, map[string]string{"group_id": "default", "node_tag": "节点 / 02", "content": "socks://127.0.0.1:1081#" + url.PathEscape("节点 / 03")})
	resourceAssertSuccess(t, server, "PUT", "/api/v1/nodes", edit, "node.edited", nil)
	query = url.Values{"group_id": {"default"}, "node_tag": {"节点 / 03"}}.Encode()
	resourceAssertSuccess(t, server, "DELETE", "/api/v1/nodes?"+query, "", "node.removed", nil)
	config, err = moduleconfig.LoadModule(server.options.ModuleConfig)
	if err != nil || config.SelectedNodeTag != "" {
		t.Fatalf("删除后未回退 Auto: %+v %v", config, err)
	}
	response, _ := resourceRequest(t, server, "GET", "/api/v1/nodes/default/node/extra", "")
	if response.Code != http.StatusNotFound {
		t.Fatal("接受了多级路径节点标签")
	}
}

func TestResourcesNodeContentAndImportNeverReadPaths(t *testing.T) {
	server := resourceFixture(t)
	path := filepath.Join(t.TempDir(), "nodes.json")
	resourceWriteFile(t, path, "socks://127.0.0.1:1099#PRIVATE_FILE_NODE")
	before, err := os.ReadFile(filepath.Join(server.options.CatalogRoot, "default", "provider.json"))
	if err != nil {
		t.Fatal(err)
	}
	for _, request := range []struct {
		method, target string
		body           any
	}{
		{"POST", "/api/v1/nodes", map[string]string{"group_id": "default", "content": path}},
		{"PUT", "/api/v1/nodes", map[string]string{"group_id": "default", "node_tag": "节点 / 02", "content": path}},
		{"POST", "/api/v1/nodes/import", map[string]string{"content": path}},
		{"POST", "/api/v1/nodes/import", map[string]string{"source": path}},
	} {
		response, _ := resourceRequest(t, server, request.method, request.target, resourceJSON(t, request.body))
		if response.Code == http.StatusOK || strings.Contains(response.Body.String(), "PRIVATE_FILE_NODE") {
			t.Fatalf("请求读取了文件路径: %s", response.Body.String())
		}
		after, err := os.ReadFile(filepath.Join(server.options.CatalogRoot, "default", "provider.json"))
		if err != nil || string(before) != string(after) {
			t.Fatalf("拒绝路径时改变了 Provider: %v", err)
		}
	}
	resourceAssertSuccess(t, server, "POST", "/api/v1/nodes", `{"content":"socks://127.0.0.1:1082#HTTP_NODE"}`, "node.added", nil)
	resourceAssertSuccess(t, server, "POST", "/api/v1/nodes/import", `{"content":"socks://127.0.0.1:1083#IMPORTED_NODE"}`, "node.imported", nil)
}

func TestResourcesConfigRevisionAndValidation(t *testing.T) {
	server := resourceFixture(t)
	target := "/api/v1/config?target=module/auto_start"
	var initial map[string]string
	resourceAssertSuccess(t, server, "GET", target, "", "config.read", &initial)
	candidate := map[string]string{"revision": initial["revision"], "content": `{"auto_start":true}`}
	resourceAssertSuccess(t, server, "POST", "/api/v1/config/validate?target=module/auto_start", resourceJSON(t, candidate), "config.validate", nil)
	var afterValidation map[string]string
	resourceAssertSuccess(t, server, "GET", target, "", "config.read", &afterValidation)
	if afterValidation["revision"] != initial["revision"] || afterValidation["content"] != initial["content"] {
		t.Fatal("校验写入了配置")
	}
	var saved map[string]string
	resourceAssertSuccess(t, server, "PUT", target, resourceJSON(t, candidate), "config.apply", &saved)
	if saved["revision"] == initial["revision"] || saved["target"] != "module/auto_start" {
		t.Fatalf("保存未返回实际 revision: %+v", saved)
	}
	response, result := resourceRequest(t, server, "PUT", target, resourceJSON(t, candidate))
	if response.Code != http.StatusConflict || result.Code != "config.conflict" {
		t.Fatalf("冲突契约错误: %s", response.Body.String())
	}
	response, result = resourceRequest(t, server, "POST", "/api/v1/config/validate?target=module/auto_start", resourceJSON(t, candidate))
	if response.Code != http.StatusConflict || result.Code != "config.conflict" {
		t.Fatalf("校验未检测冲突: %s", response.Body.String())
	}
	config, err := moduleconfig.LoadModule(server.options.ModuleConfig)
	if err != nil || !config.AutoStart || config.ActiveGroupID != "default" || config.SelectedNodeTag != "" {
		t.Fatalf("分区保存改变了选择: %+v %v", config, err)
	}
	for _, path := range []string{server.options.StateFile, server.options.WorkerPIDFile,
		filepath.Join(server.options.RuntimeDir, "inbound.json"), filepath.Join(server.options.RuntimeDir, "providers.json"), filepath.Join(server.options.RuntimeDir, "outbounds.json")} {
		if _, err := os.Stat(path); !os.IsNotExist(err) {
			t.Fatalf("保存启动了停止的服务或 Worker: %s %v", path, err)
		}
	}
}

func TestResourcesConfigRequiresRevisionAndRejectsPaths(t *testing.T) {
	server := resourceFixture(t)
	for _, body := range []string{
		`{"content":"{}"}`, `{"revision":"","content":"{}"}`, `{"revision":"x","content":{}}`,
		`{"revision":"x","content":null}`, `{"revision":"x"}`, `{"revision":"x","content":"{}","source":"/data/secret"}`,
	} {
		for _, request := range []struct{ method, target string }{{"PUT", "/api/v1/config?target=module"}, {"POST", "/api/v1/config/validate?target=module"}} {
			response, result := resourceRequest(t, server, request.method, request.target, body)
			if response.Code != http.StatusBadRequest || result.Code != "usage.invalid" {
				t.Fatalf("接受不安全配置输入: %s", response.Body.String())
			}
		}
	}
	for _, target := range []string{"/etc/passwd", "../../module.json", "singbox/../../module.json", `singbox\config.json`, "runtime/../../config/module.json", "api", "singbox/rules/local/../../secret.json"} {
		response, result := resourceRequest(t, server, "GET", "/api/v1/config?target="+url.QueryEscape(target), "")
		if response.Code != http.StatusBadRequest || result.Code != "usage.invalid" {
			t.Fatalf("配置路径未拒绝 %s: %s", target, response.Body.String())
		}
	}
	resourceWriteFile(t, filepath.Join(server.options.RuntimeDir, "inbound.json"), "{}")
	var snapshot map[string]string
	resourceAssertSuccess(t, server, "GET", "/api/v1/config?target=runtime/inbound.json", "", "config.read", &snapshot)
	response, _ := resourceRequest(t, server, "PUT", "/api/v1/config?target=runtime/inbound.json", resourceJSON(t, map[string]string{"revision": snapshot["revision"], "content": "{}"}))
	if response.Code == http.StatusOK {
		t.Fatal("运行时配置可写")
	}
}

func TestResourcesModeUsesNativeNamesWhileStopped(t *testing.T) {
	server := resourceFixture(t)
	var state service.ModeState
	resourceAssertSuccess(t, server, "PUT", "/api/v1/mode", `{"mode":"Direct"}`, "mode.changed", &state)
	if state.Mode != "Direct" {
		t.Fatalf("默认模式未保存: %+v", state)
	}
	resourceAssertSuccess(t, server, "GET", "/api/v1/mode", "", "mode.current", &state)
	if state.Mode != "Direct" {
		t.Fatalf("停止时默认模式不可读: %+v", state)
	}
	if _, err := os.Stat(server.options.StateFile); !os.IsNotExist(err) {
		t.Fatalf("切换模式启动了服务: %v", err)
	}
}

func TestResourcesReadConfigCanRecoverBrokenEditorContent(t *testing.T) {
	server := resourceFixture(t)
	broken := `{"auto_start":`
	resourceWriteFile(t, server.options.ModuleConfig, broken)
	var document map[string]string
	resourceAssertSuccess(t, server, "GET", "/api/v1/config?target=module", "", "config.read", &document)
	if document["content"] != broken || document["revision"] == "" {
		t.Fatalf("损坏原文不可修复: %+v", document)
	}
	resourceAssertSuccess(t, server, "PUT", "/api/v1/config?target=module", resourceJSON(t, map[string]string{"revision": document["revision"], "content": resourceTestModule}), "config.apply", nil)
	resourceAssertSuccess(t, server, "GET", "/api/v1/service", "", "service.status", nil)
}

func TestResourcesServiceActionsAndUnknownCommands(t *testing.T) {
	server := resourceFixture(t)
	for _, action := range []string{"start", "stop", "restart", "reload", "check"} {
		response, result := resourceRequest(t, server, "POST", "/api/v1/service/"+action, `{"binary":"/bin/sh"}`)
		if response.Code != http.StatusBadRequest || result.Code != "usage.invalid" {
			t.Fatalf("服务操作未校验请求 %s: %s", action, response.Body.String())
		}
	}
	for _, target := range []string{"/api/v1/service/toggle", "/api/v1/service/exec", "/api/v1/exec", "/api/v1/raw", "/api/v1/app-policy"} {
		response, _ := resourceRequest(t, server, "POST", target, `{"command":"id"}`)
		if response.Code != http.StatusNotFound && response.Code != http.StatusMethodNotAllowed {
			t.Fatalf("开放非白名单接口 %s: %s", target, response.Body.String())
		}
	}
	response, result := resourceRequest(t, server, "POST", "/api/v1/service/check", "")
	if response.Code == http.StatusOK || result.Code == "usage.invalid" || result.Code == "api.not_found" {
		t.Fatalf("领域检查错误未向上返回: %s", response.Body.String())
	}
}

func TestResourcesRejectMalformedBodiesAndQueryTargets(t *testing.T) {
	server := resourceFixture(t)
	for _, body := range []string{
		`{"group_id":"default","node_tag":"","extra":1}`,
		`{"group_id":"default","group_id":"other","node_tag":""}`,
		`{"group_id":"default","node_tag":""} {}`,
		`{"group_id":5,"node_tag":""}`,
		"{\"group_id\":\"default\",\"node_tag\":\"\xff\"}",
	} {
		response, result := resourceRequest(t, server, "PUT", "/api/v1/selection", body)
		if response.Code != http.StatusBadRequest || result.Code != "usage.invalid" {
			t.Fatalf("接受非法 JSON: %s", response.Body.String())
		}
	}
	for _, target := range []string{
		"/api/v1/nodes?group_id=..", "/api/v1/nodes?group_id=staging", "/api/v1/nodes?group_id=a%2Fb", "/api/v1/nodes?group_id=a%5Cb",
		"/api/v1/nodes/get?group_id=default", "/api/v1/nodes?source=/data/secret", "/api/v1/logs?kind=/data/secret",
		"/api/v1/logs?lines=0", "/api/v1/logs?lines=2001", "/api/v1/logs?lines=-1", "/api/v1/config?target=module&source=/data/secret",
	} {
		response, result := resourceRequest(t, server, "GET", target, "")
		if response.Code != http.StatusBadRequest || result.Code != "usage.invalid" {
			t.Fatalf("接受非法资源参数 %s: %s", target, response.Body.String())
		}
	}
}

func TestResourcesAcceptedWriteSurvivesRequestCancellation(t *testing.T) {
	server := resourceFixture(t)
	ctx, cancel := context.WithCancel(t.Context())
	defer cancel()
	server.route("PUT /api/v1/resource-test-cancel", time.Second, false, func(ctx context.Context, r *http.Request) (result contract.Result, err error) {
		cancel()
		return server.saveSelection(ctx, r)
	})
	request := httptest.NewRequest("PUT", "/api/v1/resource-test-cancel", strings.NewReader(`{"group_id":"default","node_tag":"节点 / 02"}`)).WithContext(ctx)
	request.Host = server.config.Listen
	request.Header.Set("Authorization", "Bearer "+server.config.Secret)
	request.Header.Set("Content-Type", "application/json")
	response := httptest.NewRecorder()
	server.ServeHTTP(response, request)
	if response.Code != http.StatusOK {
		t.Fatalf("浏览器取消中断已接受写入: %s", response.Body.String())
	}
	config, err := moduleconfig.LoadModule(server.options.ModuleConfig)
	if err != nil || config.SelectedNodeTag != "节点 / 02" {
		t.Fatalf("写入未完成: %+v %v", config, err)
	}
}

func TestResourcesSubscriptionDownloadBudgetIsBounded(t *testing.T) {
	for _, value := range []struct {
		seconds int64
		want    time.Duration
	}{
		{0, 4 * time.Minute}, {60, 4 * time.Minute}, {120, 6 * time.Minute}, {240, 10 * time.Minute}, {1 << 62, 10 * time.Minute},
	} {
		if got := resourceDownloadBudget(value.seconds); got != value.want {
			t.Fatalf("预算 %d: %s != %s", value.seconds, got, value.want)
		}
	}
}

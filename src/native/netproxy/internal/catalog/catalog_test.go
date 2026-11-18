package catalog

import (
	"context"
	"fmt"
	"os"
	"path/filepath"
	"slices"
	"strings"
	"testing"
	"time"

	"encoding/json/jsontext"
	json "encoding/json/v2"
	moduleconfig "github.com/Fanju6/NetProxy-Magisk/src/native/netproxy/internal/config"
)

func TestScanAndBuildRuntime(t *testing.T) {
	root := t.TempDir()
	writeGroup(t, root, "default", "同名分组", "local", "本地节点")
	writeGroup(t, root, "remote", "同名分组", "subscription", "订阅节点")
	progressDir := filepath.Join(t.TempDir(), "progress")
	if err := os.MkdirAll(progressDir, 0o700); err != nil {
		t.Fatal(err)
	}
	if err := os.WriteFile(filepath.Join(progressDir, "remote.progress.json"), []byte(`{"stage":"convert","current":1}`), 0o600); err != nil {
		t.Fatal(err)
	}

	groups, err := Scan(context.Background(), ScanOptions{
		Root: root, ActiveGroup: "remote", ProgressDir: progressDir, WithNodes: true,
	})
	if err != nil {
		t.Fatal(err)
	}
	if len(groups) != 2 || len(groups[0].Nodes) != 1 || !groups[1].Group.Active {
		t.Fatalf("unexpected groups: %#v", groups)
	}
	if groups[0].Group.RuntimeTag != "同名分组 [default]" || groups[1].Group.RuntimeTag != "同名分组 [remote]" {
		t.Fatalf("unexpected runtime tags: %#v", groups)
	}
	if string(groups[1].Group.Progress) != `{"stage":"convert","current":1}` {
		t.Fatalf("unexpected progress: %s", groups[1].Group.Progress)
	}

	providersPath := filepath.Join(root, "runtime", "providers.json")
	outboundsPath := filepath.Join(root, "runtime", "outbounds.json")
	result, err := BuildRuntime(context.Background(), RuntimeOptions{
		Root: root, ProvidersOutput: providersPath, OutboundsOutput: outboundsPath,
		Selection: moduleconfig.Selection{ActiveGroupID: "remote", SelectedNodeTag: "订阅节点"},
	})
	if err != nil {
		t.Fatal(err)
	}
	if result.GroupCount != 2 || result.NodeCount != 2 || result.SelectorMode != "manual" {
		t.Fatalf("unexpected runtime result: %#v", result)
	}
	providers := readFile(t, providersPath)
	outbounds := readFile(t, outboundsPath)
	state := "selected_node_ref\t" + result.SelectedNodeRef
	for _, expected := range []string{`"tag": "同名分组 [default]"`, `"tag": "同名分组 [remote]"`} {
		if !strings.Contains(providers, expected) {
			t.Fatalf("providers missing %s: %s", expected, providers)
		}
	}
	if !strings.Contains(outbounds, `"default": "Select/同名分组 [remote]"`) {
		t.Fatalf("unexpected outbounds: %s", outbounds)
	}
	assertRuntimeGroupSources(t, outbounds)
	if !strings.Contains(state, "selected_node_ref\tremote/订阅节点") {
		t.Fatalf("unexpected state: %s", state)
	}
}

func TestSelectionResolutionAndNormalization(t *testing.T) {
	root := t.TempDir()
	writeGroup(t, root, "default", "本地配置", "local", "LOCAL")
	writeGroup(t, root, "remote", "远程订阅", "subscription", "REMOTE")
	selection, runtimeTag, err := ResolveSelection(t.Context(), root, moduleconfig.Selection{ActiveGroupID: "远程订阅", SelectedNodeTag: "REMOTE"})
	if err != nil || selection.ActiveGroupID != "remote" || runtimeTag != "远程订阅" {
		t.Fatalf("名称解析和节点校验不一致: %+v %s %v", selection, runtimeTag, err)
	}
	if _, _, err := ResolveSelection(t.Context(), root, moduleconfig.Selection{ActiveGroupID: "remote", SelectedNodeTag: "missing"}); err == nil {
		t.Fatal("严格选择接受了不存在的手动节点")
	}
	for _, test := range []struct {
		selection moduleconfig.Selection
		preferred string
		want      moduleconfig.Selection
	}{
		{moduleconfig.Selection{ActiveGroupID: "remote", SelectedNodeTag: "REMOTE"}, "default", moduleconfig.Selection{ActiveGroupID: "remote", SelectedNodeTag: "REMOTE"}},
		{moduleconfig.Selection{ActiveGroupID: "remote", SelectedNodeTag: "missing"}, "default", moduleconfig.Selection{ActiveGroupID: "remote"}},
		{moduleconfig.Selection{ActiveGroupID: "removed", SelectedNodeTag: "old"}, "remote", moduleconfig.Selection{ActiveGroupID: "remote"}},
		{moduleconfig.Selection{ActiveGroupID: "removed"}, "missing", moduleconfig.Selection{ActiveGroupID: "default"}},
	} {
		selection, tag, err := NormalizeSelection(t.Context(), root, test.selection, test.preferred)
		if err != nil || selection != test.want || tag == "" {
			t.Fatalf("选择整理异常: %+v want=%+v tag=%s err=%v", selection, test.want, tag, err)
		}
	}
	if err := SetGroupName(t.Context(), root, "remote", "本地配置", time.Now()); err != nil {
		t.Fatal(err)
	}
	if _, _, err := ResolveSelection(t.Context(), root, moduleconfig.Selection{ActiveGroupID: "本地配置"}); err == nil {
		t.Fatal("接受了重名分组查询")
	}
	_, tag, err := ResolveSelection(t.Context(), root, moduleconfig.Selection{ActiveGroupID: "remote"})
	if err != nil || tag != "本地配置 [remote]" {
		t.Fatalf("ID 选择未保持运行时标签消歧: %s %v", tag, err)
	}
}

func TestSelectionOnlyValidatesActiveProviderAndKeepsEmptyLocalGroup(t *testing.T) {
	root := t.TempDir()
	writeGroup(t, root, "default", "本地配置", "local", "LOCAL")
	writeGroup(t, root, "remote", "远程订阅", "subscription", "REMOTE")
	if err := os.WriteFile(filepath.Join(root, "remote", "provider.json"), []byte(`{"outbounds":[`), 0o600); err != nil {
		t.Fatal(err)
	}
	if _, _, err := NormalizeSelection(t.Context(), root, moduleconfig.Selection{ActiveGroupID: "default", SelectedNodeTag: "LOCAL"}, "remote"); err != nil {
		t.Fatalf("整理有效选择时解析了非活动 Provider: %v", err)
	}
	if _, _, err := NormalizeSelection(t.Context(), root, moduleconfig.Selection{ActiveGroupID: "remote"}, "default"); err == nil {
		t.Fatal("活动 Provider 损坏时静默切到了其他分组")
	}
	if err := DeleteGroup(t.Context(), root, "remote"); err != nil {
		t.Fatal(err)
	}
	if _, err := RemoveNode(t.Context(), MutationOptions{GroupDir: filepath.Join(root, "default"), GroupID: "default", Tag: "LOCAL"}); err != nil {
		t.Fatal(err)
	}
	selection, tag, err := NormalizeSelection(t.Context(), root, moduleconfig.Selection{ActiveGroupID: "default", SelectedNodeTag: "LOCAL"}, "")
	if err != nil || selection.ActiveGroupID != "default" || selection.SelectedNodeTag != "" || tag != "" {
		t.Fatalf("空 Catalog 未保留本地组和空 tag: %+v %s %v", selection, tag, err)
	}
}

func assertRuntimeGroupSources(t *testing.T, content string) {
	t.Helper()
	var document struct {
		Outbounds []map[string]jsontext.Value `json:"outbounds"`
	}
	if err := json.Unmarshal([]byte(content), &document); err != nil {
		t.Fatalf("parse runtime outbounds: %v", err)
	}
	for _, outbound := range document.Outbounds {
		var tag string
		if err := json.Unmarshal(outbound["tag"], &tag); err != nil {
			t.Fatalf("parse runtime outbound tag: %v", err)
		}
		_, hasOutbounds := outbound["outbounds"]
		_, hasProviders := outbound["providers"]
		switch {
		case tag == "Proxy":
			if !hasOutbounds || hasProviders {
				t.Fatalf("Proxy should only contain outbounds: %s", content)
			}
		case strings.HasPrefix(tag, "Auto/") || strings.HasPrefix(tag, "Select/"):
			if hasOutbounds || !hasProviders {
				t.Fatalf("provider group %q should only contain providers: %s", tag, content)
			}
		}
	}
}

func TestScanSummaryUsesMetadataWithoutParsingProvider(t *testing.T) {
	root := t.TempDir()
	writeGroup(t, root, "first", "同名分组", "local", "节点一")
	writeGroup(t, root, "second", "同名分组", "subscription", "节点二")
	updateNodeCount(t, filepath.Join(root, "first", "meta.json"), 7)
	updateNodeCount(t, filepath.Join(root, "second", "meta.json"), 9)
	if err := os.WriteFile(filepath.Join(root, "second", "provider.json"), []byte("invalid"), 0o600); err != nil {
		t.Fatal(err)
	}

	groups, err := Scan(context.Background(), ScanOptions{Root: root, WithNodes: false})
	if err != nil {
		t.Fatalf("摘要扫描不应解析 Provider: %v", err)
	}
	if len(groups) != 2 || groups[0].Group.NodeCount != 7 || groups[1].Group.NodeCount != 9 {
		t.Fatalf("摘要未使用 metadata 节点数: %#v", groups)
	}
	if groups[0].Group.RuntimeTag != "同名分组 [first]" || groups[1].Group.RuntimeTag != "同名分组 [second]" {
		t.Fatalf("摘要扫描丢失名称消歧: %#v", groups)
	}
	if _, err := Scan(context.Background(), ScanOptions{Root: root, WithNodes: true}); err == nil {
		t.Fatal("节点详情扫描应拒绝损坏的 Provider")
	}
}

func TestRuntimeTagIgnoresEmptyDuplicateGroup(t *testing.T) {
	root := t.TempDir()
	writeGroup(t, root, "ready", "同名分组", "subscription", "可用节点")
	writeGroup(t, root, "empty", "同名分组", "subscription", "待更新节点")
	if err := os.WriteFile(filepath.Join(root, "empty", "provider.json"), []byte(`{"outbounds":[]}`), 0o600); err != nil {
		t.Fatal(err)
	}
	updateNodeCount(t, filepath.Join(root, "empty", "meta.json"), 0)

	groups, err := Scan(context.Background(), ScanOptions{Root: root, WithNodes: false})
	if err != nil {
		t.Fatal(err)
	}
	for _, group := range groups {
		if group.Group.RuntimeTag != "同名分组" {
			t.Fatalf("empty duplicate changed summary RuntimeTag: %#v", groups)
		}
	}
	runtimeTag, err := RuntimeTag(context.Background(), root, "ready")
	if err != nil || runtimeTag != "同名分组" {
		t.Fatalf("RuntimeTag = %q, err=%v", runtimeTag, err)
	}

	runtimeDir := t.TempDir()
	providersPath := filepath.Join(runtimeDir, "providers.json")
	result, err := BuildRuntime(context.Background(), RuntimeOptions{
		Root: root, ProvidersOutput: providersPath,
		OutboundsOutput: filepath.Join(runtimeDir, "outbounds.json"), Selection: moduleconfig.Selection{ActiveGroupID: "ready"},
	})
	if err != nil {
		t.Fatal(err)
	}
	if result.GroupCount != 1 || result.ActiveGroupTag != runtimeTag {
		t.Fatalf("runtime result and lookup disagree: result=%+v tag=%q", result, runtimeTag)
	}
	providers := readFile(t, providersPath)
	if !strings.Contains(providers, `"tag": "同名分组"`) || strings.Contains(providers, `"tag": "同名分组 [ready]"`) {
		t.Fatalf("runtime providers used a different tag: %s", providers)
	}

	if err := os.WriteFile(filepath.Join(root, "empty", "provider.json"), []byte(`{"outbounds":[{"type":"socks","tag":"新节点","server":"example.com","server_port":1080}]}`), 0o600); err != nil {
		t.Fatal(err)
	}
	updateNodeCount(t, filepath.Join(root, "empty", "meta.json"), 1)
	runtimeTag, err = RuntimeTag(context.Background(), root, "ready")
	if err != nil || runtimeTag != "同名分组 [ready]" {
		t.Fatalf("non-empty duplicate RuntimeTag = %q, err=%v", runtimeTag, err)
	}
	result, err = BuildRuntime(context.Background(), RuntimeOptions{
		Root: root, ProvidersOutput: providersPath,
		OutboundsOutput: filepath.Join(runtimeDir, "outbounds.json"), Selection: moduleconfig.Selection{ActiveGroupID: "ready"},
	})
	if err != nil {
		t.Fatal(err)
	}
	if result.GroupCount != 2 || result.ActiveGroupTag != runtimeTag {
		t.Fatalf("runtime did not adopt duplicate tags after the group gained nodes: result=%+v tag=%q", result, runtimeTag)
	}
}

func BenchmarkScanSummary(b *testing.B) {
	root := b.TempDir()
	for groupIndex := range 40 {
		id := fmt.Sprintf("group-%02d", groupIndex)
		directory := filepath.Join(root, id)
		if err := os.MkdirAll(directory, 0o700); err != nil {
			b.Fatal(err)
		}
		metadata, err := json.Marshal(map[string]any{
			"id": id, "name": id, "type": "subscription", "revision": 1,
			"node_count": 250, "update_interval": 86400, "update_via_proxy": "auto",
		}, json.Deterministic(true))
		if err != nil {
			b.Fatal(err)
		}
		nodes := make([]map[string]any, 250)
		for nodeIndex := range nodes {
			nodes[nodeIndex] = map[string]any{
				"type": "socks", "tag": fmt.Sprintf("node-%03d", nodeIndex),
				"server": "example.com", "server_port": 1080,
			}
		}
		providerDocument, err := json.Marshal(map[string]any{"outbounds": nodes}, json.Deterministic(true))
		if err != nil {
			b.Fatal(err)
		}
		if err := os.WriteFile(filepath.Join(directory, "meta.json"), metadata, 0o600); err != nil {
			b.Fatal(err)
		}
		if err := os.WriteFile(filepath.Join(directory, "provider.json"), providerDocument, 0o600); err != nil {
			b.Fatal(err)
		}
	}

	b.Run("summary", func(b *testing.B) {
		for range b.N {
			if _, err := Scan(context.Background(), ScanOptions{Root: root, WithNodes: false}); err != nil {
				b.Fatal(err)
			}
		}
	})
	b.Run("with-nodes", func(b *testing.B) {
		for range b.N {
			if _, err := Scan(context.Background(), ScanOptions{Root: root, WithNodes: true}); err != nil {
				b.Fatal(err)
			}
		}
	})
}

func TestBuildRuntimeFallbackAndEmpty(t *testing.T) {
	root := t.TempDir()
	writeGroup(t, root, "default", "本地配置", "local", "节点")
	result, err := BuildRuntime(context.Background(), RuntimeOptions{
		Root: root, ProvidersOutput: filepath.Join(root, "providers.json"),
		OutboundsOutput: filepath.Join(root, "outbounds.json"),
		Selection:       moduleconfig.Selection{ActiveGroupID: "missing", SelectedNodeTag: "节点"},
	})
	if err != nil {
		t.Fatal(err)
	}
	if result.ActiveGroup != "default" || result.SelectorMode != "urltest" || result.SelectedNodeRef != "" {
		t.Fatalf("selection was not normalized: %#v", result)
	}

	emptyRoot := t.TempDir()
	if _, err := BuildRuntime(context.Background(), RuntimeOptions{
		Root: emptyRoot, ProvidersOutput: filepath.Join(emptyRoot, "providers.json"),
		OutboundsOutput: filepath.Join(emptyRoot, "outbounds.json"),
	}); err == nil {
		t.Fatal("expected empty Catalog to fail")
	}
	if _, err := BuildRuntime(context.Background(), RuntimeOptions{
		Root: emptyRoot, ProvidersOutput: filepath.Join(emptyRoot, "providers.json"),
		OutboundsOutput: filepath.Join(emptyRoot, "outbounds.json"), AllowEmpty: true,
	}); err != nil {
		t.Fatalf("allow-empty failed: %v", err)
	}
}

func TestBuildRuntimeChainOutbounds(t *testing.T) {
	root := t.TempDir()
	writeGroup(t, root, "default", "机场", "subscription", "香港")
	runtimeDir := t.TempDir()
	landing := map[string]jsontext.Value{
		"type": jsontext.Value(`"socks"`), "tag": jsontext.Value(`"落地/住宅"`),
		"server": jsontext.Value(`"203.0.113.9"`), "server_port": jsontext.Value(`1080`),
		"detour": jsontext.Value(`"链式前置"`),
	}
	if _, err := BuildRuntime(t.Context(), RuntimeOptions{
		Root: root, ProvidersOutput: filepath.Join(runtimeDir, "providers.json"),
		OutboundsOutput: filepath.Join(runtimeDir, "outbounds.json"),
		Selection:       moduleconfig.Selection{ActiveGroupID: "default"},
		Chain: &ChainOutbounds{
			FrontTag: "链式前置", SelectorTag: "链式代理",
			Landings: []map[string]jsontext.Value{landing}, LandingTags: []string{"落地/住宅"},
			Manual: "落地/住宅", ProxyOption: true, ProxyChain: true,
		},
	}); err != nil {
		t.Fatal(err)
	}
	var document struct {
		Outbounds []struct {
			Type      string   `json:"type"`
			Tag       string   `json:"tag"`
			Outbounds []string `json:"outbounds"`
			Default   string   `json:"default"`
			Detour    string   `json:"detour"`
		} `json:"outbounds"`
	}
	if err := json.Unmarshal([]byte(readFile(t, filepath.Join(runtimeDir, "outbounds.json"))), &document, json.RejectUnknownMembers(false)); err != nil {
		t.Fatal(err)
	}
	byTag := make(map[string]int, len(document.Outbounds))
	for index, outbound := range document.Outbounds {
		byTag[outbound.Tag] = index
	}
	front := document.Outbounds[byTag["链式前置"]]
	if front.Type != "selector" || front.Default != "Auto/机场" || strings.Join(front.Outbounds, ",") != "Auto/机场,Select/机场" {
		t.Fatalf("链式前置必须与 Proxy 使用同一组节点选项并默认当前选择: %#v", front)
	}
	selector := document.Outbounds[byTag["链式代理"]]
	if selector.Default != "落地/住宅" || strings.Join(selector.Outbounds, ",") != "落地/住宅" {
		t.Fatalf("链式代理选择器错误: %#v", selector)
	}
	proxy := document.Outbounds[byTag["Proxy"]]
	if proxy.Default != "链式代理" || strings.Join(proxy.Outbounds, ",") != "Auto/机场,Select/机场,链式代理" {
		t.Fatalf("手动模式下 Proxy 必须默认走链式代理: %#v", proxy)
	}
	index, ok := byTag["落地/住宅"]
	if !ok || document.Outbounds[index].Detour != "链式前置" {
		t.Fatalf("后置代理必须经由链式前置连接: %#v", document.Outbounds)
	}
}

func TestBuildRuntimeSmartGroups(t *testing.T) {
	root := t.TempDir()
	writeGroup(t, root, "default", "机场", "subscription", "香港 01")
	writeGroup(t, root, "local", "本地", "local", "日本")
	runtimeDir := t.TempDir()
	if _, err := BuildRuntime(t.Context(), RuntimeOptions{
		Root: root, ProvidersOutput: filepath.Join(runtimeDir, "providers.json"),
		OutboundsOutput: filepath.Join(runtimeDir, "outbounds.json"),
		Selection:       moduleconfig.Selection{ActiveGroupID: "default", SmartGroupID: "custom-asia"},
		Smart: []SmartGroup{
			{ID: "custom-asia", Tag: "Smart/亚洲", Members: []SmartMember{
				{GroupID: "default", Node: "香港 01"}, {GroupID: "local", Node: "日本"},
				{GroupID: "default", Node: "已删除"}, {GroupID: "missing", Node: "x"},
			}},
			{ID: "custom-empty", Tag: "Smart/空", Members: []SmartMember{{GroupID: "default", Node: "已删除"}}},
		},
		Chain: &ChainOutbounds{
			FrontTag: "链式前置", SelectorTag: "链式代理",
			Landings:    []map[string]jsontext.Value{{"type": jsontext.Value(`"http"`), "tag": jsontext.Value(`"落地/A"`), "server": jsontext.Value(`"203.0.113.1"`), "server_port": jsontext.Value(`8080`), "detour": jsontext.Value(`"链式前置"`)}},
			LandingTags: []string{"落地/A"}, ProxyOption: true,
		},
	}); err != nil {
		t.Fatal(err)
	}
	var document struct {
		Outbounds []struct {
			Type            string   `json:"type"`
			Tag             string   `json:"tag"`
			Outbounds       []string `json:"outbounds"`
			Providers       []string `json:"providers"`
			Include         string   `json:"include"`
			Default         string   `json:"default"`
			UseAllProviders bool     `json:"use_all_providers"`
		} `json:"outbounds"`
	}
	if err := json.Unmarshal([]byte(readFile(t, filepath.Join(runtimeDir, "outbounds.json"))), &document, json.RejectUnknownMembers(false)); err != nil {
		t.Fatal(err)
	}
	byTag := make(map[string]int, len(document.Outbounds))
	for index, outbound := range document.Outbounds {
		byTag[outbound.Tag] = index
	}
	if _, exists := byTag["Smart/空"]; exists {
		t.Fatal("没有可用成员的分组不能生成出站")
	}
	smart := document.Outbounds[byTag["Smart/亚洲"]]
	if smart.Type != "urltest" || strings.Join(smart.Providers, ",") != "机场,本地" || smart.Include != `^(?:机场/香港 01|本地/日本)$` {
		t.Fatalf("智能分组出站错误: %#v", smart)
	}
	proxy := document.Outbounds[byTag["Proxy"]]
	if proxy.Default != "Smart/亚洲" || !slices.Contains(proxy.Outbounds, "Smart/亚洲") {
		t.Fatalf("选中的智能分组必须是 Proxy 的默认选择: %#v", proxy)
	}
	front := document.Outbounds[byTag["链式前置"]]
	if !front.UseAllProviders || front.Default != "Smart/亚洲" || !slices.Contains(front.Outbounds, "Smart/亚洲") {
		t.Fatalf("链式前置必须能选择全部节点与智能分组: %#v", front)
	}
}

func TestBuildRuntimeChainPolicyModeKeepsNodeSelection(t *testing.T) {
	root := t.TempDir()
	writeGroup(t, root, "default", "机场", "subscription", "香港")
	runtimeDir := t.TempDir()
	if _, err := BuildRuntime(t.Context(), RuntimeOptions{
		Root: root, ProvidersOutput: filepath.Join(runtimeDir, "providers.json"),
		OutboundsOutput: filepath.Join(runtimeDir, "outbounds.json"),
		Selection:       moduleconfig.Selection{ActiveGroupID: "default", SelectedNodeTag: "香港"},
		Chain: &ChainOutbounds{
			FrontTag: "链式前置", SelectorTag: "链式代理",
			Landings:    []map[string]jsontext.Value{{"type": jsontext.Value(`"http"`), "tag": jsontext.Value(`"落地/A"`), "server": jsontext.Value(`"203.0.113.1"`), "server_port": jsontext.Value(`8080`), "detour": jsontext.Value(`"链式前置"`)}},
			LandingTags: []string{"落地/A"}, ProxyOption: true,
		},
	}); err != nil {
		t.Fatal(err)
	}
	outbounds := readFile(t, filepath.Join(runtimeDir, "outbounds.json"))
	if !strings.Contains(outbounds, `"default": "Select/机场"`) || strings.Contains(outbounds, `"default": "链式代理"`) {
		t.Fatalf("策略模式不能改变 Proxy 的节点选择: %s", outbounds)
	}
}

func TestBuildRuntimeManualChainRequiresNodes(t *testing.T) {
	root := t.TempDir()
	if _, err := BuildRuntime(t.Context(), RuntimeOptions{
		Root: root, ProvidersOutput: filepath.Join(root, "providers.json"),
		OutboundsOutput: filepath.Join(root, "outbounds.json"), AllowEmpty: true,
		Chain: &ChainOutbounds{FrontTag: "链式前置", SelectorTag: "链式代理", LandingTags: []string{"落地/A"}, ProxyChain: true},
	}); err == nil {
		t.Fatal("没有前置节点时手动后置不能静默退回直连")
	}
}

func TestTargetedScanDoesNotParseOtherProviders(t *testing.T) {
	root := t.TempDir()
	writeGroup(t, root, "default", "本地配置", "local", "LOCAL")
	writeGroup(t, root, "remote", "远程订阅", "subscription", "REMOTE")
	if err := os.WriteFile(filepath.Join(root, "remote", "provider.json"), []byte(`{"outbounds":[`), 0o600); err != nil {
		t.Fatal(err)
	}

	groups, err := Scan(context.Background(), ScanOptions{Root: root, GroupID: "default", WithNodes: true})
	if err != nil {
		t.Fatal(err)
	}
	if len(groups) != 1 || len(groups[0].Nodes) != 1 || groups[0].Nodes[0].Tag != "LOCAL" {
		t.Fatalf("unexpected targeted scan: %#v", groups)
	}
}

func TestBuildRuntimeUsesMetadataAndOnlyChecksManualTarget(t *testing.T) {
	root := t.TempDir()
	writeGroup(t, root, "default", "本地配置", "local", "LOCAL")
	writeGroup(t, root, "remote", "远程订阅", "subscription", "REMOTE")
	if err := os.WriteFile(filepath.Join(root, "remote", "provider.json"), []byte(`{"outbounds":[`), 0o600); err != nil {
		t.Fatal(err)
	}

	result, err := BuildRuntime(context.Background(), RuntimeOptions{
		Root: root, ProvidersOutput: filepath.Join(root, "providers.json"),
		OutboundsOutput: filepath.Join(root, "outbounds.json"),
		Selection:       moduleconfig.Selection{ActiveGroupID: "default", SelectedNodeTag: "LOCAL"},
	})
	if err != nil {
		t.Fatal(err)
	}
	if result.SelectedNodeRef != "default/LOCAL" || result.NodeCount != 2 {
		t.Fatalf("unexpected runtime summary: %#v", result)
	}
}

func TestBuildRuntimeMissingManualNodeUsesSameGroupAuto(t *testing.T) {
	root := t.TempDir()
	writeGroup(t, root, "default", "本地配置", "local", "NODE")
	result, err := BuildRuntime(context.Background(), RuntimeOptions{
		Root: root, ProvidersOutput: filepath.Join(root, "providers.json"),
		OutboundsOutput: filepath.Join(root, "outbounds.json"),
		Selection:       moduleconfig.Selection{ActiveGroupID: "default", SelectedNodeTag: "missing"},
	})
	if err != nil {
		t.Fatal(err)
	}
	if result.ActiveGroup != "default" || result.SelectorMode != "urltest" || result.Selection.SelectedNodeTag != "" || result.SelectedNodeRef != "" {
		t.Fatalf("失效手动节点未回到同组 Auto: %+v", result)
	}
	if !strings.Contains(readFile(t, filepath.Join(root, "outbounds.json")), `"default": "Auto/本地配置"`) {
		t.Fatal("失效手动节点没有使用同组 Auto")
	}
}

func TestSchedule(t *testing.T) {
	root := t.TempDir()
	writeGroup(t, root, "due", "到期订阅", "subscription", "节点一")
	writeGroup(t, root, "future", "未来订阅", "subscription", "节点二")
	writeGroup(t, root, "local", "本地配置", "local", "节点三")
	updateSchedule(t, filepath.Join(root, "due", "meta.json"), true, 100)
	updateSchedule(t, filepath.Join(root, "future", "meta.json"), true, 300)

	result, err := Schedule(context.Background(), root, 200)
	if err != nil {
		t.Fatal(err)
	}
	if result.Nearest != 100 || len(result.Due) != 1 || result.Due[0] != "due" {
		t.Fatalf("unexpected schedule: %#v", result)
	}
	ids, err := GroupIDs(context.Background(), root, "subscription")
	if err != nil {
		t.Fatal(err)
	}
	if len(ids) != 2 || ids[0] != "due" || ids[1] != "future" {
		t.Fatalf("unexpected subscription ids: %#v", ids)
	}
}

func writeGroup(t *testing.T, root, id, name, groupType, tag string) {
	t.Helper()
	directory := filepath.Join(root, id)
	if err := os.MkdirAll(directory, 0o700); err != nil {
		t.Fatal(err)
	}
	metadata, err := json.Marshal(map[string]any{
		"id": id, "name": name, "type": groupType, "revision": 1,
		"node_count": 1, "update_interval": 86400, "update_via_proxy": "auto",
	}, json.Deterministic(true))
	if err != nil {
		t.Fatal(err)
	}
	providerDocument := `{"outbounds":[{"type":"socks","tag":"` + tag + `","server":"example.com","server_port":1080}]}`
	if err := os.WriteFile(filepath.Join(directory, "meta.json"), metadata, 0o600); err != nil {
		t.Fatal(err)
	}
	if err := os.WriteFile(filepath.Join(directory, "provider.json"), []byte(providerDocument), 0o600); err != nil {
		t.Fatal(err)
	}
}

func readFile(t *testing.T, path string) string {
	t.Helper()
	content, err := os.ReadFile(path)
	if err != nil {
		t.Fatal(err)
	}
	return string(content)
}

func updateSchedule(t *testing.T, path string, enabled bool, epoch int64) {
	t.Helper()
	content, err := os.ReadFile(path)
	if err != nil {
		t.Fatal(err)
	}
	var metadata map[string]any
	if err := json.Unmarshal(content, &metadata); err != nil {
		t.Fatal(err)
	}
	metadata["auto_update"] = enabled
	metadata["next_update_epoch"] = epoch
	content, err = json.Marshal(metadata, json.Deterministic(true))
	if err != nil {
		t.Fatal(err)
	}
	if err := os.WriteFile(path, content, 0o600); err != nil {
		t.Fatal(err)
	}
}

func updateNodeCount(t *testing.T, path string, nodeCount int) {
	t.Helper()
	content, err := os.ReadFile(path)
	if err != nil {
		t.Fatal(err)
	}
	var metadata map[string]any
	if err := json.Unmarshal(content, &metadata); err != nil {
		t.Fatal(err)
	}
	metadata["node_count"] = nodeCount
	content, err = json.Marshal(metadata, json.Deterministic(true))
	if err != nil {
		t.Fatal(err)
	}
	if err := os.WriteFile(path, content, 0o600); err != nil {
		t.Fatal(err)
	}
}

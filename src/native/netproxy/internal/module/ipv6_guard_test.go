package module

import (
	json "encoding/json/v2"
	"os"
	"path/filepath"
	"strings"
	"testing"

	"github.com/Fanju6/NetProxy-Magisk/src/native/netproxy/internal/inbound"
)

func ipv6GuardFixture(t *testing.T, local string) Options {
	t.Helper()
	options := selectionFixture(t)
	if err := os.MkdirAll(options.SingBoxDir, 0o700); err != nil {
		t.Fatal(err)
	}
	if err := os.WriteFile(filepath.Join(options.SingBoxDir, "config.json"), []byte(`{"log":{"level":"info"}}`), 0o600); err != nil {
		t.Fatal(err)
	}
	if err := os.MkdirAll(filepath.Dir(options.InboundConfig), 0o700); err != nil {
		t.Fatal(err)
	}
	content := strings.Replace(testInboundConfig, `"local": {"enabled": true}`, `"local": `+local, 1)
	if err := os.WriteFile(options.InboundConfig, []byte(content), 0o600); err != nil {
		t.Fatal(err)
	}
	return options
}

func runtimeLocalIPv6(t *testing.T, path string) any {
	t.Helper()
	content, err := os.ReadFile(path)
	if err != nil {
		t.Fatal(err)
	}
	var document struct {
		Inbounds []struct {
			Local map[string]any `json:"local"`
		} `json:"inbounds"`
	}
	if err := json.Unmarshal(content, &document); err != nil || len(document.Inbounds) != 1 {
		t.Fatalf("运行时入站无效: %s %v", content, err)
	}
	return document.Inbounds[0].Local["ipv6"]
}

func TestPrepareGuardsIPv6DNSWhenIPv6IsNotIntercepted(t *testing.T) {
	options := ipv6GuardFixture(t, `{"enabled": true, "ipv6": false}`)
	prepared, err := Prepare(t.Context(), options, false)
	if err != nil {
		t.Fatal(err)
	}
	if !prepared.IPv6DNSGuard || runtimeLocalIPv6(t, prepared.Inbound) != true {
		t.Fatalf("不接管 IPv6 时运行时仍应接管 IPv6 以劫持 DNS: %v", prepared.IPv6DNSGuard)
	}
	route, err := os.ReadFile(prepared.Route)
	if err != nil {
		t.Fatal(err)
	}
	hijack, reject := strings.Index(string(route), "hijack-dns"), strings.Index(string(route), `"ip_version": 6`)
	if hijack < 0 || reject < 0 || hijack > reject {
		t.Fatalf("IPv6 拒绝规则应排在 DNS 劫持之后: %s", route)
	}
	// 不能直连：本地先接住的 IPv6 连接直连超时前应用不会回退 IPv4，谷歌等双栈站点会整体打不开。
	if !strings.Contains(string(route), `"action": "reject"`) || strings.Contains(string(route), `"outbound": "direct"`) {
		t.Fatalf("其余 IPv6 连接应被拒绝而不是直连: %s", route)
	}
	saved, err := os.ReadFile(options.InboundConfig)
	if err != nil || !strings.Contains(string(saved), `"ipv6": false`) {
		t.Fatalf("用户保存的设置不应被修改: %s %v", saved, err)
	}

	// 本次开机启动失败后退回：不再接管 IPv6，策略路由为空配置。
	if err := markIPv6DNSGuardUnsupported(options); err != nil {
		t.Fatal(err)
	}
	prepared, err = Prepare(t.Context(), options, false)
	if err != nil {
		t.Fatal(err)
	}
	route, _ = os.ReadFile(prepared.Route)
	if prepared.IPv6DNSGuard || runtimeLocalIPv6(t, prepared.Inbound) != false || string(route) != emptyOverlay {
		t.Fatalf("失败标记存在时应按用户原设置运行: %v %s", prepared.IPv6DNSGuard, route)
	}
	if _, err := os.Stat(inbound.IPv6GuardMarker(options.StateFile)); err != nil {
		t.Fatalf("失败标记应位于服务状态目录: %v", err)
	}
}

func TestPrepareSkipsIPv6GuardWithoutDNSHijack(t *testing.T) {
	for name, local := range map[string]string{
		"接管 IPv6":  `{"enabled": true, "ipv6": true}`,
		"默认 IPv6":  `{"enabled": true}`,
		"DNS 不接管": `{"enabled": true, "ipv6": false, "dns_mode": "off"}`,
	} {
		options := ipv6GuardFixture(t, local)
		prepared, err := Prepare(t.Context(), options, false)
		if err != nil {
			t.Fatalf("%s: %v", name, err)
		}
		if prepared.IPv6DNSGuard {
			t.Fatalf("%s 不应启用 IPv6 DNS 保护", name)
		}
	}
}

func TestWithIPv6RejectRuleFollowsExistingDNSHijack(t *testing.T) {
	overlay := `{"route":{"rules":[{"clash_mode":"Direct","action":"route","outbound":"direct"},` +
		`{"type":"logical","mode":"or","rules":[{"port":53},{"protocol":"dns"}],"action":"hijack-dns"},` +
		`{"package_name":["com.example.app"],"action":"route","outbound":"落地/L"}]}}`
	content, err := withIPv6RejectRule([]byte(overlay))
	if err != nil {
		t.Fatal(err)
	}
	var document struct {
		Route struct {
			Rules []map[string]any `json:"rules"`
		} `json:"route"`
	}
	if err := json.Unmarshal(content, &document); err != nil {
		t.Fatal(err)
	}
	rules := document.Route.Rules
	if len(rules) != 4 || rules[1]["action"] != "hijack-dns" || rules[2]["action"] != "reject" || rules[3]["outbound"] != "落地/L" {
		t.Fatalf("IPv6 拒绝规则应插在 DNS 劫持之后、策略组之前: %s", content)
	}
}

// Direct 模式与私网地址必须排除在拒绝之外：前者要求全部直连，后者是局域网 IPv6。
func TestIPv6RejectRuleExcludesDirectModeAndPrivateAddresses(t *testing.T) {
	content, err := withIPv6RejectRule([]byte(emptyOverlay))
	if err != nil {
		t.Fatal(err)
	}
	var document struct {
		Route struct {
			Rules []struct {
				Action string           `json:"action"`
				Mode   string           `json:"mode"`
				Rules  []map[string]any `json:"rules"`
			} `json:"rules"`
		} `json:"route"`
	}
	if err := json.Unmarshal(content, &document); err != nil {
		t.Fatal(err)
	}
	rules := document.Route.Rules
	if len(rules) != 2 || rules[0].Action != "hijack-dns" || rules[1].Action != "reject" || rules[1].Mode != "and" || len(rules[1].Rules) != 3 {
		t.Fatalf("没有 DNS 劫持规则时应先补劫持再加拒绝: %s", content)
	}
	conditions := rules[1].Rules
	if conditions[0]["ip_version"] != float64(6) {
		t.Fatalf("拒绝规则只应匹配 IPv6: %s", content)
	}
	if conditions[1]["clash_mode"] != "Direct" || conditions[1]["invert"] != true {
		t.Fatalf("Direct 模式不应被拒绝: %s", content)
	}
	if conditions[2]["ip_is_private"] != true || conditions[2]["invert"] != true {
		t.Fatalf("私网 IPv6 不应被拒绝: %s", content)
	}
}

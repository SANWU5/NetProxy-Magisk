package chain

import (
	"encoding/json/jsontext"
	json "encoding/json/v2"
	"os"
	goruntime "runtime"
	"strings"
	"testing"

	"github.com/sagernet/sing-box/option"
)

func TestParseLandingsAcceptsCommonFormats(t *testing.T) {
	content := strings.Join([]string{
		"203.0.113.10:1080:alice:p:a:ss",
		"user2:secret@proxy.example.com:3128",
		"socks5h://bob:pw@198.51.100.2:1081#住宅 美国",
		"http://carol:pw@198.51.100.3:8080",
		"https://dave:pw@proxy.example.net:443#TLS 代理",
		"[2001:db8::1]:1080:erin:pw",
		"# 注释行",
		"",
		"203.0.113.20:1080",
	}, "\n")
	document, diagnostics, err := ParseLandings(t.Context(), content, DefaultTypeSOCKS)
	if err != nil || len(diagnostics) != 0 {
		t.Fatalf("解析失败: %v %#v", err, diagnostics)
	}
	if len(document.Outbounds) != 7 {
		t.Fatalf("期望 7 个后置代理，实际 %d", len(document.Outbounds))
	}
	first, ok := document.Outbounds[0].Options.(*option.SOCKSOutboundOptions)
	if !ok || first.Server != "203.0.113.10" || first.ServerPort != 1080 || first.Username != "alice" || first.Password != "p:a:ss" {
		t.Fatalf("主机:端口:用户名:密码 解析错误: %#v", document.Outbounds[0])
	}
	if document.Outbounds[0].Tag != "203.0.113.10:1080" {
		t.Fatalf("默认名称应为 主机:端口，实际 %q", document.Outbounds[0].Tag)
	}
	if document.Outbounds[2].Tag != "住宅 美国" || document.Outbounds[2].Type != "socks" {
		t.Fatalf("链接名称或 socks5h 解析错误: %#v", document.Outbounds[2])
	}
	httpOptions, ok := document.Outbounds[3].Options.(*option.HTTPOutboundOptions)
	if !ok || httpOptions.Username != "carol" || httpOptions.TLS != nil {
		t.Fatalf("http 链接解析错误: %#v", document.Outbounds[3])
	}
	tlsOptions, ok := document.Outbounds[4].Options.(*option.HTTPOutboundOptions)
	if !ok || tlsOptions.TLS == nil || !tlsOptions.TLS.Enabled {
		t.Fatalf("https 链接应启用 TLS: %#v", document.Outbounds[4])
	}
	v6, ok := document.Outbounds[5].Options.(*option.SOCKSOutboundOptions)
	if !ok || v6.Server != "2001:db8::1" || v6.Username != "erin" {
		t.Fatalf("IPv6 解析错误: %#v", document.Outbounds[5])
	}
}

func TestParseLandingsUsesDefaultHTTPAndReportsBadLines(t *testing.T) {
	document, diagnostics, err := ParseLandings(t.Context(), "203.0.113.30:8080:u:p\nnot-a-proxy\n203.0.113.31:99999\nsocks4://1.2.3.4:1080", DefaultTypeHTTP)
	if err != nil {
		t.Fatal(err)
	}
	if len(document.Outbounds) != 1 || document.Outbounds[0].Type != "http" {
		t.Fatalf("默认协议未生效: %#v", document.Outbounds)
	}
	if len(diagnostics) != 3 || diagnostics[0].Index != 2 || diagnostics[2].Index != 4 {
		t.Fatalf("错误行诊断不完整: %#v", diagnostics)
	}
	if _, _, err := ParseLandings(t.Context(), "bad", DefaultTypeSOCKS); err == nil {
		t.Fatal("全部无效时必须报错")
	}
}

func TestUpdateValidatesReferencesAndPersists(t *testing.T) {
	dir := t.TempDir()
	if _, _, _, err := Update(t.Context(), dir, func(state *State) error {
		state.Mode = ModeManual
		return nil
	}); err == nil {
		t.Fatal("没有后置代理时不能开启手动模式")
	}
	document, _, err := ParseLandings(t.Context(), "203.0.113.1:1080:u:p\n203.0.113.2:1080", DefaultTypeSOCKS)
	if err != nil {
		t.Fatal(err)
	}
	_, after, _, err := Update(t.Context(), dir, func(state *State) error {
		result := state.AppendLandings(document)
		if len(result.Added) != 2 {
			t.Fatalf("导入结果错误: %#v", result)
		}
		state.Mode = ModeManualPolicy
		state.Manual = result.Added[0]
		state.Policies = append(state.Policies, Policy{ID: "p1", Name: "海外应用", Enabled: true, Landing: result.Added[1], Packages: []string{"com.example.app"}})
		return nil
	})
	if err != nil {
		t.Fatal(err)
	}
	loaded, err := Load(t.Context(), dir)
	if err != nil || loaded.Mode != ModeManualPolicy || loaded.Manual != after.Manual || len(loaded.Landings.Outbounds) != 2 {
		t.Fatalf("持久化结果错误: %#v %v", loaded, err)
	}
	info, err := os.Stat(settingsPath(dir))
	if err != nil {
		t.Fatal(err)
	}
	if goruntime.GOOS != "windows" && info.Mode().Perm()&0o077 != 0 {
		t.Fatalf("设置文件不能被其他用户读取: %v", info.Mode())
	}
	if _, _, _, err := Update(t.Context(), dir, func(state *State) error {
		return state.RemoveLanding(state.Manual)
	}); err == nil || !strings.Contains(err.Error(), "手动模式") {
		t.Fatalf("删除手动后置应被拒绝: %v", err)
	}
	if _, _, _, err := Update(t.Context(), dir, func(state *State) error {
		return state.RemoveLanding(state.Policies[0].Landing)
	}); err == nil || !strings.Contains(err.Error(), "海外应用") {
		t.Fatalf("删除策略引用的后置应被拒绝: %v", err)
	}
	_, renamed, _, err := Update(t.Context(), dir, func(state *State) error {
		return state.RenameLanding(state.Manual, "主后置")
	})
	if err != nil || renamed.Manual != "主后置" {
		t.Fatalf("重命名未同步手动引用: %#v %v", renamed.Settings, err)
	}
	// 不同策略组可以包含同一应用，按顺序先命中的组生效。
	if _, _, _, err := Update(t.Context(), dir, func(state *State) error {
		state.Policies = append(state.Policies, Policy{ID: "p2", Name: "重复", Enabled: true, Landing: "主后置", Packages: []string{"com.example.app"}})
		return nil
	}); err != nil {
		t.Fatalf("重叠的策略组应被接受: %v", err)
	}
	for name, policy := range map[string]Policy{
		"空白名单": {ID: "p3", Name: "空", Enabled: true, Landing: "主后置"},
		"前置带后置": {ID: "p3", Name: "前置", Enabled: true, Exit: ExitFront, Landing: "主后置", Match: MatchBlacklist},
		"未知匹配":  {ID: "p3", Name: "未知", Match: "everything", Landing: "主后置"},
		"未知出口":  {ID: "p3", Name: "未知", Exit: "proxy"},
	} {
		if _, _, _, err := Update(t.Context(), dir, func(state *State) error {
			state.Policies = append(state.Policies, policy)
			return nil
		}); err == nil {
			t.Fatalf("%s 应被拒绝", name)
		}
	}
	// 不勾选应用的黑名单组表示全部应用，可以启用；直连出口不需要后置。
	if _, _, _, err := Update(t.Context(), dir, func(state *State) error {
		state.Policies = append(state.Policies, Policy{ID: "p3", Name: "其余", Enabled: true, Match: MatchBlacklist, Exit: ExitDirect})
		return nil
	}); err != nil {
		t.Fatalf("空黑名单直连组应被接受: %v", err)
	}
}

func TestRouteOverlayOrdersGroupsAndDomesticDirect(t *testing.T) {
	state := State{Settings: Settings{Mode: ModePolicy, Policies: []Policy{
		{ID: "a", Name: "白", Enabled: true, Landing: "L", Packages: []string{"com.a.app"}, DomesticDirect: true},
		{ID: "b", Name: "关", Enabled: false, Landing: "L", Packages: []string{"com.b.app"}},
		{ID: "c", Name: "黑", Enabled: true, Match: MatchBlacklist, Exit: ExitFront, Packages: []string{"com.c.app"}},
		{ID: "d", Name: "全", Enabled: true, Match: MatchBlacklist, Exit: ExitDirect, DomesticDirect: true},
	}}}
	content, err := routeOverlay(state, []string{"geosite/cn", "geoip/cn"})
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
	base := document.Route.Rules[:5]
	if base[1]["action"] != "sniff" || base[2]["action"] != "hijack-dns" || base[4]["action"] != "sniff-override-destination" {
		t.Fatalf("策略组之前应依次嗅探、劫持 DNS、私有地址直连，并把 IPv6 目标改为域名: %s", content)
	}
	rules := document.Route.Rules[5:]
	if len(rules) != 4 {
		t.Fatalf("规则数量错误: %s", content)
	}
	if rules[0]["type"] != "logical" || rules[0]["outbound"] != "direct" {
		t.Fatalf("国内直连规则应排在组出口之前: %v", rules[0])
	}
	if rules[1]["outbound"] != LandingTag("L") || rules[1]["invert"] != nil {
		t.Fatalf("白名单组规则错误: %v", rules[1])
	}
	if rules[2]["outbound"] != FrontTag || rules[2]["invert"] != true {
		t.Fatalf("黑名单组应反选应用并经由前置: %v", rules[2])
	}
	if rules[3]["outbound"] != "direct" || rules[3]["network"] == nil {
		t.Fatalf("空黑名单直连组应匹配全部流量且不重复生成国内直连: %v", rules[3])
	}
}

func TestAppendLandingsSkipsDuplicates(t *testing.T) {
	var state State
	first, _, err := ParseLandings(t.Context(), "203.0.113.1:1080:u:p", DefaultTypeSOCKS)
	if err != nil {
		t.Fatal(err)
	}
	state.AppendLandings(first)
	second, _, err := ParseLandings(t.Context(), "socks5://u:p@203.0.113.1:1080#另一个名字\n203.0.113.1:1080:u:other", DefaultTypeSOCKS)
	if err != nil {
		t.Fatal(err)
	}
	result := state.AppendLandings(second)
	if len(result.Skipped) != 1 || len(result.Added) != 1 || result.Added[0] != "203.0.113.1:1080_2" {
		t.Fatalf("重复导入处理错误: %#v", result)
	}
}

func TestBuildRuntimeSetsDetourAndPolicyRoutes(t *testing.T) {
	dir := t.TempDir()
	document, _, err := ParseLandings(t.Context(), "socks5://u:p@203.0.113.1:1080#A\nhttp://203.0.113.2:8080#B", DefaultTypeSOCKS)
	if err != nil {
		t.Fatal(err)
	}
	_, state, _, err := Update(t.Context(), dir, func(state *State) error {
		state.AppendLandings(document)
		state.Mode = ModePolicy
		state.Policies = []Policy{
			{ID: "on", Name: "启用", Enabled: true, Landing: "B", Packages: []string{"com.example.one", "com.example.two"}},
			{ID: "off", Name: "停用", Enabled: false, Landing: "A", Packages: []string{"com.example.three"}},
		}
		return nil
	})
	if err != nil {
		t.Fatal(err)
	}
	runtime, err := BuildRuntime(t.Context(), dir, state)
	if err != nil {
		t.Fatal(err)
	}
	if runtime.ProxyChain || !runtime.ProxyOption || runtime.Manual != "" {
		t.Fatalf("策略模式不能接管 Proxy: %#v", runtime)
	}
	if strings.Join(runtime.LandingTags, ",") != "落地/A,落地/B" {
		t.Fatalf("后置运行时标签错误: %v", runtime.LandingTags)
	}
	for _, object := range runtime.Landings {
		var detour string
		if err := json.Unmarshal(object["detour"], &detour); err != nil || detour != FrontTag {
			t.Fatalf("后置必须经由链式前置: %s", object["detour"])
		}
	}
	var overlay struct {
		Route struct {
			Rules []map[string]jsontext.Value `json:"rules"`
		} `json:"route"`
	}
	if err := json.Unmarshal(runtime.Route, &overlay); err != nil {
		t.Fatal(err)
	}
	rules := overlay.Route.Rules
	if len(rules) != 6 || string(rules[0]["clash_mode"]) != `"Direct"` || string(rules[2]["action"]) != `"hijack-dns"` {
		t.Fatalf("策略路由必须保持 Direct 与 DNS 劫持优先: %s", runtime.Route)
	}
	if string(rules[4]["action"]) != `"sniff-override-destination"` || string(rules[4]["ip_version"]) != "6" {
		t.Fatalf("经后置的 IPv6 连接应改用嗅探到的域名: %s", runtime.Route)
	}
	if string(rules[5]["outbound"]) != `"落地/B"` || !strings.Contains(string(rules[5]["package_name"]), "com.example.two") {
		t.Fatalf("策略规则错误: %s", runtime.Route)
	}
	_, manual, _, err := Update(t.Context(), dir, func(state *State) error {
		state.Mode = ModeManual
		state.Manual = "A"
		return nil
	})
	if err != nil {
		t.Fatal(err)
	}
	runtime, err = BuildRuntime(t.Context(), dir, manual)
	if err != nil || !runtime.ProxyChain || runtime.Manual != "落地/A" {
		t.Fatalf("手动模式运行时错误: %#v %v", runtime, err)
	}
	// 手动模式没有策略组，但经后置的 IPv6 连接同样需要改用域名，否则后置没有 IPv6 时全部失败。
	if !strings.Contains(string(runtime.Route), `"sniff-override-destination"`) || strings.Contains(string(runtime.Route), "package_name") {
		t.Fatalf("手动模式的策略路由应只包含 IPv6 目标改写: %s", runtime.Route)
	}
}

func TestLoadRejectsUnknownFieldsAndNull(t *testing.T) {
	dir := t.TempDir()
	for _, content := range []string{`{"mode":"off","manual":"","policies":[],"extra":1}`, `{"mode":null}`, `{"mode":"global"}`} {
		if err := os.WriteFile(settingsPath(dir), []byte(content), 0o600); err != nil {
			t.Fatal(err)
		}
		if _, err := Load(t.Context(), dir); err == nil {
			t.Fatalf("无效设置被接受: %s", content)
		}
	}
}

package module

import (
	"encoding/json/jsontext"
	json "encoding/json/v2"
	"errors"
	"os"
	"slices"

	"github.com/Fanju6/NetProxy-Magisk/src/native/netproxy/internal/inbound"
)

// ipv6DNSGuardApplies 判断本次运行是否启用 IPv6 DNS 保护：本机接管未开启 IPv6 但仍劫持 DNS，
// 且本次开机没有因这项保护启动失败过（失败后退回原样，下次开机再试）。
func ipv6DNSGuardApplies(options Options, config inbound.Config) (bool, error) {
	leaks, err := config.LocalIPv6DNSLeaks()
	if err != nil || !leaks {
		return false, err
	}
	if _, err := os.Stat(inbound.IPv6GuardMarker(options.StateFile)); err == nil {
		return false, nil
	} else if !errors.Is(err, os.ErrNotExist) {
		return false, err
	}
	return true, nil
}

// markIPv6DNSGuardUnsupported 记录本次开机中 IPv6 DNS 保护导致核心启动失败，之后的启动与重载不再启用它。
func markIPv6DNSGuardUnsupported(options Options) error {
	return os.WriteFile(inbound.IPv6GuardMarker(options.StateFile), []byte("1\n"), 0o600)
}

// withIPv6RejectRule 在策略路由覆盖中加入「拒绝 IPv6 连接」，放在 DNS 劫持规则之后：
// 走 IPv6 的 DNS 仍被劫持到代理的 DNS，其余 IPv6 连接立即被拒绝，应用随即回退到 IPv4 并按正常规则分流。
//
// 不能直连：本机接管会在本地先接住应用的 IPv6 连接，应用以为已连上，不会回退 IPv4；
// 而谷歌等境外双栈站点的 IPv6 直连在国内不通，连接要等直连超时（约 5 秒）后才被重置，表现为这些站点全部打不开。
// Direct 模式（Wi-Fi 绕过等）与私网地址不在拒绝范围内，仍保持直连。
// 覆盖文件排在主配置之前，没有 DNS 劫持规则时先补一条，保证 IPv6 的 DNS 不会被拒绝规则抢先命中。
func withIPv6RejectRule(content []byte) ([]byte, error) {
	var document map[string]any
	if err := json.Unmarshal(content, &document); err != nil {
		return nil, err
	}
	if document == nil {
		document = map[string]any{}
	}
	route, _ := document["route"].(map[string]any)
	if route == nil {
		route = map[string]any{}
	}
	rules, _ := route["rules"].([]any)
	reject := map[string]any{
		"type": "logical", "mode": "and",
		"rules": []any{
			map[string]any{"ip_version": 6},
			map[string]any{"clash_mode": "Direct", "invert": true},
			map[string]any{"ip_is_private": true, "invert": true},
		},
		"action": "reject",
	}
	last := -1
	for index, rule := range rules {
		if object, ok := rule.(map[string]any); ok && object["action"] == "hijack-dns" {
			last = index
		}
	}
	if last < 0 {
		hijack := map[string]any{
			"type": "logical", "mode": "or",
			"rules":  []any{map[string]any{"port": 53}, map[string]any{"protocol": "dns"}},
			"action": "hijack-dns",
		}
		rules = append([]any{hijack, reject}, rules...)
	} else {
		rules = slices.Insert(rules, last+1, any(reject))
	}
	route["rules"] = rules
	document["route"] = route
	result, err := json.Marshal(document, json.Deterministic(true), jsontext.WithIndent("  "))
	if err != nil {
		return nil, err
	}
	return append(result, '\n'), nil
}

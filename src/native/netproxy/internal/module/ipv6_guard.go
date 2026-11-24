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

// withIPv6DirectRule 在策略路由覆盖中加入「IPv6 流量直连」，放在 DNS 劫持规则之后：
// 走 IPv6 的 DNS 仍被劫持到代理的 DNS，其余 IPv6 连接保持用户「不接管 IPv6」时的直连行为。
// 覆盖文件排在主配置之前，没有 DNS 劫持规则时先补一条，保证 IPv6 的 DNS 不会被直连规则抢先命中。
func withIPv6DirectRule(content []byte) ([]byte, error) {
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
	direct := map[string]any{"ip_version": 6, "action": "route", "outbound": "direct"}
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
		rules = append([]any{hijack, direct}, rules...)
	} else {
		rules = slices.Insert(rules, last+1, any(direct))
	}
	route["rules"] = rules
	document["route"] = route
	result, err := json.Marshal(document, json.Deterministic(true), jsontext.WithIndent("  "))
	if err != nil {
		return nil, err
	}
	return append(result, '\n'), nil
}

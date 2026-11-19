package chain

import (
	"context"
	"encoding/json/jsontext"
	json "encoding/json/v2"
	"errors"
	"fmt"
	"maps"
	"os"
)

// Runtime 是一次运行时生成需要编入 sing-box 的链式片段。
type Runtime struct {
	// Landings 是后置代理出站的原始 JSON，tag 已加 LandingTagPrefix，detour 指向 FrontTag。
	Landings []map[string]jsontext.Value
	// LandingTags 与 Landings 顺序一致。
	LandingTags []string
	// Manual 是手动后置的运行时标签；未选择时为空。
	Manual string
	// ProxyOption 表示顶层 Proxy 是否把 SelectorTag 列为可选项。
	ProxyOption bool
	// ProxyChain 表示顶层 Proxy 应固定选择 SelectorTag（手动或手动+策略模式）。
	ProxyChain bool
	// Route 是策略路由片段；不需要时为 nil。
	Route []byte
}

// BuildRuntime 根据持久状态生成运行时片段。没有后置代理时返回 nil。
// domestic 是主配置中可用的中国大陆规则集标签，供开启「国内直连」的策略组使用；为空时这些组全部经由出口。
func BuildRuntime(ctx context.Context, dir string, state State, domestic ...string) (*Runtime, error) {
	if err := state.Validate(); err != nil {
		return nil, err
	}
	if len(state.Landings.Outbounds) == 0 {
		return nil, nil
	}
	landings, err := landingObjects(dir)
	if err != nil {
		return nil, err
	}
	if len(landings) != len(state.Landings.Outbounds) {
		return nil, errors.New("后置代理列表在读取期间发生变化，请重试")
	}
	result := &Runtime{
		ProxyOption: state.Mode != ModeOff,
		ProxyChain:  UsesManual(state.Mode),
	}
	if state.Manual != "" {
		result.Manual = LandingTag(state.Manual)
	}
	frontValue, err := json.Marshal(FrontTag)
	if err != nil {
		return nil, err
	}
	for index, object := range landings {
		var tag string
		if err := json.Unmarshal(object["tag"], &tag); err != nil || tag != state.Landings.Outbounds[index].Tag {
			return nil, errors.New("后置代理列表在读取期间发生变化，请重试")
		}
		runtimeTag := LandingTag(tag)
		object["tag"], err = json.Marshal(runtimeTag)
		if err != nil {
			return nil, err
		}
		// 前置由选择同步设置（跟随节点页或用户指定的节点）；后置自身不能再走别的 detour，否则链路会脱离前置。
		object["detour"] = frontValue
		result.Landings = append(result.Landings, object)
		result.LandingTags = append(result.LandingTags, runtimeTag)
	}
	if UsesPolicies(state.Mode) {
		result.Route, err = routeOverlay(state, domestic)
		if err != nil {
			return nil, err
		}
	}
	return result, ctx.Err()
}

// landingObjects 直接复制持久文件中的出站对象，避免经过 option 结构体时丢失协议字段。
func landingObjects(dir string) ([]map[string]jsontext.Value, error) {
	content, err := os.ReadFile(landingPath(dir))
	if err != nil {
		return nil, err
	}
	var document struct {
		Outbounds []map[string]jsontext.Value `json:"outbounds"`
	}
	if err := json.Unmarshal(content, &document); err != nil {
		return nil, fmt.Errorf("读取后置代理列表失败: %w", err)
	}
	return document.Outbounds, nil
}

// routeOverlay 生成先于主配置加载的策略路由。
// 主配置的 Direct 模式与 DNS 劫持必须保持优先：Wi-Fi 绕过时策略应用同样直连，
// 应用自身的 DNS 查询仍由内核接管，不能被送进后置代理。
// domestic 是主配置中已定义的中国大陆规则集，组开启「国内直连」时这些目标先于组出口直连。
func routeOverlay(state State, domestic []string) ([]byte, error) {
	rules := []map[string]any{
		{"clash_mode": "Direct", "action": "route", "outbound": "direct"},
		{
			"type": "logical",
			"mode": "or",
			"rules": []map[string]any{
				{"port": 53},
				{"protocol": "dns"},
			},
			"action": "hijack-dns",
		},
		{"ip_is_private": true, "action": "route", "outbound": "direct"},
	}
	policyRules := 0
	// 组按列表顺序生成规则，sing-box 自上而下匹配，先命中的组生效。
	for _, policy := range state.Policies {
		if !policy.Enabled {
			continue
		}
		var match map[string]any
		switch {
		case policy.MatchMode() == MatchWhitelist:
			if len(policy.Packages) == 0 {
				continue
			}
			match = map[string]any{"package_name": policy.Packages}
		case len(policy.Packages) > 0:
			match = map[string]any{"package_name": policy.Packages, "invert": true}
		default:
			// 不勾选应用的黑名单组匹配全部流量。
			match = map[string]any{"network": []string{"tcp", "udp"}}
		}
		outbound := policy.exitTag()
		if policy.DomesticDirect && outbound != "direct" && len(domestic) > 0 {
			rules = append(rules, map[string]any{
				"type":     "logical",
				"mode":     "and",
				"rules":    []map[string]any{match, {"rule_set": domestic}},
				"action":   "route",
				"outbound": "direct",
			})
		}
		rule := maps.Clone(match)
		rule["action"], rule["outbound"] = "route", outbound
		rules = append(rules, rule)
		policyRules++
	}
	if policyRules == 0 {
		return nil, nil
	}
	content, err := json.Marshal(map[string]any{"route": map[string]any{"rules": rules}}, json.Deterministic(true), jsontext.WithIndent("  "))
	if err != nil {
		return nil, err
	}
	return append(content, '\n'), nil
}

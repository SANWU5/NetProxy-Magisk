package module

import (
	"encoding/json/jsontext"
	json "encoding/json/v2"
	"maps"
	"os"
	"path/filepath"
	"slices"
	"strconv"
	"strings"

	"github.com/Fanju6/NetProxy-Magisk/src/native/netproxy/internal/chain"
	"github.com/Fanju6/NetProxy-Magisk/src/native/netproxy/internal/inbound"
)

// domesticRuleSetTags 是策略组「国内直连」使用的中国大陆规则集，按此顺序取主配置中已定义的部分。
var domesticRuleSetTags = []string{"geosite/cn", "geoip/cn"}

// domesticRuleSets 返回主配置中已定义的中国大陆规则集标签。用户删掉这些规则集后不生成依赖它们的规则，
// 避免核心因引用不存在的规则集而启动失败；此时开启「国内直连」的组全部经由出口。
func domesticRuleSets(options Options) []string {
	content, err := os.ReadFile(filepath.Join(options.SingBoxDir, "config.json"))
	if err != nil {
		return nil
	}
	var document struct {
		Route struct {
			RuleSet []struct {
				Tag jsontext.Value `json:"tag"`
			} `json:"rule_set"`
		} `json:"route"`
	}
	if json.Unmarshal(content, &document) != nil {
		return nil
	}
	defined := make(map[string]bool)
	for _, entry := range document.Route.RuleSet {
		var single string
		var several []string
		if json.Unmarshal(entry.Tag, &single) == nil {
			defined[single] = true
		} else if json.Unmarshal(entry.Tag, &several) == nil {
			for _, tag := range several {
				defined[tag] = true
			}
		}
	}
	var result []string
	for _, tag := range domesticRuleSetTags {
		if defined[tag] {
			result = append(result, tag)
		}
	}
	return result
}

// interceptPolicyApps 让启用的白名单策略组中的应用一定被接管：分应用代理是白名单时补上这些应用，
// 是黑名单时移除这些应用。只用于本次运行时配置，用户保存的分应用名单不变。
// 白名单补充的条目只保留设备上确实存在的「用户:包名」，避免产生缺失应用的提示。
func interceptPolicyApps(app inbound.AppPolicy, state chain.State, resolve inbound.PackageUIDResolver) (inbound.AppPolicy, error) {
	if !app.Enabled || !chain.UsesPolicies(state.Mode) {
		return app, nil
	}
	required := make(map[string]bool)
	for _, policy := range state.Policies {
		if policy.Enabled && policy.MatchMode() == chain.MatchWhitelist {
			for _, name := range policy.Packages {
				required[name] = true
			}
		}
	}
	if len(required) == 0 {
		return app, nil
	}
	packageOf := func(value string) string {
		_, name, _ := strings.Cut(value, ":")
		return name
	}
	if app.Mode != "whitelist" {
		app.BypassApps = slices.DeleteFunc(slices.Clone(app.BypassApps), func(value string) bool { return required[packageOf(value)] })
		return app, nil
	}
	users := map[string]bool{"0": true}
	present := make(map[string]bool, len(app.ProxyApps))
	for _, value := range app.ProxyApps {
		user, _, _ := strings.Cut(value, ":")
		users[user] = true
		present[value] = true
	}
	var candidates []inbound.PackageRef
	for _, user := range slices.Sorted(maps.Keys(users)) {
		id, err := strconv.ParseUint(user, 10, 32)
		if err != nil {
			continue
		}
		for _, name := range slices.Sorted(maps.Keys(required)) {
			if !present[user+":"+name] {
				candidates = append(candidates, inbound.PackageRef{UserID: uint32(id), Package: name})
			}
		}
	}
	if len(candidates) == 0 {
		return app, nil
	}
	resolution, err := resolve(candidates)
	if err != nil {
		return app, err
	}
	app.ProxyApps = slices.Clone(app.ProxyApps)
	for _, ref := range candidates {
		if !slices.Contains(resolution.Missing, ref) {
			app.ProxyApps = append(app.ProxyApps, strconv.FormatUint(uint64(ref.UserID), 10)+":"+ref.Package)
		}
	}
	return app, nil
}

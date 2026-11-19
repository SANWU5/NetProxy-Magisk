package module

import (
	"os"
	"path/filepath"
	"slices"
	"testing"

	"github.com/Fanju6/NetProxy-Magisk/src/native/netproxy/internal/chain"
	"github.com/Fanju6/NetProxy-Magisk/src/native/netproxy/internal/inbound"
)

func TestInterceptPolicyAppsKeepsGroupAppsIntercepted(t *testing.T) {
	state := chain.State{Settings: chain.Settings{Mode: chain.ModeManualPolicy, Policies: []chain.Policy{
		{ID: "a", Name: "a", Enabled: true, Landing: "L", Packages: []string{"com.a.app", "com.b.app"}},
		{ID: "b", Name: "b", Enabled: false, Landing: "L", Packages: []string{"com.c.app"}},
		{ID: "c", Name: "c", Enabled: true, Match: chain.MatchBlacklist, Exit: chain.ExitDirect, Packages: []string{"com.d.app"}},
	}}}
	resolve := func(refs []inbound.PackageRef) (inbound.PackageUIDResolution, error) {
		var missing []inbound.PackageRef
		for _, ref := range refs {
			if ref.UserID == 999 && ref.Package == "com.b.app" {
				missing = append(missing, ref)
			}
		}
		return inbound.PackageUIDResolution{Missing: missing}, nil
	}
	whitelist, err := interceptPolicyApps(inbound.AppPolicy{Enabled: true, Mode: "whitelist", ProxyApps: []string{"999:com.x.app", "0:com.a.app"}}, state, resolve)
	if err != nil {
		t.Fatal(err)
	}
	if want := []string{"999:com.x.app", "0:com.a.app", "0:com.b.app", "999:com.a.app"}; !slices.Equal(whitelist.ProxyApps, want) {
		t.Fatalf("白名单应补上组内应用且跳过设备上不存在的条目: %v", whitelist.ProxyApps)
	}
	blacklist, err := interceptPolicyApps(inbound.AppPolicy{Enabled: true, Mode: "blacklist", BypassApps: []string{"0:com.a.app", "10:com.b.app", "0:com.c.app", "0:com.d.app"}}, state, resolve)
	if err != nil {
		t.Fatal(err)
	}
	if want := []string{"0:com.c.app", "0:com.d.app"}; !slices.Equal(blacklist.BypassApps, want) {
		t.Fatalf("黑名单只应移除启用的白名单组中的应用: %v", blacklist.BypassApps)
	}
	state.Mode = chain.ModeManual
	unchanged, err := interceptPolicyApps(inbound.AppPolicy{Enabled: true, Mode: "blacklist", BypassApps: []string{"0:com.a.app"}}, state, resolve)
	if err != nil || len(unchanged.BypassApps) != 1 {
		t.Fatalf("不使用策略组时不应改动分应用名单: %v %v", unchanged.BypassApps, err)
	}
}

func TestDomesticRuleSetsOnlyUsesDefinedTags(t *testing.T) {
	dir := t.TempDir()
	options := Options{SingBoxDir: dir}
	if got := domesticRuleSets(options); got != nil {
		t.Fatalf("缺少主配置时不应生成国内直连规则: %v", got)
	}
	content := `{"route":{"rule_set":[{"tag":["geosite/cn","geosite/google"]},{"tag":"fcm"}]}}`
	if err := os.WriteFile(filepath.Join(dir, "config.json"), []byte(content), 0o600); err != nil {
		t.Fatal(err)
	}
	if got := domesticRuleSets(options); !slices.Equal(got, []string{"geosite/cn"}) {
		t.Fatalf("只应使用主配置中已定义的规则集: %v", got)
	}
}

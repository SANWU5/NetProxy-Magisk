package smartgroup

import (
	"context"
	"slices"
	"testing"
	"time"
)

var now = time.Date(2026, 11, 18, 8, 0, 0, 0, time.UTC)

func ref(group, node string) NodeRef { return NodeRef{GroupID: group, Node: node} }

func TestGenerateGroupsByRegionAndSkipsInformationalNodes(t *testing.T) {
	sources := []Source{
		{GroupID: "sub", Tags: []string{"剩余流量：10 GB", "🇺🇸美国洛杉矶08", "HK01", "节点 9"}},
		{GroupID: "default", Tags: []string{"香港 02", "Japan-Tokyo"}},
	}
	doc, summary := Generate(Document{}, sources, false, now)
	if summary != (Summary{Regions: 4, Nodes: 5, Unclassified: 1, Informational: 1}) {
		t.Fatalf("summary = %+v", summary)
	}
	var names []string
	for _, group := range doc.Groups {
		names = append(names, group.Name)
	}
	if want := []string{"🇭🇰 香港", "🇯🇵 日本", "🇺🇸 美国", "🌐 其他"}; !slices.Equal(names, want) {
		t.Fatalf("names = %v, want %v", names, want)
	}
	hk := doc.Groups[0]
	if hk.ID != "smart-hk" || !slices.Equal(hk.Effective(), []NodeRef{ref("sub", "HK01"), ref("default", "香港 02")}) {
		t.Fatalf("hk group = %+v", hk)
	}
	if hk.RuntimeTag() != "Smart/🇭🇰 香港" || doc.Validate() != nil {
		t.Fatalf("runtime tag %q, validate %v", hk.RuntimeTag(), doc.Validate())
	}
}

func TestRegenerateKeepsManualEditsRenamesAndCustomGroups(t *testing.T) {
	sources := []Source{{GroupID: "sub", Tags: []string{"HK01", "HK02", "🇯🇵 东京"}}}
	doc, _ := Generate(Document{}, sources, false, now)
	hk := &doc.Groups[doc.Find("smart-hk")]
	hk.Name = "港区精选"
	hk.SetMembers([]NodeRef{ref("sub", "HK02"), ref("sub", "🇯🇵 东京")})
	doc.Groups = append(doc.Groups, Group{ID: NewCustomID(), Name: "游戏", Kind: KindCustom, Members: []NodeRef{ref("sub", "HK01")}})

	sources[0].Tags = append(sources[0].Tags, "HK03")
	next, _ := Generate(doc, sources, false, now)
	if err := next.Validate(); err != nil {
		t.Fatal(err)
	}
	hk2 := next.Groups[next.Find("smart-hk")]
	want := []NodeRef{ref("sub", "HK02"), ref("sub", "HK03"), ref("sub", "🇯🇵 东京")}
	if hk2.Name != "港区精选" || !slices.Equal(hk2.Effective(), want) {
		t.Fatalf("regenerated hk = %q %v, want %v", hk2.Name, hk2.Effective(), want)
	}
	if custom := next.Groups[len(next.Groups)-1]; custom.Name != "游戏" || len(custom.Members) != 1 {
		t.Fatalf("custom group not kept: %+v", custom)
	}
}

func TestValidateRejectsDuplicateNamesAndEmptyCustomGroups(t *testing.T) {
	duplicate := Document{Groups: []Group{
		{ID: "custom-a", Name: "A", Kind: KindCustom, Members: []NodeRef{ref("g", "n")}},
		{ID: "custom-b", Name: "A", Kind: KindCustom, Members: []NodeRef{ref("g", "n")}},
	}}
	if duplicate.Validate() == nil {
		t.Fatal("duplicate names accepted")
	}
	empty := Document{Groups: []Group{{ID: "custom-a", Name: "A", Kind: KindCustom, Members: []NodeRef{}}}}
	if empty.Validate() == nil {
		t.Fatal("empty custom group accepted")
	}
}

func TestUpdatePersistsAndLoadRoundTrips(t *testing.T) {
	dir := t.TempDir()
	ctx := context.Background()
	doc, err := Load(ctx, dir)
	if err != nil || len(doc.Groups) != 0 {
		t.Fatalf("initial load = %+v, %v", doc, err)
	}
	_, after, err := Update(ctx, dir, func(doc *Document) error {
		doc.Groups = append(doc.Groups, Group{ID: "custom-x", Name: "X", Kind: KindCustom, Members: []NodeRef{ref("g", "n")}})
		return nil
	})
	if err != nil {
		t.Fatal(err)
	}
	loaded, err := Load(ctx, dir)
	if err != nil || !SameRuntime(after, loaded) {
		t.Fatalf("loaded = %+v, %v", loaded, err)
	}
	if _, _, err := Update(ctx, dir, func(doc *Document) error {
		doc.Groups[0].Members = nil
		return nil
	}); err == nil {
		t.Fatal("invalid update accepted")
	}
}

func TestManualSelectionFollowsMembership(t *testing.T) {
	hk1, hk2 := ref("sub", "HK01"), ref("sub", "HK02")
	group := Group{ID: "custom-pick", Name: "自选", Kind: KindCustom, Members: []NodeRef{hk1, hk2}, Manual: &hk2}
	doc := Document{Groups: []Group{group}}
	if err := doc.Validate(); err != nil {
		t.Fatal(err)
	}
	clone := doc.Clone()
	clone.Groups[0].Manual.Node = "changed"
	if doc.Groups[0].Manual.Node != "HK02" {
		t.Fatal("Clone 必须深拷贝手动选择")
	}
	group.SetMembers([]NodeRef{hk2, hk1})
	if group.Manual == nil || *group.Manual != hk2 {
		t.Fatalf("节点仍在分组中时必须保留手动选择: %+v", group.Manual)
	}
	group.SetMembers([]NodeRef{hk1})
	if group.Manual != nil {
		t.Fatal("手动选择的节点移出分组后必须回到自动选择")
	}
	invalid := Document{Groups: []Group{{ID: "custom-bad", Name: "无效", Kind: KindCustom, Members: []NodeRef{hk1}, Manual: &hk2}}}
	if invalid.Validate() == nil {
		t.Fatal("手动选择必须是分组成员")
	}

	// 重新智能分组：节点仍属于这个地区时保留手动选择，不再属于时回到自动选择。
	generated, _ := Generate(Document{}, []Source{{GroupID: "sub", Tags: []string{"HK01", "HK02"}}}, false, now)
	generated.Groups[generated.Find("smart-hk")].Manual = &hk2
	kept, _ := Generate(generated, []Source{{GroupID: "sub", Tags: []string{"HK01", "HK02", "JP01"}}}, false, now)
	if manual := kept.Groups[kept.Find("smart-hk")].Manual; manual == nil || *manual != hk2 {
		t.Fatalf("重新分组后节点仍在地区内，手动选择必须保留: %+v", manual)
	}
	dropped, _ := Generate(generated, []Source{{GroupID: "sub", Tags: []string{"HK01"}}}, false, now)
	if dropped.Groups[dropped.Find("smart-hk")].Manual != nil || dropped.Validate() != nil {
		t.Fatal("重新分组后节点不在地区内，必须回到自动选择")
	}
	if group.AutoTag() != "SmartAuto/自选" || group.RuntimeTag() != "Smart/自选" {
		t.Fatalf("运行时标签错误: %s %s", group.RuntimeTag(), group.AutoTag())
	}
}
